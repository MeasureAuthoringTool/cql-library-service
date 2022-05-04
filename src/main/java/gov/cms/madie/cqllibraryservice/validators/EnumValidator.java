package gov.cms.madie.cqllibraryservice.validators;

import java.lang.annotation.*;
import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.ReportAsSingleViolation;
import javax.validation.constraints.NotNull;

@NotNull(message = "Value cannot be null")
@ReportAsSingleViolation
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = EnumValidatorImpl.class)
public @interface EnumValidator {

  Class<? extends Enum<?>> enumClass();

  String message() default "Value provided is not a valid option.";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
