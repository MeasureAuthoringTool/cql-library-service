package gov.cms.madie.cqllibraryservice.models;

public enum PackageDownloadStatus {
  DOWNLOADING,
  DOWNLOADED,
  PROCESSED,
  INSTALLED,
  ERROR,
  ERROR_INFECTED_SO_REVIEW
}
