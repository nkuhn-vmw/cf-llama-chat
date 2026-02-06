package com.example.cfchat.repository;

import com.example.cfchat.model.Organization;
import com.example.cfchat.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    long countByRole(User.UserRole role);

    List<User> findByOrganization(Organization organization);

    List<User> findByOrganizationId(UUID organizationId);

    long countByOrganization(Organization organization);

    @Query("SELECT u.organization.id, COUNT(u) FROM User u WHERE u.organization IS NOT NULL GROUP BY u.organization.id")
    List<Object[]> countUsersGroupByOrganization();

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.organization WHERE u.username = :username")
    Optional<User> findByUsernameWithOrganization(@Param("username") String username);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.organization WHERE u.id = :id")
    Optional<User> findByIdWithOrganization(@Param("id") UUID id);
}
