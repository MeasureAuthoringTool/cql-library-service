package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.exceptions.BadRequestObjectException;
import gov.cms.madie.cqllibraryservice.exceptions.InternalServerErrorException;
import gov.cms.madie.cqllibraryservice.exceptions.PermissionDeniedException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotDraftableException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceCannotBeVersionedException;
import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import gov.cms.madie.cqllibraryservice.models.Version;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Service
public class VersionService {

  private final CqlLibraryService cqlLibraryService;
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
      throw new BadRequestObjectException("CQL Library", id, username);
    }

    if (cqlLibrary.isCqlErrors()) {
      log.error(
          "User [{}] cannot create a version for CQL Library with id [{}] "
              + "as the Cql has errors in it",
          username,
          cqlLibrary.getId());
      throw new ResourceCannotBeVersionedException("CQL Library", cqlLibrary.getId(), username);
    }

    cqlLibrary.setDraft(false);
    cqlLibrary.setLastModifiedAt(Instant.now());
    cqlLibrary.setLastModifiedBy(username);

    Version next = getNextVersion(cqlLibrary, isMajor);
    cqlLibrary.setVersion(next);
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

    if (!Objects.equals(cqlLibraryName, cqlLibrary.getCqlLibraryName())) {
      cqlLibraryService.checkDuplicateCqlLibraryName(cqlLibraryName);
    }

    if (!Objects.equals(cqlLibrary.getCreatedBy(), username)) {
      log.info(
          "User [{}] doest not have permission to create a draft of CQL Library with id [{}]",
          username,
          cqlLibrary.getId());
      throw new PermissionDeniedException("CQL Library", cqlLibrary.getId(), username);
    }

    if (!isDraftable(cqlLibrary)) {
      throw new ResourceNotDraftableException("CQL Library");
    }

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

  public Version getNextVersion(CqlLibrary cqlLibrary, boolean isMajor) {
    // get the max major/minor version and increment it
    try {
      if (isMajor) {
        Version version =
            cqlLibraryRepository
                .findMaxVersionByGroupId(cqlLibrary.getGroupId())
                .orElse(new Version());
        return version.toBuilder().major(version.getMajor() + 1).minor(0).build();
      } else {
        Version version =
            cqlLibraryRepository
                .findMaxMinorVersionByGroupIdAndVersionMajor(
                    cqlLibrary.getGroupId(), cqlLibrary.getVersion().getMajor())
                .orElse(new Version());
        return version.toBuilder().minor(version.getMinor() + 1).build();
      }
    } catch (RuntimeException ex) {
      log.error("VersionController::updateVersion Exception while updating version number", ex);
      throw new InternalServerErrorException("Unable to update version number", ex);
    }
  }

  /**
   * Returns false if there is already a draft for any version of this CQL Library group.
   *
   * @param cqlLibrary CQL Library to check
   * @return false if there is already a draft for any version of this CQL Library group, true
   *     otherwise.
   */
  public boolean isDraftable(CqlLibrary cqlLibrary) {
    if (cqlLibrary == null) {
      return true;
    }
    return !cqlLibraryRepository.existsByGroupIdAndDraft(cqlLibrary.getGroupId(), true);
  }
}
