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
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.ArtistBio;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicIndex;
import org.airsonic.player.domain.MusicIndex.SortableArtistWithArtist;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.AlbumService;
import org.airsonic.player.service.ArtistService;
import org.airsonic.player.service.JaxbContentService;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MusicIndexService;
import org.airsonic.player.service.PlayerService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.subsonic.restapi.AlbumID3;
import org.subsonic.restapi.ArtistID3;
import org.subsonic.restapi.ArtistInfo2;
import org.subsonic.restapi.ArtistWithAlbumsID3;
import org.subsonic.restapi.ArtistsID3;
import org.subsonic.restapi.IndexID3;

import jakarta.transaction.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Collator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class ArtistApiTest {

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
    private LastFmService lastFmService;

    @MockitoSpyBean
    private MusicIndexService musicIndexService;

    @MockitoSpyBean
    private MediaFileService mediaFileService;

    @MockitoBean
    private JaxbContentService jaxbContentService;

    @Autowired
    private PlayerService playerService;

    @TempDir
    private static Path tempAirsonicHome;

    MusicFolder testFolder = new MusicFolder(1, Paths.get("/test/folder"), "Test Folder", MusicFolder.Type.MEDIA, true,
            Instant.now());

    ObjectMapper objectMapper = JsonMapper.builder()
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
            .build();

    Album testAlbum = new Album(
            1, // id
            "/test/folder/Test Album", // path
            "Test Album", // name
            "Test Artist", // artist
            10, // songCount
            42.0, // duration
            2024, // year
            "Rock", // genre
            5, // playCount
            Instant.now(), // lastPlayed
            "Test comment", // comment
            Instant.now(), // created
            Instant.now(), // lastScanned
            true, // present
            testFolder, // folder
            "mbid-123456" // musicBrainzReleaseId
    );

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("airsonic.home", tempAirsonicHome.toString());
    }

    /*
     * Tests for the /rest/getArtist and /rest/getArtist.view endpoints.
     */

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getArtist", "/rest/getArtist.view" })
    public void getArtist_shouldReturnArtistDetails(String endpoint) throws Exception {

        String clientName = "getArtistClient";

        // check player is not created
        List<Player> initialPlayers = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertTrue(initialPlayers.isEmpty());

        // mocking
        Artist artist = new Artist(1, "Test Artist", 1, Instant.now(), true, testFolder);

        when(artistService.getArtist(eq(1))).thenReturn(artist);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER)))
                .thenReturn(List.of(testFolder));
        when(albumService.getAlbumsByArtist(eq("Test Artist"), eq(List.of(testFolder))))
                .thenReturn(List.of(testAlbum));
        when(jaxbContentService.createJaxbArtist(any(ArtistID3.class), eq(artist), eq(AIRSONIC_USER)))
                .thenReturn(TestApiUtil.createTestArtistWithAlbumsID3Full(artist.getName()));
        when(jaxbContentService.createJaxbAlbum(any(AlbumID3.class), eq(testAlbum), eq(AIRSONIC_USER)))
                .thenReturn(TestApiUtil.createTestAlbumID3());

        String responseBody = mvc.perform(get(endpoint)
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

        ArtistWithAlbumsID3 artistWithAlbums = objectMapper
                .readTree(responseBody)
                .path("subsonic-response")
                .path("artist")
                .traverse(objectMapper)
                .readValueAs(ArtistWithAlbumsID3.class);

        ArtistWithAlbumsID3 expectedArtist = TestApiUtil.createTestArtistWithAlbumsID3Full("Test Artist");
        expectedArtist.getAlbum().add(TestApiUtil.createTestAlbumID3());

        assertAritistWithAlbumID3(expectedArtist, artistWithAlbums);
        // Verify that the player is created with the expected properties
        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertEquals(1, players.size());
        WrapRequestUtil.assertRestAPIPlayer(
                players.get(0),
                clientName,
                AIRSONIC_USER);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getArtist", "/rest/getArtist.view" })
    public void getArtistWithoutAlbums_shouldReturnArtistDetail(String endpoint) throws Exception {

        String clientName = "getArtistWithoutAlbumsClient";
        // check player is not created
        List<Player> initialPlayers = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertTrue(initialPlayers.isEmpty());

        // mocking
        Artist artist = new Artist(1, "Test Artist", 0, Instant.now(), true, testFolder);

        when(artistService.getArtist(eq(1))).thenReturn(artist);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER)))
                .thenReturn(List.of(testFolder));
        when(jaxbContentService.createJaxbArtist(any(ArtistID3.class), eq(artist), eq(AIRSONIC_USER)))
                .thenReturn(TestApiUtil.createTestArtistWithAlbumsID3Minimum(artist.getName()));
        when(albumService.getAlbumsByArtist(eq("Test Artist"), eq(List.of(testFolder))))
                .thenReturn(List.of());

        String responseBody = mvc.perform(get(endpoint)
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

        ArtistWithAlbumsID3 artistWithAlbums = objectMapper
                .readTree(responseBody)
                .path("subsonic-response")
                .path("artist")
                .traverse(objectMapper)
                .readValueAs(ArtistWithAlbumsID3.class);

        ArtistWithAlbumsID3 expectedArtist = TestApiUtil.createTestArtistWithAlbumsID3Minimum("Test Artist");
        assertAritistWithAlbumID3(expectedArtist, artistWithAlbums);

        // Verify that the player is created with the expected properties
        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertEquals(1, players.size());
        WrapRequestUtil.assertRestAPIPlayer(
                players.get(0),
                clientName,
                AIRSONIC_USER);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getArtist", "/rest/getArtist.view" })
    public void getArtist_shouldReturnNotFoundForInvalidId(String endpoint) throws Exception {
        String clientName = "getArtistClientError";

        // check player is not created
        List<Player> initialPlayers = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertTrue(initialPlayers.isEmpty());

        when(artistService.getArtist(eq(999))).thenReturn(null);

        mvc.perform(get(endpoint)
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

        // Verify that player was created
        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertEquals(1, players.size());
        WrapRequestUtil.assertRestAPIPlayer(
                players.get(0),
                clientName,
                AIRSONIC_USER);

    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getArtist", "/rest/getArtist.view" })
    public void getArtistWithoutId_shouldReturnFailed(String endpoint) throws Exception {
        String clientName = "getArtistWithoutIdClient";

        // check player is not created
        List<Player> initialPlayers = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertTrue(initialPlayers.isEmpty());

        mvc.perform(get(endpoint)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("f", EXPECTED_FORMAT)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("failed"))
                .andExpect(jsonPath("$.subsonic-response.error.message").value("Required param (id) is missing"))
                .andExpect(jsonPath("$.subsonic-response.error.code").value(10))
                .andDo(print());

        // Verify that player was created
        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertEquals(1, players.size());
        WrapRequestUtil.assertRestAPIPlayer(
                players.get(0),
                clientName,
                AIRSONIC_USER);
    }

    /**
     * Assert that the ArtistWithAlbumsID3 object matches the expected values.
     * This method checks the ID, name, album count, starred date, cover art,
     * and the list of albums.
     *
     * @param expected The expected ArtistWithAlbumsID3 object.
     * @param actual   The actual ArtistWithAlbumsID3 object to compare against.
     */
    private void assertAritistWithAlbumID3(ArtistWithAlbumsID3 expected, ArtistWithAlbumsID3 actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getAlbumCount(), actual.getAlbumCount());
        assertEquals(expected.getStarred(), actual.getStarred());
        assertEquals(expected.getCoverArt(), actual.getCoverArt());
        assertEquals(expected.getAlbum().size(), actual.getAlbum().size());
        for (int i = 0; i < expected.getAlbum().size(); i++) {
            AlbumID3 expectedAlbum = expected.getAlbum().get(i);
            AlbumID3 actualAlbum = actual.getAlbum().get(i);
            assertEquals(expectedAlbum.getId(), actualAlbum.getId());
            assertEquals(expectedAlbum.getName(), actualAlbum.getName());
            assertEquals(expectedAlbum.getArtist(), actualAlbum.getArtist());
            assertEquals(expectedAlbum.getCoverArt(), actualAlbum.getCoverArt());
            assertEquals(expectedAlbum.getSongCount(), actualAlbum.getSongCount());
            assertEquals(expectedAlbum.getDuration(), actualAlbum.getDuration());
            assertEquals(expectedAlbum.getCreated(), actualAlbum.getCreated());
            assertEquals(expectedAlbum.getYear(), actualAlbum.getYear());
            assertEquals(expectedAlbum.getGenre(), actualAlbum.getGenre());
            assertEquals(expectedAlbum.getStarred(), actualAlbum.getStarred());
            assertEquals(expectedAlbum.getPlayCount(), actualAlbum.getPlayCount());
        }
    }

    /**
     * Tests for the /rest/getArtists and /rest/getArtists.view endpoints.
     * These tests cover the retrieval of a list of artists, including indexed artists.
     */

    Comparator<MusicIndex> musicIndexComparator = Comparator.comparing(MusicIndex::getIndex);

    Artist artist1 = new Artist(1, "Artist One", 2, Instant.now(), true, testFolder);
    Artist artist2 = new Artist(2, "Z Artist Two", 3, Instant.now(), true, testFolder);
    List<Artist> artists = List.of(artist1, artist2);

    private void assertArtistsID3(ArtistsID3 expected, ArtistsID3 actual) {
        assertEquals(expected.getIgnoredArticles(), actual.getIgnoredArticles());
        assertEquals(expected.getIndex().size(), actual.getIndex().size());
        for (int i = 0; i < expected.getIndex().size(); i++) {
            assertEquals(expected.getIndex().get(i).getName(), actual.getIndex().get(i).getName());
            assertEquals(expected.getIndex().get(i).getArtist().size(),
                    actual.getIndex().get(i).getArtist().size());
            for (int j = 0; j < expected.getIndex().get(i).getArtist().size(); j++) {
                ArtistID3 expectedArtist = expected.getIndex().get(i).getArtist().get(j);
                ArtistID3 actualArtist = actual.getIndex().get(i).getArtist().get(j);
                assertEquals(expectedArtist.getId(), actualArtist.getId());
                assertEquals(expectedArtist.getName(), actualArtist.getName());
                assertEquals(expectedArtist.getCoverArt(), actualArtist.getCoverArt());
                assertEquals(expectedArtist.getAlbumCount(), actualArtist.getAlbumCount());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getArtists", "/rest/getArtists.view" })
    public void getArtists_shouldReturnArtistsList(String endpoint) throws Exception {
        String clientName = "getArtistsClient";

        // check player is not created
        List<Player> initialPlayers = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertTrue(initialPlayers.isEmpty(), "No players should exist before the request");

        // Create test artists

        // Create indexed artists
        SortedMap<MusicIndex, List<SortableArtistWithArtist>> indexedArtists = new TreeMap<>(
                musicIndexComparator);
        indexedArtists.put(
                new MusicIndex("A"),
                List.of(new SortableArtistWithArtist(artist1.getName(), "Artist One", artist1,
                        Collator.getInstance())));
        indexedArtists.put(
                new MusicIndex("Z"),
                List.of(new SortableArtistWithArtist(artist2.getName(), "Z Artist Two", artist2,
                        Collator.getInstance())));

        when(artistService.getAlphabeticalArtists(List.of(testFolder)))
                .thenReturn(artists);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), eq(null)))
                .thenReturn(List.of(testFolder));
        when(musicIndexService.getIndexedArtists(artists))
                .thenReturn(indexedArtists);
        when(jaxbContentService.createJaxbArtist(any(ArtistID3.class), eq(artist1), eq(AIRSONIC_USER)))
                .thenReturn(TestApiUtil.createTestArtistID3Full(artist1.getName()));
        when(jaxbContentService.createJaxbArtist(any(ArtistID3.class), eq(artist2), eq(AIRSONIC_USER)))
                .thenReturn(TestApiUtil.createTestArtistID3Minimum(artist2.getName()));

        String responseBody = mvc.perform(get(endpoint)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("f", EXPECTED_FORMAT)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                .andExpect(jsonPath("$.subsonic-response.artists").exists())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ArtistsID3 result = objectMapper
                .readTree(responseBody)
                .path("subsonic-response")
                .path("artists")
                .traverse(objectMapper)
                .readValueAs(ArtistsID3.class);

        ArtistsID3 expected = new ArtistsID3();
        expected.setIgnoredArticles("The El La Los Las Le Les");
        IndexID3 index1 = new IndexID3();
        index1.setName("A");
        index1.getArtist().add(
                TestApiUtil.createTestArtistID3Full("Artist One"));
        IndexID3 index2 = new IndexID3();
        index2.setName("Z");
        index2.getArtist().add(
                TestApiUtil.createTestArtistID3Minimum("Z Artist Two"));
        expected.getIndex().add(index1);
        expected.getIndex().add(index2);
        assertArtistsID3(expected, result);

        // Verify that the player is created with the expected properties
        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertEquals(1, players.size());
        WrapRequestUtil.assertRestAPIPlayer(
                players.get(0),
                clientName,
                AIRSONIC_USER);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getArtists", "/rest/getArtists.view" })
    public void getArtistsWithMusicFolderId_shouldReturnArtistsList(String endpoint) throws Exception {
        String clientName = "getArtistsWithMusicFolderIdClient";

        // check player is not created
        List<Player> initialPlayers = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertTrue(initialPlayers.isEmpty());

        // Create indexed artists
        SortedMap<MusicIndex, List<SortableArtistWithArtist>> indexedArtists = new TreeMap<>(
                musicIndexComparator);
        indexedArtists.put(
                new MusicIndex("A"),
                List.of(new SortableArtistWithArtist(artist1.getName(), "Artist One", artist1,
                        Collator.getInstance())));
        indexedArtists.put(
                new MusicIndex("Z"),
                List.of(new SortableArtistWithArtist(artist2.getName(), "Z Artist Two", artist2,
                        Collator.getInstance())));

        when(artistService.getAlphabeticalArtists(List.of(testFolder)))
                .thenReturn(artists);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), eq(1)))
                .thenReturn(List.of(testFolder));
        when(musicIndexService.getIndexedArtists(artists))
                .thenReturn(indexedArtists);
        when(jaxbContentService.createJaxbArtist(any(ArtistID3.class), eq(artist1), eq(AIRSONIC_USER)))
                .thenReturn(TestApiUtil.createTestArtistID3Full(artist1.getName()));
        when(jaxbContentService.createJaxbArtist(any(ArtistID3.class), eq(artist2), eq(AIRSONIC_USER)))
                .thenReturn(TestApiUtil.createTestArtistID3Minimum(artist2.getName()));

        String responseBody = mvc.perform(get(endpoint)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("f", EXPECTED_FORMAT)
                .param("musicFolderId", "1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                .andExpect(jsonPath("$.subsonic-response.artists").exists())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ArtistsID3 result = objectMapper
                .readTree(responseBody)
                .path("subsonic-response")
                .path("artists")
                .traverse(objectMapper)
                .readValueAs(ArtistsID3.class);

        ArtistsID3 expected = new ArtistsID3();
        expected.setIgnoredArticles("The El La Los Las Le Les");
        IndexID3 index1 = new IndexID3();
        index1.setName("A");
        index1.getArtist().add(
                TestApiUtil.createTestArtistID3Full("Artist One"));
        IndexID3 index2 = new IndexID3();
        index2.setName("Z");
        index2.getArtist().add(
                TestApiUtil.createTestArtistID3Minimum("Z Artist Two"));
        expected.getIndex().add(index1);
        expected.getIndex().add(index2);
        assertArtistsID3(expected, result);

        // Verify that the player is created with the expected properties
        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertEquals(1, players.size());
        WrapRequestUtil.assertRestAPIPlayer(
                players.get(0),
                clientName,
                AIRSONIC_USER);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getArtists", "/rest/getArtists.view" })
    public void getArtists_shouldReturnEmptyListWhenNoArtists(String endpoint) throws Exception {
        String clientName = "getArtistsEmptyClient";

        // check player is not created
        List<Player> initialPlayers = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertTrue(initialPlayers.isEmpty(), "No players should exist before the request");

        // mocking
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER), eq(null)))
                .thenReturn(List.of(testFolder));
        when(artistService.getAlphabeticalArtists(List.of(testFolder)))
                .thenReturn(List.of());
        when(musicIndexService.getIndexedArtists(List.of()))
                .thenReturn(new TreeMap<>(musicIndexComparator));

        mvc.perform(get(endpoint)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("f", EXPECTED_FORMAT)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                .andExpect(jsonPath("$.subsonic-response.artists").exists())
                .andExpect(jsonPath("$.subsonic-response.artists.index").doesNotExist())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // verify no interacion
        verifyNoInteractions(jaxbContentService);

        // verify player is created
        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertEquals(1, players.size());
        WrapRequestUtil.assertRestAPIPlayer(
                players.get(0),
                clientName,
                AIRSONIC_USER);
    }

    /**
     * Tests for the /rest/getArtistInfo and /rest/getArtistInfo.view endpoints.
     * These tests cover the retrieval of detailed artist information, including biography,
     */

    @ParameterizedTest
    @CsvSource({
        // endpoint, includeNotPresent, count, expectedCount
        "/rest/getArtistInfo,false,20,1",
        "/rest/getArtistInfo.view,false,20,1",
        "/rest/getArtistInfo,true,10,1"
    })
    public void getArtistInfo_shouldReturnArtistInfo_withVariousParams(
            String endpoint,
            boolean includeNotPresent,
            int count,
            int expectedCount) throws Exception {

        String clientName = "getArtistInfoClient";

        // Check player is not created
        List<Player> initialPlayers = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertTrue(initialPlayers.isEmpty());

        // Prepare test data
        int mediaFileId = 1;
        String artistName = "Test Artist";
        String musicBrainzId = "mbid-artist-123";
        String lastFmUrl = "https://last.fm/artist/test";
        String biography = "This is a test biography.";
        Instant starredDate = Instant.parse("2023-01-01T00:00:00.123Z");

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
                "http://lastfm/img/large.jpg");

        when(lastFmService.getArtistBioByMediaFile(eq(mediaFile), any()))
                .thenReturn(artistBio);

        // Similar artists mock
        when(lastFmService.getSimilarArtistsByMediaFile(eq(mediaFile), eq(count), eq(includeNotPresent),
                eq(List.of(testFolder))))
                .thenReturn(List.of(similarFile));
        when(jaxbContentService.createJaxbArtist(eq(similarFile), eq(AIRSONIC_USER)))
                .thenReturn(TestApiUtil.createTestArtist("Similar Artist A"));

        // Build request
        var requestBuilder = get(endpoint)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("f", EXPECTED_FORMAT)
                .param("id", String.valueOf(mediaFileId))
                .contentType(MediaType.APPLICATION_JSON);

        // Add optional params if not default
        if (includeNotPresent || count != 20) {
            requestBuilder.param("includeNotPresent", String.valueOf(includeNotPresent));
            requestBuilder.param("count", String.valueOf(count));
        }

        String responseBody = mvc.perform(requestBuilder)
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
        assertEquals("3", json.read("$.subsonic-response.artistInfo.similarArtist[0].id"));
        assertEquals(starredDate.truncatedTo(ChronoUnit.MILLIS).toString(),
                json.read("$.subsonic-response.artistInfo.similarArtist[0].starred"));

        // Assert player creation
        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertEquals(1, players.size());
        WrapRequestUtil.assertRestAPIPlayer(
                players.get(0),
                clientName,
                AIRSONIC_USER);
    }

    @Test
    public void getArtistInfo_shouldReturnNotFoundForInvalidId() throws Exception {
        String clientName = "getArtistInfoClientError";
        int invalidId = 9999;

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
    public void getArtistInfoWithoutId_shouldReturnBadRequest() throws Exception {
        String clientName = "getArtistInfoWithoutIdClient";

        mvc.perform(get("/rest/getArtistInfo")
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("f", EXPECTED_FORMAT)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("failed"))
                .andExpect(jsonPath("$.subsonic-response.error.message").value("Required param (id) is missing"))
                .andExpect(jsonPath("$.subsonic-response.error.code").value(10))
                .andDo(print());
    }

    /**
     * Tests for the /rest/getArtistInfo2 and /rest/getArtistInfo2.view endpoints.
     * These tests cover the retrieval of artist information with additional details
     */
    @ParameterizedTest
    @CsvSource({
        // endpoint, includeNotPresent, count, expectedCount
        "/rest/getArtistInfo2,false,20,1",
        "/rest/getArtistInfo2.view,false,20,1",
        "/rest/getArtistInfo2,true,10,1"
    })
    public void getArtistInfo2_shouldReturnArtistInfo2_withVariousParams(
            String endpoint,
            boolean includeNotPresent,
            int count,
            int expectedCount) throws Exception {

        String clientName = "getArtistInfo2ClientJaxb";
        int artistId = 5;
        String artistName = "Jaxb Artist";
        String musicBrainzId = "mbid-artist-jaxb";
        String lastFmUrl = "https://last.fm/artist/jaxb";
        String biography = "This is a jaxb test biography.";

        // check player is not created
        List<Player> initialPlayers = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertTrue(initialPlayers.isEmpty());

        Artist artist = new Artist(artistId, artistName, 1, Instant.now(), true, testFolder);
        Artist similarArtist = new Artist(6, "Similar Artist JAXB", 2, Instant.now(), true, testFolder);

        when(artistService.getArtist(eq(artistId))).thenReturn(artist);
        when(musicFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER)))
                .thenReturn(List.of(testFolder));
        when(lastFmService.getSimilarArtists(eq(artist), eq(count), eq(includeNotPresent), eq(List.of(testFolder))))
                .thenReturn(List.of(similarArtist));
        when(lastFmService.getArtistBio(eq(artist), any()))
                .thenReturn(new ArtistBio(
                        biography,
                        musicBrainzId,
                        lastFmUrl,
                        "http://lastfm/img/small-jaxb.jpg",
                        "http://lastfm/img/medium-jaxb.jpg",
                        "http://lastfm/img/large-jaxb.jpg"));
        when(artistService.getArtistImageURL(any(), eq(artistName), eq(34), eq(AIRSONIC_USER)))
                .thenReturn("http://localhost/img/small-jaxb.jpg");
        when(artistService.getArtistImageURL(any(), eq(artistName), eq(64), eq(AIRSONIC_USER)))
                .thenReturn("http://localhost/img/medium-jaxb.jpg");
        when(artistService.getArtistImageURL(any(), eq(artistName), eq(300), eq(AIRSONIC_USER)))
                .thenReturn("http://localhost/img/large-jaxb.jpg");

        // jaxbContentService mock for similar artist
        when(jaxbContentService.createJaxbArtist(any(ArtistID3.class), eq(similarArtist), eq(AIRSONIC_USER)))
                .thenReturn(TestApiUtil.createTestArtistID3Full("Similar Artist JAXB"));

        var requestBuilder = get(endpoint)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", clientName)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("f", EXPECTED_FORMAT)
                .param("id", String.valueOf(artistId))
                .contentType(MediaType.APPLICATION_JSON);

        if (includeNotPresent || count != 20) {
            requestBuilder.param("includeNotPresent", String.valueOf(includeNotPresent));
            requestBuilder.param("count", String.valueOf(count));
        }

        String responseBody = mvc.perform(requestBuilder)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                .andExpect(jsonPath("$.subsonic-response.artistInfo2").exists())
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ArtistInfo2 artistInfo2 = objectMapper
                .readTree(responseBody)
                .path("subsonic-response")
                .path("artistInfo2")
                .traverse(objectMapper)
                .readValueAs(ArtistInfo2.class);
        ArtistInfo2 expectedArtistInfo2 = new ArtistInfo2();
        expectedArtistInfo2.setBiography(biography);
        expectedArtistInfo2.setMusicBrainzId(musicBrainzId);
        expectedArtistInfo2.setLastFmUrl(lastFmUrl);
        expectedArtistInfo2.setSmallImageUrl("http://localhost/img/small-jaxb.jpg");
        expectedArtistInfo2.setMediumImageUrl("http://localhost/img/medium-jaxb.jpg");
        expectedArtistInfo2.setLargeImageUrl("http://localhost/img/large-jaxb.jpg");
        expectedArtistInfo2.getSimilarArtist().add(
                TestApiUtil.createTestArtistID3Full("Similar Artist JAXB"));
        assertArtistInfo2(expectedArtistInfo2, artistInfo2);

        // Verify that the player is created with the expected properties
        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertEquals(1, players.size());
        WrapRequestUtil.assertRestAPIPlayer(
                players.get(0),
                clientName,
                AIRSONIC_USER);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getArtistInfo2", "/rest/getArtistInfo2.view" })
    public void getArtistInfo2_shouldReturnArtistInfo2ForValidId(String endpoint) throws Exception {
        String clientName = "getArtistInfo2ClientError";
        int invalidId = 9999;

        // check player is not created
        List<Player> initialPlayers = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertTrue(initialPlayers.isEmpty());

        when(artistService.getArtist(eq(invalidId))).thenReturn(null);

        mvc.perform(get(endpoint)
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

        // Verify that player was created
        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertEquals(1, players.size());
        WrapRequestUtil.assertRestAPIPlayer(
                players.get(0),
                clientName,
                AIRSONIC_USER);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/rest/getArtistInfo2", "/rest/getArtistInfo2.view" })
    public void getArtistInfo2WithoutId_shouldReturnBadRequest(String endpoint) throws Exception {
        String clientName = "getArtistInfo2NoSimilar";

        // check player is not created
        List<Player> initialPlayers = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertTrue(initialPlayers.isEmpty());

        // prepare test data
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

        String responseBody = mvc.perform(get(endpoint)
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
                .andExpect(jsonPath("$.subsonic-response.artistInfo2.similarArtist").doesNotExist()) // No similar
                                                                                                     // artists
                .andDo(print())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ArtistInfo2 artistInfo2 = objectMapper
                .readTree(responseBody)
                .path("subsonic-response")
                .path("artistInfo2")
                .traverse(objectMapper)
                .readValueAs(ArtistInfo2.class);

        ArtistInfo2 expectedArtistInfo2 = new ArtistInfo2();
        expectedArtistInfo2.setBiography(null);
        expectedArtistInfo2.setMusicBrainzId(null);
        expectedArtistInfo2.setLastFmUrl(null);
        expectedArtistInfo2.setSmallImageUrl("http://localhost/img/small3.jpg");
        expectedArtistInfo2.setMediumImageUrl("http://localhost/img/medium3.jpg");
        expectedArtistInfo2.setLargeImageUrl("http://localhost/img/large3.jpg");

        assertArtistInfo2(expectedArtistInfo2, artistInfo2);

        verifyNoInteractions(jaxbContentService); // No JAXB conversion for similar artists

        // Verify that the player is created with the expected properties
        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, clientName);
        assertEquals(1, players.size());
        WrapRequestUtil.assertRestAPIPlayer(
                players.get(0),
                clientName,
                AIRSONIC_USER);

    }

    /**
     * Helper method to assert equality of ArtistInfo2 objects.
     *
     * @param expected The expected ArtistInfo2 object.
     * @param actual   The actual ArtistInfo2 object.
     */
    private void assertArtistInfo2(ArtistInfo2 expected, ArtistInfo2 actual) {
        assertEquals(expected.getBiography(), actual.getBiography());
        assertEquals(expected.getMusicBrainzId(), actual.getMusicBrainzId());
        assertEquals(expected.getLastFmUrl(), actual.getLastFmUrl());
        assertEquals(expected.getSmallImageUrl(), actual.getSmallImageUrl());
        assertEquals(expected.getMediumImageUrl(), actual.getMediumImageUrl());
        assertEquals(expected.getLargeImageUrl(), actual.getLargeImageUrl());
        assertEquals(expected.getSimilarArtist().size(), actual.getSimilarArtist().size());

        for (int i = 0; i < expected.getSimilarArtist().size(); i++) {
            assertEquals(expected.getSimilarArtist().get(i).getName(),
                    actual.getSimilarArtist().get(i).getName());
            assertEquals(expected.getSimilarArtist().get(i).getId(),
                    actual.getSimilarArtist().get(i).getId());
            assertEquals(expected.getSimilarArtist().get(i).getStarred(),
                    actual.getSimilarArtist().get(i).getStarred());
        }
    }
}
