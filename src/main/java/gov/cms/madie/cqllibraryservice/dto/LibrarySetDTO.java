package gov.cms.madie.cqllibraryservice.dto;

import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
public class LibrarySetDTO {
  private LibrarySet librarySet;
  private List<CqlLibrary> libraries;
}
