package gov.cms.madie.cqllibraryservice.services;

import gov.cms.madie.cqllibraryservice.exceptions.InvalidIdException;
import gov.cms.madie.cqllibraryservice.exceptions.PermissionDeniedException;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.exceptions.UnauthorizedException;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.access.UserStatus;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.dto.UserRolesDto;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CqlLibraryAccessControlServiceTest {

  @Mock private LibrarySetService librarySetService;

  @Mock private UserServiceClient userServiceClient;

  @InjectMocks private CqlLibraryAccessControlService accessControlService;

  @Test
  void checkAccessPermissionsSucceedsWhenUserIsOwner() {
    String username = "testUser";
    CqlLibrary cqlLibrary = new CqlLibrary();
    cqlLibrary.setId("library-1");
    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner(username);
    cqlLibrary.setLibrarySet(librarySet);

    assertDoesNotThrow(() -> accessControlService.checkAccessPermissions(cqlLibrary, username));
  }

  @Test
  void checkAccessPermissionsWithMultipleAclRoles() {
    String username = "testUser";
    CqlLibrary cqlLibrary = new CqlLibrary();
    cqlLibrary.setId("library-1");

    AclSpecification acl = new AclSpecification();
    acl.setUserId(username);
    acl.setRoles(Set.of(RoleEnum.SHARED_WITH));

    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner("ownerUser");
    librarySet.setAcls(List.of(acl));

    cqlLibrary.setLibrarySet(librarySet);

    assertDoesNotThrow(() -> accessControlService.checkAccessPermissions(cqlLibrary, username));
  }

  @Test
  void checkAccessPermissionsThrowsExceptionWhenUserDoesNotHavePermission() {
    String username = "testUser";
    String libraryId = "library-1";
    CqlLibrary cqlLibrary = new CqlLibrary();
    cqlLibrary.setId(libraryId);

    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner("ownerUser");
    librarySet.setAcls(new ArrayList<>());
    cqlLibrary.setLibrarySet(librarySet);

    PermissionDeniedException exception =
        assertThrows(
            PermissionDeniedException.class,
            () -> accessControlService.checkAccessPermissions(cqlLibrary, username));
    assertThat(
        exception.getMessage(),
        is(
            equalTo(
                "User " + username + " cannot modify resource CQL Library with id: " + libraryId)));
  }

  @Test
  void checkOwnershipSucceedsWhenUserIsOwner() {
    String username = "ownerUser";
    CqlLibrary cqlLibrary = new CqlLibrary();
    cqlLibrary.setId("library-1");

    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner(username);
    cqlLibrary.setLibrarySet(librarySet);

    assertDoesNotThrow(() -> accessControlService.checkOwnership(cqlLibrary, username));
  }

  @Test
  void checkOwnershipSucceedsWithCaseInsensitiveUsername() {
    String username = "OWNERUSER";
    String ownerInLibrarySet = "owneruser";
    CqlLibrary cqlLibrary = new CqlLibrary();
    cqlLibrary.setId("library-1");

    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner(ownerInLibrarySet);
    cqlLibrary.setLibrarySet(librarySet);

    assertDoesNotThrow(() -> accessControlService.checkOwnership(cqlLibrary, username));
  }

  @Test
  void checkOwnershipThrowsExceptionWhenUserIsNotOwner() {
    String username = "testUser";
    String ownerId = "ownerUser";
    String libraryId = "library-1";
    CqlLibrary cqlLibrary = new CqlLibrary();
    cqlLibrary.setId(libraryId);

    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner(ownerId);
    cqlLibrary.setLibrarySet(librarySet);

    PermissionDeniedException exception =
        assertThrows(
            PermissionDeniedException.class,
            () -> accessControlService.checkOwnership(cqlLibrary, username));
    assertThat(
        exception.getMessage(),
        is(
            equalTo(
                "User " + username + " cannot modify resource CQL Library with id: " + libraryId)));
  }

  @Test
  void checkOwnershipThrowsExceptionWhenLibrarySetIsNull() {
    String username = "testUser";
    String libraryId = "library-1";
    CqlLibrary cqlLibrary = new CqlLibrary();
    cqlLibrary.setId(libraryId);
    cqlLibrary.setLibrarySet(null);

    PermissionDeniedException exception =
        assertThrows(
            PermissionDeniedException.class,
            () -> accessControlService.checkOwnership(cqlLibrary, username));
    assertThat(
        exception.getMessage(),
        is(
            equalTo(
                "User " + username + " cannot modify resource CQL Library with id: " + libraryId)));
  }

  @Test
  void verifyAuthorizationSucceedsWhenUserIsAdmin() {
    String username = "adminUser";
    CqlLibrary library = new CqlLibrary();
    library.setId("library-1");
    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner("otherUser");
    library.setLibrarySet(librarySet);

    assertDoesNotThrow(
        () ->
            accessControlService.verifyAuthorization(
                username, library, List.of(RoleEnum.SHARED_WITH), true));
  }

  @Test
  void verifyAuthorizationSucceedsWhenUserIsOwner() {
    String username = "ownerUser";
    CqlLibrary library = new CqlLibrary();
    library.setId("library-1");
    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner(username);
    library.setLibrarySet(librarySet);

    assertDoesNotThrow(
        () ->
            accessControlService.verifyAuthorization(
                username, library, List.of(RoleEnum.SHARED_WITH), false));
  }

  @Test
  void verifyAuthorizationSucceedsWhenUserHasAllowedRole() {
    String username = "testUser";
    CqlLibrary library = new CqlLibrary();
    library.setId("library-1");

    AclSpecification acl = new AclSpecification();
    acl.setUserId(username);
    acl.setRoles(Set.of(RoleEnum.SHARED_WITH));

    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner("ownerUser");
    librarySet.setAcls(List.of(acl));
    library.setLibrarySet(librarySet);

    assertDoesNotThrow(
        () ->
            accessControlService.verifyAuthorization(
                username, library, List.of(RoleEnum.SHARED_WITH), false));
  }

  @Test
  void verifyAuthorizationThrowsExceptionWhenUserDoesNotHaveRequiredRole() {
    String username = "testUser";
    String libraryId = "library-1";
    CqlLibrary library = new CqlLibrary();
    library.setId(libraryId);

    AclSpecification acl = new AclSpecification();
    acl.setUserId(username);
    acl.setRoles(Set.of(RoleEnum.SHARED_WITH));

    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner("ownerUser");
    librarySet.setAcls(List.of(acl));
    library.setLibrarySet(librarySet);

    UnauthorizedException exception =
        assertThrows(
            UnauthorizedException.class,
            () -> accessControlService.verifyAuthorization(username, library, List.of(), false));
    assertThat(
        exception.getMessage(),
        is(equalTo("User " + username + " is not authorized for CqlLibrary with ID " + libraryId)));
  }

  @Test
  void verifyAuthorizationFetchesLibrarySetWhenNotPresent() {
    String username = "ownerUser";
    String librarySetId = "libSet-1";
    CqlLibrary library = new CqlLibrary();
    library.setId("library-1");
    library.setLibrarySetId(librarySetId);
    library.setLibrarySet(null);

    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner(username);

    when(librarySetService.findByLibrarySetId(librarySetId)).thenReturn(librarySet);

    assertDoesNotThrow(
        () ->
            accessControlService.verifyAuthorization(
                username, library, List.of(RoleEnum.SHARED_WITH), false));
  }

  @Test
  void verifyAuthorizationThrowsExceptionWhenLibrarySetNotFound() {
    String username = "testUser";
    String libraryId = "library-1";
    String librarySetId = "libSet-1";
    CqlLibrary library = new CqlLibrary();
    library.setId(libraryId);
    library.setLibrarySetId(librarySetId);
    library.setLibrarySet(null);

    when(librarySetService.findByLibrarySetId(librarySetId)).thenReturn(null);

    ResourceNotFoundException exception =
        assertThrows(
            ResourceNotFoundException.class,
            () ->
                accessControlService.verifyAuthorization(
                    username, library, List.of(RoleEnum.SHARED_WITH), false));
    assertThat(
        exception.getMessage(),
        is(equalTo("No library set exists for library with ID : " + libraryId)));
  }

  @Test
  void verifyLibrarySetAuthorizationSucceedsWhenUserIsOwner() {
    String username = "ownerUser";
    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner(username);

    assertDoesNotThrow(
        () ->
            accessControlService.verifyLibrarySetAuthorization(
                username, "CqlLibrary", "library-1", List.of(RoleEnum.SHARED_WITH), librarySet));
  }

  @Test
  void verifyLibrarySetAuthorizationSucceedsWhenUserHasAllowedRole() {
    String username = "testUser";
    AclSpecification acl = new AclSpecification();
    acl.setUserId(username);
    acl.setRoles(Set.of(RoleEnum.SHARED_WITH));

    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner("ownerUser");
    librarySet.setAcls(List.of(acl));

    assertDoesNotThrow(
        () ->
            accessControlService.verifyLibrarySetAuthorization(
                username, "CqlLibrary", "library-1", List.of(RoleEnum.SHARED_WITH), librarySet));
  }

  @Test
  void verifyLibrarySetAuthorizationThrowsExceptionWhenUserNotAuthorized() {
    String username = "testUser";
    String targetId = "library-1";
    AclSpecification acl = new AclSpecification();
    acl.setUserId("otherUser");
    acl.setRoles(Set.of(RoleEnum.SHARED_WITH));

    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner("ownerUser");
    librarySet.setAcls(List.of(acl));

    UnauthorizedException exception =
        assertThrows(
            UnauthorizedException.class,
            () ->
                accessControlService.verifyLibrarySetAuthorization(
                    username, "CqlLibrary", targetId, List.of(RoleEnum.SHARED_WITH), librarySet));
    assertThat(
        exception.getMessage(),
        is(equalTo("User " + username + " is not authorized for CqlLibrary with ID " + targetId)));
  }

  @Test
  void verifyLibrarySetAuthorizationWithNullRoles() {
    String username = "testUser";
    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner("ownerUser");
    librarySet.setAcls(new ArrayList<>());

    UnauthorizedException exception =
        assertThrows(
            UnauthorizedException.class,
            () ->
                accessControlService.verifyLibrarySetAuthorization(
                    username,
                    "CqlLibrary",
                    "library-1",
                    List.of(RoleEnum.SHARED_WITH),
                    librarySet));
    assertThat(exception.getMessage(), is(not(nullValue())));
  }

  @Test
  void hasAdminRoleReturnsTrueWhenUserHasAdminRole() {
    String username = "adminUser";
    String accessToken = "token123";

    UserRolesDto userRolesDto = new UserRolesDto();
    userRolesDto.setRoles(List.of("MADiE-Admin", "OtherRole"));

    when(userServiceClient.getUserRoles(username, accessToken)).thenReturn(userRolesDto);

    boolean result = accessControlService.hasAdminRole(username, accessToken);

    assertThat(result, is(true));
  }

  @Test
  void hasAdminRoleReturnsFalseWhenUserDoesNotHaveAdminRole() {
    String username = "regularUser";
    String accessToken = "token123";

    UserRolesDto userRolesDto = new UserRolesDto();
    userRolesDto.setRoles(List.of("OtherRole"));

    when(userServiceClient.getUserRoles(username, accessToken)).thenReturn(userRolesDto);

    boolean result = accessControlService.hasAdminRole(username, accessToken);

    assertThat(result, is(false));
  }

  @Test
  void hasAdminRoleReturnsFalseWhenUserRolesDtoIsNull() {
    String username = "user";
    String accessToken = "token123";

    when(userServiceClient.getUserRoles(username, accessToken)).thenReturn(null);

    boolean result = accessControlService.hasAdminRole(username, accessToken);

    assertThat(result, is(false));
  }

  @Test
  void hasAdminRoleReturnsFalseWhenUserRolesIsNull() {
    String username = "user";
    String accessToken = "token123";

    UserRolesDto userRolesDto = new UserRolesDto();
    userRolesDto.setRoles(null);

    when(userServiceClient.getUserRoles(username, accessToken)).thenReturn(userRolesDto);

    boolean result = accessControlService.hasAdminRole(username, accessToken);

    assertThat(result, is(false));
  }

  @Test
  void hasAdminRoleReturnsFalseWhenUserRolesIsEmpty() {
    String username = "user";
    String accessToken = "token123";

    UserRolesDto userRolesDto = new UserRolesDto();
    userRolesDto.setRoles(List.of());

    when(userServiceClient.getUserRoles(username, accessToken)).thenReturn(userRolesDto);

    boolean result = accessControlService.hasAdminRole(username, accessToken);

    assertThat(result, is(false));
  }

  @Test
  void hasReviewerRoleReturnsTrueWhenUserHasReviewerRole() {
    String username = "reviewerUser";
    String accessToken = "token123";

    UserRolesDto userRolesDto = new UserRolesDto();
    userRolesDto.setRoles(List.of("MADiE-Reviewer", "OtherRole"));

    when(userServiceClient.getUserRoles(username, accessToken)).thenReturn(userRolesDto);

    assertThat(accessControlService.hasReviewerRole(username, accessToken), is(true));
  }

  @Test
  void hasReviewerRoleReturnsFalseWhenUserDoesNotHaveReviewerRole() {
    String username = "regularUser";
    String accessToken = "token123";

    UserRolesDto userRolesDto = new UserRolesDto();
    userRolesDto.setRoles(List.of("OtherRole"));

    when(userServiceClient.getUserRoles(username, accessToken)).thenReturn(userRolesDto);

    assertThat(accessControlService.hasReviewerRole(username, accessToken), is(false));
  }

  @Test
  void verifyReviewerAccessSucceedsForReviewer() {
    String username = "reviewerUser";
    String accessToken = "token123";

    UserRolesDto userRolesDto = new UserRolesDto();
    userRolesDto.setRoles(List.of("MADiE-Reviewer"));

    when(userServiceClient.getUserRoles(username, accessToken)).thenReturn(userRolesDto);

    assertDoesNotThrow(() -> accessControlService.verifyReviewerAccess(username, accessToken));
  }

  @Test
  void verifyReviewerAccessThrowsWhenNotReviewer() {
    String username = "regularUser";
    String accessToken = "token123";

    UserRolesDto userRolesDto = new UserRolesDto();
    userRolesDto.setRoles(List.of("OtherRole"));

    when(userServiceClient.getUserRoles(username, accessToken)).thenReturn(userRolesDto);

    assertThrows(
        PermissionDeniedException.class,
        () -> accessControlService.verifyReviewerAccess(username, accessToken));
  }

  @Test
  void validateHarpIdSucceedsForActiveUser() {
    String userId = "user123";
    String accessToken = "token123";

    UserDetailsDto userDetailsDto = new UserDetailsDto();
    userDetailsDto.setUserStatus(UserStatus.ACTIVE);

    when(userServiceClient.getUserDetails(userId, accessToken)).thenReturn(userDetailsDto);

    assertDoesNotThrow(() -> accessControlService.validateHarpId(userId, accessToken));
  }

  @Test
  void validateHarpIdThrowsExceptionWhenUserIsInactive() {
    String userId = "user123";
    String accessToken = "token123";

    UserDetailsDto userDetailsDto = new UserDetailsDto();
    userDetailsDto.setUserStatus(UserStatus.DEACTIVATED);

    when(userServiceClient.getUserDetails(userId, accessToken)).thenReturn(userDetailsDto);

    InvalidIdException exception =
        assertThrows(
            InvalidIdException.class,
            () -> accessControlService.validateHarpId(userId, accessToken));
    assertThat(
        exception.getMessage(),
        is(equalTo("The provided HARP ID is not associated with an active MADiE user.")));
  }

  void validateHarpIdThrowsExceptionWhenUserDetailsIsNull() {
    String userId = "user123";
    String accessToken = "token123";

    when(userServiceClient.getUserDetails(userId, accessToken)).thenReturn(null);

    InvalidIdException exception =
        assertThrows(
            InvalidIdException.class,
            () -> accessControlService.validateHarpId(userId, accessToken));
    assertThat(
        exception.getMessage(),
        is(equalTo("The provided HARP ID is not associated with an active MADiE user.")));
  }

  @Test
  void checkAccessPermissionsWithCaseInsensitiveUsername() {
    String username = "TESTUSER";
    CqlLibrary cqlLibrary = new CqlLibrary();
    cqlLibrary.setId("library-1");

    AclSpecification acl = new AclSpecification();
    acl.setUserId("testuser");
    acl.setRoles(Set.of(RoleEnum.SHARED_WITH));

    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner("ownerUser");
    librarySet.setAcls(List.of(acl));

    cqlLibrary.setLibrarySet(librarySet);

    assertDoesNotThrow(() -> accessControlService.checkAccessPermissions(cqlLibrary, username));
  }

  @Test
  void verifyLibrarySetAuthorizationWithEmptyAcls() {
    String username = "testUser";
    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner("ownerUser");
    librarySet.setAcls(List.of());

    UnauthorizedException exception =
        assertThrows(
            UnauthorizedException.class,
            () ->
                accessControlService.verifyLibrarySetAuthorization(
                    username,
                    "CqlLibrary",
                    "library-1",
                    List.of(RoleEnum.SHARED_WITH),
                    librarySet));
    assertThat(exception.getMessage(), is(not(nullValue())));
  }

  @Test
  void verifyLibrarySetAuthorizationWithNullAcls() {
    String username = "testUser";
    LibrarySet librarySet = new LibrarySet();
    librarySet.setOwner("ownerUser");
    librarySet.setAcls(null);

    UnauthorizedException exception =
        assertThrows(
            UnauthorizedException.class,
            () ->
                accessControlService.verifyLibrarySetAuthorization(
                    username,
                    "CqlLibrary",
                    "library-1",
                    List.of(RoleEnum.SHARED_WITH),
                    librarySet));
    assertThat(exception.getMessage(), is(not(nullValue())));
  }
}
