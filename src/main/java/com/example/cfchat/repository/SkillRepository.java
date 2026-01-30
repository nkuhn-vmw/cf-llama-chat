package com.example.cfchat.repository;

import com.example.cfchat.model.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID> {

    List<Skill> findByEnabled(boolean enabled);

    Optional<Skill> findByName(String name);

    boolean existsByName(String name);
}
