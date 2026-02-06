package com.example.cfchat.service;

import com.example.cfchat.dto.OrganizationDto;
import com.example.cfchat.dto.OrganizationRequest;
import com.example.cfchat.dto.OrganizationThemeDto;
import com.example.cfchat.model.Organization;
import com.example.cfchat.model.User;
import com.example.cfchat.repository.OrganizationRepository;
import com.example.cfchat.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrganizationService organizationService;

    @Test
    void createOrganization_validRequest_createsOrg() {
        OrganizationRequest request = OrganizationRequest.builder()
                .name("Test Org")
                .build();

        when(organizationRepository.existsByName("Test Org")).thenReturn(false);
        when(organizationRepository.existsBySlug("test-org")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(i -> {
            Organization org = i.getArgument(0);
            org.setId(UUID.randomUUID());
            return org;
        });

        Organization result = organizationService.createOrganization(request);

        assertThat(result.getName()).isEqualTo("Test Org");
        assertThat(result.getSlug()).isEqualTo("test-org");
    }

    @Test
    void createOrganization_duplicateName_throwsException() {
        OrganizationRequest request = OrganizationRequest.builder()
                .name("Existing Org")
                .build();

        when(organizationRepository.existsByName("Existing Org")).thenReturn(true);

        assertThatThrownBy(() -> organizationService.createOrganization(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createOrganization_reservedSlug_throwsException() {
        OrganizationRequest request = OrganizationRequest.builder()
                .name("Admin")
                .build();

        when(organizationRepository.existsByName("Admin")).thenReturn(false);

        assertThatThrownBy(() -> organizationService.createOrganization(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    void createOrganization_customSlug_usesProvidedSlug() {
        OrganizationRequest request = OrganizationRequest.builder()
                .name("My Org")
                .slug("custom-slug")
                .build();

        when(organizationRepository.existsByName("My Org")).thenReturn(false);
        when(organizationRepository.existsBySlug("custom-slug")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(i -> {
            Organization org = i.getArgument(0);
            org.setId(UUID.randomUUID());
            return org;
        });

        Organization result = organizationService.createOrganization(request);

        assertThat(result.getSlug()).isEqualTo("custom-slug");
    }

    @Test
    void createOrganization_withColors_usesProvidedColors() {
        OrganizationRequest request = OrganizationRequest.builder()
                .name("Colored Org")
                .primaryColor("#ff0000")
                .secondaryColor("#00ff00")
                .build();

        when(organizationRepository.existsByName("Colored Org")).thenReturn(false);
        when(organizationRepository.existsBySlug("colored-org")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(i -> {
            Organization org = i.getArgument(0);
            org.setId(UUID.randomUUID());
            return org;
        });

        Organization result = organizationService.createOrganization(request);

        assertThat(result.getPrimaryColor()).isEqualTo("#ff0000");
        assertThat(result.getSecondaryColor()).isEqualTo("#00ff00");
    }

    @Test
    void updateOrganization_validUpdate_updatesFields() {
        UUID orgId = UUID.randomUUID();
        Organization existing = Organization.builder()
                .id(orgId)
                .name("Old Name")
                .slug("old-name")
                .primaryColor("#000000")
                .build();

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(existing));
        when(organizationRepository.existsByName("New Name")).thenReturn(false);
        when(organizationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        OrganizationRequest request = OrganizationRequest.builder()
                .name("New Name")
                .primaryColor("#ff0000")
                .build();

        Organization result = organizationService.updateOrganization(orgId, request);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getPrimaryColor()).isEqualTo("#ff0000");
    }

    @Test
    void updateOrganization_notFound_throwsException() {
        UUID orgId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.empty());

        OrganizationRequest request = OrganizationRequest.builder().name("Test").build();

        assertThatThrownBy(() -> organizationService.updateOrganization(orgId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void updateOrganization_reservedSlug_throwsException() {
        UUID orgId = UUID.randomUUID();
        Organization existing = Organization.builder()
                .id(orgId)
                .name("Test")
                .slug("old-slug")
                .build();

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(existing));

        OrganizationRequest request = OrganizationRequest.builder().slug("admin").build();

        assertThatThrownBy(() -> organizationService.updateOrganization(orgId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    void getThemeForUser_nullUser_returnsDefault() {
        OrganizationThemeDto theme = organizationService.getThemeForUser(null);

        assertThat(theme.getName()).isEqualTo("Default");
        assertThat(theme.getPrimaryColor()).isEqualTo("#10a37f");
    }

    @Test
    void getThemeForUser_userWithOrg_returnsOrgTheme() {
        Organization org = Organization.builder()
                .name("My Org")
                .slug("my-org")
                .primaryColor("#ff0000")
                .secondaryColor("#1a1a1a")
                .accentColor("#10a37f")
                .textColor("#ffffff")
                .backgroundColor("#0f0f0f")
                .sidebarColor("#0f0f0f")
                .headerText("My Chat")
                .borderRadius("12px")
                .defaultTheme(Organization.Theme.DARK)
                .build();

        User user = User.builder()
                .username("testuser")
                .organization(org)
                .build();

        OrganizationThemeDto theme = organizationService.getThemeForUser(user);

        assertThat(theme.getName()).isEqualTo("My Org");
        assertThat(theme.getPrimaryColor()).isEqualTo("#ff0000");
    }

    @Test
    void getThemeForUser_userWithoutOrg_returnsDefault() {
        User user = User.builder()
                .username("testuser")
                .organization(null)
                .build();

        OrganizationThemeDto theme = organizationService.getThemeForUser(user);

        assertThat(theme.getName()).isEqualTo("Default");
    }

    @Test
    void getThemeBySlug_existing_returnsTheme() {
        Organization org = Organization.builder()
                .name("Test")
                .slug("test")
                .primaryColor("#ff0000")
                .secondaryColor("#1a1a1a")
                .accentColor("#10a37f")
                .textColor("#ffffff")
                .backgroundColor("#0f0f0f")
                .sidebarColor("#0f0f0f")
                .headerText("Chat")
                .borderRadius("12px")
                .defaultTheme(Organization.Theme.LIGHT)
                .build();

        when(organizationRepository.findBySlug("test")).thenReturn(Optional.of(org));

        OrganizationThemeDto theme = organizationService.getThemeBySlug("test");

        assertThat(theme.getPrimaryColor()).isEqualTo("#ff0000");
    }

    @Test
    void getThemeBySlug_notFound_returnsDefault() {
        when(organizationRepository.findBySlug("nonexistent")).thenReturn(Optional.empty());

        OrganizationThemeDto theme = organizationService.getThemeBySlug("nonexistent");

        assertThat(theme.getName()).isEqualTo("Default");
    }

    @Test
    void addUserToOrganization_valid_addsUser() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        User user = User.builder().id(userId).username("testuser").build();
        Organization org = Organization.builder().id(orgId).name("Test Org").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        organizationService.addUserToOrganization(userId, orgId, User.OrganizationRole.MEMBER);

        assertThat(user.getOrganization()).isEqualTo(org);
        assertThat(user.getOrganizationRole()).isEqualTo(User.OrganizationRole.MEMBER);
    }

    @Test
    void addUserToOrganization_userNotFound_throwsException() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.addUserToOrganization(userId, orgId, User.OrganizationRole.MEMBER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void removeUserFromOrganization_removesAndResets() {
        UUID userId = UUID.randomUUID();
        Organization org = Organization.builder().name("Test Org").build();
        User user = User.builder()
                .id(userId)
                .username("testuser")
                .organization(org)
                .organizationRole(User.OrganizationRole.ADMIN)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        organizationService.removeUserFromOrganization(userId);

        assertThat(user.getOrganization()).isNull();
        assertThat(user.getOrganizationRole()).isEqualTo(User.OrganizationRole.MEMBER);
    }

    @Test
    void deleteOrganization_removesMembers_thenDeletes() {
        UUID orgId = UUID.randomUUID();
        Organization org = Organization.builder().id(orgId).name("Test Org").build();

        User member = User.builder()
                .id(UUID.randomUUID())
                .username("member")
                .organization(org)
                .organizationRole(User.OrganizationRole.MEMBER)
                .build();

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(userRepository.findByOrganizationId(orgId)).thenReturn(List.of(member));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        organizationService.deleteOrganization(orgId);

        assertThat(member.getOrganization()).isNull();
        verify(organizationRepository).delete(org);
    }

    @Test
    void deleteOrganization_notFound_throwsException() {
        UUID orgId = UUID.randomUUID();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.deleteOrganization(orgId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getAllOrganizations_returnsActiveOnly() {
        Organization org = Organization.builder()
                .id(UUID.randomUUID())
                .name("Active Org")
                .slug("active-org")
                .defaultTheme(Organization.Theme.DARK)
                .active(true)
                .build();

        when(organizationRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(org));
        when(userRepository.countByOrganization(org)).thenReturn(5L);

        List<OrganizationDto> result = organizationService.getAllOrganizations();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Active Org");
        assertThat(result.get(0).getMemberCount()).isEqualTo(5L);
    }
}
