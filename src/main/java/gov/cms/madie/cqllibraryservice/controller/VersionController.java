package gov.cms.madie.cqllibraryservice.controller;

import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceOwnerNotFoundException;
import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import gov.cms.madie.cqllibraryservice.respositories.CqlLibraryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/cql-libraries")
@RequiredArgsConstructor
public class VersionController {
  private final CqlLibraryRepository cqlLibraryRepository;

  @GetMapping("version/{id}/{versionType}")
  public ResponseEntity<CqlLibrary> createVersion(
          @PathVariable("id") String id, @PathVariable("versionType") String versionType,
          Principal principal) {
    final String username = principal.getName();
    Optional<CqlLibrary> cqlLibrary = cqlLibraryRepository.findById(id);

//    if (cqlLibrary.isPresent()) {
//      if (!Objects.equals(cqlLibrary.get().getCreatedBy(), username)) {
//        throw new ResourceOwnerNotFoundException("CQL Library", id, username));
//      }
//    } else {
//      throw new ResourceNotFoundException("CQL Library", id));
//    }


    //verify if the user attempting to createVersion is the owner of the library, if not throw an exception
    // Do we need to use Put?
    // clone te object; copy groupId
    // based on major or minor get the max of major or get max of minor
    // setDraft to false
    //
    return ResponseEntity.ok(cqlLibrary.get());
  }
}
