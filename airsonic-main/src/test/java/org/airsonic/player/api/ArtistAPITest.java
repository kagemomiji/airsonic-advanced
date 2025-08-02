/*
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

 Copyright 2025 (C) Y.Tory
 */
package org.airsonic.player.api;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.ArtistBio;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.AlbumService;
import org.airsonic.player.service.ArtistService;
import org.airsonic.player.service.CoverArtService;
import org.airsonic.player.service.JaxbContentService;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.LibraryStatusService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MusicIndexService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.RatingService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.transaction.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ArtistAPITest {

    private static final String AIRSONIC_USER = "admin";
    private static final String AIRSONIC_PASSWORD = "admin";
    private static final String EXPECTED_FORMAT = "json";
    private static final String AIRSONIC_API_VERSION = "1.15.0";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ArtistService artistService;

    @MockitoBean
    private MediaFolderService musicFolderService;

    @MockitoBean
    private AlbumService albumService;

    @MockitoBean
    private CoverArtService coverArtService;

    @MockitoBean
    private LastFmService lastFmService;

    @MockitoBean
    private LibraryStatusService libraryStatusService;

    @MockitoSpyBean
    private MusicIndexService musicIndexService;

    @MockitoBean
    private RatingService ratingService;

    @MockitoSpyBean
    private MediaFileService mediaFileService;

    @MockitoSpyBean
    private JaxbContentService jaxbContentService;

    @Autowired
    private PlayerService playerService;

    @TempDir
    private static Path tempAirsonicHome;

    MusicFolder testFolder = new MusicFolder(1, Paths.get("/test/folder"), "Test Folder", MusicFolder.Type.MEDIA, true, Instant.now());

    Instant created = Instant.now();

    Album testAlbum = new Album(
        1,                          // id
        "/test/folder/Test Album",  // path
        "Test Album",               // name
        "Test Artist",              // artist
        10,                         // songCount
        42.0,                       // duration
        2024,                       // year
        "Rock",                     // genre
        5,                          // playCount
        Instant.now(),              // lastPlayed
        "Test comment",             // comment
        created,              // created
        Instant.now(),              // lastScanned
        true,                       // present
        testFolder,                 // folder
        "mbid-123456"               // musicBrainzReleaseId
    );

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("airsonic.home", tempAirsonicHome.toString());
    }

    @Test
    public void getArtist_shouldReturnArtistDetails() throws Exception {

        String clientName = "getArtistClient";

        when(artistService.getArtist(eq(1))).thenReturn(
            new Artist(1, "Test Artist", 1, Instant.now(), true, testFolder)
        );
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER)))
            .thenReturn(List.of(testFolder));
        when(albumService.getAlbumsByArtist(eq("Test Artist"), eq(List.of(testFolder))))
            .thenReturn(List.of(testAlbum));
        when(coverArtService.getAlbumArt(1)).thenReturn(new CoverArt());

        String responseBody = mvc.perform(get("/rest/getArtist")
            .param("v", AIRSONIC_API_VERSION)
            .param("c", clientName)
            .param("u", AIRSONIC_USER)
            .param("p", AIRSONIC_PASSWORD)
            .param("f", EXPECTED_FORMAT)
            .param("id", "1")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
            .andExpect(jsonPath("$.subsonic-response.artist").exists())
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        DocumentContext json = JsonPath.parse(responseBody);
        String artistName = json.read("$.subsonic-response.artist.name");
        String artistId = json.read("$.subsonic-response.artist.id");
        String coverArtId = json.read("$.subsonic-response.artist.coverArt");
        int albumCount = json.read("$.subsonic-response.artist.albumCount");
        String albumName = json.read("$.subsonic-response.artist.album[0].name");
        String albumArtist = json.read("$.subsonic-response.artist.album[0].artist");
        int songCount = json.read("$.subsonic-response.artist.album[0].songCount");
        int year = json.read("$.subsonic-response.artist.album[0].year");
        String albumCreated = json.read("$.subsonic-response.artist.album[0].created");
        String genre = json.read("$.subsonic-response.artist.album[0].genre");
        int duration = json.read("$.subsonic-response.artist.album[0].duration");

        assertEquals("1", artistId);
        assertEquals("Test Album", albumName);
        assertEquals("ar-1", coverArtId);
        assertEquals(1, albumCount);
        assertEquals("Test Artist", artistName);
        assertEquals("Test Artist", albumArtist);
        assertEquals(10, songCount);
        assertEquals(2024, year);
        assertEquals("Rock", genre);

        // convert Instant to String for comparison
        String expectedCreated = created.truncatedTo(ChronoUnit.MILLIS).toString();
        String actualCreated = Instant.parse(albumCreated).truncatedTo(ChronoUnit.MILLIS).toString();
        assertEquals(expectedCreated, actualCreated);
        assertEquals(42, duration); // duration is int

        // Verify that the player is created with the expected properties
        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertEquals(1, players.size());
        WrapRequestUtil.assertRestAPIPlayer(
            players.get(0),
            clientName,
            AIRSONIC_USER
        );
    }

    @Test
    public void getArtist_shouldReturnNotFoundForInvalidId() throws Exception {
        String clientName = "getArtistClientError";

        when(artistService.getArtist(eq(999))).thenReturn(null);

        mvc.perform(get("/rest/getArtist")
            .param("v", AIRSONIC_API_VERSION)
            .param("c", clientName)
            .param("u", AIRSONIC_USER)
            .param("p", AIRSONIC_PASSWORD)
            .param("f", EXPECTED_FORMAT)
            .param("id", "999")
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.status").value("failed"))
            .andExpect(jsonPath("$.subsonic-response.error.message").value("Artist not found."))
            .andExpect(jsonPath("$.subsonic-response.error.code").value(70))
            .andDo(print());
    }

    @Test
    public void getArtists_shouldReturnArtistsList() throws Exception {
        String clientName = "getArtistsClient";
        Artist artist1 = new Artist(1, "Artist One", 2, Instant.now(), true, testFolder);
        Artist artist2 = new Artist(2, "Artist Two", 3, Instant.now(), true, testFolder);

        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), eq(null)))
            .thenReturn(List.of(testFolder));
        when(artistService.getAlphabeticalArtists(List.of(testFolder)))
            .thenReturn(List.of(artist1, artist2));
        when(coverArtService.getArtistArt(1)).thenReturn(new CoverArt());
        when(coverArtService.getArtistArt(2)).thenReturn(new CoverArt());

        when(artistService.getAlphabeticalArtists(List.of(testFolder)))
            .thenReturn(List.of(artist1, artist2));
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), eq(null)))
            .thenReturn(List.of(testFolder));
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER)))
            .thenReturn(List.of(testFolder));
        when(coverArtService.getArtistArt(1)).thenReturn(new CoverArt());
        when(coverArtService.getArtistArt(2)).thenReturn(new CoverArt());

        String responseBody = mvc.perform(get("/rest/getArtists")
            .param("v", AIRSONIC_API_VERSION)
            .param("c", clientName)
            .param("u", AIRSONIC_USER)
            .param("p", AIRSONIC_PASSWORD)
            .param("f", EXPECTED_FORMAT)
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
            .andExpect(jsonPath("$.subsonic-response.artists").exists())
            .andExpect(jsonPath("$.subsonic-response.artists.index[0].artist[0].name").value("Artist One"))
            .andExpect(jsonPath("$.subsonic-response.artists.index[0].artist[1].name").value("Artist Two"))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        DocumentContext json = JsonPath.parse(responseBody);
        String firstArtistName = json.read("$.subsonic-response.artists.index[0].artist[0].name");
        String secondArtistName = json.read("$.subsonic-response.artists.index[0].artist[1].name");
        String firstArtistId = json.read("$.subsonic-response.artists.index[0].artist[0].id");
        String secondArtistId = json.read("$.subsonic-response.artists.index[0].artist[1].id");
        String firstArtistCoverArt = json.read("$.subsonic-response.artists.index[0].artist[0].coverArt");
        String secondArtistCoverArt = json.read("$.subsonic-response.artists.index[0].artist[1].coverArt");
        int firstArtistAlbumCount = json.read("$.subsonic-response.artists.index[0].artist[0].albumCount");
        int secondArtistAlbumCount = json.read("$.subsonic-response.artists.index[0].artist[1].albumCount");
        String ignoredArticles = json.read("$.subsonic-response.artists.ignoredArticles");
        String indexName = json.read("$.subsonic-response.artists.index[0].name");
        assertEquals("Artist One", firstArtistName);
        assertEquals("Artist Two", secondArtistName);
        assertEquals("1", firstArtistId);
        assertEquals("2", secondArtistId);
        assertEquals("ar-1", firstArtistCoverArt);
        assertEquals("ar-2", secondArtistCoverArt);
        assertEquals(2, firstArtistAlbumCount);
        assertEquals(3, secondArtistAlbumCount);
        assertEquals("The El La Los Las Le Les", ignoredArticles);
        assertEquals("A", indexName);

        // Verify that the player is created with the expected properties
        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertEquals(1, players.size());
        WrapRequestUtil.assertRestAPIPlayer(
            players.get(0),
            clientName,
            AIRSONIC_USER
        );
    }

    @Test
    public void getArtists_shouldReturnNoIndexWhenNoArtists() throws Exception {
        String clientName = "getArtistsEmptyClient";
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), eq(null)))
            .thenReturn(List.of(testFolder));
        when(artistService.getAlphabeticalArtists(List.of(testFolder)))
            .thenReturn(List.of());

        String responseBody = mvc.perform(get("/rest/getArtists")
            .param("v", AIRSONIC_API_VERSION)
            .param("c", clientName)
            .param("u", AIRSONIC_USER)
            .param("p", AIRSONIC_PASSWORD)
            .param("f", EXPECTED_FORMAT)
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        DocumentContext json = JsonPath.parse(responseBody);

        // Assert that the "index" key does not exist
        assertFalse(json.read("$.subsonic-response.artists").toString().contains("index"));
    }

    @Test
    public void getArtistInfo_shouldReturnArtistInfo() throws Exception {
        String clientName = "getArtistInfoClient";
        int mediaFileId = 1;
        String artistName = "Test Artist";
        String musicBrainzId = "mbid-artist-123";
        String lastFmUrl = "https://last.fm/artist/test";
        String biography = "This is a test biography.";
        Instant starredDate = Instant.now().minus(1, ChronoUnit.DAYS);

        // Mock MediaFile and dependencies
        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(mediaFileId);
        mediaFile.setTitle(artistName);
        mediaFile.setFolder(testFolder);

        // similar artist mediafile
        MediaFile similarFile = new MediaFile();
        similarFile.setId(2);
        similarFile.setTitle("Similar Artist A");
        similarFile.setFolder(testFolder);

        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER)))
            .thenReturn(List.of(testFolder));
        when(artistService.getArtistImageUrlByMediaFile(
                org.mockito.ArgumentMatchers.anyString(),
                eq(mediaFile),
                eq(34),
                eq(AIRSONIC_USER)))
            .thenReturn("http://localhost/img/small.jpg");
        when(artistService.getArtistImageUrlByMediaFile(
                org.mockito.ArgumentMatchers.anyString(),
                eq(mediaFile),
                eq(64),
                eq(AIRSONIC_USER)))
            .thenReturn("http://localhost/img/medium.jpg");
        when(artistService.getArtistImageUrlByMediaFile(
                org.mockito.ArgumentMatchers.anyString(),
                eq(mediaFile),
                eq(300),
                eq(AIRSONIC_USER)))
            .thenReturn("http://localhost/img/large.jpg");


        // Mock MediaFileService
        when(mediaFileService.getMediaFile(eq(mediaFileId))).thenReturn(mediaFile);

        // Mock LastFmService
        ArtistBio artistBio = new ArtistBio(
            biography,
            musicBrainzId,
            lastFmUrl,
            "http://lastfm/img/small.jpg",
            "http://lastfm/img/medium.jpg",
            "http://lastfm/img/large.jpg"
        );

        when(lastFmService.getArtistBioByMediaFile(eq(mediaFile), any()))
            .thenReturn(artistBio);
        when(lastFmService.getSimilarArtistsByMediaFile(eq(mediaFile), eq(20), eq(false), eq(List.of(testFolder))))
            .thenReturn(List.of(similarFile));

        // starred date
        when(mediaFileService.getMediaFileStarredDate(eq(mediaFile), eq(AIRSONIC_USER)))
            .thenReturn(starredDate);

        // Actually perform the request
        String responseBody = mvc.perform(get("/rest/getArtistInfo")
            .param("v", AIRSONIC_API_VERSION)
            .param("c", clientName)
            .param("u", AIRSONIC_USER)
            .param("p", AIRSONIC_PASSWORD)
            .param("f", EXPECTED_FORMAT)
            .param("id", String.valueOf(mediaFileId))
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
            .andExpect(jsonPath("$.subsonic-response.artistInfo").exists())
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        DocumentContext json = JsonPath.parse(responseBody);
        // Assert artist info
        assertEquals(biography, json.read("$.subsonic-response.artistInfo.biography"));
        assertEquals(musicBrainzId, json.read("$.subsonic-response.artistInfo.musicBrainzId"));
        assertEquals(lastFmUrl, json.read("$.subsonic-response.artistInfo.lastFmUrl"));
        assertEquals("http://localhost/img/small.jpg", json.read("$.subsonic-response.artistInfo.smallImageUrl"));
        assertEquals("http://localhost/img/medium.jpg", json.read("$.subsonic-response.artistInfo.mediumImageUrl"));
        assertEquals("http://localhost/img/large.jpg", json.read("$.subsonic-response.artistInfo.largeImageUrl"));
        // Assert similar artists
        assertEquals("Similar Artist A", json.read("$.subsonic-response.artistInfo.similarArtist[0].name"));
        assertEquals("2", json.read("$.subsonic-response.artistInfo.similarArtist[0].id"));
        assertEquals(starredDate.truncatedTo(ChronoUnit.MILLIS).toString(),
            json.read("$.subsonic-response.artistInfo.similarArtist[0].starred"));
    }

    @Test
    public void getArtistInfo_shouldReturnNotFoundForInvalidId() throws Exception {
        String clientName = "getArtistInfoClientError";
        int invalidId = 9999;

        org.airsonic.player.service.MediaFileService mediaFileService = org.mockito.Mockito.mock(org.airsonic.player.service.MediaFileService.class);
        when(mediaFileService.getMediaFile(eq(invalidId))).thenReturn(null);

        mvc.perform(get("/rest/getArtistInfo")
            .param("v", AIRSONIC_API_VERSION)
            .param("c", clientName)
            .param("u", AIRSONIC_USER)
            .param("p", AIRSONIC_PASSWORD)
            .param("f", EXPECTED_FORMAT)
            .param("id", String.valueOf(invalidId))
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.status").value("failed"))
            .andExpect(jsonPath("$.subsonic-response.error.message").value("Media file not found."))
            .andExpect(jsonPath("$.subsonic-response.error.code").value(70))
            .andDo(print());
    }

    @Test
    public void getArtistInfo2_shouldReturnArtistInfo2() throws Exception {
        String clientName = "getArtistInfo2Client";
        int artistId = 1;
        String artistName = "Test Artist";
        String musicBrainzId = "mbid-artist-456";
        String lastFmUrl = "https://last.fm/artist/test2";
        String biography = "This is another test biography.";
        Instant starredDate = Instant.now().minus(2, ChronoUnit.DAYS);

        Artist artist = new Artist(artistId, artistName, 1, Instant.now(), true, testFolder);
        Artist similarArtist = new Artist(2, "Similar Artist B", 2, Instant.now(), true, testFolder);

        when(artistService.getArtist(eq(artistId))).thenReturn(artist);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER)))
            .thenReturn(List.of(testFolder));
        when(lastFmService.getSimilarArtists(eq(artist), eq(20), eq(false), eq(List.of(testFolder))))
            .thenReturn(List.of(similarArtist));
        when(lastFmService.getArtistBio(eq(artist), any()))
            .thenReturn(new ArtistBio(
                biography,
                musicBrainzId,
                lastFmUrl,
                "http://lastfm/img/small2.jpg",
                "http://lastfm/img/medium2.jpg",
                "http://lastfm/img/large2.jpg"
            ));
        when(artistService.getArtistImageURL(any(), eq(artistName), eq(34), eq(AIRSONIC_USER)))
            .thenReturn("http://localhost/img/small2.jpg");
        when(artistService.getArtistImageURL(any(), eq(artistName), eq(64), eq(AIRSONIC_USER)))
            .thenReturn("http://localhost/img/medium2.jpg");
        when(artistService.getArtistImageURL(any(), eq(artistName), eq(300), eq(AIRSONIC_USER)))
            .thenReturn("http://localhost/img/large2.jpg");
        when(artistService.getStarredDate(eq(similarArtist.getId()), eq(AIRSONIC_USER)))
            .thenReturn(starredDate);
        when(coverArtService.getArtistArt(eq(similarArtist.getId()))).thenReturn(new CoverArt());

        String responseBody = mvc.perform(get("/rest/getArtistInfo2")
            .param("v", AIRSONIC_API_VERSION)
            .param("c", clientName)
            .param("u", AIRSONIC_USER)
            .param("p", AIRSONIC_PASSWORD)
            .param("f", EXPECTED_FORMAT)
            .param("id", String.valueOf(artistId))
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
            .andExpect(jsonPath("$.subsonic-response.artistInfo2").exists())
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        DocumentContext json = JsonPath.parse(responseBody);
        assertEquals(biography, json.read("$.subsonic-response.artistInfo2.biography"));
        assertEquals(musicBrainzId, json.read("$.subsonic-response.artistInfo2.musicBrainzId"));
        assertEquals(lastFmUrl, json.read("$.subsonic-response.artistInfo2.lastFmUrl"));
        assertEquals("http://localhost/img/small2.jpg", json.read("$.subsonic-response.artistInfo2.smallImageUrl"));
        assertEquals("http://localhost/img/medium2.jpg", json.read("$.subsonic-response.artistInfo2.mediumImageUrl"));
        assertEquals("http://localhost/img/large2.jpg", json.read("$.subsonic-response.artistInfo2.largeImageUrl"));
        assertEquals("Similar Artist B", json.read("$.subsonic-response.artistInfo2.similarArtist[0].name"));
        assertEquals("2", json.read("$.subsonic-response.artistInfo2.similarArtist[0].id"));
        assertEquals(starredDate.truncatedTo(ChronoUnit.MILLIS).toString(),
            json.read("$.subsonic-response.artistInfo2.similarArtist[0].starred"));

        // Verify that the player is created with the expected properties
        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertEquals(1, players.size());
        WrapRequestUtil.assertRestAPIPlayer(
            players.get(0),
            clientName,
            AIRSONIC_USER
        );
    }

    @Test
    public void getArtistInfo2_shouldReturnNotFoundForInvalidId() throws Exception {
        String clientName = "getArtistInfo2ClientError";
        int invalidId = 9999;

        when(artistService.getArtist(eq(invalidId))).thenReturn(null);

        mvc.perform(get("/rest/getArtistInfo2")
            .param("v", AIRSONIC_API_VERSION)
            .param("c", clientName)
            .param("u", AIRSONIC_USER)
            .param("p", AIRSONIC_PASSWORD)
            .param("f", EXPECTED_FORMAT)
            .param("id", String.valueOf(invalidId))
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.status").value("failed"))
            .andExpect(jsonPath("$.subsonic-response.error.message").value("Artist not found."))
            .andExpect(jsonPath("$.subsonic-response.error.code").value(70))
            .andDo(print());
    }

    @Test
    public void getArtistInfo2_shouldReturnEmptySimilarArtistsIfNone() throws Exception {
        String clientName = "getArtistInfo2NoSimilar";
        int artistId = 3;
        String artistName = "Lonely Artist";

        Artist artist = new Artist(artistId, artistName, 0, Instant.now(), true, testFolder);

        when(artistService.getArtist(eq(artistId))).thenReturn(artist);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER)))
            .thenReturn(List.of(testFolder));
        when(lastFmService.getSimilarArtists(eq(artist), eq(20), eq(false), eq(List.of(testFolder))))
            .thenReturn(List.of());
        when(artistService.getArtistImageURL(any(), eq(artistName), eq(34), eq(AIRSONIC_USER)))
            .thenReturn("http://localhost/img/small3.jpg");
        when(artistService.getArtistImageURL(any(), eq(artistName), eq(64), eq(AIRSONIC_USER)))
            .thenReturn("http://localhost/img/medium3.jpg");
        when(artistService.getArtistImageURL(any(), eq(artistName), eq(300), eq(AIRSONIC_USER)))
            .thenReturn("http://localhost/img/large3.jpg");

        String responseBody = mvc.perform(get("/rest/getArtistInfo2")
            .param("v", AIRSONIC_API_VERSION)
            .param("c", clientName)
            .param("u", AIRSONIC_USER)
            .param("p", AIRSONIC_PASSWORD)
            .param("f", EXPECTED_FORMAT)
            .param("id", String.valueOf(artistId))
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
            .andExpect(jsonPath("$.subsonic-response.artistInfo2").exists())
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        DocumentContext json = JsonPath.parse(responseBody);

        // Should be empty or not present
        assertEquals("http://localhost/img/small3.jpg", json.read("$.subsonic-response.artistInfo2.smallImageUrl"));
        assertEquals("http://localhost/img/medium3.jpg", json.read("$.subsonic-response.artistInfo2.mediumImageUrl"));
        assertEquals("http://localhost/img/large3.jpg", json.read("$.subsonic-response.artistInfo2.largeImageUrl"));
        String artistInfo2 = json.read("$.subsonic-response.artistInfo2").toString();
        assertFalse(artistInfo2.contains("similarArtist"));
        assertFalse(artistInfo2.contains("biography"));
        assertFalse(artistInfo2.contains("musicBrainzId"));
        assertFalse(artistInfo2.contains("lastFmUrl"));
    }
}
