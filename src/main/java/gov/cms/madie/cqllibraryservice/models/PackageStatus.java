package gov.cms.madie.cqllibraryservice.models;

public enum PackageStatus {
  DOWNLOADING,
  DOWNLOADED,
  DOWNLOAD_FAILED,
  // Common CQL Library import is in progress.
  PROCESSING,
  // Package was successfully read and no CQL Libraries were found.
  PROCESSED,
  // Package was successfully read, and CQL Libraries found were persisted.
  INSTALLED,
  // Processing/installation failed
  ERROR,
  // Package was infected with virus. need SO review
  ERROR_INFECTED_SO_REVIEW
}
