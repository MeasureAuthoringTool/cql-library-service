package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.LibraryLock;
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

    public synchronized boolean lockLibrary(String libraryId, String userName) {
        Optional<LibraryLock> existingLock = cqlLibraryLockRepository.findByLibraryId(libraryId);
        if (existingLock.isPresent()) {
            // If already locked by same user, allow
            if (existingLock.get().getLockedBy().equals(userName)) return true;
            return false; // Already locked by someone else
        }

        LibraryLock lock = new LibraryLock();
        lock.setLibraryId(libraryId);
        lock.setLockedBy(userName);
        lock.setLockedAt(Instant.now());
        cqlLibraryLockRepository.save(lock);
        return true;
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
