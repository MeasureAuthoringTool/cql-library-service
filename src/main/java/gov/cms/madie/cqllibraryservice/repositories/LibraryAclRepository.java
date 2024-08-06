package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.models.dto.LibraryUsage;

import java.util.List;

public interface LibraryAclRepository {
  /**
   * Find all library by user(either owner or SHARED_WITH ACL )
   *
   * @param userId- current user
   * @return List of cqlLibraries
   */
  List<LibraryListDTO> findAllLibrariesByUser(String userId);

  /**
   * Get all the libraries(name, version and owner) if they include library with given library name,
   * version doesn't matter
   *
   * @param name -> library name for which usage needs to be determined
   * @return List<LibraryUsage> -> LibraryUsage: name, version and owner of including library
   */
  List<LibraryUsage> findLibraryUsageByLibraryName(String name);
}
