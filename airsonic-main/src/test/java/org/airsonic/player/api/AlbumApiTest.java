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

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.AlbumService;
import org.airsonic.player.service.JaxbContentService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.RatingService;
import org.airsonic.player.service.SearchService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.subsonic.restapi.AlbumID3;
import org.subsonic.restapi.AlbumList;
import org.subsonic.restapi.AlbumWithSongsID3;
import org.subsonic.restapi.Child;

import jakarta.transaction.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class AlbumApiTest {

    private static final String AIRSONIC_USER = "admin";
    private static final String AIRSONIC_PASSWORD = "admin";
    private static final String EXPECTED_FORMAT = "json";
    private static final String AIRSONIC_API_VERSION = "1.15.0";

    @Autowired
    private MockMvc mvc;

    @TempDir
    private static Path tempAirsonicHome;

    @MockitoBean
    private AlbumService albumService;

    @MockitoBean
    private JaxbContentService jaxbContentService;

    @MockitoSpyBean
    private MediaFileService mediaFileService;

    @MockitoBean
    private RatingService ratingService;

    @MockitoBean
    private SearchService searchService;

    @MockitoBean
    private MediaFolderService musicFolderService;

    @Autowired
    private PlayerService playerService;

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("airsonic.home", tempAirsonicHome.toString());
    }

    MusicFolder testFolder = new MusicFolder(1, Paths.get("/test/folder"), "Test Folder", MusicFolder.Type.MEDIA, true, Instant.now());

    ObjectMapper objectMapper = JsonMapper.builder()
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
        .build();

    private final String clientName = "getAlbumTestsClient";

    // Test data setup
    Album testAlbum = new Album(
        "path/to/test/album",
        "Test Album",
        "Test Artist",
        Instant.now(),
        Instant.now(),
        true,
        testFolder
    );
    MediaFile testSong = new MediaFile();

    /**
     * tests for the /rest/getAlbum endpoint
     * This endpoint retrieves an album by its ID, including its songs.
     */

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getAlbum", "/rest/getAlbum.view"})
    void getAlbum_validId_returnsAlbum(String endpoint) throws Exception {

        // check player is empty
        List<Player> initPlayers = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertEquals(0, initPlayers.size());
        // Arrange: Insert a test album and song into the database or mock the services if possible.
        int testAlbumId = 1; // Replace with actual test data setup if needed

        when(albumService.getAlbum(eq(testAlbumId)))
            .thenReturn(testAlbum);

        when(mediaFileService.getSongsForAlbum(eq("Test Artist"), eq("Test Album")))
            .thenReturn(List.of(testSong));

        AlbumWithSongsID3 testAlbumWithSongs = TestApiUtil.createTestAlbumWithSongsID3Full(testAlbumId);
        when(jaxbContentService.createJaxbAlbum(any(), eq(testAlbum), eq(AIRSONIC_USER)))
            .thenReturn(testAlbumWithSongs);
        Child testSongChild = TestApiUtil.createTestMusicChild();
        when(jaxbContentService.createJaxbChild(any(), eq(testSong), eq(AIRSONIC_USER)))
            .thenReturn(testSongChild);

        String response = mvc.perform(get(endpoint)
                .param("id", String.valueOf(testAlbumId))
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.subsonic-response.album").exists())
            .andExpect(jsonPath("$.subsonic-response.album.id", is(String.valueOf(testAlbumId))))
            .andExpect(jsonPath("$.subsonic-response.album.song").isArray())
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        AlbumWithSongsID3 resultAlbumWithSongs = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("album")
            .traverse(objectMapper)
            .readValueAs(AlbumWithSongsID3.class);

        // create expeced
        // testAlbumwithSongs.song is already added by mockMvc
        assertAlbumWithSongs(testAlbumWithSongs, resultAlbumWithSongs);

        // check player is created
        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertEquals(1, players.size());
        WrapRequestUtil.assertRestAPIPlayer(
            players.get(0),
            clientName,
            AIRSONIC_USER
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getAlbum", "/rest/getAlbum.view"})
    void getAlbum_validIdWithNoSongs_returnsAlbumWithoutSongs(String endpoint) throws Exception {
        int testAlbumId = 2; // Replace with actual test data setup if needed

        when(albumService.getAlbum(eq(testAlbumId)))
            .thenReturn(testAlbum);

        when(mediaFileService.getSongsForAlbum(eq("Test Artist"), eq("Test Album")))
            .thenReturn(List.of()); // No songs for this album

        AlbumWithSongsID3 testAlbumWithSongs = TestApiUtil.createTestAlbumWithSongsID3Minimum(testAlbumId);
        when(jaxbContentService.createJaxbAlbum(any(), eq(testAlbum), eq(AIRSONIC_USER)))
            .thenReturn(testAlbumWithSongs);

        String response = mvc.perform(get(endpoint)
                .param("id", String.valueOf(testAlbumId))
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.album").exists())
            .andExpect(jsonPath("$.subsonic-response.album.id", is(String.valueOf(testAlbumId))))
            .andExpect(jsonPath("$.subsonic-response.album.song").doesNotExist())
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        AlbumWithSongsID3 resultAlbumWithSongs = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("album")
            .traverse(objectMapper)
            .readValueAs(AlbumWithSongsID3.class);

        assertAlbumWithSongs(testAlbumWithSongs, resultAlbumWithSongs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getAlbum", "/rest/getAlbum.view"})
    void getAlbum_invalidId_returnsNotFound(String endpoint) throws Exception {
        int invalidAlbumId = 999999;

        when(albumService.getAlbum(eq(invalidAlbumId)))
            .thenReturn(null);

        mvc.perform(get(endpoint)
                .param("id", String.valueOf(invalidAlbumId))
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.error").exists())
            .andExpect(jsonPath("$.subsonic-response.error.code", is(70)))
            .andExpect(jsonPath("$.subsonic-response.error.message", containsString("Album not found")));
    }

    /**
     * Utility method to assert that two AlbumWithSongsID3 objects are equal.
     * This checks all relevant fields to ensure they match.
     *
     * @param expected the expected AlbumWithSongsID3 object
     * @param actual   the actual AlbumWithSongsID3 object
     */
    private void assertAlbumWithSongs(AlbumWithSongsID3 expected, AlbumWithSongsID3 actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getArtist(), actual.getArtist());
        assertEquals(expected.getSongCount(), actual.getSongCount());
        assertEquals(expected.getDuration(), actual.getDuration());
        assertEquals(expected.getYear(), actual.getYear());
        assertEquals(expected.getGenre(), actual.getGenre());
        assertEquals(expected.getPlayCount(), actual.getPlayCount());
        assertEquals(expected.getCreated(), actual.getCreated());
        assertEquals(expected.getSong().size(), actual.getSong().size());

        // Check songs
        for (int i = 0; i < expected.getSong().size(); i++) {
            Child expectedSong = expected.getSong().get(i);
            Child actualSong = actual.getSong().get(i);
            assertChild(expectedSong, actualSong);
        }
    }

    /**
     * Utility method to assert that two Child objects are equal.
     * This checks all relevant fields to ensure they match.
     *
     * @param expected the expected Child object
     * @param actual   the actual Child object
     */
    private void assertChild(Child expected, Child actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getTitle(), actual.getTitle());
        assertEquals(expected.getArtist(), actual.getArtist());
        assertEquals(expected.getAlbum(), actual.getAlbum());
        assertEquals(expected.isIsDir(), actual.isIsDir());
        assertEquals(expected.isIsVideo(), actual.isIsVideo());
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getSuffix(), actual.getSuffix());
        assertEquals(expected.getContentType(), actual.getContentType());
        assertEquals(expected.getOriginalWidth(), actual.getOriginalWidth());
        assertEquals(expected.getOriginalHeight(), actual.getOriginalHeight());
        assertEquals(expected.getCoverArt(), actual.getCoverArt());
        assertEquals(expected.getYear(), actual.getYear());
        assertEquals(expected.getGenre(), actual.getGenre());
        assertEquals(expected.getCreated(), actual.getCreated());
        assertEquals(expected.getStarred(), actual.getStarred());
        assertEquals(expected.getUserRating(), actual.getUserRating());
        assertEquals(expected.getAverageRating(), actual.getAverageRating());
        assertEquals(expected.getPlayCount(), actual.getPlayCount());
        assertEquals(expected.getDuration(), actual.getDuration());
    }

    /**
     * Tests for the /rest/getAlbumList endpoint
     * This endpoint retrieves a list of albums based on various criteria.
     */

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getAlbumList", "/rest/getAlbumList.view"})
    void getAlbumList_highestType_returnsAlbumList(String endpoint) throws Exception {
        MediaFile album = new MediaFile();
        album.setId(1);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(ratingService.getHighestRatedAlbums(eq(0), eq(10), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        Child albumChild = TestApiUtil.createTestDirectoryChild();
        when(jaxbContentService.createJaxbChild(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(albumChild);

        String response = mvc.perform(get(endpoint)
                .param("type", "highest")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList.album[0].id", is(albumChild.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        AlbumList albumList = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList")
            .traverse(objectMapper)
            .readValueAs(AlbumList.class);

        assertEquals(1, albumList.getAlbum().size());
        assertChild(albumChild, albumList.getAlbum().get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getAlbumList", "/rest/getAlbumList.view"})
    void getAlbumList_recentType_returnsAlbumList(String endpoint) throws Exception {
        MediaFile album = new MediaFile();
        album.setId(2);
        when(mediaFileService.getMostRecentlyPlayedAlbums(eq(0), eq(10), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));

        Child albumChild = TestApiUtil.createTestDirectoryChild();
        when(jaxbContentService.createJaxbChild(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(albumChild);

        String response = mvc.perform(get(endpoint)
                .param("type", "recent")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList.album[0].id", is(albumChild.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        AlbumList albumList = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList")
            .traverse(objectMapper)
            .readValueAs(AlbumList.class);
        assertEquals(1, albumList.getAlbum().size());
        assertChild(albumChild, albumList.getAlbum().get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getAlbumList", "/rest/getAlbumList.view" })
    void getAlbumList_frequentType_returnsAlbumList(String endpoint) throws Exception {
        MediaFile album = new MediaFile();
        album.setId(3);
        when(mediaFileService.getMostFrequentlyPlayedAlbums(eq(0), eq(10), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        Child albumChild = TestApiUtil.createTestDirectoryChild();
        when(jaxbContentService.createJaxbChild(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(albumChild);

        String response = mvc.perform(get(endpoint)
                .param("type", "frequent")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList.album[0].id", is(albumChild.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        AlbumList albumList = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList")
            .traverse(objectMapper)
            .readValueAs(AlbumList.class);
        assertEquals(1, albumList.getAlbum().size());
        assertChild(albumChild, albumList.getAlbum().get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getAlbumList", "/rest/getAlbumList.view" })
    void getAlbumList_newestType_returnsAlbumList(String endpoint) throws Exception {
        MediaFile album = new MediaFile();
        album.setId(4);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(mediaFileService.getNewestAlbums(eq(0), eq(10), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        Child albumChild = TestApiUtil.createTestDirectoryChild();
        when(jaxbContentService.createJaxbChild(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(albumChild);

        String response = mvc.perform(get(endpoint)
                .param("type", "newest")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList.album[0].id", is(albumChild.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        AlbumList albumList = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList")
            .traverse(objectMapper)
            .readValueAs(AlbumList.class);
        assertEquals(1, albumList.getAlbum().size());
        assertChild(albumChild, albumList.getAlbum().get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getAlbumList", "/rest/getAlbumList.view" })
    void getAlbumList_starredType_returnsAlbumList(String endpoint) throws Exception {
        MediaFile album = new MediaFile();
        album.setId(5);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(mediaFileService.getStarredAlbums(eq(0), eq(10), eq(AIRSONIC_USER), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        Child albumChild = TestApiUtil.createTestDirectoryChild();
        when(jaxbContentService.createJaxbChild(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(albumChild);

        String response = mvc.perform(get(endpoint)
                .param("type", "starred")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList.album[0].id", is(albumChild.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        AlbumList albumList = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList")
            .traverse(objectMapper)
            .readValueAs(AlbumList.class);
        assertEquals(1, albumList.getAlbum().size());
        assertChild(albumChild, albumList.getAlbum().get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getAlbumList", "/rest/getAlbumList.view" })
    void getAlbumList_alphabeticalByArtistType_returnsAlbumList(String endpoint) throws Exception {
        MediaFile album = new MediaFile();
        album.setId(6);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(mediaFileService.getAlphabeticalAlbums(eq(0), eq(10), eq(true), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        Child albumChild = TestApiUtil.createTestDirectoryChild();
        when(jaxbContentService.createJaxbChild(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(albumChild);

        String response = mvc.perform(get(endpoint)
                .param("type", "alphabeticalByArtist")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList.album[0].id", is(albumChild.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        AlbumList albumList = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList")
            .traverse(objectMapper)
            .readValueAs(AlbumList.class);
        assertEquals(1, albumList.getAlbum().size());
        assertChild(albumChild, albumList.getAlbum().get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getAlbumList", "/rest/getAlbumList.view" })
    void getAlbumList_alphabeticalByNameType_returnsAlbumList(String endpoint) throws Exception {
        MediaFile album = new MediaFile();
        album.setId(7);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(mediaFileService.getAlphabeticalAlbums(eq(0), eq(10), eq(false), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        Child albumChild = TestApiUtil.createTestDirectoryChild();
        when(jaxbContentService.createJaxbChild(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(albumChild);

        String response = mvc.perform(get(endpoint)
                .param("type", "alphabeticalByName")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList.album[0].id", is(albumChild.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        AlbumList albumList = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList")
            .traverse(objectMapper)
            .readValueAs(AlbumList.class);
        assertEquals(1, albumList.getAlbum().size());
        assertChild(albumChild, albumList.getAlbum().get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getAlbumList", "/rest/getAlbumList.view" })
    void getAlbumList_byGenreType_returnsAlbumList(String endpoint) throws Exception {
        MediaFile album = new MediaFile();
        album.setId(8);
        String genre = "Rock";
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(mediaFileService.getAlbumsByGenre(eq(0), eq(10), eq(genre), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        Child albumChild = TestApiUtil.createTestDirectoryChild();
        when(jaxbContentService.createJaxbChild(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(albumChild);

        String response = mvc.perform(get(endpoint)
                .param("type", "byGenre")
                .param("genre", genre)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList.album[0].id", is(albumChild.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        AlbumList albumList = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList")
            .traverse(objectMapper)
            .readValueAs(AlbumList.class);
        assertEquals(1, albumList.getAlbum().size());
        assertChild(albumChild, albumList.getAlbum().get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getAlbumList", "/rest/getAlbumList.view" })
    void getAlbumList_byYearType_returnsAlbumList(String endpoint) throws Exception {
        MediaFile album = new MediaFile();
        album.setId(9);
        int fromYear = 2000;
        int toYear = 2010;
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(mediaFileService.getAlbumsByYear(eq(0), eq(10), eq(fromYear), eq(toYear), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        Child albumChild = TestApiUtil.createTestDirectoryChild();
        when(jaxbContentService.createJaxbChild(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(albumChild);

        String response = mvc.perform(get(endpoint)
                .param("type", "byYear")
                .param("fromYear", String.valueOf(fromYear))
                .param("toYear", String.valueOf(toYear))
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList.album[0].id", is(albumChild.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        AlbumList albumList = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList")
            .traverse(objectMapper)
            .readValueAs(AlbumList.class);
        assertEquals(1, albumList.getAlbum().size());
        assertChild(albumChild, albumList.getAlbum().get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getAlbumList", "/rest/getAlbumList.view" })
    void getAlbumList_randomType_returnsAlbumList(String endpoint) throws Exception {
        MediaFile album = new MediaFile();
        album.setId(10);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(searchService.getRandomAlbums(eq(10), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        Child albumChild = TestApiUtil.createTestDirectoryChild();
        when(jaxbContentService.createJaxbChild(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(albumChild);

        String response = mvc.perform(get(endpoint)
                .param("type", "random")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList.album[0].id", is(albumChild.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        AlbumList albumList = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList")
            .traverse(objectMapper)
            .readValueAs(AlbumList.class);
        assertEquals(1, albumList.getAlbum().size());
        assertChild(albumChild, albumList.getAlbum().get(0));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getAlbumList", "/rest/getAlbumList.view" })
    void getAlbumList_invalidType_returnsError(String endpoint) throws Exception {
        mvc.perform(get(endpoint)
                .param("type", "invalidType")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.error").exists())
            .andExpect(jsonPath("$.subsonic-response.error.code", is(0)))
            .andExpect(jsonPath("$.subsonic-response.error.message",
                containsString("Invalid list type: invalidType")))
            .andDo(print());
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getAlbumList", "/rest/getAlbumList.view" })
    void getAlbumList_noType_returnsError(String endpoint) throws Exception {
        mvc.perform(get(endpoint)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.error").exists())
            .andExpect(jsonPath("$.subsonic-response.error.code", is(10)))
            .andExpect(jsonPath("$.subsonic-response.error.message",
                containsString("Required param (type) is missing")))
            .andDo(print());
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getAlbumList", "/rest/getAlbumList.view" })
    void getAlbumList_withOver500Size_returnsAlbumList(String endpoint) throws Exception {
        MediaFile album = new MediaFile();
        album.setId(11);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(mediaFileService.getMostFrequentlyPlayedAlbums(eq(0), eq(500), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        Child albumChild = TestApiUtil.createTestDirectoryChild();
        when(jaxbContentService.createJaxbChild(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(albumChild);

        String response = mvc.perform(get(endpoint)
                .param("type", "frequent")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT)
                .param("size", "1000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList.album[0].id", is(albumChild.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        AlbumList albumList = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList")
            .traverse(objectMapper)
            .readValueAs(AlbumList.class);
        assertEquals(1, albumList.getAlbum().size());
        assertChild(albumChild, albumList.getAlbum().get(0));
    }

    /**
     * Tests for the /rest/getAlbumList2 endpoint
     * This endpoint retrieves a list of albums with additional details.
     */

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getAlbumList2", "/rest/getAlbumList2.view"})
    void getAlbumList2_frequentType_returnsAlbumList2(String endpoint) throws Exception {
        Album album = new Album();
        album.setId(2);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(albumService.getMostFrequentlyPlayedAlbums(eq(0), eq(10), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        AlbumID3 testJaxbAlbum = TestApiUtil.createTestAlbumID3();
        when(jaxbContentService.createJaxbAlbum(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(testJaxbAlbum);

        String response = mvc.perform(get(endpoint)
                .param("type", "frequent")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList2.album[0].id", is(testJaxbAlbum.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        org.subsonic.restapi.AlbumList2 albumList2 = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList2")
            .traverse(objectMapper)
            .readValueAs(org.subsonic.restapi.AlbumList2.class);
        assertEquals(1, albumList2.getAlbum().size());
        assertEquals(testJaxbAlbum.getId(), albumList2.getAlbum().get(0).getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getAlbumList2", "/rest/getAlbumList2.view"})
    void getAlbumList2_recentType_returnsAlbumList2(String endpoint) throws Exception {
        Album album = new Album();
        album.setId(3);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(albumService.getMostResentlyPlayedAlbums(eq(0), eq(10), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        AlbumID3 testJaxbAlbum = TestApiUtil.createTestAlbumID3();
        when(jaxbContentService.createJaxbAlbum(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(testJaxbAlbum);

        String response = mvc.perform(get(endpoint)
                .param("type", "recent")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList2.album[0].id", is(testJaxbAlbum.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        org.subsonic.restapi.AlbumList2 albumList2 = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList2")
            .traverse(objectMapper)
            .readValueAs(org.subsonic.restapi.AlbumList2.class);
        assertEquals(1, albumList2.getAlbum().size());
        assertEquals(testJaxbAlbum.getId(), albumList2.getAlbum().get(0).getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getAlbumList2", "/rest/getAlbumList2.view"})
    void getAlbumList2_newestType_returnsAlbumList2(String endpoint) throws Exception {
        Album album = new Album();
        album.setId(4);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(albumService.getRecentlyAddedAlbums(eq(0), eq(10), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        AlbumID3 testJaxbAlbum = TestApiUtil.createTestAlbumID3();
        when(jaxbContentService.createJaxbAlbum(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(testJaxbAlbum);

        String response = mvc.perform(get(endpoint)
                .param("type", "newest")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList2.album[0].id", is(testJaxbAlbum.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        org.subsonic.restapi.AlbumList2 albumList2 = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList2")
            .traverse(objectMapper)
            .readValueAs(org.subsonic.restapi.AlbumList2.class);
        assertEquals(1, albumList2.getAlbum().size());
        assertEquals(testJaxbAlbum.getId(), albumList2.getAlbum().get(0).getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getAlbumList2", "/rest/getAlbumList2.view"})
    void getAlbumList2_alphabeticalByArtistType_returnsAlbumList2(String endpoint) throws Exception {
        Album album = new Album();
        album.setId(5);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(albumService.getAlphabeticalAlbums(eq(0), eq(10), eq(true), eq(false), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        AlbumID3 testJaxbAlbum = TestApiUtil.createTestAlbumID3();
        when(jaxbContentService.createJaxbAlbum(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(testJaxbAlbum);

        String response = mvc.perform(get(endpoint)
                .param("type", "alphabeticalByArtist")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList2.album[0].id", is(testJaxbAlbum.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        org.subsonic.restapi.AlbumList2 albumList2 = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList2")
            .traverse(objectMapper)
            .readValueAs(org.subsonic.restapi.AlbumList2.class);
        assertEquals(1, albumList2.getAlbum().size());
        assertEquals(testJaxbAlbum.getId(), albumList2.getAlbum().get(0).getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getAlbumList2", "/rest/getAlbumList2.view"})
    void getAlbumList2_alphabeticalByNameType_returnsAlbumList2(String endpoint) throws Exception {
        Album album = new Album();
        album.setId(6);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(albumService.getAlphabeticalAlbums(eq(0), eq(10), eq(false), eq(false), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        AlbumID3 testJaxbAlbum = TestApiUtil.createTestAlbumID3();
        when(jaxbContentService.createJaxbAlbum(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(testJaxbAlbum);

        String response = mvc.perform(get(endpoint)
                .param("type", "alphabeticalByName")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList2.album[0].id", is(testJaxbAlbum.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        org.subsonic.restapi.AlbumList2 albumList2 = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList2")
            .traverse(objectMapper)
            .readValueAs(org.subsonic.restapi.AlbumList2.class);
        assertEquals(1, albumList2.getAlbum().size());
        assertEquals(testJaxbAlbum.getId(), albumList2.getAlbum().get(0).getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getAlbumList2", "/rest/getAlbumList2.view"})
    void getAlbumList2_byGenreType_returnsAlbumList2(String endpoint) throws Exception {
        Album album = new Album();
        album.setId(7);
        String genre = "Jazz";
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(albumService.getAlbumsByGenre(eq(0), eq(10), eq(genre), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        AlbumID3 testJaxbAlbum = TestApiUtil.createTestAlbumID3();
        when(jaxbContentService.createJaxbAlbum(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(testJaxbAlbum);

        String response = mvc.perform(get(endpoint)
                .param("type", "byGenre")
                .param("genre", genre)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList2.album[0].id", is(testJaxbAlbum.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        org.subsonic.restapi.AlbumList2 albumList2 = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList2")
            .traverse(objectMapper)
            .readValueAs(org.subsonic.restapi.AlbumList2.class);
        assertEquals(1, albumList2.getAlbum().size());
        assertEquals(testJaxbAlbum.getId(), albumList2.getAlbum().get(0).getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getAlbumList2", "/rest/getAlbumList2.view"})
    void getAlbumList2_byYearType_returnsAlbumList2(String endpoint) throws Exception {
        Album album = new Album();
        album.setId(8);
        int fromYear = 1990;
        int toYear = 2000;
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(albumService.getAlbumsByYear(eq(0), eq(10), eq(fromYear), eq(toYear), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        AlbumID3 testJaxbAlbum = TestApiUtil.createTestAlbumID3();
        when(jaxbContentService.createJaxbAlbum(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(testJaxbAlbum);

        String response = mvc.perform(get(endpoint)
                .param("type", "byYear")
                .param("fromYear", String.valueOf(fromYear))
                .param("toYear", String.valueOf(toYear))
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList2.album[0].id", is(testJaxbAlbum.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        org.subsonic.restapi.AlbumList2 albumList2 = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList2")
            .traverse(objectMapper)
            .readValueAs(org.subsonic.restapi.AlbumList2.class);
        assertEquals(1, albumList2.getAlbum().size());
        assertEquals(testJaxbAlbum.getId(), albumList2.getAlbum().get(0).getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getAlbumList2", "/rest/getAlbumList2.view"})
    void getAlbumList2_starredType_returnsAlbumList2(String endpoint) throws Exception {
        Album album = new Album();
        album.setId(9);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(albumService.getStarredAlbums(eq(0), eq(10), eq(AIRSONIC_USER), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        AlbumID3 testJaxbAlbum = TestApiUtil.createTestAlbumID3();
        when(jaxbContentService.createJaxbAlbum(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(testJaxbAlbum);

        String response = mvc.perform(get(endpoint)
                .param("type", "starred")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList2.album[0].id", is(testJaxbAlbum.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        org.subsonic.restapi.AlbumList2 albumList2 = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList2")
            .traverse(objectMapper)
            .readValueAs(org.subsonic.restapi.AlbumList2.class);
        assertEquals(1, albumList2.getAlbum().size());
        assertEquals(testJaxbAlbum.getId(), albumList2.getAlbum().get(0).getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getAlbumList2", "/rest/getAlbumList2.view"})
    void getAlbumList2_randomType_returnsAlbumList2(String endpoint) throws Exception {
        Album album = new Album();
        album.setId(10);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(searchService.getRandomAlbumsId3(eq(10), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        AlbumID3 testJaxbAlbum = TestApiUtil.createTestAlbumID3();
        when(jaxbContentService.createJaxbAlbum(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(testJaxbAlbum);

        String response = mvc.perform(get(endpoint)
                .param("type", "random")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList2.album[0].id", is(testJaxbAlbum.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        org.subsonic.restapi.AlbumList2 albumList2 = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList2")
            .traverse(objectMapper)
            .readValueAs(org.subsonic.restapi.AlbumList2.class);
        assertEquals(1, albumList2.getAlbum().size());
        assertEquals(testJaxbAlbum.getId(), albumList2.getAlbum().get(0).getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getAlbumList2", "/rest/getAlbumList2.view"})
    void getAlbumList2_invalidType_returnsError(String endpoint) throws Exception {
        mvc.perform(get(endpoint)
                .param("type", "invalidType")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.error").exists())
            .andExpect(jsonPath("$.subsonic-response.error.code", is(0)))
            .andExpect(jsonPath("$.subsonic-response.error.message",
                containsString("Invalid list type: invalidType")))
            .andDo(print());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getAlbumList2", "/rest/getAlbumList2.view"})
    void getAlbumList2_noType_returnsError(String endpoint) throws Exception {
        mvc.perform(get(endpoint)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.error").exists())
            .andExpect(jsonPath("$.subsonic-response.error.code", is(10)))
            .andExpect(jsonPath("$.subsonic-response.error.message",
                containsString("Required param (type) is missing")))
            .andDo(print());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getAlbumList2", "/rest/getAlbumList2.view"})
    void getAlbumList2_withOver500Size_returnsAlbumList2(String endpoint) throws Exception {
        Album album = new Album();
        album.setId(11);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), any()))
            .thenReturn(List.of(testFolder));
        when(albumService.getMostFrequentlyPlayedAlbums(eq(0), eq(500), eq(List.of(testFolder))))
            .thenReturn(List.of(album));
        AlbumID3 testJaxbAlbum = TestApiUtil.createTestAlbumID3();
        when(jaxbContentService.createJaxbAlbum(any(), eq(album), eq(AIRSONIC_USER)))
            .thenReturn(testJaxbAlbum);

        String response = mvc.perform(get(endpoint)
                .param("type", "frequent")
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("f", EXPECTED_FORMAT)
                .param("size", "1000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.albumList2.album[0].id", is(testJaxbAlbum.getId())))
            .andDo(print())
            .andReturn()
            .getResponse()
            .getContentAsString();

        org.subsonic.restapi.AlbumList2 albumList2 = objectMapper
            .readTree(response)
            .path("subsonic-response")
            .path("albumList2")
            .traverse(objectMapper)
            .readValueAs(org.subsonic.restapi.AlbumList2.class);
        assertEquals(1, albumList2.getAlbum().size());
        assertEquals(testJaxbAlbum.getId(), albumList2.getAlbum().get(0).getId());
    }

}
