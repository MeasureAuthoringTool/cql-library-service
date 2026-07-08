package gov.cms.madie.cqllibraryservice.services;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import gov.cms.madie.cqllibraryservice.config.EnvironmentConfig;
import gov.cms.madie.cqllibraryservice.exceptions.CqlElmTranslationServiceException;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.ElmJson;
import java.net.URI;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@AllArgsConstructor
public class ElmTranslatorClient {

  private EnvironmentConfig environmentConfig;
  private RestTemplate elmTranslatorRestTemplate;

  public ElmJson getElmJson(
      final String cql, String libraryModel, String accessToken, String errorSeverity) {
    try {
      URI uri = getCqlElmTranslationServiceUri(libraryModel);
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.TEXT_PLAIN);
      headers.set(HttpHeaders.AUTHORIZATION, accessToken);

      UriComponentsBuilder uriBuilder =
          UriComponentsBuilder.fromUri(uri).queryParam("errorSeverity", errorSeverity);

      HttpEntity<String> cqlEntity = new HttpEntity<>(cql, headers);
      return elmTranslatorRestTemplate
          .exchange(uriBuilder.build().toUri(), HttpMethod.PUT, cqlEntity, ElmJson.class)
          .getBody();
    } catch (Exception ex) {
      log.error("An error occurred calling the CQL to ELM translation service", ex);
      throw new CqlElmTranslationServiceException(
          "There was an error calling CQL-ELM translation service", ex);
    }
  }

  private URI getCqlElmTranslationServiceUri(String libraryModel) {
    var isQdm = StringUtils.equals(libraryModel, ModelType.QDM_5_6.getValue());
    String baseUrl =
        isQdm
            ? environmentConfig.getQdmCqlElmServiceBaseUrl()
            : environmentConfig.getFhirCqlElmServiceBaseUrl();
    return URI.create(baseUrl + environmentConfig.getCqlElmServiceElmJsonUri());
  }

  public boolean hasErrors(ElmJson elmJson) {
    if (elmJson == null) {
      return true;
    }
    try {
      ObjectMapper mapper = JsonMapper.builder().build();
      JsonNode jsonNode = mapper.readTree(elmJson.getJson());
      boolean hasError = false;
      if (jsonNode.has("errorExceptions") && jsonNode.get("errorExceptions").isArray()) {
        JsonNode errorExceptions = jsonNode.get("errorExceptions");
        for (JsonNode errorException : errorExceptions) {
          // TODO CqlCompilerException is the sole reason for this project to rely on cql-to-elm
          // dependency.. we could expose this value from madie-models instead
          if (CqlCompilerException.ErrorSeverity.Error.name()
              .equals(errorException.path("errorSeverity").asString(""))) {
            hasError = true;
            break;
          }
        }
      }
      return hasError;
    } catch (Exception ex) {
      log.error("An error occurred parsing the response from the CQL-ELM translation service", ex);
      throw new CqlElmTranslationServiceException(
          "There was an error calling CQL-ELM translation service", ex);
    }
  }
}
