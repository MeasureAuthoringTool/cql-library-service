package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.Version;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public class CqlLibraryVersionRepositoryImpl implements CqlLibraryVersionRepository {

  private final MongoTemplate mongoTemplate;

  public CqlLibraryVersionRepositoryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Optional<Version> findMaxVersionByGroupId(String groupId) {
    Query q =
        new Query(Criteria.where("groupId").is(groupId).and("draft").is(false))
            .with(Sort.by(Sort.Direction.DESC, "version.major", "version.minor"))
            .limit(1);
    CqlLibrary one = mongoTemplate.findOne(q, CqlLibrary.class);

    if (one == null || one.getVersion() == null) {
      return Optional.empty();
    } else {
      return Optional.of(one.getVersion());
    }
  }

  @Override
  public Optional<Version> findMaxMinorVersionByGroupIdAndVersionMajor(
      String groupId, int majorVersion) {
    Query q =
        new Query(
                Criteria.where("groupId")
                    .is(groupId)
                    .and("version.major")
                    .is(majorVersion)
                    .and("draft")
                    .is(false))
            .with(Sort.by(Sort.Direction.DESC, "version.major", "version.minor"))
            .limit(1);
    CqlLibrary one = mongoTemplate.findOne(q, CqlLibrary.class);

    if (one == null || one.getVersion() == null) {
      return Optional.empty();
    } else {
      return Optional.of(one.getVersion());
    }
  }
}
