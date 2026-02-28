package gov.cms.madie.cqllibraryservice.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nimbusds.oauth2.sdk.util.CollectionUtils;

import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.library.LibrarySet;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChangeUnit(id = "remove_duplicate_acls", order = "1", author = "madie_dev")
public class RemoveDuplicateAclsChangeUnit {
  List<LibrarySet> copyOfAllLibrarySets = null;

  @Execution
  public void removeDuplicateAcls(LibrarySetRepository librarySetRepository) {
    log.info("Entering removeDuplicateAcls()");
    List<LibrarySet> allLibrarySets = librarySetRepository.findAll();
    if (CollectionUtils.isNotEmpty(allLibrarySets)) {
      copyOfAllLibrarySets = new ArrayList<>(allLibrarySets);
      log.info("copyOfAllLibrarySets size = " + copyOfAllLibrarySets.size());
      for (LibrarySet librarySet : allLibrarySets) {
        List<AclSpecification> acls = librarySet.getAcls();
        if (CollectionUtils.isNotEmpty(acls)) {
          librarySet.setAcls(removeDuplicatesWithSharedWith(acls));
          librarySetRepository.save(librarySet);
        }
      }
    }
  }

  private List<AclSpecification> removeDuplicatesWithSharedWith(List<AclSpecification> aclList) {
    Map<String, AclSpecification> map = new HashMap<>();
    for (AclSpecification acl : aclList) {
      if (acl.getRoles() != null && acl.getRoles().contains(RoleEnum.SHARED_WITH)) {
        String lowerUserId = acl.getUserId().toLowerCase();
        acl.setUserId(lowerUserId); // ensure the userId is saved as lowercase
        map.put(lowerUserId, acl);
      }
    }
    return new ArrayList<>(map.values());
  }

  @RollbackExecution
  public void rollbackExecution(LibrarySetRepository librarySetRepository) {
    log.info("Entering rollbackExecution()");
    if (CollectionUtils.isNotEmpty(copyOfAllLibrarySets)) {
      log.info("roll back " + copyOfAllLibrarySets.size() + " library sets.");
      librarySetRepository.saveAll(copyOfAllLibrarySets);
    }
  }
}
