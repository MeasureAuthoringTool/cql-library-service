package gov.cms.madie.cqllibraryservice.exceptions;

public class HarpIdMismatchException extends RuntimeException {

  private static final String MESSAGE =
      "Response could not be completed because the HARP id of %s passed in does not " +
          "match the owner of the library with the library id of %s. The owner of the library is %s";

  public HarpIdMismatchException(String harpId, String owner, String libraryId) {
    super(String.format(MESSAGE, harpId, libraryId, owner));
  }
}
