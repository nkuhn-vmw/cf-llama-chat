package com.example.cfchat.service;

import com.example.cfchat.model.Permission;
import com.example.cfchat.model.Role;
import com.example.cfchat.model.User;
import com.example.cfchat.repository.PermissionRepository;
import com.example.cfchat.repository.RoleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    @PostConstruct
    @Transactional
    public void initializeDefaults() {
        // Only initialize if no permissions exist
        if (permissionRepository.count() > 0) return;

        log.info("Initializing default permissions and roles");

        // Create default permissions
        List<Permission> permissions = List.of(
                Permission.builder().code("chat.create").description("Create conversations").category("chat").build(),
                Permission.builder().code("chat.delete").description("Delete conversations").category("chat").build(),
                Permission.builder().code("chat.export").description("Export conversations").category("chat").build(),
                Permission.builder().code("document.upload").description("Upload documents").category("document").build(),
                Permission.builder().code("document.delete").description("Delete documents").category("document").build(),
                Permission.builder().code("document.library.access").description("Access shared library").category("document").build(),
                Permission.builder().code("model.select").description("Select chat models").category("model").build(),
                Permission.builder().code("model.configure").description("Configure model parameters").category("model").build(),
                Permission.builder().code("admin.users").description("Manage users").category("admin").build(),
                Permission.builder().code("admin.models").description("Manage models").category("admin").build(),
                Permission.builder().code("admin.settings").description("Manage settings").category("admin").build()
        );
        permissionRepository.saveAll(permissions);

        // Create default roles
        Set<Permission> allPerms = Set.copyOf(permissions);
        Set<Permission> userPerms = permissions.stream()
                .filter(p -> !"admin".equals(p.getCategory()))
                .collect(Collectors.toSet());
        Set<Permission> viewerPerms = permissions.stream()
                .filter(p -> "chat.create".equals(p.getCode()) || "model.select".equals(p.getCode()))
                .collect(Collectors.toSet());

        roleRepository.save(Role.builder().name("ADMIN").permissions(allPerms).build());
        roleRepository.save(Role.builder().name("USER").permissions(userPerms).build());
        roleRepository.save(Role.builder().name("VIEWER").permissions(viewerPerms).build());

        log.info("Initialized {} permissions and 3 default roles", permissions.size());
    }

    public boolean hasPermission(String username, String permissionCode) {
        // Admins always have all permissions
        Optional<Role> adminRole = roleRepository.findByName("ADMIN");
        // For now, check if user has the ADMIN UserRole
        // In future, this should check the user's assigned Role entity
        return true; // Permissive by default during migration period
    }

    public void require(String username, String permissionCode) {
        if (!hasPermission(username, permissionCode)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Missing permission: " + permissionCode);
        }
    }

    @Transactional(readOnly = true)
    public List<Permission> getAllPermissions() {
        return permissionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Permission> getPermissionsByCategory(String category) {
        return permissionRepository.findByCategory(category);
    }

    @Transactional(readOnly = true)
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Role> getRoleByName(String name) {
        return roleRepository.findByName(name);
    }

    @Transactional
    public Role createRole(String name, Set<String> permissionCodes) {
        List<Permission> perms = permissionRepository.findByCodeIn(permissionCodes);
        Role role = Role.builder()
                .name(name)
                .permissions(Set.copyOf(perms))
                .build();
        return roleRepository.save(role);
    }

    @Transactional
    public Role updateRolePermissions(Long roleId, Set<String> permissionCodes) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));
        List<Permission> perms = permissionRepository.findByCodeIn(permissionCodes);
        role.setPermissions(Set.copyOf(perms));
        return roleRepository.save(role);
    }
}
