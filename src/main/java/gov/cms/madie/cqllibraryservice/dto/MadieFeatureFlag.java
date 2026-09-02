package gov.cms.madie.cqllibraryservice.dto;

/** Feature flags relevant to the cql-library-service */
public enum MadieFeatureFlag {
  // No feature flags currently exist. Replace PLACEHOLDER with next real feature flag to add
  PLACEHOLDER("Placeholder");

  private final String flag;

  MadieFeatureFlag(String flag) {
    this.flag = flag;
  }

  @Override
  public String toString() {
    return this.flag;
  }
}
