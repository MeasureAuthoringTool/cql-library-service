package gov.cms.madie.cqllibraryservice.exceptions;

public class ResourceNotDraftableException extends RuntimeException {
  private static final String MESSAGE = "Cannot draft resource %s. %s";

  public ResourceNotDraftableException(String type, String reason) {
    super(String.format(MESSAGE, type, reason));
  }
}
