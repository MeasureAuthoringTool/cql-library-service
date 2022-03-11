package gov.cms.madie.cqllibraryservice.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CqlLibrary {
  @Id private String id;

  private String cqlLibraryName;
  private Instant createdAt;
  private String createdBy;
  private Instant lastModifiedAt;
  private String lastModifiedBy;
}
