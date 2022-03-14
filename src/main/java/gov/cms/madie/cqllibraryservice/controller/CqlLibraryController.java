package gov.cms.madie.cqllibraryservice.controller;

import gov.cms.madie.cqllibraryservice.exceptions.DuplicateKeyException;
import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import gov.cms.madie.cqllibraryservice.respositories.CqlLibraryRepository;
import io.micrometer.core.instrument.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/cql-libraries")
@RequiredArgsConstructor
public class CqlLibraryController {

  private final CqlLibraryRepository cqlLibraryRepository;

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
    CqlLibrary savedCqlLibrary = cqlLibraryRepository.save(cqlLibrary);
    log.info(
        "User [{}] successfully created new cql library with ID [{}]",
        username,
        cqlLibrary.getId());
    return ResponseEntity.status(HttpStatus.CREATED).body(savedCqlLibrary);
  }

  private void checkDuplicateCqlLibraryName(String cqlLibraryName) {
    if (StringUtils.isNotEmpty(cqlLibraryName)
        && cqlLibraryRepository.findByCqlLibraryName(cqlLibraryName).isPresent()) {
      throw new DuplicateKeyException("cqlLibraryName", "Library name must be unique.");
    }
  }
}
