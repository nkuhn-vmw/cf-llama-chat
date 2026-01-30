package com.example.cfchat.repository;

import com.example.cfchat.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlug(String slug);

    Optional<Organization> findBySlugAndActiveTrue(String slug);

    Optional<Organization> findByName(String name);

    boolean existsBySlug(String slug);

    boolean existsByName(String name);

    List<Organization> findByActiveTrue();

    List<Organization> findByActiveTrueOrderByNameAsc();
}
