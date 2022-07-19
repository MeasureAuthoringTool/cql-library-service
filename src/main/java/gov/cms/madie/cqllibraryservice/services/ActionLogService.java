package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryActionLogRepository;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.ActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
@Service
public class ActionLogService {

  private final CqlLibraryActionLogRepository cqlLibraryActionLogRepository;

  public boolean logAction(
      final String targetId, final ActionType actionType, final String userId) {
    return cqlLibraryActionLogRepository.pushEvent(
        targetId,
        Action.builder()
            .actionType(actionType)
            .performedBy(userId)
            .performedAt(Instant.now())
            .build());
  }
}
