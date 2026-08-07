package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.dto.NamespaceDTO;
import gov.cms.madie.cqllibraryservice.repositories.ExternalLibraryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamespaceServiceTest {

  @Mock private ExternalLibraryRepository externalLibraryRepository;

  @InjectMocks private NamespaceService namespaceService;

  @Test
  void testGetAllNamespacesReturnsAllKnownNamespaces() {
    NamespaceDTO qiCore =
        NamespaceDTO.builder()
            .namespaceCanonical("http://hl7.org/fhir/us/qicore")
            .namespacePrefix("hl7.fhir.us.qicore")
            .build();
    NamespaceDTO cqfmScoring =
        NamespaceDTO.builder()
            .namespaceCanonical("http://hl7.org/fhir/uv/cqm")
            .namespacePrefix("hl7.fhir.uv.cqm")
            .build();
    when(externalLibraryRepository.findDistinctNamespaces())
        .thenReturn(List.of(qiCore, cqfmScoring));

    List<NamespaceDTO> namespaces = namespaceService.getAllNamespaces();

    assertThat(namespaces.size(), is(equalTo(2)));
    assertThat(
        namespaces.get(0).getNamespaceCanonical(), is(equalTo("http://hl7.org/fhir/us/qicore")));
    assertThat(namespaces.get(0).getNamespacePrefix(), is(equalTo("hl7.fhir.us.qicore")));
    assertThat(
        namespaces.get(1).getNamespaceCanonical(), is(equalTo("http://hl7.org/fhir/uv/cqm")));
    assertThat(namespaces.get(1).getNamespacePrefix(), is(equalTo("hl7.fhir.uv.cqm")));
  }

  @Test
  void testGetAllNamespacesReturnsEmptyListWhenNoExternalLibrariesExist() {
    when(externalLibraryRepository.findDistinctNamespaces()).thenReturn(List.of());

    List<NamespaceDTO> namespaces = namespaceService.getAllNamespaces();

    assertThat(namespaces.isEmpty(), is(true));
  }
}
