package gov.cms.madie.cqllibraryservice.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
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
import gov.cms.madie.models.common.ReviewStatus;
import gov.cms.madie.models.library.CqlLibraryReview;
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

  @InjectMocks private CqlLibraryReviewService cqlLibraryReviewService;

  @Captor private ArgumentCaptor<CqlLibraryReview> reviewCaptor;

  private CqlLibraryReview review;

  @BeforeEach
  void setUp() {
    review =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .librarySetId("set-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment("Looks good")
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
            .comment("Looks good")
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
            .comment("old")
            .build();

    CqlLibraryReview update =
        CqlLibraryReview.builder()
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment("updated")
            .librarySetId("set-1")
            .build();

    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CqlLibraryReview result = cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);

    assertEquals("review-1", result.getId());
    assertEquals(ReviewStatus.READY_FOR_REVIEW, result.getStatus());
    assertEquals("updated", result.getComment());
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
            .comment("old")
            .build();

    CqlLibraryReview update =
        CqlLibraryReview.builder().status(ReviewStatus.READY_FOR_REVIEW).comment("updated").build();

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
  void updateReviewLogsReviewInProgressWhenStatusIsInProgress() {
    CqlLibraryReview existing =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .librarySetId("set-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .build();

    CqlLibraryReview update = CqlLibraryReview.builder().status(ReviewStatus.IN_PROGRESS).build();

    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);

    verify(actionLogService, times(1))
        .logAction("lib-1", ActionType.REVIEW_IN_PROGRESS, USERNAME, "actionLog");
  }

  @Test
  void updateReviewLogsReviewCompleteWhenStatusIsComplete() {
    CqlLibraryReview existing =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .librarySetId("set-1")
            .status(ReviewStatus.IN_PROGRESS)
            .build();

    CqlLibraryReview update = CqlLibraryReview.builder().status(ReviewStatus.COMPLETE).build();

    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);

    verify(actionLogService, times(1))
        .logAction("lib-1", ActionType.REVIEW_COMPLETE, USERNAME, "actionLog");
  }

  @Test
  void updateReviewPersistsReviewers() {
    CqlLibraryReview existing =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .librarySetId("set-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .reviewers(List.of("olduser"))
            .build();

    CqlLibraryReview update =
        CqlLibraryReview.builder()
            .status(ReviewStatus.READY_FOR_REVIEW)
            .reviewers(List.of("jtraeger", "zuser"))
            .build();

    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CqlLibraryReview result = cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);

    assertEquals(List.of("jtraeger", "zuser"), result.getReviewers());
    // A reviewer-only change must not add a history record.
    verify(actionLogService, never())
        .logAction(anyString(), any(ActionType.class), anyString(), anyString());
  }

  @Test
  void updateReviewKeepsExistingStatusWhenPayloadHasNoStatus() {
    CqlLibraryReview existing =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .librarySetId("set-1")
            .status(ReviewStatus.COMPLETE)
            .build();

    // Reviewer-only edit: no status supplied.
    CqlLibraryReview update = CqlLibraryReview.builder().reviewers(List.of("jtraeger")).build();

    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CqlLibraryReview result = cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);

    assertEquals(ReviewStatus.COMPLETE, result.getStatus());
    verify(actionLogService, never())
        .logAction(anyString(), any(ActionType.class), anyString(), anyString());
  }

  @Test
  void createReviewPersistsReviewersAndLogsInProgress() {
    CqlLibraryReview newReview =
        CqlLibraryReview.builder()
            .libraryId("lib-1")
            .librarySetId("set-1")
            .status(ReviewStatus.IN_PROGRESS)
            .reviewers(List.of("jtraeger"))
            .build();

    when(cqlLibraryReviewRepository.existsByLibraryId("lib-1")).thenReturn(false);
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CqlLibraryReview result = cqlLibraryReviewService.createReview(newReview, USERNAME);

    assertEquals(List.of("jtraeger"), result.getReviewers());
    verify(actionLogService, times(1))
        .logAction("lib-1", ActionType.REVIEW_IN_PROGRESS, USERNAME, "actionLog");
  }

  @Test
  void updateReviewLogsNotReadyForReviewWhenToggledOff() {
    CqlLibraryReview existing =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .librarySetId("set-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment("old")
            .build();

    CqlLibraryReview update =
        CqlLibraryReview.builder()
            .status(ReviewStatus.NOT_READY_FOR_REVIEW)
            .comment("needs work")
            .build();

    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);

    verify(actionLogService, times(1))
        .logAction("lib-1", ActionType.NOT_READY_FOR_REVIEW, USERNAME, "actionLog");
  }

  @Test
  void updateReviewDoesNotLogWhenStatusUnchanged() {
    CqlLibraryReview existing =
        CqlLibraryReview.builder()
            .id("review-1")
            .libraryId("lib-1")
            .librarySetId("set-1")
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment("original comment")
            .build();

    // Same status, only the comment changes.
    CqlLibraryReview update =
        CqlLibraryReview.builder()
            .status(ReviewStatus.READY_FOR_REVIEW)
            .comment("updated comment")
            .build();

    when(cqlLibraryReviewRepository.findByLibraryId("lib-1")).thenReturn(Optional.of(existing));
    when(cqlLibraryReviewRepository.save(any(CqlLibraryReview.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CqlLibraryReview result = cqlLibraryReviewService.updateReview("lib-1", update, USERNAME);

    assertEquals("updated comment", result.getComment());
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
  void getAllReadyForReviewVerifiesAccessAndDelegatesWithStatusMap() {
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
    ArgumentCaptor<List<ReviewStatus>> statusesCaptor = ArgumentCaptor.forClass(List.class);
    when(cqlLibraryReviewRepository.findAllByStatusIn(statusesCaptor.capture()))
        .thenReturn(List.of(review, inProgress, complete));

    LibraryListDTO dto =
        LibraryListDTO.builder().id("lib-1").librarySetId("set-1").reviewStatus("Ready").build();
    ArgumentCaptor<Map<String, ReviewStatus>> mapCaptor = ArgumentCaptor.forClass(Map.class);
    when(cqlLibraryService.getReviewLibraries(mapCaptor.capture())).thenReturn(List.of(dto));

    List<LibraryListDTO> results =
        cqlLibraryReviewService.getAllReadyForReview(USERNAME, ACCESS_TOKEN);

    assertEquals(1, results.size());
    assertEquals("lib-1", results.get(0).getId());
    // reviewer access is enforced before any data is gathered
    verify(cqlLibraryAccessControlService, times(1)).verifyReviewerAccess(USERNAME, ACCESS_TOKEN);
    List<ReviewStatus> queriedStatuses = statusesCaptor.getValue();
    assertEquals(3, queriedStatuses.size());
    assertTrue(queriedStatuses.contains(ReviewStatus.READY_FOR_REVIEW));
    assertTrue(queriedStatuses.contains(ReviewStatus.IN_PROGRESS));
    assertTrue(queriedStatuses.contains(ReviewStatus.COMPLETE));
    assertFalse(queriedStatuses.contains(ReviewStatus.NOT_READY_FOR_REVIEW));
    // review documents are collapsed into an id -> status map for the library service
    Map<String, ReviewStatus> statusByLibraryId = mapCaptor.getValue();
    assertEquals(3, statusByLibraryId.size());
    assertEquals(ReviewStatus.READY_FOR_REVIEW, statusByLibraryId.get("lib-1"));
    assertEquals(ReviewStatus.IN_PROGRESS, statusByLibraryId.get("lib-2"));
    assertEquals(ReviewStatus.COMPLETE, statusByLibraryId.get("lib-3"));
  }

  @Test
  void getAllReadyForReviewThrowsWhenNotReviewer() {
    doThrow(new PermissionDeniedException("CQL Library Reviews", "all", USERNAME))
        .when(cqlLibraryAccessControlService)
        .verifyReviewerAccess(USERNAME, ACCESS_TOKEN);

    assertThrows(
        PermissionDeniedException.class,
        () -> cqlLibraryReviewService.getAllReadyForReview(USERNAME, ACCESS_TOKEN));

    // no review data is read and nothing is delegated when access is denied
    verify(cqlLibraryReviewRepository, never()).findAllByStatusIn(any());
    verify(cqlLibraryService, never()).getReviewLibraries(any());
  }
}
