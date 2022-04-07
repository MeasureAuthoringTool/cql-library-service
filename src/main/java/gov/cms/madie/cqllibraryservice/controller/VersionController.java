package gov.cms.madie.cqllibraryservice.controller;

import gov.cms.madie.cqllibraryservice.exceptions.InvalidIdException;
import gov.cms.madie.cqllibraryservice.exceptions.PermissionDeniedException;
import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import gov.cms.madie.cqllibraryservice.respositories.CqlLibraryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/cql-libraries")
@RequiredArgsConstructor
public class VersionController {
  private final CqlLibraryRepository cqlLibraryRepository;

  @PutMapping("/version/{isMajor}")
  public ResponseEntity<CqlLibrary> createVersion(
      @PathVariable("isMajor") boolean isMajor,
      @RequestBody final CqlLibrary cqlLibrary,
      Principal principal) {
    final String username = principal.getName();

    try {
      if (!Objects.equals(cqlLibrary.getCreatedBy(), username)) {
        log.info("User [{}] doest not have permission to create a draft of CQL Library with id [{}]",
                username, cqlLibrary.getId());
        throw new PermissionDeniedException("CQL Library", cqlLibrary.getId(), username);
      }

      CqlLibrary clonedCqlLibrary = cloneCqlLibrary(cqlLibrary, false);
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

  @PutMapping("/draft")
  public ResponseEntity<CqlLibrary> createDraft(
          @RequestBody final CqlLibrary cqlLibrary,
          Principal principal) {
    final String username = principal.getName();

    try {
      if (!Objects.equals(cqlLibrary.getCreatedBy(), username)) {
        log.info("User [{}] doest not have permission to create a draft of CQL Library with id [{}]",
                username, cqlLibrary.getId());
        throw new PermissionDeniedException("CQL Library", cqlLibrary.getId(), username);
      }
      // Todo Make sure no other drafts already exists in this group

      CqlLibrary clonedCqlLibrary = cloneCqlLibrary(cqlLibrary, true);

      CqlLibrary savedCqlLibrary = cqlLibraryRepository.save(clonedCqlLibrary);
      log.info("User [{}] successfully created a draft cql library with ID [{}]",
              username, savedCqlLibrary.getId());
      return ResponseEntity.status(HttpStatus.CREATED).body(savedCqlLibrary);
    } catch (RuntimeException runtimeException) {
      throw new RuntimeException();
    }
  }

  private CqlLibrary cloneCqlLibrary(CqlLibrary cqlLibrary, boolean isDraft) {
    var clonedCqlLibrary = new CqlLibrary();
    // Clear ID so that the unique GUID from MongoDB will be applied
    clonedCqlLibrary.setId(null);
    clonedCqlLibrary.setCqlLibraryName(cqlLibrary.getCqlLibraryName());
    clonedCqlLibrary.setModel(cqlLibrary.getModel());
    clonedCqlLibrary.setDraft(isDraft);
    clonedCqlLibrary.setGroupId(cqlLibrary.getGroupId());
    clonedCqlLibrary.setCql(cqlLibrary.getCql());
    clonedCqlLibrary.setVersion(cqlLibrary.getVersion()); //version remains same while drafting a library

    var now = Instant.now();
    clonedCqlLibrary.setCreatedBy(cqlLibrary.getCreatedBy());
    clonedCqlLibrary.setCreatedAt(now);
    clonedCqlLibrary.setLastModifiedBy(cqlLibrary.getLastModifiedBy());
    clonedCqlLibrary.setLastModifiedAt(now);

    clonedCqlLibrary.setPublisher(cqlLibrary.getPublisher());
    clonedCqlLibrary.setDescription(cqlLibrary.getDescription());
    return clonedCqlLibrary;
  }
}
