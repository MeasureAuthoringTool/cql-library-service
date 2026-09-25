package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.LibraryAccessReportDTO;
import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.exceptions.GeneralConflictException;
import gov.cms.madie.cqllibraryservice.exceptions.HarpIdMismatchException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotDraftableException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetActionLogRepository;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.LibrarySetActionLog;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.library.CqlLibrary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

  private final CqlLibraryRepository cqlLibraryRepository;
  private final LibrarySetActionLogRepository librarySetActionLogRepository;
  private final ExcelClient excelClient;
  private final CqlLibraryService cqlLibraryService;
  private final VersionService versionService;
  private final ActionLogService actionLogService;

  private static final String VERSION_NOT_LOWER_ERROR =
      "New version # must be lower than the intended final version number";
  private static final String VERSION_ALREADY_USED_ERROR =
      "New version # must not be one that has been used previously for this library";

  public CqlLibrary correctLibraryVersion(
      String id, String inCorrectVersion, String draftVersion, String harpId, String username) {
    CqlLibrary libraryToCorrectVersion = cqlLibraryService.findCqlLibraryById(id, username);
    if (libraryToCorrectVersion.getVersion() == null
        || !libraryToCorrectVersion.getVersion().toString().equals(inCorrectVersion)) {
      throw new ResourceNotFoundException(
          String.format(
              "Could not find CQL Library with id of %s and / or a version of %s",
              id, inCorrectVersion));
    }

    if (libraryToCorrectVersion.getLibrarySet() == null
        || !libraryToCorrectVersion.getLibrarySet().getOwner().equalsIgnoreCase(harpId)) {
      throw new HarpIdMismatchException(
          harpId,
          libraryToCorrectVersion.getLibrarySet() == null
              ? null
              : libraryToCorrectVersion.getLibrarySet().getOwner(),
          libraryToCorrectVersion.getId());
    }

    // check if the associated library set already has a draft
    List<CqlLibrary> relatedDrafts =
        cqlLibraryRepository.findByLibrarySetIdAndDraftAndActive(
            libraryToCorrectVersion.getLibrarySetId(), true, true);
    if (CollectionUtils.isNotEmpty(relatedDrafts)
        && !relatedDrafts.get(0).getId().equals(libraryToCorrectVersion.getId())) {
      throw new ResourceNotDraftableException(
          "CQL Library", "Only one draft is permitted per library.");
    }

    // check if the draftVersion is lower than the current version
    if (!isLessThan(inCorrectVersion, draftVersion)) {
      throw new GeneralConflictException(VERSION_NOT_LOWER_ERROR);
    }

    // check if the given version is already used within the library set
    if (isVersionAlreadyAssociated(libraryToCorrectVersion.getLibrarySetId(), draftVersion)) {
      throw new GeneralConflictException(VERSION_ALREADY_USED_ERROR);
    }

    Version newDraftVersion = Version.parse(draftVersion);
    String newCql =
        libraryToCorrectVersion
            .getCql()
            .replace(
                versionService.generateLibraryContentLine(
                    libraryToCorrectVersion.getCqlLibraryName(),
                    libraryToCorrectVersion.getVersion()),
                versionService.generateLibraryContentLine(
                    libraryToCorrectVersion.getCqlLibraryName(), newDraftVersion));

    libraryToCorrectVersion.setCql(newCql);
    libraryToCorrectVersion.setVersion(newDraftVersion);
    libraryToCorrectVersion.setDraft(true);
    libraryToCorrectVersion.setLastModifiedAt(Instant.now());
    libraryToCorrectVersion.setLastModifiedBy(username);

    CqlLibrary correctedVersionLibrary = cqlLibraryRepository.save(libraryToCorrectVersion);

    actionLogService.logAction(
        libraryToCorrectVersion.getId(),
        ActionType.VERSION_REVERT,
        username,
        "actionLog",
        String.format(
            "Reverted from version %s to %s by MADiE Admin", inCorrectVersion, draftVersion));

    log.info(
        "Admin user [{}] reverted CQL Library [{}] from version [{}] to [{}]",
        username,
        id,
        inCorrectVersion,
        draftVersion);

    return correctedVersionLibrary;
  }

  private boolean isVersionAlreadyAssociated(String librarySetId, String draftVersion) {
    return cqlLibraryRepository.findByLibrarySetIdAndActive(librarySetId, true).stream()
        .anyMatch(
            library ->
                library.getVersion() != null
                    && library.getVersion().toString().equals(draftVersion));
  }

  private boolean isLessThan(String currentVersion, String draftVersion) {
    String[] currentVersionParts = currentVersion.split("\\.");
    String[] draftVersionParts = draftVersion.split("\\.");

    int length = Math.max(currentVersionParts.length, draftVersionParts.length);

    for (int i = 0; i < length; i++) {
      // parse the parts as integers for comparison. If a part is missing, treat it as zero.
      int currentVersionPart =
          i < currentVersionParts.length ? Integer.parseInt(currentVersionParts[i]) : 0;
      int draftVersionPart =
          i < draftVersionParts.length ? Integer.parseInt(draftVersionParts[i]) : 0;

      // compare corresponding parts of the version strings
      if (draftVersionPart < currentVersionPart) {
        return true;
      } else if (draftVersionPart > currentVersionPart) {
        return false;
      }
    }
    // if all parts are equal, draftVersion is not less than currentVersion
    return false;
  }

  public byte[] exportSharedWithLibraries(
      List<String> libraryIds, String username, String accessToken) {
    if (CollectionUtils.isEmpty(libraryIds)) {
      throw new IllegalArgumentException(
          "Please provide at least one library id to export the shared access report.");
    }
    List<LibraryAccessReportDTO> accessReportDTOS = getLibrariesWithAccessReport(libraryIds);

    byte[] export = excelClient.getSharedAccessReportForLibraries(accessReportDTOS, accessToken);
    log.info("Access report successful for libraries [{}] by user [{}]", libraryIds, username);

    return export;
  }

  public List<LibraryAccessReportDTO> getLibrariesWithAccessReport(List<String> libraryIds) {
    List<LibraryListDTO> libraryResults =
        cqlLibraryRepository.findLibrariesForAccessReport(libraryIds);

    if (libraryResults.isEmpty()) {
      return Collections.emptyList();
    }

    // Collect unique librarySetIds to fetch action logs
    List<String> librarySetIds =
        libraryResults.stream()
            .map(LibraryListDTO::getLibrarySetId)
            .filter(id -> id != null)
            .distinct()
            .collect(Collectors.toList());

    // Fetch all action logs for the librarySetIds
    Map<String, LibrarySetActionLog> actionLogMap =
        librarySetIds.stream()
            .map(librarySetActionLogRepository::findByTargetId)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toMap(LibrarySetActionLog::getTargetId, Function.identity()));

    return libraryResults.stream()
        .map(dto -> mapToLibraryAccessReportDTO(dto, actionLogMap.get(dto.getLibrarySetId())))
        .collect(Collectors.toList());
  }

  private LibraryAccessReportDTO mapToLibraryAccessReportDTO(
      LibraryListDTO dto, LibrarySetActionLog actionLog) {
    List<LibraryAccessReportDTO.SharedWithUser> sharedWithUsers = Collections.emptyList();

    // Build a map of userId -> dateShared from action logs
    Map<String, Instant> sharedDateMap = Collections.emptyMap();
    if (actionLog != null && actionLog.getActions() != null) {
      sharedDateMap =
          actionLog.getActions().stream()
              .filter(
                  action ->
                      action.getActionType() == ActionType.SHARED && action.getSharedWith() != null)
              .collect(
                  Collectors.toMap(
                      action -> action.getSharedWith().toLowerCase(),
                      action -> action.getPerformedAt(),
                      (existing, replacement) ->
                          replacement // Use the most recent share date if shared multiple times
                      ));
    }

    if (dto.getLibrarySet() != null
        && dto.getLibrarySet().getAcls() != null
        && !dto.getLibrarySet().getAcls().isEmpty()) {
      DateTimeFormatter formatter =
          DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"));
      final Map<String, Instant> finalSharedDateMap = sharedDateMap;
      sharedWithUsers =
          dto.getLibrarySet().getAcls().stream()
              .map(
                  acl -> {
                    Instant sharedDate = finalSharedDateMap.get(acl.getUserId().toLowerCase());
                    String dateSharedStr = sharedDate != null ? formatter.format(sharedDate) : null;
                    return LibraryAccessReportDTO.SharedWithUser.builder()
                        .userId(acl.getUserId())
                        .dateShared(dateSharedStr)
                        .build();
                  })
              .collect(Collectors.toList());
    }

    return LibraryAccessReportDTO.builder()
        .id(dto.getId())
        .libraryName(dto.getCqlLibraryName())
        .libraryModel(dto.getModel())
        .owner(dto.getLibrarySet() != null ? dto.getLibrarySet().getOwner() : null)
        .sharedWith(sharedWithUsers)
        .build();
  }
}
