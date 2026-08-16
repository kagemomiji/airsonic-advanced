package org.airsonic.player.service;

import org.airsonic.player.config.AirsonicScanConfig;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.service.search.IndexManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MediaScannerServiceArtistSplitTest {

    @Mock private SettingsService settingsService;
    @Mock private PlaylistFileService playlistFileService;
    @Mock private MediaFileService mediaFileService;
    @Mock private MediaFolderService mediaFolderService;
    @Mock private CoverArtService coverArtService;
    @Mock private ArtistService artistService;
    @Mock private AlbumService albumService;
    @Mock private TaskSchedulingService taskService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private IndexManager indexManager;
    @Mock private AirsonicScanConfig scanConfig;
    @Mock private MediaFile file;
    @Mock private MusicFolder musicFolder;

    private MediaScannerService service;
    private Instant lastScanned;
    private Map<String, AtomicInteger> albumCount;
    private Map<String, Artist> artists;

    @BeforeEach
    void setUp() {
        service = new MediaScannerService(settingsService, indexManager, playlistFileService,
                mediaFileService, mediaFolderService, coverArtService, artistService,
                albumService, taskService, messagingTemplate, scanConfig);
        lastScanned = Instant.parse("2020-01-01T00:00:00Z");
        albumCount = new HashMap<>();
        artists = new HashMap<>();
        when(file.isAudio()).thenReturn(true);
    }

    @Test
    void testUpdateArtistNoSplit() {
        when(settingsService.getArtistSeparators()).thenReturn("");
        when(file.getAlbumArtist()).thenReturn("Artist A");

        invokeUpdateArtist(null, file, musicFolder, lastScanned, albumCount, artists);

        verify(artistService).getArtist("Artist A");
    }

    @Test
    void testUpdateArtistSplitTwoArtists() {
        when(settingsService.getArtistSeparators()).thenReturn("/");
        when(file.getAlbumArtist()).thenReturn("Artist A / Artist B");

        invokeUpdateArtist(null, file, musicFolder, lastScanned, albumCount, artists);

        verify(artistService).getArtist("Artist A");
        verify(artistService).getArtist("Artist B");
    }

    @Test
    void testUpdateArtistSplitTrimsWhitespace() {
        when(settingsService.getArtistSeparators()).thenReturn("/");
        when(file.getAlbumArtist()).thenReturn("  Artist A  /  Artist B  ");

        invokeUpdateArtist(null, file, musicFolder, lastScanned, albumCount, artists);

        verify(artistService).getArtist("Artist A");
        verify(artistService).getArtist("Artist B");
        verify(artistService, never()).getArtist(anyString() /* whitespace-only keys */);
    }

    private void invokeUpdateArtist(MediaFile grandParent, MediaFile mediaFile, MusicFolder folder,
            Instant scanned, Map<String, AtomicInteger> count, Map<String, Artist> artistMap) {
        ReflectionTestUtils.invokeMethod(service, "updateArtist",
                grandParent, mediaFile, folder, scanned, count, artistMap);
    }
}
