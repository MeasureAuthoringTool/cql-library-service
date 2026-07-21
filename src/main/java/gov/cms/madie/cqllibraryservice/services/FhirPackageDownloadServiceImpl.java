package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.DownloadedPackageResult;
import gov.cms.madie.cqllibraryservice.exceptions.VirusScanServiceException;
import gov.cms.madie.cqllibraryservice.models.PackageDownloadStatus;
import gov.cms.madie.cqllibraryservice.models.PackageTrackingRecord;
import gov.cms.madie.cqllibraryservice.repositories.PackageTrackingRepository;
import gov.cms.madie.models.scanner.VirusScanResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.client.RestClientException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FhirPackageDownloadServiceImpl implements FhirPackageDownloadService {

  private final PackageCacheManagerAdapter packageCacheManagerAdapter;
  private final PackageTrackingRepository packageTrackingRepository;
  private final VirusScanClient virusScanClient;

  @Override
  public DownloadedPackageResult downloadPackage(
      String packageId, String version, String username) {
    log.info("Starting download for IG {}#{}", packageId, version);

    PackageTrackingRecord trackingRecord = markAsDownloading(packageId, version, username);
    Set<String> childIgs = new LinkedHashSet<>();

    try {
      PackageDownloadedCallback scanCallback =
          (downloadedPackageId, downloadedVersion, path) -> {
            boolean isRoot =
                packageId.equals(downloadedPackageId) && version.equals(downloadedVersion);
            if (!isRoot) {
              childIgs.add(toPackageKey(downloadedPackageId, downloadedVersion));
            }
            return scanAndTrackPackage(
                downloadedPackageId,
                downloadedVersion,
                path,
                username,
                isRoot,
                isRoot ? trackingRecord : null);
          };

      List<String> packagePaths =
          packageCacheManagerAdapter.loadPackageWithDependencies(packageId, version, scanCallback);
      String rootPackageLocation = packagePaths.isEmpty() ? null : packagePaths.get(0);
      if (rootPackageLocation == null) {
        String errorMessage = trackingRecord.getErrorMessage();
        if (errorMessage == null || errorMessage.isBlank()) {
          errorMessage = "Root package failed virus scan and was removed from cache";
          markAsInfected(trackingRecord, errorMessage);
        }
        return DownloadedPackageResult.builder()
            .packageId(packageId)
            .version(version)
            .success(false)
            .packageLocation(null)
            .errorMessage(errorMessage)
            .build();
      }

      markAsDownloaded(trackingRecord, new ArrayList<>(childIgs));
      log.info(
          "Successfully downloaded and scanned {} package(s) for {}#{}",
          packagePaths.size(),
          packageId,
          version);

      return DownloadedPackageResult.builder()
          .packageId(packageId)
          .version(version)
          .success(true)
          .packageLocation(rootPackageLocation)
          .errorMessage(null)
          .build();

    } catch (VirusScanServiceException ex) {
      log.error("Stopping downloading package {}#{}", packageId, version);
      markAsFailed(trackingRecord, ex.getMessage());
      return DownloadedPackageResult.builder()
          .packageId(packageId)
          .version(version)
          .success(false)
          .packageLocation(null)
          .errorMessage(ex.getMessage())
          .build();
    } catch (Exception ex) {
      log.error("Failed to download FHIR package {}#{}", packageId, version, ex);
      markAsFailed(trackingRecord, ex.getMessage());

      return DownloadedPackageResult.builder()
          .packageId(packageId)
          .version(version)
          .success(false)
          .packageLocation(null)
          .errorMessage(ex.getMessage())
          .build();
    }
  }

  private boolean scanAndTrackPackage(
      String packageId,
      String version,
      String packagePath,
      String username,
      boolean isRootPackage,
      PackageTrackingRecord rootTrackingRecord) {
    PackageTrackingRecord trackingRecord = isRootPackage ? rootTrackingRecord : null;
    if (trackingRecord == null) {
      trackingRecord = markAsDownloading(packageId, version, username);
    }

    log.info("Scanning FHIR package {}#{} for viruses at {}", packageId, version, packagePath);

    // Collect all files from the package directory to be sent for virus scanning.
    List<Resource> filesToScan;
    try {
      filesToScan = collectFiles(packagePath);
    } catch (IOException ex) {
      String message =
          "Failed to collect files for virus scanning of package "
              + packageId
              + "#"
              + version
              + ": "
              + ex.getMessage();
      log.error(message, ex);
      deletePackage(packagePath);
      markAsFailed(trackingRecord, message);
      throw new VirusScanServiceException(message, ex);
    }

    log.info(
        "sending {} file(s) for virus scan of {}#{}", filesToScan.size(), packageId, version);

    VirusScanResponseDto scanResult;
    try {
      scanResult = virusScanClient.scanFiles(filesToScan);
    } catch (RestClientException ex) {
      String message =
          "Virus scan service is error for package "
              + packageId
              + "#"
              + version
              + " - download blocked: "
              + ex.getMessage();
      log.error(message, ex);
      deletePackage(packagePath);
      markAsFailed(trackingRecord, message);
      throw new VirusScanServiceException(message, ex);
    }

    int infectedCount = scanResult.getInfectedFileCount();
    if (infectedCount > 0) {
      deletePackage(packagePath);
      String message =
          "Virus scan detected "
              + infectedCount
              + " infected file(s) in package "
              + packageId
              + "#"
              + version
              + " - infected files deleted";
      markAsInfected(trackingRecord, message);
      return false;
    }

    log.info(
        "Virus scan passed for {}#{} at {}: {}/{} file(s) clean",
        packageId,
        version,
        packagePath,
        scanResult.getCleanFileCount(),
        scanResult.getFilesScanned());

    if (!isRootPackage) {
      markAsDownloaded(trackingRecord, null);
    }

    return true;
  }

  /**
   * Collects all files under {@code packagePath} as {@link Resource} instances suitable for
   * multipart upload. If the path points to a single file (e.g. a {@code .json}), that file is
   * returned as-is. If it points to a directory, all files are collected recursively.
   *
   * @param packagePath path to a file or directory
   * @return a flat list of file resources
   * @throws IOException if the path cannot be walked
   */
  private List<Resource> collectFiles(String packagePath) throws IOException {
    File source = new File(packagePath);
    List<Resource> resources = new ArrayList<>();
    if (source.isFile()) {
      resources.add(new FileSystemResource(source));
    } else {
      try (var stream = Files.walk(source.toPath())) {
        stream
            .filter(p -> !Files.isDirectory(p))
            .map(Path::toFile)
            .map(FileSystemResource::new)
            .forEach(resources::add);
      }
    }
    return resources;
  }

  private void deletePackage(String packagePath) {
    try {
      File packageDir = new File(packagePath);
      if (packageDir.exists()) {
        FileSystemUtils.deleteRecursively(packageDir);
        log.warn("Deleted infected package directory: {}", packagePath);
      }
    } catch (Exception e) {
      log.error("Failed to delete infected package directory {}: {}", packagePath, e.getMessage());
    }
  }

  private PackageTrackingRecord markAsDownloading(
      String packageId, String version, String username) {
    return packageTrackingRepository
        .findByPackageIdAndVersion(packageId, version)
        .map(
            existing -> {
              existing.setInitiatedBy(username);
              existing.setStatus(PackageDownloadStatus.DOWNLOADING);
              existing.setErrorMessage(null);
              existing.setLastAttemptedAt(Instant.now());
              return packageTrackingRepository.save(existing);
            })
        .orElseGet(
            () ->
                packageTrackingRepository.save(
                    PackageTrackingRecord.builder()
                        .packageId(packageId)
                        .version(version)
                        .status(PackageDownloadStatus.DOWNLOADING)
                        .initiatedBy(username)
                        .lastAttemptedAt(Instant.now())
                        .build()));
  }

  private void markAsDownloaded(PackageTrackingRecord record, List<String> childIgs) {
    record.setStatus(PackageDownloadStatus.DOWNLOADED);
    if (childIgs != null) {
      record.setChildIgs(childIgs);
    }
    record.setDownloadedAt(Instant.now());
    record.setErrorMessage(null);
    packageTrackingRepository.save(record);
  }

  private void markAsInfected(PackageTrackingRecord record, String errorMessage) {
    record.setStatus(PackageDownloadStatus.ERROR_INFECTED_SO_REVIEW);
    record.setErrorMessage(errorMessage);
    packageTrackingRepository.save(record);
  }

  private String toPackageKey(String packageId, String version) {
    return packageId + "#" + version;
  }

  private void markAsFailed(PackageTrackingRecord record, String errorMessage) {
    record.setStatus(PackageDownloadStatus.DOWNLOAD_FAILED);
    record.setErrorMessage(errorMessage);
    packageTrackingRepository.save(record);
  }
}
