package gov.cms.madie.cqllibraryservice.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.cms.madie.cqllibraryservice.dto.LibraryListDTO;
import gov.cms.madie.cqllibraryservice.dto.UserLibrariesDTO;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@ExtendWith(MockitoExtension.class)
class UserLibraryExportServiceTest {

  @Mock private MongoTemplate mongoTemplate;
  @Mock private UserServiceClient userServiceClient;

  @InjectMocks private UserLibraryExportService service;

  @org.mockito.Captor private org.mockito.ArgumentCaptor<List<String>> ownerIdsCaptor;

  private void stubAggregate(List<LibraryListDTO> results) {
    when(mongoTemplate.aggregate(
            any(Aggregation.class), eq(CqlLibrary.class), eq(LibraryListDTO.class)))
        .thenReturn(new AggregationResults<>(results, new Document()));
  }

  private LibraryListDTO library(
      String setId, String name, String owner, AclSpecification... acls) {
    LibrarySet librarySet =
        LibrarySet.builder()
            .librarySetId(setId)
            .owner(owner)
            .acls(acls.length == 0 ? new ArrayList<>() : new ArrayList<>(List.of(acls)))
            .build();
    return LibraryListDTO.builder()
        .librarySetId(setId)
        .cqlLibraryName(name)
        .librarySet(librarySet)
        .build();
  }

  private LibraryListDTO libraryWithoutSet(String name) {
    return LibraryListDTO.builder().cqlLibraryName(name).build();
  }

  private AclSpecification sharedWith(String userId) {
    return AclSpecification.builder().userId(userId).roles(Set.of(RoleEnum.SHARED_WITH)).build();
  }

  private UserDetailsDto userDetails(String harpId, String firstName, String lastName) {
    return UserDetailsDto.builder().harpId(harpId).firstName(firstName).lastName(lastName).build();
  }

  @Test
  void assemblesOwnedAndSharedLibrariesForAllUsersWhenHarpIdsNull() {
    LibraryListDTO owned = library("set-1", "Library One", "OwnerA", sharedWith("UserB"));
    LibraryListDTO other = library("set-2", "Library Two", "OwnerB");
    stubAggregate(List.of(owned, other));
    when(userServiceClient.getBulkUserDetails(anyList()))
        .thenReturn(
            Map.of(
                "ownera", userDetails("ownera", "Alice", "Owner"),
                "ownerb", userDetails("ownerb", "Bob", "Owner")));

    Map<String, UserLibrariesDTO> result = service.getLibrariesForUsers(null);

    assertEquals(3, result.size());
    assertSame(owned, result.get("ownera").getOwnedLibraries().get(0));
    assertTrue(result.get("ownera").getSharedLibraries().isEmpty());
    assertSame(other, result.get("ownerb").getOwnedLibraries().get(0));
    assertSame(owned, result.get("userb").getSharedLibraries().get(0));
    assertTrue(result.get("userb").getOwnedLibraries().isEmpty());
    assertEquals("Alice Owner", owned.getOwnerDisplayName());
    assertEquals("Bob Owner", other.getOwnerDisplayName());

    verify(userServiceClient).getBulkUserDetails(ownerIdsCaptor.capture());
    assertEquals(2, ownerIdsCaptor.getValue().size());
    assertTrue(ownerIdsCaptor.getValue().containsAll(List.of("ownera", "ownerb")));
  }

  @Test
  void filtersToRequestedHarpIdsIgnoringCase() {
    LibraryListDTO owned = library("set-1", "Library One", "OwnerA", sharedWith("UserB"));
    LibraryListDTO other = library("set-2", "Library Two", "OwnerB");
    stubAggregate(List.of(owned, other));
    when(userServiceClient.getBulkUserDetails(anyList()))
        .thenReturn(Map.of("ownera", userDetails("ownera", "Alice", "Owner")));

    Map<String, UserLibrariesDTO> result = service.getLibrariesForUsers(List.of("ownerA"));

    assertEquals(1, result.size());
    assertTrue(result.containsKey("ownera"));
    assertEquals(1, result.get("ownera").getOwnedLibraries().size());
    assertSame(owned, result.get("ownera").getOwnedLibraries().get(0));
    assertTrue(result.get("ownera").getSharedLibraries().isEmpty());
    assertFalse(result.containsKey("ownerb"));
    assertFalse(result.containsKey("userb"));
  }

