package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;

import java.util.List;

public interface LibraryAclRepository {
  /**
   * Find all library by user(either owner or SHARED_WITH ACL )
   *
   * @param userId- current user
   * @return List of cqlLibraries
   */
  List<LibraryListDTO> findAllLibrariesByUser(String userId);
}
