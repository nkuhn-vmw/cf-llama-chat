package com.example.cfchat.auth;

import com.example.cfchat.model.User;
import com.example.cfchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Register a new local user with username and password.
     */
    @Transactional
    public User registerUser(String username, String password, String email, String displayName) {
        log.info("Registering new user: {}", username);

        // Check if username already exists
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        // Check if email already exists (if provided)
        if (email != null && !email.isBlank() && userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        // Determine role - first user becomes admin
        long userCount = userRepository.count();
        User.UserRole role = userCount == 0 ? User.UserRole.ADMIN : User.UserRole.USER;

        User newUser = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .email(email != null && !email.isBlank() ? email : null)
                .displayName(displayName != null && !displayName.isBlank() ? displayName : username)
                .role(role)
                .authProvider(User.AuthProvider.LOCAL)
                .build();

        User savedUser = userRepository.save(newUser);
        log.info("Registered new user: {} with role: {} (id: {})", username, role, savedUser.getId());
        return savedUser;
    }

    /**
     * Authenticate a local user with username and password.
     */
    @Transactional
    public Optional<User> authenticateUser(String username, String password) {
        log.debug("Authenticating user: {}", username);

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            log.debug("User not found: {}", username);
            return Optional.empty();
        }

        User user = userOpt.get();

        // SSO users cannot login with password
        if (user.getAuthProvider() == User.AuthProvider.SSO) {
            log.debug("User {} is SSO user, cannot authenticate with password", username);
            return Optional.empty();
        }

        // Legacy user without password - set password on first login
        if (user.getPasswordHash() == null) {
            log.info("Legacy user {} logging in for first time - setting password", username);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("Password set for legacy user: {}", username);
            return Optional.of(user);
        }

        // Check password
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.debug("Invalid password for user: {}", username);
            return Optional.empty();
        }

        // Update last login
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("User authenticated: {} with role: {}", username, user.getRole());
        return Optional.of(user);
    }

    /**
     * Check if a username is available.
     */
    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String username) {
        return userRepository.findByUsername(username).isEmpty();
    }

    /**
     * Check if an email is available.
     */
    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        if (email == null || email.isBlank()) {
            return true;
        }
        return userRepository.findByEmail(email).isEmpty();
    }

    /**
     * Get or create user for SSO login.
     */
    @Transactional
    public User getOrCreateUser(String username, String email, String displayName, User.AuthProvider provider) {
        log.debug("getOrCreateUser called - username: {}, provider: {}", username, provider);

        Optional<User> existingUser = userRepository.findByUsername(username);

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            log.debug("Found existing user: {} (id: {}, role: {})", username, user.getId(), user.getRole());
            user.setLastLoginAt(LocalDateTime.now());
            if (email != null && user.getEmail() == null) {
                user.setEmail(email);
            }
            if (displayName != null && user.getDisplayName() == null) {
                user.setDisplayName(displayName);
            }
            return userRepository.save(user);
        }

        // Determine role - first user becomes admin
        long userCount = userRepository.count();
        User.UserRole role = userCount == 0 ? User.UserRole.ADMIN : User.UserRole.USER;
        log.info("Creating new user: {} (current user count: {}, assigning role: {})", username, userCount, role);

        User newUser = User.builder()
                .username(username)
                .email(email)
                .displayName(displayName != null ? displayName : username)
                .role(role)
                .authProvider(provider)
                .build();

        User savedUser = userRepository.save(newUser);
        log.info("Created new user: {} with role: {} (id: {})", username, role, savedUser.getId());
        return savedUser;
    }

    @Transactional(readOnly = true)
    public Optional<User> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }

        String username = extractUsername(auth);
        return userRepository.findByUsername(username);
    }

    public String extractUsername(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof OAuth2User oauth2User) {
            // Try common attribute names for username
            String username = oauth2User.getAttribute("preferred_username");
            if (username == null) {
                username = oauth2User.getAttribute("email");
            }
            if (username == null) {
                username = oauth2User.getAttribute("sub");
            }
            return username != null ? username : auth.getName();
        }
        return auth.getName();
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserById(UUID id) {
        return userRepository.findById(id);
    }

    @Transactional
    public User updateUserRole(UUID userId, User.UserRole newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setRole(newRole);
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(UUID userId) {
        userRepository.deleteById(userId);
        log.info("Deleted user: {}", userId);
    }

    @Transactional(readOnly = true)
    public long getUserCount() {
        return userRepository.count();
    }

    @Transactional(readOnly = true)
    public long getAdminCount() {
        return userRepository.countByRole(User.UserRole.ADMIN);
    }
}
