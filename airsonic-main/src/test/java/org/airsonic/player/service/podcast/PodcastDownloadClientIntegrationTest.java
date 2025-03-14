package org.airsonic.player.service.podcast;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastEpisode;
import org.airsonic.player.domain.PodcastStatus;
import org.airsonic.player.repository.MediaFileRepository;
import org.airsonic.player.repository.MusicFolderRepository;
import org.airsonic.player.repository.PodcastChannelRepository;
import org.airsonic.player.repository.PodcastEpisodeRepository;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.VersionService;
import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicStatusLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = {PodcastTestConfig.class})
@EnableConfigurationProperties({AirsonicHomeConfig.class})
public class PodcastDownloadClientIntegrationTest {

    @MockitoBean
    private VersionService versionService;

    @TempDir
    private Path tempFolder;

    @TempDir
    private static Path airsonicFolder;

    @Autowired
    private PodcastDownloadClient podcastDownloadClient;

    @Mock
    private CloseableHttpClient mockedHttpClient;

    @Mock
    private HttpEntity mockedHttpEntity;

    @Mock
    private CloseableHttpResponse mockedHttpResponse;

    @Autowired
    private PodcastEpisodeRepository podcastEpisodeRepository;

    @Autowired
    private PodcastChannelRepository podcastChannelRepository;

    @Autowired
    private MusicFolderRepository musicFolderRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private MediaFolderService mediaFolderService;

    private PodcastEpisode podcastEpisode;

    private MediaFile channelMediaFile;

    private String folderPath;

    @BeforeAll
    private static void init() {
        System.setProperty("airsonic.home", airsonicFolder.toString());
    }

    @BeforeEach
    private void setUp() throws IOException {
        MusicFolder podcastFolder = musicFolderRepository.findAll().stream()
                .filter(folder -> folder.getType() == MusicFolder.Type.PODCAST).findFirst().orElse(null);
        folderPath = podcastFolder.getPath().toString();
        podcastFolder.setPath(tempFolder);
        musicFolderRepository.saveAndFlush(podcastFolder);

        channelMediaFile = new MediaFile();
        channelMediaFile.setFolder(podcastFolder);
        channelMediaFile.setPresent(true);
        channelMediaFile.setChanged(Instant.now());
        channelMediaFile.setCreated(Instant.now());
        channelMediaFile.setParentPath("");
        channelMediaFile.setPath("test");
        channelMediaFile.setLastScanned(Instant.now());
        channelMediaFile.setChildrenLastUpdated(Instant.now());
        channelMediaFile.setMediaType(MediaFile.MediaType.PODCAST);
        mediaFileRepository.saveAndFlush(channelMediaFile);
        Files.createDirectories(tempFolder.resolve("test"));

        PodcastChannel podcastChannel = new PodcastChannel();
        podcastChannel.setUrl("http://example.com");
        podcastChannel.setMediaFile(channelMediaFile);
        podcastChannel.setTitle("Test");
        podcastChannel.setStatus(PodcastStatus.COMPLETED);
        podcastChannelRepository.save(podcastChannel);

        podcastEpisode = new PodcastEpisode();
        podcastEpisode.setUrl("http://example.com");
        podcastEpisode.setTitle("Test");
        podcastEpisode.setStatus(PodcastStatus.SKIPPED);
        podcastEpisode.setChannel(podcastChannel);
        podcastEpisodeRepository.save(podcastEpisode);
        mediaFolderService.clearMusicFolderCache();
    }

    @AfterEach
    private void tearDown() throws IOException {
        musicFolderRepository.findAll().stream().filter(folder -> folder.getType() == MusicFolder.Type.PODCAST)
                .findFirst().ifPresent(folder -> {
                    folder.setPath(Path.of(folderPath));
                    musicFolderRepository.saveAndFlush(folder);
                });
        mediaFileRepository.delete(channelMediaFile);
        podcastEpisodeRepository.delete(podcastEpisode);
        deleteDirectory(tempFolder.resolve("test"));
    }

    @Test
    public void testDownload() throws IOException {
        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class,
                Mockito.CALLS_REAL_METHODS)) {
            mockedHttpClients.when(() -> HttpClients.createDefault()).thenReturn(mockedHttpClient);
            when(mockedHttpClient.execute(any())).thenReturn(mockedHttpResponse);
            HttpEntity httpEntity = new ByteArrayEntity("non mp3 data".getBytes());
            StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "test");
            when(mockedHttpResponse.getEntity()).thenReturn(httpEntity);
            when(mockedHttpResponse.getStatusLine()).thenReturn(statusLine);
            podcastDownloadClient.downloadEpisode(podcastEpisode.getId()).join();
        }
        PodcastEpisode episode = podcastEpisodeRepository.findById(podcastEpisode.getId()).orElse(null);
        assertNotNull(episode);
        assertEquals(episode.getStatus(), PodcastStatus.ERROR);
        List<MediaFile> mediaFiles = mediaFileRepository.findByFolderAndPath(channelMediaFile.getFolder(), "test");
        assertEquals(1, mediaFiles.size());
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.list(directory).forEach(file -> {
                try {
                    if (Files.isDirectory(file)) {
                        deleteDirectory(file);
                    } else {
                        Files.deleteIfExists(file);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        Files.deleteIfExists(directory);
    }

}
