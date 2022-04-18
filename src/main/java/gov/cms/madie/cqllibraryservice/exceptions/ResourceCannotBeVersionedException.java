package gov.cms.madie.cqllibraryservice.exceptions;

public class ResourceCannotBeVersionedException extends RuntimeException {
    private static final String MESSAGE = "User %s cannot version resource %s with id: %s as the Cql has errors in it";

    public ResourceCannotBeVersionedException(String type, String id, String user) {
        super(String.format(MESSAGE, user, type, id));
    }
}