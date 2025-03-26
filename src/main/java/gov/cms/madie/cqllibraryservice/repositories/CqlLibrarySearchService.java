package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CqlLibrarySearchService {

  Page<LibraryListDTO> searchLibrariesByCriteria(
      String userId, Pageable pageable, String searchCriteria, boolean filterByCurrentUser);
}
