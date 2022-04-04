package gov.cms.madie.cqllibraryservice.respositories;

import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CqlLibraryRepository extends MongoRepository<CqlLibrary, String> {
  Optional<CqlLibrary> findByCqlLibraryName(String cqlLibraryName);

  boolean existsByCqlLibraryName(String cqlLibraryName);

  List<CqlLibrary> findAllByCreatedBy(String user);
}
