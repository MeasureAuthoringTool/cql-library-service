package gov.cms.madie.cqllibraryservice.controllers;

import gov.cms.madie.cqllibraryservice.config.security.SecurityConfig;
import gov.cms.madie.cqllibraryservice.dto.*;
import gov.cms.madie.cqllibraryservice.exceptions.*;
import gov.cms.madie.cqllibraryservice.locks.CqlLibraryLock;
import gov.cms.madie.cqllibraryservice.services.*;
import gov.cms.madie.models.common.ModelType;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.CqlLibraryDraft;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

import gov.cms.madie.models.library.LibrarySet;

import org.bson.types.ObjectId;
import org.hamcrest.CustomMatcher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@WebMvcTest({CqlLibraryController.class})
@Import(SecurityConfig.class)
public class CqlLibraryControllerMvcTest {

  private static final String TEST_USER_ID = "test-okta-user-id-123";
  private static final String TEST_LIBRARYSET_ID = "test-okta-user-id-321";
  private static final String MODEL = ModelType.QI_CORE.toString();
  public static final String ELM_SEVERITY = "Info";

  @MockitoBean CqlLibraryRepository cqlLibraryRepository;
  @MockitoBean VersionService versionService;
  @MockitoBean CqlLibraryService cqlLibraryService;
  @MockitoBean LibrarySetService librarySetService;
  @MockitoBean private CqlDifferentiatorService cqlDifferentiatorService;
  @MockitoBean ActionLogService actionLogService;
  @MockitoBean private UserServiceClient userServiceClient;
  @MockitoBean private CqlLibraryLockService cqlLibraryLockService;

  @Captor private ArgumentCaptor<CqlLibrary> cqlLibraryArgumentCaptor;

  @Captor private ArgumentCaptor<ActionType> actionTypeArgumentCaptor;

  @Captor private ArgumentCaptor<String> targetIdArgumentCaptor;

  @Autowired private MockMvc mockMvc;

