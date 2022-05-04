package gov.cms.madie.cqllibraryservice.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class EnvironmentConfig {

  @Value("${madie.cql-elm.service.base-url}")
  private String cqlElmServiceBaseUrl;

  @Value("${madie.cql-elm.service.elm-json-uri}")
  private String cqlElmServiceElmJsonUri;
}
