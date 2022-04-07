package gov.cms.madie.cqllibraryservice.exceptions;

public class PermissionDeniedException extends RuntimeException {
  private static final String MESSAGE = "User %s cannot modify resource %s with id: %s";

  public PermissionDeniedException(String type, String id, String user) {
    super(String.format(MESSAGE, user, type, id));
  }
}
