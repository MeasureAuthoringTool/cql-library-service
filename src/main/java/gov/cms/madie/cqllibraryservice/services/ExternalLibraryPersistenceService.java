package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.models.ExternalLibrary;
import gov.cms.madie.cqllibraryservice.repositories.ExternalLibraryRepository;
import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.library.LibrarySet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Handles persistence of discovered CQL Libraries.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Find or create a {@link LibrarySet} for each unique {@code (canonical, libraryName)}
 *       combination.
 *   <li>Skip duplicate {@code (canonical, libraryName, version)} entries.
 *   <li>Persist new {@link ExternalLibrary} records.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalLibraryPersistenceService {

  @Value("${fhir.external-libraries-owner}")
  private static String defaultOwner;

  private final ExternalLibraryRepository externalLibraryRepository;
  private final LibrarySetRepository librarySetRepository;
  private final ActionLogService actionLogService;

  /**
   * Persists all libraries that are not already present. Duplicates are logged and skipped.
   *
   * @param libraries the list of discovered {@link ExternalLibrary} objects to persist
   * @return the number of libraries actually saved to MongoDB
   */
  public int persistLibraries(List<ExternalLibrary> libraries) {
    int totalPersisted = 0;
    for (ExternalLibrary library : libraries) {
      boolean persisted = persistSingleLibrary(library);
      if (persisted) {
        totalPersisted = totalPersisted + 1;
      }
    }
    return totalPersisted;
  }

  /** Persists a single library, returning true if saved or false if it was a duplicate. */
  private boolean persistSingleLibrary(ExternalLibrary library) {
    if (externalLibraryRepository.existsByCanonicalAndLibraryNameAndVersion(
        library.getCanonical(), library.getLibraryName(), library.getVersion())) {
      log.info(
          "Skipping duplicate ExternalLibrary [{}/{}@{}]",
          library.getCanonical(),
          library.getLibraryName(),
          library.getVersion());
      return false;
    }

    String librarySetId = findOrCreateLibrarySet(library.getCanonical(), library.getLibraryName());
    library.setLibrarySetId(librarySetId);
    library.setLibrarySetId(librarySetId);

    externalLibraryRepository.save(library);
    actionLogService.logAction(library.getId(), ActionType.IMPORTED, defaultOwner, "actionLog");
    log.info(
        "Persisted ExternalLibrary [{}/{}@{}] under LibrarySet [{}]",
        library.getCanonical(),
        library.getLibraryName(),
        library.getVersion(),
        librarySetId);
    return true;
  }

  /**
   * Finds an existing {@link LibrarySet} for the given namespace and library name, or creates a new
   * one if none exists.
   *
   * @param namespaceCanonical the IG canonical URL
   * @param libraryName the CQL library name
   * @return the ID of the found or newly-created {@link LibrarySet}
   */
  String findOrCreateLibrarySet(String namespaceCanonical, String libraryName) {
    return externalLibraryRepository
        .findByCanonicalAndLibraryName(namespaceCanonical, libraryName)
        .map(ExternalLibrary::getLibrarySetId)
        .orElseGet(
            () -> {
              LibrarySet librarySet =
                  LibrarySet.builder()
                      .librarySetId(UUID.randomUUID().toString())
                      .owner(defaultOwner)
                      .build();
              librarySetRepository.save(librarySet);
              actionLogService.logAction(
                  librarySet.getLibrarySetId(),
                  ActionType.CREATED,
                  defaultOwner,
                  "librarySetActionLog");
              return librarySet.getLibrarySetId();
            });
  }
}
