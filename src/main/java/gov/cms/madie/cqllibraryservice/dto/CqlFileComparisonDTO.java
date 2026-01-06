package gov.cms.madie.cqllibraryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a comparison between old and new versions of a CQL file. Contains normalized and
 * reordered text ready for diff display.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CqlFileComparisonDTO {
  // Original filename from the old library (may be "not found" for new files)
  private String oldFileName;

  // Filename from the new library
  private String newFileName;

  // Normalized text content from old library
  private String oldText;

  // Normalized and reordered text content from new library
  private String newText;
}
