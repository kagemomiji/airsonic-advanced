package org.airsonic.player.api;

import org.airsonic.player.controller.JAXBWriter;
import org.subsonic.restapi.AlbumID3;
import org.subsonic.restapi.AlbumWithSongsID3;
import org.subsonic.restapi.Artist;
import org.subsonic.restapi.ArtistID3;
import org.subsonic.restapi.ArtistWithAlbumsID3;
import org.subsonic.restapi.Child;

import java.time.Instant;

public class TestApiUtil {


    // 標準的な音楽ファイル(MUSIC)のChildを生成
    public static Child createTestMusicChild() {
        Child child = new Child();
        child.setId("1");
        child.setTitle("Test Song");
        child.setAlbum("Test Album");
        child.setArtist("Test Artist");
        child.setIsDir(false);
        child.setYear(2024);
        child.setGenre("Rock");
        child.setDuration(180);
        child.setBitRate(320);
        child.setTrack(1);
        child.setDiscNumber(1);
        child.setSize(12345678L);
        child.setSuffix("mp3");
        child.setContentType("audio/mpeg");
        child.setIsVideo(false);
        child.setPath("/music/test.mp3");
        child.setType(org.subsonic.restapi.MediaType.MUSIC);
        return child;
    }

    // ポッドキャスト(PODCAST)のChildを生成
    public static Child createTestPodcastChild() {
        Child child = createTestMusicChild();
        child.setId("2");
        child.setTitle("Test Podcast");
        child.setType(org.subsonic.restapi.MediaType.PODCAST);
        child.setSuffix("m4a");
        child.setContentType("audio/mp4");
        return child;
    }

    // オーディオブック(AUDIOBOOK)のChildを生成
    public static Child createTestAudiobookChild() {
        Child child = createTestMusicChild();
        child.setId("3");
        child.setTitle("Test Audiobook");
        child.setType(org.subsonic.restapi.MediaType.AUDIOBOOK);
        child.setSuffix("m4b");
        child.setContentType("audio/mp4");
        return child;
    }

    // ビデオ(VIDEO)のChildを生成
    public static Child createTestVideoChild() {
        Child child = createTestMusicChild();
        child.setId("4");
        child.setTitle("Test Video");
        child.setType(org.subsonic.restapi.MediaType.VIDEO);
        child.setIsVideo(true);
        child.setSuffix("mp4");
        child.setContentType("video/mp4");
        child.setOriginalWidth(1920);
        child.setOriginalHeight(1080);
        return child;
    }

    // ディレクトリ(フォルダ)のChildを生成
    public static Child createTestDirectoryChild() {
        Child child = new Child();
        child.setId("5");
        child.setTitle("Test Folder");
        child.setIsDir(true);
        child.setArtist("Test Artist");
        child.setAlbum("Test Album");
        return child;
    }

    /**
     * Generate a full AlbumID3 object for testing.
     *
     * @param id The ID of the album.
     * @return AlbumID3 object with all fields set.
     */
    public static AlbumWithSongsID3 createTestAlbumWithSongsID3Full(Integer id) {
        JAXBWriter jaxbWriter = new JAXBWriter();
        AlbumWithSongsID3 album = new AlbumWithSongsID3();
        album.setId(id.toString());
        album.setName("Full Album");
        album.setArtist("Test Artist");
        album.setArtistId("100");
        album.setSongCount(8);
        album.setPlayCount(3L);
        album.setDuration(2400);
        album.setYear(2023);
        album.setGenre("Jazz");
        album.setStarred(jaxbWriter.convertDate(Instant.now()));
        album.setCreated(jaxbWriter.convertDate(Instant.now()));
        album.setCoverArt("al-11");
        return album;
    }