  public String toJsonString(Object obj) throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    return mapper.writeValueAsString(obj);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForNullCqlLibraryName() throws Exception {
    String json = toJsonString(CqlLibrary.builder().cqlLibraryName(null).model(MODEL).build());
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(false);
    mockMvc
        .perform(
            post("/cql-libraries")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.cqlLibraryName").value("Library name is required."));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForEmptyCqlLibraryName() throws Exception {
    String json = toJsonString(CqlLibrary.builder().cqlLibraryName("").model(MODEL).build());
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(false);
    mockMvc
        .perform(
            post("/cql-libraries")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.cqlLibraryName").value("Library name is required."));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForLowercaseStartCharacter()
      throws Exception {
    String json =
        toJsonString(
            CqlLibrary.builder()
                .cqlLibraryName("aBCDefg")
                .model(MODEL)
                .librarySetId(TEST_LIBRARYSET_ID)
                .build());
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(false);
    mockMvc
        .perform(
            post("/cql-libraries")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.cqlLibrary")
                .value(
                    "Library name must start with an upper case letter, "
                        + "followed by alpha-numeric character(s) and must not contain "
                        + "spaces or other special characters except of underscore for QDM."));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForContainingSpaces() throws Exception {
    String json =
        toJsonString(
            CqlLibrary.builder()
                .cqlLibraryName("With  spaces ")
                .model(MODEL)
                .librarySetId(TEST_LIBRARYSET_ID)
                .build());
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(false);
    mockMvc
        .perform(
            post("/cql-libraries")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.cqlLibrary")
                .value(
                    "Library name must start with an upper case letter, "
                        + "followed by alpha-numeric character(s) and must not contain "
                        + "spaces or other special characters except of underscore for QDM."));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForContainingUnderscore() throws Exception {
    String json =
        toJsonString(
            CqlLibrary.builder()
                .cqlLibraryName("With_underscore")
                .model(MODEL)
                .librarySetId(TEST_LIBRARYSET_ID)
                .build());
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(false);
    mockMvc
        .perform(
            post("/cql-libraries")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.cqlLibrary")
                .value(
                    "Library name must start with an upper case letter, "
                        + "followed by alpha-numeric character(s) and must not contain "
                        + "spaces or other special characters except of underscore for QDM."));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForContainingSpecialCharacters()
      throws Exception {
    String json =
        toJsonString(
            CqlLibrary.builder()
                .cqlLibraryName("Name*$")
                .model(MODEL)
                .librarySetId(TEST_LIBRARYSET_ID)
                .build());
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(false);
    mockMvc
        .perform(
            post("/cql-libraries")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.cqlLibrary")
                .value(
                    "Library name must start with an upper case letter, "
                        + "followed by alpha-numeric character(s) and must not contain "
                        + "spaces or other special characters except of underscore for QDM."));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForLengthOver64Chars() throws Exception {
    final String reallyLongName =
        "Reallylongnamethatisover255charactersbutwouldotherwisebevalidifitwereunder255charactersandisjustanattempttogetthevalidatortoblowupwiththisstupidlylongnamethatnobodywouldeveractuallyusebecausereallywhowouldtypeareallylongnamelikethiswithoutspacesorunderscorestoseparatewords";
    String json =
        toJsonString(
            CqlLibrary.builder()
                .cqlLibraryName(reallyLongName)
                .model(MODEL)
                .librarySetId(TEST_LIBRARYSET_ID)
                .build());
    when(cqlLibraryRepository.existsByCqlLibraryName(anyString())).thenReturn(false);
    mockMvc
        .perform(
            post("/cql-libraries")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.cqlLibraryName")
                .value("Library name cannot be more than 64 characters."));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForDuplicateCqlLibraryName()
      throws Exception {
    String json =
        toJsonString(
            CqlLibrary.builder()
                .cqlLibraryName("DuplicateName")
                .model(MODEL)
                .librarySetId(TEST_LIBRARYSET_ID)
                .build());
    doThrow(new DuplicateKeyException("cqlLibraryName", "Library name must be unique."))
        .when(cqlLibraryService)
        .checkDuplicateCqlLibraryName(anyString());
    mockMvc
        .perform(
            post("/cql-libraries")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.cqlLibraryName").value("Library name must be unique."));
    verify(cqlLibraryService, times(1)).checkDuplicateCqlLibraryName(anyString());
    verifyNoMoreInteractions(cqlLibraryRepository);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForInvalidModel() throws Exception {
    String json =
        toJsonString(
            CqlLibrary.builder()
                .cqlLibraryName("Name")
                .model("RANDOM")
                .librarySetId(TEST_LIBRARYSET_ID)
                .build());
    doNothing().when(cqlLibraryService).checkDuplicateCqlLibraryName(anyString());
    mockMvc
        .perform(
            post("/cql-libraries")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.model")
                .value("Model must be one of the supported types in MADiE."));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForNullModel() throws Exception {
    String json = toJsonString(CqlLibrary.builder().cqlLibraryName("Name").model(null).build());
    mockMvc
        .perform(
            post("/cql-libraries")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.validationErrors.model").value("Model is required."));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testCreateCqlLibraryReturnsCreatedForValidObject() throws Exception {
    final String cql = "library AdvancedIllnessandFrailtyExclusion_QICore4 version '5.0.000'";
    CqlLibrary library =
        CqlLibrary.builder()
            .cqlLibraryName("NewValidName1")
            .model(MODEL)
            .cql(cql)
            .librarySetId(TEST_LIBRARYSET_ID)
            .build();

    String json = toJsonString(library);
    doNothing().when(cqlLibraryService).checkDuplicateCqlLibraryName(anyString());
    doNothing().when(librarySetService).createLibrarySet(anyString(), anyString(), anyString());
    String objectId = ObjectId.get().toHexString();
    when(cqlLibraryRepository.save(any(CqlLibrary.class)))
        .then(
            (args) -> {
              CqlLibrary lib = args.getArgument(0);
              lib.setId(objectId);
              return lib;
            });

    CustomMatcher<Instant> fiveMinMatcher =
        new CustomMatcher<>("Instant within last five minutes") {
          @Override
          public boolean matches(Object actual) {
            System.out.println(actual);
            final Instant i = Instant.parse(actual.toString());
            return Instant.now().minus(5, ChronoUnit.MINUTES).isBefore(i);
          }
        };

    mockMvc
        .perform(
            post("/cql-libraries")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.cqlLibraryName").value("NewValidName1"))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.cql").value(cql))
        .andExpect(jsonPath("$.createdBy").value(TEST_USER_ID))
        .andExpect(jsonPath("$.lastModifiedBy").value(TEST_USER_ID))
        .andExpect(jsonPath("$.createdAt").value(fiveMinMatcher))
        .andExpect(jsonPath("$.lastModifiedAt").value(fiveMinMatcher));
    verify(cqlLibraryService, times(1)).checkDuplicateCqlLibraryName(anyString());
    verify(cqlLibraryRepository, times(1)).save(any(CqlLibrary.class));

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(),
            actionTypeArgumentCaptor.capture(),
            anyString(),
            anyString());
    assertThat(targetIdArgumentCaptor.getValue(), is(notNullValue()));
    assertThat(actionTypeArgumentCaptor.getValue(), is(equalTo(ActionType.CREATED)));
  }

  @Test
  public void testCreateCqlLibraryReturnsCreatedForValidQdmLibrary() throws Exception {
    final String cql = "library QdmLibrary1 version '1.0.000'";
    CqlLibrary library =
        CqlLibrary.builder()
            .cqlLibraryName("NewValidNameQdm1")
            .model(ModelType.QDM_5_6.toString())
            .cql(cql)
            .librarySetId(TEST_LIBRARYSET_ID)
            .build();

    String json = toJsonString(library);
    doNothing().when(cqlLibraryService).checkDuplicateCqlLibraryName(anyString());
    doNothing().when(librarySetService).createLibrarySet(anyString(), anyString(), anyString());
    String objectId = ObjectId.get().toHexString();
    when(cqlLibraryRepository.save(any(CqlLibrary.class)))
        .then(
            (args) -> {
              CqlLibrary lib = args.getArgument(0);
              lib.setId(objectId);
              return lib;
            });

    CustomMatcher<Instant> fiveMinMatcher =
        new CustomMatcher<>("Instant within last five minutes") {
          @Override
          public boolean matches(Object actual) {
            System.out.println(actual);
            final Instant i = Instant.parse(actual.toString());
            return Instant.now().minus(5, ChronoUnit.MINUTES).isBefore(i);
          }
        };

    mockMvc
        .perform(
            post("/cql-libraries")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.cqlLibraryName").value("NewValidNameQdm1"))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.cql").value(cql))
        .andExpect(jsonPath("$.model").value(ModelType.QDM_5_6.toString()))
        .andExpect(jsonPath("$.createdBy").value(TEST_USER_ID))
        .andExpect(jsonPath("$.lastModifiedBy").value(TEST_USER_ID))
        .andExpect(jsonPath("$.createdAt").value(fiveMinMatcher))
        .andExpect(jsonPath("$.lastModifiedAt").value(fiveMinMatcher));
    verify(cqlLibraryService, times(1)).checkDuplicateCqlLibraryName(anyString());
    verify(cqlLibraryRepository, times(1)).save(any(CqlLibrary.class));

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(),
            actionTypeArgumentCaptor.capture(),
            anyString(),
            anyString());
    assertThat(targetIdArgumentCaptor.getValue(), is(notNullValue()));
    assertThat(actionTypeArgumentCaptor.getValue(), is(equalTo(ActionType.CREATED)));
  }

  @Test
  public void testGetCqlLibraryReturns404() throws Exception {
    doThrow(new ResourceNotFoundException("CQL Library", "Library1_ID"))
        .when(cqlLibraryService)
        .findCqlLibraryById(anyString(), anyString());
    mockMvc
        .perform(get("/cql-libraries/Libary1_ID").with(user(TEST_USER_ID)).with(csrf()))
        .andExpect(status().isNotFound());
    verify(cqlLibraryService, times(1)).findCqlLibraryById(anyString(), anyString());
  }

  @Test
  public void testGetCqlLibraryReturnsCqlLibrary() throws Exception {
    final Instant createdTime = Instant.now().minus(100, ChronoUnit.MINUTES);
    final CqlLibrary existingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .createdAt(createdTime)
            .createdBy("User1")
            .lastModifiedAt(createdTime)
            .lastModifiedBy("User1")
            .build();
    when(cqlLibraryService.findCqlLibraryById(anyString(), anyString()))
        .thenReturn(existingLibrary);
    mockMvc
        .perform(get("/cql-libraries/Libary1_ID").with(user(TEST_USER_ID)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cqlLibraryName").value(existingLibrary.getCqlLibraryName()))
        .andExpect(jsonPath("$.id").value(existingLibrary.getId()))
        .andExpect(jsonPath("$.createdBy").value("User1"))
        .andExpect(jsonPath("$.lastModifiedBy").value("User1"))
        .andExpect(jsonPath("$.createdAt").value(is(equalTo(createdTime.toString()))))
        .andExpect(jsonPath("$.lastModifiedAt").value(is(equalTo(createdTime.toString()))));
    verify(cqlLibraryService, times(1)).findCqlLibraryById(anyString(), anyString());
  }

  @Test
  public void testUpdateCqlLibraryReturns400ForNullLibraryId() throws Exception {
    final CqlLibrary updatingLibrary =
        CqlLibrary.builder()
            .id(null)
            .cqlLibraryName("NewName")
            .model(MODEL)
            .librarySetId(TEST_LIBRARYSET_ID)
            .build();
    String json = toJsonString(updatingLibrary);
    mockMvc
        .perform(
            put("/cql-libraries/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "CQL Library ID is required for Update (PUT) operation on a CQL Library. (PUT [base]/[resource]/[id])"));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testUpdateCqlLibraryReturns400ForEmptyLibraryId() throws Exception {
    final CqlLibrary updatingLibrary =
        CqlLibrary.builder()
            .id("")
            .cqlLibraryName("NewName")
            .model(MODEL)
            .librarySetId(TEST_LIBRARYSET_ID)
            .build();
    String json = toJsonString(updatingLibrary);
    mockMvc
        .perform(
            put("/cql-libraries/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "CQL Library ID is required for Update (PUT) operation on a CQL Library. (PUT [base]/[resource]/[id])"));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testUpdateCqlLibraryReturns400ForMismatchedLibraryId() throws Exception {
    final CqlLibrary updatingLibrary =
        CqlLibrary.builder()
            .id("Wrong_ID")
            .cqlLibraryName("NewName")
            .model(MODEL)
            .librarySetId(TEST_LIBRARYSET_ID)
            .build();
    String json = toJsonString(updatingLibrary);
    mockMvc
        .perform(
            put("/cql-libraries/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "CQL Library ID is required for Update (PUT) operation on a CQL Library. (PUT [base]/[resource]/[id])"));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testUpdateCqlLibraryReturns400ForNullName() throws Exception {
    final CqlLibrary updatingLibrary =
        CqlLibrary.builder().id("Wrong_ID").cqlLibraryName(null).model(MODEL).build();
    String json = toJsonString(updatingLibrary);
    mockMvc
        .perform(
            put("/cql-libraries/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.cqlLibraryName").value("Library name is required."));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testUpdateCqlLibraryReturns400ForEmptyName() throws Exception {
    final CqlLibrary updatingLibrary =
        CqlLibrary.builder().id("Wrong_ID").cqlLibraryName("").model(MODEL).build();
    String json = toJsonString(updatingLibrary);
    mockMvc
        .perform(
            put("/cql-libraries/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.cqlLibraryName").value("Library name is required."));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testUpdateCqlLibraryReturns400ForNullModel() throws Exception {
    final CqlLibrary updatingLibrary =
        CqlLibrary.builder().id("Wrong_ID").cqlLibraryName("LibraryName").model(null).build();
    String json = toJsonString(updatingLibrary);
    mockMvc
        .perform(
            put("/cql-libraries/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.validationErrors.model").value("Model is required."));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testUpdateCqlLibraryReturns400ForEmptyModel() throws Exception {
    final CqlLibrary updatingLibrary =
        CqlLibrary.builder()
            .id("Wrong_ID")
            .cqlLibraryName("LibraryName")
            .model("")
            .librarySetId(TEST_LIBRARYSET_ID)
            .build();
    String json = toJsonString(updatingLibrary);
    mockMvc
        .perform(
            put("/cql-libraries/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.model")
                .value("Model must be one of the supported types in MADiE."));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testUpdateCqlLibraryReturns400ForBadModel() throws Exception {
    final CqlLibrary updatingLibrary =
        CqlLibrary.builder()
            .id("Wrong_ID")
            .cqlLibraryName("LibraryName")
            .model("FAKE_MODEL")
            .librarySetId(TEST_LIBRARYSET_ID)
            .build();
    String json = toJsonString(updatingLibrary);
    mockMvc
        .perform(
            put("/cql-libraries/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.model")
                .value("Model must be one of the supported types in MADiE."));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testUpdateCqlLibraryReturns404ForNotFoundLibrary() throws Exception {
    final CqlLibrary updatingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("NewName")
            .model(MODEL)
            .librarySetId(TEST_LIBRARYSET_ID)
            .build();
    String json = toJsonString(updatingLibrary);
    doThrow(new ResourceNotFoundException("CQL Library", "Library1_ID"))
        .when(cqlLibraryService)
        .updateCqlLibrary(any(CqlLibrary.class), anyString());
    mockMvc
        .perform(
            put("/cql-libraries/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.message")
                .value("Could not find resource CQL Library with id: Library1_ID"));
    verify(cqlLibraryService, times(1)).updateCqlLibrary(any(CqlLibrary.class), anyString());
  }

  @Test
  public void testUpdateCqlLibraryReturns400ForNonUniqueLibraryName() throws Exception {
    final Instant createdTime = Instant.now().minus(100, ChronoUnit.MINUTES);
    final CqlLibrary existingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .draft(true)
            .createdAt(createdTime)
            .createdBy(TEST_USER_ID)
            .lastModifiedAt(createdTime)
            .lastModifiedBy("User1")
            .librarySetId(TEST_LIBRARYSET_ID)
            .librarySet(
                LibrarySet.builder().librarySetId(TEST_LIBRARYSET_ID).owner(TEST_USER_ID).build())
            .build();
    final CqlLibrary updatingLibrary =
        existingLibrary.toBuilder().id("Library1_ID").cqlLibraryName("NewName").build();
    String json = toJsonString(updatingLibrary);
    doThrow(new DuplicateKeyException("cqlLibraryName", "Library name must be unique."))
        .when(cqlLibraryService)
        .updateCqlLibrary(any(CqlLibrary.class), anyString());

    mockMvc
        .perform(
            put("/cql-libraries/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.validationErrors.cqlLibraryName").value("Library name must be unique."));
    verify(cqlLibraryService, times(1)).updateCqlLibrary(any(CqlLibrary.class), anyString());
  }

  @Test
  public void testUpdateCqlLibraryReturns409ForUpdateAttemptOnVersionedLibrary() throws Exception {
    final Instant createdTime = Instant.now().minus(100, ChronoUnit.MINUTES);
    final CqlLibrary existingLibrary =
        CqlLibrary.builder()
            .id("L1")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .draft(false)
            .createdAt(createdTime)
            .createdBy(TEST_USER_ID)
            .lastModifiedAt(createdTime)
            .lastModifiedBy("User1")
            .librarySetId(TEST_LIBRARYSET_ID)
            .librarySet(
                LibrarySet.builder().librarySetId("testLibrarySetId").owner(TEST_USER_ID).build())
            .build();
    final CqlLibrary updatingLibrary =
        existingLibrary.toBuilder().id("L1").cqlLibraryName("NewName").build();
    String json = toJsonString(updatingLibrary);
    doThrow(new InvalidResourceStateException("CQL Library", "L1"))
        .when(cqlLibraryService)
        .updateCqlLibrary(any(CqlLibrary.class), anyString());

    mockMvc
        .perform(
            put("/cql-libraries/L1")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Could not update resource CQL Library with id: L1. Resource is not a Draft."));
    verify(cqlLibraryService, times(1)).updateCqlLibrary(any(CqlLibrary.class), anyString());
  }

  @Test
  public void testUpdateCqlLibraryReturns200ForSuccessfulUpdate() throws Exception {
    final CqlLibrary updatingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .librarySetId(TEST_LIBRARYSET_ID)
            .cqlLibraryName("NewName")
            .model(ModelType.QI_CORE.getValue())
            .cql("library testCql version '2.1.000'")
            .draft(true)
            .build();

    when(cqlLibraryService.updateCqlLibrary(any(CqlLibrary.class), anyString()))
        .thenReturn(updatingLibrary);

    mockMvc
        .perform(
            put("/cql-libraries/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(toJsonString(updatingLibrary))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(content().json(toJsonString(updatingLibrary)));

    verify(cqlLibraryService, times(1)).updateCqlLibrary(updatingLibrary, TEST_USER_ID);
  }

  @Test
  public void testCreateDraftReturnsValidationErrorForContainingUnderscore() throws Exception {
    final CqlLibraryDraft draft = CqlLibraryDraft.builder().cqlLibraryName("Invalid_").build();
    mockMvc
        .perform(
            post("/cql-libraries/draft/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(toJsonString(draft))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(
            jsonPath("$.validationErrors.cqlLibraryName")
                .value(
                    "Library name must start with an upper case letter, "
                        + "followed by alpha-numeric character(s) and must not contain "
                        + "spaces or other special characters."));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testCreateDraftReturnsValidationErrorForContainingSpecialCharacters()
      throws Exception {
    final CqlLibraryDraft draft = CqlLibraryDraft.builder().cqlLibraryName("Name*$").build();
    mockMvc
        .perform(
            post("/cql-libraries/draft/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(toJsonString(draft))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(
            jsonPath("$.validationErrors.cqlLibraryName")
                .value(
                    "Library name must start with an upper case letter, "
                        + "followed by alpha-numeric character(s) and must not contain "
                        + "spaces or other special characters."));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testCreateDraftReturnsValidationErrorForLengthOver64Chars() throws Exception {
    final String reallyLongName =
        "Reallylongnamethatisover255charactersbutwouldotherwisebevalidifitwereunder255charactersandisjustanattempttogetthevalidatortoblowupwiththisstupidlylongnamethatnobodywouldeveractuallyusebecausereallywhowouldtypeareallylongnamelikethiswithoutspacesorunderscorestoseparatewords";
    final CqlLibraryDraft draft = CqlLibraryDraft.builder().cqlLibraryName(reallyLongName).build();
    mockMvc
        .perform(
            post("/cql-libraries/draft/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(toJsonString(draft))
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(
            jsonPath("$.validationErrors.cqlLibraryName")
                .value("Library name cannot be more than 64 characters."));
    verifyNoInteractions(cqlLibraryRepository);
  }

  @Test
  public void testCreateDraftReturnsConflictWhenDraftAlreadyExists() throws Exception {
    final Instant createdTime = Instant.now().minus(100, ChronoUnit.MINUTES);
    final CqlLibrary existingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .draft(true)
            .version(new Version(1, 0, 0))
            .createdAt(createdTime)
            .createdBy("User1")
            .lastModifiedAt(createdTime)
            .lastModifiedBy("User1")
            .build();
    final String json =
        toJsonString(
            existingLibrary.toBuilder()
                .draft(false)
                .version(new Version(2, 1, 0))
                .cql("library Library1 version '1.0.000'")
                .build());

    when(versionService.createDraft(anyString(), anyString(), anyString(), anyString()))
        .thenThrow(
            new ResourceNotDraftableException(
                "CQL Library", "A draft already exists for the CQL Library Group."));
    mockMvc
        .perform(
            post("/cql-libraries/draft/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
    verify(versionService, times(1))
        .createDraft(
            eq("Library1_ID"), eq("Library1"), eq(ModelType.QI_CORE.getValue()), eq(TEST_USER_ID));
  }

  @Test
  public void testCreateDraftReturnsNotFound() throws Exception {
    final Instant createdTime = Instant.now().minus(100, ChronoUnit.MINUTES);
    final CqlLibrary existingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .draft(true)
            .version(new Version(1, 0, 0))
            .createdAt(createdTime)
            .createdBy("User1")
            .lastModifiedAt(createdTime)
            .lastModifiedBy("User1")
            .build();
    final String json =
        toJsonString(
            existingLibrary.toBuilder()
                .draft(false)
                .version(new Version(2, 1, 0))
                .cql("library Library1 version '1.0.000'")
                .build());

    when(versionService.createDraft(anyString(), anyString(), anyString(), anyString()))
        .thenThrow(new ResourceNotFoundException("CQL Library", "Library1_ID"));
    mockMvc
        .perform(
            post("/cql-libraries/draft/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
    verify(versionService, times(1))
        .createDraft(
            eq("Library1_ID"), eq("Library1"), eq(ModelType.QI_CORE.getValue()), eq(TEST_USER_ID));
  }

  @Test
  public void testCreateDraftReturnsBadRequestForNameChangeNonUnique() throws Exception {
    final Instant createdTime = Instant.now().minus(100, ChronoUnit.MINUTES);
    final CqlLibrary existingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .draft(true)
            .version(new Version(1, 0, 0))
            .createdAt(createdTime)
            .createdBy("User1")
            .lastModifiedAt(createdTime)
            .lastModifiedBy("User1")
            .build();
    final String json =
        toJsonString(
            existingLibrary.toBuilder()
                .cqlLibraryName("ChangedName")
                .cql("library ChangedName version '1.0.000'")
                .draft(false)
                .version(new Version(2, 1, 0))
                .build());
    System.out.println(json);
    when(versionService.createDraft(anyString(), anyString(), anyString(), anyString()))
        .thenThrow(new DuplicateKeyException("cqlLibraryName", "Library name must be unique."));
    mockMvc
        .perform(
            post("/cql-libraries/draft/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(jsonPath("$.message").value("Library name must be unique."));
    verify(versionService, times(1))
        .createDraft(
            eq("Library1_ID"),
            eq("ChangedName"),
            eq(ModelType.QI_CORE.getValue()),
            eq(TEST_USER_ID));
  }

  @Test
  public void testCreateDraftReturnsCreatedDraft() throws Exception {
    final Instant createdTime = Instant.now().minus(100, ChronoUnit.MINUTES);
    final CqlLibrary draftLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .draft(true)
            .version(new Version(1, 2, 0))
            .createdAt(createdTime)
            .createdBy("User1")
            .lastModifiedAt(createdTime)
            .lastModifiedBy("User1")
            .build();
    final String json =
        toJsonString(
            draftLibrary.toBuilder()
                .draft(false)
                .cql("library Library1 version '1.2.000'")
                .build());

    when(versionService.createDraft(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(draftLibrary);
    mockMvc
        .perform(
            post("/cql-libraries/draft/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isCreated())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(jsonPath("$.id").value("Library1_ID"))
        .andExpect(jsonPath("$.cqlLibraryName").value("Library1"))
        .andExpect(jsonPath("$.draft").value(true))
        .andExpect(jsonPath("$.version").value("1.2.000"));
    verify(versionService, times(1))
        .createDraft(
            eq("Library1_ID"), eq("Library1"), eq(ModelType.QI_CORE.getValue()), eq(TEST_USER_ID));
  }

  @Test
  public void testCreateVersionReturnsNotFound() throws Exception {
    when(versionService.createVersion(anyString(), anyBoolean(), anyString(), anyString()))
        .thenThrow(new ResourceNotFoundException("CQL Library", "Library1_ID"));
    mockMvc
        .perform(
            put("/cql-libraries/version/Library1_ID?isMajor=true")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
    verify(versionService, times(1))
        .createVersion(eq("Library1_ID"), eq(true), eq(TEST_USER_ID), eq("test-okta"));
  }

  @Test
  public void testCreateVersionReturnsForbiddenForPermissionDenied() throws Exception {
    when(versionService.createVersion(anyString(), anyBoolean(), anyString(), anyString()))
        .thenThrow(new PermissionDeniedException("CQL Library", "Library1_ID", "test.user"));
    mockMvc
        .perform(
            put("/cql-libraries/version/Library1_ID?isMajor=false")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
    verify(versionService, times(1))
        .createVersion(eq("Library1_ID"), eq(false), eq(TEST_USER_ID), eq("test-okta"));
  }

  @Test
  public void testCreateVersionReturnsInternalServerError() throws Exception {
    when(versionService.createVersion(anyString(), anyBoolean(), anyString(), anyString()))
        .thenThrow(new InternalServerErrorException("Unable to update version number"));
    mockMvc
        .perform(
            put("/cql-libraries/version/Library1_ID?isMajor=false")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(jsonPath("$.message").value("Unable to update version number"));
    verify(versionService, times(1))
        .createVersion(eq("Library1_ID"), eq(false), eq(TEST_USER_ID), eq("test-okta"));
  }

  @Test
  public void testCreateVersionReturnsInternalServerErrorForCqlElmTranslationErrorException()
      throws Exception {
    when(versionService.createVersion(anyString(), anyBoolean(), anyString(), anyString()))
        .thenThrow(new CqlElmTranslationErrorException("TestLibrary"));
    mockMvc
        .perform(
            put("/cql-libraries/version/Library1_ID?isMajor=false")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(
            jsonPath("$.message")
                .value(
                    "CQL-ELM translator found errors in the CQL for library TestLibrary! Version not created."));
    verify(versionService, times(1))
        .createVersion(eq("Library1_ID"), eq(false), eq(TEST_USER_ID), eq("test-okta"));
  }

  @Test
  public void testCreateVersionReturnsInternalServerErrorForCqlElmTranslationServiceException()
      throws Exception {
    when(versionService.createVersion(anyString(), anyBoolean(), anyString(), anyString()))
        .thenThrow(
            new CqlElmTranslationServiceException(
                "There was an error calling CQL-ELM translation service",
                new RuntimeException("cause")));
    mockMvc
        .perform(
            put("/cql-libraries/version/Library1_ID?isMajor=false")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(
            jsonPath("$.message").value("There was an error calling CQL-ELM translation service"));
    verify(versionService, times(1))
        .createVersion(eq("Library1_ID"), eq(false), eq(TEST_USER_ID), eq("test-okta"));
  }

  @Test
  public void testCreateVersionReturnsCreatedVersion() throws Exception {
    final Instant createdTime = Instant.now().minus(100, ChronoUnit.MINUTES);
    final CqlLibrary versionLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
            .cqlLibraryName("Library1")
            .model(ModelType.QI_CORE.getValue())
            .draft(false)
            .version(new Version(2, 0, 0))
            .createdAt(createdTime)
            .createdBy("User1")
            .lastModifiedAt(createdTime)
            .lastModifiedBy("User1")
            .build();

    when(versionService.createVersion(anyString(), anyBoolean(), anyString(), anyString()))
        .thenReturn(versionLibrary);
    mockMvc
        .perform(
            put("/cql-libraries/version/Library1_ID?isMajor=true")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(jsonPath("$.id").value("Library1_ID"))
        .andExpect(jsonPath("$.cqlLibraryName").value("Library1"))
        .andExpect(jsonPath("$.draft").value(false))
        .andExpect(jsonPath("$.version").value("2.0.000"));
    verify(versionService, times(1))
        .createVersion(eq("Library1_ID"), eq(true), eq(TEST_USER_ID), eq("test-okta"));
  }

  @Test
  public void testGetLibraryCql() throws Exception {
    var cqlLibrary =
        CqlLibrary.builder()
            .cqlLibraryName("TestFHIRHelpers")
            .version(Version.builder().major(1).minor(0).revisionNumber(0).build())
            .cql("Test Cql")
            .model("QI-Core v4.1.1")
            .draft(false)
            .build();
    when(cqlLibraryService.getVersionedCqlLibrary(
            anyString(), any(), any(), anyBoolean(), anyString(), any()))
        .thenReturn(cqlLibrary);

    mockMvc
        .perform(
            get("/cql-libraries/cql?name=TestFHIRHelpers&version=1.0.000&model=QI-Core v4.1.1")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isOk())
        .andExpect(content().string("Test Cql"));

    verify(cqlLibraryService, times(1))
        .getVersionedCqlLibrary(
            "TestFHIRHelpers", "1.0.000", Optional.of("QI-Core v4.1.1"), false, ELM_SEVERITY, null);
  }

  @Test
  public void testGetLibraryCqlReturnsNotFound() throws Exception {
    when(cqlLibraryService.getVersionedCqlLibrary(
            anyString(), any(), any(), anyBoolean(), anyString(), any()))
        .thenThrow(new ResourceNotFoundException("Library", "name", "TestFHIRHelpers"));

    mockMvc
        .perform(
            get("/cql-libraries/cql?name=TestFHIRHelpers&version=1.0.000&model=QI-Core v4.1.1")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.message")
                .value("Could not find resource Library with name: TestFHIRHelpers"));

    verify(cqlLibraryService, times(1))
        .getVersionedCqlLibrary(
            "TestFHIRHelpers", "1.0.000", Optional.of("QI-Core v4.1.1"), false, ELM_SEVERITY, null);
  }

  @Test
  public void testGetLibraryCqlReturnsConflict() throws Exception {
    when(cqlLibraryService.getVersionedCqlLibrary(
            anyString(), any(), any(), anyBoolean(), anyString(), any()))
        .thenThrow(
            new GeneralConflictException(
                "Multiple versioned libraries were found. "
                    + "Please provide additional filters "
                    + "to narrow down the results to a single library."));

    mockMvc
        .perform(
            get("/cql-libraries/cql?name=TestFHIRHelpers&version=1.0.000&model=QI-Core v4.1.1")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Multiple versioned libraries were found. Please provide additional filters to narrow down the results to a single library."));

    verify(cqlLibraryService, times(1))
        .getVersionedCqlLibrary(
            "TestFHIRHelpers", "1.0.000", Optional.of("QI-Core v4.1.1"), false, ELM_SEVERITY, null);
  }

  @Test
  public void testGetVersionedCqlLibrary() throws Exception {
    var cqlLibrary =
        CqlLibrary.builder()
            .cqlLibraryName("TestFHIRHelpers")
            .version(Version.builder().major(1).minor(0).revisionNumber(0).build())
            .model("QI-Core v4.1.1")
            .draft(false)
            .build();
    when(cqlLibraryService.getVersionedCqlLibrary(
            anyString(), any(), any(), anyBoolean(), anyString(), any()))
        .thenReturn(cqlLibrary);

    mockMvc
        .perform(
            get("/cql-libraries/versioned?name=TestFHIRHelpers&version=1.0.000&model=QI-Core v4.1.1")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isOk());

    verify(cqlLibraryService, times(1))
        .getVersionedCqlLibrary(
            "TestFHIRHelpers",
            "1.0.000",
            Optional.of("QI-Core v4.1.1"),
            true,
            ELM_SEVERITY,
            "test-okta");
  }

  @Test
  public void testGetVersionedCqlLibraryReturnsNotFound() throws Exception {
    when(cqlLibraryService.getVersionedCqlLibrary(
            anyString(), any(), any(), anyBoolean(), anyString(), any()))
        .thenThrow(new ResourceNotFoundException("Library", "name", "TestFHIRHelpers"));

    mockMvc
        .perform(
            get("/cql-libraries/versioned?name=TestFHIRHelpers&version=1.0.000&model=QI-Core v4.1.1")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isNotFound())
        .andExpect(
            jsonPath("$.message")
                .value("Could not find resource Library with name: TestFHIRHelpers"));

    verify(cqlLibraryService, times(1))
        .getVersionedCqlLibrary(
            "TestFHIRHelpers",
            "1.0.000",
            Optional.of("QI-Core v4.1.1"),
            true,
            ELM_SEVERITY,
            "test-okta");
  }

  @Test
  public void testGetVersionedCqlLibraryReturnsConflict() throws Exception {
    when(cqlLibraryService.getVersionedCqlLibrary(
            anyString(), any(), any(), anyBoolean(), anyString(), any()))
        .thenThrow(
            new GeneralConflictException(
                "Multiple versioned libraries were found. "
                    + "Please provide additional filters "
                    + "to narrow down the results to a single library."));

    mockMvc
        .perform(
            get("/cql-libraries/versioned?name=TestFHIRHelpers&version=1.0.000&model=QI-Core v4.1.1")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Multiple versioned libraries were found. Please provide additional filters to narrow down the results to a single library."));

    verify(cqlLibraryService, times(1))
        .getVersionedCqlLibrary(
            "TestFHIRHelpers",
            "1.0.000",
            Optional.of("QI-Core v4.1.1"),
            true,
            ELM_SEVERITY,
            "test-okta");
  }

  @Test
  public void testHardDeleteDraftLibraryForNonOwnerReturnsForbidden() throws Exception {
    String libraryId = "f225481c-921e-4015-9e14-e5046bfac9ff";

    when(cqlLibraryService.deleteDraftLibrary(anyString(), anyString()))
        .thenThrow(new PermissionDeniedException("CQL Library", libraryId, TEST_USER_ID));

    mockMvc
        .perform(
            delete("/cql-libraries/" + libraryId)
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isForbidden());

    verify(cqlLibraryService, times(1)).deleteDraftLibrary(eq(libraryId), anyString());
  }

  @Test
  public void testHardDeleteDraftLibraryForMissingLibraryReturnsNotFound() throws Exception {
    String libraryId = "f225481c-921e-4015-9e14-e5046bfac9ff";

    when(cqlLibraryService.deleteDraftLibrary(anyString(), anyString()))
        .thenThrow(new ResourceNotFoundException("CQL Library", libraryId));

    mockMvc
        .perform(
            delete("/cql-libraries/" + libraryId)
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isNotFound());

    verify(cqlLibraryService, times(1)).deleteDraftLibrary(eq(libraryId), eq(TEST_USER_ID));
  }

  @Test
  public void testHardDeleteDraftLibraryForNonDraftReturnsConflict() throws Exception {
    String libraryId = "f225481c-921e-4015-9e14-e5046bfac9ff";

    when(cqlLibraryService.deleteDraftLibrary(anyString(), anyString()))
        .thenThrow(
            new GeneralConflictException(
                String.format(
                    "Could not update resource %s with id: %s. Resource is not a Draft.",
                    "CQL Library", libraryId)));

    mockMvc
        .perform(
            delete("/cql-libraries/" + libraryId)
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isConflict());

    verify(cqlLibraryService, times(1)).deleteDraftLibrary(eq(libraryId), eq(TEST_USER_ID));
  }

  @Test
  public void testHardDeleteDraftLibraryForDraftReturnsDeletedLibrary() throws Exception {
    String libraryId = "f225481c-921e-4015-9e14-e5046bfac9ff";

    when(cqlLibraryService.deleteDraftLibrary(anyString(), anyString()))
        .thenReturn(
            CqlLibrary.builder().cqlLibraryName("WillBeDeleted").draft(true).id(libraryId).build());

    mockMvc
        .perform(
            delete("/cql-libraries/" + libraryId)
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta")
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(jsonPath("$.id").value(libraryId))
        .andExpect(jsonPath("$.draft").value(true))
        .andExpect(jsonPath("$.cqlLibraryName").value("WillBeDeleted"))
        .andExpect(status().isOk());

    verify(cqlLibraryService, times(1)).deleteDraftLibrary(eq(libraryId), eq(TEST_USER_ID));
  }

  @Test
  void testGetLibraryUsage() throws Exception {
    String libraryName = "Helper";
    String owner = "john";
    LibraryUsage libraryUsage = LibraryUsage.builder().name(libraryName).owner(owner).build();
    when(cqlLibraryService.findLibraryUsage(anyString())).thenReturn(List.of(libraryUsage));
    MvcResult result =
        mockMvc
            .perform(
                get("/cql-libraries/usage?libraryName=Test").with(user(TEST_USER_ID)).with(csrf()))
            .andReturn();
    assertEquals(result.getResponse().getStatus(), HttpStatus.OK.value());
    assertEquals(
        result.getResponse().getContentAsString(),
        "[{\"name\":\"Helper\",\"version\":null,\"owner\":\"john\"}]");
  }

  @Test
  void testGetLibrariesByNameAndModel() throws Exception {
    LibraryListDTO l1 =
        LibraryListDTO.builder()
            .cqlLibraryName("Test")
            .version(Version.parse("0.1.000"))
            .model("QDM 5.6")
            .build();

    when(cqlLibraryService.findLibrariesByNameAndModel(anyString(), anyString()))
        .thenReturn(List.of(l1));
    MvcResult result =
        mockMvc
            .perform(
                get("/cql-libraries/all-versioned?libraryName=test&model=QDM")
                    .with(user(TEST_USER_ID))
                    .with(csrf()))
            .andReturn();
    assertThat(result.getResponse().getStatus(), is(equalTo(HttpStatus.OK.value())));
    assertThat(result.getResponse().getContentAsString(), containsString(l1.getCqlLibraryName()));
    assertThat(result.getResponse().getContentAsString(), containsString(l1.getModel()));
    assertThat(
        result.getResponse().getContentAsString(), containsString(l1.getVersion().toString()));
  }

  @Test
  void testGetLibrarySetBySetId() throws Exception {
    String librarySetId = "1-1-1-1";
    String owner = "John";
    LibrarySet librarySet = LibrarySet.builder().librarySetId(librarySetId).owner(owner).build();
    CqlLibrary library =
        CqlLibrary.builder()
            .cqlLibraryName("Lib1")
            .librarySetId(librarySetId)
            .version(Version.parse("0.1.000"))
            .build();
    LibrarySetDTO librarySetDTO =
        LibrarySetDTO.builder().librarySet(librarySet).libraries(List.of(library)).build();
    when(cqlLibraryService.getLibrarySetBySetId(anyString())).thenReturn(librarySetDTO);
    MvcResult result =
        mockMvc
            .perform(
                get("/cql-libraries/library-set/" + librarySetId)
                    .with(user(TEST_USER_ID))
                    .with(csrf()))
            .andReturn();
    assertThat(
        result.getResponse().getContentAsString(),
        containsString(librarySetDTO.getLibrarySet().getLibrarySetId()));
    assertThat(
        result.getResponse().getContentAsString(),
        containsString(librarySetDTO.getLibrarySet().getOwner()));
    assertThat(
        result.getResponse().getContentAsString(),
        containsString(librarySetDTO.getLibraries().get(0).getCqlLibraryName()));
  }

  @Test
  void testGetLibrariesByLibrarySetId() throws Exception {
    String librarySetId = "1-1-1-1";
    String owner = "John";
    LibrarySet librarySet = LibrarySet.builder().librarySetId(librarySetId).owner(owner).build();
    LibraryListDTO listDTO =
        LibraryListDTO.builder()
            .cqlLibraryName("Test")
            .version(Version.parse("0.1.000"))
            .model(MODEL)
            .librarySetId(librarySetId)
            .librarySet(librarySet)
            .build();

    LibrarySearchCriteria librarySearchCriteria = LibrarySearchCriteria.builder().build();
    ObjectMapper objectMapper = new ObjectMapper(); // Jackson mapper
    String jsonBody = objectMapper.writeValueAsString(librarySearchCriteria);

    when(cqlLibraryService.getLibrariesByLibrarySetId(
            anyString(), anyBoolean(), eq(librarySearchCriteria)))
        .thenReturn(List.of(listDTO));
    MvcResult result =
        mockMvc
            .perform(
                put("/cql-libraries/byLibrarySetId?librarySetId=" + librarySetId)
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonBody))
            .andReturn();
    assertThat(
        result.getResponse().getContentAsString(),
        containsString(listDTO.getLibrarySet().getLibrarySetId()));
    assertThat(
        result.getResponse().getContentAsString(),
        containsString(listDTO.getLibrarySet().getOwner()));
    assertThat(
        result.getResponse().getContentAsString(), containsString(listDTO.getCqlLibraryName()));
    assertThat(
        result.getResponse().getContentAsString(), containsString(listDTO.getVersion().toString()));
    assertThat(result.getResponse().getContentAsString(), containsString(listDTO.getModel()));
  }

  @Test
  public void testGetSharedLibraries() throws Exception {
    String libraryId1 = "libraryId1";
    String libraryId2 = "libraryId2";

    Instant fixedInstant = Instant.parse("2025-03-17T10:00:00Z");
    ZoneId utc = ZoneId.of("UTC");
    Clock fixedClock = Clock.fixed(fixedInstant, utc);

    List<String> libraryIds = List.of(libraryId1, libraryId2);
    SharedUser sharedUser1 =
        SharedUser.builder()
            .userId("userId1")
            .displayName("John Doe (userId1)")
            .performedAt(fixedClock.instant())
            .build();
    SharedUser sharedUser2 =
        SharedUser.builder()
            .userId("userId2")
            .displayName("Jane Doe (userId2)")
            .performedAt(fixedClock.instant())
            .build();

    Map<String, List<SharedUser>> sharedLibraries = new HashMap<>();
    sharedLibraries.put(libraryId1, List.of(sharedUser1));
    sharedLibraries.put(libraryId2, List.of(sharedUser1, sharedUser2));

    doReturn(sharedLibraries)
        .when(cqlLibraryService)
        .getSharedLibraries(eq(libraryIds), anyString());

    mockMvc
        .perform(
            get(String.format("/cql-libraries/shared?libraryIds=%s", String.join(",", libraryIds)))
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    "{\"libraryId1\":[{\"userId\":\"userId1\",\"displayName\":\"John Doe (userId1)\",\"performedAt\":\"2025-03-17T10:00:00Z\"}],\"libraryId2\":[{\"userId\":\"userId1\",\"displayName\":\"John Doe (userId1)\",\"performedAt\":\"2025-03-17T10:00:00Z\"},{\"userId\":\"userId2\",\"displayName\":\"Jane Doe (userId2)\",\"performedAt\":\"2025-03-17T10:00:00Z\"}]}"));

    verify(cqlLibraryService, times(1)).getSharedLibraries(eq(libraryIds), anyString());
  }

  @Test
  public void testUpdateSharedLibraries() throws Exception {
    AclSpecification aclSpecification1 = new AclSpecification();
    aclSpecification1.setUserId("userId1");
    aclSpecification1.setRoles(Set.of(RoleEnum.SHARED_WITH));

    AclSpecification aclSpecification2 = new AclSpecification();
    aclSpecification2.setUserId("userId2");
    aclSpecification2.setRoles(Set.of(RoleEnum.SHARED_WITH));

    Map<String, List<AclSpecification>> updatedSharedLibraries = new HashMap<>();
    updatedSharedLibraries.put("libraryId1", List.of(aclSpecification1));
    updatedSharedLibraries.put("libraryId2", List.of(aclSpecification1, aclSpecification2));

    doReturn(updatedSharedLibraries)
        .when(cqlLibraryService)
        .shareLibraries(any(), anyString(), anyString());

    MvcResult result =
        mockMvc
            .perform(
                put("/cql-libraries/share")
                    .with(user(TEST_USER_ID))
                    .header("Authorization", "test-okta")
                    .with(csrf())
                    .content("{\"libraryId1\": [\"userId1\"],\"libraryId2\": [\"userId1\"]}")
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();
    verify(cqlLibraryService, times(1)).shareLibraries(any(), anyString(), anyString());
    assertEquals(
        result.getResponse().getContentAsString(),
        "{\"libraryId1\":[{\"userId\":\"userId1\",\"roles\":[\"SHARED_WITH\"]}],\"libraryId2\":[{\"userId\":\"userId1\",\"roles\":[\"SHARED_WITH\"]},{\"userId\":\"userId2\",\"roles\":[\"SHARED_WITH\"]}]}");
  }

  @Test
  public void testGetRecentLibrariesByLibrarySetId() throws Exception {
    CqlLibrary library1 = new CqlLibrary();
    library1.setId("L1");
    library1.setCqlLibraryName("Library 1");

    CqlLibrary library2 = new CqlLibrary();
    library2.setId("L2");
    library2.setCqlLibraryName("Library 2");

    List<CqlLibrary> recentLibraries = List.of(library1, library2);

    when(librarySetService.getRecentLibrariesByLibrarySetId(eq(List.of("set1", "set2"))))
        .thenReturn(recentLibraries);

    mockMvc
        .perform(
            get("/cql-libraries/recentsByLibrarySetId")
                .with(user(TEST_USER_ID))
                .queryParam("librarySetIds", "set1", "set2")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].id").value("L1"))
        .andExpect(jsonPath("$[0].cqlLibraryName").value("Library 1"))
        .andExpect(jsonPath("$[1].id").value("L2"))
        .andExpect(jsonPath("$[1].cqlLibraryName").value("Library 2"));

    verify(librarySetService, times(1))
        .getRecentLibrariesByLibrarySetId(eq(List.of("set1", "set2")));
  }

  @Test
  public void testUnshareLibraries() throws Exception {
    AclSpecification aclSpecification2 = new AclSpecification();
    aclSpecification2.setUserId("userId2");
    aclSpecification2.setRoles(Set.of(RoleEnum.SHARED_WITH));

    Map<String, List<AclSpecification>> libraryIdToAclSpecification = new HashMap<>();
    libraryIdToAclSpecification.put("libraryId2", List.of(aclSpecification2));

    doReturn(libraryIdToAclSpecification)
        .when(cqlLibraryService)
        .unshareLibraries(any(), anyString(), anyString());

    MvcResult result =
        mockMvc
            .perform(
                put("/cql-libraries/unshare")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("Authorization", "test-okta")
                    .content("{\"libraryId1\": [\"userId1\"],\"libraryId2\": [\"userId1\"]}")
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();
    verify(cqlLibraryService, times(1)).unshareLibraries(any(), anyString(), anyString());
    assertEquals(
        result.getResponse().getContentAsString(),
        "{\"libraryId2\":[{\"userId\":\"userId2\",\"roles\":[\"SHARED_WITH\"]}]}");
  }

  @Test
  public void testTransferLibraries() throws Exception {
    String libraryId = "f225481c-921e-4015-9e14-e5046bfac9ff";

    when(cqlLibraryLockService.findByCqlLibraryId(libraryId)).thenReturn(null);

    doReturn(Collections.emptyList())
        .when(cqlLibraryService)
        .transferLibraries(
            eq(List.of(libraryId)), eq("testuser"), eq(true), eq(TEST_USER_ID), eq("test-okta"));

    mockMvc
        .perform(
            put("/cql-libraries/transfer")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("harpId", "testUser")
                .header("Authorization", "test-okta")
                .queryParam("retainShareAccess", "true")
                .content(new ObjectMapper().writeValueAsString(List.of(libraryId)))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk());

    verify(cqlLibraryLockService, times(1)).findByCqlLibraryId(libraryId);
    verify(cqlLibraryService, times(1))
        .transferLibraries(
            eq(List.of(libraryId)), eq("testuser"), eq(true), eq(TEST_USER_ID), eq("test-okta"));
  }

  @Test
  public void testTransferLibrariesPartialResults() throws Exception {
    String libraryId = "f225481c-921e-4015-9e14-e5046bfac9ff";

    when(cqlLibraryLockService.findByCqlLibraryId(anyString())).thenReturn(null);

    doReturn(List.of("1"))
        .when(cqlLibraryService)
        .transferLibraries(
            eq(List.of(libraryId, "1")),
            eq("testuser"),
            eq(false),
            eq(TEST_USER_ID),
            eq("test-okta"));

    mockMvc
        .perform(
            put("/cql-libraries/transfer")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("harpId", "testUser")
                .header("Authorization", "test-okta")
                .queryParam("retainShareAccess", "false")
                .content(new ObjectMapper().writeValueAsString(List.of(libraryId, "1")))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isMultiStatus());

    verify(cqlLibraryService, times(1))
        .transferLibraries(
            eq(List.of(libraryId, "1")),
            eq("testuser"),
            eq(false),
            eq(TEST_USER_ID),
            eq("test-okta"));
  }

  @Test
  public void testTransferLibrariesNullLibraryIds() throws Exception {
    String libraryId = "f225481c-921e-4015-9e14-e5046bfac9ff";

    mockMvc
        .perform(
            put("/cql-libraries/transfer")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("harpId", "testUser")
                .header("Authorization", "test-okta")
                .queryParam("retainShareAccess", "true")
                .content(new ObjectMapper().writeValueAsString(Collections.emptyList()))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isBadRequest());

    verify(cqlLibraryService, times(0))
        .transferLibraries(
            eq(List.of(libraryId)), eq("testUser"), eq(true), eq(TEST_USER_ID), anyString());
  }

  @Test
  public void testTransferLibrariesLockedLibrary() throws Exception {
    String libraryId = "f225481c-921e-4015-9e14-e5046bfac9ff";

    when(cqlLibraryLockService.findByCqlLibraryId(libraryId))
        .thenReturn(CqlLibraryLock.builder().cqlLibraryId(libraryId).lockedBy("someUser").build());

    mockMvc
        .perform(
            put("/cql-libraries/transfer")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("harpId", "testUser")
                .header("Authorization", "test-okta")
                .queryParam("retainShareAccess", "true")
                .content(new ObjectMapper().writeValueAsString(List.of(libraryId)))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isMultiStatus());

    verify(cqlLibraryLockService, times(1)).findByCqlLibraryId(libraryId);
    verify(cqlLibraryService, never())
        .transferLibraries(anyList(), anyString(), anyBoolean(), anyString(), anyString());
  }

  @Test
  public void testTransferLibrariesLockedByCurrentUserAllowsTransfer() throws Exception {
    String libraryId = "f225481c-921e-4015-9e14-e5046bfac9ff";

    when(cqlLibraryLockService.findByCqlLibraryId(libraryId))
        .thenReturn(
            CqlLibraryLock.builder().cqlLibraryId(libraryId).lockedBy(TEST_USER_ID).build());

    doReturn(Collections.emptyList())
        .when(cqlLibraryService)
        .transferLibraries(
            eq(List.of(libraryId)), eq("testuser"), eq(true), eq(TEST_USER_ID), eq("test-okta"));

    mockMvc
        .perform(
            put("/cql-libraries/transfer")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .header("harpId", "testUser")
                .header("Authorization", "test-okta")
                .queryParam("retainShareAccess", "true")
                .content(new ObjectMapper().writeValueAsString(List.of(libraryId)))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk());

    verify(cqlLibraryLockService, times(1)).findByCqlLibraryId(libraryId);
    verify(cqlLibraryService, times(1))
        .transferLibraries(
            eq(List.of(libraryId)), eq("testuser"), eq(true), eq(TEST_USER_ID), eq("test-okta"));
  }

  @Test
  public void testGetLibraryCqlReturns200() throws Exception {
    when(cqlLibraryService.getVersionedCqlLibrary(
            eq("TestFHIRHelpers"),
            eq("1.0.000"),
            eq(Optional.of("QI-Core v4.1.1")),
            anyBoolean(),
            anyString(),
            any()))
        .thenReturn(
            CqlLibrary.builder()
                .cqlLibraryName("TestFHIRHelpers")
                .version(Version.builder().major(1).minor(0).revisionNumber(0).build())
                .cql("Test Cql")
                .model("QI-Core v4.1.1")
                .draft(false)
                .build());

    MvcResult result =
        mockMvc
            .perform(
                get("/cql-libraries/cql?name=TestFHIRHelpers&version=1.0.000&model=QI-Core v4.1.1")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("Authorization", "test-okta")
                    .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andExpect(content().string("Test Cql"))
            .andReturn();

    assertNotNull(result.getResponse().getContentAsString(), "Response content should not be null");
  }

  @Test
  public void testCompareLibrariesReturnsComparisonResult() throws Exception {
    String oldLibraryId = "oldLibraryId";
    String newLibraryId = "newLibraryId";

    // Mocking old and new libraries
    CqlLibrary oldLibrary =
        CqlLibrary.builder()
            .id(oldLibraryId)
            .cqlLibraryName("OldLibrary")
            .cql("Old content 1")
            .build();

    CqlLibrary newLibrary =
        CqlLibrary.builder()
            .id(newLibraryId)
            .cqlLibraryName("NewLibrary")
            .cql("New content 1")
            .build();

    // Mocking the comparison result
    CqlFileComparisonDTO comparison =
        CqlFileComparisonDTO.builder()
            .oldFileName("OldLibrary.cql")
            .newFileName("NewLibrary.cql")
            .oldText("Old content 1")
            .newText("New content 1")
            .build();

    // Mocking service calls
    when(cqlLibraryService.findCqlLibraryById(eq(oldLibraryId), anyString()))
        .thenReturn(oldLibrary);
    when(cqlLibraryService.findCqlLibraryById(eq(newLibraryId), anyString()))
        .thenReturn(newLibrary);
    when(cqlDifferentiatorService.compareLibraries(anyMap(), anyMap(), eq(true)))
        .thenReturn(List.of(comparison));

    // Perform the request
    mockMvc
        .perform(
            get("/cql-libraries/{oldLibraryId}/compare/{newLibraryId}", oldLibraryId, newLibraryId)
                .with(user(TEST_USER_ID))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(jsonPath("$.oldLibraryId").value(oldLibraryId))
        .andExpect(jsonPath("$.newLibraryId").value(newLibraryId))
        .andExpect(jsonPath("$.comparisons[0].oldFileName").value("OldLibrary.cql"))
        .andExpect(jsonPath("$.comparisons[0].newFileName").value("NewLibrary.cql"))
        .andExpect(jsonPath("$.comparisons[0].oldText").value("Old content 1"))
        .andExpect(jsonPath("$.comparisons[0].newText").value("New content 1"));

    // Verify interactions
    verify(cqlLibraryService, times(1)).findCqlLibraryById(eq(oldLibraryId), anyString());
    verify(cqlLibraryService, times(1)).findCqlLibraryById(eq(newLibraryId), anyString());
    verify(cqlDifferentiatorService, times(1)).compareLibraries(anyMap(), anyMap(), eq(true));
  }
}
