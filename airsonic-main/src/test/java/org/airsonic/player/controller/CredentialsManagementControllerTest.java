package org.airsonic.player.controller;

import org.airsonic.player.command.CredentialsManagementCommand.CredentialsCommand;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.User.Role;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests for the admin user selector fix: admins can view/edit credentials for
 * any user via the ?username= query parameter. Non-admins are restricted to
 * their own credentials regardless of the parameter value.
 */
@ExtendWith(MockitoExtension.class)
class CredentialsManagementControllerTest {

    @Mock
    private SecurityService securityService;
    @Mock
    private SettingsService settingsService;
    @Mock
    private UserService userService;
    @Mock
    private Authentication auth;
    @Mock
    private BindingResult bindingResult;

    @InjectMocks
    private CredentialsManagementController controller;

    private User adminUser;
    private User regularUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        adminUser = new User("admin", null, false, 0, 0, 0, EnumSet.of(Role.ADMIN));
        regularUser = new User("alice", null, false, 0, 0, 0, EnumSet.noneOf(Role.class));
        otherUser = new User("bob", null, false, 0, 0, 0, EnumSet.noneOf(Role.class));
        when(bindingResult.hasErrors()).thenReturn(false);
    }

    @Test
    void adminTargetingKnownUser_redirectIncludesTargetUsername() {
        when(auth.getName()).thenReturn("admin");
        when(securityService.getUserByName("admin")).thenReturn(adminUser);
        when(securityService.getUserByName("bob")).thenReturn(otherUser);
        when(securityService.createCredential(eq("bob"), any(), any())).thenReturn(true);

        String redirect = controller.createNewCreds(auth, new CredentialsCommand(),
                bindingResult, new RedirectAttributesModelMap(), new ModelMap(), "bob");

        assertThat(redirect).contains("?username=bob");
    }

    @Test
    void nonAdminWithTargetUsername_redirectOmitsUsername() {
        when(auth.getName()).thenReturn("alice");
        when(securityService.getUserByName("alice")).thenReturn(regularUser);
        when(securityService.createCredential(eq("alice"), any(), any())).thenReturn(true);

        String redirect = controller.createNewCreds(auth, new CredentialsCommand(),
                bindingResult, new RedirectAttributesModelMap(), new ModelMap(), "bob");

        assertThat(redirect).doesNotContain("username=");
    }

    @Test
    void adminTargetingUnknownUser_fallsBackToOwnRedirect() {
        when(auth.getName()).thenReturn("admin");
        when(securityService.getUserByName("admin")).thenReturn(adminUser);
        when(securityService.getUserByName("nobody")).thenReturn(null);
        when(securityService.createCredential(eq("admin"), any(), any())).thenReturn(true);

        String redirect = controller.createNewCreds(auth, new CredentialsCommand(),
                bindingResult, new RedirectAttributesModelMap(), new ModelMap(), "nobody");

        assertThat(redirect).doesNotContain("username=");
    }

    @Test
    void nullTargetUsername_usesAuthenticatedUser() {
        when(auth.getName()).thenReturn("alice");
        // resolveTargetUsername returns early for null — getUserByName is never called
        when(securityService.createCredential(eq("alice"), any(), any())).thenReturn(true);

        String redirect = controller.createNewCreds(auth, new CredentialsCommand(),
                bindingResult, new RedirectAttributesModelMap(), new ModelMap(), null);

        assertThat(redirect).doesNotContain("username=");
    }

    @Test
    void blankTargetUsername_usesAuthenticatedUser() {
        when(auth.getName()).thenReturn("alice");
        // resolveTargetUsername returns early for blank — getUserByName is never called
        when(securityService.createCredential(eq("alice"), any(), any())).thenReturn(true);

        String redirect = controller.createNewCreds(auth, new CredentialsCommand(),
                bindingResult, new RedirectAttributesModelMap(), new ModelMap(), "   ");

        // blank param treated same as null — targets own user, no ?username= suffix
        assertThat(redirect).doesNotContain("username=");
    }
}
