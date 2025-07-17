package gov.cms.madie.cqllibraryservice.controllers;

import gov.cms.madie.cqllibraryservice.dto.LockResponse;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CqlLibraryLockController {
    private final CqlLibraryLockService cqlLibraryLockService;
    @PostMapping("/lock/{libraryId}")
    public ResponseEntity<LockResponse> lockLibrary(@PathVariable String libraryId, Principal principal) {
        final String userName = principal.getName();
        boolean locked = cqlLibraryLockService.lockLibrary(libraryId, userName);
        if (locked) {
            return ResponseEntity.ok(new LockResponse(false, userName));
        } else {
            return cqlLibraryLockService.getLockStatus(libraryId)
                    .map(lock -> ResponseEntity.status(423)
                            .body(new LockResponse(true, lock.getLockedBy())))
                    .orElse(ResponseEntity.status(500).body(new LockResponse(true, null)));
        }
    }

    @PostMapping("/unlock/{libraryId}")
    public ResponseEntity<?> unlockLibrary(@PathVariable String libraryId, Principal principal) {
        final String userName = principal.getName();
        boolean unlocked = cqlLibraryLockService.unlockLibrary(libraryId, userName);
        if (unlocked) {
            return ResponseEntity.ok("Library unlocked successfully.");
        } else {
            return ResponseEntity.status(403).body("Unlock failed. You do not hold the lock.");
        }
    }

    @GetMapping("/status/{libraryId}")
    public ResponseEntity<LockResponse> getLockStatus(@PathVariable String libraryId) {
        return cqlLibraryLockService.getLockStatus(libraryId)
                .map(lock -> ResponseEntity.ok(new LockResponse(true, lock.getLockedBy())))
                .orElse(ResponseEntity.ok(new LockResponse(false, null)));
    }
}
