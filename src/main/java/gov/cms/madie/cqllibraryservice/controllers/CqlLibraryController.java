package gov.cms.madie.cqllibraryservice.controllers;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.exceptions.InvalidIdException;
import gov.cms.madie.cqllibraryservice.exceptions.InvalidResourceStateException;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.cqllibraryservice.services.ActionLogService;
import gov.cms.madie.cqllibraryservice.services.LibrarySetService;
import gov.cms.madie.cqllibraryservice.utils.AuthUtils;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.CqlLibraryDraft;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryService;
import gov.cms.madie.cqllibraryservice.services.VersionService;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gov.cms.madie.models.library.LibrarySet;
import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.codehaus.plexus.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
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
  private final LibrarySetRepository librarySetRepository;

  @GetMapping
  public ResponseEntity<List<LibraryListDTO>> getCqlLibraries(
      Principal principal,
      @RequestParam(required = false, defaultValue = "false", name = "currentUser")
          boolean filterByCurrentUser) {
    final String username = principal.getName();
    List<LibraryListDTO> cqlLibraries =
        filterByCurrentUser
            ? cqlLibraryRepository.findAllLibrariesByUser(username)
            : cqlLibraryRepository.findAllProjected();
    cqlLibraries.forEach(
        l -> {
          LibrarySet librarySet =
              librarySetRepository.findByLibrarySetId(l.getLibrarySetId()).orElse(null);
          l.setLibrarySet(librarySet);
        });
    return ResponseEntity.ok(cqlLibraries);
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
      @RequestHeader("Authorization") String accessToken) {
    return ResponseEntity.ok(
        cqlLibraryService.getVersionedCqlLibrary(name, version, model, accessToken));
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
    actionLogService.logAction(cqlLibrary.getLibrarySetId(), ActionType.CREATED, username);

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
    cqlLibrary.setLibrarySet(persistedLibrary.getLibrarySet());
    cqlLibrary.setDraft(persistedLibrary.isDraft());
    cqlLibrary.setVersion(persistedLibrary.getVersion());
    cqlLibrary.setLastModifiedAt(Instant.now());
    cqlLibrary.setLastModifiedBy(username);
    cqlLibrary.setCreatedAt(persistedLibrary.getCreatedAt());
    cqlLibrary.setCreatedBy(persistedLibrary.getCreatedBy());
    return ResponseEntity.ok(cqlLibraryRepository.save(cqlLibrary));
  }

  @GetMapping(value = "/cql", produces = MediaType.TEXT_PLAIN_VALUE)
  public String getLibraryCql(
      @RequestParam String name,
      @RequestParam String version,
      @RequestParam Optional<String> model) {
    return cqlLibraryService.getVersionedCqlLibrary(name, version, model, null).getCql();
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
            id, cqlLibrary.getCqlLibraryName(), cqlLibrary.getCql(), principal.getName());
    log.info("output: {}", output);
    return ResponseEntity.status(HttpStatus.CREATED).body(output);
  }

  @PutMapping(
      value = "/{id}/ownership",
      produces = {MediaType.TEXT_PLAIN_VALUE})
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<String> changeOwnership(
      HttpServletRequest request,
      @PathVariable("id") String id,
      @RequestParam(name = "userid") String userid,
      @Value("${lambda-api-key}") String apiKey) {
    ResponseEntity<String> response = ResponseEntity.badRequest().body("Library does not exist.");

    if (cqlLibraryService.changeOwnership(id, userid)) {
      response =
          ResponseEntity.ok()
              .contentType(MediaType.TEXT_PLAIN)
              .body(String.format("%s granted ownership to Library successfully.", userid));
      actionLogService.logAction(id, ActionType.UPDATED, "apiKey");
    }

    return response;
  }

  @PutMapping(
      value = "/{id}/grant",
      produces = {MediaType.TEXT_PLAIN_VALUE})
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<String> grantAccess(
      HttpServletRequest request,
      @PathVariable("id") String id,
      @RequestParam(required = true, name = "userid") String userid,
      @Value("${lambda-api-key}") String apiKey) {
    ResponseEntity<String> response =
        ResponseEntity.badRequest().body("Cql Library does not exist.");

    log.info("getLibraryId [{}] using apiKey ", id);

    if (cqlLibraryService.grantAccess(id, userid)) {
      response =
          ResponseEntity.ok()
              .body(String.format("%s granted access to Library successfully.", userid));
      actionLogService.logAction(id, ActionType.UPDATED, "apiKey");
    }
    return response;
  }

  @GetMapping("/sharedWith")
  @PreAuthorize("#request.getHeader('api-key') == #apiKey")
  public ResponseEntity<List<Map<String, Object>>> getMeasureSharedWith(
      HttpServletRequest request,
      @Value("${lambda-api-key}") String apiKey,
      @RequestHeader("Authorization") String accessToken,
      @RequestParam(required = true, name = "measureids") String measureids) {
    List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
    String[] ids = StringUtils.split(measureids, ",");
    for (String id : ids) {
      CqlLibrary library = cqlLibraryService.findCqlLibraryById(id);
      if (library != null) {

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
}
