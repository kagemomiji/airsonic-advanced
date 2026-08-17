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
import org.airsonic.player.service.cache.MediaFileCache;
import org.airsonic.player.service.metadata.MetaDataParserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

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
    @SuppressWarnings("unchecked")
    public void testGetSongsByGenreUsesFindAllWithSpecification() {
        MediaFile song = new MediaFile();
        song.setPath("songs/track.mp3");
        song.setMediaType(MediaType.MUSIC);
        song.setGenre("Dance; Edm; House");
        song.setFolder(mockedFolder);

        when(settingsService.getGenreSeparators()).thenReturn(";");
        when(mediaFileRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(List.of(song));

        List<MediaFile> result = mediaFileService.getSongsByGenre(0, 10, "Dance", List.of(mockedFolder));

        assertEquals(1, result.size());
        assertEquals("Dance; Edm; House", result.get(0).getGenre());
        verify(mediaFileRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetSongsByGenreEmptyFoldersReturnsEmpty() {
        List<MediaFile> result = mediaFileService.getSongsByGenre(0, 10, "Dance", Collections.emptyList());
        assertTrue(result.isEmpty());
    }
}
