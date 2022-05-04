package gov.cms.madie.cqllibraryservice.utils;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import gov.cms.madie.cqllibraryservice.models.Version;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
class VersionJsonSerializerTest {

  @Autowired private ObjectMapper objectMapper;

  @Test
  public void testSerializerHandlesVersionInCqlLibrary() throws JsonProcessingException {
    CqlLibrary library =
        CqlLibrary.builder()
            .version(Version.builder().major(1).minor(2).revisionNumber(0).build())
            .build();
    String output = objectMapper.writeValueAsString(library);
    log.info("output: {}", output);
    assertThat(
        output,
        is(
            equalTo(
                "{\"id\":null,\"cqlLibraryName\":null,\"model\":null,\"version\":\"1.2.000\",\"draft\":false,\"groupId\":null,\"cqlErrors\":false,\"cql\":null,\"elmJson\":null,\"elmXml\":null,\"createdAt\":null,\"createdBy\":null,\"lastModifiedAt\":null,\"lastModifiedBy\":null,\"publisher\":null,\"description\":null,\"experimental\":false}")));
  }

  @Test
  public void testSerializerHandlesVersionWithMajorAndMinor() throws JsonProcessingException {
    String output =
        objectMapper.writeValueAsString(
            Version.builder().major(1).minor(2).revisionNumber(0).build());
    assertThat(output, is(equalTo("\"1.2.000\"")));
  }

  @Test
  public void testSerializerHandlesVersionAllZeroes() throws JsonProcessingException {
    String output = objectMapper.writeValueAsString(new Version());
    assertThat(output, is(equalTo("\"0.0.000\"")));
  }

  @Test
  public void testDeSerializerHandlesAllZeroes() throws JsonProcessingException {
    Version expected = new Version(0, 0, 0);
    String json = "{\"version\":\"0.0.000\"}";
    Version output = objectMapper.readValue(json, Version.class);
    assertThat(output, is(equalTo(expected)));
  }

  @Test
  public void testDeSerializerHandlesMajorMinor() throws JsonProcessingException {
    Version expected = new Version(2, 45, 0);
    String json = "\"2.45.000\"";
    Version output = objectMapper.readValue(json, Version.class);
    assertThat(output, is(equalTo(expected)));
  }

  @Test
  public void testDeSerializerHandlesNull() throws JsonProcessingException {
    String json = "null";
    Version output = objectMapper.readValue(json, Version.class);
    assertThat(output, is(nullValue()));
  }

  @Test
  public void testDeSerializerHandlesException() throws JsonProcessingException {
    String json = "\"ab.bc.ddd\"";
    Version output = objectMapper.readValue(json, Version.class);
    assertThat(output, is(nullValue()));
  }
}
