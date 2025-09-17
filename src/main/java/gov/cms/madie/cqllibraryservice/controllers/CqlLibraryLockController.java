package gov.cms.madie.cqllibraryservice.controllers;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gov.cms.madie.cqllibraryservice.dto.LockInfo;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryLockService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/cql-libraries")
@RequiredArgsConstructor
public class CqlLibraryLockController {
  private final CqlLibraryLockService cqlLibraryLockService;

  @PutMapping("/{libraryId}/lock")
  public ResponseEntity<LockInfo> addCqlLibraryLock(
      @PathVariable String libraryId, Principal principal) {
    log.info("User: " + principal.getName() + " lock library: " + libraryId);
    return ResponseEntity.ok(cqlLibraryLockService.lockCqlLibrary(libraryId, principal.getName()));
  }

  @DeleteMapping("/{libraryId}/lock")
  public ResponseEntity<LockInfo> unlockCqlLibrary(
      @PathVariable String libraryId, Principal principal) {
    log.info("User: " + principal.getName() + " unlock library: " + libraryId);
    return ResponseEntity.ok(
        cqlLibraryLockService.unlockCqlLibrary(libraryId, principal.getName()));
  }

  @DeleteMapping("/unlock")
  public ResponseEntity<List<String>> unlockAll(HttpServletRequest request, Principal principal) {
    final String username = principal.getName();
    log.info("Unlock libraries for user: " + username);
    List<String> messages = new ArrayList<>();
    messages.addAll(cqlLibraryLockService.unlockByUser(username));
    return ResponseEntity.ok(messages);
  }
}
