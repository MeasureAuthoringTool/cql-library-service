package gov.cms.madie.cqllibraryservice.controller;

import gov.cms.madie.cqllibraryservice.exceptions.DuplicateKeyException;
import gov.cms.madie.cqllibraryservice.exceptions.InvalidIdException;
import gov.cms.madie.cqllibraryservice.exceptions.PermissionDeniedException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotDraftableException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import gov.cms.madie.cqllibraryservice.respositories.CqlLibraryRepository;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryService;
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
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/cql-libraries")
@RequiredArgsConstructor
public class CqlLibraryController {

  private final CqlLibraryRepository cqlLibraryRepository;
  private final CqlLibraryService cqlLibraryService;

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
//    cqlLibrary.setVersion("0.0.000");
    cqlLibrary.setDraft(true);
    cqlLibrary.setGroupId(null); // generate a new id
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

  @PostMapping("/{id}/draft")
  public ResponseEntity<CqlLibrary> createDraft(
      @PathVariable("id") String id, Principal principal) {
    final String username = principal.getName();
    Optional<CqlLibrary> libraryOptional = cqlLibraryRepository.findById(id);
    if (!libraryOptional.isPresent()) {
      throw new ResourceNotFoundException("CQL Library", id);
    }
    final CqlLibrary cqlLibrary = libraryOptional.get();

    log.info("creating draft for: {}", cqlLibrary);

    if (!Objects.equals(cqlLibrary.getCreatedBy(), username)) {
      log.info("User [{}] doest not have permission to create a draft of CQL Library with id [{}]",
          username, cqlLibrary.getId());
      throw new PermissionDeniedException("CQL Library", cqlLibrary.getId(), username);
    }
    if (!cqlLibraryService.isDraftable(cqlLibrary)) {
      throw new ResourceNotDraftableException("CQL Library");
    }
    var now = Instant.now();
    CqlLibrary clonedCqlLibrary = cqlLibrary
        .toBuilder()
        .draft(true)
        .createdAt(now)
        .lastModifiedAt(now)
        .build();

    CqlLibrary savedCqlLibrary = cqlLibraryRepository.save(clonedCqlLibrary);
    log.info("User [{}] successfully created a draft cql library with ID [{}]",
        username, savedCqlLibrary.getId());
    return ResponseEntity.status(HttpStatus.CREATED).body(savedCqlLibrary);

  }

  @PostMapping("/{id}/version/{isMajor}")
  public ResponseEntity<CqlLibrary> createVersion(
      @PathVariable("id") String id,
      @PathVariable("isMajor") boolean isMajor,
      Principal principal) {
    final String username = principal.getName();
    Optional<CqlLibrary> libraryOptional = cqlLibraryRepository.findById(id);
    if (!libraryOptional.isPresent()) {
      throw new ResourceNotFoundException("CQL Library", id);
    }
    final CqlLibrary cqlLibrary = libraryOptional.get();

    try {
      if (!Objects.equals(cqlLibrary.getCreatedBy(), username)) {
        log.info("User [{}] doest not have permission to create a draft of CQL Library with id [{}]",
            username, cqlLibrary.getId());
        throw new PermissionDeniedException("CQL Library", cqlLibrary.getId(), username);
      }
      var now = Instant.now();
      CqlLibrary clonedCqlLibrary = cqlLibrary
          .toBuilder()
          .draft(false)
          .createdAt(now)
          .lastModifiedAt(now)
          .build();

      if (isMajor) {
        // Todo get the max major and increment it add it to this version
        clonedCqlLibrary.setVersion(cqlLibrary.getVersion());
      } else {
        // Todo get the max of minor version and it
        clonedCqlLibrary.setVersion(cqlLibrary.getVersion());
      }

      CqlLibrary savedCqlLibrary = cqlLibraryRepository.save(clonedCqlLibrary);
      log.info("User [{}] successfully versioned cql library with ID [{}]",
          username, savedCqlLibrary.getId());
      return ResponseEntity.status(HttpStatus.CREATED).body(savedCqlLibrary);

    } catch (Exception exception) {
      throw new RuntimeException();
    }
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
