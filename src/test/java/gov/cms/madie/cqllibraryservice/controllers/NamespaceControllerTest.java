package gov.cms.madie.cqllibraryservice.controllers;

import gov.cms.madie.cqllibraryservice.dto.NamespaceDTO;
import gov.cms.madie.cqllibraryservice.services.NamespaceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamespaceControllerTest {

  @Mock private NamespaceService namespaceService;

  @InjectMocks NamespaceController namespaceController;

  @Test
  public void testGetAllNamespaces() {
    List<NamespaceDTO> mockedResponse =
        List.of(
            NamespaceDTO.builder()
                .namespaceCanonical("http://hl7.org/fhir/us/qicore")
                .namespacePrefix("hl7.fhir.us.qicore")
                .build());
    when(namespaceService.getAllNamespaces()).thenReturn(mockedResponse);

    ResponseEntity<List<NamespaceDTO>> response = namespaceController.getAllNamespaces();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(mockedResponse, response.getBody());
  }
}
