package gov.cms.madie.cqllibraryservice.dto;

/** Feature flags relevant to the measure-service */
public enum MadieFeatureFlag {
  LIBRARY_SEARCH("LibrarySearch"),
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
