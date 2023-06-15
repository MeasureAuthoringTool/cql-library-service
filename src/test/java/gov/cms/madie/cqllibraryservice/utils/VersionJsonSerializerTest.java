package gov.cms.madie.cqllibraryservice.utils;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.common.Version;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Slf4j
@ActiveProfiles("test")
@SpringBootTest
class VersionJsonSerializerTest {

  @Autowired private ObjectMapper objectMapper;

  @Test
  public void testSerializerHandlesVersionInCqlLibrary() throws JsonProcessingException {
    CqlLibrary library =
        CqlLibrary.builder()
            .version(Version.builder().major(1).minor(2).revisionNumber(0).build())
            .librarySetId("testid")
            .build();
    String output = objectMapper.writeValueAsString(library);
    log.info("output: {}", output);
    assertThat(
        output,
        is(
            equalTo(
                "{\"id\":null,\"librarySetId\":\"testid\",\"cqlLibraryName\":null,\"model\":null,\"version\":\"1.2.000\",\"draft\":false,\"cqlErrors\":false,\"cql\":null,\"elmJson\":null,\"elmXml\":null,\"createdAt\":null,\"createdBy\":null,\"lastModifiedAt\":null,\"lastModifiedBy\":null,\"publisher\":null,\"description\":null,\"experimental\":false,\"programUseContext\":null,\"librarySet\":null}")));
  }

  @Test
  public void testSerializerHandlesVersionWithMajorAndMinor() throws JsonProcessingException {
    Version versionWithMajorAndMinor =
        Version.builder().major(1).minor(2).revisionNumber(0).build();
    String output = objectMapper.writeValueAsString(versionWithMajorAndMinor);
    assertThat(versionWithMajorAndMinor.toString(), is(equalTo("1.2.000")));
    assertThat(output, is(equalTo("{\"major\":1,\"minor\":2,\"revisionNumber\":0}")));
  }

  @Test
  public void testSerializerHandlesVersionAllZeroes() throws JsonProcessingException {
    Version allZeroVersion = new Version();
    String output = objectMapper.writeValueAsString(allZeroVersion);
    assertThat(allZeroVersion.toString(), is(equalTo("0.0.000")));
    assertThat(output, is(equalTo("{\"major\":0,\"minor\":0,\"revisionNumber\":0}")));
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
    String json = "{\"major\":2,\"minor\":45,\"revisionNumber\":0}";
    Version output = objectMapper.readValue(json, Version.class);
    assertThat(output, is(equalTo(expected)));
  }

  @Test
  public void testDeSerializerHandlesNull() {
    String json = "null";
    Version output = null;
    try {
      objectMapper.readValue(json, Version.class);
    } catch (JsonProcessingException ex) {
    }
    assertThat(output, is(nullValue()));
  }

  @Test
  public void testDeSerializerHandlesException() {
    String json = "\"ab.bc.ddd\"";
    Version output = null;
    try {
      objectMapper.readValue(json, Version.class);
    } catch (JsonProcessingException ex) {
    }
    assertThat(output, is(nullValue()));
  }
}
