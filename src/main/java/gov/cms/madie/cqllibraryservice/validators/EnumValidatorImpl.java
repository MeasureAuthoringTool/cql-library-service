package gov.cms.madie.cqllibraryservice.validators;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EnumValidatorImpl implements ConstraintValidator<gov.cms.madie.cqllibraryservice.validators.EnumValidator, String> {

  private List<String> acceptedValues;

  @Override
  public void initialize(gov.cms.madie.cqllibraryservice.validators.EnumValidator annotation) {
    acceptedValues =
        Stream.of(annotation.enumClass().getEnumConstants())
            .map(Enum::toString)
            .collect(Collectors.toList());
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return acceptedValues.contains(value);
  }
}
