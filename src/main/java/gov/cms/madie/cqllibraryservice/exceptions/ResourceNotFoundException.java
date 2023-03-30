package gov.cms.madie.cqllibraryservice.exceptions;

public class ResourceNotFoundException extends RuntimeException {
  private static final String MESSAGE = "Could not find resource %s with id: %s";
  private static final String IDENTIFIER_MESSAGE = "Could not find resource %s with %s: %s";

  public ResourceNotFoundException(String type, String id) {
    super(String.format(MESSAGE, type, id));
  }

  public ResourceNotFoundException(String type, String identifier, String identifierValue) {
    super(String.format(IDENTIFIER_MESSAGE, type, identifier, identifierValue));
  }
}
