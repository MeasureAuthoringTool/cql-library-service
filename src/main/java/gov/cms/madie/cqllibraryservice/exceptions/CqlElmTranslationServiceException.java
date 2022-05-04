package gov.cms.madie.cqllibraryservice.exceptions;

public class CqlElmTranslationServiceException extends RuntimeException {

  public CqlElmTranslationServiceException(String message, Exception cause) {
    super(message, cause);
  }
}
