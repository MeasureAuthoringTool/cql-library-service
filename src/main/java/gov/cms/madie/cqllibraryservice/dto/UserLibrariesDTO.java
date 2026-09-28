package gov.cms.madie.cqllibraryservice.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single user's owned and shared libraries for the bulk Full User Export. Each list holds the
 * latest library per family (one row per librarySetId), mirroring what the per-user search endpoint
 * returns - but assembled for many users in a single request.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLibrariesDTO {
  private List<LibraryListDTO> ownedLibraries = new ArrayList<>();
  private List<LibraryListDTO> sharedLibraries = new ArrayList<>();
}
