package gov.cms.madie.cqllibraryservice.exceptions;

public class ResourceOwnerNotFoundException extends RuntimeException {
  private static final String MESSAGE = "User %s cannot upda resource %s with id: %s";

  public ResourceOwnerNotFoundException(String type, String id, String user) {
    super(String.format(MESSAGE, type, id));
  }
}
