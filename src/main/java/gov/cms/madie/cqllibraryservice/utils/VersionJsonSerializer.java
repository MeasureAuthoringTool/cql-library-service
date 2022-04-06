package gov.cms.madie.cqllibraryservice.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import gov.cms.madie.cqllibraryservice.models.Version;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;

@Slf4j
@JsonComponent
public class VersionJsonSerializer {

  public static class VersionSerializer extends JsonSerializer<Version> {
    @Override
    public void serialize(Version value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeString(value == null ? null : value.toString());
    }
  }

  public static class VersionDeserializer extends JsonDeserializer<Version> {
    @Override
    public Version deserialize(JsonParser jp, DeserializationContext ctxt) {
      try {
        JsonNode node = jp.getCodec().readTree(jp);
        return Version.parse(node.asText());
      } catch (Exception ex) {
        log.error("An error occurred while deserializing the version", ex);
      }
      return null;
    }
  }


}
