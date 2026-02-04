package com.example.cfchat.repository;

import com.example.cfchat.model.ExternalBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExternalBindingRepository extends JpaRepository<ExternalBinding, UUID> {

    List<ExternalBinding> findByEnabled(boolean enabled);

    Optional<ExternalBinding> findByName(String name);

    boolean existsByName(String name);
}
