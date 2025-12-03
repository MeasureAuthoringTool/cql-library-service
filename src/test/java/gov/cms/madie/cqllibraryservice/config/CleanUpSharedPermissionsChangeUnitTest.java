package gov.cms.madie.cqllibraryservice.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.cms.madie.cqllibraryservice.repositories.LibrarySetRepository;
import gov.cms.madie.cqllibraryservice.services.ActionLogService;
import gov.cms.madie.models.access.AclSpecification;
import gov.cms.madie.models.access.RoleEnum;
import gov.cms.madie.models.common.ActionType;
import gov.cms.madie.models.library.LibrarySet;

@ExtendWith(MockitoExtension.class)
public class CleanUpSharedPermissionsChangeUnitTest {
  @Mock private LibrarySetRepository librarySetRepository;
  @Mock private ActionLogService actionLogService;
  @InjectMocks private CleanUpSharedPermissionsChangeUnit changeUnit;

  private LibrarySet librarySet1;
  private LibrarySet librarySet2;
  private LibrarySet librarySet3;

  private RoleEnum role = RoleEnum.SHARED_WITH;
  private final String USER1 = "testCreatedBy1";
  private final String USER2 = "testCreatedBy2";
  private AclSpecification aclSpecification1 =
      AclSpecification.builder().userId(USER1).roles(new HashSet<>(Set.of(role))).build();
  private AclSpecification aclSpecification2 =
      AclSpecification.builder().userId(USER2).roles(new HashSet<>(Set.of(role))).build();
  private AclSpecification aclSpecification3 =
      AclSpecification.builder().userId(USER1).roles(new HashSet<>(Set.of(role))).build();
  private AclSpecification aclSpecification4 =
      AclSpecification.builder().userId(USER1).roles(new HashSet<>(Set.of(role))).build();
  private AclSpecification aclSpecification5 =
      AclSpecification.builder().userId("differentUser").roles(new HashSet<>(Set.of(role))).build();

  @BeforeEach
  public void setUp() {

    librarySet1 =
        LibrarySet.builder()
            .librarySetId("testCqlLibrarySetId1")
            .owner(USER1)
            .acls(new ArrayList<>(List.of(aclSpecification1, aclSpecification2)))
            .build();
    librarySet2 =
        LibrarySet.builder()
            .librarySetId("testCqlLibrarySetId2")
            .owner(USER1)
            .acls(new ArrayList<>(List.of(aclSpecification3, aclSpecification4)))
            .build();
    librarySet3 =
        LibrarySet.builder()
            .librarySetId("testCqlLibrarySetId3")
            .owner(USER1)
            .acls(new ArrayList<>(List.of(aclSpecification5)))
            .build();
  }

  @Test
  public void testCleanUpSharedPermissionsForOwners() {
    when(librarySetRepository.findAll()).thenReturn(List.of(librarySet1, librarySet2, librarySet3));

    List<LibrarySet> updatedLibrarySets =
        changeUnit.cleanUpSharedPermissionsForOwners(librarySetRepository);

    verify(librarySetRepository, new Times(1)).findAll();
    assertEquals(2, updatedLibrarySets.size());
    verify(actionLogService, times(2))
        .logShareAccessControlAction(
            anyString(), any(ActionType.class), anyString(), anyString(), anyString());
    // verify(librarySetRepository, times(1)).saveAll(any(List.class));
    verify(librarySetRepository, times(2)).save(any(LibrarySet.class));
  }

  @Test
  public void testCleanUpSharedPermissionsForOwnersNoAcls() {
    librarySet3.setAcls(Collections.emptyList());
    when(librarySetRepository.findAll()).thenReturn(List.of(librarySet3));

    List<LibrarySet> updatedLibrarySets =
        changeUnit.cleanUpSharedPermissionsForOwners(librarySetRepository);

    verify(librarySetRepository, new Times(1)).findAll();
    assertEquals(0, updatedLibrarySets.size());
    verify(actionLogService, times(0))
        .logShareAccessControlAction(
            anyString(), any(ActionType.class), anyString(), anyString(), anyString());
    // verify(librarySetRepository, times(1)).saveAll(any(List.class));
    verify(librarySetRepository, times(0)).save(any(LibrarySet.class));
  }

  @Test
  public void testRollbackExecution() {
    when(librarySetRepository.findAll()).thenReturn(List.of(librarySet1, librarySet2, librarySet3));
    changeUnit.cleanUpSharedPermissionsForOwners(librarySetRepository);

    changeUnit.rollbackExecution(librarySetRepository);

    verify(librarySetRepository, times(1)).deleteAll();
    verify(librarySetRepository, times(1)).saveAll(any(List.class));
  }
}
