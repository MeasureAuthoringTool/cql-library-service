package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.LibrarySetDTO;
import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.dto.SharedUser;
import gov.cms.madie.cqllibraryservice.exceptions.*;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.access.AclOperation;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.LibrarySetActionLog;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.models.library.LibrarySet;
import gov.cms.madie.models.measure.ElmJson;

import java.util.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.apache.commons.collections4.CollectionUtils;

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

  public Page<LibraryListDTO> getLibrariesByCriteria(
      String searchCriteria, boolean filterByCurrentUser, Pageable pageReq, String username) {
    return cqlLibraryRepository.searchLibrariesByCriteria(
        username, pageReq, searchCriteria, filterByCurrentUser);
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

  public CqlLibrary findCqlLibraryById(String id) {
    Optional<CqlLibrary> optionalLibrary = cqlLibraryRepository.findById(id);
    if (optionalLibrary.isPresent()) {
      CqlLibrary cqlLibrary = optionalLibrary.get();
      LibrarySet librarySet = librarySetService.findByLibrarySetId(cqlLibrary.getLibrarySetId());
      cqlLibrary.setLibrarySet(librarySet);
      return cqlLibrary;
    }
    log.error("CqlLibrary with library ID [{}] was not found", id);
    throw new ResourceNotFoundException("CQL Library", id);
  }

  public List<AclSpecification> updateAccessControlList(
      String cqlLibraryId, AclOperation aclOperation, String performedBy) {
    Optional<CqlLibrary> persistedLibrary = cqlLibraryRepository.findById(cqlLibraryId);
    if (persistedLibrary.isEmpty()) {
      throw new ResourceNotFoundException("Library does not exist: " + cqlLibraryId);
    }

    CqlLibrary library = persistedLibrary.get();
    LibrarySet librarySet =
        librarySetService.updateLibrarySetAcls(
            library.getLibrarySetId(), aclOperation, performedBy);
    return librarySet.getAcls();
  }

  public boolean changeOwnership(String id, String userid) {
    boolean result = false;
    Optional<CqlLibrary> persistedCqlLibrary = cqlLibraryRepository.findById(id);
    if (persistedCqlLibrary.isPresent()) {
      CqlLibrary cqlLibrary = persistedCqlLibrary.get();
      librarySetService.updateOwnership(cqlLibrary.getLibrarySetId(), userid);
      result = true;
    }
    return result;
  }

  public CqlLibrary deleteDraftLibrary(final String id, final String userId) {
    CqlLibrary cqlLibrary = findCqlLibraryById(id);
    if (!userId.equalsIgnoreCase(cqlLibrary.getLibrarySet().getOwner())) {
      throw new PermissionDeniedException("CQL Library", cqlLibrary.getId(), userId);
    }

    if (cqlLibrary.isDraft()) {
      cqlLibraryRepository.delete(cqlLibrary);
    } else {
      throw new GeneralConflictException(
          String.format(
              "Could not update resource %s with id: %s. Resource is not a Draft.",
              "CQL Library", id));
    }
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
   * @param accessToken
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

  public Map<String, List<SharedUser>> getSharedLibraries(List<String> libraryIds) {
    Map<String, List<SharedUser>> sharedLibraries = new HashMap<>();

    for (String libraryId : libraryIds) {
      CqlLibrary library = findCqlLibraryById(libraryId);

      if (library == null) {
        throw new ResourceNotFoundException("Library does not exist: " + libraryId);
      }
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
      Map<String, List<String>> libraryUserIdMap, String performedBy) {
    Map<String, List<AclSpecification>> libraryIdToAclSpecification = new HashMap<>();

    libraryUserIdMap
        .keySet()
        .forEach(
            libraryId -> {
              CqlLibrary library = findCqlLibraryById(libraryId);

              if (library == null) {
                throw new ResourceNotFoundException("Library does not exist: " + libraryId);
              }
              verifyAuthorization(performedBy, library, null);
            });

    libraryUserIdMap.forEach(
        (LibraryId, userIds) -> {
          AclOperation aclOperation = buildShareAclOperation(userIds);
          libraryIdToAclSpecification.put(
              LibraryId, updateAccessControlList(LibraryId, aclOperation, performedBy));
        });

    return libraryIdToAclSpecification;
  }

  private AclOperation buildShareAclOperation(List<String> userIds) {
    return AclOperation.builder()
        .acls(buildShareAclSpecifications(userIds))
        .action(AclOperation.AclAction.GRANT)
        .build();
  }

  private List<AclSpecification> buildShareAclSpecifications(List<String> userIds) {
    return userIds.stream()
        .map(
            userId ->
                AclSpecification.builder()
                    .userId(userId)
                    .roles(Set.of(RoleEnum.SHARED_WITH))
                    .build())
        .toList();
  }

  public void verifyAuthorization(String username, CqlLibrary library, List<RoleEnum> roles) {
    LibrarySet librarySet =
        library.getLibrarySet() == null
            ? librarySetService.findByLibrarySetId(library.getLibrarySetId())
            : library.getLibrarySet();
    if (librarySet == null) {
      throw new ResourceNotFoundException(
          "No library set exists for library with ID : " + library.getId());
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
}
