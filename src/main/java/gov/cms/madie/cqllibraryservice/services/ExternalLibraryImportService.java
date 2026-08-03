package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.models.ExternalLibrary;
import gov.cms.madie.cqllibraryservice.models.PackageStatus;
import gov.cms.madie.cqllibraryservice.models.PackageTrackingRecord;
import gov.cms.madie.cqllibraryservice.repositories.PackageTrackingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the full workflow for importing CQL Libraries from a downloaded FHIR IG package and
 * all of its transitive dependencies.
 *
 * <p>Workflow:
 *
 * <ol>
 *   <li>Mark the package tracking record as {@code PROCESSING}.
 *   <li>Recursively resolve the package and all dependency packages via {@link
 *       PackageCacheManagerAdapter}.
 *   <li>For each resolved package path, discover valid CQL logic libraries via {@link
 *       ExternalLibraryDiscoveryService}.
 *   <li>Persist discovered libraries via {@link ExternalLibraryPersistenceService}.
 *   <li>Update status to {@code INSTALLED} if any libraries were persisted, {@code PROCESSED} if
 *       none were found, or {@code ERROR} on unrecoverable failure.
 * </ol>
 *
 * <p>The main entry point ({@link #importLibraries}) is annotated with {@code @Async} so that
 * callers receive control back immediately while processing continues in the background.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalLibraryImportService {

  private final PackageCacheManagerAdapter packageCacheManagerAdapter;
  private final ExternalLibraryDiscoveryService externalLibraryDiscoveryService;
  private final ExternalLibraryPersistenceService externalLibraryPersistenceService;
  private final PackageTrackingRepository packageTrackingRepository;

  /**
   * Imports CQL Libraries from the specified IG package and all of its transitive dependencies.
   *
   * @param packageId the IG package identifier (e.g. {@code hl7.fhir.us.qicore})
   * @param packageVersion the IG package version (e.g. {@code 6.0.0})
   */
  public void importLibraries(String packageId, String packageVersion) {
    log.info("Importing CQL Libraries for IG [{}#{}] started", packageId, packageVersion);
    long startTime = System.nanoTime();

    // IG level counts
    int totalDiscovered = 0;
    int totalPersisted = 0;

    try {
      // Collect package paths via the existing adapter (handles recursion + circular dep guard).
      // The callback always returns true because we are reading from the local cache only –
      // no virus scanning needed at this stage.
      List<String[]> packageEntries = new ArrayList<>();
      packageCacheManagerAdapter.loadPackageWithDependencies(
          packageId,
          packageVersion,
          (pkgId, pkgVersion, path) -> {
            packageEntries.add(new String[] {pkgId, pkgVersion, path});
            return true;
          });

      log.info(
          "Resolved {} package(s) for import of [{}#{}]",
          packageEntries.size(),
          packageId,
          packageVersion);

      for (String[] entry : packageEntries) {
        String pkgId = entry[0];
        String pkgVersion = entry[1];
        String path = entry[2];

        log.info("Importing CQL Libraries for package [{}#{}]", pkgId, pkgVersion);
        PackageTrackingRecord packageRecord = markAsProcessing(pkgId, pkgVersion);

        // Package level counts
        int packageDiscovered = 0;
        int packagePersisted = 0;

        try {
          List<ExternalLibrary> discoveredLibraries =
              externalLibraryDiscoveryService.discoverLibraries(path, pkgId, pkgVersion);
          packageDiscovered = discoveredLibraries.size();
          totalDiscovered += packageDiscovered;

          packagePersisted =
              externalLibraryPersistenceService.persistLibraries(discoveredLibraries);
          totalPersisted += packagePersisted;

          PackageStatus packageStatus =
              discoveredLibraries.isEmpty() ? PackageStatus.PROCESSED : PackageStatus.INSTALLED;
          markAsCompleted(packageRecord, packageStatus, packageDiscovered, packagePersisted);

          log.info(
              "Package [{}#{}]: status=[{}], discovered [{}], persisted [{}] CQL Libraries",
              pkgId,
              pkgVersion,
              packageStatus,
              packageDiscovered,
              packagePersisted);
        } catch (Exception ex) {
          log.error(
              "Import failed while processing package [{}#{}]: {}",
              pkgId,
              pkgVersion,
              ex.getMessage(),
              ex);
          markAsError(packageRecord, ex.getMessage(), packageDiscovered, packagePersisted);
        }
      }

      log.info(
          "Import completed for [{}#{}], discovered=[{}], persisted=[{}]",
          packageId,
          packageVersion,
          totalDiscovered,
          totalPersisted);

    } catch (Exception ex) {
      log.error(
          "Import failed for package [{}#{}]: {}", packageId, packageVersion, ex.getMessage(), ex);
    } finally {
      double elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
      log.info(
          "Importing CQL Libraries for [{}#{}] finished in [{}] seconds",
          packageId,
          packageVersion,
          elapsedSeconds);
    }
  }

  // ---------------------------------------------------------------------------
  // Status helpers
  // ---------------------------------------------------------------------------

  /**
   * Marks the tracking record as {@code PROCESSING} and resets import counters. Creates the record
   * if it does not exist yet.
   */
  public PackageTrackingRecord markAsProcessing(String packageId, String packageVersion) {
    return packageTrackingRepository
        .findByPackageIdAndVersion(packageId, packageVersion)
        .map(
            existing -> {
              existing.setStatus(PackageStatus.PROCESSING);
              existing.setErrorMessage(null);
              existing.setDiscoveredLibraryCount(0);
              existing.setPersistedLibraryCount(0);
              existing.setImportStartedAt(Instant.now());
              existing.setImportCompletedAt(null);
              return packageTrackingRepository.save(existing);
            })
        .orElseGet(
            () ->
                packageTrackingRepository.save(
                    PackageTrackingRecord.builder()
                        .packageId(packageId)
                        .version(packageVersion)
                        .status(PackageStatus.PROCESSING)
                        .importStartedAt(Instant.now())
                        .discoveredLibraryCount(0)
                        .persistedLibraryCount(0)
                        .build()));
  }

  private void markAsCompleted(
      PackageTrackingRecord record, PackageStatus status, int discovered, int persisted) {
    record.setStatus(status);
    record.setDiscoveredLibraryCount(discovered);
    record.setPersistedLibraryCount(persisted);
    record.setImportCompletedAt(Instant.now());
    record.setErrorMessage(null);
    packageTrackingRepository.save(record);
  }

  private void markAsError(
      PackageTrackingRecord record, String errorMessage, int discovered, int persisted) {
    record.setStatus(PackageStatus.ERROR);
    record.setErrorMessage(errorMessage);
    record.setDiscoveredLibraryCount(discovered);
    record.setPersistedLibraryCount(persisted);
    record.setImportCompletedAt(Instant.now());
    packageTrackingRepository.save(record);
  }
}
