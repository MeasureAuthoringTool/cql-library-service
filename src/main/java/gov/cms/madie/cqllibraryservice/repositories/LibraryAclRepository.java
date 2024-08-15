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

  /**
   * This method queries libraries by library name and model. Library name match is a "like"
   * comparison. It can be library name or part of the library name. Results returned are sorted in
   * ascending order by library name and descending by version. Draft libraries are excluded.
   *
   * @param name -> library name
   * @param model -> library model
   * @return list of LibraryListDTO
   */
  List<LibraryListDTO> findLibrariesByNameAndModelOrderByNameAscAndVersionDsc(
      String name, String model);
}
