package gov.cms.madie.cqllibraryservice.controllers;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import gov.cms.madie.cqllibraryservice.dto.DownloadedPackageResult;
import gov.cms.madie.cqllibraryservice.dto.IgPackageInstallRequest;
import gov.cms.madie.cqllibraryservice.services.AdminService;
import gov.cms.madie.cqllibraryservice.services.IgPackageService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.util.HtmlUtils;

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
  private final AdminService adminService;
  private final IgPackageService igPackageService;

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
      @PathVariable String id,
      @RequestBody @Validated AclOperation aclOperation,
      @RequestHeader("Authorization") String accessToken) {
    List<AclSpecification> aclSpecifications =
        cqlLibraryService.updateAccessControlList(id, aclOperation, "admin", true, accessToken);
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

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<CqlLibrary> deleteCqlLibraryById(
      Principal principal, @PathVariable String id, @RequestHeader(name = "harpId") String harpId) {
    CqlLibrary deleted =
        cqlLibraryService.deleteCqlLibraryById(
            id, harpId.toLowerCase(), principal.getName().toLowerCase());

    return ResponseEntity.ok(deleted);
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

  @PutMapping("/shared-access-report")
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<byte[]> exportSharedWith(
      Principal principal,
      @RequestBody List<String> libraryids,
      @RequestHeader("Authorization") String accessToken) {
    final String username = principal.getName().toLowerCase();
    log.info("User [{}] is attempting to export Library Access Report", username);

    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"LibrarySharingExport.xlsx\"")
        .header(
            HttpHeaders.CONTENT_TYPE,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        .body(adminService.exportSharedWithLibraries(libraryids, username, accessToken));
  }

  @PostMapping("/ig-packages")
  @PreAuthorize("hasRole('MADIE-ADMIN')")
  public ResponseEntity<DownloadedPackageResult> installIgPackage(
      Principal principal, @RequestBody @Validated IgPackageInstallRequest request) {
    final String username = principal.getName().toLowerCase();
    log.info(
        "Admin user [{}] is initiating IG package installation for package [{}] version [{}]",
        username,
        request.getPackageId(),
        request.getPackageVersion());
    String sanitizedPackageId = HtmlUtils.htmlEscape(request.getPackageId());
    String sanitizedPackageVersion = HtmlUtils.htmlEscape(request.getPackageVersion());
    DownloadedPackageResult result =
        igPackageService.installIgPackage(sanitizedPackageId, sanitizedPackageVersion, username);
    if (result.isSuccess()) {
      return ResponseEntity.ok(result);
    } else {
      return ResponseEntity.internalServerError().body(result);
    }
  }
}
