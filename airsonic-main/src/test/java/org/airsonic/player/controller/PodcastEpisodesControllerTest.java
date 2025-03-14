/**
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2024 (C) Y.Tory
 */
package org.airsonic.player.controller;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastEpisode;
import org.airsonic.player.service.PodcastPersistenceService;
import org.airsonic.player.service.podcast.PodcastDownloadClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Y.Tory
 * @version $Id$
 */
@ExtendWith(MockitoExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@EnableConfigurationProperties({AirsonicHomeConfig.class})
public class PodcastEpisodesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @TempDir
    private static Path tempDir;

    @MockitoBean
    private PodcastPersistenceService podcastService;

    @MockitoBean
    private PodcastDownloadClient podcastDownloadClient;

    @Mock
    private PodcastEpisode mockedEpisode;

    @Mock
    private PodcastChannel mockedChannel;

    @BeforeAll
    public static void setUp() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @AfterAll
    public static void teardown() {
        System.clearProperty("airsonic.home");
    }

    @Test
    @WithMockUser(username = "podcastuser", roles = {"PODCAST", "USER"})
    public void testPostDownloadEpisode() throws Exception {
        // given
        when(podcastService.getEpisode(1, false)).thenReturn(mockedEpisode);
        when(mockedEpisode.getChannel()).thenReturn(mockedChannel);
        when(mockedChannel.getId()).thenReturn(2);

        // when
        mockMvc.perform(
            post("/podcastEpisodes")
            .with(csrf())
            .param("download", "")
            .param("episodeId", "1"))
            .andExpect(redirectedUrl("/podcastChannel.view?id=2&page=0&size=10"));
        // verify
        verify(podcastDownloadClient).downloadEpisode(1);
        verify(podcastService).getEpisode(1, false);
    }

    @Test
    @WithMockUser(username = "podcastuser", roles = {"PODCAST", "USER"})
    public void testPostInitEpisode() throws Exception {
        // given
        when(podcastService.getEpisode(1, true)).thenReturn(mockedEpisode);
        when(mockedEpisode.getChannel()).thenReturn(mockedChannel);
        when(mockedChannel.getId()).thenReturn(2);

        // when
        mockMvc.perform(
            post("/podcastEpisodes")
            .with(csrf())
            .param("init", "")
            .param("episodeId", "1"))
            .andExpect(redirectedUrl("/podcastChannel.view?id=2&page=0&size=10"));
        // verify
        verify(podcastService).resetEpisode(1);
        verify(podcastService).getEpisode(1, true);
    }

    @Test
    @WithMockUser(username = "podcastuser", roles = {"PODCAST", "USER"})
    public void testPostLockEpisode() throws Exception {
        // given
        when(podcastService.getEpisode(1, true)).thenReturn(mockedEpisode);
        when(mockedEpisode.getChannel()).thenReturn(mockedChannel);
        when(mockedChannel.getId()).thenReturn(2);

        // when
        mockMvc.perform(
            post("/podcastEpisodes")
            .with(csrf())
            .param("lock", "")
            .param("episodeId", "1"))
            .andExpect(redirectedUrl("/podcastChannel.view?id=2&page=0&size=10"));
        // verify
        verify(podcastService).getEpisode(1, true);
    }

    @Test
    @WithMockUser(username = "podcastuser", roles = {"PODCAST", "USER"})
    public void testPostUnlockEpisode() throws Exception {
        // given
        when(podcastService.getEpisode(1, true)).thenReturn(mockedEpisode);
        when(mockedEpisode.getChannel()).thenReturn(mockedChannel);
        when(mockedChannel.getId()).thenReturn(2);

        // when
        mockMvc.perform(
            post("/podcastEpisodes")
            .with(csrf())
            .param("unlock", "")
            .param("episodeId", "1"))
            .andExpect(redirectedUrl("/podcastChannel.view?id=2&page=0&size=10"));
        // verify
        verify(podcastService).getEpisode(1, true);
    }

    @ParameterizedTest
    @WithMockUser(username = "user_no_podcast", roles = {"USER"})
    @ValueSource(strings = {"download", "init", "lock", "unlock"})
    public void testPostEpisodeWithNoPodcastRole(String action) throws Exception {
        // when
        mockMvc.perform(
            post("/podcastEpisodes")
            .with(csrf())
            .param(action, "")
            .param("episodeId", "1"))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @WithMockUser(username = "podcastuser", roles = {"PODCAST", "USER"})
    @ValueSource(strings = {"download", "init", "lock", "unlock"})
    public void testPostEpisodeWithNoEpisodeId(String action) throws Exception {
        // when
        mockMvc.perform(
            post("/podcastEpisodes")
            .with(csrf())
            .param(action, ""))
            .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @WithMockUser(username = "podcastuser", roles = {"PODCAST", "USER"})
    @ValueSource(strings = {"download", "init", "lock", "unlock"})
    public void testPostEpisodeWithNoEpisode(String action) throws Exception {
        // given
        when(podcastService.getEpisode(1, true)).thenReturn(null);

        // when
        mockMvc.perform(
            post("/podcastEpisodes")
            .with(csrf())
            .param(action, "")
            .param("episodeId", "1"))
            .andExpect(redirectedUrl("/notFound"));
    }



}
