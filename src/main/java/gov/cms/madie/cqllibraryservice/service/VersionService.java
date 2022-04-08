package gov.cms.madie.cqllibraryservice.service;

import gov.cms.madie.cqllibraryservice.exceptions.BadRequestObject;
import gov.cms.madie.cqllibraryservice.exceptions.PermissionDeniedException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import gov.cms.madie.cqllibraryservice.respositories.CqlLibraryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class VersionService {

  private final CqlLibraryRepository cqlLibraryRepository;

  public CqlLibrary createVersion(String id, boolean isMajor, String username) {
    CqlLibrary cqlLibrary =
        cqlLibraryRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("CQL Library", id));

    if (!Objects.equals(cqlLibrary.getCreatedBy(), username)) {
      log.error(
          "User [{}] doest not have permission to create a version of CQL Library with id [{}]",
          username,
          cqlLibrary.getId());
      throw new PermissionDeniedException("CQL Library", cqlLibrary.getId(), username);
    }

    if (!cqlLibrary.isDraft()) {
      log.error(
          "User [{}] attempted to version CQL Library with id [{}] which is not in a draft state",
          username,
          cqlLibrary.getId());
      throw new BadRequestObject("CQL Library", id, username);
    }

    cqlLibrary.setDraft(false);
    cqlLibrary.setLastModifiedAt(Instant.now());
    cqlLibrary.setLastModifiedBy(username);

    updateVersion(cqlLibrary, isMajor);
    var savedCqlLibrary = cqlLibraryRepository.save(cqlLibrary);

    log.info(
        "User [{}] successfully versioned cql library with ID [{}]",
        username,
        savedCqlLibrary.getId());
    return savedCqlLibrary;
  }

  public CqlLibrary createDraft(String id, String cqlLibraryName, String username) {
    CqlLibrary cqlLibrary =
        cqlLibraryRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("CQL Library", id));

    if (!Objects.equals(cqlLibrary.getCreatedBy(), username)) {
      log.info(
          "User [{}] doest not have permission to create a draft of CQL Library with id [{}]",
          username,
          cqlLibrary.getId());
      throw new PermissionDeniedException("CQL Library", cqlLibrary.getId(), username);
    }

    // Todo Make sure no other drafts already exists in this group

    CqlLibrary clonedCqlLibrary = cqlLibrary.toBuilder().build(); // creates a shallow copy
    // Clear ID so that the unique GUID from MongoDB will be applied
    clonedCqlLibrary.setId(null);
    clonedCqlLibrary.setCqlLibraryName(cqlLibraryName);
    clonedCqlLibrary.setDraft(true);
    var now = Instant.now();
    clonedCqlLibrary.setCreatedAt(now);
    clonedCqlLibrary.setCreatedBy(username);
    clonedCqlLibrary.setLastModifiedAt(now);
    clonedCqlLibrary.setLastModifiedBy(username);

    var savedCqlLibrary = cqlLibraryRepository.save(clonedCqlLibrary);

    log.info(
        "User [{}] successfully created a draft cql library with ID [{}]",
        username,
        savedCqlLibrary.getId());
    return savedCqlLibrary;
  }

  private void updateVersion(CqlLibrary cqlLibrary, boolean isMajor) {
    // get the max major/minor version and increment it
    try {
      if (isMajor) {
        int maxMajorVersion =
            cqlLibraryRepository.findAll().stream()
                .filter(c -> c.getGroupId().equals(cqlLibrary.getGroupId()))
                .collect(Collectors.toList())
                .stream()
                .max(Comparator.comparing(c -> c.getVersion().getMajor()))
                .orElseThrow(NoSuchElementException::new)
                .getVersion()
                .getMajor();
        cqlLibrary.getVersion().setMajor(maxMajorVersion + 1);
      } else {
        int maxMinorVersion =
            cqlLibraryRepository.findAll().stream()
                .filter(c -> c.getGroupId().equals(cqlLibrary.getGroupId()))
                .collect(Collectors.toList())
                .stream()
                .max(Comparator.comparing(c -> c.getVersion().getMinor()))
                .orElseThrow(NoSuchElementException::new)
                .getVersion()
                .getMinor();
        cqlLibrary.getVersion().setMinor(maxMinorVersion + 1);
      }
    } catch (RuntimeException e) {
      log.error("VersionController::updateVersion Exception while updating version number");
      throw new RuntimeException("Unable to update version number");
    }
  }
}
