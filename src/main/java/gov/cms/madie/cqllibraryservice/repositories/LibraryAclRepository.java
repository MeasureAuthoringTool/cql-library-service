package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.models.library.CqlLibrary;

import java.util.List;

public interface LibraryAclRepository {
  /**
   * Find all library by user(either owner or SHARED_WITH ACL )
   *
   * @param userId- current user
   * @return List of cqlLibraries
   */
  List<CqlLibrary> findAllLibrariesByUser(String userId);
}
