package org.airsonic.player.controller;

import org.airsonic.player.command.UserSettingsCommand;
import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.TranscodeScheme;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.TranscodingService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Thomas Keppler
 * @version $Id$
 */
@ExtendWith(MockitoExtension.class)
@WebMvcTest
@ContextConfiguration(classes = {UserSettingsController.class}, initializers = {ConfigDataApplicationContextInitializer.class})
@EnableConfigurationProperties({AirsonicHomeConfig.class})
public class UserSettingsControllerTest {

    @MockBean
    private SecurityService securityService;
    @MockBean
    private SettingsService settingsService;
    @MockBean
    private MediaFolderService mediaFolderService;
    @MockBean
    private TranscodingService transcodingService;

    @Autowired
    private UserSettingsController userSettingsController;

    @TempDir
    private static Path tempDir;

    @BeforeAll
    public static void setup() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @AfterAll
    public static void tearDown() {
        System.clearProperty("airsonic.home");
    }

    @Test
    void testUsersCanBeUpdated() {
        // Given I have a freshly created user
        final User newUser = new User("test", "test@example.com");
        when(securityService.getUserByName("test")).thenReturn(newUser);
        final UserSettings someSettings = new UserSettings();
        when(settingsService.getUserSettings("test")).thenReturn(someSettings);

        // And I have a command for it
        final UserSettingsCommand command = new UserSettingsCommand();
        command.setNewUser(true);
        command.setUsername("test");
        command.setUser(newUser);
        command.setTranscodeSchemeName(TranscodeScheme.OFF.name());
        command.setLdapAuthenticated(false);
        command.setStreamRole(true);
        command.setSettingsRole(true);
        command.setAllowedMusicFolderIds(new int[] {1, 2, 3});

        // When I run the update
        // Then the update succeeds
        assertDoesNotThrow(() -> userSettingsController.updateUser(command));
        verify(mediaFolderService).setMusicFoldersForUser(eq("test"), eq(Set.of(1, 2, 3)));
    }

    @Test
    void testUsersCanBeUpdatedWithoutAllowedMusicFolderIds() {
        // Given I have a freshly created user
        final User newUser = new User("test", "test@example.com");
        when(securityService.getUserByName("test")).thenReturn(newUser);
        final UserSettings someSettings = new UserSettings();
        when(settingsService.getUserSettings("test")).thenReturn(someSettings);

        // And I have a command for it, that does not contain any "allowedMediaFolderIds"
        final UserSettingsCommand command = new UserSettingsCommand();
        command.setNewUser(true);
        command.setUsername("test");
        command.setUser(newUser);
        command.setTranscodeSchemeName(TranscodeScheme.OFF.name());
        command.setLdapAuthenticated(false);
        command.setStreamRole(true);
        command.setSettingsRole(true);
        // v~~~ Special case for when the system doesn't have any enabled or existing folders.
        command.setAllowedMusicFolderIds(null);

        // When I run the update
        // Then the update succeeds
        assertDoesNotThrow(() -> userSettingsController.updateUser(command));
        verify(mediaFolderService).setMusicFoldersForUser(eq("test"), eq(Collections.emptySet()));
    }
}
