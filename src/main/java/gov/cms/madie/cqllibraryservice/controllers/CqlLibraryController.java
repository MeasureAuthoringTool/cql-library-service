package gov.cms.madie.cqllibraryservice.controllers;

import gov.cms.madie.cqllibraryservice.exceptions.DuplicateKeyException;
import gov.cms.madie.cqllibraryservice.exceptions.InvalidIdException;
import gov.cms.madie.cqllibraryservice.exceptions.InvalidResourceStateException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import gov.cms.madie.cqllibraryservice.models.Version;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.cqllibraryservice.services.VersionService;
import io.micrometer.core.instrument.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/cql-libraries")
@RequiredArgsConstructor
public class CqlLibraryController {

  private final CqlLibraryRepository cqlLibraryRepository;
  private final VersionService versionService;

  @GetMapping
  public ResponseEntity<List<CqlLibrary>> getCqlLibraries(
      Principal principal,
      @RequestParam(required = false, defaultValue = "false", name = "currentUser")
          boolean filterByCurrentUser) {
    final String username = principal.getName();
    List<CqlLibrary> cqlLibraries =
        filterByCurrentUser
            ? cqlLibraryRepository.findAllByCreatedBy(username)
            : cqlLibraryRepository.findAll();
    return ResponseEntity.ok(cqlLibraries);
  }

  @GetMapping("/{id}")
  public ResponseEntity<CqlLibrary> getCqlLibrary(@PathVariable("id") String id) {
    return cqlLibraryRepository
        .findById(id)
        .map(ResponseEntity::ok)
        .orElseThrow(() -> new ResourceNotFoundException("CQL Library", id));
  }

  @PostMapping
  public ResponseEntity<CqlLibrary> createCqlLibrary(
      @Validated(CqlLibrary.ValidationSequence.class) @RequestBody CqlLibrary cqlLibrary,
      Principal principal) {
    final String username = principal.getName();
    log.info("User [{}] is attempting to create a new cql library", username);

    checkDuplicateCqlLibraryName(cqlLibrary.getCqlLibraryName());

    // Clear ID so that the unique GUID from MongoDB will be applied
    Instant now = Instant.now();
    cqlLibrary.setId(null);
    cqlLibrary.setCreatedBy(username);
    cqlLibrary.setCreatedAt(now);
    cqlLibrary.setLastModifiedBy(username);
    cqlLibrary.setLastModifiedAt(now);
    cqlLibrary.setVersion(Version.parse("0.0.000"));
    cqlLibrary.setDraft(true);
    cqlLibrary.setGroupId(UUID.randomUUID().toString()); // generate a new id
    CqlLibrary savedCqlLibrary = cqlLibraryRepository.save(cqlLibrary);
    log.info(
        "User [{}] successfully created new cql library with ID [{}]",
        username,
        cqlLibrary.getId());
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

    return cqlLibraryRepository
        .findById(cqlLibrary.getId())
        .map(
            persistedLibrary -> {
              if (!cqlLibrary.isDraft()) {
                throw new InvalidResourceStateException("CQL Library", id);
              }
              if (isCqlLibraryNameChanged(cqlLibrary, persistedLibrary)) {
                checkDuplicateCqlLibraryName(cqlLibrary.getCqlLibraryName());
              }
              cqlLibrary.setLastModifiedAt(Instant.now());
              cqlLibrary.setLastModifiedBy(username);
              cqlLibrary.setCreatedAt(persistedLibrary.getCreatedAt());
              cqlLibrary.setCreatedBy(persistedLibrary.getCreatedBy());
              return ResponseEntity.ok(cqlLibraryRepository.save(cqlLibrary));
            })
        .orElseThrow(() -> new ResourceNotFoundException("CQL Library", id));
  }

  @PutMapping("/version/{id}")
  public ResponseEntity<CqlLibrary> createVersion(
      @PathVariable("id") String id, @RequestParam boolean isMajor, Principal principal) {
    return ResponseEntity.ok(versionService.createVersion(id, isMajor, principal.getName()));
  }

  @PostMapping("/draft/{id}")
  public ResponseEntity<CqlLibrary> createDraft(
      @PathVariable("id") String id,
      @RequestBody final CqlLibrary cqlLibrary,
      Principal principal) {
    var output =
        versionService.createDraft(id, cqlLibrary.getCqlLibraryName(), principal.getName());
    log.info("output: {}", output);
    return ResponseEntity.status(HttpStatus.CREATED).body(output);
  }

  private void checkDuplicateCqlLibraryName(String cqlLibraryName) {
    if (StringUtils.isNotEmpty(cqlLibraryName)
        && cqlLibraryRepository.existsByCqlLibraryName(cqlLibraryName)) {
      throw new DuplicateKeyException("cqlLibraryName", "Library name must be unique.");
    }
  }

  private boolean isCqlLibraryNameChanged(CqlLibrary cqlLibrary, CqlLibrary persistedCqlLibrary) {
    return !Objects.equals(persistedCqlLibrary.getCqlLibraryName(), cqlLibrary.getCqlLibraryName());
  }
}