package gov.cms.madie.cqllibraryservice.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import gov.cms.madie.cqllibraryservice.services.*;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import gov.cms.madie.cqllibraryservice.config.security.SecurityConfig;
import gov.cms.madie.cqllibraryservice.dto.DownloadedPackageResult;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;

@ActiveProfiles("test")
@WebMvcTest({CqlLibraryAdminController.class})
@Import(SecurityConfig.class)
public class CqlLibraryAdminControllerMvcTest {

  @MockitoBean CqlLibraryRepository cqlLibraryRepository;
  @MockitoBean VersionService versionService;
  @MockitoBean CqlLibraryService cqlLibraryService;
  @MockitoBean LibrarySetService librarySetService;
  @MockitoBean private CqlDifferentiatorService cqlDifferentiatorService;
  @MockitoBean ActionLogService actionLogService;
  @MockitoBean CqlLibraryLockService cqlLibraryLockService;
  @MockitoBean private UserServiceClient userServiceClient;
  @MockitoBean AdminService adminService;
  @MockitoBean IgPackageService igPackageService;

  @Captor private ArgumentCaptor<CqlLibrary> cqlLibraryArgumentCaptor;

  @Captor private ArgumentCaptor<ActionType> actionTypeArgumentCaptor;

  @Captor private ArgumentCaptor<String> targetIdArgumentCaptor;

  @Autowired private MockMvc mockMvc;

  private static final String TEST_USER_ID = "test-okta-user-id-123";
  public static final String ELM_SEVERITY = "Info";
  public static final String TEST_OKTA = "test-okta";

