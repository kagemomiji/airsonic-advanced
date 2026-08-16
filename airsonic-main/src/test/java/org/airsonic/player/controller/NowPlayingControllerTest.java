package org.airsonic.player.controller;

import org.airsonic.player.domain.AvatarScheme;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.PlayStatus;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.PersonalSettingsService;
import org.airsonic.player.service.StatusService;
import org.airsonic.player.view.NowPlayingView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
public class NowPlayingControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private StatusService statusService;

    @MockitoBean
    private PersonalSettingsService personalSettingsService;

    @Mock
    private PlayStatus playStatus;

    @Mock
    private MediaFile mediaFile;

    @Mock
    private UserSettings userSettings;

    private static final String TEST_USER = "testuser";
    private static final String TEST_PASSWORD = "testpassword";

    @TempDir
    private static Path tempDir;

    @BeforeAll
    public static void setup() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @Test
    @WithMockUser(username = TEST_USER, password = TEST_PASSWORD)
    public void testNowPlayingStatusShouldReturnCorrectView() throws Exception {
        UUID id = UUID.randomUUID();
        Player player = new Player();
        player.setId(10);
        player.setName("Test Player");
        player.setUsername(TEST_USER);

        when(playStatus.getTransferId()).thenReturn(id);
        when(playStatus.getPlayer()).thenReturn(player);
        when(playStatus.getMediaFile()).thenReturn(mediaFile);
        when(playStatus.getMinutesAgo()).thenReturn(5L);

        when(statusService.getActivePlayStatuses()).thenReturn(List.of(playStatus));
        when(statusService.getInactivePlayStatuses()).thenReturn(List.of());
        when(playStatus.getMediaFile()).thenReturn(mediaFile);
        when(mediaFile.getId()).thenReturn(11);
        when(mediaFile.isVideo()).thenReturn(false);
        when(mediaFile.getArtist()).thenReturn("Test Artist");
        when(mediaFile.getTitle()).thenReturn("Test Title");

        when(userSettings.getAvatarScheme()).thenReturn(AvatarScheme.NONE);

        when(personalSettingsService.getUserSettings(TEST_USER)).thenReturn(userSettings);

        NowPlayingView view = (NowPlayingView) mvc.perform(get("/nowPlaying/status").param("id", id.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("nowplaying/_status::status"))
                .andExpect(model().attributeExists("np"))
                .andReturn()
                .getModelAndView()
                .getModel()
                .get("np");

        assertEquals(id, view.getTransferId());
        assertEquals(player.getId(), view.getPlayerId());
        assertEquals(11, view.getMediaFileId());
        assertEquals("Test Artist", view.getArtist());
        assertEquals("Test Title", view.getTitle());
        assertEquals(5L, view.getMinutesAgo());
        assertEquals(AvatarScheme.NONE.name(), view.getAvatarScheme());
        assertEquals(TEST_USER + "@" + player.getName(), view.getUsername());
        // avatarUsername must remain the original username, not the display name with "@player"
        assertEquals(TEST_USER, view.getAvatarUsername());
        // Since mediaFile is not a video, lyrics should be shown.
        assertTrue(view.isShowLyrics());
        // Since AvatarScheme.NONE is set in userSettings, no custom avatar should be present.
        assertFalse(view.hasCustomAvatar());

    }

    @Test
    @WithMockUser(username = TEST_USER, password = TEST_PASSWORD)
    public void testAvatarUrlUsesOriginalUsername() throws Exception {
        UUID id = UUID.randomUUID();
        Player player = new Player();
        player.setId(20);
        player.setName("ExternalPlayer");
        player.setUsername(TEST_USER);

        when(playStatus.getTransferId()).thenReturn(id);
        when(playStatus.getPlayer()).thenReturn(player);
        when(playStatus.getMediaFile()).thenReturn(mediaFile);
        when(playStatus.getMinutesAgo()).thenReturn(2L);
        when(mediaFile.getId()).thenReturn(30);
        when(mediaFile.isVideo()).thenReturn(false);
        when(mediaFile.getArtist()).thenReturn("Artist");
        when(mediaFile.getTitle()).thenReturn("Title");
        when(userSettings.getAvatarScheme()).thenReturn(AvatarScheme.NONE);
        when(personalSettingsService.getUserSettings(TEST_USER)).thenReturn(userSettings);
        when(statusService.getActivePlayStatuses()).thenReturn(List.of(playStatus));
        when(statusService.getInactivePlayStatuses()).thenReturn(List.of());

        NowPlayingView view = (NowPlayingView) mvc.perform(get("/nowPlaying/status").param("id", id.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getModelAndView()
                .getModel()
                .get("np");

        // display name gets player suffix appended
        assertEquals(TEST_USER + "@ExternalPlayer", view.getUsername());
        // avatar URL lookup must use the unmodified username, not the display name
        assertEquals(TEST_USER, view.getAvatarUsername());
    }

    @Test
    @WithAnonymousUser
    public void testNowPlayingStatusShouldReturnUnauthorizedForAnonymousUser() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/nowPlaying/status").param("id", id.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = TEST_USER, password = TEST_PASSWORD)
    public void testNowPlayingStatusWithoutIdShouldReturnBadRequest() throws Exception {
        mvc.perform(get("/nowPlaying/status"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_USER, password = TEST_PASSWORD)
    public void testNowPlayingStatusWithNonUUIDIdShouldReturnBadRequest() throws Exception {
        mvc.perform(get("/nowPlaying/status").param("id", "invalid-uuid"))
                .andExpect(status().isBadRequest());
    }

}
