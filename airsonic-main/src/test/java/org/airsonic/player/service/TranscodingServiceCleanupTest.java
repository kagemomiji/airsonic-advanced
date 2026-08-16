package org.airsonic.player.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class TranscodingServiceCleanupTest {

    @Mock
    private SettingsService settingsService;
    @Mock
    private org.airsonic.player.repository.PlayerRepository playerRepository;
    @Mock
    private org.airsonic.player.repository.TranscodingRepository transcodingRepository;
    @Mock
    private PersonalSettingsService personalSettingsService;
    @Mock
    private TaskSchedulingService taskService;

    @InjectMocks
    private TranscodingService transcodingService;

    @TempDir
    Path tempDir;

    private String originalTmpDir;

    @BeforeEach
    void setUp() {
        originalTmpDir = System.getProperty("java.io.tmpdir");
        System.setProperty("java.io.tmpdir", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        System.setProperty("java.io.tmpdir", originalTmpDir);
    }

    private void invokeCleanup() throws Exception {
        Method m = TranscodingService.class.getDeclaredMethod("cleanupStaleTranscodeTmpFiles");
        m.setAccessible(true);
        m.invoke(transcodingService);
    }

    @Test
    void staleAirsonicFileIsDeleted() throws Exception {
        Path stale = Files.createFile(tempDir.resolve("airsonic-stale-test.tmp"));
        Files.setLastModifiedTime(stale, FileTime.from(Instant.now().minus(2, ChronoUnit.HOURS)));

        invokeCleanup();

        assertFalse(Files.exists(stale), "stale airsonic temp file should have been deleted");
    }

    @Test
    void recentAirsonicFileIsNotDeleted() throws Exception {
        Path recent = Files.createFile(tempDir.resolve("airsonic-recent-test.tmp"));
        // default mtime is now, well within the 1-hour window

        invokeCleanup();

        assertTrue(Files.exists(recent), "recent airsonic temp file should not be deleted");
    }

    @Test
    void nonAirsonicStaleFileIsNotDeleted() throws Exception {
        Path other = Files.createFile(tempDir.resolve("ffmpeg-tmp-123.tmp"));
        Files.setLastModifiedTime(other, FileTime.from(Instant.now().minus(2, ChronoUnit.HOURS)));

        invokeCleanup();

        assertTrue(Files.exists(other), "non-airsonic temp file should not be touched");
    }
}
