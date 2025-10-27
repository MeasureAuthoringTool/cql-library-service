package gov.cms.madie.cqllibraryservice.controllers;

import gov.cms.madie.cqllibraryservice.dto.LibrarySearchCriteria;
import gov.cms.madie.cqllibraryservice.dto.LibrarySetDTO;
import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.dto.SharedUser;
import gov.cms.madie.cqllibraryservice.exceptions.HarpIdMismatchException;
import gov.cms.madie.cqllibraryservice.exceptions.InvalidIdException;
import gov.cms.madie.cqllibraryservice.exceptions.InvalidResourceStateException;
import gov.cms.madie.cqllibraryservice.services.*;
import gov.cms.madie.cqllibraryservice.utils.AuthUtils;
import gov.cms.madie.cqllibraryservice.utils.LibraryUtils;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.OwnershipType;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.CqlLibraryDraft;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;

import java.security.Principal;
import java.time.Instant;
import java.util.*;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;

@Slf4j
@RestController
@RequestMapping("/cql-libraries")
@RequiredArgsConstructor
public class CqlLibraryController {

  private final CqlLibraryRepository cqlLibraryRepository;
  private final ActionLogService actionLogService;
  private final VersionService versionService;
  private final CqlLibraryService cqlLibraryService;
  private final LibrarySetService librarySetService;

  @PutMapping("/searches")
  public ResponseEntity<Page<LibraryListDTO>> fetchLibrariesByCriteria(
      Principal principal,
      @RequestParam(required = false, defaultValue = "ALL", name = "ownershipType")
          OwnershipType ownershipType,
      @RequestBody(required = false) LibrarySearchCriteria librarySearchCriteria,
      @RequestParam(required = false, defaultValue = "10", name = "limit") int limit,
      @RequestParam(required = false, defaultValue = "0", name = "page") int page,
      @RequestParam(required = false, name = "sortInfo") String sortInfo) {
    final String username = principal.getName();
    Pageable pageReq;
    // if sortInfo provided
    if (sortInfo != null && !sortInfo.trim().isEmpty()) {
      String[] sortParts = sortInfo.split(",");
      // sort parts correct length
      if (sortParts.length == 2) {
        String sortBy = sortParts[0];
        boolean desc = Boolean.parseBoolean(sortParts[1]);
        pageReq =
            PageRequest.of(
                page, limit, Sort.by(desc ? Sort.Order.desc(sortBy) : Sort.Order.asc(sortBy)));
      } else {
        // sortParts wrong length
        // in case we provide bad info we just do last modified
        pageReq = PageRequest.of(page, limit, Sort.by(Sort.Order.desc("lastModifiedAt")));
      }
      // default behavior no sort.
    } else {
      pageReq = PageRequest.of(page, limit, Sort.by(Sort.Order.desc("lastModifiedAt")));
    }
    Page<LibraryListDTO> cqlLibraries =
        cqlLibraryService.getLibrariesByCriteria(
            librarySearchCriteria, ownershipType, pageReq, username);
    return ResponseEntity.ok(cqlLibraries);
  }

  @GetMapping("/getAllOwners")
  public ResponseEntity<List<String>> getAllOwners(
      @RequestParam(name = "librarySetIds") List<String> librarySetIds) {
    List<String> results = librarySetService.getAllOwners(librarySetIds);
    log.info("results: {}", results);
    return ResponseEntity.status(HttpStatus.OK).body(results);
  }

  @GetMapping("/{id}")
  public ResponseEntity<CqlLibrary> getCqlLibrary(@PathVariable("id") String id) {
    return ResponseEntity.ok(cqlLibraryService.findCqlLibraryById(id));
  }

  @GetMapping("/versioned")
  public ResponseEntity<CqlLibrary> getVersionedCqlLibrary(
      @RequestParam String name,
      @RequestParam String version,
      @RequestParam Optional<String> model,
      @RequestParam(defaultValue = "true") boolean includeElm,
      @RequestParam(defaultValue = "Info") String elmErrorSeverity,
      @RequestHeader("Authorization") String accessToken) {
    return ResponseEntity.ok(
        cqlLibraryService.getVersionedCqlLibrary(
            name, version, model, includeElm, elmErrorSeverity, accessToken));
  }

