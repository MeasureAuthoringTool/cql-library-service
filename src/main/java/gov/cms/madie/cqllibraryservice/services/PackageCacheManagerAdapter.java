package gov.cms.madie.cqllibraryservice.services;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.hl7.fhir.utilities.npm.NpmPackage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter around HAPI FHIR's FilesystemPackageCacheManager to improve testability. This component
 * manages the lifecycle of the cache manager and delegates package loading.
 */
@Slf4j
@Component
public class PackageCacheManagerAdapter {

  private final FilesystemPackageCacheManager cacheManager;

  public PackageCacheManagerAdapter(@Value("${fhir.package.cache-path}") String cachePath)
      throws IOException {
    if (cachePath != null && !cachePath.isBlank()) {
      ensureDirectoryExists(cachePath);
      log.info("Initializing FHIR package cache at configured path: {}", cachePath);
      this.cacheManager =
          new FilesystemPackageCacheManager.Builder().withCacheFolder(cachePath).build();
    } else {
      String defaultPath =
          org.hl7.fhir.utilities.Utilities.path(
              System.getProperty("user.home"), ".fhir", "packages");
      ensureDirectoryExists(defaultPath);
      log.info("Initializing FHIR package cache at default system path: {}", defaultPath);
      this.cacheManager = new FilesystemPackageCacheManager.Builder().build();
    }
  }

  private void ensureDirectoryExists(String path) throws IOException {
    File dir = new File(path);
    if (!dir.exists()) {
      log.info("Cache directory does not exist, creating: {}", path);
      if (!dir.mkdirs()) {
        throw new IOException("Failed to create FHIR package cache directory: " + path);
      }
    } else if (!dir.isDirectory()) {
      throw new IOException("FHIR package cache path exists but is not a directory: " + path);
    }
  }

  /**
   * Loads a FHIR NPM package and recursively loads all of its declared dependencies. The provided
   * {@code callback} is invoked immediately after each package (root and every transitive
   * dependency) is downloaded.
   *
   * @param packageId the package identifier
   * @param version the package version
   * @param callback called right after each package path becomes available
   * @return paths to every downloaded package (root first, then dependencies)
   * @throws Exception if a package cannot be loaded or if the callback aborts processing
   */
  public List<String> loadPackageWithDependencies(
      String packageId, String version, PackageDownloadedCallback callback) throws Exception {
    List<String> collectedPaths = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    loadPackageWithDependencies(packageId, version, visited, collectedPaths, callback);
    return collectedPaths;
  }

  private void loadPackageWithDependencies(
      String packageId,
      String version,
      Set<String> visited,
      List<String> collectedPaths,
      PackageDownloadedCallback callback)
      throws Exception {
    String key = packageId + "#" + version;
    if (visited.contains(key)) {
      log.debug("Skipping already-visited package: {}", key);
      return;
    }
    visited.add(key);

    log.info("Downloading FHIR package: {}", key);
    NpmPackage npmPackage = cacheManager.loadPackage(packageId, version);
    if (npmPackage == null) {
      log.warn("Package not found: {}", key);
      return;
    }

    String path = npmPackage.getPath();
    if (path != null) {
      boolean keepPackage = callback.onDownloaded(packageId, version, path);
      if (!keepPackage) {
        log.warn("Skipping package {} after callback requested stop for this package", key);
        return;
      }
      collectedPaths.add(path);
    }

    // Recursively load dependencies declared in package.json
    JsonObject npm = npmPackage.getNpm();
    if (npm != null) {
      JsonObject dependencies = npm.getJsonObject("dependencies");
      if (dependencies != null) {
        for (String depId : dependencies.getNames()) {
          String depVersion = dependencies.asString(depId);
          log.info("Downloading dependency {}#{} (required by {})", depId, depVersion, key);
          try {
            loadPackageWithDependencies(depId, depVersion, visited, collectedPaths, callback);
          } catch (Exception e) {
            log.warn(
                "Failed to download dependency {}#{} required by {}: {}",
                depId,
                depVersion,
                key,
                e.getMessage());
          }
        }
      }
    }
  }
}
