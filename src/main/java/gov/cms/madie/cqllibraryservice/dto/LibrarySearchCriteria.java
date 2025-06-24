package gov.cms.madie.cqllibraryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class LibrarySearchCriteria {
  private String searchField;
  private List<String> optionalSearchProperties; // can be ["libraryName", "version"] ..etc
}
