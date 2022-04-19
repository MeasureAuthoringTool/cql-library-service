package gov.cms.madie.cqllibraryservice.exceptions;

public class ResourceCannotBeVersionedException extends RuntimeException {
  private static final String MESSAGE = "User %s cannot version resource %s with id: %s as %s";

  public ResourceCannotBeVersionedException(String type, String id, String user, String cause) {
    super(String.format(MESSAGE, user, type, id, cause));
  }
}
