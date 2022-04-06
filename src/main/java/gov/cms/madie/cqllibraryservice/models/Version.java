package gov.cms.madie.cqllibraryservice.models;

import lombok.Getter;

@Getter
public enum Version {
  private Integer major;
  private Integer minor;
  private Integer revisionNumber;

  @Override
  public String toString() {
    return this.getMajor() + "." + this.getMinor() + "." + this.getRevisionNumber();
  }

  // format revisionNumber
}
