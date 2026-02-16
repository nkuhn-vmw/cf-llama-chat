package com.example.cfchat.controller;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SCIM 2.0 provisioning endpoints (RFC 7644).
 * Supports automated user lifecycle management from identity providers
 * such as Okta, Azure AD, and Google Workspace.
 */
@RestController
@RequestMapping("/scim/v2")
@ConditionalOnProperty(name = "auth.scim.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ScimController {

    private static final String SCIM_MEDIA_TYPE = "application/scim+json";
    private static final String SCHEMA_USER = "urn:ietf:params:scim:schemas:core:2.0:User";
    private static final String SCHEMA_LIST_RESPONSE = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
    private static final String SCHEMA_ERROR = "urn:ietf:params:scim:api:messages:2.0:Error";
    private static final String SCHEMA_PATCH_OP = "urn:ietf:params:scim:api:messages:2.0:PatchOp";

    private final UserService userService;

    /**
     * GET /scim/v2/Users - List/search users.
     * Supports filter parameter with "userName eq" syntax.
     */
    @GetMapping(value = "/Users", produces = SCIM_MEDIA_TYPE)
    public ResponseEntity<Map<String, Object>> listUsers(
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "1") int startIndex,
            @RequestParam(defaultValue = "100") int count) {

        log.debug("SCIM listUsers - filter: {}, startIndex: {}, count: {}", filter, startIndex, count);

        List<User> users;

        if (filter != null && !filter.isBlank()) {
            // Parse simple "userName eq \"value\"" filter
            String username = parseUserNameEqFilter(filter);
            if (username != null) {
                Optional<User> user = userService.findByUsername(username);
                users = user.map(List::of).orElse(List.of());
            } else {
                // Parse "externalId eq \"value\"" filter
                String externalId = parseExternalIdEqFilter(filter);
                if (externalId != null) {
                    Optional<User> user = userService.findByExternalId(externalId);
                    users = user.map(List::of).orElse(List.of());
                } else {
                    users = userService.getAllUsers();
                }
            }
        } else {
            users = userService.getAllUsers();
        }

        // Apply pagination
        int totalResults = users.size();
        int fromIndex = Math.min(startIndex - 1, totalResults);
        int toIndex = Math.min(fromIndex + count, totalResults);
        List<User> pagedUsers = users.subList(Math.max(0, fromIndex), toIndex);

        List<Map<String, Object>> resources = pagedUsers.stream()
                .map(this::userToScimResource)
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("schemas", List.of(SCHEMA_LIST_RESPONSE));
        response.put("totalResults", totalResults);
        response.put("startIndex", startIndex);
        response.put("itemsPerPage", pagedUsers.size());
        response.put("Resources", resources);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /scim/v2/Users/{id} - Get a single user.
     */
    @GetMapping(value = "/Users/{id}", produces = SCIM_MEDIA_TYPE)
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable UUID id) {
        log.debug("SCIM getUser: {}", id);

        Optional<User> userOpt = userService.getUserById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(scimError("User not found", "404"));
        }

        return ResponseEntity.ok(userToScimResource(userOpt.get()));
    }

    /**
     * POST /scim/v2/Users - Create a new user.
     */
    @PostMapping(value = "/Users", consumes = {SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE},
            produces = SCIM_MEDIA_TYPE)
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, Object> scimUser) {
        log.info("SCIM createUser request");

        String userName = (String) scimUser.get("userName");
        if (userName == null || userName.isBlank()) {
            return ResponseEntity.badRequest().body(scimError("userName is required", "400"));
        }

        // Check if user already exists
        Optional<User> existing = userService.findByUsername(userName);
        if (existing.isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(scimError("User already exists with userName: " + userName, "409"));
        }

        // Extract fields from SCIM request
        String externalId = (String) scimUser.get("externalId");
        String displayName = (String) scimUser.get("displayName");
        Boolean active = scimUser.containsKey("active") ? (Boolean) scimUser.get("active") : Boolean.TRUE;

        // Extract email from emails array
        String email = extractPrimaryEmail(scimUser);

        // Extract name
        if (displayName == null) {
            @SuppressWarnings("unchecked")
            Map<String, String> name = (Map<String, String>) scimUser.get("name");
            if (name != null) {
                String givenName = name.get("givenName");
                String familyName = name.get("familyName");
                if (givenName != null && familyName != null) {
                    displayName = givenName + " " + familyName;
                } else if (givenName != null) {
                    displayName = givenName;
                } else if (familyName != null) {
                    displayName = familyName;
                }
            }
        }

        // Create user via service (no password for SCIM users)
        User user = userService.getOrCreateUser(userName, email, displayName, User.AuthProvider.SCIM);
        user.setExternalId(externalId);
        user.setEnabled(active);
        user = userService.saveUser(user);

        log.info("SCIM user created: {} (id: {}, externalId: {})", userName, user.getId(), externalId);

        return ResponseEntity.status(HttpStatus.CREATED).body(userToScimResource(user));
    }

    /**
     * PUT /scim/v2/Users/{id} - Replace user.
     */
    @PutMapping(value = "/Users/{id}", consumes = {SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE},
            produces = SCIM_MEDIA_TYPE)
    public ResponseEntity<Map<String, Object>> replaceUser(@PathVariable UUID id,
                                                            @RequestBody Map<String, Object> scimUser) {
        log.info("SCIM replaceUser: {}", id);

        Optional<User> userOpt = userService.getUserById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(scimError("User not found", "404"));
        }

        User user = userOpt.get();

        // Update fields
        String userName = (String) scimUser.get("userName");
        if (userName != null && !userName.isBlank()) {
            // Check if new username conflicts with another user
            Optional<User> conflicting = userService.findByUsername(userName);
            if (conflicting.isPresent() && !conflicting.get().getId().equals(id)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(scimError("userName already in use: " + userName, "409"));
            }
            user.setUsername(userName);
        }

        String externalId = (String) scimUser.get("externalId");
        if (externalId != null) {
            user.setExternalId(externalId);
        }

        String displayName = (String) scimUser.get("displayName");
        if (displayName != null) {
            user.setDisplayName(displayName);
        } else {
            @SuppressWarnings("unchecked")
            Map<String, String> name = (Map<String, String>) scimUser.get("name");
            if (name != null) {
                String givenName = name.get("givenName");
                String familyName = name.get("familyName");
                if (givenName != null && familyName != null) {
                    user.setDisplayName(givenName + " " + familyName);
                }
            }
        }

        String email = extractPrimaryEmail(scimUser);
        if (email != null) {
            user.setEmail(email);
        }

        if (scimUser.containsKey("active")) {
            user.setEnabled((Boolean) scimUser.get("active"));
        }

        user = userService.saveUser(user);
        log.info("SCIM user replaced: {} (id: {})", user.getUsername(), id);

        return ResponseEntity.ok(userToScimResource(user));
    }

    /**
     * PATCH /scim/v2/Users/{id} - Partial update (activate/deactivate).
     */
    @PatchMapping(value = "/Users/{id}", consumes = {SCIM_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE},
            produces = SCIM_MEDIA_TYPE)
    public ResponseEntity<Map<String, Object>> patchUser(@PathVariable UUID id,
                                                          @RequestBody Map<String, Object> patchRequest) {
        log.info("SCIM patchUser: {}", id);

        Optional<User> userOpt = userService.getUserById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(scimError("User not found", "404"));
        }

        User user = userOpt.get();

        // Process SCIM PATCH operations
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> operations = (List<Map<String, Object>>) patchRequest.get("Operations");
        if (operations == null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ops = (List<Map<String, Object>>) patchRequest.get("operations");
            operations = ops;
        }

        if (operations != null) {
            for (Map<String, Object> operation : operations) {
                String op = ((String) operation.get("op")).toLowerCase();
                String path = (String) operation.get("path");
                Object value = operation.get("value");

                switch (op) {
                    case "replace" -> {
                        if ("active".equalsIgnoreCase(path)) {
                            user.setEnabled(toBoolean(value));
                        } else if ("displayName".equalsIgnoreCase(path)) {
                            user.setDisplayName((String) value);
                        } else if ("userName".equalsIgnoreCase(path)) {
                            user.setUsername((String) value);
                        } else if ("externalId".equalsIgnoreCase(path)) {
                            user.setExternalId((String) value);
                        } else if (path == null && value instanceof Map) {
                            // Value-based replace without path (Okta format)
                            @SuppressWarnings("unchecked")
                            Map<String, Object> valueMap = (Map<String, Object>) value;
                            if (valueMap.containsKey("active")) {
                                user.setEnabled(toBoolean(valueMap.get("active")));
                            }
                            if (valueMap.containsKey("displayName")) {
                                user.setDisplayName((String) valueMap.get("displayName"));
                            }
                        }
                    }
                    case "add" -> {
                        if ("active".equalsIgnoreCase(path)) {
                            user.setEnabled(toBoolean(value));
                        }
                    }
                    default -> log.debug("Unsupported SCIM PATCH operation: {}", op);
                }
            }
        }

        user = userService.saveUser(user);
        log.info("SCIM user patched: {} (id: {}, enabled: {})", user.getUsername(), id, user.getEnabled());

        return ResponseEntity.ok(userToScimResource(user));
    }

    /**
     * DELETE /scim/v2/Users/{id} - Soft-delete (deactivate) a user.
     */
    @DeleteMapping(value = "/Users/{id}", produces = SCIM_MEDIA_TYPE)
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        log.info("SCIM deleteUser (soft-delete): {}", id);

        Optional<User> userOpt = userService.getUserById(id);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = userOpt.get();
        user.setEnabled(false);
        userService.saveUser(user);

        log.info("SCIM user deactivated: {} (id: {})", user.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    // --- Helper methods ---

    private Map<String, Object> userToScimResource(User user) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("schemas", List.of(SCHEMA_USER));
        resource.put("id", user.getId().toString());

        if (user.getExternalId() != null) {
            resource.put("externalId", user.getExternalId());
        }

        resource.put("userName", user.getUsername());

        // Name
        Map<String, String> name = new LinkedHashMap<>();
        if (user.getDisplayName() != null) {
            String[] parts = user.getDisplayName().split("\\s+", 2);
            name.put("givenName", parts[0]);
            if (parts.length > 1) {
                name.put("familyName", parts[1]);
            }
            name.put("formatted", user.getDisplayName());
        }
        resource.put("name", name);
        resource.put("displayName", user.getDisplayName());

        // Emails
        if (user.getEmail() != null) {
            List<Map<String, Object>> emails = new ArrayList<>();
            Map<String, Object> emailEntry = new LinkedHashMap<>();
            emailEntry.put("value", user.getEmail());
            emailEntry.put("type", "work");
            emailEntry.put("primary", true);
            emails.add(emailEntry);
            resource.put("emails", emails);
        }

        resource.put("active", Boolean.TRUE.equals(user.getEnabled()));

        // Meta
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("resourceType", "User");

        if (user.getCreatedAt() != null) {
            meta.put("created", formatDateTime(user.getCreatedAt()));
        }
        if (user.getLastLoginAt() != null) {
            meta.put("lastModified", formatDateTime(user.getLastLoginAt()));
        }
        meta.put("location", "/scim/v2/Users/" + user.getId());

        resource.put("meta", meta);

        return resource;
    }

    private Map<String, Object> scimError(String detail, String status) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("schemas", List.of(SCHEMA_ERROR));
        error.put("detail", detail);
        error.put("status", status);
        return error;
    }

    private String extractPrimaryEmail(Map<String, Object> scimUser) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> emails = (List<Map<String, Object>>) scimUser.get("emails");
        if (emails != null && !emails.isEmpty()) {
            // Find primary email first
            for (Map<String, Object> emailEntry : emails) {
                if (Boolean.TRUE.equals(emailEntry.get("primary"))) {
                    return (String) emailEntry.get("value");
                }
            }
            // Fall back to first email
            return (String) emails.get(0).get("value");
        }
        return null;
    }

    private String parseUserNameEqFilter(String filter) {
        // Parse: userName eq "value" or userName eq 'value'
        if (filter == null) return null;
        String trimmed = filter.trim();
        if (trimmed.toLowerCase().startsWith("username eq ")) {
            String value = trimmed.substring(12).trim();
            return stripQuotes(value);
        }
        return null;
    }

    private String parseExternalIdEqFilter(String filter) {
        if (filter == null) return null;
        String trimmed = filter.trim();
        if (trimmed.toLowerCase().startsWith("externalid eq ")) {
            String value = trimmed.substring(14).trim();
            return stripQuotes(value);
        }
        return null;
    }

    private String stripQuotes(String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        if (value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return Boolean.TRUE;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
