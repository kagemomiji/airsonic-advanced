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

  */
package org.airsonic.player.controller;

import org.airsonic.player.dao.AlbumDao;
import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.dao.PlaylistDao;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.CoverArt.EntityType;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.service.CoverArtService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.PodcastService;
import org.airsonic.player.util.HomeRule;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Y.Tory
 * @version $Id$
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class CoverArtControllerTest {

    @Autowired
    private MockMvc mvc;

    @SpyBean
    private MediaFileService mediaFileService;

    @MockBean
    private AlbumDao albumDao;

    @MockBean
    private ArtistDao artistDao;

    @MockBean
    private PlaylistDao playlistDao;

    @MockBean
    private PodcastService podcastService;

    @MockBean
    private CoverArtService coverArtService;

    @Mock
    private CoverArt mockedCoverArt;

    @InjectMocks
    private CoverArtController coverArtController;

    @ClassRule
    public static final HomeRule classRule = new HomeRule(); // sets airsonic.home to a temporary dir
    /** authentication info */
    private final String AIRSONIC_USER = "admin";
    private final String AIRSONIC_PASSWORD = "admin";

    /** resource */
    private final ClassPathResource IMAGE_RESOURCE = new ClassPathResource("MEDIAS/Music2/_DIR_ chrome hoof - 2004/Folder.jpg");
    private final ClassPathResource PLAYLIST_RESOURCE = new ClassPathResource("COVERARTS/playlist.png");
    private final ClassPathResource PODCAST_RESOURCE = new ClassPathResource("COVERARTS/podcast.png");

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
            BufferedImage thumbImage = CoverArtController.scale(image, 30, 30);

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
        CoverArt mockedCoverArt = new CoverArt(1, EntityType.ALBUM, IMAGE_RESOURCE.getFile().getAbsolutePath(), null, false);

        // set up mock bean
        when(mediaFileService.getMediaFile(anyInt())).thenReturn(mockedMediaFile);
        when(coverArtService.get(any(), anyInt())).thenReturn(mockedCoverArt);
        when(coverArtService.getFullPath(any())).thenReturn(IMAGE_RESOURCE.getFile().toPath());

        // prepare expected
        byte[] expected = IMAGE_RESOURCE.getInputStream().readAllBytes();

        // execution
        byte[] actual = mvc.perform(get("/coverArt")
                .param("id", MEDIA_ID.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        // assertion
        assertArrayEquals(expected, actual);
        verify(coverArtService).get(eq(EntityType.MEDIA_FILE), eq(MEDIA_ID));

    }

    /** get cover art by id with album prerix "al-" */
    @Test
    @WithMockUser(username = AIRSONIC_USER, password = AIRSONIC_PASSWORD)
    public void getCoverArtWithAlbumIdTest() throws Exception {

        final int ALBUM_ID = 100;

        // set up mocked cover art
        CoverArt mockedCoverArt = new CoverArt(1, EntityType.ALBUM, IMAGE_RESOURCE.getFile().getAbsolutePath(),null, false);

        // set up mocked album
        Album mockedAlbum = new Album();
        mockedAlbum.setId(ALBUM_ID);

        // set up mock
        when(albumDao.getAlbum(anyInt())).thenReturn(mockedAlbum);
        when(coverArtService.get(any(), anyInt())).thenReturn(mockedCoverArt);
        when(coverArtService.getFullPath(any())).thenReturn(IMAGE_RESOURCE.getFile().toPath());

        // prepare expected
        byte[] expected = IMAGE_RESOURCE.getInputStream().readAllBytes();

        // execution
        byte[] actual = mvc.perform(get("/coverArt")
                .param("id", String.format("al-%d", ALBUM_ID)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        // assertion
        assertArrayEquals(expected, actual);
        verify(albumDao).getAlbum(eq(ALBUM_ID));
        verify(coverArtService).get(eq(EntityType.ALBUM), eq(ALBUM_ID));
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
        when(artistDao.getArtist(anyInt())).thenReturn(mockedArtist);
        when(coverArtService.get(any(), anyInt())).thenReturn(mockedCoverArt);
        when(coverArtService.getFullPath(any())).thenReturn(IMAGE_RESOURCE.getFile().toPath());

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
        verify(coverArtService).get(eq(EntityType.ARTIST), eq(ARTIST_ID));
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
        when(playlistDao.getPlaylist(anyInt())).thenReturn(mockedPlaylist);

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
    }

    /** get cover art by id with podcast prerix "pod-" */
    @Test
    @WithMockUser(username = AIRSONIC_USER, password = AIRSONIC_PASSWORD)
    public void getCoverArtWithPodcastIdTest() throws Exception {


        // set up mocked playlist
        final Integer PODCAST_ID = 100;
        PodcastChannel mockedPodcast =
            new PodcastChannel(PODCAST_ID, "http://example.com", "podcast", "description", "http://example.com", null, "errorMessage", null);

        // set up mock
        when(podcastService.getChannel(anyInt())).thenReturn(mockedPodcast);

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
    }


}
