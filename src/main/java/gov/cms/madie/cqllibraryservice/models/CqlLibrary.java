package gov.cms.madie.cqllibraryservice.models;

import gov.cms.madie.cqllibraryservice.validators.EnumValidator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;

import javax.validation.GroupSequence;
import javax.validation.constraints.*;
import javax.validation.groups.Default;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CqlLibrary {
  @Id private String id;

  @NotNull(message = "Library name is required.")
  @NotBlank(
      groups = {ValidationOrder1.class},
      message = "Library name is required.")
  @Size(
      max = 255,
      groups = {ValidationOrder2.class},
      message = "Library name cannot be more than 255 characters.")
  @Pattern(
      regexp = "^[A-Z][a-zA-Z0-9]*$",
      groups = {ValidationOrder3.class},
      message =
          "Library name must start with an upper case letter, "
              + "followed by alpha-numeric character(s) and must not contain "
              + "spaces or other special characters.")
  @Indexed(unique = true, name = "UniqueCqlLibraryName")
  private String cqlLibraryName;

  @NotNull(
      groups = {ValidationOrder1.class},
      message = "Model is required."
  )
  @EnumValidator(
      enumClass = ModelType.class,
      message = "Model must be one of the supported types in MADiE.",
      groups = {ValidationOrder4.class})
  private String model;

  private Instant createdAt;
  private String createdBy;
  private Instant lastModifiedAt;
  private String lastModifiedBy;

  @GroupSequence({
    CqlLibrary.ValidationOrder1.class,
    CqlLibrary.ValidationOrder2.class,
    CqlLibrary.ValidationOrder3.class,
    CqlLibrary.ValidationOrder4.class,
    Default.class
  })
  public interface ValidationSequence {}

  public interface ValidationOrder1 {}

  public interface ValidationOrder2 {}

  public interface ValidationOrder3 {}

  public interface ValidationOrder4 {}
}
