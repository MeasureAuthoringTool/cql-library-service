package gov.cms.madie.cqllibraryservice.exceptions;

public class ResourceLockedException extends RuntimeException {
  private static final String MESSAGE = "User %s cannot lock library with id: %s as %s";

  public ResourceLockedException(String user, String id, String cause) {
    super(String.format(MESSAGE, user, id, cause));
  }
}
