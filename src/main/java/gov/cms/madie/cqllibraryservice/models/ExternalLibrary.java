package gov.cms.madie.cqllibraryservice.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Represents a External CQL Library imported from a FHIR Implementation Guide (IG) package.
 *
 * <p>External CQL Libraries are separate from user-created CQL Libraries. They are not editable by
 * users and are not included in user library validations, but are available for use by translation,
 * execution, and the CQL Builder.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "externalLibrary")
@CompoundIndex(
    name = "ns_canonical_library_name_version_idx",
    def = "{'canonical': 1, 'libraryName': 1, 'version': 1}",
    unique = true)
public class ExternalLibrary {

  @Id private String id;
  private String librarySetId;
  private String libraryName;
  private String libraryTitle;
  private String description;
  private String version;

  /**
   * The canonical URL of the IG that contains this library (from {@code package.json#canonical}).
   */
  private String canonical;

  /** The NPM package name of the IG that contains this library (from {@code package.json#name}). */
  private String namespacePrefix;

  /** The raw CQL source text. */
  private String cqlContent;

  /** Always {@code false} – imported common libraries are never in draft state. */
  private boolean draft;

  /** The system owner for common libraries. */
  private String publisher;

  /** The system owner for common libraries. */
  private String createdBy;

  /** Timestamp when this library was first imported into the system. */
  private Instant dateImported;

  /** The original FHIR resource JSON string */
  private String fhirResource;
}
