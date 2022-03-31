package gov.cms.madie.cqllibraryservice.exceptions;

public class ResourceNotFoundException extends RuntimeException {
  private static final String MESSAGE = "Could not find resource %s with id: %s";

  public ResourceNotFoundException(String type, String id) {
    super(String.format(MESSAGE, type, id));
  }
}
