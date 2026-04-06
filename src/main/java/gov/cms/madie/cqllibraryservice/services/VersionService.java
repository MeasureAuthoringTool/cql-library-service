package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.dto.LockInfo;
import gov.cms.madie.cqllibraryservice.dto.NotificationDto;
import gov.cms.madie.cqllibraryservice.exceptions.*;
import gov.cms.madie.cqllibraryservice.utils.AuthUtils;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;
import gov.cms.madie.models.measure.ElmJson;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class VersionService {

  private final CqlLibraryService cqlLibraryService;
  private final ActionLogService actionLogService;
  private final CqlLibraryRepository cqlLibraryRepository;
  private final ElmTranslatorClient elmTranslatorClient;
  private final AppConfigService appConfigService;
  private final CqlLibraryLockService cqlLibraryLockService;
  private final NotificationServiceClient notificationServiceClient;

  public CqlLibrary createVersion(String id, boolean isMajor, String username, String accessToken) {
    CqlLibrary cqlLibrary = cqlLibraryService.findCqlLibraryById(id, username);
    validateCqlLibrary(cqlLibrary, username);

    LockInfo lockInfo = cqlLibraryLockService.lockCqlLibrary(id, username);
    log.info("user: [{}] is trying to lock Library: [{}]", username, id);
    if (lockInfo != null && lockInfo.isLocked() && !lockInfo.getLockedBy().equals(username)) {
      log.info(
          "user: [{}] can't version Library: [{}], because library is locked by: [{}]",
          username,
          id,
          lockInfo.getLockedBy());
      throw new ResourceLockedException(
          "Unable to version library. Locked while being edited by " + lockInfo.getLockedBy());
    }

    cqlLibrary.setDraft(false);
    cqlLibrary.setLastModifiedAt(Instant.now());
    cqlLibrary.setLastModifiedBy(username);

    String existingCqlLibraryLine =
        libraryContentTemplate(cqlLibrary.getCqlLibraryName(), cqlLibrary.getVersion());
    Version next = getNextVersion(cqlLibrary, isMajor);
    cqlLibrary.setVersion(next);
    String synchedCqlLibraryLine = libraryContentTemplate(cqlLibrary.getCqlLibraryName(), next);
    cqlLibrary.setCql(cqlLibrary.getCql().replace(existingCqlLibraryLine, synchedCqlLibraryLine));

    try {
      final ElmJson elmJson =
          elmTranslatorClient.getElmJson(
              cqlLibrary.getCql(), cqlLibrary.getModel(), accessToken, "Error");
      if (elmTranslatorClient.hasErrors(elmJson)) {
        throw new CqlElmTranslationErrorException(cqlLibrary.getCqlLibraryName());
      }
      cqlLibrary.setElmJson(elmJson.getJson());
      cqlLibrary.setElmXml(elmJson.getXml());
    } catch (CqlElmTranslationServiceException | CqlElmTranslationErrorException e) {
      throw e;
    } catch (Exception e) {
      log.error(
          "User [{}] cannot create a version for CQL Library with id [{}]"
              + "as there was an issue calling the Hapi Fhir service",
          username,
          cqlLibrary.getId(),
          e);
      throw new PersistHapiFhirCqlLibraryException("CQL Library", cqlLibrary.getId(), username);
    }

    var savedCqlLibrary = cqlLibraryRepository.save(cqlLibrary);

    actionLogService.logAction(
        cqlLibrary.getId(),
        isMajor ? ActionType.VERSIONED_MAJOR : ActionType.VERSIONED_MINOR,
        username,
        "actionLog",
        String.format("Versioned to %s", cqlLibrary.getVersion()));

    log.info(
        "User [{}] successfully versioned cql library with ID [{}]",
        username,
        savedCqlLibrary.getId());

    cqlLibraryLockService.unlockCqlLibrary(id, username);
    log.info("user: [{}] had unlocked Library: [{}]", username, id);

    // Build and send notifications to owner and shared users (excluding the action user)
    try {
      List<NotificationDto> notifications = buildVersionNotifications(savedCqlLibrary, username);
      if (!notifications.isEmpty()) {
        notificationServiceClient.sendNotifications(notifications);
      }
    } catch (Exception e) {
      log.error(
          "Failed to send version notifications for library [{}]", savedCqlLibrary.getId(), e);
    }

    return savedCqlLibrary;
  }

  private List<NotificationDto> buildVersionNotifications(
      CqlLibrary cqlLibrary, String actionUser) {
    List<NotificationDto> notifications = new ArrayList<>();
    LibrarySet librarySet = cqlLibrary.getLibrarySet();
    if (librarySet == null) {
      return notifications;
    }

    String message =
        String.format(
            "%s created version %s of CQL library \"%s\".",
            actionUser, cqlLibrary.getVersion(), cqlLibrary.getCqlLibraryName());
    String additionalLink = "/cql-libraries/" + cqlLibrary.getId() + "/edit/details";

    // Collect all users who should be notified (owner + shared users), excluding the action user
    Set<String> usersToNotify = new HashSet<>();

    // Add owner
    if (librarySet.getOwner() != null) {
      usersToNotify.add(librarySet.getOwner().toLowerCase());
    }

    // Add all shared users from ACLs
    if (librarySet.getAcls() != null) {
      for (var acl : librarySet.getAcls()) {
        if (acl.getUserId() != null) {
          usersToNotify.add(acl.getUserId().toLowerCase());
        }
      }
    }

    // Exclude the user who performed the action
    usersToNotify.remove(actionUser.toLowerCase());

    for (String userId : usersToNotify) {
      notifications.add(
          NotificationDto.builder()
              .userId(userId)
              .message(message)
              .additionalLink(additionalLink)
              .build());
    }

    return notifications;
  }

  private String libraryContentTemplate(String cqlLibraryName, Version version) {
    return "library " + cqlLibraryName + " version " + "\'" + version + "\'";
  }

  private void validateCqlLibrary(CqlLibrary cqlLibrary, String username) {
    AuthUtils.checkAccessPermissions(cqlLibrary, username);

    if (!cqlLibrary.isDraft()) {
      log.error(
          "User [{}] attempted to version CQL Library with id [{}] which is not in a draft state",
          username,
          cqlLibrary.getId());
      throw new BadRequestObjectException("CQL Library", cqlLibrary.getId(), username);
    }

    if (cqlLibrary.isCqlErrors()) {
      log.error(
          "User [{}] cannot create a version for CQL Library with id [{}] "
              + "as the Cql has errors in it",
          username,
          cqlLibrary.getId());

      throw new ResourceCannotBeVersionedException(
          "CQL Library", cqlLibrary.getId(), username, "the Cql has errors in it");
    }

    if (cqlLibrary.getCql().length() == 0) {
      log.error(
          "User [{}] cannot create a version for CQL Library with id [{}] "
              + "as there is no associated Cql with this library",
          username,
          cqlLibrary.getId());
      throw new ResourceCannotBeVersionedException(
          "CQL Library",
          cqlLibrary.getId(),
          username,
          "there is no associated Cql with this library");
    }
  }

  public CqlLibrary createDraft(String id, String cqlLibraryName, String model, String username) {
    CqlLibrary cqlLibrary = cqlLibraryService.findCqlLibraryById(id, username);

    if (!Objects.equals(cqlLibraryName, cqlLibrary.getCqlLibraryName())) {
      cqlLibraryService.checkDuplicateCqlLibraryName(cqlLibraryName);
    }

    AuthUtils.checkAccessPermissions(cqlLibrary, username);

    if (cqlLibrary.isDraft()) {
      throw new ResourceNotDraftableException(
          "CQL Library", "Only versioned library can be drafted.");
    }
    if (!isDraftable(cqlLibrary)) {
      throw new ResourceNotDraftableException(
          "CQL Library", "A draft already exists for the CQL Library Group.");
    }
    if (isQiCore411AndHasOtherQiCoreLibrary(cqlLibrary)) {
      throw new ResourceNotDraftableException(
          "CQL Library", "You cannot draft a 4.1.1 library when a newer version is available.");
    }
    if (!isValidDraftableVersion(cqlLibrary, model)) {
      throw new ResourceNotDraftableException(
          "CQL Library", "You cannot draft a 6.0.0 or 7.0.0 library to an older version library.");
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
    if (!cqlLibraryName.equals(cqlLibrary.getCqlLibraryName())) {
      clonedCqlLibrary.setCql(
          cqlLibrary
              .getCql()
              .replaceFirst(
                  ".*?[\n\r]",
                  "library " + cqlLibraryName + " version '" + cqlLibrary.getVersion() + "'\n"));
    }

    clonedCqlLibrary.setModel(model);
    clonedCqlLibrary.setCql(updateUsingStatement(model, cqlLibrary.getCql()));

    var savedCqlLibrary = cqlLibraryRepository.save(clonedCqlLibrary);

    log.info(
        "User [{}] successfully created a draft cql library with ID [{}]",
        username,
        savedCqlLibrary.getId());
    actionLogService.logAction(
        savedCqlLibrary.getId(),
        ActionType.DRAFTED,
        username,
        "actionLog",
        String.format("Draft created from version %s", cqlLibrary.getVersion()));
    return savedCqlLibrary;
  }

  public Version getNextVersion(CqlLibrary cqlLibrary, boolean isMajor) {
    // get the max major/minor version and increment it
    try {
      if (isMajor) {
        Version version =
            cqlLibraryRepository
                .findMaxVersionByLibrarySetId(cqlLibrary.getLibrarySetId())
                .orElse(new Version());
        return version.toBuilder().major(version.getMajor() + 1).minor(0).build();
      } else {
        Version version =
            cqlLibraryRepository
                .findMaxMinorVersionByLibrarySetIdAndVersionMajor(
                    cqlLibrary.getLibrarySetId(), cqlLibrary.getVersion().getMajor())
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
    return !cqlLibraryRepository.existsByLibrarySetIdAndDraft(cqlLibrary.getLibrarySetId(), true);
  }

  boolean isQiCore411AndHasOtherQiCoreLibrary(CqlLibrary cqlLibrary) {
    boolean isQiCore411AndHasOtherQiCoreLibrary = false;
    if (ModelType.QI_CORE.getValue().equalsIgnoreCase(cqlLibrary.getModel())) {

      List<LibraryListDTO> cqlLibraries =
          cqlLibraryService.getLibrariesByLibrarySetId(cqlLibrary.getLibrarySetId(), true, null);
      Optional<LibraryListDTO> libsWithSameSet =
          cqlLibraries.stream()
              .filter(
                  (library) ->
                      !library.getId().equals(cqlLibrary.getId())
                          && (library.getModel().equals(ModelType.QI_CORE_6_0_0.getValue())
                              || library.getModel().equals(ModelType.QI_CORE_7_0_0.getValue())))
              .findFirst();
      isQiCore411AndHasOtherQiCoreLibrary = libsWithSameSet.isPresent() ? true : false;
    }
    return isQiCore411AndHasOtherQiCoreLibrary;
  }

  /**
   * Returns false if a QI-Core 6.0.0 or QI-Core 7.0.0 versioned library is drafted with an older
   * model version
   */
  boolean isValidDraftableVersion(CqlLibrary cqlLibrary, String model) {
    boolean valid = true;
    if (ModelType.QI_CORE_6_0_0.getValue().equals(cqlLibrary.getModel())
        && ModelType.QI_CORE.getValue().equals(model)) {
      valid = false;
    } else if (ModelType.QI_CORE_7_0_0.getValue().equals(cqlLibrary.getModel())
        && (ModelType.QI_CORE_6_0_0.getValue().equals(model)
            || ModelType.QI_CORE.getValue().equals(model))) {
      valid = false;
    }
    return valid;
  }

  private String updateUsingStatement(String model, final String cql) {
    Pattern qicorePattern = Pattern.compile("using QICore .*version '[0-9]\\.[0-9](\\.[0-9])?'");
    Matcher matcher = qicorePattern.matcher(cql);
    String updatedCql = cql;
    if (matcher.find()) {
      updatedCql =
          matcher.replaceAll(
              "using QICore version '" + model.substring(model.lastIndexOf("v") + 1) + "'");
    }
    return updatedCql;
  }
}