  @GetMapping("/library-set/{setId}")
  public ResponseEntity<LibrarySetDTO> getLibrarySetBySetId(@PathVariable String setId) {
    return ResponseEntity.ok(cqlLibraryService.getLibrarySetBySetId(setId));
  }

  @PutMapping("/byLibrarySetId")
  public ResponseEntity<List<LibraryListDTO>> getLibrariesByLibrarySetId(
      @RequestParam(name = "librarySetId") String librarySetId,
      @RequestParam(defaultValue = "true") boolean sortByLatestVersion,
      @RequestBody(required = false) LibrarySearchCriteria librarySearchCriteria) {
    List<LibraryListDTO> cqlLibraries =
        cqlLibraryService.getLibrariesByLibrarySetId(
            librarySetId, sortByLatestVersion, librarySearchCriteria);
    return ResponseEntity.ok(cqlLibraries);
  }

  @PostMapping
  public ResponseEntity<CqlLibrary> createCqlLibrary(
      @Validated(CqlLibrary.ValidationSequence.class) @RequestBody CqlLibrary cqlLibrary,
      Principal principal) {
    final String username = principal.getName();
    log.info("User [{}] is attempting to create a new cql library", username);

    cqlLibraryService.checkDuplicateCqlLibraryName(cqlLibrary.getCqlLibraryName());

    // Clear ID so that the unique GUID from MongoDB will be applied
    Instant now = Instant.now();
    cqlLibrary.setId(null);
    cqlLibrary.setCreatedBy(username);
    cqlLibrary.setCreatedAt(now);
    cqlLibrary.setLastModifiedBy(username);
    cqlLibrary.setLastModifiedAt(now);
    cqlLibrary.setVersion(Version.parse("0.0.000"));
    cqlLibrary.setDraft(true);
    cqlLibrary.setLibrarySetId(UUID.randomUUID().toString());
    CqlLibrary savedCqlLibrary = cqlLibraryRepository.save(cqlLibrary);
    log.info(
        "User [{}] successfully created new cql library with ID [{}]",
        username,
        cqlLibrary.getId());
    actionLogService.logAction(savedCqlLibrary.getId(), ActionType.CREATED, username, "actionLog");

    librarySetService.createLibrarySet(
        username, savedCqlLibrary.getId(), savedCqlLibrary.getLibrarySetId());
    return ResponseEntity.status(HttpStatus.CREATED).body(savedCqlLibrary);
  }

  @PutMapping("/{id}")
  public ResponseEntity<CqlLibrary> updateCqlLibrary(
      @PathVariable("id") String id,
      @Validated(CqlLibrary.ValidationSequence.class) @RequestBody final CqlLibrary cqlLibrary,
      Principal principal) {
    final String username = principal.getName();

    if (id == null || id.isEmpty() || !id.equals(cqlLibrary.getId())) {
      log.info("got invalid id [{}] vs cqlLibraryId: [{}]", id, cqlLibrary.getId());
      throw new InvalidIdException("CQL Library", "Update (PUT)", "(PUT [base]/[resource]/[id])");
    }

    CqlLibrary persistedLibrary = cqlLibraryService.findCqlLibraryById(cqlLibrary.getId());
    AuthUtils.checkAccessPermissions(persistedLibrary, username);
    if (!persistedLibrary.isDraft()) {
      throw new InvalidResourceStateException("CQL Library", id);
    }
    if (cqlLibraryService.isCqlLibraryNameChanged(cqlLibrary, persistedLibrary)) {
      cqlLibraryService.checkDuplicateCqlLibraryName(cqlLibrary.getCqlLibraryName());
    }
    // update includedLibraries if cql changed
    if (!StringUtils.equals(cqlLibrary.getCql(), persistedLibrary.getCql())) {
      cqlLibrary.setIncludedLibraries(LibraryUtils.getIncludedLibraries(cqlLibrary.getCql()));
    }
    cqlLibrary.setLibrarySet(persistedLibrary.getLibrarySet());
    cqlLibrary.setDraft(persistedLibrary.isDraft());
    cqlLibrary.setVersion(persistedLibrary.getVersion());
    cqlLibrary.setLastModifiedAt(Instant.now());
    cqlLibrary.setLastModifiedBy(username);
    cqlLibrary.setCreatedAt(persistedLibrary.getCreatedAt());
    cqlLibrary.setCreatedBy(persistedLibrary.getCreatedBy());
    ResponseEntity<CqlLibrary> response = ResponseEntity.ok(cqlLibraryRepository.save(cqlLibrary));
    actionLogService.logAction(id, ActionType.UPDATED, username, "actionLog");
    return response;
  }

