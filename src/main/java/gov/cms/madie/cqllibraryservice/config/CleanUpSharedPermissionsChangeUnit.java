package gov.cms.madie.cqllibraryservice.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;

import gov.cms.madie.models.library.LibrarySet;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.cqllibraryservice.services.ActionLogService;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.ActionType;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "cleanup_shared_permissions_for_owners", order = "1", author = "madie_dev")
public class CleanUpSharedPermissionsChangeUnit {
  private List<LibrarySet> librarySets = new ArrayList<>();
  private List<LibrarySet> originalLibrarySets = new ArrayList<>();
  private List<LibrarySet> updatedLibrarySets = new ArrayList<>();
  private final ActionLogService actionLogService;

  public CleanUpSharedPermissionsChangeUnit(ActionLogService actionLogService) {
    this.actionLogService = actionLogService;
  }

  @Execution
  public List<LibrarySet> cleanUpSharedPermissionsForOwners(
      LibrarySetRepository librarySetRepository) {
    librarySets = librarySetRepository.findAll();

    for (LibrarySet librarySet : librarySets) {
      List<AclSpecification> updatedAcls = new ArrayList<>();
      Map<String, ActionType> actionLogDetails = new HashMap<>();

      if (CollectionUtils.isNotEmpty(librarySet.getAcls())) {
        for (AclSpecification acl : librarySet.getAcls()) {
          if (librarySet.getOwner().equalsIgnoreCase(acl.getUserId())
              && acl.getRoles().contains(RoleEnum.SHARED_WITH)) {
            acl.getRoles().remove(RoleEnum.SHARED_WITH);
            log.info(
                "remove SHARED_WITH for librarySetId: [{}], owner: [{}]",
                librarySet.getLibrarySetId(),
                librarySet.getOwner());
            actionLogDetails.put(librarySet.getOwner(), ActionType.UNSHARED);
          }
          if (!acl.getRoles().isEmpty()) {
            updatedAcls.add(acl);
          }
        }
        if (librarySet.getAcls().size() != updatedAcls.size()) {
          // save to originalLibrarySets before update, for possible roll back
          originalLibrarySets.add(librarySet);
          librarySet.setAcls(updatedAcls);
          updatedLibrarySets.add(librarySet);
          librarySetRepository.save(librarySet);
          actionLogDetails.forEach(
              (userId, actionType) -> {
                actionLogService.logShareAccessControlAction(
                    librarySet.getLibrarySetId(),
                    actionType,
                    "admin",
                    userId,
                    "Cleaning up share access on owned measure");
              });
          log.info(
              "Logging actions for LibrarySetId: [{}], owner: [{}], size: [{}]",
              librarySet.getLibrarySetId(),
              librarySet.getOwner(),
              actionLogDetails.size());
        }
      }
    }
    log.info("updatedLibrarySets -> [{}]", updatedLibrarySets.toString());
    return updatedLibrarySets;
  }

  @RollbackExecution
  public void rollbackExecution(LibrarySetRepository librarySetRepository) {
    log.debug("Entering rollbackExecution()");

    librarySetRepository.deleteAll(updatedLibrarySets);
    librarySetRepository.saveAll(originalLibrarySets);
  }
}
