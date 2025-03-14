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

 Copyright 2023 (C) Y.Tory
 */
package org.airsonic.player.controller;

import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.domain.dto.AlbumCoverArtRequest;
import org.airsonic.player.domain.dto.ArtistCoverArtRequest;
import org.airsonic.player.domain.dto.MediaFileCoverArtRequest;
import org.airsonic.player.domain.dto.PlaylistCoverArtRequest;
import org.airsonic.player.service.CoverArtCreateService;
import org.airsonic.player.util.ImageUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Y.Tory
 * @version $Id$
 */
@SpringBootTest
@AutoConfigureMockMvc
public class CoverArtControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoSpyBean
    private CoverArtCreateService coverArtService;

    @Mock
    private CoverArt mockedCoverArt;

    @InjectMocks
    private CoverArtController coverArtController;

    @TempDir
    private static Path tempDir;

    /** authentication info */
    private final String AIRSONIC_USER = "admin";
    private final String AIRSONIC_PASSWORD = "admin";

    /** resource */
    private final ClassPathResource IMAGE_RESOURCE = new ClassPathResource(
            "MEDIAS/Music2/_DIR_ chrome hoof - 2004/Folder.jpg");
    private final ClassPathResource PLAYLIST_RESOURCE = new ClassPathResource("COVERARTS/playlist.png");
    private final ClassPathResource PODCAST_RESOURCE = new ClassPathResource("COVERARTS/podcast.png");

    @BeforeAll
    public static void setup() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @Test
    @WithMockUser(username = AIRSONIC_USER, password = AIRSONIC_PASSWORD)
    public void getCoverArtFallbackWithNullIdTest() throws Exception {
        // no parameter
        byte[] actual = mvc.perform(get("/coverArt"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // load default_cover.jpg image
            InputStream in = coverArtController.getClass().getResourceAsStream("default_cover.jpg");
            BufferedImage image = ImageIO.read(in);

            // create expected response body
            ImageIO.write(image, "jpeg", out);
            byte[] expected = out.toByteArray();

            // assertion
            assertArrayEquals(expected, actual);
        }
    }

    @Test
    @WithMockUser(username = AIRSONIC_USER, password = AIRSONIC_PASSWORD)
    public void getCoverArtFallbackWithSizeParameterTest() throws Exception {

        // add size parameter to query
        byte[] actual = mvc.perform(get("/coverArt")
                .param("size", "30"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // load default_cover.jpg image
            InputStream in = coverArtController.getClass().getResourceAsStream("default_cover.jpg");
            BufferedImage image = ImageIO.read(in);

            // scale expected image
            BufferedImage thumbImage = ImageUtil.scale(image, 30, 30);

            // create expected response body
            ImageIO.write(thumbImage, "jpeg", out);
            byte[] expected = out.toByteArray();

            // assertion
            assertArrayEquals(expected, actual);
        }
    }

    /** get cover art by id of media which type is album */
    @Test
    @WithMockUser(username = AIRSONIC_USER, password = AIRSONIC_PASSWORD)
    public void getCoverArtWithIntegerIdTest() throws Exception {

        final Integer MEDIA_ID = 100;
        // set up mocked media file
        MediaFile mockedMediaFile = new MediaFile();
        mockedMediaFile.setId(MEDIA_ID);
        mockedMediaFile.setMediaType(MediaType.ALBUM);

        // set up mocked cover art
        MediaFileCoverArtRequest request = new MediaFileCoverArtRequest(mockedCoverArt, mockedMediaFile);

        // set up mock bean
        doReturn(request).when(coverArtService).createMediaFileCoverArtRequest(anyInt(), anyInt());
        doReturn(IMAGE_RESOURCE.getFile().toPath()).when(mockedCoverArt).getFullPath();

        // prepare expected
        byte[] expected = IMAGE_RESOURCE.getInputStream().readAllBytes();

        // execution
        byte[] actual = mvc.perform(get("/coverArt")
                .param("id", MEDIA_ID.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        // assertion
        assertArrayEquals(expected, actual);
        verify(coverArtService).createMediaFileCoverArtRequest(eq(MEDIA_ID), eq(60));
        verify(coverArtService).getImageInputStreamWithType(eq(IMAGE_RESOURCE.getFile().toPath()));
        verifyNoMoreInteractions(coverArtService);

    }

    /** get cover art by id with album prerix "al-" */
    @Test
    @WithMockUser(username = AIRSONIC_USER, password = AIRSONIC_PASSWORD)
    public void getCoverArtWithAlbumIdTest() throws Exception {

        final int ALBUM_ID = 100;

        // set up mocked album
        Album mockedAlbum = new Album();
        mockedAlbum.setId(ALBUM_ID);

        // setup result
        AlbumCoverArtRequest request = new AlbumCoverArtRequest(mockedCoverArt, mockedAlbum);

        // set up mock
        doReturn(request).when(coverArtService).createAlbumCoverArtRequest(anyInt());
        doReturn(IMAGE_RESOURCE.getFile().toPath()).when(mockedCoverArt).getFullPath();

        // prepare expected
        byte[] expected = IMAGE_RESOURCE.getInputStream().readAllBytes();

        // execution
        byte[] actual = mvc.perform(get("/coverArt")
                .param("id", String.format("al-%d", ALBUM_ID)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        // assertion
        assertArrayEquals(expected, actual);
        verify(coverArtService).createAlbumCoverArtRequest(eq(ALBUM_ID));
        verify(coverArtService).getImageInputStreamWithType(eq(IMAGE_RESOURCE.getFile().toPath()));
        verifyNoMoreInteractions(coverArtService);
    }

    /** get cover art by id with artist prerix "ar-" */
    @Test
    @WithMockUser(username = AIRSONIC_USER, password = AIRSONIC_PASSWORD)
    public void getCoverArtWithArtistIdTest() throws Exception {

        final int ARTIST_ID = 100;

        // set up mocked album
        Artist mockedArtist = new Artist();
        mockedArtist.setId(ARTIST_ID);

        // set up mock
        ArtistCoverArtRequest request = new ArtistCoverArtRequest(mockedCoverArt, mockedArtist);
        doReturn(IMAGE_RESOURCE.getFile().toPath()).when(mockedCoverArt).getFullPath();
        doReturn(request).when(coverArtService).createArtistCoverArtRequest(anyInt());

        // prepare expected
        byte[] expected = IMAGE_RESOURCE.getInputStream().readAllBytes();

        // execution
        MvcResult result = mvc.perform(get("/coverArt")
                .param("id", String.format("ar-%d", ARTIST_ID)))
                .andExpect(status().isOk())
                .andReturn();
        byte[] actual = result.getResponse().getContentAsByteArray();

        // assertion
        assertArrayEquals(expected, actual);
        verify(coverArtService).createArtistCoverArtRequest(eq(ARTIST_ID));
        verify(coverArtService).getImageInputStreamWithType(eq(IMAGE_RESOURCE.getFile().toPath()));
        verifyNoMoreInteractions(coverArtService);
    }

    /** get cover art by id with playlist prerix "pl-" */
    @Test
    @WithMockUser(username = AIRSONIC_USER, password = AIRSONIC_PASSWORD)
    public void getCoverArtWithPlaylistIdTest() throws Exception {

        // set up mocked playlist
        final int PLAYLIST_ID = 100;
        Playlist mockedPlaylist = new Playlist();
        mockedPlaylist.setId(PLAYLIST_ID);
        mockedPlaylist.setName("playlist");

        // set up mock
        PlaylistCoverArtRequest request = new PlaylistCoverArtRequest(mockedCoverArt, mockedPlaylist);
        doReturn(request).when(coverArtService).createPlaylistCoverArtRequest(anyInt());
        doReturn(PLAYLIST_RESOURCE.getFile().toPath()).when(mockedCoverArt).getFullPath();

        // prepare expected
        byte[] expected = PLAYLIST_RESOURCE.getInputStream().readAllBytes();

        // execution
        MvcResult result = mvc.perform(get("/coverArt")
                .param("id", String.format("pl-%d", PLAYLIST_ID)))
                .andExpect(status().isOk())
                .andReturn();
        byte[] actual = result.getResponse().getContentAsByteArray();

        // assertion
        assertArrayEquals(expected, actual);
        verify(coverArtService).createPlaylistCoverArtRequest(eq(PLAYLIST_ID));
        verify(coverArtService).getImageInputStreamWithType(eq(PLAYLIST_RESOURCE.getFile().toPath()));
        verifyNoMoreInteractions(coverArtService);
    }

    /** get cover art by id with podcast prerix "pod-" */
    @Test
    @WithMockUser(username = AIRSONIC_USER, password = AIRSONIC_PASSWORD)
    public void getCoverArtWithPodcastIdTest() throws Exception {

        // set up mocked playlist
        final Integer PODCAST_ID = 100;
        final Integer PODCAST_MEDIA_FILE_ID = 101;
        MediaFile mockedMediaFile = new MediaFile();
        mockedMediaFile.setId(PODCAST_MEDIA_FILE_ID);

        // set up mock
        MediaFileCoverArtRequest request = new MediaFileCoverArtRequest(mockedCoverArt, mockedMediaFile);
        doReturn(request).when(coverArtService).createPodcastCoverArtRequest(anyInt(), anyInt());
        doReturn(PODCAST_RESOURCE.getFile().toPath()).when(mockedCoverArt).getFullPath();

        // prepare expected
        byte[] expected = PODCAST_RESOURCE.getInputStream().readAllBytes();

        // execution
        MvcResult result = mvc.perform(get("/coverArt")
                .param("id", String.format("pod-%d", PODCAST_ID)))
                .andExpect(status().isOk())
                .andReturn();
        byte[] actual = result.getResponse().getContentAsByteArray();

        // assertion
        assertArrayEquals(expected, actual);
        verify(coverArtService).createPodcastCoverArtRequest(eq(PODCAST_ID), eq(60));
        verify(coverArtService).getImageInputStreamWithType(eq(PODCAST_RESOURCE.getFile().toPath()));
        verifyNoMoreInteractions(coverArtService);
    }

}
