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
package org.airsonic.player.service;

import org.airsonic.player.controller.CoverArtController;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.Playlist;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.subsonic.restapi.AlbumID3;
import org.subsonic.restapi.ArtistID3;
import org.subsonic.restapi.Child;
import org.subsonic.restapi.MediaType;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JaxbContentServiceTest {
    @Mock
    private ArtistService artistService;
    @Mock
    private CoverArtService coverArtService;
    @Mock
    private PlaylistService playlistService;
    @Mock
    private AlbumService albumService;
    @Mock
    private MediaFileService mediaFileService;
    @Mock
    private TranscodingService transcodingService;
    @Mock
    private RatingService ratingService;

    @InjectMocks
    private JaxbContentService service;

    @Nested
    public class JaxbArtistTest {
        @Mock
        private Artist artist;
        private CoverArt coverArt = new CoverArt();

        @Test
        void createJaxbArtist_setsFieldsCorrectly() {
            Instant starredDate = Instant.now();
            ArtistID3 jaxbArtist = new ArtistID3();
            when(artist.getId()).thenReturn(42);
            when(artist.getName()).thenReturn("Test Artist");
            when(artist.getAlbumCount()).thenReturn(3);
            when(artistService.getStarredDate(eq(42), anyString())).thenReturn(starredDate);
            when(coverArtService.getArtistArt(42)).thenReturn(coverArt);

            ArtistID3 result = service.createJaxbArtist(jaxbArtist, artist, "user");

            assertEquals("42", result.getId());
            assertEquals("Test Artist", result.getName());
            assertEquals(3, result.getAlbumCount());
            assertThat(result.getStarred()).isNotNull();
            assertEquals(CoverArtController.ARTIST_COVERART_PREFIX + "42", result.getCoverArt());
        }

        @Test
        void createJaxbArtist_noCoverArt() {
            ArtistID3 jaxbArtist = new ArtistID3();
            Artist artist = mock(Artist.class);
            when(artist.getId()).thenReturn(1);
            when(artist.getName()).thenReturn("NoArt");
            when(artist.getAlbumCount()).thenReturn(0);
            when(artistService.getStarredDate(eq(1), anyString())).thenReturn(null);
            when(coverArtService.getArtistArt(1)).thenReturn(CoverArt.NULL_ART);

            ArtistID3 result = service.createJaxbArtist(jaxbArtist, artist, "user");
            assertNull(result.getCoverArt());
            assertNull(result.getStarred());
            assertEquals("1", result.getId());
            assertEquals("NoArt", result.getName());
            assertEquals(0, result.getAlbumCount());
        }
    }

    @Nested
    public class JaxbAlbumTest {
        @Mock
        private Album album;
        @Mock
        private Artist artist;
        private CoverArt coverArt = new CoverArt();
        private Instant starredDate = Instant.now();

        @Test
        void createJaxbAlbum_setsFieldsCorrectly() {
            AlbumID3 jaxbAlbum = new AlbumID3();
            when(album.getId()).thenReturn(10);
            when(album.getName()).thenReturn("AlbumName");
            when(album.getArtist()).thenReturn("ArtistName");
            when(album.getSongCount()).thenReturn(12);
            when(album.getDuration()).thenReturn(1234.5);
            when(album.getCreated()).thenReturn(starredDate);
            when(album.getYear()).thenReturn(2020);
            when(album.getGenre()).thenReturn("Rock");
            when(coverArtService.getAlbumArt(10)).thenReturn(coverArt);
            when(albumService.getAlbumStarredDate(10, "user")).thenReturn(starredDate);
            when(coverArtService.getAlbumArt(10)).thenReturn(coverArt);
            when(artistService.getArtist("ArtistName")).thenReturn(artist);
            when(artist.getId()).thenReturn(99);

            AlbumID3 result = service.createJaxbAlbum(jaxbAlbum, album, "user");

            assertEquals("10", result.getId());
            assertEquals("AlbumName", result.getName());
            assertEquals("ArtistName", result.getArtist());
            assertEquals("99", result.getArtistId());
            assertEquals(CoverArtController.ALBUM_COVERART_PREFIX + "10", result.getCoverArt());
            assertEquals(12, result.getSongCount());
            assertEquals(1235, result.getDuration());
            assertNotNull(result.getCreated());
            assertNotNull(result.getStarred());
            assertEquals(2020, result.getYear());
            assertEquals("Rock", result.getGenre());
        }

        @Test
        void createJaxbAlbum_noArtistOrCoverArt() {
            AlbumID3 jaxbAlbum = new AlbumID3();
            when(album.getId()).thenReturn(2);
            when(album.getName()).thenReturn("NoArtist");
            when(album.getArtist()).thenReturn(null);
            when(album.getSongCount()).thenReturn(0);
            when(album.getDuration()).thenReturn(0.0);
            when(album.getCreated()).thenReturn(null);
            when(album.getYear()).thenReturn(null);
            when(album.getGenre()).thenReturn(null);
            when(coverArtService.getAlbumArt(2)).thenReturn(CoverArt.NULL_ART);
            when(albumService.getAlbumStarredDate(2, "user")).thenReturn(null);

            AlbumID3 result = service.createJaxbAlbum(jaxbAlbum, album, "user");

            assertNull(result.getArtistId());
            assertNull(result.getCoverArt());
        }
    }

    @Nested
    public class JaxbPlaylistTest {
        @Mock
        private Playlist playlist;

        @Test
        void createJaxbPlaylist_setsFieldsCorrectly() {
            org.subsonic.restapi.Playlist jaxbPlaylist = new org.subsonic.restapi.Playlist();
            Playlist playlist = mock(Playlist.class);
            when(playlist.getId()).thenReturn(5);
            when(playlist.getName()).thenReturn("MyPlaylist");
            when(playlist.getComment()).thenReturn("A comment");
            when(playlist.getUsername()).thenReturn("owner");
            when(playlist.getShared()).thenReturn(true);
            when(playlist.getFileCount()).thenReturn(7);
            when(playlist.getDuration()).thenReturn(321.0);
            when(playlist.getCreated()).thenReturn(Instant.ofEpochMilli(33333333L));
            when(playlist.getChanged()).thenReturn(Instant.ofEpochMilli(44444444L));
            when(playlistService.getPlaylistUsers(5)).thenReturn(Arrays.asList("user1", "user2"));

            org.subsonic.restapi.Playlist result = service.createJaxbPlaylist(jaxbPlaylist, playlist);

            assertEquals("5", result.getId());
            assertEquals("MyPlaylist", result.getName());
            assertEquals("A comment", result.getComment());
            assertEquals("owner", result.getOwner());
            assertEquals(true, result.isPublic());
            assertEquals(7, result.getSongCount());
            assertEquals(321, result.getDuration());
            assertNotNull(result.getCreated());
            assertNotNull(result.getChanged());
            assertEquals(CoverArtController.PLAYLIST_COVERART_PREFIX + "5", result.getCoverArt());
            assertEquals(Arrays.asList("user1", "user2"), result.getAllowedUser());
        }
    }

    @Nested
    class JaxbChildTest {
        @Mock
        private MediaFile mediaFile;
        @Mock
        private MediaFile parent;
        private CoverArt coverArt = new CoverArt();

        @Test
        void createJaxbChild_setsFieldsForFile() {
            Player player = mock(Player.class);
            when(mediaFileService.getParentOf(mediaFile)).thenReturn(parent);
            when(mediaFile.getId()).thenReturn(100);
            when(parent.getId()).thenReturn(99);
            when(mediaFileService.isRoot(parent)).thenReturn(false);
            when(mediaFile.getName()).thenReturn("song.mp3");
            when(mediaFile.getAlbumName()).thenReturn("Album");
            when(mediaFile.getArtist()).thenReturn("Artist");
            when(mediaFile.isDirectory()).thenReturn(false);
            when(mediaFile.isFile()).thenReturn(true);
            when(mediaFile.getYear()).thenReturn(2021);
            when(mediaFile.getGenre()).thenReturn("Pop");
            when(mediaFile.getCreated()).thenReturn(Instant.ofEpochMilli(55555555L));
            when(mediaFileService.getMediaFileStarredDate(mediaFile, "user"))
                    .thenReturn(Instant.ofEpochMilli(66666666L));
            when(ratingService.getRatingForUser("user", mediaFile)).thenReturn(4);
            when(ratingService.getAverageRating(mediaFile)).thenReturn(3.5);
            when(mediaFile.getPlayCount()).thenReturn(10);
            when(mediaFile.getDuration()).thenReturn(200.0);
            when(mediaFile.getBitRate()).thenReturn(320);
            when(mediaFile.getTrackNumber()).thenReturn(1);
            when(mediaFile.getDiscNumber()).thenReturn(1);
            when(mediaFile.getFileSize()).thenReturn(123456L);
            when(mediaFile.getFormat()).thenReturn("mp3");
            when(mediaFile.isVideo()).thenReturn(false);
            when(mediaFile.getPath()).thenReturn("/music/song.mp3");
            when(mediaFile.getMediaType()).thenReturn(MediaFile.MediaType.MUSIC);
            Album album = mock(Album.class);
            when(albumService.getAlbumByMediaFile(mediaFile)).thenReturn(album);
            when(album.getId()).thenReturn(77);
            Artist artist = mock(Artist.class);
            when(artistService.getArtist("Artist")).thenReturn(artist);
            when(artist.getId()).thenReturn(88);
            when(coverArtService.getMediaFileArt(99)).thenReturn(coverArt);
            when(transcodingService.isTranscodingRequired(mediaFile, player)).thenReturn(true);
            when(transcodingService.getSuffix(player, mediaFile, null)).thenReturn("ogg");

            Child child = service.createJaxbChild(player, mediaFile, "user");
            assertEquals("100", child.getId());
            assertEquals("99", child.getParent());
            assertEquals("song.mp3", child.getTitle());
            assertEquals("Album", child.getAlbum());
            assertEquals("Artist", child.getArtist());
            assertFalse(child.isIsDir());
            assertEquals("99", child.getCoverArt());
            assertEquals(2021, child.getYear());
            assertEquals("Pop", child.getGenre());
            assertNotNull(child.getCreated());
            assertNotNull(child.getStarred());
            assertEquals(4, child.getUserRating());
            assertEquals(3.5, child.getAverageRating());
            assertEquals(10L, child.getPlayCount());
            assertEquals(200, child.getDuration());
            assertEquals(320, child.getBitRate());
            assertEquals(1, child.getTrack());
            assertEquals(1, child.getDiscNumber());
            assertEquals(123456L, child.getSize());
            assertEquals("mp3", child.getSuffix());
            assertNotNull(child.getContentType());
            assertFalse(child.isIsVideo());
            assertEquals("/music/song.mp3", child.getPath());
            assertEquals("77", child.getAlbumId());
            assertEquals("88", child.getArtistId());
            assertEquals(MediaType.MUSIC, child.getType());
            assertEquals("ogg", child.getTranscodedSuffix());
            assertNotNull(child.getTranscodedContentType());
        }

        @Test
        void createJaxbChild_setsFieldsForDirectory() {
            Player player = mock(Player.class);
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFileService.getParentOf(mediaFile)).thenReturn(null);
            when(mediaFile.getId()).thenReturn(200);
            when(mediaFile.isDirectory()).thenReturn(true);
            when(mediaFile.isFile()).thenReturn(false);
            when(coverArtService.getMediaFileArt(200)).thenReturn(coverArt);

            Child child = service.createJaxbChild(player, mediaFile, "user");

            assertEquals("200", child.getId());
            assertTrue(child.isIsDir());
            assertEquals("200", child.getCoverArt());
        }

        @Test
        void createJaxbChild_noCoverArt() {
            Player player = mock(Player.class);
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFileService.getParentOf(mediaFile)).thenReturn(null);
            when(mediaFile.getId()).thenReturn(300);
            when(mediaFile.isDirectory()).thenReturn(true);
            when(mediaFile.isFile()).thenReturn(false);
            when(coverArtService.getMediaFileArt(300)).thenReturn(CoverArt.NULL_ART);

            Child child = service.createJaxbChild(player, mediaFile, "user");

            assertNull(child.getCoverArt());
        }
    }

    @Nested
    class CreateJaxbArtistFromMediaFileTest {
        @Test
        void createJaxbArtist_fromMediaFile_setsFieldsCorrectly_withTitle() {
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFile.getId()).thenReturn(123);
            when(mediaFile.getTitle()).thenReturn("Artist Title");
            when(mediaFileService.getMediaFileStarredDate(mediaFile, "user"))
                    .thenReturn(Instant.ofEpochMilli(123456789L));

            org.subsonic.restapi.Artist result = service.createJaxbArtist(mediaFile, "user");

            assertEquals("123", result.getId());
            assertEquals("Artist Title", result.getName());
            assertNotNull(result.getStarred());
        }

        @Test
        void createJaxbArtist_fromMediaFile_setsFieldsCorrectly_withNullTitle() {
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFile.getId()).thenReturn(124);
            when(mediaFile.getTitle()).thenReturn(null);
            when(mediaFile.getArtist()).thenReturn("Artist Name");
            when(mediaFileService.getMediaFileStarredDate(mediaFile, "user")).thenReturn(null);

            org.subsonic.restapi.Artist result = service.createJaxbArtist(mediaFile, "user");

            assertEquals("124", result.getId());
            assertEquals("Artist Name", result.getName());
            assertNull(result.getStarred());
        }
    }

}
