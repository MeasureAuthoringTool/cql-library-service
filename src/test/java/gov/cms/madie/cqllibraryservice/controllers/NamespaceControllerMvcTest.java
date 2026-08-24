package gov.cms.madie.cqllibraryservice.controllers;

import gov.cms.madie.cqllibraryservice.config.security.SecurityConfig;
import gov.cms.madie.cqllibraryservice.dto.NamespaceDTO;
import gov.cms.madie.cqllibraryservice.services.NamespaceService;
import gov.cms.madie.cqllibraryservice.services.UserServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@WebMvcTest({NamespaceController.class})
@Import(SecurityConfig.class)
@ExtendWith(MockitoExtension.class)
public class NamespaceControllerMvcTest {

  private static final String API_KEY_HEADER = "custom-header";
  private static final String API_KEY_VALUE = "test value";

  @MockitoBean private UserServiceClient userServiceClient;
  @MockitoBean private NamespaceService namespaceService;

  @Autowired private MockMvc mockMvc;

  @Test
  public void testGetAllNamespacesReturnsKnownNamespaces() throws Exception {
    when(namespaceService.getAllNamespaces())
        .thenReturn(
            List.of(
                NamespaceDTO.builder()
                    .namespaceCanonical("http://hl7.org/fhir/us/qicore")
                    .namespacePrefix("hl7.fhir.us.qicore")
                    .build(),
                NamespaceDTO.builder()
                    .namespaceCanonical("http://hl7.org/fhir/uv/cqm")
                    .namespacePrefix("hl7.fhir.uv.cqm")
                    .build()));
    mockMvc
        .perform(
            get("/cql-libraries/namespaces").with(csrf()).header(API_KEY_HEADER, API_KEY_VALUE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].namespaceCanonical").value("http://hl7.org/fhir/us/qicore"))
        .andExpect(jsonPath("$[0].namespacePrefix").value("hl7.fhir.us.qicore"))
        .andExpect(jsonPath("$[1].namespaceCanonical").value("http://hl7.org/fhir/uv/cqm"))
        .andExpect(jsonPath("$[1].namespacePrefix").value("hl7.fhir.uv.cqm"));
    verify(namespaceService, times(1)).getAllNamespaces();
  }

  @Test
  public void testGetAllNamespacesReturnsUnauthorizedWhenApiKeyMissing() throws Exception {
    mockMvc
        .perform(get("/cql-libraries/namespaces").with(csrf()))
        .andExpect(status().isUnauthorized());
    verify(namespaceService, never()).getAllNamespaces();
  }

  @Test
  public void testGetAllNamespacesReturnsUnauthorizedWhenApiKeyIsWrong() throws Exception {
    mockMvc
        .perform(get("/cql-libraries/namespaces").with(csrf()).header(API_KEY_HEADER, "wrong key"))
        .andExpect(status().isUnauthorized());
    verify(namespaceService, never()).getAllNamespaces();
  }
}
