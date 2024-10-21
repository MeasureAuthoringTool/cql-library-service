package gov.cms.madie.cqllibraryservice.controllers;

import gov.cms.madie.cqllibraryservice.config.security.SecurityConfig;
import gov.cms.madie.cqllibraryservice.dto.LibrarySetDTO;
import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.exceptions.GeneralConflictException;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import gov.cms.madie.cqllibraryservice.exceptions.CqlElmTranslationErrorException;
import gov.cms.madie.cqllibraryservice.exceptions.CqlElmTranslationServiceException;
import gov.cms.madie.cqllibraryservice.exceptions.DuplicateKeyException;
import gov.cms.madie.cqllibraryservice.exceptions.InternalServerErrorException;
import gov.cms.madie.cqllibraryservice.exceptions.PermissionDeniedException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotDraftableException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.services.ActionLogService;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.dto.LibraryUsage;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.CqlLibraryDraft;
import gov.cms.madie.models.common.Version;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.cqllibraryservice.services.CqlLibraryService;
import gov.cms.madie.cqllibraryservice.services.LibrarySetService;
import gov.cms.madie.cqllibraryservice.services.VersionService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import gov.cms.madie.models.library.LibrarySet;

import org.bson.types.ObjectId;
import org.hamcrest.CustomMatcher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@WebMvcTest({CqlLibraryController.class})
@Import(SecurityConfig.class)
public class CqlLibraryControllerMvcTest {

  private static final String TEST_USER_ID = "test-okta-user-id-123";
  private static final String TEST_LIBRARYSET_ID = "test-okta-user-id-321";
  private static final String TEST_API_KEY_HEADER = "api-key";
  private static final String TEST_API_KEY_HEADER_VALUE = "0a51991c";
  private static final String MODEL = ModelType.QI_CORE.toString();

  @MockBean private CqlLibraryRepository repository;
  @MockBean private VersionService versionService;
  @MockBean private CqlLibraryService cqlLibraryService;
  @MockBean private LibrarySetService librarySetService;

