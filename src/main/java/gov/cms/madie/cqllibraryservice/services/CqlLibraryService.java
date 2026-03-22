package gov.cms.madie.cqllibraryservice.services;

import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import gov.cms.madie.cqllibraryservice.dto.*;
import gov.cms.madie.cqllibraryservice.exceptions.*;
import gov.cms.madie.cqllibraryservice.locks.CqlLibraryLock;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.cqllibraryservice.utils.AuthUtils;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.*;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.dto.UserRolesDto;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.CqlLibraryLockInfo;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.models.library.LibrarySet;
import gov.cms.madie.models.measure.ElmJson;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.apache.commons.collections4.CollectionUtils;
import gov.cms.madie.cqllibraryservice.utils.LibraryUtils;

@Slf4j
@Service
@AllArgsConstructor
public class CqlLibraryService {

  private final ElmTranslatorClient elmTranslatorClient;
  private final LibrarySetRepository librarySetRepository;
  private CqlLibraryRepository cqlLibraryRepository;
  private final ActionLogService actionLogService;
  private LibrarySetService librarySetService;
  private MeasureServiceClient measureServiceClient;
  private final AppConfigService appConfigService;
  private final CqlLibraryLockService cqlLibraryLockService;
  private final UserServiceClient userServiceClient;

  public CqlLibrary updateCqlLibrary(CqlLibrary cqlLibrary, String username) {
    if (cqlLibrary == null || StringUtils.isBlank(cqlLibrary.getId())) {
      throw new BadRequestObjectException("CQL Library or CQL Library ID cannot be null.");
    }
    if (StringUtils.isBlank(username)) {
      throw new BadRequestObjectException("Harp id cannot be null or empty.");
    }

    CqlLibrary persistedLibrary = findCqlLibraryById(cqlLibrary.getId(), username);
    AuthUtils.checkAccessPermissions(persistedLibrary, username);

    enforceDraftState(persistedLibrary);
    enforceLocking(cqlLibrary.getId(), username);
    ensureUniqueName(cqlLibrary, persistedLibrary);
    refreshIncludedLibraries(cqlLibrary, persistedLibrary);
    preserveImmutableFields(cqlLibrary, persistedLibrary);

    cqlLibrary.setLastModifiedAt(Instant.now());
    cqlLibrary.setLastModifiedBy(username);

    CqlLibrary savedLibrary = cqlLibraryRepository.save(cqlLibrary);
    actionLogService.logAction(savedLibrary.getId(), ActionType.UPDATED, username, "actionLog");

    return savedLibrary;
  }

  private void enforceDraftState(CqlLibrary persistedLibrary) {
    if (!persistedLibrary.isDraft()) {
      throw new InvalidResourceStateException("CQL Library", persistedLibrary.getId());
    }
  }

  private void enforceLocking(String libraryId, String username) {
    LockInfo lockInfo = cqlLibraryLockService.lockCqlLibrary(libraryId, username);
    if (lockInfo != null && lockInfo.isLocked() && !lockInfo.getLockedBy().equals(username)) {
      throw new ResourceLockedException(
          "Unable to update CQL Library", libraryId, lockInfo.getLockedBy());
    }
  }

  private void ensureUniqueName(CqlLibrary updatedLibrary, CqlLibrary persistedLibrary) {
    if (isCqlLibraryNameChanged(updatedLibrary, persistedLibrary)) {
      checkDuplicateCqlLibraryName(updatedLibrary.getCqlLibraryName());
    }
  }

  private void refreshIncludedLibraries(CqlLibrary updatedLibrary, CqlLibrary persistedLibrary) {
    if (!StringUtils.equals(updatedLibrary.getCql(), persistedLibrary.getCql())) {
      updatedLibrary.setIncludedLibraries(
          LibraryUtils.getIncludedLibraries(updatedLibrary.getCql()));
    }
  }

  private void preserveImmutableFields(CqlLibrary updatedLibrary, CqlLibrary persistedLibrary) {
    updatedLibrary.setLibrarySet(persistedLibrary.getLibrarySet());
    updatedLibrary.setDraft(persistedLibrary.isDraft());
    updatedLibrary.setVersion(persistedLibrary.getVersion());
    updatedLibrary.setCreatedAt(persistedLibrary.getCreatedAt());
    updatedLibrary.setCreatedBy(persistedLibrary.getCreatedBy());
  }

