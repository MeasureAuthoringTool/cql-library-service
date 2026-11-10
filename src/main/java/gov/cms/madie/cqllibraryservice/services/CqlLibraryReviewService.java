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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class CqlLibraryReviewService {

  private static final String LIBRARY_HISTORY_COLLECTION = "actionLog";

  private static final String REVIEW_ACTION_LOG_COLLECTION = "libraryReviewActionLog";
  private static final String RELATED_COMMENT_MESSAGE_PREFIX = "relatedCommentId: ";
  // second entry on a reply's logged message, naming the comment it replies to
  private static final String PARENT_COMMENT_MESSAGE_PREFIX = "parentCommentId: ";

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
    assignIdsToNewComments(review.getComments());
    boolean isReadyForReviewOnCreate = review.getStatus() == ReviewStatus.READY_FOR_REVIEW;
    review.setMarkedForReviewBy(isReadyForReviewOnCreate ? username : null);
    review.setMarkedForReviewAt(isReadyForReviewOnCreate ? Instant.now() : null);
    CqlLibraryReview saved = cqlLibraryReviewRepository.save(review);
    log.info("Created review [{}] for CQL Library [{}]", saved.getId(), libraryId);
    logReviewAction(libraryId, saved.getStatus(), username);
    List<CommentChange> addedCommentChanges =
        ListUtils.emptyIfNull(saved.getComments()).stream()
            .map(comment -> new CommentChange(ActionType.COMMENT_ADDED, comment, null))
            .toList();
    logCommentActions(libraryId, addedCommentChanges, username);
    return withMarkedForReviewByDisplayName(saved);
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
        review.getComments() != null
            ? mergeUserChangesIntoStoredComments(
                existing.getComments(), review.getComments(), username)
            : existing.getComments();
    List<CommentChange> commentChanges =
        findChangedCommentsAndReplies(existing.getComments(), comments);
    assignIdsToNewComments(comments);
    existing.setComments(comments);
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
    return withMarkedForReviewByDisplayName(saved);
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
    return withMarkedForReviewByDisplayName(review);
  }

  private CqlLibraryReview withMarkedForReviewByDisplayName(CqlLibraryReview review) {
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

  private void assignIdsToNewComments(List<Comment> comments) {
    if (comments != null) {
      comments.stream()
          .filter(comment -> StringUtils.isBlank(comment.getId()))
          .forEach(comment -> comment.setId(ObjectId.get().toString()));
      comments.stream()
          .map(CqlLibraryReviewService::getRepliesOrEmpty)
          .flatMap(List::stream)
          .filter(reply -> StringUtils.isBlank(reply.getId()))
          .forEach(reply -> reply.setId(ObjectId.get().toString()));
    }
  }

  /**
   * Builds the comment list to save from the stored comments and the user's update. The update can
   * come from a page that was loaded before other users made changes, so it is only trusted for the
   * comments and replies the user wrote; everyone else's stay exactly as stored.
   *
   * <ul>
   *   <li>A stored entry that is missing from the update is deleted only if the user wrote it.
   *   <li>A stored entry whose text changed in the update takes the new text only if the user wrote
   *       it.
   *   <li>An entry in the update whose id is no longer stored (deleted meanwhile) is dropped.
   *   <li>An entry in the update without an id is new and is stamped with the user's HARP id.
   * </ul>
   *
   * @param storedEntries the comments (or one comment's replies) currently saved
   * @param incomingEntries the comments (or replies) sent in the update
   * @param username the HARP id of the user saving the update
   * @return the entries to save
   */
  private static List<Comment> mergeUserChangesIntoStoredComments(
      List<Comment> storedEntries, List<Comment> incomingEntries, String username) {
    if (storedEntries == null && incomingEntries == null) {
      return null;
    }
    Map<String, Comment> incomingEntriesById =
        mapCommentsById(ListUtils.emptyIfNull(incomingEntries));
    List<Comment> mergedEntries = new ArrayList<>();

    for (Comment storedEntry : ListUtils.emptyIfNull(storedEntries)) {
      Comment incomingEntry = incomingEntriesById.get(storedEntry.getId());
      boolean writtenByUser = isWrittenByUser(storedEntry, username);
      if (incomingEntry == null) {
        if (!writtenByUser) {
          mergedEntries.add(storedEntry);
        }
        continue;
      }
      Comment versionToKeep = writtenByUser ? incomingEntry : storedEntry;
      mergedEntries.add(
          versionToKeep.toBuilder()
              .harpId(storedEntry.getHarpId())
              .replies(
                  mergeUserChangesIntoStoredComments(
                      storedEntry.getReplies(), incomingEntry.getReplies(), username))
              .build());
    }

    ListUtils.emptyIfNull(incomingEntries).stream()
        .filter(entry -> StringUtils.isBlank(entry.getId()))
        .map(newEntry -> newEntry.toBuilder().harpId(username).build())
        .forEach(mergedEntries::add);
    return mergedEntries;
  }

  private static boolean isWrittenByUser(Comment entry, String username) {
    String author =
        StringUtils.isNotBlank(entry.getHarpId()) ? entry.getHarpId() : entry.getAuthorName();
    return StringUtils.equalsIgnoreCase(author, username);
  }

  /**
   * Finds every comment and reply that was added, edited or deleted between the stored review and
   * the incoming update. Replies are only compared for comments that still exist; when a comment is
   * deleted its replies go with it and only the comment deletion is reported.
   */
  private static List<CommentChange> findChangedCommentsAndReplies(
      List<Comment> storedComments, List<Comment> incomingComments) {
    List<Comment> storedCommentsOrEmpty = ListUtils.emptyIfNull(storedComments);
    List<Comment> incomingCommentsOrEmpty = ListUtils.emptyIfNull(incomingComments);
    Map<String, Comment> storedCommentsById = mapCommentsById(storedCommentsOrEmpty);

    List<CommentChange> collectedChanges = new ArrayList<>();
    collectAddedEditedAndDeletedEntries(
        storedCommentsOrEmpty, incomingCommentsOrEmpty, null, collectedChanges);
    incomingCommentsOrEmpty.forEach(
        incomingComment -> {
          Comment storedVersionOfComment =
              StringUtils.isBlank(incomingComment.getId())
                  ? null
                  : storedCommentsById.get(incomingComment.getId());
          collectAddedEditedAndDeletedEntries(
              getRepliesOrEmpty(storedVersionOfComment),
              getRepliesOrEmpty(incomingComment),
              incomingComment,
              collectedChanges);
        });
    return collectedChanges;
  }

  private static List<Comment> getRepliesOrEmpty(Comment comment) {
    return comment == null ? List.of() : ListUtils.emptyIfNull(comment.getReplies());
  }

  private static Map<String, Comment> mapCommentsById(List<Comment> comments) {
    return comments.stream()
        .filter(comment -> comment.getId() != null)
        .collect(
            Collectors.toMap(Comment::getId, Function.identity(), (first, duplicate) -> first));
  }

  /**
   * Compares one level of entries, either the top-level comments or the replies of one comment, and
   * adds an added / edited / deleted change for each entry that differs.
   *
   * @param storedEntries the entries currently saved
   * @param incomingEntries the entries sent in the update
   * @param parentComment the comment the entries reply to, or null for top-level comments
   * @param collectedChanges the list the changes are added to
   */
  private static void collectAddedEditedAndDeletedEntries(
      List<Comment> storedEntries,
      List<Comment> incomingEntries,
      Comment parentComment,
      List<CommentChange> collectedChanges) {
    Map<String, Comment> storedEntriesById = mapCommentsById(storedEntries);
    Set<String> incomingEntryIds =
        incomingEntries.stream()
            .map(Comment::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    List<Comment> addedEntries =
        incomingEntries.stream().filter(entry -> StringUtils.isBlank(entry.getId())).toList();
    List<Comment> editedEntries =
        incomingEntries.stream()
            .filter(entry -> entry.getId() != null && storedEntriesById.containsKey(entry.getId()))
            .filter(
                entry ->
                    !Objects.equals(
                        entry.getContent(), storedEntriesById.get(entry.getId()).getContent()))
            .toList();
    List<Comment> deletedEntries =
        storedEntries.stream()
            .filter(entry -> entry.getId() != null && !incomingEntryIds.contains(entry.getId()))
            .toList();

    addedEntries.forEach(
        entry ->
            collectedChanges.add(
                new CommentChange(ActionType.COMMENT_ADDED, entry, parentComment)));
    editedEntries.forEach(
        entry ->
            collectedChanges.add(
                new CommentChange(ActionType.COMMENT_EDITED, entry, parentComment)));
    deletedEntries.forEach(
        entry ->
            collectedChanges.add(
                new CommentChange(ActionType.COMMENT_DELETED, entry, parentComment)));
  }

  private void logCommentActions(
      String libraryId, List<CommentChange> commentChanges, String username) {
    commentChanges.forEach(
        commentChange ->
            actionLogService.logAction(
                libraryId,
                commentChange.actionType(),
                username,
                REVIEW_ACTION_LOG_COLLECTION,
                buildLogMessages(commentChange)));
  }

  private static String[] buildLogMessages(CommentChange commentChange) {
    String changedCommentMessage = relatedCommentLogMessage(commentChange.changedComment());
    return commentChange.parentComment() == null
        ? new String[] {changedCommentMessage}
        : new String[] {
          changedCommentMessage,
          PARENT_COMMENT_MESSAGE_PREFIX + commentChange.parentComment().getId()
        };
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

  // one comment or reply to log; parentComment is set only when the changed entry is a reply
  private record CommentChange(
      ActionType actionType, Comment changedComment, Comment parentComment) {}
}
