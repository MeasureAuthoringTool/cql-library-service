package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryActionLogRepository;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.ActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;

@Slf4j
@RequiredArgsConstructor
@Service
public class ActionLogService {

  private final CqlLibraryActionLogRepository cqlLibraryHistoryRepository;

  public boolean logAction(
      final String targetId, final ActionType actionType, final String userId, final String... additionalActionMessage) {
    return cqlLibraryHistoryRepository.pushEvent(
        targetId,
        Action.builder()
            .actionType(actionType)
            .performedBy(userId)
            .performedAt(Instant.now())
            .additionalActionMessage(Arrays.toString(additionalActionMessage))
            .build());
  }

  public boolean logAccessControlAction(
      final String targetId, final ActionType actionType, final String userId, final String sharedWith, final String... additionalActionMessage) {
    return cqlLibraryHistoryRepository.pushEvent(
        targetId,
        AccessControlAction.builder()
            .actionType(actionType)
            .performedBy(userId)
            .performedAt(Instant.now())
            .sharedWith(sharedWith)
            .additionalActionMessage(Arrays.toString(additionalActionMessage))
            .build());
  }
}
