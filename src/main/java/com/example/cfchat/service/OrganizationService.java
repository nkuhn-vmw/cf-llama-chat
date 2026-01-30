package com.example.cfchat.service;

import com.example.cfchat.dto.OrganizationDto;
import com.example.cfchat.dto.OrganizationRequest;
import com.example.cfchat.dto.OrganizationThemeDto;
import com.example.cfchat.model.Organization;
import com.example.cfchat.model.User;
import com.example.cfchat.repository.OrganizationRepository;
import com.example.cfchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    @Transactional
    public Organization createOrganization(OrganizationRequest request) {
        if (organizationRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Organization with name '" + request.getName() + "' already exists");
        }

        String slug = request.getSlug();
        if (slug == null || slug.isBlank()) {
            slug = request.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        }

        if (organizationRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Organization with slug '" + slug + "' already exists");
        }

        Organization org = Organization.builder()
                .name(request.getName())
                .slug(slug)
                .logoUrl(request.getLogoUrl())
                .faviconUrl(request.getFaviconUrl())
                .welcomeMessage(request.getWelcomeMessage())
                .primaryColor(request.getPrimaryColor() != null ? request.getPrimaryColor() : "#10a37f")
                .secondaryColor(request.getSecondaryColor() != null ? request.getSecondaryColor() : "#1a1a1a")
                .accentColor(request.getAccentColor() != null ? request.getAccentColor() : "#10a37f")
                .textColor(request.getTextColor() != null ? request.getTextColor() : "#ffffff")
                .backgroundColor(request.getBackgroundColor() != null ? request.getBackgroundColor() : "#0f0f0f")
                .sidebarColor(request.getSidebarColor() != null ? request.getSidebarColor() : "#0f0f0f")
                .headerText(request.getHeaderText() != null ? request.getHeaderText() : "Chat")
                .fontFamily(request.getFontFamily())
                .borderRadius(request.getBorderRadius() != null ? request.getBorderRadius() : "12px")
                .defaultTheme(parseTheme(request.getDefaultTheme()))
                .customCss(request.getCustomCss())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Organization saved = organizationRepository.save(org);
        log.info("Created organization: {} ({})", saved.getName(), saved.getId());
        return saved;
    }

    @Transactional
    public Organization updateOrganization(UUID id, OrganizationRequest request) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));

        if (request.getName() != null && !request.getName().equals(org.getName())) {
            if (organizationRepository.existsByName(request.getName())) {
                throw new IllegalArgumentException("Organization with name '" + request.getName() + "' already exists");
            }
            org.setName(request.getName());
        }

        if (request.getSlug() != null && !request.getSlug().equals(org.getSlug())) {
            if (organizationRepository.existsBySlug(request.getSlug())) {
                throw new IllegalArgumentException("Organization with slug '" + request.getSlug() + "' already exists");
            }
            org.setSlug(request.getSlug());
        }

        if (request.getLogoUrl() != null) org.setLogoUrl(request.getLogoUrl());
        if (request.getFaviconUrl() != null) org.setFaviconUrl(request.getFaviconUrl());
        if (request.getWelcomeMessage() != null) org.setWelcomeMessage(request.getWelcomeMessage());
        if (request.getPrimaryColor() != null) org.setPrimaryColor(request.getPrimaryColor());
        if (request.getSecondaryColor() != null) org.setSecondaryColor(request.getSecondaryColor());
        if (request.getAccentColor() != null) org.setAccentColor(request.getAccentColor());
        if (request.getTextColor() != null) org.setTextColor(request.getTextColor());
        if (request.getBackgroundColor() != null) org.setBackgroundColor(request.getBackgroundColor());
        if (request.getSidebarColor() != null) org.setSidebarColor(request.getSidebarColor());
        if (request.getHeaderText() != null) org.setHeaderText(request.getHeaderText());
        if (request.getFontFamily() != null) org.setFontFamily(request.getFontFamily());
        if (request.getBorderRadius() != null) org.setBorderRadius(request.getBorderRadius());
        if (request.getDefaultTheme() != null) org.setDefaultTheme(parseTheme(request.getDefaultTheme()));
        if (request.getCustomCss() != null) org.setCustomCss(request.getCustomCss());
        if (request.getActive() != null) org.setActive(request.getActive());

        Organization saved = organizationRepository.save(org);
        log.info("Updated organization: {} ({})", saved.getName(), saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<OrganizationDto> getAllOrganizations() {
        return organizationRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .map(org -> OrganizationDto.fromEntityWithMemberCount(org,
                        userRepository.countByOrganization(org)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrganizationDto> getAllOrganizationsIncludingInactive() {
        return organizationRepository.findAll()
                .stream()
                .map(org -> OrganizationDto.fromEntityWithMemberCount(org,
                        userRepository.countByOrganization(org)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<OrganizationDto> getOrganization(UUID id) {
        return organizationRepository.findById(id)
                .map(org -> OrganizationDto.fromEntityWithMemberCount(org,
                        userRepository.countByOrganization(org)));
    }

    @Transactional(readOnly = true)
    public Optional<OrganizationDto> getOrganizationBySlug(String slug) {
        return organizationRepository.findBySlug(slug)
                .map(org -> OrganizationDto.fromEntityWithMemberCount(org,
                        userRepository.countByOrganization(org)));
    }

    @Transactional(readOnly = true)
    public Optional<Organization> getOrganizationEntity(UUID id) {
        return organizationRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Organization> getOrganizationEntityBySlug(String slug) {
        return organizationRepository.findBySlug(slug);
    }

    @Transactional(readOnly = true)
    public OrganizationThemeDto getThemeForUser(User user) {
        if (user == null || user.getOrganization() == null) {
            return OrganizationThemeDto.getDefaultTheme();
        }
        return OrganizationThemeDto.fromEntity(user.getOrganization());
    }

    @Transactional(readOnly = true)
    public OrganizationThemeDto getThemeBySlug(String slug) {
        return organizationRepository.findBySlug(slug)
                .map(OrganizationThemeDto::fromEntity)
                .orElse(OrganizationThemeDto.getDefaultTheme());
    }

    @Transactional(readOnly = true)
    public Optional<OrganizationThemeDto> getThemeBySlugOptional(String slug) {
        return organizationRepository.findBySlugAndActiveTrue(slug)
                .map(OrganizationThemeDto::fromEntity);
    }

    @Transactional
    public void addUserToOrganization(UUID userId, UUID organizationId, User.OrganizationRole role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + organizationId));

        user.setOrganization(org);
        user.setOrganizationRole(role);
        userRepository.save(user);
        log.info("Added user {} to organization {} with role {}", user.getUsername(), org.getName(), role);
    }

    @Transactional
    public void removeUserFromOrganization(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        String orgName = user.getOrganization() != null ? user.getOrganization().getName() : "none";
        user.setOrganization(null);
        user.setOrganizationRole(User.OrganizationRole.MEMBER);
        userRepository.save(user);
        log.info("Removed user {} from organization {}", user.getUsername(), orgName);
    }

    @Transactional(readOnly = true)
    public List<User> getOrganizationMembers(UUID organizationId) {
        return userRepository.findByOrganizationId(organizationId);
    }

    @Transactional
    public void deleteOrganization(UUID id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));

        // Remove all users from this organization first
        List<User> members = userRepository.findByOrganizationId(id);
        for (User member : members) {
            member.setOrganization(null);
            member.setOrganizationRole(User.OrganizationRole.MEMBER);
            userRepository.save(member);
        }

        organizationRepository.delete(org);
        log.info("Deleted organization: {} ({})", org.getName(), org.getId());
    }

    private Organization.Theme parseTheme(String theme) {
        if (theme == null) {
            return Organization.Theme.DARK;
        }
        try {
            return Organization.Theme.valueOf(theme.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Organization.Theme.DARK;
        }
    }
}
