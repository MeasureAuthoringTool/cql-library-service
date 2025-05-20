package gov.cms.madie.cqllibraryservice.exceptions;

public class QiCore411DraftOffQiCore600Exception extends RuntimeException {

  private static final long serialVersionUID = 8181203082737619340L;

  private static final String MESSAGE =
      "You cannot draft a 4.1.1 library when a 6.0.0 version is available";

  public QiCore411DraftOffQiCore600Exception() {
    super(String.format(MESSAGE));
  }
}
