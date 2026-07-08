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

 Copyright 2023 (C) Y.Tory
 */
package org.airsonic.player.service;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.repository.MediaFileRepository;
import org.airsonic.player.repository.MusicFileInfoRepository;
import org.airsonic.player.service.cache.MediaFileCache;
import org.airsonic.player.service.metadata.MetaDataParserFactory;
import org.digitalmediaserver.cuelib.CueSheet;
import org.digitalmediaserver.cuelib.FileData;
import org.digitalmediaserver.cuelib.Index;
import org.digitalmediaserver.cuelib.Position;
import org.digitalmediaserver.cuelib.TrackData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MediaFileServiceTest {

    @Mock
    private MetaDataParserFactory metaDataParserFactory;
    @Mock
    private MediaFileRepository mediaFileRepository;
    @Mock
    private CoverArtService coverArtService;
    @Mock
    private MediaFileCache mediaFileCache;
    @Mock
    private MediaFolderService mediaFolderService;
    @Mock
    private MusicFileInfoRepository musicFileInfoRepository;
    @Mock
    private SettingsService settingsService;

    @InjectMocks
    private MediaFileService mediaFileService;


    @Mock
    private MusicFolder mockedFolder;

    @Mock
    private MediaFile mockedMediaFile;

    private final Path CLASS_PATH = Paths.get("src", "test", "resources");

    @BeforeEach
    public void setUp() {
        when(mockedFolder.getPath()).thenReturn(CLASS_PATH.resolve("MEDIAS"));
    }

    @Test
    public void createIndexedTracksFailedByNoIndexTracksReturnEmptyList() {
        // prepare test data
        MediaFile base = new MediaFile();
        base.setIndexPath("invalidCue/airsonic-test.cue");
        base.setPath("valid/airsonic-test.wav");
        base.setMediaType(MediaType.MUSIC);
        base.setFormat("wav");
        base.setId(10);
        base.setFolder(mockedFolder);

        when(mediaFileRepository.findByFolderAndPath(any(), eq("valid/airsonic-test.wav"))).thenReturn(List.of(mockedMediaFile));
        when(mockedMediaFile.isIndexedTrack()).thenReturn(true);
        when(mediaFileRepository.existsById(any())).thenReturn(true);

        // execute
        List<MediaFile> actual = ReflectionTestUtils.invokeMethod(mediaFileService, "createIndexedTracks", base);

        // check empty list is returned
        assertTrue(actual.isEmpty());
        // verify updateMedia does not called
        verify(mediaFileRepository).findByFolderAndPath(any(), eq("valid/airsonic-test.wav"));
        verify(mediaFileRepository).save(base);
        verify(coverArtService).persistIfNeeded(eq(base));
    }

    @Test
    public void createIndexedTracksUsesOnlyTracksFromMatchingCueFileData() {
        CueSheet cueSheet = new CueSheet();
        cueSheet.setTitle("Album");
        cueSheet.setPerformer("Album Artist");

        FileData otherFileData = new FileData(cueSheet, "airsonic-test.wav", "WAVE");
        otherFileData.getTrackData().add(createTrack(otherFileData, 1, "Wrong File Track", 0, 0, 0));
        cueSheet.getFileData().add(otherFileData);

        FileData matchingFileData = new FileData(cueSheet, "piano.mp3", "MP3");
        matchingFileData.getTrackData().add(createTrack(matchingFileData, 2, "Matching Track 1", 0, 0, 0));
        matchingFileData.getTrackData().add(createTrack(matchingFileData, 3, "Matching Track 2", 0, 10, 37));
        cueSheet.getFileData().add(matchingFileData);

        MediaFile base = new MediaFile();
        base.setIndexPath("cue/airsonic-test.cue");
        base.setPath("piano.mp3");
        base.setMediaType(MediaType.MUSIC);
        base.setFormat("mp3");
        base.setDuration(30.0);
        base.setFolder(mockedFolder);
        base.setChanged(Instant.now());
        base.setLastScanned(Instant.now());
        base.setCreated(Instant.now());

        when(mediaFileRepository.findByFolderAndPath(any(), eq("piano.mp3"))).thenReturn(List.of());
        when(mediaFileRepository.findByPathAndFolderAndStartPosition(any(), any(), any())).thenReturn(Optional.empty());
        when(musicFileInfoRepository.findByPath(any())).thenReturn(Optional.empty());

        List<MediaFile> actual = ReflectionTestUtils.invokeMethod(mediaFileService, "createIndexedTracks", base, cueSheet);

        assertEquals(2, actual.size());
        assertEquals("Matching Track 1", actual.get(0).getTitle());
        assertEquals("Matching Track 2", actual.get(1).getTitle());
        assertEquals("piano.mp3", actual.get(0).getPath());
        assertEquals("piano.mp3", actual.get(1).getPath());
        assertEquals(0.0d, actual.get(0).getStartPosition(), 0.0d);
        assertEquals(10.493d, actual.get(1).getStartPosition(), 0.001d);
        assertEquals(10.493d, actual.get(0).getDuration(), 0.001d);
        assertEquals(19.506d, actual.get(1).getDuration(), 0.001d);
    }

    @Test
    public void createIndexedTracksRefreshesExistingCueTrackMetadataAndDuration() {
        CueSheet cueSheet = new CueSheet();
        cueSheet.setTitle("Album");
        cueSheet.setPerformer("Album Artist");

        FileData fileData = new FileData(cueSheet, "piano.mp3", "MP3");
        fileData.getTrackData().add(createTrack(fileData, 2, "Fresh Title", 0, 0, 0));
        cueSheet.getFileData().add(fileData);

        MediaFile base = new MediaFile();
        base.setIndexPath("cue/airsonic-test.cue");
        base.setPath("piano.mp3");
        base.setMediaType(MediaType.MUSIC);
        base.setFormat("mp3");
        base.setDuration(30.0);
        base.setFolder(mockedFolder);
        base.setChanged(Instant.now());
        base.setLastScanned(Instant.now());
        base.setCreated(Instant.now());

        MediaFile staleTrack = new MediaFile();
        staleTrack.setId(42);
        staleTrack.setPath("piano.mp3");
        staleTrack.setFolder(mockedFolder);
        staleTrack.setStartPosition(0.0);
        staleTrack.setDuration(10.0);
        staleTrack.setTitle("Stale Title");
        staleTrack.setTrackNumber(5);
        staleTrack.setPlayCount(7);

        when(mediaFileRepository.findByFolderAndPath(any(), eq("piano.mp3"))).thenReturn(List.of(staleTrack));
        when(mediaFileRepository.existsById(eq(42))).thenReturn(true);

        List<MediaFile> actual = ReflectionTestUtils.invokeMethod(mediaFileService, "createIndexedTracks", base, cueSheet);

        assertEquals(1, actual.size());
        assertEquals("Fresh Title", actual.get(0).getTitle());
        assertEquals(2L, (long)actual.get(0).getTrackNumber());
        assertEquals(30.0d, actual.get(0).getDuration(), 0.001d);
        assertEquals(7, actual.get(0).getPlayCount());
        verify(mediaFileRepository).save(staleTrack);
    }

    private TrackData createTrack(FileData fileData, int number, String title, int minutes, int seconds, int frames) {
        TrackData trackData = new TrackData(fileData, number, "AUDIO");
        trackData.setTitle(title);
        trackData.getIndices().add(new Index(1, new Position(minutes, seconds, frames)));
        return trackData;
    }
}
