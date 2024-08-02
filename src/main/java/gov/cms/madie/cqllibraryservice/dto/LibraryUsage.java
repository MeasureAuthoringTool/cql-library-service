package gov.cms.madie.cqllibraryservice.dto;

import gov.cms.madie.models.common.Version;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LibraryUsage {
  private String name;
  private Version version;
  private String owner;
}
