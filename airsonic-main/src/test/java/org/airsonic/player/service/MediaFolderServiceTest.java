package org.airsonic.player.service;

import org.airsonic.player.command.MusicFolderSettingsCommand.MusicFolderInfo;
import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.domain.User;
import org.airsonic.player.repository.CoverArtRepository;
import org.airsonic.player.repository.MediaFileRepository;
import org.airsonic.player.repository.MusicFolderRepository;
import org.airsonic.player.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
@EnableConfigurationProperties({ AirsonicHomeConfig.class })
@Transactional
public class MediaFolderServiceTest {

    @Autowired
    private MediaFolderService mediaFolderService;

    @MockitoSpyBean
    private MusicFolderRepository musicFolderRepository;

    @MockitoSpyBean
    private UserRepository userRepository;

    @MockitoBean
    private CoverArtRepository coverArtRepository;

    @MockitoBean
    private MediaFileRepository mediaFileRepository;

    @TempDir
    private static Path tempAirsonicHome;

    @TempDir
    private static Path tempDefaultMusicFolder;

    @TempDir
    private static Path tempDefaultPodcastFolder;

    @TempDir
    private Path tempMusicFolder;

    @TempDir
    private Path tempMusicFolder2;

    @Mock
    private MediaFile mockedFile;

    @Mock
    private MediaFile mockedRootFile;

    @Mock
    private MediaFile mockedChildFile;

    @Mock
    private CoverArt mockedCoverArt;

    private final String TEST_USER_NAME = "testUserForMediaFolder";

    @BeforeAll
    public static void setup() {
        System.setProperty("airsonic.home", tempAirsonicHome.toString());
        System.setProperty("airsonic.defaultMusicFolder", tempDefaultMusicFolder.toString());
        System.setProperty("airsonic.defaultPodcastFolder", tempDefaultPodcastFolder.toString());
    }

    @BeforeEach
    public void beforeEach() {
        mediaFolderService.clearMusicFolderCache();
        User user = new User(TEST_USER_NAME, "me@exampl.com");
        userRepository.save(user);
    }

    @AfterEach
    public void afterEach() {
        userRepository.deleteById(TEST_USER_NAME);
    }

    @Test
    public void testGetAllMusicFolders() {

        // given
        long count = musicFolderRepository.count();
        Mockito.reset(musicFolderRepository);

        // when
        List<MusicFolder> musicFolders = mediaFolderService.getAllMusicFolders(true, true);

        // then
        assertEquals(count, musicFolders.size());
        verify(musicFolderRepository).findByDeleted(false);

        // caching test
        mediaFolderService.getAllMusicFolders();
        verify(musicFolderRepository).findByDeleted(false);
    }

    @Test
    public void testGetMusicFoldersForUser() {
        // given
        Mockito.reset(musicFolderRepository);
        User user = userRepository.findById(TEST_USER_NAME).get();
        MusicFolder musicFolder = new MusicFolder(tempMusicFolder, "testMusicFolder", Type.MEDIA, true, Instant.now());
        musicFolderRepository.save(musicFolder);
        user.addMusicFolder(musicFolder);
        userRepository.save(user);

        // when
        List<MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(TEST_USER_NAME);

        // then
        assertEquals(1, musicFolders.size());
        assertEquals(musicFolder, musicFolders.get(0));
        verify(userRepository).findByUsername(TEST_USER_NAME);

        // caching test
        mediaFolderService.getMusicFoldersForUser(TEST_USER_NAME);
        verify(userRepository).findByUsername(TEST_USER_NAME);
    }

