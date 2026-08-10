package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.NamespaceDTO;
import gov.cms.madie.cqllibraryservice.repositories.ExternalLibraryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NamespaceService {

  private final ExternalLibraryRepository externalLibraryRepository;

  public List<NamespaceDTO> getAllNamespaces() {
    List<NamespaceDTO> namespaces = externalLibraryRepository.findDistinctNamespaces();
    log.info("Found [{}] known namespaces", namespaces.size());
    return namespaces;
  }
}
