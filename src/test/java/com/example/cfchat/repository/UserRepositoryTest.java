package com.example.cfchat.repository;

import com.example.cfchat.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByUsername_existingUser_returnsUser() {
        User user = User.builder()
                .username("testuser")
                .passwordHash("hash")
                .role(User.UserRole.USER)
                .authProvider(User.AuthProvider.LOCAL)
                .build();
        entityManager.persistAndFlush(user);

        Optional<User> found = userRepository.findByUsername("testuser");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    void findByUsername_nonexistent_returnsEmpty() {
        Optional<User> found = userRepository.findByUsername("ghost");
        assertThat(found).isEmpty();
    }

    @Test
    void findByEmail_existingUser_returnsUser() {
        User user = User.builder()
                .username("emailuser")
                .email("test@example.com")
                .passwordHash("hash")
                .role(User.UserRole.USER)
                .authProvider(User.AuthProvider.LOCAL)
                .build();
        entityManager.persistAndFlush(user);

        Optional<User> found = userRepository.findByEmail("test@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void countByRole_returnsCorrectCount() {
        entityManager.persistAndFlush(User.builder()
                .username("admin1").passwordHash("h").role(User.UserRole.ADMIN).authProvider(User.AuthProvider.LOCAL).build());
        entityManager.persistAndFlush(User.builder()
                .username("user1").passwordHash("h").role(User.UserRole.USER).authProvider(User.AuthProvider.LOCAL).build());
        entityManager.persistAndFlush(User.builder()
                .username("user2").passwordHash("h").role(User.UserRole.USER).authProvider(User.AuthProvider.LOCAL).build());

        long adminCount = userRepository.countByRole(User.UserRole.ADMIN);
        long userCount = userRepository.countByRole(User.UserRole.USER);

        assertThat(adminCount).isEqualTo(1);
        assertThat(userCount).isEqualTo(2);
    }

    @Test
    void existsByUsername_existing_returnsTrue() {
        entityManager.persistAndFlush(User.builder()
                .username("existing").passwordHash("h").role(User.UserRole.USER).authProvider(User.AuthProvider.LOCAL).build());

        assertThat(userRepository.existsByUsername("existing")).isTrue();
    }

    @Test
    void existsByUsername_nonexistent_returnsFalse() {
        assertThat(userRepository.existsByUsername("nonexistent")).isFalse();
    }

    @Test
    void save_setsCreatedAt() {
        User user = User.builder()
                .username("newuser")
                .passwordHash("hash")
                .role(User.UserRole.USER)
                .authProvider(User.AuthProvider.LOCAL)
                .build();

        User saved = userRepository.save(user);
        entityManager.flush();

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getId()).isNotNull();
    }
}
