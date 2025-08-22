package gov.cms.madie.cqllibraryservice.locks;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "cqlLibraryLock")
public class CqlLibraryLock {
  @Id private String cqlLibraryId;
  private String lockedBy;
  private Instant lockedAt;

  @Indexed(expireAfter = "0m")
  private Instant expiresAt;
}
