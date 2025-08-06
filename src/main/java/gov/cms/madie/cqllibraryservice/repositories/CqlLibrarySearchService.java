package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.dto.LibrarySearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CqlLibrarySearchService {

  Page<LibraryListDTO> searchLibrariesByCriteria(
      String userId,
      Pageable pageable,
      LibrarySearchCriteria librarySearchCriteria,
      boolean filterByCurrentUser);

  List<LibraryListDTO> findLibrariesByLibrarySetId(
      String librarySetId,
      boolean sortByLatestVersion,
      LibrarySearchCriteria librarySearchCriteria);
}
