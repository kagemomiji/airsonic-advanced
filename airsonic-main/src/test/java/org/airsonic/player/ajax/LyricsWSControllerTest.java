package org.airsonic.player.ajax;

import org.airsonic.player.domain.Lyrics;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.LyricsService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LyricsWSControllerTest {

    @Mock
    private LyricsService lyricsService;
    @Mock
    private SecurityService securityService;
    @Mock
    private MediaFileService mediaFileService;
    @Mock
    private Principal user;

    @InjectMocks
    private LyricsWSController controller;

    private MediaFile mediaFile;

    @BeforeEach
    void setUp() {
        mediaFile = new MediaFile();
        mediaFile.setId(42);
        when(user.getName()).thenReturn("alice");
    }

    @Test
    void nullId_returnsEmptyStatus() {
        LyricsWSController.LyricsGetRequest req = new LyricsWSController.LyricsGetRequest(null, null, null);
        LyricsStatus status = controller.getLyrics(user, req);
        assertFalse(status.isPersisted());
        verifyNoInteractions(mediaFileService, lyricsService, securityService);
    }

    @Test
    void unknownMediaFile_returnsEmptyStatus() {
        when(mediaFileService.getMediaFile(99)).thenReturn(null);
        LyricsWSController.LyricsGetRequest req = new LyricsWSController.LyricsGetRequest(null, null, 99);
        LyricsStatus status = controller.getLyrics(user, req);
        assertFalse(status.isPersisted());
        verifyNoInteractions(lyricsService);
    }

    @Test
    void accessDenied_returnsEmptyStatus() {
        when(mediaFileService.getMediaFile(42)).thenReturn(mediaFile);
        when(securityService.isFolderAccessAllowed(mediaFile, "alice")).thenReturn(false);
        LyricsWSController.LyricsGetRequest req = new LyricsWSController.LyricsGetRequest(null, null, 42);
        LyricsStatus status = controller.getLyrics(user, req);
        assertFalse(status.isPersisted());
        verifyNoInteractions(lyricsService);
    }

    @Test
    void lyricsFound_returnsPersistedTrue() throws Exception {
        Lyrics lyrics = new Lyrics();
        when(mediaFileService.getMediaFile(42)).thenReturn(mediaFile);
        when(securityService.isFolderAccessAllowed(mediaFile, "alice")).thenReturn(true);
        when(lyricsService.getLyricsFromMediaFile(mediaFile)).thenReturn(lyrics);
        LyricsWSController.LyricsGetRequest req = new LyricsWSController.LyricsGetRequest(null, null, 42);
        LyricsStatus status = controller.getLyrics(user, req);
        assertTrue(status.isPersisted());
    }

    @Test
    void noLyricsFound_returnsPersistedFalse() throws Exception {
        when(mediaFileService.getMediaFile(42)).thenReturn(mediaFile);
        when(securityService.isFolderAccessAllowed(mediaFile, "alice")).thenReturn(true);
        when(lyricsService.getLyricsFromMediaFile(mediaFile)).thenReturn(null);
        LyricsWSController.LyricsGetRequest req = new LyricsWSController.LyricsGetRequest(null, null, 42);
        LyricsStatus status = controller.getLyrics(user, req);
        assertFalse(status.isPersisted());
    }

    @Test
    void serviceException_returnsEmptyStatus() throws Exception {
        when(mediaFileService.getMediaFile(42)).thenReturn(mediaFile);
        when(securityService.isFolderAccessAllowed(mediaFile, "alice")).thenReturn(true);
        when(lyricsService.getLyricsFromMediaFile(mediaFile)).thenThrow(new RuntimeException("db error"));
        LyricsWSController.LyricsGetRequest req = new LyricsWSController.LyricsGetRequest(null, null, 42);
        LyricsStatus status = controller.getLyrics(user, req);
        assertFalse(status.isPersisted());
    }
}
