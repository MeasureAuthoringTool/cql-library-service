package gov.cms.madie.cqllibraryservice.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class ServiceConfig {
  private String madieVersion;
  private Map<String, Boolean> features;
}
