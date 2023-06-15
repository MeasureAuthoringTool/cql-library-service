package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.models.common.Version;
import java.util.Optional;

public interface CqlLibraryVersionRepository {

  Optional<Version> findMaxVersionByLibrarySetId(String librarySetId);

  Optional<Version> findMaxMinorVersionByLibrarySetIdAndVersionMajor(
      String librarySetId, int majorVersion);
}
