package gov.cms.madie.cqllibraryservice.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import gov.cms.madie.cqllibraryservice.exceptions.InternalServerErrorException;
import gov.cms.madie.cqllibraryservice.exceptions.PermissionDeniedException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotDraftableException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import gov.cms.madie.cqllibraryservice.models.ModelType;
import gov.cms.madie.cqllibraryservice.models.Version;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.cqllibraryservice.services.VersionService;
import org.bson.types.ObjectId;
import org.hamcrest.CustomMatcher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({CqlLibraryController.class})
public class CqlLibraryControllerMvcTest {

  private static final String TEST_USER_ID = "test-okta-user-id-123";

  @MockBean private CqlLibraryRepository repository;

  @MockBean private VersionService versionService;

  @Captor private ArgumentCaptor<CqlLibrary> cqlLibraryArgumentCaptor;

  @Autowired private MockMvc mockMvc;

  public String toJsonString(CqlLibrary cqlLibrary) throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    return mapper.writeValueAsString(cqlLibrary);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForNullCqlLibraryName() throws Exception {
    String json = toJsonString(CqlLibrary.builder().cqlLibraryName(null).model("QI-Core").build());
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
    String json = toJsonString(CqlLibrary.builder().cqlLibraryName("").model("QI-Core").build());
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
        toJsonString(CqlLibrary.builder().cqlLibraryName("aBCDefg").model("QI-Core").build());
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
                .value(
                    "Library name must start with an upper case letter, "
                        + "followed by alpha-numeric character(s) and must not contain "
                        + "spaces or other special characters."));
    verifyNoInteractions(repository);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForContainingSpaces() throws Exception {
    String json =
        toJsonString(CqlLibrary.builder().cqlLibraryName("With  spaces ").model("QI-Core").build());
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
                .value(
                    "Library name must start with an upper case letter, "
                        + "followed by alpha-numeric character(s) and must not contain "
                        + "spaces or other special characters."));
    verifyNoInteractions(repository);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForContainingUnderscore() throws Exception {
    String json =
        toJsonString(
            CqlLibrary.builder().cqlLibraryName("With_underscore").model("QI-Core").build());
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
                .value(
                    "Library name must start with an upper case letter, "
                        + "followed by alpha-numeric character(s) and must not contain "
                        + "spaces or other special characters."));
    verifyNoInteractions(repository);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForContainingSpecialCharacters()
      throws Exception {
    String json =
        toJsonString(CqlLibrary.builder().cqlLibraryName("Name*$").model("QI-Core").build());
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
                .value(
                    "Library name must start with an upper case letter, "
                        + "followed by alpha-numeric character(s) and must not contain "
                        + "spaces or other special characters."));
    verifyNoInteractions(repository);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForLengthOver255Chars() throws Exception {
    final String reallyLongName =
        "Reallylongnamethatisover255charactersbutwouldotherwisebevalidifitwereunder255charactersandisjustanattempttogetthevalidatortoblowupwiththisstupidlylongnamethatnobodywouldeveractuallyusebecausereallywhowouldtypeareallylongnamelikethiswithoutspacesorunderscorestoseparatewords";
    String json =
        toJsonString(CqlLibrary.builder().cqlLibraryName(reallyLongName).model("QI-Core").build());
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
                .value("Library name cannot be more than 255 characters."));
    verifyNoInteractions(repository);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForDuplicateCqlLibraryName()
      throws Exception {
    String json =
        toJsonString(CqlLibrary.builder().cqlLibraryName("DuplicateName").model("QI-Core").build());
    when(repository.existsByCqlLibraryName(anyString())).thenReturn(true);
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
    verify(repository, times(1)).existsByCqlLibraryName(anyString());
    verifyNoMoreInteractions(repository);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForInvalidModel() throws Exception {
    String json = toJsonString(CqlLibrary.builder().cqlLibraryName("Name").model("RANDOM").build());
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
        CqlLibrary.builder().cqlLibraryName("NewValidName1").model("QI-Core").cql(cql).build();

    String json = toJsonString(library);
    when(repository.existsByCqlLibraryName(anyString())).thenReturn(false);
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
    verify(repository, times(1)).existsByCqlLibraryName(anyString());
    verify(repository, times(1)).save(any(CqlLibrary.class));
  }

  @Test
  public void testGetCqlLibraryReturns404() throws Exception {
    when(repository.findById(anyString())).thenReturn(Optional.empty());
    mockMvc
        .perform(get("/cql-libraries/Libary1_ID").with(user(TEST_USER_ID)).with(csrf()))
        .andExpect(status().isNotFound());
    verify(repository, times(1)).findById(anyString());
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
    when(repository.findById(anyString())).thenReturn(Optional.of(existingLibrary));
    mockMvc
        .perform(get("/cql-libraries/Libary1_ID").with(user(TEST_USER_ID)).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cqlLibraryName").value(existingLibrary.getCqlLibraryName()))
        .andExpect(jsonPath("$.id").value(existingLibrary.getId()))
        .andExpect(jsonPath("$.createdBy").value("User1"))
        .andExpect(jsonPath("$.lastModifiedBy").value("User1"))
        .andExpect(jsonPath("$.createdAt").value(is(equalTo(createdTime.toString()))))
        .andExpect(jsonPath("$.lastModifiedAt").value(is(equalTo(createdTime.toString()))));
    verify(repository, times(1)).findById(anyString());
  }

  @Test
  public void testUpdateCqlLibraryReturns400ForNullLibraryId() throws Exception {
    final CqlLibrary updatingLibrary =
        CqlLibrary.builder().id(null).cqlLibraryName("NewName").model("QI-Core").build();
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
        CqlLibrary.builder().id("").cqlLibraryName("NewName").model("QI-Core").build();
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
        CqlLibrary.builder().id("Wrong_ID").cqlLibraryName("NewName").model("QI-Core").build();
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
        CqlLibrary.builder().id("Wrong_ID").cqlLibraryName(null).model("QI-Core").build();
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
        CqlLibrary.builder().id("Wrong_ID").cqlLibraryName("").model("QI-Core").build();
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
        CqlLibrary.builder().id("Wrong_ID").cqlLibraryName("LibraryName").model("").build();
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
        CqlLibrary.builder().id("Library1_ID").cqlLibraryName("NewName").model("QI-Core").build();
    String json = toJsonString(updatingLibrary);
    when(repository.findById(anyString())).thenReturn(Optional.empty());
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
    verify(repository, times(1)).findById(anyString());
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
            .createdBy("User1")
            .lastModifiedAt(createdTime)
            .lastModifiedBy("User1")
            .build();
    final CqlLibrary updatingLibrary =
        existingLibrary.toBuilder().id("Library1_ID").cqlLibraryName("NewName").build();
    String json = toJsonString(updatingLibrary);
    when(repository.findById(anyString())).thenReturn(Optional.of(existingLibrary));
    when(repository.existsByCqlLibraryName(anyString())).thenReturn(true);
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
    verify(repository, times(1)).findById(anyString());
    verify(repository, times(1)).existsByCqlLibraryName(anyString());
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
            .createdBy("User1")
            .lastModifiedAt(createdTime)
            .lastModifiedBy("User1")
            .build();
    final CqlLibrary updatingLibrary =
        existingLibrary.toBuilder().id("Library1_ID").cqlLibraryName("NewName").build();
    String json = toJsonString(updatingLibrary);
    when(repository.findById(anyString())).thenReturn(Optional.of(existingLibrary));
    mockMvc
        .perform(
            put("/cql-libraries/Library1_ID")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .content(json)
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message").value(
                "Could not update resource CQL Library with id: Library1_ID. Resource is not a Draft."));
    verify(repository, times(1)).findById(anyString());
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
            .createdBy("User1")
            .lastModifiedAt(createdTime)
            .lastModifiedBy("User1")
            .build();
    final CqlLibrary updatingLibrary =
        existingLibrary
            .toBuilder()
            .id("Library1_ID")
            .cqlLibraryName("NewName")
            .cql("library testCql version '2.1.000'")
            .build();
    String json = toJsonString(updatingLibrary);
    when(repository.findById(anyString())).thenReturn(Optional.of(existingLibrary));
    when(repository.existsByCqlLibraryName(anyString())).thenReturn(false);
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
    verify(repository, times(1)).findById(anyString());
    verify(repository, times(1)).existsByCqlLibraryName(anyString());
    verify(repository, times(1)).save(cqlLibraryArgumentCaptor.capture());
    CqlLibrary savedValue = cqlLibraryArgumentCaptor.getValue();
    assertThat(savedValue, is(notNullValue()));
    assertThat(savedValue.getId(), is(equalTo("Library1_ID")));
    assertThat(savedValue.getCqlLibraryName(), is(equalTo("NewName")));
    assertThat(savedValue.getCql(), is(equalTo("library testCql version '2.1.000'")));
    assertThat(savedValue.getCreatedAt(), is(equalTo(createdTime)));
    assertThat(savedValue.getCreatedBy(), is(equalTo("User1")));
    assertThat(savedValue.getLastModifiedAt(), is(notNullValue()));
    assertThat(savedValue.getLastModifiedAt().isAfter(createdTime), is(true));
    assertThat(savedValue.getLastModifiedBy(), is(equalTo(TEST_USER_ID)));
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
            .groupId("group1")
            .createdAt(createdTime)
            .createdBy("User1")
            .lastModifiedAt(createdTime)
            .lastModifiedBy("User1")
            .build();
    final String json =
        toJsonString(
            existingLibrary.toBuilder().draft(false).version(new Version(2, 1, 0)).build());

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
    verify(versionService, times(1)).createDraft(eq("Library1_ID"), eq("Library1"), eq(TEST_USER_ID));
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
            .groupId("group1")
            .createdAt(createdTime)
            .createdBy("User1")
            .lastModifiedAt(createdTime)
            .lastModifiedBy("User1")
            .build();
    final String json =
        toJsonString(
            existingLibrary.toBuilder().draft(false).version(new Version(2, 1, 0)).build());

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
    verify(versionService, times(1)).createDraft(eq("Library1_ID"), eq("Library1"), eq(TEST_USER_ID));
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
            .groupId("group1")
            .createdAt(createdTime)
            .createdBy("User1")
            .lastModifiedAt(createdTime)
            .lastModifiedBy("User1")
            .build();
    final String json = toJsonString(draftLibrary.toBuilder().draft(false).build());

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
    verify(versionService, times(1)).createDraft(eq("Library1_ID"), eq("Library1"), eq(TEST_USER_ID));
  }

  @Test
  public void testCreateVersionReturnsNotFound() throws Exception {
    when(versionService.createVersion(anyString(), anyBoolean(), anyString()))
        .thenThrow(new ResourceNotFoundException("CQL Library", "Library1_ID"));
    mockMvc
        .perform(
            put("/cql-libraries/version/Library1_ID?isMajor=true")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
    verify(versionService, times(1)).createVersion(eq("Library1_ID"), eq(true), eq(TEST_USER_ID));
  }

  @Test
  public void testCreateVersionReturnsForbiddenForPermissionDenied() throws Exception {
    when(versionService.createVersion(anyString(), anyBoolean(), anyString()))
        .thenThrow(new PermissionDeniedException("CQL Library", "Library1_ID", "test.user"));
    mockMvc
        .perform(
            put("/cql-libraries/version/Library1_ID?isMajor=false")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));
    verify(versionService, times(1)).createVersion(eq("Library1_ID"), eq(false), eq(TEST_USER_ID));
  }

  @Test
  public void testCreateVersionReturnsInternalServerError() throws Exception {
    when(versionService.createVersion(anyString(), anyBoolean(), anyString()))
        .thenThrow(new InternalServerErrorException("Unable to update version number"));
    mockMvc
        .perform(
            put("/cql-libraries/version/Library1_ID?isMajor=false")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(jsonPath("$.message").value("Unable to update version number"));
    verify(versionService, times(1)).createVersion(eq("Library1_ID"), eq(false), eq(TEST_USER_ID));
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
            .groupId("group1")
            .createdAt(createdTime)
            .createdBy("User1")
            .lastModifiedAt(createdTime)
            .lastModifiedBy("User1")
            .build();

    when(versionService.createVersion(anyString(), anyBoolean(), anyString()))
        .thenReturn(versionLibrary);
    mockMvc
        .perform(
            put("/cql-libraries/version/Library1_ID?isMajor=true")
                .with(user(TEST_USER_ID))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
        .andExpect(jsonPath("$.id").value("Library1_ID"))
        .andExpect(jsonPath("$.cqlLibraryName").value("Library1"))
        .andExpect(jsonPath("$.draft").value(false))
        .andExpect(jsonPath("$.version").value("2.0.000"));
    verify(versionService, times(1)).createVersion(eq("Library1_ID"), eq(true), eq(TEST_USER_ID));
  }
}