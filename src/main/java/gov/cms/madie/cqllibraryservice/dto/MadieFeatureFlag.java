package gov.cms.madie.cqllibraryservice.dto;

/** Feature flags relevant to the cql-library-service */
public enum MadieFeatureFlag {
  // Enables creating CQL libraries using the "US Quality Core v0.5.0" model
  US_QUALITY_CORE("usQualityCore");

  private final String flag;

  MadieFeatureFlag(String flag) {
    this.flag = flag;
  }

  @Override
  public String toString() {
    return this.flag;
  }
}
