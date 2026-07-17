package gov.cms.madie.cqllibraryservice.utils;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.skyscreamer.jsonassert.JSONAssert.assertEquals;

import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONCompareMode;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
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
  public void testSerializerHandlesVersionInCqlLibrary() throws JSONException {
    CqlLibrary library =
        CqlLibrary.builder()
            .version(Version.builder().major(1).minor(2).revisionNumber(0).build())
            .librarySetId("testid")
            .active(true)
            .build();
    String output = objectMapper.writeValueAsString(library);
    log.info("output: {}", output);
    assertEquals(
        "{\"active\":true,\"cql\":null,\"cqlErrors\":false,\"cqlLibraryLock\":null,\"cqlLibraryName\":null,\"createdAt\":null,\"createdBy\":null,\"description\":null,\"draft\":false,\"elmJson\":null,\"elmXml\":null,\"experimental\":false,\"id\":null,\"includedLibraries\":null,\"lastModifiedAt\":null,\"lastModifiedBy\":null,\"librarySet\":null,\"librarySetId\":\"testid\",\"model\":null,\"ownerDisplayName\":null,\"publisher\":null,\"review\":null,\"version\":\"1.2.000\"}",
        output,
        JSONCompareMode.STRICT);
  }

  @Test
  public void testSerializerHandlesVersionWithMajorAndMinor() {
    Version versionWithMajorAndMinor =
        Version.builder().major(1).minor(2).revisionNumber(0).build();
    String output = objectMapper.writeValueAsString(versionWithMajorAndMinor);
    assertThat(versionWithMajorAndMinor.toString(), is(equalTo("1.2.000")));
    assertThat(output, is(equalTo("{\"major\":1,\"minor\":2,\"revisionNumber\":0}")));
  }

  @Test
  public void testSerializerHandlesVersionAllZeroes() {
    Version allZeroVersion = new Version();
    String output = objectMapper.writeValueAsString(allZeroVersion);
    assertThat(allZeroVersion.toString(), is(equalTo("0.0.000")));
    assertThat(output, is(equalTo("{\"major\":0,\"minor\":0,\"revisionNumber\":0}")));
  }

  @Test
  public void testDeSerializerHandlesAllZeroes() {
    Version expected = new Version(0, 0, 0);
    String json = "{\"version\":\"0.0.000\"}";
    Version output = objectMapper.readValue(json, Version.class);
    assertThat(output, is(equalTo(expected)));
  }

  @Test
  public void testDeSerializerHandlesMajorMinor() {
    Version expected = new Version(2, 45, 0);
    String json = "{\"major\":2,\"minor\":45,\"revisionNumber\":0}";
    Version output = objectMapper.readValue(json, Version.class);
    assertThat(output, is(equalTo(expected)));
  }

  @Test
  public void testDeSerializerHandlesNull() {
    String json = "null";
    Version output = objectMapper.readValue(json, Version.class);
    assertThat(output, is(nullValue()));
  }

  @Test
  public void testDeSerializerThrowsOnInvalidVersionString() {
    String json = "\"ab.bc.ddd\"";
    assertThrows(JacksonException.class, () -> objectMapper.readValue(json, Version.class));
  }
}
