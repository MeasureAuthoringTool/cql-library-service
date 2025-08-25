package gov.cms.madie.cqllibraryservice.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;

import gov.cms.madie.cqllibraryservice.dto.LockInfo;
import gov.cms.madie.cqllibraryservice.exceptions.DuplicateKeyException;
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

  public synchronized LockInfo unlockCqlLibrary(String libraryId, String userName) {
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
}
