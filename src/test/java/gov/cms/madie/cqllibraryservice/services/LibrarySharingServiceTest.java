package gov.cms.madie.cqllibraryservice.services;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import gov.cms.madie.cqllibraryservice.dto.SharedUser;
import gov.cms.madie.cqllibraryservice.exceptions.ResourceNotFoundException;
import gov.cms.madie.cqllibraryservice.repositories.CqlLibraryRepository;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.AccessControlAction;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.common.LibrarySetActionLog;
import gov.cms.madie.models.dto.UserDetailsDto;
import gov.cms.madie.models.library.CqlLibrary;
import gov.cms.madie.models.library.LibrarySet;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LibrarySharingServiceTest {

  @InjectMocks private LibrarySharingService librarySharingService;

  @Mock private CqlLibraryRepository cqlLibraryRepository;
  @Mock private LibrarySetService librarySetService;
  @Mock private ActionLogService actionLogService;
  @Mock private UserServiceClient userServiceClient;
  @Mock private CqlLibraryAccessControlService cqlLibraryAccessControlService;

  private final String USERNAME = "testUserName";
  private final String ACCESSTOKEN = "accessToken";

  @Test
  public void testGetSharedLibrariesWithNoLibraryFound() {
    String libraryId1 = "libraryId1";
    when(cqlLibraryRepository.findById(libraryId1)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> librarySharingService.getSharedLibraries(List.of(libraryId1), USERNAME));
  }

  @Test
  public void testGetSharedLibrariesWithNoLibrarySetFound() {
    CqlLibrary library1 =
        CqlLibrary.builder().id("libraryId1").librarySetId("librarySetId1").build();

    when(cqlLibraryRepository.findById("libraryId1")).thenReturn(Optional.of(library1));
    when(librarySetService.findByLibrarySetId("librarySetId1")).thenReturn(null);

    assertThrows(
        ResourceNotFoundException.class,
        () -> librarySharingService.getSharedLibraries(List.of("libraryId1"), USERNAME));
  }

  @Test
  public void testGetSharedLibrariesLibrarySetAclsNull() {
    String libraryId = "libraryId";
    LibrarySet librarySet = LibrarySet.builder().librarySetId("librarySetId").build();
    CqlLibrary library = CqlLibrary.builder().id(libraryId).librarySetId("librarySetId").build();

    when(cqlLibraryRepository.findById(libraryId)).thenReturn(Optional.of(library));
    when(librarySetService.findByLibrarySetId("librarySetId")).thenReturn(librarySet);

    Map<String, List<SharedUser>> result =
        librarySharingService.getSharedLibraries(List.of(libraryId), "test-okta");

    assertNotNull(result);
    assertTrue(result.containsKey(libraryId));
    assertTrue(result.get(libraryId).isEmpty());
  }

  @Test
  public void testGetSharedLibrariesWithNoLibrarySetAclsFoundForOneLibrary() {
    AclSpecification aclSpec = new AclSpecification();
    aclSpec.setUserId("john");
    aclSpec.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });

    LibrarySet librarySet1 =
        LibrarySet.builder()
            .librarySetId("librarySetId1")
            .owner("testUser")
            .acls(
                new ArrayList<>() {
                  {
                    add(aclSpec);
                  }
                })
            .build();

    String libraryId1 = "libraryId1";
    CqlLibrary library1 = CqlLibrary.builder().id(libraryId1).librarySetId("librarySetId1").build();

    LibrarySet librarySet2 =
        LibrarySet.builder().librarySetId("librarySetId2").owner("testUser").build();

    String libraryId2 = "libraryId2";
    CqlLibrary library2 = CqlLibrary.builder().id(libraryId2).librarySetId("librarySetId2").build();

    List<String> libraryIds = List.of(libraryId1, libraryId2);

    when(cqlLibraryRepository.findById(libraryId1)).thenReturn(Optional.of(library1));
    when(librarySetService.findByLibrarySetId("librarySetId1")).thenReturn(librarySet1);
    when(cqlLibraryRepository.findById(libraryId2)).thenReturn(Optional.of(library2));
    when(librarySetService.findByLibrarySetId("librarySetId2")).thenReturn(librarySet2);
    when(userServiceClient.getBulkUserDetails(anyList()))
        .thenReturn(
            Map.of(
                aclSpec.getUserId(),
                UserDetailsDto.builder().firstName("John").lastName("Doe").build()));
    when(librarySetService.formatDisplayName(any(), eq(aclSpec.getUserId())))
        .thenReturn("John Doe (" + aclSpec.getUserId() + ")");

    Map<String, List<SharedUser>> sharedLibraries =
        librarySharingService.getSharedLibraries(libraryIds, USERNAME);

    assertThat(sharedLibraries.size(), is(equalTo(2)));

    assertTrue(sharedLibraries.containsKey(libraryId1));
    assertThat(sharedLibraries.get(libraryId1).size(), is(equalTo(1)));
    assertThat(
        sharedLibraries.get(libraryId1).get(0).getUserId(), is(equalTo(aclSpec.getUserId())));
    assertThat(
        sharedLibraries.get(libraryId1).get(0).getDisplayName(),
        is(equalTo("John Doe (" + aclSpec.getUserId() + ")")));

    assertTrue(sharedLibraries.containsKey(libraryId2));
    assertThat(sharedLibraries.get(libraryId2).size(), is(equalTo(0)));
  }

  @Test
  public void testGetSharedLibraries() {
    AclSpecification aclSpec1 = new AclSpecification();
    aclSpec1.setUserId("userId1");
    aclSpec1.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });

    AclSpecification aclSpec2 = new AclSpecification();
    aclSpec2.setUserId("userId2");
    aclSpec2.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });

    LibrarySet librarySet1 =
        LibrarySet.builder()
            .librarySetId("librarySetId1")
            .owner("testUser")
            .acls(List.of(aclSpec2, aclSpec1))
            .build();

    LibrarySet librarySet2 =
        LibrarySet.builder()
            .librarySetId("librarySetId2")
            .owner("testUser")
            .acls(
                new ArrayList<>() {
                  {
                    add(aclSpec1);
                  }
                })
            .build();

    String libraryId1 = "libraryId1";
    CqlLibrary library1 = CqlLibrary.builder().id(libraryId1).librarySetId("librarySetId1").build();

    String libraryId2 = "libraryId2";
    CqlLibrary library2 = CqlLibrary.builder().id(libraryId2).librarySetId("librarySetId2").build();

    Instant fixedInstant = Instant.parse("2025-03-17T10:00:00Z");
    Clock fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

    LibrarySetActionLog librarySetActionLog =
        LibrarySetActionLog.builder()
            .actions(
                new ArrayList<>(
                    List.of(
                        AccessControlAction.builder()
                            .sharedWith(aclSpec1.getUserId())
                            .actionType(ActionType.SHARED)
                            .performedAt(fixedClock.instant())
                            .performedBy("performedByUserId")
                            .build())))
            .build();

    List<String> libraryIds = List.of(libraryId1, libraryId2);

    when(cqlLibraryRepository.findById(libraryId1)).thenReturn(Optional.of(library1));
    when(librarySetService.findByLibrarySetId("librarySetId1")).thenReturn(librarySet1);
    when(cqlLibraryRepository.findById(libraryId2)).thenReturn(Optional.of(library2));
    when(librarySetService.findByLibrarySetId("librarySetId2")).thenReturn(librarySet2);
    when(actionLogService.findLibrarySetActionLogByTargetId(anyString()))
        .thenReturn(librarySetActionLog);
    when(userServiceClient.getBulkUserDetails(anyList()))
        .thenReturn(
            Map.of(
                aclSpec1.getUserId(),
                UserDetailsDto.builder().firstName("John").lastName("Doe").build(),
                aclSpec2.getUserId(),
                UserDetailsDto.builder().firstName("Jane").lastName("Doe").build()));
    when(librarySetService.formatDisplayName(any(), eq(aclSpec1.getUserId())))
        .thenReturn("John Doe (" + aclSpec1.getUserId() + ")");
    when(librarySetService.formatDisplayName(any(), eq(aclSpec2.getUserId())))
        .thenReturn("Jane Doe (" + aclSpec2.getUserId() + ")");

    Map<String, List<SharedUser>> sharedLibraries =
        librarySharingService.getSharedLibraries(libraryIds, USERNAME);

    assertThat(sharedLibraries.size(), is(equalTo(2)));

    assertTrue(sharedLibraries.containsKey(libraryId1));
    assertThat(sharedLibraries.get(libraryId1).size(), is(equalTo(2)));
    assertThat(
        sharedLibraries.get(libraryId1).get(0).getUserId(), is(equalTo(aclSpec2.getUserId())));
    assertThat(sharedLibraries.get(libraryId1).get(0).getPerformedAt(), is(equalTo(null)));
    assertThat(
        sharedLibraries.get(libraryId1).get(0).getDisplayName(),
        is(equalTo("Jane Doe (" + aclSpec2.getUserId() + ")")));
    assertThat(
        sharedLibraries.get(libraryId1).get(1).getUserId(), is(equalTo(aclSpec1.getUserId())));
    assertThat(
        sharedLibraries.get(libraryId1).get(1).getDisplayName(),
        is(equalTo("John Doe (" + aclSpec1.getUserId() + ")")));

    assertTrue(sharedLibraries.containsKey(libraryId2));
    assertThat(sharedLibraries.get(libraryId2).size(), is(equalTo(1)));
    assertThat(
        sharedLibraries.get(libraryId2).get(0).getUserId(), is(equalTo(aclSpec1.getUserId())));
  }

  @Test
  public void testShareLibrariesWithNoLibraryFound() {
    Map<String, List<String>> libraries = new HashMap<>();
    String libraryId1 = "libraryId1";
    libraries.put(libraryId1, List.of("userId1", "userId2"));

    when(cqlLibraryRepository.findById(eq(libraryId1))).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> librarySharingService.shareLibraries(libraries, "userName", ACCESSTOKEN));
  }

  @Test
  public void testShareLibraries() {
    Map<String, List<String>> libraries = new HashMap<>();

    AclSpecification aclSpec1 = new AclSpecification();
    aclSpec1.setUserId("testUser");
    aclSpec1.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });

    AclSpecification aclSpec2 = new AclSpecification();
    aclSpec2.setUserId("userId2");
    aclSpec2.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });

    LibrarySet librarySet1 =
        LibrarySet.builder()
            .librarySetId("librarySetId1")
            .owner("testUser")
            .acls(List.of(aclSpec2, aclSpec1))
            .build();

    LibrarySet librarySet2 =
        LibrarySet.builder()
            .librarySetId("librarySetId1")
            .owner("testUser")
            .acls(List.of(aclSpec2, aclSpec1))
            .build();

    String libraryId1 = "libraryId1";
    CqlLibrary library1 =
        CqlLibrary.builder().id(libraryId1).librarySetId(librarySet1.getLibrarySetId()).build();

    String libraryId2 = "libraryId2";
    CqlLibrary library2 =
        CqlLibrary.builder().id(libraryId2).librarySetId(librarySet2.getLibrarySetId()).build();

    libraries.put(libraryId1, List.of("testUser", "userId2"));
    libraries.put(libraryId2, List.of("userId2"));

    when(cqlLibraryRepository.findById(libraryId1)).thenReturn(Optional.of(library1));
    when(cqlLibraryRepository.findById(libraryId2)).thenReturn(Optional.of(library2));
    when(librarySetService.findByLibrarySetId("librarySetId1"))
        .thenReturn(librarySet1)
        .thenReturn(librarySet2);
    when(librarySetService.updateLibrarySetAcls(any(), any(), any(), any(Boolean.class)))
        .thenReturn(librarySet1);

    Map<String, List<AclSpecification>> result =
        librarySharingService.shareLibraries(libraries, "testUser", ACCESSTOKEN);

    assertThat(result.size(), is(equalTo(2)));
    assertTrue(result.containsKey(libraryId1));
    assertTrue(result.containsKey(libraryId2));
    assertThat(result.get(libraryId1), is(equalTo(List.of(aclSpec2, aclSpec1))));
    assertThat(result.get(libraryId2), is(equalTo(List.of(aclSpec2, aclSpec1))));
  }

  @Test
  public void testShareLibrariesByNonAdminUser() {
    Map<String, List<String>> libraries = new HashMap<>();

    AclSpecification aclSpec1 = new AclSpecification();
    aclSpec1.setUserId("testUser");
    aclSpec1.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });

    LibrarySet librarySet1 =
        LibrarySet.builder()
            .librarySetId("librarySetId1")
            .owner("testUser")
            .acls(List.of(aclSpec1))
            .build();

    String libraryId1 = "libraryId1";
    CqlLibrary library1 =
        CqlLibrary.builder().id(libraryId1).librarySetId(librarySet1.getLibrarySetId()).build();

    libraries.put(libraryId1, List.of("testUser", "userId2"));

    when(cqlLibraryRepository.findById(libraryId1)).thenReturn(Optional.of(library1));
    when(librarySetService.findByLibrarySetId("librarySetId1")).thenReturn(librarySet1);
    when(librarySetService.updateLibrarySetAcls(any(), any(), any(), any(Boolean.class)))
        .thenReturn(librarySet1);
    when(cqlLibraryAccessControlService.hasAdminRole(anyString(), anyString())).thenReturn(false);

    Map<String, List<AclSpecification>> result =
        librarySharingService.shareLibraries(libraries, "testUser", ACCESSTOKEN);

    assertThat(result.size(), is(equalTo(1)));
    assertThat(result.get(libraryId1), is(equalTo(List.of(aclSpec1))));
  }

  @Test
  public void testUnshareLibraries() {
    Map<String, List<String>> libraries = new HashMap<>();

    AclSpecification aclSpec1 = new AclSpecification();
    aclSpec1.setUserId("testUser");
    aclSpec1.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });

    LibrarySet librarySet1 =
        LibrarySet.builder()
            .librarySetId("librarySetId1")
            .owner("testUser")
            .acls(List.of(aclSpec1))
            .build();

    String libraryId1 = "libraryId1";
    CqlLibrary library1 =
        CqlLibrary.builder().id(libraryId1).librarySetId(librarySet1.getLibrarySetId()).build();

    libraries.put(libraryId1, List.of("testUser", "userId2"));

    when(cqlLibraryRepository.findById(libraryId1)).thenReturn(Optional.of(library1));
    when(librarySetService.findByLibrarySetId("librarySetId1")).thenReturn(librarySet1);
    when(librarySetService.updateLibrarySetAcls(any(), any(), any(), any(Boolean.class)))
        .thenReturn(librarySet1);
    when(cqlLibraryAccessControlService.hasAdminRole(anyString(), anyString())).thenReturn(true);

    AclSpecification aclSpecification1 =
        AclSpecification.builder().userId("testUser").roles(Set.of(RoleEnum.SHARED_WITH)).build();

    Map<String, List<AclSpecification>> result =
        librarySharingService.unshareLibraries(libraries, "testUser", ACCESSTOKEN);

    assertThat(result.size(), is(equalTo(1)));
    assertThat(result.get(libraryId1), is(equalTo(List.of(aclSpecification1))));
  }

  @Test
  public void testUnshareLibrariesByAdminUser() {
    Map<String, List<String>> libraries = new HashMap<>();

    AclSpecification aclSpec1 = new AclSpecification();
    aclSpec1.setUserId("testUser");
    aclSpec1.setRoles(
        new HashSet<>() {
          {
            add(RoleEnum.SHARED_WITH);
          }
        });

    LibrarySet librarySet1 =
        LibrarySet.builder()
            .librarySetId("librarySetId1")
            .owner("testUser")
            .acls(List.of(aclSpec1))
            .build();

    String libraryId1 = "libraryId1";
    CqlLibrary library1 =
        CqlLibrary.builder().id(libraryId1).librarySetId(librarySet1.getLibrarySetId()).build();

    libraries.put(libraryId1, List.of("testUser", "userId2"));

    when(cqlLibraryRepository.findById(libraryId1)).thenReturn(Optional.of(library1));
    when(librarySetService.findByLibrarySetId("librarySetId1")).thenReturn(librarySet1);
    when(librarySetService.updateLibrarySetAcls(any(), any(), any(), any(Boolean.class)))
        .thenReturn(librarySet1);
    when(cqlLibraryAccessControlService.hasAdminRole(anyString(), anyString())).thenReturn(true);

    AclSpecification aclSpecification1 =
        AclSpecification.builder().userId("testUser").roles(Set.of(RoleEnum.SHARED_WITH)).build();

    Map<String, List<AclSpecification>> result =
        librarySharingService.unshareLibraries(libraries, "testUser", ACCESSTOKEN);

    assertThat(result.size(), is(equalTo(1)));
    assertThat(result.get(libraryId1), is(equalTo(List.of(aclSpecification1))));
  }
}
