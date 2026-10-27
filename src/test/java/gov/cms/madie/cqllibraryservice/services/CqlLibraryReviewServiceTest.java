package gov.cms.madie.cqllibraryservice.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.exceptions.GeneralConflictException;
import gov.cms.madie.cqllibraryservice.exceptions.PermissionDeniedException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryReviewRepository;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.Comment;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.common.ReviewStatus;
import gov.cms.madie.models.library.CqlLibraryReview;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CqlLibraryReviewServiceTest {

  private static final String USERNAME = "test.user";
  private static final String ACCESS_TOKEN = "token";

  @Mock private CqlLibraryReviewRepository cqlLibraryReviewRepository;

  @Mock private ActionLogService actionLogService;

  @Mock private CqlLibraryService cqlLibraryService;

  @Mock private CqlLibraryAccessControlService cqlLibraryAccessControlService;

  @Mock private UserNameService userNameService;

  @InjectMocks private CqlLibraryReviewService cqlLibraryReviewService;

  @Captor private ArgumentCaptor<CqlLibraryReview> reviewCaptor;

  private CqlLibraryReview review;

  @BeforeEach
  void setUp() {
    // Resolving display names is covered by ReviewDisplayNameServiceTest; here it
    // just has to hand the review back untouched.
    lenient()
        .when(userNameService.getDisplayName(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    review =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .librarySetId("set-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment(List.of(Comment.builder().content("Looks good").build()))
            .reviewers(List.of(USERNAME))
            .build();
  }

  @Test
  void createReviewSucceedsWhenNoneExists() {
    CqlLibraryReview persisted =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .librarySetId("set-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment(List.of(Comment.builder().content("Looks good").build()))
            .build();
    when(cqlLibraryReviewRepository.existsByLibraryId("lib-1")).thenReturn(false);
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class))).thenReturn(persisted);

    CqlLibraryReview result = cqlLibraryReviewService.createReview(review, USERNAME);

    assertEquals("review-1", result.getId());
    verify(cqlLibraryReviewRepository).save(reviewCaptor.capture());
    assertNull(reviewCaptor.getValue().getId(), "id should be cleared before save");
    verify(actionLogService, times(1))
        .logAction("lib-1", ActionType.READY_FOR_REVIEW, USERNAME, "actionLog");
  }

  @Test
  void createReviewStampsReadyForReviewUser() {
    when(cqlLibraryReviewRepository.existsByLibraryId("lib-1")).thenReturn(false);
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    cqlLibraryReviewService.createReview(review, USERNAME);

    verify(cqlLibraryReviewRepository).save(reviewCaptor.capture());
    assertEquals(USERNAME, reviewCaptor.getValue().getMarkedForReviewBy());
    assertNotNull(reviewCaptor.getValue().getMarkedForReviewAt());
  }

  @Test
  void createReviewIgnoresClientSuppliedMarkedForReviewStamp() {
    review.setStatus(ReviewStatus.IN_PROGRESS);
    review.setMarkedForReviewBy("someone.else");
    review.setMarkedForReviewAt(Instant.parse("2000-01-01T00:00:00Z"));
    when(cqlLibraryReviewRepository.existsByLibraryId("lib-1")).thenReturn(false);
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    cqlLibraryReviewService.createReview(review, USERNAME);

    verify(cqlLibraryReviewRepository).save(reviewCaptor.capture());
    // Created straight into IN_PROGRESS, so there is no ready-for-review stamp.
    assertNull(reviewCaptor.getValue().getMarkedForReviewBy());
    assertNull(reviewCaptor.getValue().getMarkedForReviewAt());
  }

  @Test
  void updateReviewStampsReadyForReviewUserOnFirstTransition() {
    CqlLibraryReview existing =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .status(ReviewStatus.NOT_READY_FOR_REVIEW)
            .build();
    CqlLibraryReview update =
        CqlLibraryReview.builder().status(ReviewStatus.READY_FOR_REVIEW).build();

    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CqlLibraryReview result = cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);

    assertEquals(USERNAME, result.getMarkedForReviewBy());
    assertNotNull(result.getMarkedForReviewAt());
  }

  @Test
  void updateReviewKeepsOriginalStampWhenMovingThroughReviewStatuses() {
    Instant originallyReadyAt = Instant.parse("2026-08-12T09:00:00Z");
    CqlLibraryReview existing =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .markedForReviewBy("original.user")
            .markedForReviewAt(originallyReadyAt)
            .build();
    CqlLibraryReview update = CqlLibraryReview.builder().status(ReviewStatus.IN_PROGRESS).build();

    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CqlLibraryReview result = cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);

    assertEquals("original.user", result.getMarkedForReviewBy());
    assertEquals(originallyReadyAt, result.getMarkedForReviewAt());
  }

  @Test
  void updateReviewClearsStampWhenMarkedNotReadyForReview() {
    CqlLibraryReview existing =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .markedForReviewBy("original.user")
            .markedForReviewAt(Instant.parse("2026-08-12T09:00:00Z"))
            .build();
    CqlLibraryReview update =
        CqlLibraryReview.builder().status(ReviewStatus.NOT_READY_FOR_REVIEW).build();

    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CqlLibraryReview result = cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);

    assertNull(result.getMarkedForReviewBy());
    assertNull(result.getMarkedForReviewAt());
  }

  @Test
  void createReviewKeepsTheHarpIdOnTheSavedAndReturnedReview() {
    when(cqlLibraryReviewRepository.existsByLibraryId("lib-1")).thenReturn(false);
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CqlLibraryReview result = cqlLibraryReviewService.createReview(review, USERNAME);

    verify(cqlLibraryReviewRepository).save(reviewCaptor.capture());
    assertEquals(USERNAME, reviewCaptor.getValue().getMarkedForReviewBy());
    assertEquals(USERNAME, result.getMarkedForReviewBy());
    verify(userNameService, never()).getDisplayName(any());
  }

  @Test
  void createReviewThrowsWhenReviewAlreadyExists() {
    when(cqlLibraryReviewRepository.existsByLibraryId("lib-1")).thenReturn(true);

    assertThrows(
        GeneralConflictException.class,
        () -> cqlLibraryReviewService.createReview(review, USERNAME));

    verify(cqlLibraryReviewRepository, never()).save(any(CqlLibraryReview.class));
    verify(actionLogService, never())
        .logAction(anyString(), any(ActionType.class), anyString(), anyString());
  }

  @Test
  void updateReviewSucceedsWhenReviewExists() {
    CqlLibraryReview existing =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .librarySetId("set-1")
            .status(ReviewStatus.NOT_READY_FOR_REVIEW)
            .comment(List.of(Comment.builder().content("old").build()))
            .build();

    CqlLibraryReview update =
        CqlLibraryReview.builder()
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment(List.of(Comment.builder().content("updated").build()))
            .librarySetId("set-1")
            .build();

    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CqlLibraryReview result = cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);

    assertEquals("review-1", result.getId());
    assertEquals(ReviewStatus.READY_FOR_REVIEW, result.getStatus());
    assertEquals(1, result.getComment().size());
    assertEquals("updated", result.getComment().get(0).getContent());
    assertEquals("set-1", result.getLibrarySetId());
    verify(actionLogService, times(1))
        .logAction("lib-1", ActionType.READY_FOR_REVIEW, USERNAME, "actionLog");
  }

  @Test
  void updateReviewKeepsExistingLibrarySetIdWhenNotProvided() {
    CqlLibraryReview existing =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .librarySetId("set-1")
            .status(ReviewStatus.NOT_READY_FOR_REVIEW)
            .comment(List.of(Comment.builder().content("old").build()))
            .build();

    CqlLibraryReview update =
        CqlLibraryReview.builder()
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment(List.of(Comment.builder().content("updated").build()))
            .build();

    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CqlLibraryReview result = cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);

    assertEquals("set-1", result.getLibrarySetId());
  }

  @Test
  void updateReviewThrowsWhenReviewNotFound() {
    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> cqlLibraryReviewService.updateReview("lib-1", review, USERNAME));

    verify(cqlLibraryReviewRepository, never()).save(any(CqlLibraryReview.class));
    verify(actionLogService, never())
        .logAction(anyString(), any(ActionType.class), anyString(), anyString());
  }

  @Test
  void updateReviewLogsNotReadyForReviewWhenToggledOff() {
    CqlLibraryReview existing =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .librarySetId("set-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment(List.of(Comment.builder().content("old").build()))
            .build();

    CqlLibraryReview update =
        CqlLibraryReview.builder()
            .status(ReviewStatus.NOT_READY_FOR_REVIEW)
            .comment(List.of(Comment.builder().content("needs work").build()))
            .build();

    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);

    verify(actionLogService, times(1))
        .logAction("lib-1", ActionType.NOT_READY_FOR_REVIEW, USERNAME, "actionLog");
  }

  @Test
  void updateReviewLogsTheEventMatchingTheNewStatus() {
    CqlLibraryReview existing =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .librarySetId("set-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .build();
    CqlLibraryReview update =
        CqlLibraryReview.builder()
            .status(ReviewStatus.IN_PROGRESS)
            .comment(List.of(Comment.builder().content("reviewing").build()))
            .build();

    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CqlLibraryReview saved = cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);

    assertEquals(ReviewStatus.IN_PROGRESS, saved.getStatus());
    verify(actionLogService, times(1))
        .logAction("lib-1", ActionType.REVIEW_IN_PROGRESS, USERNAME, "actionLog");
  }

  @Test
  void updateReviewSavesReviewersAndKeepsStatusWhenNoneIsSent() {
    CqlLibraryReview existing =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .librarySetId("set-1")
            .status(ReviewStatus.COMPLETE)
            .build();
    CqlLibraryReview update =
        CqlLibraryReview.builder()
            .comment(List.of(Comment.builder().content("assigned").build()))
            .reviewers(List.of("jane", "john"))
            .build();

    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CqlLibraryReview saved = cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);
    assertEquals(List.of("jane", "john"), saved.getReviewers());
    assertEquals(ReviewStatus.COMPLETE, saved.getStatus());
    verify(actionLogService, never())
        .logAction(anyString(), any(ActionType.class), anyString(), anyString());
  }

  @Test
  void updateReviewDoesNotLogWhenStatusUnchanged() {
    CqlLibraryReview existing =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .librarySetId("set-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment(List.of(Comment.builder().content("original comment").build()))
            .build();

    // Same status, only the comment changes.
    CqlLibraryReview update =
        CqlLibraryReview.builder()
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment(List.of(Comment.builder().content("updated comment").build()))
            .build();

    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CqlLibraryReview result = cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);

    assertEquals(1, result.getComment().size());
    assertEquals("updated comment", result.getComment().get(0).getContent());
    verify(actionLogService, never())
        .logAction(anyString(), any(ActionType.class), anyString(), anyString());
  }

  @Test
  void getReviewByLibraryIdSucceeds() {
    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(review));

    CqlLibraryReview result = cqlLibraryReviewService.getReviewByLibraryId("lib-1");

    assertEquals("review-1", result.getId());
  }

  @Test
  void getReviewByLibraryIdThrowsWhenNotFound() {
    when(cqlLibraryReviewRepository.findByLibraryId(anyString())).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> cqlLibraryReviewService.getReviewByLibraryId("lib-1"));
  }

  @Test
  void getReviewsByLibrarySetIdReturnsAllReviews() {
    CqlLibraryReview other =
        CqlLibraryReview.builder().id("review-2").libraryId("lib-2").librarySetId("set-1").build();
    when(cqlLibraryReviewRepository.findAllByLibrarySetId("set-1"))
        .thenReturn(List.of(review, other));

    List<CqlLibraryReview> results = cqlLibraryReviewService.getReviewsByLibrarySetId("set-1");

    assertEquals(2, results.size());
    verify(cqlLibraryReviewRepository, times(1)).findAllByLibrarySetId("set-1");
  }

  @Test
  void getLibrariesInReviewVerifiesAccessAndDelegatesWithReviewMap() {
    CqlLibraryReview inProgress =
        CqlLibraryReview.builder()
            .id("review-2")
            .libraryId("lib-2")
            .librarySetId("set-2")
            .status(ReviewStatus.IN_PROGRESS)
            .build();
    CqlLibraryReview complete =
        CqlLibraryReview.builder()
            .id("review-3")
            .libraryId("lib-3")
            .librarySetId("set-3")
            .status(ReviewStatus.COMPLETE)
            .build();
    when(cqlLibraryReviewRepository.findAllByStatusIn(
            List.of(
                ReviewStatus.READY_FOR_REVIEW, ReviewStatus.IN_PROGRESS, ReviewStatus.COMPLETE)))
        .thenReturn(List.of(review, inProgress, complete));

    LibraryListDTO dto =
        LibraryListDTO.builder().id("lib-1").librarySetId("set-1").reviewStatus("Ready").build();
    ArgumentCaptor<Map<String, CqlLibraryReview>> mapCaptor = ArgumentCaptor.forClass(Map.class);
    when(cqlLibraryService.getReviewLibraries(mapCaptor.capture())).thenReturn(List.of(dto));

    List<LibraryListDTO> results =
        cqlLibraryReviewService.getLibrariesInReview(USERNAME, ACCESS_TOKEN, OwnershipType.ALL);

    assertEquals(1, results.size());
    assertEquals("lib-1", results.get(0).getId());
    // reviewer access is enforced before any data is gathered
    verify(cqlLibraryAccessControlService, times(1)).verifyReviewerAccess(USERNAME, ACCESS_TOKEN);
    // review documents are collapsed into an id -> review map for the library service
    Map<String, CqlLibraryReview> reviewByLibraryId = mapCaptor.getValue();
    assertEquals(3, reviewByLibraryId.size());
    assertEquals(ReviewStatus.READY_FOR_REVIEW, reviewByLibraryId.get("lib-1").getStatus());
    assertEquals(ReviewStatus.IN_PROGRESS, reviewByLibraryId.get("lib-2").getStatus());
    assertEquals(ReviewStatus.COMPLETE, reviewByLibraryId.get("lib-3").getStatus());
  }

  @Test
  void getLibrariesInReviewThrowsWhenNotReviewer() {
    doThrow(new PermissionDeniedException("CQL Library Reviews", "all", USERNAME))
        .when(cqlLibraryAccessControlService)
        .verifyReviewerAccess(USERNAME, ACCESS_TOKEN);

    assertThrows(
        PermissionDeniedException.class,
        () ->
            cqlLibraryReviewService.getLibrariesInReview(
                USERNAME, ACCESS_TOKEN, OwnershipType.ALL));

    // no review data is read and nothing is delegated when access is denied
    verify(cqlLibraryReviewRepository, never()).findAllByStatusIn(any());
    verify(cqlLibraryService, never()).getReviewLibraries(any());
  }

  @Test
  void getAllReadyForReviewForOwnedReviews() {
    CqlLibraryReview other =
        CqlLibraryReview.builder()
            .id("review-2")
            .libraryId("lib-2")
            .librarySetId("set-2")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .reviewers(List.of(USERNAME))
            .build();
    when(cqlLibraryReviewRepository.findAllByStatusInAndReviewersContaining(
            List.of(ReviewStatus.READY_FOR_REVIEW, ReviewStatus.IN_PROGRESS), USERNAME))
        .thenReturn(List.of(review, other));

    LibraryListDTO dto =
        LibraryListDTO.builder().id("lib-1").librarySetId("set-1").reviewStatus("Ready").build();
    ArgumentCaptor<Map<String, CqlLibraryReview>> mapCaptor = ArgumentCaptor.forClass(Map.class);
    when(cqlLibraryService.getReviewLibraries(mapCaptor.capture())).thenReturn(List.of(dto));

    List<LibraryListDTO> results =
        cqlLibraryReviewService.getLibrariesInReview(USERNAME, ACCESS_TOKEN, OwnershipType.OWNED);

    assertEquals(1, results.size());
    assertEquals("lib-1", results.get(0).getId());
    // reviewer access is enforced before any data is gathered
    verify(cqlLibraryAccessControlService, times(1)).verifyReviewerAccess(USERNAME, ACCESS_TOKEN);
    // review documents are collapsed into an id -> review map for the library service
    Map<String, CqlLibraryReview> reviewByLibraryId = mapCaptor.getValue();
    assertEquals(2, reviewByLibraryId.size());
    assertEquals(ReviewStatus.READY_FOR_REVIEW, reviewByLibraryId.get("lib-1").getStatus());
    assertEquals(ReviewStatus.READY_FOR_REVIEW, reviewByLibraryId.get("lib-2").getStatus());
  }

  @Test
  void getLibrariesInReviewFiltersOutNullLibraryIds() {
    CqlLibraryReview missingLibraryId =
        CqlLibraryReview.builder()
            .id("review-missing")
            .librarySetId("set-1")
            .status(ReviewStatus.IN_PROGRESS)
            .build();
    CqlLibraryReview valid =
        CqlLibraryReview.builder()
            .id("review-2")
            .libraryId("lib-2")
            .librarySetId("set-2")
            .status(ReviewStatus.IN_PROGRESS)
            .build();

    when(cqlLibraryReviewRepository.findAllByStatusIn(
            List.of(
                ReviewStatus.READY_FOR_REVIEW, ReviewStatus.IN_PROGRESS, ReviewStatus.COMPLETE)))
        .thenReturn(List.of(missingLibraryId, valid));

    ArgumentCaptor<Map<String, CqlLibraryReview>> mapCaptor = ArgumentCaptor.forClass(Map.class);
    when(cqlLibraryService.getReviewLibraries(mapCaptor.capture()))
        .thenReturn(List.of(new LibraryListDTO()));

    cqlLibraryReviewService.getLibrariesInReview(USERNAME, ACCESS_TOKEN, OwnershipType.ALL);

    Map<String, CqlLibraryReview> reviewByLibraryId = mapCaptor.getValue();
    assertEquals(1, reviewByLibraryId.size());
    assertEquals(ReviewStatus.IN_PROGRESS, reviewByLibraryId.get("lib-2").getStatus());
  }

  @Test
  void getLibrariesInReviewPreservesFirstStatusWhenDuplicateLibraryIdsExist() {
    CqlLibraryReview first =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-dup")
            .librarySetId("set-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .build();
    CqlLibraryReview duplicate =
        CqlLibraryReview.builder()
            .id("review-2")
            .libraryId("lib-dup")
            .librarySetId("set-2")
            .status(ReviewStatus.COMPLETE)
            .build();

    when(cqlLibraryReviewRepository.findAllByStatusIn(
            List.of(
                ReviewStatus.READY_FOR_REVIEW, ReviewStatus.IN_PROGRESS, ReviewStatus.COMPLETE)))
        .thenReturn(List.of(first, duplicate));

    ArgumentCaptor<Map<String, CqlLibraryReview>> mapCaptor = ArgumentCaptor.forClass(Map.class);
    when(cqlLibraryService.getReviewLibraries(mapCaptor.capture()))
        .thenReturn(List.of(new LibraryListDTO()));

    cqlLibraryReviewService.getLibrariesInReview(USERNAME, ACCESS_TOKEN, OwnershipType.ALL);

    assertEquals(ReviewStatus.READY_FOR_REVIEW, mapCaptor.getValue().get("lib-dup").getStatus());
  }

  @Test
  void createReviewAssignsIdsToCommentsWithoutOne() {
    review.setComment(
        List.of(
            Comment.builder().content("no id yet").build(),
            Comment.builder().id("c-existing").content("already has one").build()));
    when(cqlLibraryReviewRepository.existsByLibraryId("lib-1")).thenReturn(false);
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CqlLibraryReview result = cqlLibraryReviewService.createReview(review, USERNAME);

    assertNotNull(
        result.getComment().get(0).getId(), "embedded comments need a service-assigned id");
    assertEquals("c-existing", result.getComment().get(1).getId());
  }

  @Test
  void updateReviewAssignsIdsOnlyToCommentsWithoutOne() {
    CqlLibraryReview existing =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .build();
    CqlLibraryReview update =
        CqlLibraryReview.builder()
            .comment(
                List.of(
                    Comment.builder().id("c1").content("kept").build(),
                    Comment.builder().content("new").build()))
            .build();
    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CqlLibraryReview result = cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);

    assertEquals("c1", result.getComment().get(0).getId());
    assertNotNull(result.getComment().get(1).getId());
  }

  @Test
  void updateReviewLogsAddedEditedAndDeletedCommentsAgainstTheLibrary() {
    CqlLibraryReview existing =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment(
                List.of(
                    Comment.builder().id("c1").content("old text").build(),
                    Comment.builder().id("c2").content("to be removed").build(),
                    Comment.builder().id("c3").content("untouched").build()))
            .build();
    CqlLibraryReview update =
        CqlLibraryReview.builder()
            .comment(
                List.of(
                    Comment.builder().id("c1").content("new text").build(),
                    Comment.builder().id("c3").content("untouched").build(),
                    Comment.builder().content("brand new").build()))
            .build();
    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CqlLibraryReview result = cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);

    String newCommentId = result.getComment().get(2).getId();
    assertNotNull(newCommentId);
    verify(actionLogService)
        .logAction(
            "lib-1",
            ActionType.COMMENT_ADDED,
            USERNAME,
            "libraryReviewActionLog",
            "relatedCommentId: " + newCommentId);
    verify(actionLogService)
        .logAction(
            "lib-1",
            ActionType.COMMENT_EDITED,
            USERNAME,
            "libraryReviewActionLog",
            "relatedCommentId: c1");
    verify(actionLogService)
        .logAction(
            "lib-1",
            ActionType.COMMENT_DELETED,
            USERNAME,
            "libraryReviewActionLog",
            "relatedCommentId: c2");
    // the untouched comment is not logged, and nothing lands in library history
    verify(actionLogService, never())
        .logAction(
            anyString(),
            any(ActionType.class),
            anyString(),
            anyString(),
            eq("relatedCommentId: c3"));
    verify(actionLogService, never())
        .logAction(anyString(), any(ActionType.class), anyString(), eq("actionLog"));
  }

  @Test
  void updateReviewDoesNotLogCommentsThatDidNotChange() {
    CqlLibraryReview existing =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment(List.of(Comment.builder().id("c1").content("same").build()))
            .build();
    CqlLibraryReview update =
        CqlLibraryReview.builder()
            .reviewers(List.of("someone"))
            .comment(List.of(Comment.builder().id("c1").content("same").build()))
            .build();
    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);

    verify(actionLogService, never())
        .logAction(
            anyString(), any(ActionType.class), anyString(), eq("libraryReviewActionLog"), any());
  }

  @Test
  void createReviewLogsCommentsItIsCreatedWith() {
    review.setComment(List.of(Comment.builder().content("first").build()));
    when(cqlLibraryReviewRepository.existsByLibraryId("lib-1")).thenReturn(false);
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(
            invocation -> {
              CqlLibraryReview toSave = invocation.getArgument(0);
              toSave.setId("review-1");
              return toSave;
            });

    CqlLibraryReview result = cqlLibraryReviewService.createReview(review, USERNAME);

    verify(actionLogService)
        .logAction(
            "lib-1",
            ActionType.COMMENT_ADDED,
            USERNAME,
            "libraryReviewActionLog",
            "relatedCommentId: " + result.getComment().get(0).getId());
  }
}