    @Test
    public void testSetMusicFoldersForUser() {
        // given
        Mockito.reset(musicFolderRepository);
        User user = userRepository.findById(TEST_USER_NAME).get();
        MusicFolder musicFolder = new MusicFolder(tempMusicFolder, "testMusicFolder", Type.MEDIA, true, Instant.now());
        musicFolderRepository.save(musicFolder);
        MusicFolder musicFolder2 = new MusicFolder(tempMusicFolder.resolve("test2"), "testMusicFolder2", Type.MEDIA,
                true, Instant.now());
        musicFolderRepository.save(musicFolder2);

        // when
        mediaFolderService.setMusicFoldersForUser(TEST_USER_NAME, List.of(musicFolder.getId(), musicFolder2.getId()));

        // then
        user = userRepository.findById(TEST_USER_NAME).get();
        assertEquals(2, user.getMusicFolders().size());
        assertEquals(musicFolder, user.getMusicFolders().get(0));
        assertEquals(musicFolder2, user.getMusicFolders().get(1));
        verify(userRepository).findByUsername(TEST_USER_NAME);

    }

    @Test
    public void testCreateWithSamePath() {
        // given
        MusicFolder existingFolder = new MusicFolder(tempMusicFolder, "Existing Folder", MusicFolder.Type.MEDIA, true,
                Instant.now());
        musicFolderRepository.save(existingFolder);

        // when and then
        MusicFolder newFolder = new MusicFolder(tempMusicFolder, "New Folder", MusicFolder.Type.MEDIA, true,
                Instant.now());
        assertThrows(IllegalArgumentException.class, () -> {
            mediaFolderService.createMusicFolder(newFolder);
        });
    }

    @Test
    public void testCreateWithAncestorPath() {
        // given
        MusicFolder existingFolder = new MusicFolder(tempMusicFolder, "Existing Folder", MusicFolder.Type.MEDIA, true,
                Instant.now());
        musicFolderRepository.save(existingFolder);
        // children
        when(mediaFileRepository.findByFolderAndPathStartsWith(existingFolder, "child" + File.separator))
                .thenReturn(List.of(mockedChildFile));
        when(mockedChildFile.getRelativePath()).thenReturn(Paths.get("child/test.mp3"));
        when(mockedChildFile.getRelativeParentPath()).thenReturn(Paths.get("child"));
        // root
        when(mediaFileRepository.findByFolderAndPath(existingFolder, "child")).thenReturn(List.of(mockedRootFile));
        // cover art
        when(coverArtRepository.findByFolderAndPathStartsWith(existingFolder, "child" + File.separator))
                .thenReturn(List.of(mockedCoverArt));
        when(mockedCoverArt.getRelativePath()).thenReturn(Paths.get("child/test.jpg"));

        // when
        MusicFolder newFolder = new MusicFolder(tempMusicFolder.resolve("child"), "New Folder", MusicFolder.Type.MEDIA,
                true, Instant.now());
        mediaFolderService.createMusicFolder(newFolder);

        // then
        verify(musicFolderRepository).saveAndFlush(newFolder);
        assertTrue(musicFolderRepository.findAll().contains(newFolder));
        assertTrue(musicFolderRepository.findAll().contains(existingFolder));
        // root update
        verify(mockedRootFile).setFolder(newFolder);
        verify(mockedRootFile).setPath("");
        verify(mockedRootFile).setParentPath(null);
        verify(mockedRootFile).setTitle("New Folder");
        verify(mockedRootFile).setMediaType(MediaType.DIRECTORY);
        verify(mediaFileRepository).save(mockedRootFile);
        // child update
        verify(mockedChildFile).setFolder(newFolder);
        verify(mockedChildFile).setPath("test.mp3");
        verify(mockedChildFile).setParentPath("");
        verify(mediaFileRepository).save(mockedChildFile);
        // cover art update
        verify(mockedCoverArt).setFolder(newFolder);
        verify(mockedCoverArt).setPath("test.jpg");
        verify(coverArtRepository).save(mockedCoverArt);
    }

