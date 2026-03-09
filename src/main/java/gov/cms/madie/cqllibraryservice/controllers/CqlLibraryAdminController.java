package gov.cms.madie.cqllibraryservice.controllers;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gov.cms.madie.cqllibraryservice.exceptions.HarpIdMismatchException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryLockService;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryService;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.library.CqlLibrary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/cql-libraries/admin")
@RequiredArgsConstructor
public class CqlLibraryAdminController {

  private final CqlLibraryLockService cqlLibraryLockService;
  private final CqlLibraryService cqlLibraryService;

  @DeleteMapping("/locks")
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<List<String>> unlockAllByUser(
      @RequestHeader(name = "harpId") String harpId, Principal principal) {
    log.info("Unlock all libraries for the user: " + harpId);
    List<String> messages = cqlLibraryLockService.unlockByUser(harpId.toLowerCase());
    return ResponseEntity.ok(messages);
  }

  @PutMapping("/{id}/acls")
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<List<AclSpecification>> updateAccessControl(
      @PathVariable String id, @RequestBody @Validated AclOperation aclOperation) {
    List<AclSpecification> aclSpecifications =
        cqlLibraryService.updateAccessControlList(id, aclOperation, "admin");
    return ResponseEntity.ok().body(aclSpecifications);
  }

  @GetMapping("/sharedWith")
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<List<Map<String, Object>>> getLibrarySharedWith(
      @RequestHeader(name = "harpId") String harpId,
      @RequestParam(name = "libraryids") String libraryids,
      Principal principal) {
    final String username = principal.getName().toLowerCase();
    List<Map<String, Object>> results = new ArrayList<>();
    String[] ids = StringUtils.split(libraryids, ",");
    for (String id : ids) {
      CqlLibrary library = cqlLibraryService.findCqlLibraryById(id, username);
      if (library != null) {
        if (!library.getLibrarySet().getOwner().equalsIgnoreCase(harpId)) {
          throw new HarpIdMismatchException(
              harpId, library.getLibrarySet().getOwner(), library.getId());
        }
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("libraryName", library.getCqlLibraryName());
        result.put("libraryId", library.getId());
        result.put("libraryOwner", library.getLibrarySet().getOwner());
        result.put("sharedWith", library.getLibrarySet().getAcls());
        results.add(result);
      } else {
        throw new ResourceNotFoundException(id);
      }
    }
    return ResponseEntity.ok(results);
  }

  @DeleteMapping("/{libraryName}/delete-all-versions")
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<String> deleteLibraryAlongWithVersions(
      @PathVariable String libraryName,
      @RequestHeader("Authorization") String accessToken,
      @RequestHeader(name = "harpId") String harpId) {
    cqlLibraryService.deleteLibraryAlongWithVersions(
        libraryName, accessToken, harpId.toLowerCase());
    return ResponseEntity.ok()
        .body("The library and all its associated versions have been removed successfully.");
  }
}