  @MockBean ActionLogService actionLogService;

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
    when(repository.existsByCqlLibraryName(anyString())).thenReturn(false);
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
    verifyNoInteractions(repository);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForEmptyCqlLibraryName() throws Exception {
    String json = toJsonString(CqlLibrary.builder().cqlLibraryName("").model(MODEL).build());
    when(repository.existsByCqlLibraryName(anyString())).thenReturn(false);
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
    verifyNoInteractions(repository);
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
    when(repository.existsByCqlLibraryName(anyString())).thenReturn(false);
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
    verifyNoInteractions(repository);
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
    when(repository.existsByCqlLibraryName(anyString())).thenReturn(false);
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
    verifyNoInteractions(repository);
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
    when(repository.existsByCqlLibraryName(anyString())).thenReturn(false);
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
    verifyNoInteractions(repository);
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
    when(repository.existsByCqlLibraryName(anyString())).thenReturn(false);
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
    verifyNoInteractions(repository);
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
    when(repository.existsByCqlLibraryName(anyString())).thenReturn(false);
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
    verifyNoInteractions(repository);
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
    verifyNoMoreInteractions(repository);
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
    verifyNoInteractions(repository);
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
    verifyNoInteractions(repository);
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
    when(repository.save(any(CqlLibrary.class)))
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
    verify(repository, times(1)).save(any(CqlLibrary.class));

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(), actionTypeArgumentCaptor.capture(), anyString());
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
    when(repository.save(any(CqlLibrary.class)))
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
    verify(repository, times(1)).save(any(CqlLibrary.class));

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(), actionTypeArgumentCaptor.capture(), anyString());
    assertThat(targetIdArgumentCaptor.getValue(), is(notNullValue()));
    assertThat(actionTypeArgumentCaptor.getValue(), is(equalTo(ActionType.CREATED)));
  }

  @Test
  public void testGetCqlLibraryReturns404() throws Exception {
    doThrow(new ResourceNotFoundException("CQL Library", "Library1_ID"))
        .when(cqlLibraryService)
        .findCqlLibraryById(anyString());
    mockMvc
        .perform(get("/cql-libraries/Libary1_ID").with(user(TEST_USER_ID)).with(csrf()))
        .andExpect(status().isNotFound());
    verify(cqlLibraryService, times(1)).findCqlLibraryById(anyString());
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
    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingLibrary);
    mockMvc
        .perform(get("/cql-libraries/Libary1_ID").with(user(TEST_USER_ID)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cqlLibraryName").value(existingLibrary.getCqlLibraryName()))
        .andExpect(jsonPath("$.id").value(existingLibrary.getId()))
        .andExpect(jsonPath("$.createdBy").value("User1"))
        .andExpect(jsonPath("$.lastModifiedBy").value("User1"))
        .andExpect(jsonPath("$.createdAt").value(is(equalTo(createdTime.toString()))))
        .andExpect(jsonPath("$.lastModifiedAt").value(is(equalTo(createdTime.toString()))));
    verify(cqlLibraryService, times(1)).findCqlLibraryById(anyString());
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
    verifyNoInteractions(repository);
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
    verifyNoInteractions(repository);
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
    verifyNoInteractions(repository);
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
    verifyNoInteractions(repository);
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
    verifyNoInteractions(repository);
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
    verifyNoInteractions(repository);
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
    verifyNoInteractions(repository);
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
    verifyNoInteractions(repository);
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
        .findCqlLibraryById(anyString());
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
    verify(cqlLibraryService, times(1)).findCqlLibraryById(anyString());
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
    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingLibrary);
    when(cqlLibraryService.isCqlLibraryNameChanged(any(CqlLibrary.class), any(CqlLibrary.class)))
        .thenReturn(true);
    doThrow(new DuplicateKeyException("cqlLibraryName", "Library name must be unique."))
        .when(cqlLibraryService)
        .checkDuplicateCqlLibraryName(anyString());

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
    verify(cqlLibraryService, times(1)).findCqlLibraryById(anyString());
    verify(cqlLibraryService, times(1))
        .isCqlLibraryNameChanged(any(CqlLibrary.class), any(CqlLibrary.class));
  }

  @Test
  public void testUpdateCqlLibraryReturns409ForUpdateAttemptOnVersionedLibrary() throws Exception {
    final Instant createdTime = Instant.now().minus(100, ChronoUnit.MINUTES);
    final CqlLibrary existingLibrary =
        CqlLibrary.builder()
            .id("Library1_ID")
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
        existingLibrary.toBuilder().id("Library1_ID").cqlLibraryName("NewName").build();
    String json = toJsonString(updatingLibrary);
    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingLibrary);

    mockMvc
        .perform(
            put("/cql-libraries/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message")
                .value(
                    "Could not update resource CQL Library with id: Library1_ID. Resource is not a Draft."));
    verify(cqlLibraryService, times(1)).findCqlLibraryById(anyString());
  }

  @Test
  public void testUpdateCqlLibraryReturnsPermissionDeniedExceptionForNonOwner() throws Exception {
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
                LibrarySet.builder().librarySetId("testLibrarySetId").owner(TEST_USER_ID).build())
            .build();
    final CqlLibrary updatingLibrary =
        existingLibrary.toBuilder().id("Library1_ID").cqlLibraryName("NewName").build();
    String json = toJsonString(updatingLibrary);
    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingLibrary);

    mockMvc
        .perform(
            put("/cql-libraries/Library1_ID")
                .with(user("random.user"))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isForbidden());
    verify(cqlLibraryService, times(1)).findCqlLibraryById(anyString());
  }

  @Test
  public void testUpdateCqlLibraryReturns200ForSuccessfulUpdate() throws Exception {
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
        existingLibrary.toBuilder()
            .id("Library1_ID")
            .cqlLibraryName("NewName")
            .cql("library testCql version '2.1.000'")
            .librarySetId(TEST_LIBRARYSET_ID)
            .build();
    String json = toJsonString(updatingLibrary);
    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(existingLibrary);
    when(cqlLibraryService.isCqlLibraryNameChanged(any(CqlLibrary.class), any(CqlLibrary.class)))
        .thenReturn(false);
    when(repository.save(any(CqlLibrary.class))).thenReturn(updatingLibrary);
    mockMvc
        .perform(
            put("/cql-libraries/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(content().json(toJsonString(updatingLibrary)));
    verify(cqlLibraryService, times(1)).findCqlLibraryById(anyString());
    verify(repository, times(1)).save(cqlLibraryArgumentCaptor.capture());
    CqlLibrary savedValue = cqlLibraryArgumentCaptor.getValue();
    assertThat(savedValue, is(notNullValue()));
    assertThat(savedValue.getId(), is(equalTo("Library1_ID")));
    assertThat(savedValue.getCqlLibraryName(), is(equalTo("NewName")));
    assertThat(savedValue.getCql(), is(equalTo("library testCql version '2.1.000'")));
    assertThat(savedValue.getCreatedAt(), is(equalTo(createdTime)));
    assertThat(savedValue.getCreatedBy(), is(equalTo(TEST_USER_ID)));
    assertThat(savedValue.getLastModifiedAt(), is(notNullValue()));
    assertThat(savedValue.getLastModifiedAt().isAfter(createdTime), is(true));
    assertThat(savedValue.getLastModifiedBy(), is(equalTo(TEST_USER_ID)));
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
    verifyNoInteractions(repository);
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
    verifyNoInteractions(repository);
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
    verifyNoInteractions(repository);
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

    when(versionService.createDraft(anyString(), anyString(), anyString()))
        .thenThrow(new ResourceNotDraftableException("CQL Library"));
    mockMvc
        .perform(
            post("/cql-libraries/draft/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
    verify(versionService, times(1))
        .createDraft(eq("Library1_ID"), eq("Library1"), eq(TEST_USER_ID));
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

    when(versionService.createDraft(anyString(), anyString(), anyString()))
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
        .createDraft(eq("Library1_ID"), eq("Library1"), eq(TEST_USER_ID));
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

    when(versionService.createDraft(anyString(), anyString(), anyString()))
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
        .createDraft(eq("Library1_ID"), eq("ChangedName"), eq(TEST_USER_ID));
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

    when(versionService.createDraft(anyString(), anyString(), anyString()))
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
        .createDraft(eq("Library1_ID"), eq("Library1"), eq(TEST_USER_ID));
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
  public void testAdminMultipleMeasuresGetSharedWith() throws Exception {
    CqlLibrary lib1 = CqlLibrary.builder().id("12345").build();
    CqlLibrary lib2 = CqlLibrary.builder().id("6789").build();
    AclSpecification acl1 = new AclSpecification();
    acl1.setUserId("raoulduke");
    acl1.setRoles(List.of(RoleEnum.SHARED_WITH));

    List<AclSpecification> acls = List.of(acl1);
    LibrarySet librarySet = LibrarySet.builder().acls(acls).build();
    lib1.setLibrarySet(librarySet);
    lib2.setLibrarySet(librarySet);
    when(cqlLibraryService.findCqlLibraryById(eq("12345"))).thenReturn(lib1);
    when(cqlLibraryService.findCqlLibraryById(eq("6789"))).thenReturn(lib2);

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/cql-libraries/sharedWith?measureids=12345,6789")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(TEST_API_KEY_HEADER, TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].libraryId", equalTo("12345")))
        .andExpect(jsonPath("$[1].libraryId", equalTo("6789")))
        .andExpect(jsonPath("$[0].sharedWith.[0].userId", equalTo("raoulduke")));
  }

  @Test
  public void testAdminMeasureGetSharedWith() throws Exception {
    CqlLibrary testLibrary = CqlLibrary.builder().id("12345").build();
    AclSpecification acl1 = new AclSpecification();
    acl1.setUserId("raoulduke");
    acl1.setRoles(List.of(RoleEnum.SHARED_WITH));

    List<AclSpecification> acls = List.of(acl1);
    LibrarySet librarySet = LibrarySet.builder().acls(acls).build();
    testLibrary.setLibrarySet(librarySet);
    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(testLibrary);

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/cql-libraries/sharedWith?measureids=12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(TEST_API_KEY_HEADER, TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].libraryId", equalTo("12345")))
        .andExpect(jsonPath("$[0].sharedWith.[0].userId", equalTo("raoulduke")));
  }

  @Test
  public void testAdminMeasureGetSharedWithNoone() throws Exception {
    CqlLibrary testLibrary = CqlLibrary.builder().id("12345").build();

    LibrarySet librarySet = LibrarySet.builder().acls(null).build();
    testLibrary.setLibrarySet(librarySet);
    when(cqlLibraryService.findCqlLibraryById(anyString())).thenReturn(testLibrary);

    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/cql-libraries/sharedWith?measureids=12345")
                .with(csrf())
                .with(user(TEST_USER_ID))
                .header(TEST_API_KEY_HEADER, TEST_API_KEY_HEADER_VALUE)
                .header("Authorization", "test-okta"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].libraryId", equalTo("12345")))
        .andExpect(jsonPath("$[0].sharedWith", equalTo(null)));
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
    when(cqlLibraryService.getVersionedCqlLibrary(anyString(), any(), any(), anyBoolean(), any()))
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
            "TestFHIRHelpers", "1.0.000", Optional.of("QI-Core v4.1.1"), false, null);
  }

  @Test
  public void testGetLibraryCqlReturnsNotFound() throws Exception {
    when(cqlLibraryService.getVersionedCqlLibrary(anyString(), any(), any(), anyBoolean(), any()))
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
            "TestFHIRHelpers", "1.0.000", Optional.of("QI-Core v4.1.1"), false, null);
  }

  @Test
  public void testGetLibraryCqlReturnsConflict() throws Exception {
    when(cqlLibraryService.getVersionedCqlLibrary(anyString(), any(), any(), anyBoolean(), any()))
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
            "TestFHIRHelpers", "1.0.000", Optional.of("QI-Core v4.1.1"), false, null);
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
    when(cqlLibraryService.getVersionedCqlLibrary(anyString(), any(), any(), anyBoolean(), any()))
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
            "TestFHIRHelpers", "1.0.000", Optional.of("QI-Core v4.1.1"), true, "test-okta");
  }

  @Test
  public void testGetVersionedCqlLibraryReturnsNotFound() throws Exception {
    when(cqlLibraryService.getVersionedCqlLibrary(anyString(), any(), any(), anyBoolean(), any()))
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
            "TestFHIRHelpers", "1.0.000", Optional.of("QI-Core v4.1.1"), true, "test-okta");
  }

  @Test
  public void testGetVersionedCqlLibraryReturnsConflict() throws Exception {
    when(cqlLibraryService.getVersionedCqlLibrary(anyString(), any(), any(), anyBoolean(), any()))
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
            "TestFHIRHelpers", "1.0.000", Optional.of("QI-Core v4.1.1"), true, "test-okta");
  }

  @Test
  public void testChangeOwnership() throws Exception {
    String libraryId = "f225481c-921e-4015-9e14-e5046bfac9ff";

    doReturn(true).when(cqlLibraryService).changeOwnership(eq(libraryId), eq("testUser"));

    mockMvc
        .perform(
            put("/cql-libraries/" + libraryId + "/ownership?userid=testUser")
                .header(TEST_API_KEY_HEADER, TEST_API_KEY_HEADER_VALUE))
        .andExpect(status().isOk())
        .andExpect(content().string("testUser granted ownership to Library successfully."));

    verify(cqlLibraryService, times(1)).changeOwnership(eq(libraryId), eq("testUser"));

    verify(actionLogService, times(1))
        .logAction(
            targetIdArgumentCaptor.capture(), actionTypeArgumentCaptor.capture(), anyString());
    assertNotNull(targetIdArgumentCaptor.getValue());
    assertThat(actionTypeArgumentCaptor.getValue(), is(equalTo(ActionType.UPDATED)));
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
  void testDeleteLibraryAlongWithVersions() throws Exception {
    doNothing().when(cqlLibraryService).deleteLibraryAlongWithVersions(anyString(), anyString());
    MvcResult result =
        mockMvc
            .perform(
                delete("/cql-libraries/Test/delete-all-versions")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("Authorization", "test-okta")
                    .header("api-key", "0a51991c"))
            .andReturn();
    assertEquals(result.getResponse().getStatus(), HttpStatus.OK.value());
    assertEquals(
        result.getResponse().getContentAsString(),
        "The library and all its associated versions have been removed successfully.");
  }

  @Test
  void testDeleteLibraryAlongWithVersionsMissingAdminKey() throws Exception {
    doNothing().when(cqlLibraryService).deleteLibraryAlongWithVersions(anyString(), anyString());
    MvcResult result =
        mockMvc
            .perform(
                delete("/cql-libraries/Test/delete-all-versions")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header("Authorization", "test-okta"))
            .andReturn();
    assertEquals(result.getResponse().getStatus(), HttpStatus.FORBIDDEN.value());
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
}
