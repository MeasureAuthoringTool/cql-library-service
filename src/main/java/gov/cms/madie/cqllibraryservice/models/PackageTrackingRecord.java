package gov.cms.madie.cqllibraryservice.models;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document
@CompoundIndex(
    name = "packageId_version_idx",
    def = "{'packageId': 1, 'version': 1}",
    unique = true)
public class PackageTrackingRecord {
  @Id private String id;
  private String packageId;
  private String version;
  private List<String> childIgs;
  private PackageStatus status;
  private String errorMessage;
  private Instant lastAttemptedAt;
  private Instant downloadedAt;
  private String initiatedBy;

  /** Number of CQL Libraries discovered across all packages during the last import run. */
  private Integer discoveredLibraryCount;

  /** Number of CQL Libraries actually persisted during the last import run. */
  private Integer persistedLibraryCount;

  /** Timestamp when the most recent CQL Library import started. */
  private Instant importStartedAt;

  /** Timestamp when the most recent CQL Library import completed. */
  private Instant importCompletedAt;
}
