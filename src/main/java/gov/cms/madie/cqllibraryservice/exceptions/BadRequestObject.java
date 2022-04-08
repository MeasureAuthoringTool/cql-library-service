package gov.cms.madie.cqllibraryservice.exceptions;

public class BadRequestObject extends RuntimeException {
  private static final String MESSAGE =
      "Received a invalid object of type %s with Id %s from User %s";

  public BadRequestObject(String type, String id, String user) {
    super(String.format(MESSAGE, type, id, user));
  }
}
