package org.airsonic.player.service;



import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.config.AirsonicScanConfig;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.repository.MusicFolderRepository;
import org.airsonic.player.service.search.IndexManager;
import org.airsonic.player.util.MusicFolderTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = {
    "airsonic.cue.enabled=true",
    "airsonic.scan.parallelism=1"
})
@SpringBootTest
public class MediaScannerServiceTest {

    @Autowired
    private MediaScannerService mediaScannerService;

    @MockitoSpyBean
    private MediaFileService mediaFileService;

    @MockitoSpyBean
    private SettingsService settingsService;

    @MockitoSpyBean
    private IndexManager indexManager;

    @MockitoSpyBean
    private AirsonicScanConfig scanConfig;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MediaFolderService mediaFolderService;

    @Autowired
    private MusicFolderRepository musicFolderRepository;

    @TempDir
    private static Path tempDir;

    @BeforeAll
    public static void setUp() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    private List<MusicFolder> testFolders = new ArrayList<>();

    @BeforeEach
    public void setup() {
        jdbcTemplate.execute("DELETE FROM media_file");
        jdbcTemplate.execute("DELETE FROM album");
        jdbcTemplate.execute("DELETE FROM artist");
        TestCaseUtils.waitForScanFinish(mediaScannerService);
        mediaFolderService.clearMediaFileCache();
        mediaFolderService.clearMusicFolderCache();
        Mockito.reset(indexManager);
    }

    @AfterEach
    public void cleanup() {
        musicFolderRepository.deleteAll(testFolders);
        testFolders = new ArrayList<>();
    }

    @Test
    public void testMusicFullScanTimeOut() {
        when(settingsService.getFullScan()).thenReturn(true);
        when(settingsService.getIgnoreSymLinks()).thenReturn(false);
        when(scanConfig.getFullTimeout()).thenReturn(1);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            Thread.sleep(10000);
            return null;
        }).when(mediaFileService).setMemoryCacheEnabled(anyBoolean());

        // Add the "loop" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicLoopFolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "loop", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        musicFolderRepository.save(musicFolder);
        testFolders.add(musicFolder);
        long start = System.currentTimeMillis();
        TestCaseUtils.execScan(mediaScannerService);
        long end = System.currentTimeMillis();
        // Test that the scan time out is respected
        assertTrue(end - start < 10000);
        verify(indexManager).stopIndexing(any());
    }

    @Test
    public void testMusicScanTimeOut() {
        when(settingsService.getFullScan()).thenReturn(false);
        when(settingsService.getIgnoreSymLinks()).thenReturn(false);
        when(scanConfig.getTimeout()).thenReturn(1);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            Thread.sleep(10000);
            return null;
        }).when(mediaFileService).setMemoryCacheEnabled(anyBoolean());

        // Add the "loop" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicLoopFolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "loop", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        musicFolderRepository.save(musicFolder);
        testFolders.add(musicFolder);
        long start = System.currentTimeMillis();
        TestCaseUtils.execScan(mediaScannerService);
        long end = System.currentTimeMillis();
        // Test that the scan time out is respected
        assertTrue(end - start < 10000);
        verify(indexManager).stopIndexing(any());
    }
}
