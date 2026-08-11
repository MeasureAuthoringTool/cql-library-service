package gov.cms.madie.cqllibraryservice.services;

import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import gov.cms.madie.cqllibraryservice.dto.*;
import gov.cms.madie.cqllibraryservice.exceptions.*;
import gov.cms.madie.cqllibraryservice.locks.CqlLibraryLock;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.cqllibraryservice.repositories.ExternalLibraryRepository;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.*;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.CqlLibraryLockInfo;
import gov.cms.madie.models.library.CqlLibraryReview;
import gov.cms.madie.cqllibraryservice.models.ExternalLibrary;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryReviewRepository;
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
  private final CqlLibraryAccessControlService cqlLibraryAccessControlService;
  private final CqlLibraryReviewRepository cqlLibraryReviewRepository;
  private final ExternalLibraryRepository externalLibraryRepository;

  public CqlLibrary updateCqlLibrary(CqlLibrary cqlLibrary, String username) {
    if (cqlLibrary == null || StringUtils.isBlank(cqlLibrary.getId())) {
      throw new BadRequestObjectException("CQL Library or CQL Library ID cannot be null.");
    }
    if (StringUtils.isBlank(username)) {
      throw new BadRequestObjectException("Harp id cannot be null or empty.");
    }

    CqlLibrary persistedLibrary = findCqlLibraryById(cqlLibrary.getId(), username);
    cqlLibraryAccessControlService.checkAccessPermissions(persistedLibrary, username);

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

  /**
   * Builds an enriched list of {@link LibraryListDTO} for the given libraries keyed by review
   * status. Fetches the libraries, maps them to DTOs (with review status) and enriches owner
   * display names. The full list is returned (no pagination); ordering is left to the client.
   * Authorization is NOT enforced here; callers (e.g. {@code CqlLibraryReviewService}) must verify
   * access first.
   *
   * @param statusByLibraryId map of library id to its review status (typically READY_FOR_REVIEW)
   * @return the enriched list of {@link LibraryListDTO} for the requested libraries
   */
  public List<LibraryListDTO> getReviewLibraries(Map<String, ReviewStatus> statusByLibraryId) {
    if (statusByLibraryId == null || statusByLibraryId.isEmpty()) {
      return List.of();
    }

    List<LibraryListDTO> libraries =
        cqlLibraryRepository.findByIdIn(statusByLibraryId.keySet()).stream()
            .map(library -> toReviewLibraryListDTO(library, statusByLibraryId.get(library.getId())))
            .collect(Collectors.toList());

    enrichWithUserDetails(libraries);

    return libraries;
  }

  private LibraryListDTO toReviewLibraryListDTO(CqlLibrary library, ReviewStatus reviewStatus) {
    LibrarySet librarySet = librarySetService.findByLibrarySetId(library.getLibrarySetId());
    return LibraryListDTO.builder()
        .id(library.getId())
        .librarySetId(library.getLibrarySetId())
        .cqlLibraryName(library.getCqlLibraryName())
        .model(library.getModel())
        .version(library.getVersion())
        .draft(library.isDraft())
        .createdAt(library.getCreatedAt())
        .lastModifiedAt(library.getLastModifiedAt())
        .librarySet(librarySet)
        .reviewStatus(ReviewStatus.READY_FOR_REVIEW.equals(reviewStatus) ? "Ready" : "")
        .build();
  }

  private void enrichWithUserDetails(List<LibraryListDTO> libraries) {
    if (CollectionUtils.isEmpty(libraries)) {
      return;
    }

    List<String> ownerIds =
        libraries.stream()
            .map(lib -> lib.getLibrarySet() != null ? lib.getLibrarySet().getOwner() : null)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());

    if (ownerIds.isEmpty()) {
      return;
    }

    Map<String, UserDetailsDto> userDetailsMap = userServiceClient.getBulkUserDetails(ownerIds);

    libraries.forEach(
        library -> {
          if (library.getLibrarySet() != null && library.getLibrarySet().getOwner() != null) {
            String ownerId = library.getLibrarySet().getOwner();
            UserDetailsDto userDetails = userDetailsMap.get(ownerId);
            library.setOwnerDisplayName(resolveOwnerDisplayName(userDetails, ownerId));
          }
        });
  }

  /**
   * Resolves an owner's display name with a consistent fallback chain: full/partial name when the
   * user is found and named, otherwise the owner's HARP id, otherwise "-". A null {@code
   * userDetails} means the user-service lookup failed (service error / not found), which falls
   * through to "-".
   *
   * @param userDetails user details from the user service, or null if the lookup failed
   * @param ownerId the owner's HARP id
   * @return the resolved display name
   */
  private String resolveOwnerDisplayName(UserDetailsDto userDetails, String ownerId) {
    if (userDetails == null) {
      return "-";
    }
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

    return StringUtils.isNotBlank(displayName)
        ? displayName
        : StringUtils.isNotBlank(ownerId) ? ownerId : "-";
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
    return getVersionedCqlLibrary(
        name, version, model, Optional.empty(), fetchElm, elmErrorSeverity, accessToken);
  }

  public CqlLibrary getVersionedCqlLibrary(
      String name,
      String version,
      Optional<String> model,
      Optional<String> namespaceCanonical,
      boolean fetchElm,
      String elmErrorSeverity,
      final String accessToken) {
    if (namespaceCanonical.isPresent() && StringUtils.isNotBlank(namespaceCanonical.get())) {
      ExternalLibrary externalLibrary =
          externalLibraryRepository
              .findByPackageCanonicalAndLibraryNameAndVersion(
                  namespaceCanonical.get(), name, version)
              .orElseThrow(
                  () -> {
                    log.error(
                        "Could not find Library with canonical: [{}], name: [{}], version: [{}]",
                        namespaceCanonical.get(),
                        name,
                        version);
                    return new ResourceNotFoundException(
                        "Library", "name", name + " in namespace " + namespaceCanonical.get());
                  });
      return CqlLibrary.builder()
          .cqlLibraryName(externalLibrary.getLibraryName())
          .version(Version.parse(externalLibrary.getVersion()))
          .cql(externalLibrary.getCqlContent())
          .draft(externalLibrary.isDraft())
          .build();
    }
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
        final ElmJson elmJson =
            elmTranslatorClient.getElmJson(
                cqlLibrary.getCql(), cqlLibrary.getModel(), accessToken, elmErrorSeverity);
        if (elmTranslatorClient.hasErrors(elmJson)) {
          log.error("CQL-ELM translator found errors in the CQL for library [{}]!", name);
          throw new CqlElmTranslationErrorException(cqlLibrary.getCqlLibraryName());
        }
        cqlLibrary.setElmJson(elmJson.getJson());
        cqlLibrary.setElmXml(elmJson.getXml());
      }
      LibrarySet librarySet = librarySetService.findByLibrarySetId(cqlLibrary.getLibrarySetId());
      cqlLibrary.setLibrarySet(librarySet);
      if (librarySet != null && StringUtils.isNotBlank(librarySet.getOwner())) {
        UserDetailsDto details = userServiceClient.getSingleUserDetails(librarySet.getOwner());
        cqlLibrary.setOwnerDisplayName(resolveOwnerDisplayName(details, librarySet.getOwner()));
      }

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

  /**
   * Deletes a single CQL library version permanently by its document ID
   *
   * @param id - MongoDB document ID of the library to delete
   * @param harpId - HARP ID of the library owner, validated against the actual owner to prevent
   *     unintended deletions
   * @param username - username of the admin performing the deletion, used for audit logging
   * @return the deleted CqlLibrary
   */
  public CqlLibrary deleteCqlLibraryById(String id, String harpId, String username) {
    CqlLibrary library =
        cqlLibraryRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("CqlLibrary", "id", id));

    LibrarySet librarySet = librarySetService.findByLibrarySetId(library.getLibrarySetId());

    if (librarySet == null) {
      throw new ResourceNotFoundException("LibrarySet", "librarySetId", library.getLibrarySetId());
    }

    if (!librarySet.getOwner().equalsIgnoreCase(harpId)) {
      throw new HarpIdMismatchException(harpId, librarySet.getOwner(), library.getId());
    }

    cqlLibraryRepository.delete(library);
    actionLogService.logAction(id, ActionType.DELETED, username, "actionLog");

    return library;
  }

  public List<LibraryListDTO> findLibrariesByNameAndModel(String libraryName, String model) {
    if (StringUtils.isBlank(libraryName) || StringUtils.isBlank(model)) {
      throw new BadRequestObjectException("Please provide library name and model.");
    }
    List<LibraryListDTO> libraries =
        cqlLibraryRepository.findLibrariesByNameAndModelOrderByNameAscAndVersionDsc(
            libraryName, model);
    enrichWithUserDetails(libraries);
    return libraries;
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
            library -> library.setOwnerDisplayName(getFullName(singleUserDetails)));
      }
    }

    enrichWithReviewStatus(librarySetId, librariesByLibrarySetId);

    return librariesByLibrarySetId;
  }

  private void enrichWithReviewStatus(String librarySetId, List<LibraryListDTO> libraries) {
    if (CollectionUtils.isEmpty(libraries)) {
      return;
    }
    Set<String> readyForReviewLibraryIds =
        cqlLibraryReviewRepository.findAllByLibrarySetId(librarySetId).stream()
            .filter(review -> ReviewStatus.READY_FOR_REVIEW.equals(review.getStatus()))
            .map(CqlLibraryReview::getLibraryId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    libraries.forEach(
        library ->
            library.setReviewStatus(
                readyForReviewLibraryIds.contains(library.getId()) ? "Ready" : ""));
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

    return updateAccessControll(libraryUserIdMap, "Grant", performedBy, isAdminRole, accessToken);
  }

  public Map<String, List<AclSpecification>> unshareLibraries(
      Map<String, List<String>> libraryUserIdMap, String username, String accessToken) {
    log.info(
        "User [{}] has called unshareLibraries with libraryUserIdMap [{}]",
        username,
        libraryUserIdMap);

    boolean isAdminRole = cqlLibraryAccessControlService.hasAdminRole(username, accessToken);
    verifyShareAuthorization(libraryUserIdMap, username, false, isAdminRole);

    return updateAccessControll(libraryUserIdMap, "Revoke", username, isAdminRole, accessToken);
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

  public List<String> transferLibraries(
      List<String> libraryIds,
      String harpId,
      boolean retainShareAccess,
      String conductedBy,
      String accessToken) {
    cqlLibraryAccessControlService.validateHarpId(harpId, accessToken);

    List<String> failedLibraries = new ArrayList<>();
    boolean isAdmin = cqlLibraryAccessControlService.hasAdminRole(conductedBy, accessToken);
    for (String libraryId : libraryIds) {
      try {
        CqlLibrary cqlLibrary = findCqlLibraryById(libraryId, harpId);
        if (!isAdmin) {
          cqlLibraryAccessControlService.checkOwnership(cqlLibrary, conductedBy);
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
    librarySetService.populatePerformedByDisplayNames(cqlLibraryHistory);
    log.info(
        "User [{}] successfully retrieved the history of the cql library with ID [{}]",
        userName,
        cqlLibraryId);
    return cqlLibraryHistory;
  }

  private Map<String, List<AclSpecification>> updateAccessControll(
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
}
