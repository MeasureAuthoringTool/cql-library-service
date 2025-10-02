package gov.cms.madie.cqllibraryservice.services;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.nimbusds.oauth2.sdk.util.CollectionUtils;

import gov.cms.madie.cqllibraryservice.dto.LockInfo;
import gov.cms.madie.cqllibraryservice.locks.CqlLibraryLock;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CqlLibraryLockService {
  private final CqlLibraryLockRepository cqlLibraryLockRepository;

  public synchronized LockInfo lockCqlLibrary(String libraryId, String userName) {
    LockInfo lockInfo = null;
    Instant now = Instant.now();
    Instant expiresAt = now.plus(Duration.ofMinutes(15));

    CqlLibraryLock lock =
        CqlLibraryLock.builder()
            .cqlLibraryId(libraryId)
            .lockedBy(userName)
            .lockedAt(Instant.now())
            .expiresAt(expiresAt)
            .build();

    try {
      cqlLibraryLockRepository.insert(lock);
      lockInfo = LockInfo.builder().lockedId(libraryId).isLocked(true).lockedBy(userName).build();
    } catch (DuplicateKeyException ex) {
      log.error("DuplicateKeyException for libraryId: " + libraryId + " userName: " + userName);
      Optional<CqlLibraryLock> existingLock =
          cqlLibraryLockRepository.findByCqlLibraryId(libraryId);
      if (existingLock.isPresent()) {
        lockInfo =
            LockInfo.builder()
                .lockedId(libraryId)
                .isLocked(true)
                .lockedBy(existingLock.get().getLockedBy())
                .build();
      }
    }
    return lockInfo;
  }

  public LockInfo unlockCqlLibrary(String libraryId, String userName) {
    Optional<CqlLibraryLock> existingLock = cqlLibraryLockRepository.findByCqlLibraryId(libraryId);
    if (existingLock.isPresent()) {
      if (existingLock.get().getLockedBy().equals(userName)) {
        cqlLibraryLockRepository.deleteByCqlLibraryId(libraryId);
        return LockInfo.builder().isLocked(false).build();
      } else {
        log.info(
            "unlockCqlLibrary: existingLock not found for libraryId: "
                + libraryId
                + " userName: "
                + userName);
        return LockInfo.builder()
            .lockedId(existingLock.get().getCqlLibraryId())
            .isLocked(true)
            .lockedBy(existingLock.get().getLockedBy())
            .build();
      }
    }
    return null;
  }

  public List<String> unlockByUser(String userName) {
    List<String> deleteMessages = new ArrayList<>();
    deleteMessages.add("Delete library locks for harpId: " + userName);
    List<CqlLibraryLock> existingLocks = cqlLibraryLockRepository.findAllByLockedBy(userName);
    log.info(
        (CollectionUtils.isNotEmpty(existingLocks) ? existingLocks.size() : "No")
            + " library locks found for harpId: "
            + userName);
    if (CollectionUtils.isNotEmpty(existingLocks)) {
      existingLocks.forEach(
          existingLock -> {
            cqlLibraryLockRepository.deleteByCqlLibraryId(existingLock.getCqlLibraryId());
            deleteMessages.add("Deleted library lock for Id: " + existingLock.getCqlLibraryId());
          });
    } else {
      deleteMessages.add("No library locks found for harpId: " + userName);
    }
    return deleteMessages;
  }
}
