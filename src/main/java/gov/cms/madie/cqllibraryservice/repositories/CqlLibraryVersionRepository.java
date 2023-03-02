package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.models.common.Version;
import java.util.Optional;

public interface CqlLibraryVersionRepository {

  Optional<Version> findMaxVersionByGroupId(String groupId);

  Optional<Version> findMaxMinorVersionByGroupIdAndVersionMajor(String groupId, int majorVersion);
}
