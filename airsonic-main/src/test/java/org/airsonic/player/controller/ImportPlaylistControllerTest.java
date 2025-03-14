package org.airsonic.player.controller;

import org.airsonic.player.domain.Playlist;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.PlaylistFileService;
import org.airsonic.player.service.SecurityService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
@WebMvcTest
@ContextConfiguration(classes = {ImportPlaylistController.class})
@SuppressWarnings("unchecked")
public class ImportPlaylistControllerTest {

    private static final String FILE_NAME = "testPlaylist.m3u";
    private static final String PLAYLIST_NAME = "testPlaylist";
    private static final String USERNAME = "testuser";
    private static final String CONTENTS = "#EXTM3U\n" +
            "#EXTINF:0,Sample Artist - Sample Track\n" +
            "sample.mp3";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SecurityService securityService;

    @MockitoBean
    private PlaylistFileService playlistFileService;

    @TempDir
    private static Path tempDir;

    @BeforeAll
    public static void setup() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @AfterAll
    public static void teardown() {
        System.clearProperty("airsonic.home");
    }

    @Test
    @WithMockUser(username = USERNAME)
    public void handlePostWithValidFileShouldImportPlaylistAndRedirect() throws Exception {
        // Arrange

        MockMultipartFile file = new MockMultipartFile("file", FILE_NAME, "text/plain", CONTENTS.getBytes());

        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.multipart("/importPlaylist")
                .file(file)
                .with(csrf());

        Playlist expectedPlaylist = new Playlist();
        expectedPlaylist.setName(PLAYLIST_NAME);
        expectedPlaylist.setUsername(USERNAME);

        User user = new User();
        user.setUsername(USERNAME);

        when(securityService.getCurrentUser(any())).thenReturn(user);
        when(playlistFileService.importPlaylist(eq(user), eq(PLAYLIST_NAME), eq(FILE_NAME), isNull(), any(), isNull())).thenReturn(expectedPlaylist);

        MvcResult result = mockMvc.perform(request)
            .andExpect(status().is3xxRedirection())
            .andExpect(view().name("redirect:importPlaylist"))
            .andExpect(redirectedUrl("importPlaylist"))
            .andReturn();

        Map<String, Object> actual = (Map<String, Object>) result.getFlashMap().get("model");
        assertEquals(expectedPlaylist, actual.get("playlist"));
        assertNull(actual.get("error"));
    }

    @Test
    @WithMockUser(username = USERNAME)
    public void handlePostWithBlankFileNameShouldNotImportPlaylistAndRedirectWithError() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("file", "", "text/plain", CONTENTS.getBytes());

        // Act
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.multipart("/importPlaylist")
                .file(file)
                .with(csrf());

        // Assert
        MvcResult result = mockMvc.perform(request)
            .andExpect(status().is3xxRedirection())
            .andExpect(view().name("redirect:importPlaylist"))
            .andExpect(redirectedUrl("importPlaylist"))
            .andReturn();
        Map<String, Object> actual = (Map<String, Object>) result.getFlashMap().get("model");
        assertNull(actual.get("playlist"));
        assertEquals("No file specified.", actual.get("error"));
        verify(playlistFileService, never()).importPlaylist(any(User.class), anyString(), anyString(), isNull(), any(), isNull());

    }

    @Test
    @WithMockUser(username = USERNAME)
    public void handlePostWithLargeFileShouldNotImportPlaylistAndRedirectWithError() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("file", FILE_NAME, "text/plain", new byte[1024 * 1024 * 6]);

        // Act
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.multipart("/importPlaylist")
                .file(file)
                .with(csrf());

        // Assert
        MvcResult result = mockMvc.perform(request)
            .andExpect(status().is3xxRedirection())
            .andExpect(view().name("redirect:importPlaylist"))
            .andExpect(redirectedUrl("importPlaylist"))
            .andReturn();
        Map<String, Object> actual = (Map<String, Object>) result.getFlashMap().get("model");
        assertNull(actual.get("playlist"));
        assertEquals("The playlist file is too large. Max file size is 5 MB.", actual.get("error"));
        verify(playlistFileService, never()).importPlaylist(any(User.class), anyString(), anyString(), isNull(), any(), isNull());

    }
}
