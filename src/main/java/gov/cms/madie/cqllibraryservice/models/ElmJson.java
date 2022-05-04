package gov.cms.madie.cqllibraryservice.models;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ElmJson {
  private String json;
  private String xml;
}
