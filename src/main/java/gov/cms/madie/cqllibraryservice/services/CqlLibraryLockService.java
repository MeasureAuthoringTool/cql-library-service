package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.LibraryLock;
import gov.cms.madie.cqllibraryservice.exceptions.DuplicateKeyException;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryLockRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@AllArgsConstructor
public class CqlLibraryLockService {
    private CqlLibraryLockRepository cqlLibraryLockRepository;

    public boolean lockLibrary(String libraryId, String userName) {
        LibraryLock lock = new LibraryLock();
        lock.setLibraryId(libraryId);
        lock.setLockedBy(userName);
        lock.setLockedAt(Instant.now());

        try {
            cqlLibraryLockRepository.insert(lock);
            return false;
        } catch (DuplicateKeyException ex) {
            Optional<LibraryLock> existingLock = cqlLibraryLockRepository.findByLibraryId(libraryId);

            if (existingLock.isPresent()) {
                String lockedBy = existingLock.get().getLockedBy();
                if (lockedBy.equals(userName)) {
                    return false;
                } else {
                    return true;
                }
            }
            return true;
        }
    }


    public synchronized boolean unlockLibrary(String libraryId, String userName) {
        Optional<LibraryLock> existingLock = cqlLibraryLockRepository.findByLibraryId(libraryId);
        if (existingLock.isPresent() && existingLock.get().getLockedBy().equals(userName)) {
            cqlLibraryLockRepository.deleteByLibraryId(libraryId);
            return true;
        }
        return false;
    }

    public Optional<LibraryLock> getLockStatus(String libraryId) {
        return cqlLibraryLockRepository.findByLibraryId(libraryId);
    }
}