  @GetMapping(value = "/cql", produces = MediaType.TEXT_PLAIN_VALUE)
  public String getLibraryCql(
      @RequestParam String name,
      @RequestParam String version,
      @RequestParam Optional<String> model) {
    return cqlLibraryService
        .getVersionedCqlLibrary(name, version, model, false, "Info", null)
        .getCql();
  }

  @PutMapping("/version/{id}")
  public ResponseEntity<CqlLibrary> createVersion(
      @PathVariable("id") String id,
      @RequestParam boolean isMajor,
      Principal principal,
      @RequestHeader("Authorization") String accessToken) {
    return ResponseEntity.ok(
        versionService.createVersion(id, isMajor, principal.getName(), accessToken));
  }

  @PostMapping("/draft/{id}")
  public ResponseEntity<CqlLibrary> createDraft(
      @PathVariable("id") String id,
      @Validated(CqlLibrary.ValidationSequence.class) @RequestBody final CqlLibraryDraft cqlLibrary,
      Principal principal) {
    var output =
        versionService.createDraft(
            id, cqlLibrary.getCqlLibraryName(), cqlLibrary.getModel(), principal.getName());
    log.debug("output: {}", output);
    return ResponseEntity.status(HttpStatus.CREATED).body(output);
  }

  @GetMapping(
      value = "/usage",
      produces = {MediaType.APPLICATION_JSON_VALUE})
  public ResponseEntity<List<LibraryUsage>> getLibraryUsage(@RequestParam String libraryName) {
    return ResponseEntity.ok().body(cqlLibraryService.findLibraryUsage(libraryName));
  }

  @GetMapping(
      value = "/all-versioned",
      produces = {MediaType.APPLICATION_JSON_VALUE})
  public ResponseEntity<List<LibraryListDTO>> getLibrariesByNameAndModel(
      @RequestParam String libraryName, @RequestParam String model) {
    return ResponseEntity.ok()
        .body(cqlLibraryService.findLibrariesByNameAndModel(libraryName, model));
  }

  @PutMapping(
      value = "/{id}/ownership",
      produces = {MediaType.TEXT_PLAIN_VALUE})
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<String> changeOwnership(
      HttpServletRequest request,
      @PathVariable("id") String id,
      @RequestParam(name = "userid") String userid,
      @Value("${admin-api-key}") String apiKey,
      Principal principal) {
    try {
      cqlLibraryService.changeOwnership(id, userid, false, principal.getName());
      return ResponseEntity.ok(userid + " granted ownership to Library successfully.");
    } catch (ResourceNotFoundException e) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Library does not exist.");
    } catch (RuntimeException e) {
      log.error(
          "Failed to change ownership for Library [{}] to user [{}]: {}",
          id,
          userid,
          e.getMessage(),
          e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Failed to grant ownership.");
    }
  }

  @PutMapping("/{id}/acls")
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<List<AclSpecification>> updateAccessControl(
      HttpServletRequest request,
      @PathVariable String id,
      @RequestBody @Validated AclOperation aclOperation,
      @Value("${admin-api-key}") String apiKey) {
    List<AclSpecification> aclSpecifications =
        cqlLibraryService.updateAccessControlList(id, aclOperation, "admin");
    return ResponseEntity.ok().body(aclSpecifications);
  }

