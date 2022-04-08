package gov.cms.madie.cqllibraryservice.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Version {
  private int major;
  private int minor;
  private int revisionNumber;

  public static Version parse(String versionStr) {
    if (versionStr == null || versionStr.isEmpty()) {
      return new Version();
    }

    String[] parts = versionStr.split("\\.");

    return Version.builder()
        .major(Integer.parseInt(parts[0]))
        .minor(parts.length > 1 ? Integer.parseInt(parts[1]) : 0)
        .revisionNumber(parts.length > 2 ? Integer.parseInt(parts[2]) : 0)
        .build();
  }

  @Override
  public String toString() {
    return this.getMajor()
        + "."
        + this.getMinor()
        + "."
        + String.format("%03d", this.getRevisionNumber());
  }
}
