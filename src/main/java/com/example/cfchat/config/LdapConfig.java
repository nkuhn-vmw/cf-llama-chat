package com.example.cfchat.config;

import com.example.cfchat.auth.UserService;
import com.example.cfchat.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.authentication.LdapAuthenticator;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;
import org.springframework.security.ldap.userdetails.LdapUserDetailsMapper;
import org.springframework.security.ldap.userdetails.UserDetailsContextMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "auth.ldap.enabled", havingValue = "true")
@Slf4j
public class LdapConfig {

    @Value("${auth.ldap.url}")
    private String url;

    @Value("${auth.ldap.base}")
    private String base;

    @Value("${auth.ldap.user-dn-pattern:uid={0},ou=people}")
    private String userDnPattern;

    @Value("${auth.ldap.user-search-filter:(uid={0})}")
    private String userSearchFilter;

    @Value("${auth.ldap.group-search-base:ou=groups}")
    private String groupSearchBase;

    @Value("${auth.ldap.manager-dn:}")
    private String managerDn;

    @Value("${auth.ldap.manager-password:}")
    private String managerPassword;

    @Value("${auth.ldap.default-role:USER}")
    private String defaultRole;

    @Bean
    public DefaultSpringSecurityContextSource ldapContextSource() {
        String providerUrl = url + "/" + base;
        log.info("Configuring LDAP context source: {}", providerUrl);

        DefaultSpringSecurityContextSource contextSource = new DefaultSpringSecurityContextSource(providerUrl);

        if (managerDn != null && !managerDn.isBlank()) {
            contextSource.setUserDn(managerDn);
            contextSource.setPassword(managerPassword);
            log.info("LDAP manager DN configured: {}", managerDn);
        }

        contextSource.afterPropertiesSet();
        return contextSource;
    }

    @Bean
    public LdapAuthoritiesPopulator ldapAuthoritiesPopulator(DefaultSpringSecurityContextSource contextSource) {
        DefaultLdapAuthoritiesPopulator populator =
                new DefaultLdapAuthoritiesPopulator(contextSource, groupSearchBase);
        populator.setGroupSearchFilter("(member={0})");
        populator.setSearchSubtree(true);
        populator.setConvertToUpperCase(true);
        populator.setDefaultRole("ROLE_" + defaultRole.toUpperCase());
        log.info("LDAP authorities populator configured with group search base: {}", groupSearchBase);
        return populator;
    }

    @Bean
    public AuthenticationProvider ldapAuthenticationProvider(
            DefaultSpringSecurityContextSource contextSource,
            LdapAuthoritiesPopulator authoritiesPopulator,
            UserService userService) {

        BindAuthenticator authenticator = new BindAuthenticator(contextSource);
        authenticator.setUserDnPatterns(new String[]{userDnPattern});

        // Also configure search-based authentication as fallback
        if (managerDn != null && !managerDn.isBlank()) {
            FilterBasedLdapUserSearch userSearch =
                    new FilterBasedLdapUserSearch("", userSearchFilter, contextSource);
            authenticator.setUserSearch(userSearch);
        }

        LdapAuthenticationProvider provider =
                new LdapAuthenticationProvider(authenticator, authoritiesPopulator);

        // Wrap the provider to auto-provision users on first LDAP login
        log.info("LDAP authentication provider configured with user DN pattern: {}", userDnPattern);

        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                Authentication ldapAuth = provider.authenticate(authentication);

                if (ldapAuth != null && ldapAuth.isAuthenticated()) {
                    String username = ldapAuth.getName();
                    log.info("LDAP authentication successful for user: {}", username);

                    // Extract email from LDAP attributes if available
                    String email = null;
                    String displayName = null;
                    Object principal = ldapAuth.getPrincipal();
                    if (principal instanceof org.springframework.security.ldap.userdetails.LdapUserDetails ldapDetails) {
                        displayName = ldapDetails.getUsername();
                    }

                    // Auto-provision or update user in local database
                    User user = userService.getOrCreateUser(username, email, displayName, User.AuthProvider.LDAP);

                    // Map LDAP groups to application roles
                    User.UserRole userRole = mapLdapAuthoritiesToRole(ldapAuth.getAuthorities());
                    if (user.getRole() != userRole && user.getRole() != User.UserRole.ADMIN) {
                        // Don't demote existing admins, but promote if LDAP says admin
                        if (userRole == User.UserRole.ADMIN) {
                            userService.updateUserRole(user.getId(), userRole);
                        }
                    }

                    List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
                    );

                    return new UsernamePasswordAuthenticationToken(
                            username, null, authorities);
                }

                return ldapAuth;
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
            }
        };
    }

    private User.UserRole mapLdapAuthoritiesToRole(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null) {
            return User.UserRole.valueOf(defaultRole.toUpperCase());
        }

        for (GrantedAuthority authority : authorities) {
            String auth = authority.getAuthority().toUpperCase();
            if (auth.contains("ADMIN") || auth.contains("ADMINISTRATORS")) {
                return User.UserRole.ADMIN;
            }
        }

        return User.UserRole.valueOf(defaultRole.toUpperCase());
    }
}