  public Page<LibraryListDTO> getLibrariesByCriteria(
      LibrarySearchCriteria librarySearchCriteria,
      OwnershipType ownershipType,
      Pageable pageReq,
      String username) {

    Page<LibraryListDTO> librariesPage =
        cqlLibraryRepository.searchLibrariesByCriteria(
            username, pageReq, librarySearchCriteria, ownershipType);

    log.debug("Enriching {} libraries with user details", librariesPage.getContent().size());
    enrichWithUserDetails(librariesPage.getContent());

    return librariesPage;
  }

  private void enrichWithUserDetails(List<LibraryListDTO> libraries) {
    if (CollectionUtils.isEmpty(libraries)) {
      log.debug("No libraries to enrich");
      return;
    }

    // Extract unique owner HARP IDs
    List<String> ownerIds =
        libraries.stream()
            .map(lib -> lib.getLibrarySet() != null ? lib.getLibrarySet().getOwner() : null)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());

    log.debug("Found {} unique owner IDs: {}", ownerIds.size(), ownerIds);

    if (ownerIds.isEmpty()) {
      log.debug("No owner IDs found to fetch user details");
      return;
    }

    // Fetch user details in bulk
    Map<String, UserDetailsDto> userDetailsMap = userServiceClient.getBulkUserDetails(ownerIds);

