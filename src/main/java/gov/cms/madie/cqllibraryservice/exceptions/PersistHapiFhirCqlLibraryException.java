package gov.cms.madie.cqllibraryservice.exceptions;

public class PersistHapiFhirCqlLibraryException extends RuntimeException {
  private static final String MESSAGE =
      "User %s cannot version resource %s with id: %s as there "
          + "was an issue calling the Hapi Fhir service";

  public PersistHapiFhirCqlLibraryException(String type, String id, String user) {
    super(String.format(MESSAGE, user, type, id));
  }
}
