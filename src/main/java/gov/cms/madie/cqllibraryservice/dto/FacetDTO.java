package gov.cms.madie.cqllibraryservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FacetDTO {
  List<Object> count;
  List<LibraryListDTO> queryResults;
}
