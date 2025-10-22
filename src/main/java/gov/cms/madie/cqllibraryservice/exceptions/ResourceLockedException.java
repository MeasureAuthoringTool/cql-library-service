package gov.cms.madie.cqllibraryservice.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.LOCKED) // 423
public class ResourceLockedException extends RuntimeException {
  private static final long serialVersionUID = -4055361824526700706L;

  public ResourceLockedException(String resourceType, String id, String lockedBy) {
    super(resourceType + " with id " + id + ". It is being edited by user: " + lockedBy);
  }

  public ResourceLockedException(String message) {
    super(message);
  }
}
