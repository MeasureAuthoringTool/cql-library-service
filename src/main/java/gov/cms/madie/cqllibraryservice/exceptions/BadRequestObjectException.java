package gov.cms.madie.cqllibraryservice.exceptions;

public class BadRequestObjectException extends RuntimeException {
  private static final String MESSAGE =
      "Received a invalid object of type %s with Id %s from User %s";

  public BadRequestObjectException(String message) {
    super(message);
  }

  public BadRequestObjectException(String type, String id, String user) {
    super(String.format(MESSAGE, type, id, user));
  }
}
