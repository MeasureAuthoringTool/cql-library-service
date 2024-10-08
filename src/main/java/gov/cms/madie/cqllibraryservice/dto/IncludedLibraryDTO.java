package gov.cms.madie.cqllibraryservice.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.library.LibrarySet;
import gov.cms.madie.models.utils.VersionJsonSerializer;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class IncludedLibraryDTO {
  private String id;
  private String librarySetId;
  private String cqlLibraryName;
  private String model;
  private String cql;

  @JsonSerialize(using = VersionJsonSerializer.VersionSerializer.class)
  @JsonDeserialize(using = VersionJsonSerializer.VersionDeserializer.class)
  private Version version;

  private LibrarySet librarySet;
  private List<String> relatedVersions;
}
