package gov.cms.madie.cqllibraryservice.controllers;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gov.cms.madie.cqllibraryservice.locks.CqlLibraryLock;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryLockService;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/cql-libraries")
@RequiredArgsConstructor
public class CqlLibraryAdminController {

  private final CqlLibraryLockService cqlLibraryLockService;
  private final CqlLibraryService cqlLibraryService;

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

  /**
   * Handles transfer of multiple libraries to a new owner (identified by harpId).
   *
   * <p>Validates the input list of library IDs. Delegates transfer logic to CqlLibraryService,
   * which attempts to reassign each library. Returns:
   *
   * <ul>
   *   <li>200 OK if all transfers succeed and returns the success library IDs
   *   <li>400 BAD REQUEST if the input list is empty.
   *   <li>207 MULTI_STATUS if some transfers fail, returning only the failed library IDs in the
   *       body.
   * </ul>
   */
  @PutMapping(
      value = "/admin/ownership",
      produces = {MediaType.APPLICATION_JSON_VALUE})
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<List<String>> changeOwnership(
      HttpServletRequest request,
      @Value("${admin-api-key}") String apiKey,
      @RequestBody List<String> cqlLibraryIds,
      @RequestParam(name = "harpId") String harpId,
      @RequestParam(defaultValue = "false") boolean retainShareAccess,
      Principal principal) {
    log.info(
        "User [{}] - Starting admin task [changeOwnership] to [{}] for cqlLibraryIds: [{}]",
        principal.getName(),
        harpId,
        cqlLibraryIds);

    if (CollectionUtils.isEmpty(cqlLibraryIds)) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.emptyList());
    }

    List<String> validLibraryIds = new ArrayList<>();
    List<String> failedTransfers = new ArrayList<>();
    // Check lock and filter out the locked ones
    cqlLibraryIds.forEach(
        cqlLibraryId -> {
          CqlLibraryLock libraryLock = cqlLibraryLockService.findByCqlLibraryId(cqlLibraryId);
          if (libraryLock == null) {
            validLibraryIds.add(cqlLibraryId);
          } else {
            failedTransfers.add(cqlLibraryId);
          }
        });

    if (CollectionUtils.isNotEmpty(validLibraryIds)) {
      failedTransfers.addAll(
          cqlLibraryService.transferLibraries(validLibraryIds, harpId, retainShareAccess, "admin"));
    }
    List<String> successLibraryIds =
        cqlLibraryIds.stream().filter(libraryId -> !failedTransfers.contains(libraryId)).toList();

    if (CollectionUtils.isEmpty(failedTransfers)) {
      log.info("Successful libraryIds: [{}]", successLibraryIds);
      return ResponseEntity.ok().body(successLibraryIds);
    } else {
      log.info("Failed transfer measureIds: [{}]", failedTransfers);
      return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(failedTransfers);
    }
  }
}
