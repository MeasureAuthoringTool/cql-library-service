package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.exceptions.GeneralConflictException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryReviewRepository;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ReviewStatus;
import gov.cms.madie.models.library.CqlLibraryReview;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class CqlLibraryReviewService {

  private static final String LIBRARY_HISTORY_COLLECTION = "actionLog";

  private final CqlLibraryReviewRepository cqlLibraryReviewRepository;
  private final ActionLogService actionLogService;
  private final CqlLibraryService cqlLibraryService;
  private final CqlLibraryAccessControlService cqlLibraryAccessControlService;

  /**
   * Creates a new review document for the given library. Enforces the one-review-per-library
   * invariant by rejecting the request if a review already exists for the library. Logs a
   * READY_FOR_REVIEW / NOT_READY_FOR_REVIEW event against the library instance history.
   *
   * @param review the review to create; libraryId must be populated on the review
   * @param username the HARP id / name of the user performing the action
   * @return the persisted review
   */
  public CqlLibraryReview createReview(CqlLibraryReview review, String username) {
    final String libraryId = review.getLibraryId();
    if (cqlLibraryReviewRepository.existsByLibraryId(libraryId)) {
      throw new GeneralConflictException(
          "A review already exists for CQL Library with id: " + libraryId);
    }
    // Ensure a new document is created rather than overwriting an existing one.
    review.setId(null);
    CqlLibraryReview saved = cqlLibraryReviewRepository.save(review);
    log.info("Created review [{}] for CQL Library [{}]", saved.getId(), libraryId);
    logReviewAction(libraryId, saved.getStatus(), username);
    return saved;
  }

  /**
   * Updates the existing review for the given library. The review is looked up by libraryId, so the
   * caller does not need to know the review document id. Logs a READY_FOR_REVIEW /
   * NOT_READY_FOR_REVIEW event against the library instance history.
   *
   * @param libraryId the library whose review should be updated
   * @param review the review payload containing the new status/comment
   * @param username the HARP id / name of the user performing the action
   * @return the updated review
   */
  public CqlLibraryReview updateReview(String libraryId, CqlLibraryReview review, String username) {
    CqlLibraryReview existing =
        cqlLibraryReviewRepository
            .findByLibraryId(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("CQL Library Review", libraryId));

    final ReviewStatus previousStatus = existing.getStatus();
    final ReviewStatus newStatus = review.getStatus();

    existing.setStatus(newStatus);
    existing.setComment(review.getComment());

    CqlLibraryReview saved = cqlLibraryReviewRepository.save(existing);
    log.info("Updated review [{}] for CQL Library [{}]", saved.getId(), libraryId);

    // Only log a history event when the review status actually changed. A no-op status (e.g. a
    // comment-only update) should not add READY_FOR_REVIEW / NOT_READY_FOR_REVIEW history records.
    if (newStatus != null && newStatus != previousStatus) {
      logReviewAction(libraryId, newStatus, username);
    } else {
      log.info(
          "Review status unchanged [{}] for CQL Library [{}]; skipping history log",
          newStatus,
          libraryId);
    }
    return saved;
  }

  /**
   * Retrieves the single review associated with the given libraryId.
   *
   * @param libraryId the library id
   * @return the review for the library
   */
  public CqlLibraryReview getReviewByLibraryId(String libraryId) {
    return cqlLibraryReviewRepository
        .findByLibraryId(libraryId)
        .orElseThrow(() -> new ResourceNotFoundException("CQL Library Review", libraryId));
  }

  /**
   * Retrieves all reviews for libraries belonging to the given librarySetId. librarySetIds are not
   * unique across libraries, so this may return multiple reviews.
   *
   * @param librarySetId the library set id
   * @return the list of reviews under the library set
   */
  public List<CqlLibraryReview> getReviewsByLibrarySetId(String librarySetId) {
    return cqlLibraryReviewRepository.findAllByLibrarySetId(librarySetId);
  }

  /**
   * Retrieves the full, enriched list of libraries that are currently marked as ready for review
   * (i.e. review status is READY_FOR_REVIEW). This method owns the review concerns: it verifies the
   * caller has the MADiE-Reviewer role and gathers the libraries currently in review, then
   * delegates to {@link CqlLibraryService#getReviewLibraries} to fetch and enrich the libraries.
   * The full list is returned (no pagination); ordering is left to the client.
   *
   * @param username the HARP id of the requesting user
   * @param accessToken the bearer token used to verify the reviewer role
   * @return the list of libraries marked as ready for review
   */
  public List<LibraryListDTO> getAllReadyForReview(String username, String accessToken) {
    cqlLibraryAccessControlService.verifyReviewerAccess(username, accessToken);

    Map<String, ReviewStatus> statusByLibraryId =
        cqlLibraryReviewRepository.findAllByStatus(ReviewStatus.READY_FOR_REVIEW).stream()
            .filter(review -> review.getLibraryId() != null)
            .collect(
                Collectors.toMap(
                    CqlLibraryReview::getLibraryId,
                    CqlLibraryReview::getStatus,
                    (existing, duplicate) -> existing));

    return cqlLibraryService.getReviewLibraries(statusByLibraryId);
  }

  /**
   * Logs a review status change against the library instance (not the library set) history. When
   * the "Mark as Ready" toggle is ON the status is READY_FOR_REVIEW; when OFF it is
   * NOT_READY_FOR_REVIEW. No additional info is recorded.
   *
   * @param libraryId the library instance id the event is logged against
   * @param status the review status driving the event type
   * @param username the HARP id / name of the user performing the action
   */
  private void logReviewAction(String libraryId, ReviewStatus status, String username) {
    if (status == null) {
      return;
    }
    ActionType actionType =
        ReviewStatus.READY_FOR_REVIEW.equals(status)
            ? ActionType.READY_FOR_REVIEW
            : ActionType.NOT_READY_FOR_REVIEW;
    actionLogService.logAction(libraryId, actionType, username, LIBRARY_HISTORY_COLLECTION);
  }
}
