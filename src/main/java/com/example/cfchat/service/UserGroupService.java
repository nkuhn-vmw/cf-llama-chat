package com.example.cfchat.service;

import com.example.cfchat.model.User;
import com.example.cfchat.model.UserGroup;
import com.example.cfchat.repository.UserGroupRepository;
import com.example.cfchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserGroupService {

    private final UserGroupRepository userGroupRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserGroup> getAllGroups() {
        return userGroupRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<UserGroup> getGroupById(Long id) {
        return userGroupRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<UserGroup> getGroupByName(String name) {
        return userGroupRepository.findByName(name);
    }

    @Transactional
    public UserGroup createGroup(String name, String description) {
        if (userGroupRepository.existsByName(name)) {
            throw new IllegalArgumentException("Group with name '" + name + "' already exists");
        }

        UserGroup group = UserGroup.builder()
                .name(name)
                .description(description)
                .build();

        UserGroup saved = userGroupRepository.save(group);
        log.info("Created user group: {} (id: {})", name, saved.getId());
        return saved;
    }

    @Transactional
    public UserGroup updateGroup(Long id, String name, String description) {
        UserGroup group = userGroupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + id));

        if (name != null && !name.equals(group.getName())) {
            if (userGroupRepository.existsByName(name)) {
                throw new IllegalArgumentException("Group with name '" + name + "' already exists");
            }
            group.setName(name);
        }

        if (description != null) {
            group.setDescription(description);
        }

        UserGroup saved = userGroupRepository.save(group);
        log.info("Updated user group: {} (id: {})", saved.getName(), id);
        return saved;
    }

    @Transactional
    public void deleteGroup(Long id) {
        UserGroup group = userGroupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + id));

        // Clear member associations before deleting
        group.getMembers().clear();
        userGroupRepository.save(group);
        userGroupRepository.delete(group);
        log.info("Deleted user group: {} (id: {})", group.getName(), id);
    }

    @Transactional
    public UserGroup addMember(Long groupId, UUID userId) {
        UserGroup group = userGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        group.getMembers().add(user);
        UserGroup saved = userGroupRepository.save(group);
        log.info("Added user {} to group {} (id: {})", user.getUsername(), group.getName(), groupId);
        return saved;
    }

    @Transactional
    public UserGroup removeMember(Long groupId, UUID userId) {
        UserGroup group = userGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        group.getMembers().remove(user);
        UserGroup saved = userGroupRepository.save(group);
        log.info("Removed user {} from group {} (id: {})", user.getUsername(), group.getName(), groupId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Set<User> getGroupMembers(Long groupId) {
        UserGroup group = userGroupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
        return group.getMembers();
    }

    @Transactional(readOnly = true)
    public List<UserGroup> getGroupsForUser(UUID userId) {
        return userGroupRepository.findByMembersId(userId);
    }
}
