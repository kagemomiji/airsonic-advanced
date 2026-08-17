package org.airsonic.player.service;

import org.airsonic.player.config.AirsonicScanConfig;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.service.search.IndexManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MediaScannerServiceNonAlbumArtistTest {

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

    private MediaScannerService buildService() {
        return new MediaScannerService(settingsService, indexManager, playlistFileService,
                mediaFileService, mediaFolderService, coverArtService, artistService,
                albumService, taskService, messagingTemplate, scanConfig);
    }

    @Test
    public void testUpdateArtistFallsBackToArtistTagWhenNoAlbumArtist() {
        MediaScannerService service = buildService();

        MediaFile file = new MediaFile();
        file.setAlbumArtist(null);
        file.setArtist("SoloArtist");
        file.setMediaType(MediaFile.MediaType.MUSIC);

        MusicFolder folder = new MusicFolder("/music", "Music", true, null, false);
        Instant now = Instant.now();

        when(artistService.getArtist("SoloArtist")).thenReturn(null);

        ArgumentCaptor<Artist> savedArtist = ArgumentCaptor.forClass(Artist.class);

        ReflectionTestUtils.invokeMethod(service, "updateArtist",
                null, file, folder, now, new HashMap<String, AtomicInteger>(), new HashMap<String, Artist>());

        verify(artistService).save(savedArtist.capture());
        assertThat(savedArtist.getValue().getName()).isEqualTo("SoloArtist");
    }

    @Test
    public void testUpdateArtistPrefersAlbumArtistOverArtist() {
        MediaScannerService service = buildService();

        MediaFile file = new MediaFile();
        file.setAlbumArtist("AlbumBand");
        file.setArtist("FeaturedArtist");
        file.setMediaType(MediaFile.MediaType.MUSIC);

        MusicFolder folder = new MusicFolder("/music", "Music", true, null, false);
        Instant now = Instant.now();

        when(artistService.getArtist("AlbumBand")).thenReturn(null);

        ArgumentCaptor<Artist> savedArtist = ArgumentCaptor.forClass(Artist.class);

        ReflectionTestUtils.invokeMethod(service, "updateArtist",
                null, file, folder, now, new HashMap<String, AtomicInteger>(), new HashMap<String, Artist>());

        verify(artistService).save(savedArtist.capture());
        assertThat(savedArtist.getValue().getName()).isEqualTo("AlbumBand");
    }
}
