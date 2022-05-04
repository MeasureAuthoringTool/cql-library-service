package gov.cms.madie.cqllibraryservice.services;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import gov.cms.madie.cqllibraryservice.config.EnvironmentConfig;
import gov.cms.madie.cqllibraryservice.exceptions.CqlElmTranslationServiceException;
import gov.cms.madie.cqllibraryservice.models.ElmJson;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class ElmTranslatorClientTest {

  @Mock private EnvironmentConfig environmentConfig;
  @Mock private RestTemplate restTemplate;

  @InjectMocks private ElmTranslatorClient elmTranslatorClient;

  @BeforeEach
  void beforeEach() {
    lenient().when(environmentConfig.getCqlElmServiceBaseUrl()).thenReturn("http://test");
    lenient()
        .when(environmentConfig.getCqlElmServiceElmJsonUri())
        .thenReturn("/cql/translator/cql");
  }

  @Test
  void testRestTemplateHandlesClientErrorException() {
    when(restTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenThrow(new HttpClientErrorException(HttpStatus.FORBIDDEN));
    assertThrows(
        CqlElmTranslationServiceException.class,
        () -> elmTranslatorClient.getElmJson("TEST_CQL", "TEST_TOKEN"));
  }

  @Test
  void testRestTemplateReturnsElmJson() {
    ElmJson elmJson = ElmJson.builder().json("{}").xml("<></>").build();
    when(restTemplate.exchange(
            any(URI.class), eq(HttpMethod.PUT), any(HttpEntity.class), any(Class.class)))
        .thenReturn(ResponseEntity.ok(elmJson));
    ElmJson output = elmTranslatorClient.getElmJson("TEST_CQL", "TEST_TOKEN");
    assertThat(output, is(equalTo(elmJson)));
  }

  @Test
  void testHasErrorsHandlesNull() {
    boolean output = elmTranslatorClient.hasErrors(null);
    assertThat(output, is(true));
  }

  @Test
  void testHasErrorsHandlesMalformedJson() {
    ElmJson elmJson = ElmJson.builder().json("NOT_JSON").build();
    assertThrows(
        CqlElmTranslationServiceException.class, () -> elmTranslatorClient.hasErrors(elmJson));
  }

  @Test
  void testHasErrorsReturnsTrue() {
    final String json =
        "{\n"
            + "          \"errorExceptions\": [{\n"
            + "                                  \"startLine\" : 2,\n"
            + "                                  \"startChar\" : 1,\n"
            + "                                  \"endLine\" : 2,\n"
            + "                                  \"endChar\" : 6,\n"
            + "                                  \"errorType\" : null,\n"
            + "                                  \"errorSeverity\" : \"Error\",\n"
            + "                                  \"targetIncludeLibraryId\" : \"TestLib\",\n"
            + "                                  \"targetIncludeLibraryVersionId\" : \"2\",\n"
            + "                                  \"type\" : null,\n"
            + "                                  \"message\" : \"Could not resolve identifier define in the current library.\"\n"
            + "                                }]\n"
            + "        }";
    //        """
    //        {
    //          "errorExceptions": [{
    //                                  "startLine" : 2,
    //                                  "startChar" : 1,
    //                                  "endLine" : 2,
    //                                  "endChar" : 6,
    //                                  "errorType" : null,
    //                                  "errorSeverity" : "Error",
    //                                  "targetIncludeLibraryId" : "TestLib",
    //                                  "targetIncludeLibraryVersionId" : "2",
    //                                  "type" : null,
    //                                  "message" : "Could not resolve identifier define in the
    // current library."
    //                                }]
    //        }
    //        """;
    ElmJson elmJson = ElmJson.builder().json(json).build();
    boolean output = elmTranslatorClient.hasErrors(elmJson);
    assertThat(output, is(true));
  }

  @Test
  void testHasErrorsReturnsFalseForEmptyArray() {
    final String json = "{\n" + "          \"errorExceptions\": []\n" + "        }";
    //    final String json = """
    //        {
    //          "errorExceptions": []
    //        }
    //        """;
    ElmJson elmJson = ElmJson.builder().json(json).build();
    boolean output = elmTranslatorClient.hasErrors(elmJson);
    assertThat(output, is(false));
  }

  @Test
  void testHasErrorsReturnsFalseForNullFieldValue() {
    //    final String json = """
    //        {
    //          "errorExceptions": null
    //        }
    //        """;
    final String json = "{\n" + "          \"errorExceptions\": null\n" + "        }";
    ElmJson elmJson = ElmJson.builder().json(json).build();
    boolean output = elmTranslatorClient.hasErrors(elmJson);
    assertThat(output, is(false));
  }

  @Test
  void testHasErrorsReturnsFalseForMissingField() {
    //    final String json =
    //        """
    //        {
    //          "library" : {
    //            "annotation" : [ { } ]
    //          }
    //        }
    //        """;

    final String json =
        "{\n"
            + "          \"library\" : {\n"
            + "            \"annotation\" : [ { } ]\n"
            + "          }\n"
            + "        }";
    ElmJson elmJson = ElmJson.builder().json(json).build();
    boolean output = elmTranslatorClient.hasErrors(elmJson);
    assertThat(output, is(false));
  }
}
