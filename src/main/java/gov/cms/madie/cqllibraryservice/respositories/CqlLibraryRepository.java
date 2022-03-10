package gov.cms.madie.cqllibraryservice.respositories;

import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CqlLibraryRepository extends MongoRepository<CqlLibrary, String> {
  List<CqlLibrary> findAllByCreatedBy(String user);
}