    /**
     * Generate a minimal AlbumWithSongsID3 object for testing.
     * This is useful for cases where only the ID and name are needed.
     *
     * @param id The ID of the album.
     * @return AlbumWithSongsID3 object with minimal fields set.
     */
    public static AlbumWithSongsID3 createTestAlbumWithSongsID3Minimum(Integer id) {
        JAXBWriter jaxbWriter = new JAXBWriter();
        AlbumWithSongsID3 album = new AlbumWithSongsID3();
        album.setId(id.toString());
        album.setName("Minimal Album");
        album.setSongCount(1);
        album.setPlayCount(0L);
        album.setCreated(jaxbWriter.convertDate(Instant.now()));
        return album;
    }

    /**
     * Generate an AlbumID3 object for testing, with preset values simulating the
     * logic of createJaxbAlbum.
     *
     * @return AlbumID3 object with fields set to typical test values.
     */
    public static AlbumID3 createTestAlbumID3() {
        JAXBWriter jaxbWriter = new JAXBWriter();
        AlbumID3 album = new AlbumID3();
        album.setId("10");
        album.setName("Test Album 10");
        album.setArtist("Test Artist");
        album.setArtistId("100");
        album.setCoverArt("al-10");
        album.setSongCount(12);
        album.setDuration(3600);
        album.setCreated(jaxbWriter.convertDate(Instant.parse("2023-01-01T00:00:00.123Z")));
        album.setStarred(jaxbWriter.convertDate(Instant.parse("2023-06-01T00:00:00.123Z")));
        album.setYear(2023);
        album.setGenre("Rock");
        return album;
    }

    /**
     * Create a typical ArtistID3 object for testing purposes.
     *
     * @return ArtistID3 object with preset values.
     */
    public static ArtistID3 createTestArtistID3Full(String name) {
        JAXBWriter jaxbWriter = new JAXBWriter();
        ArtistID3 artist = new ArtistID3();
        artist.setId("1");
        artist.setName(name);
        artist.setStarred(jaxbWriter.convertDate(Instant.parse("2023-01-01T00:00:00Z")));
        artist.setAlbumCount(5);
        artist.setCoverArt("ar-1");
        return artist;
    }

    /**
     * Create a minimal ArtistID3 object for testing.
     * This is useful when only the ID and name are needed.
     *
     * @return ArtistID3 object with minimal fields set.
     */
    public static ArtistID3 createTestArtistID3Minimum(String name) {
        ArtistID3 artist = new ArtistID3();
        artist.setId("2");
        artist.setName(name);
        return artist;
    }

    /**
     * Create a typical Artist object for testing purposes.
     *
     * @return Artist object with preset values.
     */
    public static Artist createTestArtist(String name) {
        JAXBWriter jaxbWriter = new JAXBWriter();
        Artist artist = new Artist();
        artist.setId("3");
        artist.setName(name);
        artist.setStarred(jaxbWriter.convertDate(Instant.parse("2023-01-01T00:00:00.123Z")));
        return artist;
    }

    /**
     * Create a typical ArtistWithAlbumsID3 object for testing purposes.
     * This object includes fields like ID, name, starred date, album count, and cover art.
     * It is useful for testing scenarios where artist data with albums is required.
     * @param name The name of the artist.
     * @return ArtistWithAlbumsID3 object with preset values.
     */
    public static ArtistWithAlbumsID3 createTestArtistWithAlbumsID3Full(String name) {
        JAXBWriter jaxbWriter = new JAXBWriter();
        ArtistWithAlbumsID3 artist = new ArtistWithAlbumsID3();
        artist.setId("4");
        artist.setName(name);
        artist.setStarred(jaxbWriter.convertDate(Instant.parse("2023-01-01T00:00:00.123Z")));
        artist.setAlbumCount(10);
        artist.setCoverArt("ar-4");
        return artist;
    }

    /**
     * Create a minimal ArtistWithAlbumsID3 object for testing.
     * This is useful when only the ID and name are needed.
     * @param name The name of the artist.
     * @return ArtistWithAlbumsID3 object with minimal fields set.
     */
    public static ArtistWithAlbumsID3 createTestArtistWithAlbumsID3Minimum(String name) {
        ArtistWithAlbumsID3 artist = new ArtistWithAlbumsID3();
        artist.setId("5");
        artist.setName(name);
        return artist;
    }

}
