package gov.cms.madie.cqllibraryservice.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.LOCKED) // 423
public class ResourceLockedException extends RuntimeException {
    public ResourceLockedException(String resourceType, String id, String lockedBy) {
        super(resourceType + " with id " + id + " is locked by user " + lockedBy);
    }
}