    @Test
    public void testCreateWithDeletedDecendantPath() {
        // given
        MusicFolder existingFolder = new MusicFolder(tempMusicFolder.resolve("child"), "Existing Folder",
                MusicFolder.Type.MEDIA, true, Instant.now());
        existingFolder.setDeleted(true);
        musicFolderRepository.save(existingFolder);
        // root
        when(mediaFileRepository.findByFolderAndPath(existingFolder, "")).thenReturn(List.of(mockedRootFile));
        when(mediaFileRepository.findByFolder(existingFolder)).thenReturn(List.of(mockedChildFile));
        // childredn
        when(mockedChildFile.getPath()).thenReturn("test.mp3");
        when(mockedChildFile.getParentPath()).thenReturn("");
        // cover art
        when(coverArtRepository.findByFolder(existingFolder)).thenReturn(List.of(mockedCoverArt));
        when(mockedCoverArt.getPath()).thenReturn("test.jpg");

        // when
        MusicFolder newFolder = new MusicFolder(tempMusicFolder, "New Folder", MusicFolder.Type.MEDIA, true,
                Instant.now());
        mediaFolderService.createMusicFolder(newFolder);

        // then
        verify(musicFolderRepository).saveAndFlush(newFolder);
        verify(musicFolderRepository).delete(existingFolder);
        assertTrue(musicFolderRepository.findAll().contains(newFolder));
        assertFalse(musicFolderRepository.findAll().contains(existingFolder));

        // root update
        verify(mockedRootFile).setFolder(newFolder);
        verify(mockedRootFile).setPath("child");
        verify(mockedRootFile).setParentPath("");
        verify(mockedRootFile).setTitle(null);
        verify(mediaFileRepository).save(mockedRootFile);

        // child update
        verify(mockedChildFile).setFolder(newFolder);
        verify(mockedChildFile).setPath("child/test.mp3");
        verify(mockedChildFile).setParentPath("child");
        verify(mediaFileRepository).save(mockedChildFile);

        // cover art update
        verify(mockedCoverArt).setFolder(newFolder);
        verify(mockedCoverArt).setPath("child/test.jpg");
        verify(coverArtRepository).save(mockedCoverArt);
    }

    @Test
    public void testCreateWithNonDeletedDecendantPath() {
        Mockito.reset(mediaFileRepository);
        Mockito.reset(coverArtRepository);
        // given
        MusicFolder existingFolder = new MusicFolder(tempMusicFolder.resolve("child"), "Existing Folder",
                MusicFolder.Type.MEDIA, true, Instant.now());
        musicFolderRepository.save(existingFolder);

        // when
        MusicFolder newFolder = new MusicFolder(tempMusicFolder, "New Folder", MusicFolder.Type.MEDIA, true,
                Instant.now());
        mediaFolderService.createMusicFolder(newFolder);

        // then
        verify(musicFolderRepository).saveAndFlush(newFolder);
        verifyNoInteractions(mediaFileRepository);
        verifyNoInteractions(coverArtRepository);
        verify(musicFolderRepository, never()).delete(existingFolder);
        assertTrue(musicFolderRepository.findAll().contains(newFolder));
        assertTrue(musicFolderRepository.findAll().contains(existingFolder));
    }

    @Test
    public void testUpdateWithoutId() {
        // given
        MusicFolderInfo musicFolderInfo = new MusicFolderInfo();
        Mockito.reset(musicFolderRepository);

        // when and then
        assertThrows(IllegalArgumentException.class, () -> {
            mediaFolderService.updateMusicFolderByInfo(musicFolderInfo);
        });
        verifyNoInteractions(musicFolderRepository);
    }

    @Test
    public void testUpdateWithNonExistentId() {
        // given
        MusicFolderInfo musicFolderInfo = new MusicFolderInfo();
        musicFolderInfo.setId(-1);
        musicFolderInfo.setName("test");
        musicFolderInfo.setPath(tempMusicFolder.toString());
        musicFolderInfo.setType(Type.MEDIA.name());
        musicFolderInfo.setEnabled(true);
        Mockito.reset(musicFolderRepository);

        // when and then
        assertThrows(IllegalArgumentException.class, () -> {
            mediaFolderService.updateMusicFolderByInfo(musicFolderInfo);
        });
        verify(musicFolderRepository).findByIdAndDeletedFalse(-1);
        verify(musicFolderRepository, never()).findByDeleted(anyBoolean());
        verify(musicFolderRepository, never()).save(any(MusicFolder.class));

    }