  @GetMapping("/sharedWith")
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<List<Map<String, Object>>> getMeasureSharedWith(
      HttpServletRequest request,
      @Value("${admin-api-key}") String apiKey,
      @RequestHeader(name = "harpId") String harpId,
      @RequestParam(name = "measureids") String measureids) {
    List<Map<String, Object>> results = new ArrayList<>();
    String[] ids = StringUtils.split(measureids, ",");
    for (String id : ids) {
      CqlLibrary library = cqlLibraryService.findCqlLibraryById(id);
      if (library != null) {
        if (!library.getLibrarySet().getOwner().equals(harpId)) {
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
  public ResponseEntity<CqlLibrary> hardDeleteLibrary(
      @PathVariable("id") String id, Principal principal) {
    return ResponseEntity.ok(cqlLibraryService.deleteDraftLibrary(id, principal.getName()));
  }

  @DeleteMapping("/{libraryName}/delete-all-versions")
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<String> deleteLibraryAlongWithVersions(
      HttpServletRequest request,
      @PathVariable String libraryName,
      @RequestHeader("Authorization") String accessToken,
      @RequestHeader(name = "harpId") String harpId,
      @Value("${admin-api-key}") String apiKey) {
    cqlLibraryService.deleteLibraryAlongWithVersions(libraryName, accessToken, harpId);
    return ResponseEntity.ok()
        .body("The library and all its associated versions have been removed successfully.");
  }

  @GetMapping("/shared")
  public ResponseEntity<Map<String, List<SharedUser>>> getSharedLibraries(
      @RequestParam(name = "libraryIds") List<String> libraryIds) {
    return ResponseEntity.ok().body(cqlLibraryService.getSharedLibraries(libraryIds));
  }

  @GetMapping("/recentsByLibrarySetId")
  public ResponseEntity<List<CqlLibrary>> getRecentLibrariesByLibrarySetId(
      @RequestParam(name = "librarySetIds") List<String> librarySetIds) {
    List<CqlLibrary> results = librarySetService.getRecentLibrariesByLibrarySetId(librarySetIds);
    return ResponseEntity.status(HttpStatus.OK).body(results);
  }

  @PutMapping("/share")
  public ResponseEntity<Map<String, List<AclSpecification>>> shareLibraries(
      @RequestBody Map<String, List<String>> libraryUserIdMap, Principal principal) {

    return ResponseEntity.ok(
        cqlLibraryService.shareLibraries(libraryUserIdMap, principal.getName()));
  }

  @PutMapping("/unshare")
  public ResponseEntity<Map<String, List<AclSpecification>>> unshareLibraries(
      @RequestBody Map<String, List<String>> libraryUserIdMap, Principal principal) {
    return ResponseEntity.ok(
        cqlLibraryService.unshareLibraries(libraryUserIdMap, principal.getName()));
  }

  /**
   * Handles transfer of multiple libraries to a new owner (identified by harpId).
   *
   * <p>Validates the input list of library IDs. Delegates transfer logic to CqlLibraryService,
   * which attempts to reassign each library. Returns:
   *
   * <ul>
   *   <li>200 OK if all transfers succeed.
   *   <li>400 BAD REQUEST if the input list is empty.
   *   <li>207 MULTI_STATUS if some transfers fail, returning only the failed library IDs in the
   *       body.
   * </ul>
   */
  @PutMapping("/transfer")
  public ResponseEntity<List<String>> transferLibraries(
      @RequestBody List<String> cqlLibraryIds,
      @RequestHeader(name = "harpId") String harpId,
      @RequestParam(defaultValue = "false") boolean retainShareAccess,
      Principal principal,
      @RequestHeader("Authorization") String accessToken) {
    log.info("transferLibraries to [{}] ", harpId);
    if (CollectionUtils.isEmpty(cqlLibraryIds)) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.emptyList());
    }
    List<String> failedTransfers =
        cqlLibraryService.transferLibraries(
            cqlLibraryIds, harpId, retainShareAccess, principal.getName());
    if (CollectionUtils.isEmpty(failedTransfers)) {
      return ResponseEntity.ok().body(failedTransfers);
    } else {
      return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(failedTransfers);
    }
  }

  @GetMapping(value = "/{id}/history")
  public ResponseEntity<List<Action>> getCqlLibraryHistory(
      @PathVariable("id") String cqlLibraryId, Principal principal) {
    return ResponseEntity.ok()
        .body(cqlLibraryService.getCqlLibraryHistory(cqlLibraryId, principal.getName()));
  }
}
