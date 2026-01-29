package gov.cms.madie.cqllibraryservice.controllers;

import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gov.cms.madie.cqllibraryservice.services.CqlLibraryLockService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/cql-libraries")
@RequiredArgsConstructor
public class CqlLibraryAdminController {

  private final CqlLibraryLockService cqlLibraryLockService;

  @DeleteMapping("/admin/locks")
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<List<String>> unlockAllByUser(
      HttpServletRequest request,
      @Value("${admin-api-key}") String apiKey,
      @RequestHeader(name = "harpId") String harpId,
      Principal principal) {
    log.info("Unlock all libraries for the user: " + harpId);
    List<String> messages = cqlLibraryLockService.unlockByUser(harpId.toLowerCase());
    return ResponseEntity.ok(messages);
  }
}
