# CF Llama Chat — Security and Authentication

> Enterprise-grade authentication with local credentials, SSO via OAuth2, role-based access control, and invitation codes for secure onboarding.

---

## Problem Statement

Enterprise AI platforms handle sensitive conversations, proprietary documents, and internal data. Without proper authentication and access control, deploying an AI chat tool creates security risk. CF Llama Chat provides multiple authentication methods, role-based access control, and optional invitation codes — meeting enterprise security requirements out of the box.

---

## Scene 1: Local Authentication

CF Llama Chat includes a built-in authentication system that works with no external dependencies.

### User registration and login

Users sign up with a username and password directly on the platform. The login page accepts these credentials and creates an authenticated session. All passwords are securely hashed using bcrypt — the industry standard for password storage. Plain-text passwords are never stored.

### Changing your password

Click your avatar in the header to open the user menu and select "Change Password." Enter your current password and your new password. The update takes effect immediately for your next login.

### Admin password resets

When users are locked out or forget their credentials, administrators can reset their password from the Admin Portal's Users page. Click "Reset Password" next to the user's name, enter a new password, and the user can log in immediately with the new credentials.

---

## Scene 2: Single Sign-On With OAuth2

For enterprise deployments, CF Llama Chat integrates with Cloud Foundry's p-identity service for single sign-on.

### How SSO works

When SSO is configured, the login page redirects users to their corporate identity provider — Active Directory, Okta, or any SAML or OIDC provider connected through the p-identity tile. Users authenticate with their existing corporate credentials. After successful authentication, they're redirected back to CF Llama Chat with an authenticated session. No separate password to remember, no separate account to manage.

### Automatic user provisioning

When a user authenticates via SSO for the first time, CF Llama Chat automatically creates their account with the appropriate role. No manual user creation needed — just bind the p-identity service and users can log in with their corporate credentials immediately.

### Benefits for IT teams

SSO integration means CF Llama Chat inherits your existing access policies — multi-factor authentication, password rotation, session timeouts, and account deprovisioning all flow through your corporate identity provider. When an employee leaves the organization, disabling their corporate account immediately revokes their CF Llama Chat access.

---

## Scene 3: Role-Based Access Control

CF Llama Chat enforces two distinct roles with clear permission boundaries.

### The User role

Regular users have access to the chat interface with all its features — model selection, streaming, document search, and tool usage. They can access the Workspace Hub with channels, notes, memory, prompts, documents, and tools. They can manage their personal settings, export their data, and change their password. They cannot access the Admin Portal or any administrative functions.

### The Admin role

Administrators have all the same capabilities as regular users, plus full access to the Admin Portal. This includes user management, model configuration, MCP server registration, skills configuration, organization theming, system settings, database monitoring, banner management, and webhook configuration. The "Admin" link in the header navigation only appears for users with the Admin role.

### Promoting and demoting

Administrators can promote any user to admin or demote an admin back to a regular user from the Admin Portal's Users page. This is a simple toggle — no redeployment or configuration change needed.

---

## Scene 4: Invitation Codes

For deployments that are accessible on a public network, invitation codes provide an additional layer of access control.

### How invitation codes work

When enabled, the registration page requires an invitation code in addition to a username and password. Without a valid code, new users cannot create accounts. This prevents unauthorized signups on internet-facing deployments while still allowing controlled onboarding.

### Managing invitation codes

Administrators generate invitation codes and distribute them to approved users through a secure channel — email, Slack, or in-person. Each code can be configured for single use or limited uses to prevent sharing beyond the intended recipients.

### When to use invitation codes

Invitation codes are ideal for scenarios where SSO isn't available but you still need to control who can access the platform. For fully internal deployments behind a corporate VPN, invitation codes are optional since network access itself serves as a control.

---

## Summary

CF Llama Chat meets enterprise security requirements with multiple layers of protection. Local authentication with bcrypt-hashed passwords works out of the box. SSO via Cloud Foundry's p-identity service integrates with your corporate identity provider for seamless, managed access. Role-based access control separates regular users from administrators with clear permission boundaries. Optional invitation codes prevent unauthorized signups on public-facing deployments. Together, these features ensure that your AI platform is as secure as the conversations it hosts.
