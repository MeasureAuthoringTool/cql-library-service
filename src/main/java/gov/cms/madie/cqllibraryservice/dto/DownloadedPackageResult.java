package gov.cms.madie.cqllibraryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadedPackageResult {
  private String packageId;
  private String version;
  private boolean success;
  private String packageLocation;
  private String errorMessage;
}
