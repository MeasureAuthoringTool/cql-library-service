package gov.cms.madie.cqllibraryservice.controllers;

import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import gov.cms.madie.cqllibraryservice.dto.LockInfo;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CqlLibraryLockController {
  private final CqlLibraryLockService cqlLibraryLockService;

  @PostMapping("/libraries/{libraryId}/lock")
  public ResponseEntity<LockInfo> addCqlLibraryLock(
      @PathVariable String libraryId, Principal principal) {
    log.info("User: " + principal.getName() + " lock library: " + libraryId);
    return ResponseEntity.ok(cqlLibraryLockService.lockCqlLibrary(libraryId, principal.getName()));
  }

  @DeleteMapping("/libraries/{libraryId}/unlock")
  public ResponseEntity<LockInfo> unlockCqlLibrary(
      @PathVariable String libraryId, Principal principal) {
    log.info("User: " + principal.getName() + " unlock library: " + libraryId);
    return ResponseEntity.ok(
        cqlLibraryLockService.unlockCqlLibrary(libraryId, principal.getName()));
  }
}
