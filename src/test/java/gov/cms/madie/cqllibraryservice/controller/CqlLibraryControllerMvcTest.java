package gov.cms.madie.cqllibraryservice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.cqllibraryservice.models.CqlLibrary;
import gov.cms.madie.cqllibraryservice.respositories.CqlLibraryRepository;
import org.bson.types.ObjectId;
import org.hamcrest.CustomMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({CqlLibraryController.class})
public class CqlLibraryControllerMvcTest {

  private static final String TEST_USER_ID = "test-okta-user-id-123";

  @MockBean private CqlLibraryRepository repository;

  @Autowired private MockMvc mockMvc;

  public String toJsonString(CqlLibrary cqlLibrary) throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.writeValueAsString(cqlLibrary);
  }

  public CqlLibrary fromJsonString(final String json) throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.readValue(json, CqlLibrary.class);
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForNullCqlLibraryName() throws Exception {
    String json = toJsonString(CqlLibrary.builder().cqlLibraryName(null).build());
    when(repository.findByCqlLibraryName(anyString())).thenReturn(Optional.empty());
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
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForEmptyCqlLibraryName() throws Exception {
    String json = toJsonString(CqlLibrary.builder().cqlLibraryName("").build());
    when(repository.findByCqlLibraryName(anyString())).thenReturn(Optional.empty());
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
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForLowercaseStartCharacter()
      throws Exception {
    String json = toJsonString(CqlLibrary.builder().cqlLibraryName("aBCDefg").build());
    when(repository.findByCqlLibraryName(anyString())).thenReturn(Optional.empty());
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
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForContainingSpaces() throws Exception {
    String json = toJsonString(CqlLibrary.builder().cqlLibraryName("With  spaces ").build());
    when(repository.findByCqlLibraryName(anyString())).thenReturn(Optional.empty());
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
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForContainingUnderscore() throws Exception {
    String json = toJsonString(CqlLibrary.builder().cqlLibraryName("With_underscore").build());
    when(repository.findByCqlLibraryName(anyString())).thenReturn(Optional.empty());
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
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForContainingSpecialCharacters()
      throws Exception {
    String json = toJsonString(CqlLibrary.builder().cqlLibraryName("Name*$").build());
    when(repository.findByCqlLibraryName(anyString())).thenReturn(Optional.empty());
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
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForLengthOver255Chars() throws Exception {
    final String reallyLongName =
        "Reallylongnamethatisover255charactersbutwouldotherwisebevalidifitwereunder255charactersandisjustanattempttogetthevalidatortoblowupwiththisstupidlylongnamethatnobodywouldeveractuallyusebecausereallywhowouldtypeareallylongnamelikethiswithoutspacesorunderscorestoseparatewords";
    String json = toJsonString(CqlLibrary.builder().cqlLibraryName(reallyLongName).build());
    when(repository.findByCqlLibraryName(anyString())).thenReturn(Optional.empty());
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
  }

  @Test
  public void testCreateCqlLibraryReturnsValidationErrorForDuplicateCqlLibraryName()
      throws Exception {
    String json = toJsonString(CqlLibrary.builder().cqlLibraryName("DuplicateName").build());
    CqlLibrary existingCqlLibrary = CqlLibrary.builder().cqlLibraryName("DuplicateName").build();
    when(repository.findByCqlLibraryName(anyString())).thenReturn(Optional.of(existingCqlLibrary));
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
  }

  @Test
  public void testCreateCqlLibraryReturnsCreatedForValidName() throws Exception {
    CqlLibrary library = CqlLibrary.builder().cqlLibraryName("NewValidName1").build();
    String json = toJsonString(library);
    when(repository.findByCqlLibraryName(anyString())).thenReturn(Optional.empty());
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
        .andExpect(jsonPath("$.createdBy").value(TEST_USER_ID))
        .andExpect(jsonPath("$.lastModifiedBy").value(TEST_USER_ID))
        .andExpect(jsonPath("$.createdAt").value(fiveMinMatcher))
        .andExpect(jsonPath("$.lastModifiedAt").value(fiveMinMatcher));
  }
}