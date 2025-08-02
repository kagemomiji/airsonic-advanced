package org.airsonic.player.service;

import org.airsonic.player.domain.*;
import org.airsonic.player.util.FileUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class LibraryStatusServiceTest {

    @Mock
    private MediaScannerService mediaScannerService;
    @Mock
    private SettingsService settingsService;
    @Mock
    private PersonalSettingsService personalSettingsService;
    @Mock
    private MediaFolderService mediaFolderService;
    @Mock
    private InternetRadioService internetRadioService;
    @InjectMocks
    private LibraryStatusService libraryStatusService;

    @Mock
    private UserSettings userSettings;

    @Test
    void getLastModified_returnsMinusOne_whenMediaScannerIsScanning() throws Exception {
        when(mediaScannerService.isScanning()).thenReturn(true);

        long result = libraryStatusService.getLastModified("user", null);

        assertEquals(-1L, result);
        verify(mediaScannerService).isScanning();
        verifyNoMoreInteractions(mediaScannerService, settingsService, personalSettingsService, mediaFolderService, internetRadioService);
    }

    @Test
    void getLastModified_updatesSelectedMusicFolderId_whenMusicFolderIdIsNotNull() throws Exception {
        when(mediaScannerService.isScanning()).thenReturn(false);
        when(personalSettingsService.getUserSettings("user")).thenReturn(userSettings);
        when(userSettings.getChanged()).thenReturn(Instant.now().plusSeconds(1000));
        when(settingsService.getSettingsChanged()).thenReturn(0L);
        when(mediaFolderService.getMusicFoldersForUser("user")).thenReturn(Collections.emptyList());
        when(internetRadioService.getEnabledInternetRadios()).thenReturn(Collections.emptyList());

        libraryStatusService.getLastModified("user", 42);

        verify(personalSettingsService).updateSelectedMusicFolderId("user", 42);
    }

    @Test
    void getLastModified_returnsMaxOfAllRelevantTimestamps() throws Exception {
        String username = "user";
        Integer musicFolderId = null;

        Instant now = Instant.now();

        when(personalSettingsService.getUserSettings(username)).thenReturn(userSettings);
        when(userSettings.getSelectedMusicFolderId()).thenReturn(1);
        when(userSettings.getChanged()).thenReturn(now.plusSeconds(1000));

        when(settingsService.getSettingsChanged()).thenReturn(now.plusSeconds(2000).toEpochMilli());

        MusicFolder folder1 = mock(MusicFolder.class);
        when(folder1.getId()).thenReturn(1);
        Path folder1Path = mock(Path.class);
        when(folder1.getPath()).thenReturn(folder1Path);
        when(folder1.getChanged()).thenReturn(now.plusSeconds(3000));

        MusicFolder folder2 = mock(MusicFolder.class);
        Path folder2Path = mock(Path.class);
        when(folder2.getChanged()).thenReturn(now.plusSeconds(3500));

        List<MusicFolder> folders = Arrays.asList(folder1, folder2);
        when(mediaFolderService.getMusicFoldersForUser(username)).thenReturn(folders);

        InternetRadio radio1 = mock(InternetRadio.class);
        when(radio1.getChanged()).thenReturn(now.plusSeconds(5000));
        InternetRadio radio2 = mock(InternetRadio.class);
        when(radio2.getChanged()).thenReturn(now.plusSeconds(2500));
        when(internetRadioService.getEnabledInternetRadios()).thenReturn(Arrays.asList(radio1, radio2));

        try (MockedStatic<FileUtil> fileUtilMock = mockStatic(FileUtil.class)) {
            fileUtilMock.when(() -> FileUtil.lastModified(folder1Path)).thenReturn(now.plusSeconds(6000));
            fileUtilMock.when(() -> FileUtil.lastModified(folder2Path)).thenReturn(now.plusSeconds(4000));

            long result = libraryStatusService.getLastModified(username, musicFolderId);

            assertEquals(now.plusSeconds(6000L).toEpochMilli(), result);
        }
    }

    @Test
    void getLastModified_handlesNoMusicFoldersOrRadios() throws Exception {
        String username = "user";
        when(mediaScannerService.isScanning()).thenReturn(false);

        when(personalSettingsService.getUserSettings(username)).thenReturn(userSettings);
        when(userSettings.getChanged()).thenReturn(Instant.ofEpochMilli(1000L));
        when(settingsService.getSettingsChanged()).thenReturn(500L);
        when(mediaFolderService.getMusicFoldersForUser(username)).thenReturn(Collections.emptyList());
        when(internetRadioService.getEnabledInternetRadios()).thenReturn(Collections.emptyList());

        long expected = Math.max(
                Instant.parse("2012-03-06T00:00:00.00Z").toEpochMilli(),
                Math.max(500L, 1000L)
        );

        long result = libraryStatusService.getLastModified(username, null);

        assertEquals(expected, result);
    }
}