    @Test
    public void testUpdateWithSamePathShouldSuccess() {
        // given
        MusicFolder existingFolder = new MusicFolder(tempMusicFolder, "Existing Folder", MusicFolder.Type.MEDIA, true,
                Instant.now());
        musicFolderRepository.save(existingFolder);
        MusicFolderInfo musicFolderInfo = new MusicFolderInfo(existingFolder, false, "");
        musicFolderInfo.setName("New Name");
        musicFolderInfo.setType(Type.PODCAST.name());
        musicFolderInfo.setEnabled(false);

        Mockito.reset(musicFolderRepository);
        // when
        mediaFolderService.updateMusicFolderByInfo(musicFolderInfo);

        // then
        verify(musicFolderRepository).findByIdAndDeletedFalse(existingFolder.getId());
        verify(musicFolderRepository, never()).findByDeleted(anyBoolean());
        verify(musicFolderRepository).save(any(MusicFolder.class));

        MusicFolder updatedFolder = musicFolderRepository.findById(existingFolder.getId()).get();
        assertEquals(musicFolderInfo.getName(), updatedFolder.getName());
        assertEquals(musicFolderInfo.getPath(), updatedFolder.getPath().toString());
        assertEquals(musicFolderInfo.getType(), updatedFolder.getType().name());
        assertEquals(musicFolderInfo.getEnabled(), updatedFolder.isEnabled());

    }

    @Test
    public void testUpdateWithSamePathWithDefferentFolderShouldFail() {
        // given
        MusicFolder existingFolder = new MusicFolder(tempMusicFolder, "Existing Folder", MusicFolder.Type.MEDIA, true,
                Instant.now());
        musicFolderRepository.save(existingFolder);
        MusicFolder existingFolder2 = new MusicFolder(tempMusicFolder2, "Existing Folder2", MusicFolder.Type.MEDIA,
                true, Instant.now());
        musicFolderRepository.save(existingFolder2);
        MusicFolderInfo musicFolderInfo = new MusicFolderInfo(existingFolder, false, "");
        musicFolderInfo.setPath(tempMusicFolder2.toString());
        Mockito.reset(musicFolderRepository);
        // when and then
        assertThrows(IllegalArgumentException.class, () -> {
            mediaFolderService.updateMusicFolderByInfo(musicFolderInfo);
        });
        verify(musicFolderRepository).findByIdAndDeletedFalse(existingFolder.getId());
        verify(musicFolderRepository).findByDeleted(false);
        verify(musicFolderRepository).findByDeleted(true);
        verify(musicFolderRepository, never()).save(any(MusicFolder.class));
    }

    @Test
    public void testUpdateWithAncestorPathOfDifferentFolderShouldFail() {
        // given
        MusicFolder existingFolder = new MusicFolder(tempMusicFolder, "Existing Folder", MusicFolder.Type.MEDIA, true,
                Instant.now());
        musicFolderRepository.save(existingFolder);
        MusicFolder existingFolder2 = new MusicFolder(tempMusicFolder2, "Existing Folder2", MusicFolder.Type.MEDIA,
                true, Instant.now());
        musicFolderRepository.save(existingFolder2);
        MusicFolderInfo musicFolderInfo = new MusicFolderInfo(existingFolder, false, "");
        musicFolderInfo.setPath(tempMusicFolder2.resolve("child").toString());
        Mockito.reset(musicFolderRepository);
        // when and then
        assertThrows(IllegalArgumentException.class, () -> {
            mediaFolderService.updateMusicFolderByInfo(musicFolderInfo);
        });
        verify(musicFolderRepository).findByIdAndDeletedFalse(existingFolder.getId());
        verify(musicFolderRepository).findByDeleted(false);
        verify(musicFolderRepository).findByDeleted(true);
        verify(musicFolderRepository, never()).save(any(MusicFolder.class));
    }

