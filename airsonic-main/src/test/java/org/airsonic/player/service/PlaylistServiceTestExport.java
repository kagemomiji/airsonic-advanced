package org.airsonic.player.service;

import com.google.common.collect.Lists;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.dao.PlaylistDao;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.service.playlist.DefaultPlaylistExportHandler;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PlaylistServiceTestExport {

    PlaylistService playlistService;

    @InjectMocks
    DefaultPlaylistExportHandler defaultPlaylistExportHandler;

    @Mock
    MediaFileDao mediaFileDao;

    @Mock
    PlaylistDao playlistDao;

    @Mock
    MediaFileService mediaFileService;

    @Mock
    MediaFolderService mediaFolderService;

    @Mock
    SettingsService settingsService;

    @Mock
    SecurityService securityService;

    @Mock
    MusicFolder mockedFolder;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Captor
    ArgumentCaptor<Playlist> actual;

    @Captor
    ArgumentCaptor<List<MediaFile>> medias;

    @Before
    public void setup() {
        playlistService = new PlaylistService(mediaFileDao,
                                              playlistDao,
                                              securityService,
                                              settingsService,
                                              Lists.newArrayList(
                                                      defaultPlaylistExportHandler),
                                              Collections.emptyList());
    }

    @Test
    public void testExportToM3U() throws Exception {

        when(mediaFileDao.getFilesInPlaylist(eq(23))).thenReturn(getPlaylistFiles());
        when(settingsService.getPlaylistExportFormat()).thenReturn("m3u");
        when(mediaFolderService.getMusicFolderById(any())).thenReturn(mockedFolder);
        when(mockedFolder.getPath()).thenReturn(Paths.get("/"));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        playlistService.exportPlaylist(23, outputStream);
        byte[] actual = outputStream.toByteArray();
        byte[] expected = getClass().getResourceAsStream("/PLAYLISTS/23.m3u").readAllBytes();
        assertArrayEquals(expected, actual);
    }

    private List<MediaFile> getPlaylistFiles() {
        List<MediaFile> mediaFiles = new ArrayList<>();

        MediaFile mf1 = new MediaFile();
        mf1.setId(142);
        mf1.setPath("/some/path/to_album/to_artist/name - of - song.mp3");
        mf1.setPresent(true);
        mediaFiles.add(mf1);

        MediaFile mf2 = new MediaFile();
        mf2.setId(1235);
        mf2.setPath("/some/path/to_album2/to_artist/another song.mp3");
        mf2.setPresent(true);
        mediaFiles.add(mf2);

        MediaFile mf3 = new MediaFile();
        mf3.setId(198403);
        mf3.setPath("/some/path/to_album2/to_artist/another song2.mp3");
        mf3.setPresent(false);
        mediaFiles.add(mf3);

        return mediaFiles;
    }
}
