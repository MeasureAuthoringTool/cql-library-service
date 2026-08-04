package gov.cms.madie.cqllibraryservice.controllers;

import gov.cms.madie.cqllibraryservice.dto.*;
import gov.cms.madie.cqllibraryservice.exceptions.BadRequestObjectException;
import gov.cms.madie.cqllibraryservice.exceptions.InvalidIdException;
import gov.cms.madie.cqllibraryservice.locks.CqlLibraryLock;
import gov.cms.madie.cqllibraryservice.services.*;
import gov.cms.madie.cqllibraryservice.utils.PaginationUtils;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.common.Action;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
  private final CqlDifferentiatorService cqlDifferentiatorService;
  private final CqlLibraryLockService cqlLibraryLockService;
  private final AppConfigService appConfigService;

  @PutMapping("/searches")
  public ResponseEntity<Page<LibraryListDTO>> fetchLibrariesByCriteria(
      Principal principal,
      @RequestParam(required = false, defaultValue = "ALL", name = "ownershipType")
          OwnershipType ownershipType,
      @RequestBody(required = false) LibrarySearchCriteria librarySearchCriteria,
      @RequestParam(required = false, defaultValue = "10", name = "limit") int limit,
      @RequestParam(required = false, defaultValue = "0", name = "page") int page,
      @RequestParam(required = false, name = "sortInfo") String sortInfo) {
    final String username = principal.getName().toLowerCase();
    Pageable pageReq = PaginationUtils.createPageable(page, limit, sortInfo);
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
  public ResponseEntity<CqlLibrary> getCqlLibrary(
      @PathVariable("id") String id, Principal principal) {
    final String username = principal.getName().toLowerCase();
    return ResponseEntity.ok(cqlLibraryService.findCqlLibraryById(id, username));
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
    final String username = principal.getName().toLowerCase();
    log.info("User [{}] is attempting to create a new cql library", username);

    if (ModelType.US_QUALITY_CORE_0_5_0.getValue().equals(cqlLibrary.getModel())
        && !appConfigService.isFlagEnabled(MadieFeatureFlag.US_QUALITY_CORE)) {
      log.info(
          "User [{}] attempted to create a cql library with model [{}] while the usQualityCore "
              + "feature flag is disabled",
          username,
          cqlLibrary.getModel());
      throw new BadRequestObjectException(
          "The model " + cqlLibrary.getModel() + " is not currently supported in MADiE.");
    }

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
    final String username = principal.getName().toLowerCase();

    if (StringUtils.isEmpty(id) || !id.equals(cqlLibrary.getId())) {
      log.info("got invalid id [{}] vs cqlLibraryId: [{}]", id, cqlLibrary.getId());
      throw new InvalidIdException("CQL Library", "Update (PUT)", "(PUT [base]/[resource]/[id])");
    }

    return ResponseEntity.ok(cqlLibraryService.updateCqlLibrary(cqlLibrary, username));
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
        versionService.createVersion(id, isMajor, principal.getName().toLowerCase(), accessToken));
  }

  @PostMapping("/draft/{id}")
  public ResponseEntity<CqlLibrary> createDraft(
      @PathVariable("id") String id,
      @Validated(CqlLibrary.ValidationSequence.class) @RequestBody final CqlLibraryDraft cqlLibrary,
      Principal principal) {
    var output =
        versionService.createDraft(
            id,
            cqlLibrary.getCqlLibraryName(),
            cqlLibrary.getModel(),
            principal.getName().toLowerCase());
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

  @DeleteMapping("/{id}")
  public ResponseEntity<CqlLibrary> hardDeleteLibrary(
      @PathVariable("id") String id, Principal principal) {
    final String username = principal.getName().toLowerCase();
    return ResponseEntity.ok(cqlLibraryService.deleteDraftLibrary(id, username));
  }

  @GetMapping("/shared")
  public ResponseEntity<Map<String, List<SharedUser>>> getSharedLibraries(
      @RequestParam(name = "libraryIds") List<String> libraryIds, Principal principal) {
    final String username = principal.getName().toLowerCase();
    return ResponseEntity.ok().body(cqlLibraryService.getSharedLibraries(libraryIds, username));
  }

  @GetMapping("/recentsByLibrarySetId")
  public ResponseEntity<List<CqlLibrary>> getRecentLibrariesByLibrarySetId(
      @RequestParam(name = "librarySetIds") List<String> librarySetIds) {
    List<CqlLibrary> results = librarySetService.getRecentLibrariesByLibrarySetId(librarySetIds);
    return ResponseEntity.status(HttpStatus.OK).body(results);
  }

  @PutMapping("/share")
  public ResponseEntity<Map<String, List<AclSpecification>>> shareLibraries(
      @RequestBody Map<String, List<String>> libraryUserIdMap,
      Principal principal,
      @RequestHeader("Authorization") String accessToken) {

    return ResponseEntity.ok(
        cqlLibraryService.shareLibraries(
            libraryUserIdMap, principal.getName().toLowerCase(), accessToken));
  }

  @PutMapping("/unshare")
  public ResponseEntity<Map<String, List<AclSpecification>>> unshareLibraries(
      @RequestBody Map<String, List<String>> libraryUserIdMap,
      Principal principal,
      @RequestHeader("Authorization") String accessToken) {
    return ResponseEntity.ok(
        cqlLibraryService.unshareLibraries(
            libraryUserIdMap, principal.getName().toLowerCase(), accessToken));
  }

  /**
   * Handles transfer of multiple libraries to a new owner (identified by harpId).
   *
   * <p>Validates the input list of library IDs. Locks each library before transfer to avoid race
   * conditions. Delegates transfer logic to CqlLibraryService, which attempts to reassign each
   * library. Unlocks all locked libraries after the transfer. Returns:
   *
   * <ul>
   *   <li>200 OK if all transfers succeed and returns the success library IDs.
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
    log.info(
        "User [{}] - Starting task [transferLibraries] to [{}] for cqlLibraryIds: [{}]",
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
          if (libraryLock == null
              || libraryLock.getLockedBy().equalsIgnoreCase(principal.getName())) {
            validLibraryIds.add(cqlLibraryId);
          } else {
            failedTransfers.add(cqlLibraryId);
          }
        });

    if (CollectionUtils.isNotEmpty(validLibraryIds)) {
      failedTransfers.addAll(
          cqlLibraryService.transferLibraries(
              validLibraryIds,
              harpId.toLowerCase(),
              retainShareAccess,
              principal.getName().toLowerCase(),
              accessToken));
    }

    List<String> successLibraryIds =
        cqlLibraryIds.stream().filter(libraryId -> !failedTransfers.contains(libraryId)).toList();

    if (CollectionUtils.isEmpty(failedTransfers)) {
      log.info("Successful libraryIds: [{}]", successLibraryIds);
      return ResponseEntity.ok().body(successLibraryIds);
    } else {
      log.info("Failed transfer Ids: [{}]", failedTransfers);
      return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(failedTransfers);
    }
  }

  @GetMapping(value = "/{id}/history")
  public ResponseEntity<List<Action>> getCqlLibraryHistory(
      @PathVariable("id") String cqlLibraryId, Principal principal) {
    return ResponseEntity.ok()
        .body(
            cqlLibraryService.getCqlLibraryHistory(
                cqlLibraryId, principal.getName().toLowerCase()));
  }

  @GetMapping(value = "/{oldLibraryId}/compare/{newLibraryId}")
  public ResponseEntity<CqlDiffResultDTO> compareLibraries(
      @PathVariable("oldLibraryId") String oldLibraryId,
      @PathVariable("newLibraryId") String newLibraryId,
      @RequestParam(required = false, defaultValue = "true") boolean autoReorder,
      Principal principal) {

    log.info(
        "Comparing libraries: old={}, new={}, autoReorder={}",
        oldLibraryId,
        newLibraryId,
        autoReorder);

    final CqlLibrary oldLibrary =
        cqlLibraryService.findCqlLibraryById(oldLibraryId, principal.getName().toLowerCase());
    final CqlLibrary newLibrary =
        cqlLibraryService.findCqlLibraryById(newLibraryId, principal.getName().toLowerCase());

    if (oldLibrary == null) {
      throw new ResourceNotFoundException("Cql Library", oldLibraryId);
    }
    if (newLibrary == null) {
      throw new ResourceNotFoundException("Cql Library", newLibraryId);
    }

    // Extract CQL content - for now, treating single CQL file as a "library"
    // In future, this could be extended to handle multiple CQL libraries if needed
    Map<String, String> oldLibraries = new HashMap<>();
    Map<String, String> newLibraries = new HashMap<>();

    if (StringUtils.isNotBlank(oldLibrary.getCql())) {
      String oldFileName = oldLibrary.getCqlLibraryName() + ".cql";
      oldLibraries.put(oldFileName, oldLibrary.getCql());
    }

    if (StringUtils.isNotBlank(newLibrary.getCql())) {
      String newFileName = newLibrary.getCqlLibraryName() + ".cql";
      newLibraries.put(newFileName, newLibrary.getCql());
    }

    // Perform comparison
    List<CqlFileComparisonDTO> comparisons =
        cqlDifferentiatorService.compareLibraries(oldLibraries, newLibraries, autoReorder);

    CqlDiffResultDTO result =
        CqlDiffResultDTO.builder()
            .comparisons(comparisons)
            .oldLibraryId(oldLibraryId)
            .newLibraryId(newLibraryId)
            .build();

    log.info("Comparison complete: {} file comparison(s)", comparisons.size());
    return ResponseEntity.ok(result);
  }
}
