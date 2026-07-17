package gov.cms.madie.cqllibraryservice.models;

public enum PackageDownloadStatus {
  DOWNLOADING,
  DOWNLOADED,
  DOWNLOAD_FAILED,
  // Package was successfully read and no CQL Libraries were found.
  PROCESSED,
  // Package was successfully read, and CQL Libraries found were persisted.
  INSTALLED,
  // Processing/installation failed
  ERROR,
  // Package was infected with virus. need SO review
  ERROR_INFECTED_SO_REVIEW
}
