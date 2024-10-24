package gov.cms.madie.cqllibraryservice.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.cqllibraryservice.config.EnvironmentConfig;
import gov.cms.madie.cqllibraryservice.exceptions.CqlElmTranslationServiceException;
import gov.cms.madie.models.common.ModelType;
import gov.cms.madie.models.measure.ElmJson;
import java.net.URI;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@AllArgsConstructor
public class ElmTranslatorClient {

  private EnvironmentConfig environmentConfig;
  private RestTemplate elmTranslatorRestTemplate;

  public ElmJson getElmJson(final String cql, String libraryModel, String accessToken) {
    try {
      URI uri = getCqlElmTranslationServiceUri(libraryModel);
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.TEXT_PLAIN);
      headers.set(HttpHeaders.AUTHORIZATION, accessToken);
      HttpEntity<String> cqlEntity = new HttpEntity<>(cql, headers);
      return elmTranslatorRestTemplate
          .exchange(uri, HttpMethod.PUT, cqlEntity, ElmJson.class)
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
      ObjectMapper mapper = new ObjectMapper();
      JsonNode jsonNode = mapper.readTree(elmJson.getJson());
      return jsonNode.has("errorExceptions") && jsonNode.get("errorExceptions").size() > 0;
    } catch (Exception ex) {
      log.error("An error occurred parsing the response from the CQL-ELM translation service", ex);
      throw new CqlElmTranslationServiceException(
          "There was an error calling CQL-ELM translation service", ex);
    }
  }
}
