package gov.cms.madie.cqllibraryservice.repositories;

import gov.cms.madie.cqllibraryservice.models.PackageTrackingRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PackageTrackingRepository extends MongoRepository<PackageTrackingRecord, String> {
  Optional<PackageTrackingRecord> findByPackageIdAndVersion(String packageId, String version);
}
