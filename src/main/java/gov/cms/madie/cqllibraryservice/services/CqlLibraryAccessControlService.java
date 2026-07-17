package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.exceptions.InvalidIdException;
import gov.cms.madie.cqllibraryservice.exceptions.PermissionDeniedException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.exceptions.UnauthorizedException;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.access.UserStatus;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.dto.UserRolesDto;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@AllArgsConstructor
public class CqlLibraryAccessControlService {

  private final LibrarySetService librarySetService;
  private final UserServiceClient userServiceClient;

  public void checkAccessPermissions(CqlLibrary cqlLibrary, String username) {
    if (!isOwnerOrHasAnyRole(cqlLibrary.getLibrarySet(), username, List.of(RoleEnum.SHARED_WITH))) {
      log.error(
          "User [{}] does not have permission to modify CQL Library with id [{}]",
          username,
          cqlLibrary.getId());
      throw new PermissionDeniedException("CQL Library", cqlLibrary.getId(), username);
    }
  }

  public void checkOwnership(CqlLibrary cqlLibrary, String username) {
    if (cqlLibrary.getLibrarySet() == null
        || !cqlLibrary.getLibrarySet().getOwner().equalsIgnoreCase(username)) {
      log.error(
          "User [{}] is not the owner of CQL Library with id [{}]. Owner is [{}]",
          username,
          cqlLibrary.getId(),
          cqlLibrary.getLibrarySet() == null ? null : cqlLibrary.getLibrarySet().getOwner());
      throw new PermissionDeniedException("CQL Library", cqlLibrary.getId(), username);
    }
  }

  public void verifyAuthorization(
      String username, CqlLibrary library, List<RoleEnum> roles, boolean isAdminRole) {
    LibrarySet librarySet =
        library.getLibrarySet() == null
            ? librarySetService.findByLibrarySetId(library.getLibrarySetId())
            : library.getLibrarySet();
    if (librarySet == null) {
      throw new ResourceNotFoundException(
          "No library set exists for library with ID : " + library.getId());
    }
    if (isAdminRole) {
      return;
    }
    verifyLibrarySetAuthorization(username, "CqlLibrary", library.getId(), roles, librarySet);
  }

  public void verifyLibrarySetAuthorization(
      String username,
      String target,
      String targetId,
      List<RoleEnum> roles,
      LibrarySet librarySet) {
    List<RoleEnum> allowedRoles = roles == null ? List.of() : roles;
    if (!isOwnerOrHasAnyRole(librarySet, username, allowedRoles)) {
      throw new UnauthorizedException(target, targetId, username);
    }
  }

  public boolean hasAdminRole(String conductedBy, String accessToken) {
    UserRolesDto userRolesDto = userServiceClient.getUserRoles(conductedBy, accessToken);
    boolean isAdmin =
        userRolesDto != null
            && userRolesDto.getRoles() != null
            && userRolesDto.getRoles().contains("MADiE-Admin");
    if (isAdmin) {
      log.info("User [{}] has MADiE-Admin role", conductedBy);
    }
    return isAdmin;
  }

  public void validateHarpId(String userId, String accessToken) {
    UserDetailsDto userDetailsDto = userServiceClient.getUserDetails(userId, accessToken);
    if (userDetailsDto == null || !UserStatus.ACTIVE.equals(userDetailsDto.getUserStatus())) {
      throw new InvalidIdException(
          "The provided HARP ID is not associated with an active MADiE user.");
    }
  }

  private boolean isOwnerOrHasAnyRole(
      LibrarySet librarySet, String username, List<RoleEnum> roles) {
    return librarySet != null
        && librarySet.getOwner() != null
        && (librarySet.getOwner().equalsIgnoreCase(username)
            || hasMatchingAclRole(librarySet.getAcls(), username, roles));
  }

  private boolean hasMatchingAclRole(
      List<AclSpecification> acls, String username, List<RoleEnum> allowedRoles) {
    return !CollectionUtils.isEmpty(acls)
        && acls.stream()
            .anyMatch(
                acl ->
                    acl.getUserId().equalsIgnoreCase(username)
                        && acl.getRoles().stream().anyMatch(allowedRoles::contains));
  }
}
