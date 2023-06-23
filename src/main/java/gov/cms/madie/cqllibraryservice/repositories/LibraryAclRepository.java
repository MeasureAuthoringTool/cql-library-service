package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.library.CqlLibrary;

import java.util.List;

public interface LibraryAclRepository {
  /**
   * Library is considered to be my library if the libraryset.owner is the userid provided
   * @param userId- current user
   * @return List of cqlLibraries
   */
  List<CqlLibrary> findAllMyLibraries(String userId);
  /**
   * @param cqlLibraryName- current user
   * @param draft- current user
   * @param cqlLibraryName- current user
   * @param version- current user
   * @return List of cqlLibraries
   */
  List<CqlLibrary> findAllByCqlLibraryNameAndDraftAndVersion(
      String cqlLibraryName, boolean draft, Version version);
  /**
   * @param cqlLibraryName- current user
   * @param draft- current user
   * @param cqlLibraryName- current user
   * @param version- current user
   * @param model- current user
   * @return List of cqlLibraries
   */
  List<CqlLibrary> findAllByCqlLibraryNameAndDraftAndVersionAndModel(
      String cqlLibraryName, boolean draft, Version version, String model);
}
