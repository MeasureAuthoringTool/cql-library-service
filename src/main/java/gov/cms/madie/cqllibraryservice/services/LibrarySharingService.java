package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.SharedUser;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.LibrarySetActionLog;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class LibrarySharingService {

  private final CqlLibraryRepository cqlLibraryRepository;
  private final LibrarySetService librarySetService;
  private final ActionLogService actionLogService;
  private final UserServiceClient userServiceClient;
  private final CqlLibraryAccessControlService cqlLibraryAccessControlService;

  public Map<String, List<SharedUser>> getSharedLibraries(
      List<String> libraryIds, String username) {
    Map<String, List<SharedUser>> sharedLibraries = new HashMap<>();

    for (String libraryId : libraryIds) {
      CqlLibrary library = findLibraryForSharing(libraryId);

      if (library.getLibrarySet() == null) {
        throw new ResourceNotFoundException(
            "Library set does not exist for library with ID : " + libraryId);
      }
      if (library.getLibrarySet().getAcls() == null) {
        sharedLibraries.put(libraryId, Collections.emptyList());
      } else {
        List<String> userIds =
            library.getLibrarySet().getAcls().stream()
                .filter(
                    aclSpecification -> aclSpecification.getRoles().contains(RoleEnum.SHARED_WITH))
                .map(AclSpecification::getUserId)
                .toList();
        LibrarySetActionLog librarySetActionLog =
            actionLogService.findLibrarySetActionLogByTargetId(library.getLibrarySetId());

        if (librarySetActionLog != null) {
          List<AccessControlAction> actions = new ArrayList<>(librarySetActionLog.getActions());
          Collections.reverse(actions);
          List<AccessControlAction> shareActions =
              actions.stream()
                  .filter(action -> action.getActionType().equals(ActionType.SHARED))
                  .toList();
          List<SharedUser> sharedUsers =
              userIds.stream()
                  .map(
                      userId -> {
                        SharedUser sharedUser = SharedUser.builder().userId(userId).build();
                        Optional<AccessControlAction> latestShareActionByUserId =
                            shareActions.stream()
                                .filter(action -> action.getSharedWith().equals(userId))
                                .findFirst();
                        latestShareActionByUserId.ifPresent(
                            action -> sharedUser.setPerformedAt(action.getPerformedAt()));

                        return sharedUser;
                      })
                  .toList();
          sharedLibraries.put(libraryId, sharedUsers);
        } else {
          sharedLibraries.put(
              libraryId,
              userIds.stream().map(userId -> SharedUser.builder().userId(userId).build()).toList());
        }
      }
    }
    List<String> userIds =
        sharedLibraries.values().stream()
            .flatMap(List::stream)
            .map(SharedUser::getUserId)
            .distinct()
            .toList();

    Map<String, UserDetailsDto> userDetailsMap = userServiceClient.getBulkUserDetails(userIds);

    sharedLibraries.values().stream()
        .flatMap(List::stream)
        .forEach(
            sharedUser ->
                sharedUser.setDisplayName(
                    librarySetService.formatDisplayName(userDetailsMap, sharedUser.getUserId())));

    return sharedLibraries;
  }

  public Map<String, List<AclSpecification>> shareLibraries(
      Map<String, List<String>> libraryUserIdMap, String performedBy, String accessToken) {
    log.info(
        "User [{}] has called shareLibraries with libraryUserIdMap [{}]",
        performedBy,
        libraryUserIdMap);

    boolean isAdminRole = cqlLibraryAccessControlService.hasAdminRole(performedBy, accessToken);
    verifyShareAuthorization(libraryUserIdMap, performedBy, true, isAdminRole);

    return updateAccessControl(libraryUserIdMap, "Grant", performedBy, isAdminRole, accessToken);
  }

  public Map<String, List<AclSpecification>> unshareLibraries(
      Map<String, List<String>> libraryUserIdMap, String username, String accessToken) {
    log.info(
        "User [{}] has called unshareLibraries with libraryUserIdMap [{}]",
        username,
        libraryUserIdMap);

    boolean isAdminRole = cqlLibraryAccessControlService.hasAdminRole(username, accessToken);
    verifyShareAuthorization(libraryUserIdMap, username, false, isAdminRole);

    return updateAccessControl(libraryUserIdMap, "Revoke", username, isAdminRole, accessToken);
  }

  private void verifyShareAuthorization(
      Map<String, List<String>> libraryUserIdMap,
      String username,
      boolean ownerOnly,
      boolean isAdminRole) {
    log.info(
        "User [{}] has called verifyShareAuthorization to determine whether operation with [{}]"
            + " is allowed to be performed",
        username,
        libraryUserIdMap);

    libraryUserIdMap
        .keySet()
        .forEach(
            libraryId -> {
              CqlLibrary library = findLibraryForSharing(libraryId);
              cqlLibraryAccessControlService.verifyAuthorization(
                  username,
                  library,
                  ownerOnly ? List.of() : List.of(RoleEnum.SHARED_WITH),
                  isAdminRole);
            });
    log.info(
        "User [{}] successfully called verifyShareAuthorization and determined that operation "
            + "with [{}] is allowed to be performed",
        username,
        libraryUserIdMap);
  }

  private AclOperation buildAclOperation(List<String> userIds, String operation) {
    AclOperation.AclAction aclOperationAction =
        operation.equals("Grant") ? AclOperation.AclAction.GRANT : AclOperation.AclAction.REVOKE;
    return AclOperation.builder()
        .acls(buildShareAclSpecifications(userIds))
        .action(aclOperationAction)
        .build();
  }

  private List<AclSpecification> buildShareAclSpecifications(List<String> userIds) {
    return userIds.stream()
        .map(
            userId ->
                AclSpecification.builder()
                    .userId(userId.toLowerCase())
                    .roles(Set.of(RoleEnum.SHARED_WITH))
                    .build())
        .toList();
  }

  private Map<String, List<AclSpecification>> updateAccessControl(
      Map<String, List<String>> libraryUserIdMap,
      String type,
      String performedBy,
      boolean isAdminRole,
      String accessToken) {
    Map<String, List<AclSpecification>> libraryIdToAclSpecification = new HashMap<>();
    libraryUserIdMap.forEach(
        (libraryId, userIds) -> {
          AclOperation aclOperation = buildAclOperation(userIds, type);
          libraryIdToAclSpecification.put(
              libraryId,
              updateAccessControlList(
                  libraryId, aclOperation, performedBy, isAdminRole, accessToken));
        });

    log.info(
        "User [{}] successfully called [{}] with libraryUserIdMap [{}]. The "
            + "AclSpecification is now [{}]",
        performedBy,
        "Grant".equalsIgnoreCase(type) ? "shared library(s)" : "unshareLibraries",
        libraryUserIdMap,
        libraryIdToAclSpecification);
    return libraryIdToAclSpecification;
  }

  List<AclSpecification> updateAccessControlList(
      String cqlLibraryId,
      AclOperation aclOperation,
      String performedBy,
      boolean isAdminRole,
      String accessToken) {
    Optional<CqlLibrary> persistedLibrary = cqlLibraryRepository.findById(cqlLibraryId);
    if (persistedLibrary.isEmpty()) {
      throw new ResourceNotFoundException("Library does not exist: " + cqlLibraryId);
    }

    if (AclOperation.AclAction.GRANT.equals(aclOperation.getAction())) {
      aclOperation
          .getAcls()
          .forEach(
              acl -> cqlLibraryAccessControlService.validateHarpId(acl.getUserId(), accessToken));
    }

    CqlLibrary library = persistedLibrary.get();
    LibrarySet librarySet =
        librarySetService.updateLibrarySetAcls(
            library.getLibrarySetId(), aclOperation, performedBy, isAdminRole);
    return librarySet.getAcls();
  }

  private CqlLibrary findLibraryForSharing(String libraryId) {
    CqlLibrary library =
        cqlLibraryRepository
            .findById(libraryId)
            .orElseThrow(() -> new ResourceNotFoundException("CQL Library", libraryId));
    library.setLibrarySet(librarySetService.findByLibrarySetId(library.getLibrarySetId()));
    return library;
  }
}
