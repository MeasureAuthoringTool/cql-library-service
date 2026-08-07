package gov.cms.madie.cqllibraryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NamespaceDTO {

  private String namespaceCanonical;
  private String namespacePrefix;
}
