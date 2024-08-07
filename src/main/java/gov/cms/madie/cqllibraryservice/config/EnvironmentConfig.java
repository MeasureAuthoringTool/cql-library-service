package gov.cms.madie.cqllibraryservice.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class EnvironmentConfig {

  @Value("${madie.cql-elm.service.qdm-base-url}")
  private String qdmCqlElmServiceBaseUrl;

  @Value("${madie.cql-elm.service.fhir-base-url}")
  private String fhirCqlElmServiceBaseUrl;

  @Value("${madie.cql-elm.service.elm-json-uri}")
  private String cqlElmServiceElmJsonUri;

  @Value("${madie.measure-service.base-url}")
  private String measureServiceBaseUrl;
}
