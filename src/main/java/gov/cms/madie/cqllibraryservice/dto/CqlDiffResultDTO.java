package gov.cms.madie.cqllibraryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of CQL differentiator operation containing all file comparisons between two library
 * versions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CqlDiffResultDTO {

  // List of file comparisons with normalized and reordered text
  private List<CqlFileComparisonDTO> comparisons;

  private String oldLibraryId;

  private String newLibraryId;
}
