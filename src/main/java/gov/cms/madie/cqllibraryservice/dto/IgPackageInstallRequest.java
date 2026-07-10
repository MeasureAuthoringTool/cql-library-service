package gov.cms.madie.cqllibraryservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IgPackageInstallRequest {

  @NotBlank(message = "Package ID is required.")
  private String packageId;

  @NotBlank(message = "Package Version is required.")
  private String packageVersion;
}