  @Test
  void includesUserReachedOnlyThroughSharedAcl() {
    LibraryListDTO owned = library("set-1", "Library One", "OwnerA", sharedWith("UserB"));
    stubAggregate(List.of(owned));
    when(userServiceClient.getBulkUserDetails(anyList()))
        .thenReturn(Map.of("ownera", userDetails("ownera", "Alice", "Owner")));

    Map<String, UserLibrariesDTO> result = service.getLibrariesForUsers(List.of("UserB"));

    assertEquals(1, result.size());
    UserLibrariesDTO userB = result.get("userb");
    assertNotNull(userB);
    assertTrue(userB.getOwnedLibraries().isEmpty());
    assertEquals(1, userB.getSharedLibraries().size());
    assertSame(owned, userB.getSharedLibraries().get(0));
  }

  @Test
  void skipsLibrariesWithNullLibrarySet() {
    LibraryListDTO orphan = libraryWithoutSet("Orphan");
    LibraryListDTO other = library("set-2", "Library Two", "OwnerB");
    stubAggregate(List.of(orphan, other));
    when(userServiceClient.getBulkUserDetails(anyList()))
        .thenReturn(Map.of("ownerb", userDetails("ownerb", "Bob", "Owner")));

    Map<String, UserLibrariesDTO> result = service.getLibrariesForUsers(null);

    assertEquals(1, result.size());
    assertTrue(result.containsKey("ownerb"));
    verify(userServiceClient).getBulkUserDetails(List.of("ownerb"));
  }

  @Test
  void fallsBackToHarpIdWhenOwnerDetailsMissing() {
    LibraryListDTO owned = library("set-1", "Library One", "OwnerA");
    stubAggregate(List.of(owned));
    when(userServiceClient.getBulkUserDetails(anyList())).thenReturn(Map.of());

    Map<String, UserLibrariesDTO> result = service.getLibrariesForUsers(null);

    assertEquals("ownera", owned.getOwnerDisplayName());
    assertSame(owned, result.get("ownera").getOwnedLibraries().get(0));
  }

  @Test
  void doesNotCallUserServiceWhenNoLibraryHasOwner() {
    LibraryListDTO shared = library("set-1", "Library One", "", sharedWith("UserB"));
    stubAggregate(List.of(shared));

    Map<String, UserLibrariesDTO> result = service.getLibrariesForUsers(null);

    verify(userServiceClient, never()).getBulkUserDetails(any());
    assertEquals(1, result.size());
    assertEquals(1, result.get("userb").getSharedLibraries().size());
    assertTrue(result.get("userb").getOwnedLibraries().isEmpty());
  }

  @Test
  void ignoresAclsWithoutSharedWithRoleOrBlankUserId() {
    AclSpecification notShared = AclSpecification.builder().userId("UserC").roles(Set.of()).build();
    AclSpecification nullRoles = AclSpecification.builder().userId("UserD").roles(null).build();
    AclSpecification blankUser =
        AclSpecification.builder().userId(" ").roles(Set.of(RoleEnum.SHARED_WITH)).build();
    LibraryListDTO owned =
        library("set-1", "Library One", "OwnerA", notShared, nullRoles, blankUser);
    stubAggregate(List.of(owned));
    when(userServiceClient.getBulkUserDetails(anyList()))
        .thenReturn(Map.of("ownera", userDetails("ownera", "Alice", "Owner")));

    Map<String, UserLibrariesDTO> result = service.getLibrariesForUsers(null);

    assertEquals(1, result.size());
    assertTrue(result.containsKey("ownera"));
    assertFalse(result.containsKey("userc"));
    assertFalse(result.containsKey("userd"));
  }
}
