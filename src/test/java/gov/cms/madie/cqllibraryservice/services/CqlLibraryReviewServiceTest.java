package gov.cms.madie.cqllibraryservice.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.cms.madie.cqllibraryservice.exceptions.GeneralConflictException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryReviewRepository;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ReviewStatus;
import gov.cms.madie.models.library.CqlLibraryReview;
import java.util.List;
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

  @Mock private CqlLibraryReviewRepository cqlLibraryReviewRepository;

  @Mock private ActionLogService actionLogService;

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
}
