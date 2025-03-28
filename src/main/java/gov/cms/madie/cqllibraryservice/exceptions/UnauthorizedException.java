package gov.cms.madie.cqllibraryservice.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {

  private static final String MESSAGE = "User %s is not authorized for %s with ID %s";

  public UnauthorizedException(String type, String id, String user) {
    super(String.format(MESSAGE, user, type, id));
  }

  public UnauthorizedException(String message) {
    super(message);
  }
}
