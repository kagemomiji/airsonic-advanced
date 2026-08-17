package org.airsonic.player.service;

import org.airsonic.player.domain.Lyrics;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.repository.LyricsRepository;
import org.airsonic.player.util.MusicFolderTestData;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LyricsServiceTest {

    @Mock
    private LyricsRepository lyricsRepository;
    @Mock
    private MediaFileService mediaFileService;
    @InjectMocks
    private LyricsService lyricsService;

    @Mock
    private MediaFile mediaFile;

    @Test
    void getLyricsFromMediaFile_shouldReturnNullForDirectory() {
        when(mediaFile.isDirectory()).thenReturn(true);

        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

        assertNull(result);
        verifyNoInteractions(lyricsRepository);
    }

    @Test
    void getLyricsFromMediaFile_shouldReturnNullForIndexedTrack() {
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(true);
        when(mediaFile.getId()).thenReturn(1);
        when(lyricsRepository.findByMediaFileId(eq(1))).thenReturn(Optional.empty());

        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

        verifyNoMoreInteractions(lyricsRepository);
        assertNull(result);
    }

    @Test
    void getLyricsFromMediaFile_shouldReturnExistingLyrics() {
        MediaFile mediaFile = mock(MediaFile.class);
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(2);
        Lyrics lyrics = new Lyrics("existing lyrics", 2, "file");
        when(lyricsRepository.findByMediaFileId(eq(2))).thenReturn(Optional.of(lyrics));

        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);
        verifyNoMoreInteractions(lyricsRepository);

        assertEquals(lyrics, result);
    }

    @Test
    void getLyricsFromMediaFile_shouldReturnNullIfNoLrcFileExists() {
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(3);
        Path path = Path.of("lyrics.mp3");

        when(mediaFile.getFullPath()).thenReturn(path);
        when(lyricsRepository.findByMediaFileId(eq(3))).thenReturn(Optional.empty());

        // Simulate Files.exists() by using a spy
        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);
        verifyNoMoreInteractions(lyricsRepository);

        assertNull(result);
    }

    @Test
    void getLyricsFromMediaFile_shouldParseLrcFileAndSaveLyrics() {
        Path path = MusicFolderTestData.resolveLyricsFolderPath().resolve("simple.mp3");
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(3);
        when(mediaFile.getFullPath()).thenReturn(path);
        when(lyricsRepository.findByMediaFileId(eq(3))).thenReturn(Optional.empty());
        when(lyricsRepository.save(any(Lyrics.class))).thenAnswer(invocation -> {
            Lyrics savedLyrics = invocation.getArgument(0);
            savedLyrics.setId(1); // Simulate ID assignment
            return savedLyrics;
        });

        // Simulate Files.exists() by using a spy
        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

        assertNotNull(result.getLyrics());
        assertEquals(3, result.getMediaFileId());
        assertEquals(1, result.getId());
        assertEquals("file", result.getSource());
        assertNotNull(result.getCreated());
        assertNotNull(result.getUpdated());

    }

    @Test
    void getLyricsFromMediaFile_shouldReadEmbeddedUsltTagWhenNoLrcFile() {
        Path path = Path.of("nonexistent.mp3");
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(5);
        when(mediaFile.getFullPath()).thenReturn(path);
        when(lyricsRepository.findByMediaFileId(eq(5))).thenReturn(Optional.empty());
        when(lyricsRepository.save(any(Lyrics.class))).thenAnswer(i -> i.getArgument(0));

        AudioFile mockAudioFile = mock(AudioFile.class);
        Tag mockTag = mock(Tag.class);
        when(mockAudioFile.getTag()).thenReturn(mockTag);
        when(mockTag.getFirst(FieldKey.LYRICS)).thenReturn("Embedded lyrics text");

        try (MockedStatic<AudioFileIO> mocked = mockStatic(AudioFileIO.class)) {
            mocked.when(() -> AudioFileIO.read(any(File.class))).thenReturn(mockAudioFile);

            Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

            assertNotNull(result);
            assertEquals("Embedded lyrics text", result.getLyrics());
            assertEquals(5, result.getMediaFileId());
            assertEquals("tag", result.getSource());
            ArgumentCaptor<Lyrics> captor = ArgumentCaptor.forClass(Lyrics.class);
            verify(lyricsRepository).save(captor.capture());
            assertEquals("tag", captor.getValue().getSource());
        }
    }

    @Test
    void getLyricsFromMediaFile_shouldReturnNullWhenTagHasNoLyrics() {
        Path path = Path.of("nonexistent.mp3");
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(6);
        when(mediaFile.getFullPath()).thenReturn(path);
        when(lyricsRepository.findByMediaFileId(eq(6))).thenReturn(Optional.empty());

        AudioFile mockAudioFile = mock(AudioFile.class);
        Tag mockTag = mock(Tag.class);
        when(mockAudioFile.getTag()).thenReturn(mockTag);
        when(mockTag.getFirst(FieldKey.LYRICS)).thenReturn("  "); // blank

        try (MockedStatic<AudioFileIO> mocked = mockStatic(AudioFileIO.class)) {
            mocked.when(() -> AudioFileIO.read(any(File.class))).thenReturn(mockAudioFile);

            Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

            assertNull(result);
            verify(lyricsRepository, never()).save(any());
        }
    }

    @Test
    void getLyricsFromArtistAndTitle_shouldReturnNullIfArtistOrTitleNull() {
        Lyrics result = lyricsService.getLyricsFromArtistAndTitle(null, "title", null);
        assertNull(result);

        result = lyricsService.getLyricsFromArtistAndTitle("artist", null, null);
        assertNull(result);

        verifyNoInteractions(mediaFileService);
        verifyNoInteractions(lyricsRepository);
    }

    @Test
    void getLyricsFromArtistAndTitle_shouldReturnNullIfMediaFileNotFound() {
        when(mediaFileService.getSongByArtistAndTitle("artist", "title", null)).thenReturn(null);

        Lyrics result = lyricsService.getLyricsFromArtistAndTitle("artist", "title", null);

        assertNull(result);
        verifyNoInteractions(lyricsRepository);

    }

    @ParameterizedTest
    @ValueSource(strings = { "file", "user" })
    void saveLyricsForMediaFile_shouldReturnFalseForDirectory(String source) {
        MediaFile mediaFile = mock(MediaFile.class);
        when(mediaFile.isDirectory()).thenReturn(true);

        boolean result = lyricsService.saveLyricsForMediaFile(mediaFile, "lyrics", source);

        assertFalse(result);
        verifyNoInteractions(lyricsRepository);
    }

    @Test
    void saveLyricsForMediaFile_shouldSaveNewLyrics() {
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(10);
        when(lyricsRepository.findByMediaFileId(eq(10))).thenReturn(Optional.empty());

        boolean result = lyricsService.saveLyricsForMediaFile(mediaFile, "new lyrics", "user");

        assertTrue(result);
        ArgumentCaptor<Lyrics> captor = ArgumentCaptor.forClass(Lyrics.class);
        verify(lyricsRepository).save(captor.capture());
        assertEquals("new lyrics", captor.getValue().getLyrics());
        assertEquals(10, captor.getValue().getMediaFileId());
        assertEquals("user", captor.getValue().getSource());
    }

    @Test
    void saveLyricsForMediaFile_shouldUpdateExistingLyrics() {
        MediaFile mediaFile = mock(MediaFile.class);
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(11);
        Lyrics lyrics = new Lyrics("old lyrics", 11, "file");
        when(lyricsRepository.findByMediaFileId(11)).thenReturn(Optional.of(lyrics));

        boolean result = lyricsService.saveLyricsForMediaFile(mediaFile, "updated lyrics", "user");

        assertTrue(result);
        assertEquals("updated lyrics", lyrics.getLyrics());
        assertEquals(11, lyrics.getMediaFileId());
        assertEquals("user", lyrics.getSource());
        verify(lyricsRepository).save(lyrics);
    }
}