    @Test
    public void testUpdateWithDecendantPathOfDifferentFolderShouldFail() {
        // given
        MusicFolder existingFolder = new MusicFolder(tempMusicFolder, "Existing Folder", MusicFolder.Type.MEDIA, true,
                Instant.now());
        musicFolderRepository.save(existingFolder);
        MusicFolder existingFolder2 = new MusicFolder(tempMusicFolder2, "Existing Folder2", MusicFolder.Type.MEDIA,
                true, Instant.now());
        musicFolderRepository.save(existingFolder2);
        MusicFolderInfo musicFolderInfo = new MusicFolderInfo(existingFolder, false, "");
        musicFolderInfo.setPath(tempMusicFolder2.getParent().toString());
        Mockito.reset(musicFolderRepository);
        // when and then
        assertThrows(IllegalArgumentException.class, () -> {
            mediaFolderService.updateMusicFolderByInfo(musicFolderInfo);
        });
        verify(musicFolderRepository).findByIdAndDeletedFalse(existingFolder.getId());
        verify(musicFolderRepository).findByDeleted(false);
        verify(musicFolderRepository).findByDeleted(true);
        verify(musicFolderRepository, never()).save(any(MusicFolder.class));
    }

    @Test
    public void testUpdateByInfo() {
        // given
        MusicFolder existingFolder = new MusicFolder(tempMusicFolder, "Existing Folder", MusicFolder.Type.MEDIA, true,
                Instant.now());
        musicFolderRepository.save(existingFolder);
        MusicFolderInfo musicFolderInfo = new MusicFolderInfo(existingFolder, false, "");
        musicFolderInfo.setName("New Name");
        musicFolderInfo.setPath(tempMusicFolder.resolve("child").toString());
        musicFolderInfo.setType(Type.PODCAST.name());
        musicFolderInfo.setEnabled(false);
        Mockito.reset(musicFolderRepository);

        // when
        mediaFolderService.updateMusicFolderByInfo(musicFolderInfo);

        // then
        verify(musicFolderRepository).findByIdAndDeletedFalse(existingFolder.getId());
        verify(musicFolderRepository).findByDeleted(false);
        verify(musicFolderRepository).findByDeleted(true);
        verify(musicFolderRepository).save(any(MusicFolder.class));

        MusicFolder updatedFolder = musicFolderRepository.findById(existingFolder.getId()).get();
        assertEquals(musicFolderInfo.getName(), updatedFolder.getName());
        assertEquals(musicFolderInfo.getPath(), updatedFolder.getPath().toString());
        assertEquals(musicFolderInfo.getType(), updatedFolder.getType().name());
        assertEquals(musicFolderInfo.getEnabled(), updatedFolder.isEnabled());
    }

    @Test
    public void testEnableNonexistentPodcastFolder() {
        // when
        boolean result = mediaFolderService.enablePodcastFolder(-1);

        // then
        assertFalse(result);
        verify(musicFolderRepository).findByIdAndTypeAndDeletedFalse(-1, Type.PODCAST);
    }

    @Test
    public void testEnableNonPodcastFolder() {
        // given
        MusicFolder mediaFolder = new MusicFolder(tempMusicFolder, "Media Folder", MusicFolder.Type.MEDIA, true,
                Instant.now());
        musicFolderRepository.save(mediaFolder);

        // when
        boolean result = mediaFolderService.enablePodcastFolder(mediaFolder.getId());

        // then
        assertFalse(result);
        verify(musicFolderRepository).findByIdAndTypeAndDeletedFalse(mediaFolder.getId(), Type.PODCAST);
    }

    @Test
    public void testEnablePodcastFolderSuccessfully() {
        // given
        MusicFolder podcastFolder1 = new MusicFolder(tempMusicFolder.resolve("podcast1"), "Podcast Folder 1",
                MusicFolder.Type.PODCAST, false, Instant.now());
        MusicFolder podcastFolder2 = new MusicFolder(tempMusicFolder.resolve("podcast2"), "Podcast Folder 2",
                MusicFolder.Type.PODCAST, true, Instant.now());
        musicFolderRepository.saveAll(List.of(podcastFolder1, podcastFolder2));

        // when
        boolean result = mediaFolderService.enablePodcastFolder(podcastFolder1.getId());

        // then
        assertTrue(result);
        assertTrue(musicFolderRepository.findById(podcastFolder1.getId()).get().isEnabled());
        assertFalse(musicFolderRepository.findById(podcastFolder2.getId()).get().isEnabled());
        verify(musicFolderRepository).findByIdAndTypeAndDeletedFalse(podcastFolder1.getId(), Type.PODCAST);
        verify(musicFolderRepository).findByIdNotAndTypeAndDeletedFalse(podcastFolder1.getId(), Type.PODCAST);
    }

