package gov.cms.madie.cqllibraryservice.dto;

/** Feature flags relevant to the cql-library-service */
public enum MadieFeatureFlag {
  LOCKING("Locking"),
  DISPLAY_OWNER("DisplayOwner");

  private final String flag;

  MadieFeatureFlag(String flag) {
    this.flag = flag;
  }

  @Override
  public String toString() {
    return this.flag;
  }
}
