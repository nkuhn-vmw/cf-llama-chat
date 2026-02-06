package com.example.cfchat.auth;

import com.example.cfchat.model.User;
import com.example.cfchat.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "defaultAdminUsername", "admin");
        ReflectionTestUtils.setField(userService, "defaultAdminPassword", "password123");
    }

    @Test
    void createDefaultAdminIfNeeded_noUsers_createsAdmin() {
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        userService.createDefaultAdminIfNeeded();

        verify(userRepository).save(argThat(user ->
                user.getUsername().equals("admin") &&
                user.getRole() == User.UserRole.ADMIN
        ));
    }

    @Test
    void createDefaultAdminIfNeeded_usersExist_doesNothing() {
        when(userRepository.count()).thenReturn(5L);

        userService.createDefaultAdminIfNeeded();

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerUser_newUser_createsUser() {
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(1L); // Not first user
        when(passwordEncoder.encode("password")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        User result = userService.registerUser("newuser", "password", "test@test.com", "New User");

        assertThat(result.getUsername()).isEqualTo("newuser");
        assertThat(result.getRole()).isEqualTo(User.UserRole.USER);
        assertThat(result.getAuthProvider()).isEqualTo(User.AuthProvider.LOCAL);
    }

    @Test
    void registerUser_firstUser_becomesAdmin() {
        when(userRepository.findByUsername("firstuser")).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(0L); // First user
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        User result = userService.registerUser("firstuser", "password", null, null);

        assertThat(result.getRole()).isEqualTo(User.UserRole.ADMIN);
    }

    @Test
    void registerUser_existingUsername_throwsException() {
        when(userRepository.findByUsername("existing")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> userService.registerUser("existing", "pass", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username already exists");
    }

    @Test
    void registerUser_existingEmail_throwsException() {
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("taken@test.com")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> userService.registerUser("newuser", "pass", "taken@test.com", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email already registered");
    }

    @Test
    void authenticateUser_validCredentials_returnsUser() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashedpw")
                .authProvider(User.AuthProvider.LOCAL)
                .role(User.UserRole.USER)
                .build();
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashedpw")).thenReturn(true);
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Optional<User> result = userService.authenticateUser("testuser", "password");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    void authenticateUser_wrongPassword_returnsEmpty() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hashedpw")
                .authProvider(User.AuthProvider.LOCAL)
                .build();
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashedpw")).thenReturn(false);

        Optional<User> result = userService.authenticateUser("testuser", "wrong");

        assertThat(result).isEmpty();
    }

    @Test
    void authenticateUser_nonexistentUser_returnsEmpty() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        Optional<User> result = userService.authenticateUser("ghost", "password");

        assertThat(result).isEmpty();
    }

    @Test
    void authenticateUser_ssoUser_returnsEmpty() {
        User ssoUser = User.builder()
                .username("ssouser")
                .authProvider(User.AuthProvider.SSO)
                .build();
        when(userRepository.findByUsername("ssouser")).thenReturn(Optional.of(ssoUser));

        Optional<User> result = userService.authenticateUser("ssouser", "password");

        assertThat(result).isEmpty();
    }

    @Test
    void authenticateUser_legacyUserNoPassword_deniesLogin() {
        User legacyUser = User.builder()
                .username("legacy")
                .passwordHash(null)
                .authProvider(User.AuthProvider.LOCAL)
                .build();
        when(userRepository.findByUsername("legacy")).thenReturn(Optional.of(legacyUser));

        Optional<User> result = userService.authenticateUser("legacy", "newpass");

        assertThat(result).isEmpty();
    }

    @Test
    void isUsernameAvailable_available_returnsTrue() {
        when(userRepository.findByUsername("available")).thenReturn(Optional.empty());
        assertThat(userService.isUsernameAvailable("available")).isTrue();
    }

    @Test
    void isUsernameAvailable_taken_returnsFalse() {
        when(userRepository.findByUsername("taken")).thenReturn(Optional.of(new User()));
        assertThat(userService.isUsernameAvailable("taken")).isFalse();
    }

    @Test
    void isEmailAvailable_null_returnsTrue() {
        assertThat(userService.isEmailAvailable(null)).isTrue();
    }

    @Test
    void isEmailAvailable_blank_returnsTrue() {
        assertThat(userService.isEmailAvailable("  ")).isTrue();
    }

    @Test
    void getOrCreateUser_existingUser_updatesLastLogin() {
        User existing = User.builder()
                .id(UUID.randomUUID())
                .username("ssouser")
                .role(User.UserRole.USER)
                .authProvider(User.AuthProvider.SSO)
                .build();
        when(userRepository.findByUsername("ssouser")).thenReturn(Optional.of(existing));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User result = userService.getOrCreateUser("ssouser", "email@test.com", "Display", User.AuthProvider.SSO);

        assertThat(result.getLastLoginAt()).isNotNull();
        verify(userRepository, never()).count();
    }

    @Test
    void getOrCreateUser_newUser_createsWithCorrectRole() {
        when(userRepository.findByUsername("newsso")).thenReturn(Optional.empty());
        when(userRepository.count()).thenReturn(5L);
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        User result = userService.getOrCreateUser("newsso", "email@test.com", "Name", User.AuthProvider.SSO);

        assertThat(result.getRole()).isEqualTo(User.UserRole.USER);
        assertThat(result.getAuthProvider()).isEqualTo(User.AuthProvider.SSO);
    }

    @Test
    void canChangePassword_localUser_returnsTrue() {
        User localUser = User.builder().authProvider(User.AuthProvider.LOCAL).build();
        assertThat(userService.canChangePassword(localUser)).isTrue();
    }

    @Test
    void canChangePassword_ssoUser_returnsFalse() {
        User ssoUser = User.builder().authProvider(User.AuthProvider.SSO).build();
        assertThat(userService.canChangePassword(ssoUser)).isFalse();
    }

    @Test
    void canChangePassword_null_returnsFalse() {
        assertThat(userService.canChangePassword(null)).isFalse();
    }

    @Test
    void getCurrentUser_anonymous_returnsEmpty() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("anonymousUser");

        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        Optional<User> result = userService.getCurrentUser();
        assertThat(result).isEmpty();

        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_noAuth_returnsEmpty() {
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(null);
        SecurityContextHolder.setContext(context);

        Optional<User> result = userService.getCurrentUser();
        assertThat(result).isEmpty();

        SecurityContextHolder.clearContext();
    }

    @Test
    void updateUserRole_validUser_updatesRole() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).role(User.UserRole.USER).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User result = userService.updateUserRole(userId, User.UserRole.ADMIN);

        assertThat(result.getRole()).isEqualTo(User.UserRole.ADMIN);
    }

    @Test
    void updateUserRole_nonexistentUser_throwsException() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUserRole(userId, User.UserRole.ADMIN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetUserPassword_ssoUser_returnsFalse() {
        UUID userId = UUID.randomUUID();
        User ssoUser = User.builder().id(userId).authProvider(User.AuthProvider.SSO).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(ssoUser));

        assertThat(userService.resetUserPassword(userId, "newpass")).isFalse();
    }

    @Test
    void resetUserPassword_localUser_returnsTrue() {
        UUID userId = UUID.randomUUID();
        User localUser = User.builder().id(userId).authProvider(User.AuthProvider.LOCAL).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(localUser));
        when(passwordEncoder.encode("newpass")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThat(userService.resetUserPassword(userId, "newpass")).isTrue();
    }
}