    @Test
    public void testDeleteMusicFolderWithNonExistentId() {

        // given
        Mockito.reset(musicFolderRepository);
        Mockito.reset(mediaFileRepository);

        // when
        mediaFolderService.deleteMusicFolder(-1);

        // then
        verify(musicFolderRepository).findByIdAndDeletedFalse(-1);
        verifyNoMoreInteractions(musicFolderRepository);
        verifyNoInteractions(mediaFileRepository);
    }

    @Test
    public void testDeleteMusicFolderWithEmptyFolder() {

        // given
        MusicFolder musicFolder = new MusicFolder(tempMusicFolder, "Music Folder", MusicFolder.Type.MEDIA, true,
                Instant.now());
        musicFolderRepository.save(musicFolder);
        long count = musicFolderRepository.count();
        Mockito.reset(musicFolderRepository);
        Mockito.reset(mediaFileRepository);

        when(mediaFileRepository.countByFolder(musicFolder)).thenReturn(0);

        // when
        mediaFolderService.deleteMusicFolder(musicFolder.getId());

        // then
        verify(musicFolderRepository).findByIdAndDeletedFalse(musicFolder.getId());
        verify(musicFolderRepository).delete(any(MusicFolder.class));
        assertEquals(count - 1L, musicFolderRepository.count());
    }

    @Test
    public void testDeleteMusicFolderWithAncestors() {

        // given
        MusicFolder musicFolder = new MusicFolder(tempMusicFolder, "Music Folder", MusicFolder.Type.MEDIA, true,
                Instant.now());
        musicFolderRepository.save(musicFolder);
        MusicFolder childFolder = new MusicFolder(tempMusicFolder.resolve("child"), "Child Folder",
                MusicFolder.Type.MEDIA, true, Instant.now());
        musicFolderRepository.save(childFolder);
        long count = musicFolderRepository.count();
        Mockito.reset(musicFolderRepository);
        Mockito.reset(mediaFileRepository);

        when(mediaFileRepository.countByFolder(childFolder)).thenReturn(10);
        when(mediaFileRepository.findByFolderAndPresentTrue(childFolder)).thenReturn(List.of(mockedFile));

        // root
        when(mediaFileRepository.findByFolderAndPath(childFolder, "")).thenReturn(List.of(mockedRootFile));
        when(mediaFileRepository.findByFolder(childFolder)).thenReturn(List.of(mockedChildFile));
        // children
        when(mockedChildFile.getPath()).thenReturn("test.mp3");
        when(mockedChildFile.getParentPath()).thenReturn("");
        // cover art
        when(coverArtRepository.findByFolder(childFolder)).thenReturn(List.of(mockedCoverArt));
        when(mockedCoverArt.getPath()).thenReturn("test.jpg");

        // when
        mediaFolderService.deleteMusicFolder(childFolder.getId());

        // then
        verify(musicFolderRepository).findByIdAndDeletedFalse(childFolder.getId());
        verify(musicFolderRepository).delete(any(MusicFolder.class));
        verify(mockedFile).setPresent(false);
        verify(mockedFile).setChildrenLastUpdated(Instant.ofEpochMilli(1));
        verify(mediaFileRepository).save(mockedFile);
        assertEquals(count - 1L, musicFolderRepository.count());

        // root update
        verify(mockedRootFile).setFolder(musicFolder);
        verify(mockedRootFile).setPath("child");
        verify(mockedRootFile).setParentPath("");
        verify(mockedRootFile).setTitle(null);
        verify(mediaFileRepository).save(mockedRootFile);

        // child update
        verify(mockedChildFile).setFolder(musicFolder);
        verify(mockedChildFile).setPath("child/test.mp3");
        verify(mockedChildFile).setParentPath("child");
        verify(mediaFileRepository).save(mockedChildFile);

        // cover art update
        verify(mockedCoverArt).setFolder(musicFolder);
        verify(mockedCoverArt).setPath("child/test.jpg");
        verify(coverArtRepository).save(mockedCoverArt);

    }

