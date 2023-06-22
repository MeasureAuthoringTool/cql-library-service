package gov.cms.madie.cqllibraryservice.repositories;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.models.library.CqlLibrary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface LibraryAclRepository {
    /**
     * Library is considered to be my measure if provided user is the owner of this measure or is
     * @param userId- current user
     * @return Pageable List of measures
     */

    List<CqlLibrary> findAllMyLibraries(String userId);

    List<CqlLibrary> findAllByCqlLibraryNameAndDraftAndVersion(
            String cqlLibraryName, boolean draft, Version version);

    List<CqlLibrary> findAllByCqlLibraryNameAndDraftAndVersionAndModel(
            String cqlLibraryName, boolean draft, Version version, String model);
}