    // Enrich each library with user details
    libraries.forEach(
        library -> {
          if (library.getLibrarySet() != null && library.getLibrarySet().getOwner() != null) {
            String ownerId = library.getLibrarySet().getOwner();
            UserDetailsDto userDetails = userDetailsMap.get(ownerId);

            if (userDetails != null) {
              String firstName = userDetails.getFirstName();
              String lastName = userDetails.getLastName();

              String displayName = "";
              if (StringUtils.isNotBlank(firstName) && StringUtils.isNotBlank(lastName)) {
                displayName = firstName + " " + lastName;
              } else if (StringUtils.isNotBlank(firstName)) {
                displayName = firstName;
              } else if (StringUtils.isNotBlank(lastName)) {
                displayName = lastName;
              }
              library.setOwner(
                  StringUtils.isNotBlank(displayName)
                      ? displayName
                      : StringUtils.isNotBlank(ownerId) ? ownerId : "-");
            } else {
              library.setOwner("-");
            }
          }
        });
  }

  private String getFullName(UserDetailsDto userDetails) {
    String firstName = userDetails.getFirstName() != null ? userDetails.getFirstName() : "";
    String lastName = userDetails.getLastName() != null ? userDetails.getLastName() : "";
    return (firstName + " " + lastName).trim();
  }

  public void checkDuplicateCqlLibraryName(String cqlLibraryName) {
    if (StringUtils.isNotEmpty(cqlLibraryName)
        && cqlLibraryRepository.existsByCqlLibraryName(cqlLibraryName)) {
      throw new DuplicateKeyException("cqlLibraryName", "Library name must be unique.");
    }
  }

  public boolean isCqlLibraryNameChanged(CqlLibrary cqlLibrary, CqlLibrary persistedCqlLibrary) {
    return !Objects.equals(persistedCqlLibrary.getCqlLibraryName(), cqlLibrary.getCqlLibraryName());
  }

  public CqlLibrary getVersionedCqlLibrary(
      String name,
      String version,
      Optional<String> model,
      boolean fetchElm,
      String elmErrorSeverity,
      final String accessToken) {
    List<CqlLibrary> libs =
        model.isPresent()
            ? cqlLibraryRepository.findAllByCqlLibraryNameAndDraftAndVersionAndModel(
                name, false, Version.parse(version), model.get())
            : cqlLibraryRepository.findAllByCqlLibraryNameAndDraftAndVersion(
                name, false, Version.parse(version));
    if (CollectionUtils.isEmpty(libs)) {
      log.error("Could not find Library resource with name: [{}] Version: [{}]", name, version);
      throw new ResourceNotFoundException("Library", "name", name);
    } else if (libs.size() > 1) {
      log.error("Multiple versioned libraries were found for [{}] Version: [{}]", name, version);
      throw new GeneralConflictException(
          "Multiple versioned libraries were found. "
              + "Please provide additional filters "
              + "to narrow down the results to a single library.");
    } else {
      CqlLibrary cqlLibrary = libs.get(0);
      if (fetchElm) {
        try {
          final ElmJson elmJson =
              elmTranslatorClient.getElmJson(
                  cqlLibrary.getCql(), cqlLibrary.getModel(), accessToken, elmErrorSeverity);
          if (elmTranslatorClient.hasErrors(elmJson)) {
            log.error("CQL-ELM translator found errors in the CQL for library [{}]!", name);
            throw new CqlElmTranslationErrorException(cqlLibrary.getCqlLibraryName());
          }
          cqlLibrary.setElmJson(elmJson.getJson());
          cqlLibrary.setElmXml(elmJson.getXml());
        } catch (CqlElmTranslationServiceException | CqlElmTranslationErrorException e) {
          throw e;
        }
      }
      LibrarySet librarySet = librarySetService.findByLibrarySetId(cqlLibrary.getLibrarySetId());
      cqlLibrary.setLibrarySet(librarySet);
      return cqlLibrary;
    }
  }

  public CqlLibrary findCqlLibraryById(String id, String username) {
    Optional<CqlLibrary> optionalLibrary = cqlLibraryRepository.findById(id);
    if (optionalLibrary.isPresent()) {
      CqlLibrary cqlLibrary = optionalLibrary.get();
      LibrarySet librarySet = librarySetService.findByLibrarySetId(cqlLibrary.getLibrarySetId());
      cqlLibrary.setLibrarySet(librarySet);

      CqlLibraryLock lock = cqlLibraryLockService.findByCqlLibraryId(id);
      cqlLibrary.setCqlLibraryLock(
          lock != null && !username.equalsIgnoreCase(lock.getLockedBy())
              ? CqlLibraryLockInfo.builder()
                  .cqlLibraryId(lock.getCqlLibraryId())
                  .lockedBy(lock.getLockedBy())
                  .build()
              : null);

      return cqlLibrary;
    }
    log.error("CqlLibrary with library ID [{}] was not found", id);
    throw new ResourceNotFoundException("CQL Library", id);
  }

  public List<AclSpecification> updateAccessControlList(
      String cqlLibraryId, AclOperation aclOperation, String performedBy, boolean isAdminRole) {
    Optional<CqlLibrary> persistedLibrary = cqlLibraryRepository.findById(cqlLibraryId);
    if (persistedLibrary.isEmpty()) {
      throw new ResourceNotFoundException("Library does not exist: " + cqlLibraryId);
    }

    CqlLibrary library = persistedLibrary.get();
    LibrarySet librarySet =
        librarySetService.updateLibrarySetAcls(
            library.getLibrarySetId(), aclOperation, performedBy, isAdminRole);
    return librarySet.getAcls();
  }

  public CqlLibrary deleteDraftLibrary(final String id, final String userId) {
    enforceLocking(id, userId);
    CqlLibrary cqlLibrary = findCqlLibraryById(id, userId);
    if (!userId.equalsIgnoreCase(cqlLibrary.getLibrarySet().getOwner())) {
      throw new PermissionDeniedException("CQL Library", cqlLibrary.getId(), userId);
    }

    enforceDraftState(cqlLibrary);
    cqlLibraryRepository.delete(cqlLibrary);
    cqlLibraryLockService.unlockCqlLibrary(cqlLibrary.getId(), userId);
    return cqlLibrary;
  }

  public List<LibraryUsage> findLibraryUsage(String libraryName) {
    if (StringUtils.isBlank(libraryName)) {
      throw new BadRequestObjectException("Please provide library name.");
    }
    // check if library exists before finding usage and delete
    if (!cqlLibraryRepository.existsByCqlLibraryName(libraryName)) {
      throw new ResourceNotFoundException("Library", "name", libraryName);
    }
    return cqlLibraryRepository.findLibraryUsageByLibraryName(libraryName);
  }

  /**
   * Library is being used if any of its version is either included in other library or measure
   *
   * @param name - library name
   * @param accessToken accessToken
   * @return true/false
   */
  public boolean isLibraryBeinUsed(String name, String accessToken) {
    // check usage in libraries
    List<LibraryUsage> usageInLibraries = findLibraryUsage(name);
    if (CollectionUtils.isEmpty(usageInLibraries)) {
      // check usage in measures
      List<LibraryUsage> usageInMeasures =
          measureServiceClient.getLibraryUsageInMeasures(name, accessToken);
      return CollectionUtils.isNotEmpty(usageInMeasures);
    }
    return true;
  }

  /**
   * This method deletes cql library and its versions permanently, if none of the versions is being
   * used either in measure or another library
   *
   * @param name - library name
   * @param accessToken - auth token
   */
  public void deleteLibraryAlongWithVersions(String name, String accessToken, String harpId) {
    if (isLibraryBeinUsed(name, accessToken)) {
      throw new GeneralConflictException(
          "Library is being used actively, hence can not be deleted.");
    }
    List<CqlLibrary> libraries = cqlLibraryRepository.findAllByCqlLibraryName(name);

    for (CqlLibrary cqlLibrary : libraries) {
      LibrarySet librarySet = librarySetService.findByLibrarySetId(cqlLibrary.getLibrarySetId());

      if (!librarySet.getOwner().equals(harpId)) {
        throw new HarpIdMismatchException(harpId, librarySet.getOwner(), cqlLibrary.getId());
      }
    }
    cqlLibraryRepository.deleteAll(libraries);
  }

  public List<LibraryListDTO> findLibrariesByNameAndModel(String libraryName, String model) {
    if (StringUtils.isBlank(libraryName) || StringUtils.isBlank(model)) {
      throw new BadRequestObjectException("Please provide library name and model.");
    }
    return cqlLibraryRepository.findLibrariesByNameAndModelOrderByNameAscAndVersionDsc(
        libraryName, model);
  }

  /**
   * Get the versioned libraries that belongs to given set id
   *
   * @param librarySetId - set id of a Library
   * @return LibrarySetDTO - DTO containing all the versioned libraries for a set id and library set
   *     itself
   */
  public LibrarySetDTO getLibrarySetBySetId(String librarySetId) {
    if (StringUtils.isBlank(librarySetId)) {
      throw new BadRequestObjectException("Please provide library set ID.");
    }
    List<CqlLibrary> libraries =
        cqlLibraryRepository.findByLibrarySetIdAndDraftAndActive(librarySetId, false, true);
    if (CollectionUtils.isEmpty(libraries)) {
      return null;
    }
    LibrarySet librarySet = librarySetRepository.findByLibrarySetId(librarySetId).orElse(null);
    return LibrarySetDTO.builder().libraries(libraries).librarySet(librarySet).build();
  }

  public List<LibraryListDTO> getLibrariesByLibrarySetId(
      String librarySetId,
      boolean sortByLatestVersion,
      LibrarySearchCriteria librarySearchCriteria) {
    if (StringUtils.isBlank(librarySetId)) {
      throw new BadRequestObjectException("Please provide library set ID.");
    }
    List<LibraryListDTO> librariesByLibrarySetId =
        cqlLibraryRepository.findLibrariesByLibrarySetId(
            librarySetId, sortByLatestVersion, librarySearchCriteria);

    log.debug("Enriching {} libraries with user details", librariesByLibrarySetId.size());

    if (CollectionUtils.isNotEmpty(librariesByLibrarySetId)
        && librariesByLibrarySetId.get(0).getLibrarySet() != null) {
      UserDetailsDto singleUserDetails =
          userServiceClient.getSingleUserDetails(
              librariesByLibrarySetId.get(0).getLibrarySet().getOwner());
      if (singleUserDetails != null) {
        librariesByLibrarySetId.forEach(
            library -> library.setOwner(getFullName(singleUserDetails)));
      }
    }

    return librariesByLibrarySetId;
  }

  public boolean hasAssociatedLibraries(LibraryListDTO library) {
    return cqlLibraryRepository.countAllByLibrarySetIdAndActiveAndIdIsNot(
            library.getLibrarySetId(), true, library.getId())
        > 0;
  }

  public Map<String, List<SharedUser>> getSharedLibraries(
      List<String> libraryIds, String username) {
    Map<String, List<SharedUser>> sharedLibraries = new HashMap<>();

    for (String libraryId : libraryIds) {
      CqlLibrary library = findCqlLibraryById(libraryId, username);

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
          Collections.reverse(librarySetActionLog.getActions());
          List<AccessControlAction> shareActions =
              librarySetActionLog.getActions().stream()
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
    return sharedLibraries;
  }

  public Map<String, List<AclSpecification>> shareLibraries(
      Map<String, List<String>> libraryUserIdMap, String performedBy, String accessToken) {
    Map<String, List<AclSpecification>> libraryIdToAclSpecification = new HashMap<>();

    log.info(
        "User [{}] has called shareLibraries with libraryUserIdMap [{}]",
        performedBy,
        libraryUserIdMap);

    boolean isAdminRole = hasAdminRole(performedBy, accessToken);
    verifyShareAuthorization(libraryUserIdMap, performedBy, true, isAdminRole);

    libraryUserIdMap.forEach(
        (LibraryId, userIds) -> {
          AclOperation aclOperation = buildAclOperation(userIds, "Grant");
          libraryIdToAclSpecification.put(
              LibraryId,
              updateAccessControlList(LibraryId, aclOperation, performedBy, isAdminRole));
        });

    log.info(
        "User [{}] successfully called shared library(s) with libraryUserIdMap [{}]. The "
            + "AclSpecification is now [{}]",
        performedBy,
        libraryUserIdMap,
        libraryIdToAclSpecification);

    return libraryIdToAclSpecification;
  }

  public Map<String, List<AclSpecification>> unshareLibraries(
      Map<String, List<String>> libraryUserIdMap, String username, String accessToken) {
    log.info(
        "User [{}] has called unshareLibraries with libraryUserIdMap [{}]",
        username,
        libraryUserIdMap);

    Map<String, List<AclSpecification>> libraryIdToAclSpecification = new HashMap<>();

    boolean isAdminRole = hasAdminRole(username, accessToken);
    verifyShareAuthorization(libraryUserIdMap, username, false, isAdminRole);

    libraryUserIdMap.forEach(
        (libraryId, userIds) -> {
          AclOperation aclOperation = buildAclOperation(userIds, "Revoke");
          libraryIdToAclSpecification.put(
              libraryId, updateAccessControlList(libraryId, aclOperation, username, isAdminRole));
        });

    log.info(
        "User [{}] successfully called unshareLibraries with libraryUserIdMap [{}]. The "
            + "AclSpecification is now [{}]",
        username,
        libraryUserIdMap,
        libraryIdToAclSpecification);

    return libraryIdToAclSpecification;
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
              CqlLibrary library = findCqlLibraryById(libraryId, username);
              verifyAuthorization(
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
    // allow admin user and no further verification
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
    if (!librarySet.getOwner().equalsIgnoreCase(username)
        && (org.springframework.util.CollectionUtils.isEmpty(librarySet.getAcls())
            || librarySet.getAcls().stream()
                .noneMatch(
                    acl ->
                        acl.getUserId().equalsIgnoreCase(username)
                            && acl.getRoles().stream().anyMatch(allowedRoles::contains)))) {
      throw new UnauthorizedException(target, targetId, username);
    }
  }

  public List<String> transferLibraries(
      List<String> libraryIds,
      String harpId,
      boolean retainShareAccess,
      String conductedBy,
      String accessToken) {
    List<String> failedLibraries = new ArrayList<>();
    boolean isAdmin = hasAdminRole(conductedBy, accessToken);
    for (String libraryId : libraryIds) {
      try {
        CqlLibrary cqlLibrary = findCqlLibraryById(libraryId, harpId);
        if (!isAdmin) {
          AuthUtils.checkOwnership(cqlLibrary, conductedBy);
        }
        librarySetService.updateOwnership(
            cqlLibrary.getLibrarySetId(), harpId, retainShareAccess, conductedBy, isAdmin);
      } catch (RuntimeException e) {
        log.warn(
            "User [{}] failed to change ownership of library [{}] to user [{}]",
            conductedBy,
            libraryId,
            harpId,
            e);
        failedLibraries.add(libraryId);
      }
    }
    return failedLibraries;
  }

  public List<Action> getCqlLibraryHistory(String cqlLibraryId, String userName) {
    if (StringUtils.isBlank(cqlLibraryId)) {
      throw new InvalidRequestException("Cql Library ID cannot be null or empty.");
    }
    Optional<CqlLibrary> persistedCqlLibrary = cqlLibraryRepository.findById(cqlLibraryId);
    if (persistedCqlLibrary.isEmpty()) {
      throw new ResourceNotFoundException("Cql Library does not exist: " + cqlLibraryId);
    }
    List<Action> cqlLibraryHistory =
        actionLogService.findCqlLibraryHistory(
            cqlLibraryId, persistedCqlLibrary.get().getLibrarySetId());
    log.info(
        "User [{}] successfully retrieved the history of the cql library with ID [{}]",
        userName,
        cqlLibraryId);
    return cqlLibraryHistory;
  }

  private boolean hasAdminRole(String conductedBy, String accessToken) {
    boolean isAdmin = false;
    UserRolesDto userRolesDto = userServiceClient.getUserRoles(conductedBy, accessToken);
    if (userRolesDto != null
        && userRolesDto.getRoles() != null
        && userRolesDto.getRoles().contains("MADiE-Admin")) {
      log.info("User [{}] has MADiE-Admin role", conductedBy);
      isAdmin = true;
    }
    return isAdmin;
  }
}