    @Test
    public void testDeleteMusicFolderWithDecendants() {

        // given
        MusicFolder musicFolder = new MusicFolder(tempMusicFolder, "Music Folder", MusicFolder.Type.MEDIA, true,
                Instant.now());
        musicFolderRepository.save(musicFolder);
        MusicFolder childFolder = new MusicFolder(tempMusicFolder.resolve("child"), "Child Folder",
                MusicFolder.Type.MEDIA, true, Instant.now());
        musicFolderRepository.save(childFolder);
        long count = musicFolderRepository.count();
        Mockito.reset(musicFolderRepository);
        Mockito.reset(mediaFileRepository);

        when(mediaFileRepository.findByFolderAndPresentTrue(musicFolder)).thenReturn(List.of(mockedFile));
        when(mediaFileRepository.countByFolder(musicFolder)).thenReturn(10);

        // when
        mediaFolderService.deleteMusicFolder(musicFolder.getId());

        // then
        verify(musicFolderRepository).findByIdAndDeletedFalse(musicFolder.getId());
        verify(musicFolderRepository).save(any(MusicFolder.class));
        verify(mediaFileRepository).countByFolder(musicFolder);
        verify(mediaFileRepository).findByFolderAndPresentTrue(musicFolder);
        verify(mediaFileRepository).save(mockedFile);
        verifyNoInteractions(coverArtRepository);
        verifyNoMoreInteractions(mediaFileRepository);
        verify(mockedFile).setPresent(false);
        verify(mockedFile).setChildrenLastUpdated(Instant.ofEpochMilli(1));
        verify(mediaFileRepository).save(mockedFile);
        assertEquals(count, musicFolderRepository.count());
        MusicFolder deletedFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        assertTrue(deletedFolder.isDeleted());
        assertFalse(deletedFolder.isEnabled());

    }

    @Test
    public void testDeleteMusicFolder() {

        // given
        MusicFolder musicFolder = new MusicFolder(tempMusicFolder, "Music Folder", MusicFolder.Type.MEDIA, true,
                Instant.now());
        musicFolderRepository.save(musicFolder);
        long count = musicFolderRepository.count();
        Mockito.reset(musicFolderRepository);
        Mockito.reset(mediaFileRepository);
        Mockito.reset(coverArtRepository);

        when(mediaFileRepository.findByFolderAndPresentTrue(musicFolder)).thenReturn(List.of(mockedFile));
        when(mediaFileRepository.countByFolder(musicFolder)).thenReturn(10);

        // when
        mediaFolderService.deleteMusicFolder(musicFolder.getId());

        // then
        verify(musicFolderRepository).findByIdAndDeletedFalse(musicFolder.getId());
        verify(musicFolderRepository).save(any(MusicFolder.class));
        verify(mediaFileRepository).countByFolder(musicFolder);
        verify(mediaFileRepository).findByFolderAndPresentTrue(musicFolder);
        verify(mediaFileRepository).save(mockedFile);
        verifyNoInteractions(coverArtRepository);
        verifyNoMoreInteractions(mediaFileRepository);
        verify(mockedFile).setPresent(false);
        verify(mockedFile).setChildrenLastUpdated(Instant.ofEpochMilli(1));
        verify(mediaFileRepository).save(mockedFile);
        assertEquals(count, musicFolderRepository.count());
        MusicFolder deletedFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        assertTrue(deletedFolder.isDeleted());
        assertFalse(deletedFolder.isEnabled());

    }
}
