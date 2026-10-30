package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.exceptions.GeneralConflictException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryReviewRepository;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.Comment;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.common.ReviewStatus;
import gov.cms.madie.models.library.CqlLibraryReview;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class CqlLibraryReviewService {

  private static final String LIBRARY_HISTORY_COLLECTION = "actionLog";

  private static final String REVIEW_ACTION_LOG_COLLECTION = "libraryReviewActionLog";
  private static final String RELATED_COMMENT_MESSAGE_PREFIX = "relatedCommentId: ";

  private static final List<ReviewStatus> IN_REVIEW_STATUSES =
      List.of(ReviewStatus.READY_FOR_REVIEW, ReviewStatus.IN_PROGRESS, ReviewStatus.COMPLETE);
  private static final List<ReviewStatus> MY_REVIEW_STATUSES =
      List.of(ReviewStatus.READY_FOR_REVIEW, ReviewStatus.IN_PROGRESS);

  private final CqlLibraryReviewRepository cqlLibraryReviewRepository;
  private final ActionLogService actionLogService;
  private final CqlLibraryService cqlLibraryService;
  private final CqlLibraryAccessControlService cqlLibraryAccessControlService;
  private final UserNameService userNameService;

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
    review.setComments(assignIdsToNewComments(review.getComments()));
    boolean isReadyForReviewOnCreate = review.getStatus() == ReviewStatus.READY_FOR_REVIEW;
    review.setMarkedForReviewBy(isReadyForReviewOnCreate ? username : null);
    review.setMarkedForReviewAt(isReadyForReviewOnCreate ? Instant.now() : null);
    CqlLibraryReview saved = cqlLibraryReviewRepository.save(review);
    log.info("Created review [{}] for CQL Library [{}]", saved.getId(), libraryId);
    logReviewAction(libraryId, saved.getStatus(), username);
    List<Comment> addedComments = Objects.requireNonNullElse(saved.getComments(), List.of());
    logCommentActions(libraryId, Map.of(ActionType.COMMENT_ADDED, addedComments), username);
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

    if (newStatus != null) {
      existing.setStatus(newStatus);
    }

    List<Comment> comments =
        review.getComments() != null ? review.getComments() : existing.getComments();
    Map<ActionType, List<Comment>> commentChanges = diffComments(existing.getComments(), comments);
    existing.setComments(assignIdsToNewComments(comments));
    existing.setReviewers(review.getReviewers());
    applyMarkedForReviewStamp(existing, previousStatus, newStatus, username);

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
    logCommentActions(libraryId, commentChanges, username);
    return saved;
  }

  /**
   * Retrieves the single review associated with the given libraryId.
   *
   * @param libraryId the library id
   * @return the review for the library
   */
  public CqlLibraryReview getReviewByLibraryId(String libraryId) {
    CqlLibraryReview review =
        cqlLibraryReviewRepository
            .findByLibraryId(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("CQL Library Review", libraryId));
    review.setMarkedForReviewBy(userNameService.getDisplayName(review.getMarkedForReviewBy()));
    return review;
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
   * Retrieves the full, enriched list of libraries that are currently under review, i.e. whose
   * review status is Ready, In Progress or Complete. This method owns the review concerns: it
   * verifies the caller has the MADiE-Reviewer role and gathers the libraries currently in review,
   * then delegates to {@link CqlLibraryService#getReviewLibraries} to fetch and enrich the
   * libraries. The full list is returned (no pagination); ordering is left to the client.
   *
   * @param username the HARP id of the requesting user
   * @param accessToken the bearer token used to verify the reviewer role
   * @return the list of libraries under review
   */
  public List<LibraryListDTO> getLibrariesInReview(
      String username, String accessToken, OwnershipType ownershipType) {
    cqlLibraryAccessControlService.verifyReviewerAccess(username, accessToken);

    Map<String, CqlLibraryReview> reviewByLibraryId = new HashMap<>();
    if (ownershipType.equals(OwnershipType.OWNED)) {
      reviewByLibraryId =
          cqlLibraryReviewRepository
              .findAllByStatusInAndReviewersContaining(MY_REVIEW_STATUSES, username)
              .stream()
              .filter(review -> review.getLibraryId() != null)
              .collect(
                  Collectors.toMap(
                      CqlLibraryReview::getLibraryId,
                      review -> review,
                      (existing, duplicate) -> existing));
    } else {

      reviewByLibraryId =
          cqlLibraryReviewRepository.findAllByStatusIn(IN_REVIEW_STATUSES).stream()
              .filter(review -> review.getLibraryId() != null)
              .collect(
                  Collectors.toMap(
                      CqlLibraryReview::getLibraryId,
                      review -> review,
                      (existing, duplicate) -> existing));
    }

    return cqlLibraryService.getReviewLibraries(reviewByLibraryId);
  }

  private static List<Comment> assignIdsToNewComments(List<Comment> comments) {
    if (comments != null) {
      comments.stream()
          .filter(comment -> comment.getId() == null || comment.getId().isBlank())
          .forEach(comment -> comment.setId(ObjectId.get().toString()));
    }
    return comments;
  }

  // comments that changed between the stored and incoming review, keyed by the action to log
  private static Map<ActionType, List<Comment>> diffComments(
      List<Comment> storedComments, List<Comment> incomingComments) {
    List<Comment> stored = storedComments == null ? List.of() : storedComments;
    List<Comment> incoming = incomingComments == null ? List.of() : incomingComments;
    Map<String, Comment> storedCommentsById =
        stored.stream()
            .filter(comment -> comment.getId() != null)
            .collect(Collectors.toMap(Comment::getId, Function.identity(), (a, b) -> a));
    Set<String> incomingCommentIds =
        incoming.stream().map(Comment::getId).filter(Objects::nonNull).collect(Collectors.toSet());

    List<Comment> addedComments =
        incoming.stream()
            .filter(comment -> comment.getId() == null || comment.getId().isBlank())
            .toList();
    List<Comment> editedComments =
        incoming.stream()
            .filter(
                comment ->
                    comment.getId() != null && storedCommentsById.containsKey(comment.getId()))
            .filter(
                comment ->
                    !Objects.equals(
                        comment.getContent(), storedCommentsById.get(comment.getId()).getContent()))
            .toList();
    List<Comment> deletedComments =
        stored.stream()
            .filter(
                comment -> comment.getId() != null && !incomingCommentIds.contains(comment.getId()))
            .toList();
    return Map.of(
        ActionType.COMMENT_ADDED, addedComments,
        ActionType.COMMENT_EDITED, editedComments,
        ActionType.COMMENT_DELETED, deletedComments);
  }

  private void logCommentActions(
      String libraryId, Map<ActionType, List<Comment>> commentsByAction, String username) {
    commentsByAction.forEach(
        (actionType, comments) ->
            comments.forEach(
                comment ->
                    actionLogService.logAction(
                        libraryId,
                        actionType,
                        username,
                        REVIEW_ACTION_LOG_COLLECTION,
                        relatedCommentLogMessage(comment))));
  }

  static String relatedCommentLogMessage(Comment comment) {
    return RELATED_COMMENT_MESSAGE_PREFIX + comment.getId();
  }

  private void applyMarkedForReviewStamp(
      CqlLibraryReview review,
      ReviewStatus previousStatus,
      ReviewStatus newStatus,
      String username) {
    if (newStatus == null || newStatus == previousStatus) {
      return;
    }
    if (newStatus == ReviewStatus.NOT_READY_FOR_REVIEW) {
      review.setMarkedForReviewBy(null);
      review.setMarkedForReviewAt(null);
    } else if (newStatus == ReviewStatus.READY_FOR_REVIEW
        && review.getMarkedForReviewAt() == null) {
      review.setMarkedForReviewBy(username);
      review.setMarkedForReviewAt(Instant.now());
    }
  }

  /**
   * Logs a review status change against the library instance (not the library set) history. Each
   * status logs its own event: READY_FOR_REVIEW, REVIEW_IN_PROGRESS, REVIEW_COMPLETE, or
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
    actionLogService.logAction(
        libraryId, status.toActionType(), username, LIBRARY_HISTORY_COLLECTION);
  }
}
