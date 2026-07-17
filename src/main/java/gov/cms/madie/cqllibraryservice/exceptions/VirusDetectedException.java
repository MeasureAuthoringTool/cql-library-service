package gov.cms.madie.cqllibraryservice.exceptions;

/** Thrown when a virus scan identifies an infected file in a downloaded FHIR package. */
public class VirusDetectedException extends RuntimeException {

  public VirusDetectedException(String message) {
    super(message);
  }
}
