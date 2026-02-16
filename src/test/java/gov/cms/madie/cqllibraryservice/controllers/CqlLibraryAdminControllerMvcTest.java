package gov.cms.madie.cqllibraryservice.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import gov.cms.madie.cqllibraryservice.config.security.SecurityConfig;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.cqllibraryservice.services.ActionLogService;
import gov.cms.madie.cqllibraryservice.services.CqlDifferentiatorService;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryLockService;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryService;
import gov.cms.madie.cqllibraryservice.services.LibrarySetService;
import gov.cms.madie.cqllibraryservice.services.VersionService;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.ModelType;
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

  @Captor private ArgumentCaptor<CqlLibrary> cqlLibraryArgumentCaptor;

  @Captor private ArgumentCaptor<ActionType> actionTypeArgumentCaptor;

  @Captor private ArgumentCaptor<String> targetIdArgumentCaptor;

  @Autowired private MockMvc mockMvc;

  private static final String TEST_USER_ID = "test-okta-user-id-123";
  private static final String TEST_LIBRARYSET_ID = "test-okta-user-id-321";
  private static final String TEST_API_KEY_HEADER = "api-key";
  private static final String TEST_API_KEY_HEADER_VALUE = "0a51991c";
  private static final String MODEL = ModelType.QI_CORE.toString();
  public static final String ELM_SEVERITY = "Info";

  @Test
  public void testAdminMeasureGetSharedWith() throws Exception {
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
            MockMvcRequestBuilders.get("/cql-libraries/admin/sharedWith?measureids=12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(TEST_API_KEY_HEADER, TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta")
                .header("harpId", "owner1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].libraryId", equalTo("12345")))
        .andExpect(jsonPath("$[0].sharedWith.[0].userId", equalTo("raoulduke")));
  }

  @Test
  public void testAdminMeasureGetSharedWithHarpIdMismatchException() throws Exception {
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
                MockMvcRequestBuilders.get("/cql-libraries/admin/sharedWith?measureids=12345")
                    .with(csrf())
                    .with(user(TEST_USER_ID))
                    .header(TEST_API_KEY_HEADER, TEST_API_KEY_HEADER_VALUE)
                    .header("Authorization", "test-okta")
                    .header("harpId", "owner2"))
            .andExpect(
                jsonPath("$.message")
                    .value(
                        "Response could not be completed because the HARP id of owner2 passed in does not match the owner of the library with the library id of 12345. The owner of the library is owner1"))
            .andReturn();

    assertEquals(HttpStatus.CONFLICT.value(), result.getResponse().getStatus());
  }

  @Test
  public void testAdminMeasureGetSharedWithNone() throws Exception {
    CqlLibrary testLibrary = CqlLibrary.builder().id("12345").build();

    LibrarySet librarySet = LibrarySet.builder().acls(null).owner("owner1").build();
    testLibrary.setLibrarySet(librarySet);
    when(cqlLibraryService.findCqlLibraryById(anyString(), anyString())).thenReturn(testLibrary);

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/cql-libraries/admin/sharedWith?measureids=12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(TEST_API_KEY_HEADER, TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta")
                .header("harpId", "owner1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].libraryId", equalTo("12345")))
        .andExpect(jsonPath("$[0].sharedWith", equalTo(null)));
  }

  @Test
  public void testAdminMeasureGetSharedWithResourceNotFoundException() throws Exception {
    when(cqlLibraryService.findCqlLibraryById(anyString(), anyString())).thenReturn(null);

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/cql-libraries/admin/sharedWith?measureids=12345")
                    .with(csrf())
                    .with(user(TEST_USER_ID))
                    .header(TEST_API_KEY_HEADER, TEST_API_KEY_HEADER_VALUE)
                    .header("Authorization", "test-okta")
                    .header("harpId", "owner1"))
            .andReturn();

    assertEquals(HttpStatus.NOT_FOUND.value(), result.getResponse().getStatus());
  }

  @Test
  public void testAdminMultipleMeasuresGetSharedWith() throws Exception {
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
            MockMvcRequestBuilders.get("/cql-libraries/admin/sharedWith?measureids=12345,6789")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(TEST_API_KEY_HEADER, TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta")
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
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("Authorization", "test-okta")
                    .header("api-key", "0a51991c")
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
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("Authorization", "test-okta")
                    .header("harpId", "owner1"))
            .andReturn();
    assertEquals(result.getResponse().getStatus(), HttpStatus.FORBIDDEN.value());
  }
}
