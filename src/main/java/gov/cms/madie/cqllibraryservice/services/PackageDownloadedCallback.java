package gov.cms.madie.cqllibraryservice.services;

/**
 * Callback invoked immediately after each package (root or dependency) has been downloaded to the
 * local filesystem. Return {@code false} to skip persisting/processing that package's path.
 */
@FunctionalInterface
public interface PackageDownloadedCallback {
  boolean onDownloaded(String packageId, String version, String packagePath) throws Exception;
}