  @Test
  public void testAdminLibraryGetSharedWith() throws Exception {
    CqlLibrary testLibrary = CqlLibrary.builder().id("12345").build();
    AclSpecification acl1 = new AclSpecification();
    acl1.setUserId("raoulduke");
    acl1.setRoles(Set.of(RoleEnum.SHARED_WITH));

    List<AclSpecification> acls = List.of(acl1);
    LibrarySet librarySet = LibrarySet.builder().acls(acls).owner("owner1").build();
    testLibrary.setLibrarySet(librarySet);
    when(cqlLibraryService.findCqlLibraryById(anyString(), anyString())).thenReturn(testLibrary);

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/cql-libraries/admin/sharedWith?libraryids=12345")
                .with(csrf())
                .with(user(TEST_USER_ID).roles("MADIE-ADMIN"))
                .header("Authorization", TEST_OKTA)
                .header("harpId", "owner1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].libraryId", equalTo("12345")))
        .andExpect(jsonPath("$[0].sharedWith.[0].userId", equalTo("raoulduke")));
  }

  @Test
  public void testAdminLibraryGetSharedWithHarpIdMismatchException() throws Exception {
    CqlLibrary testLibrary = CqlLibrary.builder().id("12345").build();
    AclSpecification acl1 = new AclSpecification();
    acl1.setUserId("raoulduke");
    acl1.setRoles(Set.of(RoleEnum.SHARED_WITH));

    List<AclSpecification> acls = List.of(acl1);
    LibrarySet librarySet = LibrarySet.builder().acls(acls).owner("owner1").build();
    testLibrary.setLibrarySet(librarySet);
    when(cqlLibraryService.findCqlLibraryById(anyString(), anyString())).thenReturn(testLibrary);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/cql-libraries/admin/sharedWith?libraryids=12345")
                    .with(csrf())
                    .with(user(TEST_USER_ID).roles("MADIE-ADMIN"))
                    .header("Authorization", TEST_OKTA)
                    .header("harpId", "owner2"))
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Response could not be completed because the HARP id of owner2 passed in does not match the owner of the library with the library id of 12345. The owner of the library is owner1"))
            .andReturn();

    assertEquals(HttpStatus.CONFLICT.value(), result.getResponse().getStatus());
  }

  @Test
  public void testAdminLibraryGetSharedWithNone() throws Exception {
    CqlLibrary testLibrary = CqlLibrary.builder().id("12345").build();

    LibrarySet librarySet = LibrarySet.builder().acls(null).owner("owner1").build();
    testLibrary.setLibrarySet(librarySet);
    when(cqlLibraryService.findCqlLibraryById(anyString(), anyString())).thenReturn(testLibrary);

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/cql-libraries/admin/sharedWith?libraryids=12345")
                .with(csrf())
                .with(user(TEST_USER_ID).roles("MADIE-ADMIN"))
                .header("Authorization", TEST_OKTA)
                .header("harpId", "owner1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].libraryId", equalTo("12345")))
        .andExpect(jsonPath("$[0].sharedWith", equalTo(null)));
  }

  @Test
  public void testAdminLibraryGetSharedWithResourceNotFoundException() throws Exception {
    when(cqlLibraryService.findCqlLibraryById(anyString(), anyString())).thenReturn(null);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/cql-libraries/admin/sharedWith?libraryids=12345")
                    .with(csrf())
                    .with(user(TEST_USER_ID).roles("MADIE-ADMIN"))
                    .header("Authorization", TEST_OKTA)
                    .header("harpId", "owner1"))
            .andReturn();

    assertEquals(HttpStatus.NOT_FOUND.value(), result.getResponse().getStatus());
  }

  @Test
  public void testAdminMultipleLibrariesGetSharedWith() throws Exception {
    CqlLibrary lib1 = CqlLibrary.builder().id("12345").build();
    CqlLibrary lib2 = CqlLibrary.builder().id("6789").build();
    AclSpecification acl1 = new AclSpecification();
    acl1.setUserId("raoulduke");
    acl1.setRoles(Set.of(RoleEnum.SHARED_WITH));

    List<AclSpecification> acls = List.of(acl1);
    LibrarySet librarySet = LibrarySet.builder().acls(acls).owner("owner1").build();
    lib1.setLibrarySet(librarySet);
    lib2.setLibrarySet(librarySet);
    when(cqlLibraryService.findCqlLibraryById(eq("12345"), anyString())).thenReturn(lib1);
    when(cqlLibraryService.findCqlLibraryById(eq("6789"), anyString())).thenReturn(lib2);

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/cql-libraries/admin/sharedWith?libraryids=12345,6789")
                .with(csrf())
                .with(user(TEST_USER_ID).roles("MADIE-ADMIN"))
                .header("Authorization", TEST_OKTA)
                .header("harpId", "owner1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].libraryId", equalTo("12345")))
        .andExpect(jsonPath("$[1].libraryId", equalTo("6789")))
        .andExpect(jsonPath("$[0].sharedWith.[0].userId", equalTo("raoulduke")));
  }

  @Test
  void testDeleteLibraryAlongWithVersions() throws Exception {
    doNothing()
        .when(cqlLibraryService)
        .deleteLibraryAlongWithVersions(anyString(), anyString(), anyString());
    MvcResult result =
        mockMvc
            .perform(
                delete("/cql-libraries/admin/Test/delete-all-versions")
                    .with(user(TEST_USER_ID).roles("MADIE-ADMIN"))
                    .with(csrf())
                    .header("Authorization", TEST_OKTA)
                    .header("harpId", "owner1"))
            .andReturn();
    assertEquals(result.getResponse().getStatus(), HttpStatus.OK.value());
    assertEquals(
        result.getResponse().getContentAsString(),
        "The library and all its associated versions have been removed successfully.");
  }

  @Test
  void testDeleteLibraryAlongWithVersionsMissingAdminKey() throws Exception {
    doNothing()
        .when(cqlLibraryService)
        .deleteLibraryAlongWithVersions(anyString(), anyString(), anyString());
    MvcResult result =
        mockMvc
            .perform(
                delete("/cql-libraries/admin/Test/delete-all-versions")
                    .with(user(TEST_USER_ID).roles("MADIE-USER"))
                    .with(csrf())
                    .header("Authorization", TEST_OKTA)
                    .header("harpId", "owner1"))
            .andReturn();
    assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
  }

  @Test
  void exportSharedWithReturnsExcelFile() throws Exception {
    byte[] excelContent = "mock excel content".getBytes();
    when(adminService.exportSharedWithLibraries(any(), anyString(), anyString()))
        .thenReturn(excelContent);

    MvcResult result =
        mockMvc
            .perform(
                put("/cql-libraries/admin/shared-access-report")
                    .with(csrf())
                    .with(user(TEST_USER_ID).roles("MADIE-ADMIN"))
                    .header("Authorization", TEST_OKTA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(List.of("lib1", "lib2"))))
            .andExpect(status().isOk())
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"LibrarySharingExport.xlsx\""))
            .andExpect(
                header()
                    .string(
                        HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(content().bytes(excelContent))
            .andReturn();
    assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
  }

  @Test
  void exportSharedWithReturnsExcelFileForSingleLibrary() throws Exception {
    byte[] excelContent = "mock excel content".getBytes();
    when(adminService.exportSharedWithLibraries(eq(List.of("singleLib")), anyString(), anyString()))
        .thenReturn(excelContent);

    MvcResult result =
        mockMvc
            .perform(
                put("/cql-libraries/admin/shared-access-report")
                    .with(csrf())
                    .with(user(TEST_USER_ID).roles("MADIE-ADMIN"))
                    .header("Authorization", TEST_OKTA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(List.of("singleLib"))))
            .andExpect(status().isOk())
            .andExpect(content().bytes(excelContent))
            .andReturn();
    assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
  }

  @Test
  void exportSharedWithForbiddenForNonAdminUser() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                put("/cql-libraries/admin/shared-access-report")
                    .with(csrf())
                    .with(user(TEST_USER_ID).roles("MADIE-USER"))
                    .header("Authorization", TEST_OKTA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(List.of("lib1"))))
            .andExpect(status().isForbidden())
            .andReturn();
    assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
  }

  @Test
  void exportSharedWithBadRequestForEmptyLibraryList() throws Exception {
    when(adminService.exportSharedWithLibraries(any(), anyString(), anyString()))
        .thenThrow(
            new IllegalArgumentException(
                "Please provide at least one library id to export the shared access report."));

    MvcResult result =
        mockMvc
            .perform(
                put("/cql-libraries/admin/shared-access-report")
                    .with(csrf())
                    .with(user(TEST_USER_ID).roles("MADIE-ADMIN"))
                    .header("Authorization", TEST_OKTA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(List.of())))
            .andReturn();

    assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
  }

  @Test
  void testDeleteCqlLibraryByIdForbiddenForNonAdmin() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                delete("/cql-libraries/admin/libId123")
                    .with(user(TEST_USER_ID).roles("MADIE-USER"))
                    .with(csrf())
                    .header("harpId", "owner1"))
            .andReturn();
    assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
  }

  @Test
  void testDeleteCqlLibraryByIdNotFound() throws Exception {
    when(cqlLibraryService.deleteCqlLibraryById(anyString(), anyString(), anyString()))
        .thenThrow(
            new gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException(
                "CqlLibrary", "id", "libId123"));

    MvcResult result =
        mockMvc
            .perform(
                delete("/cql-libraries/admin/libId123")
                    .with(user(TEST_USER_ID).roles("MADIE-ADMIN"))
                    .with(csrf())
                    .header("harpId", "owner1"))
            .andReturn();
    assertEquals(HttpStatus.NOT_FOUND.value(), result.getResponse().getStatus());
  }

  @Test
  void testDeleteCqlLibraryByIdHarpIdMismatch() throws Exception {
    when(cqlLibraryService.deleteCqlLibraryById(anyString(), anyString(), anyString()))
        .thenThrow(
            new gov.cms.madie.cqllibraryservice.exceptions.HarpIdMismatchException(
                "owner2", "owner1", "libId123"));

    MvcResult result =
        mockMvc
            .perform(
                delete("/cql-libraries/admin/libId123")
                    .with(user(TEST_USER_ID).roles("MADIE-ADMIN"))
                    .with(csrf())
                    .header("harpId", "owner2"))
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Response could not be completed because the HARP id of owner2 passed in does not match the owner of the library with the library id of libId123. The owner of the library is owner1"))
            .andReturn();
    assertEquals(HttpStatus.CONFLICT.value(), result.getResponse().getStatus());
  }

  @Test
  void testDeleteCqlLibraryByIdSuccess() throws Exception {
    CqlLibrary library = CqlLibrary.builder().id("libId123").cqlLibraryName("TestLib").build();
    when(cqlLibraryService.deleteCqlLibraryById(anyString(), anyString(), anyString()))
        .thenReturn(library);

    MvcResult result =
        mockMvc
            .perform(
                delete("/cql-libraries/admin/libId123")
                    .with(user(TEST_USER_ID).roles("MADIE-ADMIN"))
                    .with(csrf())
                    .header("harpId", "owner1"))
            .andExpect(jsonPath("$.id", equalTo("libId123")))
            .andExpect(jsonPath("$.cqlLibraryName", equalTo("TestLib")))
            .andReturn();
    assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
  }

  @Test
  void exportSharedWithUnauthorizedWithoutAuthentication() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                put("/cql-libraries/admin/shared-access-report")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(List.of("lib1"))))
            .andExpect(status().isUnauthorized())
            .andReturn();
    assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getResponse().getStatus());
  }

  @Test
  void testInstallIgPackageSuccess() throws Exception {
    DownloadedPackageResult downloadResult =
        DownloadedPackageResult.builder()
            .packageId("hl7.fhir.us.qicore")
            .version("7.0.2")
            .success(true)
            .packageLocation("/cache/hl7.fhir.us.qicore#7.0.2")
            .build();
    when(igPackageService.installIgPackage(anyString(), anyString(), anyString()))
        .thenReturn(downloadResult);

    String requestBody = "{\"packageId\":\"hl7.fhir.us.qicore\",\"packageVersion\":\"7.0.2\"}";

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/cql-libraries/admin/ig-packages")
                    .with(csrf())
                    .with(user(TEST_USER_ID).roles("MADIE-ADMIN"))
                    .header("Authorization", TEST_OKTA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.packageId").value("hl7.fhir.us.qicore"))
            .andExpect(jsonPath("$.version").value("7.0.2"))
            .andReturn();
    assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
  }

  @Test
  void testInstallIgPackageForbiddenForNonAdmin() throws Exception {
    String requestBody = "{\"packageId\":\"hl7.fhir.us.qicore\",\"packageVersion\":\"7.0.2\"}";

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/cql-libraries/admin/ig-packages")
                    .with(csrf())
                    .with(user(TEST_USER_ID).roles("MADIE-USER"))
                    .header("Authorization", TEST_OKTA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isForbidden())
            .andReturn();
    assertEquals(HttpStatus.FORBIDDEN.value(), result.getResponse().getStatus());
  }

  @Test
  void testInstallIgPackageBadRequestWhenPackageIdMissing() throws Exception {
    String requestBody = "{\"packageVersion\":\"7.0.2\"}";

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/cql-libraries/admin/ig-packages")
                    .with(csrf())
                    .with(user(TEST_USER_ID).roles("MADIE-ADMIN"))
                    .header("Authorization", TEST_OKTA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.packageId").value("Package ID is required."))
            .andReturn();
    assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
  }

  @Test
  void testInstallIgPackageBadRequestWhenPackageVersionMissing() throws Exception {
    String requestBody = "{\"packageId\":\"hl7.fhir.us.qicore\"}";

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/cql-libraries/admin/ig-packages")
                    .with(csrf())
                    .with(user(TEST_USER_ID).roles("MADIE-ADMIN"))
                    .header("Authorization", TEST_OKTA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.validationErrors.packageVersion").value("Package Version is required."))
            .andReturn();
    assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
  }

  @Test
  void testInstallIgPackageBadRequestWhenBodyEmpty() throws Exception {
    String requestBody = "{}";

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/cql-libraries/admin/ig-packages")
                    .with(csrf())
                    .with(user(TEST_USER_ID).roles("MADIE-ADMIN"))
                    .header("Authorization", TEST_OKTA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isBadRequest())
            .andReturn();
    assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
  }

  @Test
  void testInstallIgPackageUnauthorizedWithoutAuthentication() throws Exception {
    String requestBody = "{\"packageId\":\"hl7.fhir.us.qicore\",\"packageVersion\":\"7.0.2\"}";

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/cql-libraries/admin/ig-packages")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isUnauthorized())
            .andReturn();
    assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getResponse().getStatus());
  }

  @Test
  void testInstallIgPackageReturnsInternalServerErrorWhenInstallationFails() throws Exception {
    DownloadedPackageResult failedResult =
        DownloadedPackageResult.builder()
            .packageId("hl7.fhir.us.qicore")
            .version("7.0.2")
            .success(false)
            .errorMessage("Failed to download package from registry")
            .build();
    when(igPackageService.installIgPackage(anyString(), anyString(), anyString()))
        .thenReturn(failedResult);

    String requestBody = "{\"packageId\":\"hl7.fhir.us.qicore\",\"packageVersion\":\"7.0.2\"}";

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/cql-libraries/admin/ig-packages")
                    .with(csrf())
                    .with(user(TEST_USER_ID).roles("MADIE-ADMIN"))
                    .header("Authorization", TEST_OKTA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.packageId").value("hl7.fhir.us.qicore"))
            .andExpect(jsonPath("$.errorMessage").value("Failed to download package from registry"))
            .andReturn();
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), result.getResponse().getStatus());
  }
}
