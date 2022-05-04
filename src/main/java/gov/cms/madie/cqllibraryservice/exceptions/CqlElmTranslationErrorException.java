package gov.cms.madie.cqllibraryservice.exceptions;

public class CqlElmTranslationErrorException extends RuntimeException {
  private static final String MESSAGE =
      "CQL-ELM translator found errors in the CQL for library %s! Version not created.";

  public CqlElmTranslationErrorException(String libraryName) {
    super(String.format(MESSAGE, libraryName));
  }
}
