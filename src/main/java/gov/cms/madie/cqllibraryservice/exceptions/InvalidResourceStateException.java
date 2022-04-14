package gov.cms.madie.cqllibraryservice.exceptions;

public class InvalidResourceStateException extends RuntimeException{
  private static final String MESSAGE = "Could not update resource %s with id: %s. Resource is not a Draft.";

  public InvalidResourceStateException(String type, String id) {
    super(String.format(MESSAGE, type, id));
  }
}
