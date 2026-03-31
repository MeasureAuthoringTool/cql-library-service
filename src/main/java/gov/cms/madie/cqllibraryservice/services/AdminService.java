package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.LibraryAccessReportDTO;
import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetActionLogRepository;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.LibrarySetActionLog;
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
