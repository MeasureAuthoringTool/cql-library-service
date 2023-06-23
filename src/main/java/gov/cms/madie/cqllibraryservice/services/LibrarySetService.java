package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.models.library.LibrarySet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LibrarySetService {
  private final LibrarySetRepository librarySetRepository;
  private final ActionLogService actionLogService;

  public void createLibrarySet(
      final String harpId, final String libraryId, final String savedLibrarySetId) {

    boolean isLibrarySetPresent = librarySetRepository.existsByLibrarySetId(savedLibrarySetId);
    if (!isLibrarySetPresent) {
      LibrarySet librarySet =
          LibrarySet.builder().owner(harpId).librarySetId(savedLibrarySetId).build();
      LibrarySet savedLibrarySet = librarySetRepository.save(librarySet);
      log.info(
          "Library set [{}] is successfully created for the library [{}]",
          savedLibrarySet.getId(),
          libraryId);
      actionLogService.logAction(savedLibrarySet.getId(), ActionType.CREATED, harpId);
    }
  }

  public LibrarySet updateLibrarySetAcls(String librarySetId, AclSpecification aclSpec) {
    Optional<LibrarySet> optionalLibrarySet = librarySetRepository.findByLibrarySetId(librarySetId);
    if (optionalLibrarySet.isPresent()) {
      LibrarySet librarySet = optionalLibrarySet.get();
      if (CollectionUtils.isEmpty(librarySet.getAcls())) {
        librarySet.setAcls(List.of(aclSpec));
      } else {
        librarySet.getAcls().add(aclSpec);
      }
      LibrarySet updatedLibrarySet = librarySetRepository.save(librarySet);
      log.info("SHARED acl added to Measure set [{}]", updatedLibrarySet.getId());
      return updatedLibrarySet;
    } else {
      String error =
          String.format(
              "Library with set id `%s` can not be shared. Library set may not exists.",
              librarySetId, aclSpec.getUserId());
      log.error(error);
      throw new ResourceNotFoundException("LibrarySet", "id", librarySetId);
    }
  }

  public LibrarySet findByLibrarySetId(final String librarySetId) {
    return librarySetRepository.findByLibrarySetId(librarySetId).orElse(null);
  }

  public LibrarySet updateOwnership(String librarySetId, String userId) {
    Optional<LibrarySet> optionalLibrarySet = librarySetRepository.findByLibrarySetId(librarySetId);
    if (optionalLibrarySet.isPresent()) {
      LibrarySet librarySet = optionalLibrarySet.get();
      librarySet.setOwner(userId);
      LibrarySet updatedLibrarySet = librarySetRepository.save(librarySet);
      log.info("Owner changed in Library set [{}]", updatedLibrarySet.getId());
      return updatedLibrarySet;
    } else {
      String error =
          String.format(
              "Library with set id `%s` can not change ownership `%s`, Library set may not exist.",
              librarySetId, userId);
      log.error(error);
      throw new ResourceNotFoundException("LibrarySet", "id", librarySetId);
    }
  }
}
