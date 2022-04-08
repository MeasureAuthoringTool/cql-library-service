package gov.cms.madie.cqllibraryservice.exceptions;

public class ResourceNotDraftableException extends RuntimeException{
  private static final String MESSAGE = "Cannot draft resource %s. A draft already exists for the CQL Library Group.";

  public ResourceNotDraftableException(String type) {
    super(String.format(MESSAGE, type));
  }
}
