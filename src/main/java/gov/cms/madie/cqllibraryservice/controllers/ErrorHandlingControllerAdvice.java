package gov.cms.madie.cqllibraryservice.controllers;

import gov.cms.madie.cqllibraryservice.exceptions.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;

@RequiredArgsConstructor
@ControllerAdvice
public class ErrorHandlingControllerAdvice {

  @Autowired private final ErrorAttributes errorAttributes;

  @ExceptionHandler(ConstraintViolationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onConstraintValidationException(
      ConstraintViolationException ex, WebRequest request) {
    // Collect simplified validation errors
    Map<String, String> validationErrors = new HashMap<>();
    ex.getConstraintViolations()
        .forEach(
            (error) ->
                validationErrors.put(error.getPropertyPath().toString(), error.getMessage()));
    Map<String, Object> errorAttributes = getErrorAttributes(request, HttpStatus.BAD_REQUEST);
    errorAttributes.put("validationErrors", validationErrors);
    return errorAttributes;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onMethodArgumentNotValidException(
      MethodArgumentNotValidException ex, WebRequest request) {
    // Collect simplified validation errors
    Map<String, String> validationErrors = new HashMap<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            (error) -> {
              if (error instanceof FieldError) {
                String fieldName = ((FieldError) error).getField();
                String errorMessage = error.getDefaultMessage();
                validationErrors.put(fieldName, errorMessage);
              } else {
                validationErrors.put(error.getObjectName(), error.getDefaultMessage());
              }
            });
    Map<String, Object> errorAttributes = getErrorAttributes(request, HttpStatus.BAD_REQUEST);
    errorAttributes.put("validationErrors", validationErrors);
    return errorAttributes;
  }

  @ExceptionHandler(DuplicateKeyException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onDuplicateKeyExceptionException(
      DuplicateKeyException ex, WebRequest request) {
    Map<String, Object> errorAttributes = getErrorAttributes(request, HttpStatus.BAD_REQUEST);
    errorAttributes.put(
        "validationErrors", Map.of(ex.getKey(), Objects.requireNonNull(ex.getMessage())));
    return errorAttributes;
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ResponseBody
  Map<String, Object> onResourceNotFoundException(
      ResourceNotFoundException ex, WebRequest request) {
    return getErrorAttributes(request, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler({
    GeneralConflictException.class,
    ResourceNotDraftableException.class,
    InvalidResourceStateException.class
  })
  @ResponseStatus(HttpStatus.CONFLICT)
  @ResponseBody
  Map<String, Object> onResourceNotDraftableException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.CONFLICT);
  }

  @ExceptionHandler(InvalidIdException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onInvalidKeyException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(BadRequestObjectException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onBadRequestObjectException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(PermissionDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  @ResponseBody
  Map<String, Object> onPermissionDeniedException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.FORBIDDEN);
  }

  @ExceptionHandler(UnauthorizedException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  @ResponseBody
  Map<String, Object> onUnauthorizedException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.UNAUTHORIZED);
  }

  @ExceptionHandler(ResourceCannotBeVersionedException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onResourceCannotBeVersionedException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler({
    InternalServerErrorException.class,
    PersistHapiFhirCqlLibraryException.class,
    CqlElmTranslationErrorException.class,
    CqlElmTranslationServiceException.class,
    MeasureServiceException.class
  })
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ResponseBody
  Map<String, Object> onInternalServerErrorException(WebRequest request) {
    return getErrorAttributes(request, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private Map<String, Object> getErrorAttributes(WebRequest request, HttpStatus httpStatus) {
    // BINDING_ERRORS and STACK_TRACE are too detailed and confusing to parse
    // Let's just add a list of simplified validation errors
    ErrorAttributeOptions errorOptions =
        ErrorAttributeOptions.of(ErrorAttributeOptions.Include.MESSAGE);
    Map<String, Object> errorAttributes =
        this.errorAttributes.getErrorAttributes(request, errorOptions);
    errorAttributes.put("status", httpStatus.value());
    errorAttributes.put("error", httpStatus.getReasonPhrase());
    return errorAttributes;
  }
}
