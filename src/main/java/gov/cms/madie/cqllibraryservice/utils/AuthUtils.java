package gov.cms.madie.cqllibraryservice.utils;

import gov.cms.madie.cqllibraryservice.exceptions.PermissionDeniedException;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.library.CqlLibrary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Slf4j
public class AuthUtils {

  public static void checkAccessPermissions(CqlLibrary cqlLibrary, String username) {
    // TODO: hardcoded allowed ACLs for now
    List<RoleEnum> allowedRoles = List.of(RoleEnum.SHARED_WITH);
    if (!username.equalsIgnoreCase(cqlLibrary.getLibrarySet().getOwner())
        && (CollectionUtils.isEmpty(cqlLibrary.getLibrarySet().getAcls())
            || cqlLibrary.getLibrarySet().getAcls().stream()
                .noneMatch(
                    acl ->
                        acl.getUserId().equalsIgnoreCase(username)
                            && acl.getRoles().stream().anyMatch(allowedRoles::contains)))) {
      log.error(
          "User [{}] does not have permission to modify CQL Library with id [{}]",
          username,
          cqlLibrary.getId());
      throw new PermissionDeniedException("CQL Library", cqlLibrary.getId(), username);
    }
  }
}
