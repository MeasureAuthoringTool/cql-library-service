package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryActionLogRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetActionLogRepository;
import gov.cms.madie.models.common.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class ActionLogService {

  private final CqlLibraryActionLogRepository cqlLibraryHistoryRepository;
  private final LibrarySetActionLogRepository librarySetActionLogRepository;

  public boolean logAction(
      final String targetId,
      final ActionType actionType,
      final String userId,
      final String collection,
      final String... additionalActionMessage) {
    return cqlLibraryHistoryRepository.pushEvent(
        targetId,
        Action.builder()
            .actionType(actionType)
            .performedBy(userId)
            .performedAt(Instant.now())
            .additionalActionMessage(
                additionalActionMessage != null ? String.join(", ", additionalActionMessage) : "")
            .build(),
        collection);
  }

  // logs only: share and unshare of the library
  public boolean logShareAccessControlAction(
      final String targetId,
      final ActionType actionType,
      final String performedBy,
      final String sharedWith,
      final String... additionalActionMessage) {
    return librarySetActionLogRepository.pushEvent(
        targetId,
        AccessControlAction.builder()
            .actionType(actionType)
            .performedBy(performedBy)
            .performedAt(Instant.now())
            .sharedWith(sharedWith)
            .additionalActionMessage(
                additionalActionMessage != null ? String.join(", ", additionalActionMessage) : "")
            .build());
  }

  public LibrarySetActionLog findLibrarySetActionLogByTargetId(final String targetId) {
    return librarySetActionLogRepository.findByTargetId(targetId).orElse(null);
  }

  public List<Action> findCqlLibraryHistory(String cqlLibraryId, String librarySetId) {
    Optional<LibraryActionLog> libraryActionLogs =
        cqlLibraryHistoryRepository.findByTargetId(cqlLibraryId);
    Optional<LibrarySetActionLog> librarySetActionLogs =
        librarySetActionLogRepository.findByTargetId(librarySetId);

    List<Action> combinedActionLogs = new ArrayList<>();

    libraryActionLogs.ifPresent(
        log -> {
          if (CollectionUtils.isNotEmpty(log.getActions())) {
            combinedActionLogs.addAll(log.getActions());
          }
        });

    librarySetActionLogs.ifPresent(
        log -> {
          if (CollectionUtils.isNotEmpty(log.getActions())) {
            combinedActionLogs.addAll(
                log.getActions().stream()
                    .filter(action -> action.getActionType() != ActionType.CREATED)
                    .toList());
          }
        });

    combinedActionLogs.sort((a, b) -> b.getPerformedAt().compareTo(a.getPerformedAt()));
    return combinedActionLogs;
  }
}
