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
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.service.podcast;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastEpisode;
import org.airsonic.player.domain.PodcastStatus;
import org.airsonic.player.domain.Version;
import org.airsonic.player.repository.PodcastEpisodeRepository;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PodcastPersistenceService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.VersionService;
import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicStatusLine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = {PodcastTestConfig.class})
@EnableConfigurationProperties({AirsonicHomeConfig.class})
public class PodcastDownloadClientTest {

    @MockitoBean
    private PodcastEpisodeRepository podcastEpisodeRepository;
    @MockitoBean
    private PodcastPersistenceService podcastPersistenceService;
    @MockitoBean
    private MediaFolderService mediaFolderService;
    @MockitoBean
    private MediaFileService mediaFileService;
    @MockitoBean
    private SecurityService securityService;
    @MockitoBean
    private VersionService versionService;
    @TempDir
    private Path tempFolder;
    @TempDir
    private static Path airsonicFolder;

    @Mock
    private PodcastEpisode mockedEpisode;
    @Mock
    private PodcastChannel mockedChannel;
    @Mock
    private MediaFile mockedChannelMediaFile;
    @Mock
    private MediaFile mockedEpisodeMediaFile;
    @Mock
    private MusicFolder mockedMusicFolder;
    @Mock
    private CloseableHttpClient mockedHttpClient;
    @Mock
    private CloseableHttpResponse mockedHttpResponse;
    @Mock
    private ThreadPoolTaskExecutor podcastDownloadThreadPool;

    @Autowired
    private PodcastDownloadClient podcastDownloadClient;
    @Autowired
    private ResourceLoader resourceLoader;

    @BeforeAll
    private static void init() {
        System.setProperty("airsonic.home", airsonicFolder.toString());
    }

    @Test
    public void testDownloadEpisode() throws Exception {

        // given
        when(podcastPersistenceService.prepareDownloadEpisode(1)).thenReturn(mockedEpisode);
        when(podcastPersistenceService.isEpisodeDeleted(1)).thenReturn(false);
        when(mockedEpisode.getChannel()).thenReturn(mockedChannel);
        when(mockedEpisode.getUrl()).thenReturn("http://test.com/test.mp3");
        when(mockedEpisode.getChannel()).thenReturn(mockedChannel);
        when(mockedEpisode.getTitle()).thenReturn("testEpisode");
        when(mockedChannel.getMediaFile()).thenReturn(mockedChannelMediaFile);
        when(mockedChannelMediaFile.getFolder()).thenReturn(mockedMusicFolder);
        when(mockedMusicFolder.getPath()).thenReturn(tempFolder);
        when(mockedChannelMediaFile.getFullPath()).thenReturn(tempFolder);
        when(securityService.isWriteAllowed(Paths.get("test.mp3"), mockedMusicFolder)).thenReturn(true);
        when(versionService.getLocalVersion()).thenReturn(new Version("1.0.0"));
        when(mediaFileService.getMediaFile(any(Path.class), eq(mockedMusicFolder))).thenReturn(mockedEpisodeMediaFile);
        when(mockedEpisodeMediaFile.getFullPath()).thenReturn(tempFolder.resolve("test.mp3"));

        // when
        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class,
                Mockito.CALLS_REAL_METHODS)) {
            mockedHttpClients.when(() -> HttpClients.createDefault()).thenReturn(mockedHttpClient);
            when(mockedHttpClient.execute(any())).thenReturn(mockedHttpResponse);
            StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "test");
            when(mockedHttpResponse.getStatusLine()).thenReturn(statusLine);
            Resource resource = resourceLoader.getResource("classpath:/MEDIAS/piano.mp3");
            HttpEntity entity = new ByteArrayEntity(resource.getContentAsByteArray());
            when(mockedHttpResponse.getEntity()).thenReturn(entity);
            podcastDownloadClient.downloadEpisode(1).get();
        }

        // then
        verify(mockedEpisode).setStatus(PodcastStatus.COMPLETED);
        verify(mockedEpisode).setErrorMessage(null);
        verify(mockedEpisode, times(5)).setBytesDownloaded(anyLong());
        verify(mockedEpisode).setMediaFile(mockedEpisodeMediaFile);
        verify(podcastPersistenceService, times(5)).updateEpisode(mockedEpisode);
        verify(podcastPersistenceService).deleteObsoleteEpisodes(mockedChannel);
        // verify media file is refreshed by updateTag
        verify(mediaFileService).refreshMediaFile(mockedEpisodeMediaFile);
        // verify file is downloaded
        assertTrue(tempFolder.resolve("test.mp3").toFile().exists());

    }

    @Test
    public void testDownloadWithEpisodeFileReturnNullDurationShouldSetErrorStatus() throws Exception {

        // given
        Mockito.reset(mediaFileService);
        when(podcastPersistenceService.prepareDownloadEpisode(1)).thenReturn(mockedEpisode);
        when(podcastPersistenceService.isEpisodeDeleted(1)).thenReturn(false);
        when(mockedEpisode.getChannel()).thenReturn(mockedChannel);
        when(mockedEpisode.getUrl()).thenReturn("http://test.com/test.mp3");
        when(mockedEpisode.getChannel()).thenReturn(mockedChannel);
        when(mockedChannel.getMediaFile()).thenReturn(mockedChannelMediaFile);
        when(mockedChannelMediaFile.getFolder()).thenReturn(mockedMusicFolder);
        when(mockedMusicFolder.getPath()).thenReturn(tempFolder);
        when(mockedChannelMediaFile.getFullPath()).thenReturn(tempFolder);
        when(securityService.isWriteAllowed(Paths.get("test.mp3"), mockedMusicFolder)).thenReturn(true);
        when(versionService.getLocalVersion()).thenReturn(new Version("1.0.0"));
        when(mediaFileService.getMediaFile(any(Path.class), eq(mockedMusicFolder))).thenReturn(mockedEpisodeMediaFile);
        when(mockedEpisodeMediaFile.getDuration()).thenReturn(null);

        // when
        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class,
                Mockito.CALLS_REAL_METHODS)) {
            mockedHttpClients.when(() -> HttpClients.createDefault()).thenReturn(mockedHttpClient);
            when(mockedHttpClient.execute(any())).thenReturn(mockedHttpResponse);
            StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "test");
            when(mockedHttpResponse.getStatusLine()).thenReturn(statusLine);
            Resource resource = resourceLoader.getResource("classpath:/MEDIAS/piano.mp3");
            HttpEntity entity = new ByteArrayEntity(resource.getContentAsByteArray());
            when(mockedHttpResponse.getEntity()).thenReturn(entity);
            podcastDownloadClient.downloadEpisode(1).get();
        }

        // then
        verify(mockedEpisode).setStatus(PodcastStatus.ERROR);
        verify(mockedEpisode).setErrorMessage("Failed to get duration for mockedEpisodeMediaFile");
        verify(mockedEpisode, times(5)).setBytesDownloaded(anyLong());
        verify(mockedEpisode).setMediaFile(mockedEpisodeMediaFile);
        verify(podcastPersistenceService, times(5)).updateEpisode(mockedEpisode);
        verify(podcastPersistenceService, never()).deleteObsoleteEpisodes(mockedChannel);
        // verify media file is refreshed by updateTag
        verify(mediaFileService, never()).refreshMediaFile(mockedEpisodeMediaFile);
        // verify file is downloaded
        assertTrue(tempFolder.resolve("test.mp3").toFile().exists());

    }

    @Test
    public void testDownloadWithEpisodeDeletedByUserShouldAbortDownload() throws Exception {

        // given
        Mockito.reset(mediaFileService);
        when(podcastPersistenceService.prepareDownloadEpisode(1)).thenReturn(mockedEpisode);
        when(podcastPersistenceService.isEpisodeDeleted(1))
                .thenReturn(false)
                .thenReturn(true);
        when(mockedEpisode.getChannel()).thenReturn(mockedChannel);
        when(mockedEpisode.getUrl()).thenReturn("http://test.com/test.mp3");
        when(mockedEpisode.getChannel()).thenReturn(mockedChannel);
        when(mockedChannel.getMediaFile()).thenReturn(mockedChannelMediaFile);
        when(mockedChannelMediaFile.getFolder()).thenReturn(mockedMusicFolder);
        when(mockedMusicFolder.getPath()).thenReturn(tempFolder);
        when(mockedChannelMediaFile.getFullPath()).thenReturn(tempFolder);
        when(securityService.isWriteAllowed(Paths.get("test.mp3"), mockedMusicFolder)).thenReturn(true);
        when(versionService.getLocalVersion()).thenReturn(new Version("1.0.0"));

        // when
        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class,
                Mockito.CALLS_REAL_METHODS)) {
            mockedHttpClients.when(() -> HttpClients.createDefault()).thenReturn(mockedHttpClient);
            when(mockedHttpClient.execute(any())).thenReturn(mockedHttpResponse);
            StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), 200, "test");
            when(mockedHttpResponse.getStatusLine()).thenReturn(statusLine);
            Resource resource = resourceLoader.getResource("classpath:/MEDIAS/piano.mp3");
            HttpEntity entity = new ByteArrayEntity(resource.getContentAsByteArray());
            when(mockedHttpResponse.getEntity()).thenReturn(entity);
            podcastDownloadClient.downloadEpisode(1).get();
        }

        // then
        verify(mockedEpisode, never()).setStatus(any());
        verify(mockedEpisode).setErrorMessage(null);
        verify(mockedEpisode, times(3)).setBytesDownloaded(anyLong());
        verify(mockedEpisode, never()).setMediaFile(mockedEpisodeMediaFile);
        verify(podcastPersistenceService, times(2)).updateEpisode(mockedEpisode);
        // verify media file is refreshed by updateTag
        verifyNoInteractions(mediaFileService);
        // verify file is downloaded
        assertFalse(tempFolder.resolve("test.mp3").toFile().exists());

    }

    @Test
    void testDoDownloadEpisodeWithDeletedEpisodeShouldDoNothing() throws Exception {

        // given
        when(podcastPersistenceService.prepareDownloadEpisode(1)).thenReturn(null);

        // when
        podcastDownloadClient.downloadEpisode(1).get();

        // then
        verify(podcastEpisodeRepository, never()).save(any(PodcastEpisode.class));
    }

    @Test
    public void testDoDonwloadEpisodeWithExceptionShouldSetErrorStatus() throws Exception {

        // given
        when(podcastPersistenceService.prepareDownloadEpisode(1)).thenReturn(mockedEpisode);
        when(mockedEpisode.getChannel()).thenReturn(mockedChannel);
        when(mockedEpisode.getUrl()).thenReturn("http://test.com/test.mp3");
        when(mockedEpisode.getChannel()).thenReturn(mockedChannel);
        when(mockedChannel.getMediaFile()).thenReturn(mockedChannelMediaFile);
        when(mockedChannelMediaFile.getFolder()).thenReturn(mockedMusicFolder);
        when(mockedMusicFolder.getPath()).thenReturn(tempFolder);
        when(mockedChannelMediaFile.getFullPath()).thenReturn(tempFolder);
        when(securityService.isWriteAllowed(Paths.get("test.mp3"), mockedMusicFolder)).thenReturn(true);
        when(versionService.getLocalVersion()).thenReturn(new Version("1.0.0"));

        // when
        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class,
                Mockito.CALLS_REAL_METHODS)) {
            mockedHttpClients.when(() -> HttpClients.createDefault()).thenReturn(mockedHttpClient);
            when(mockedHttpClient.execute(any())).thenThrow(new IOException("test"));
            podcastDownloadClient.downloadEpisode(1).get();
        }

        // then
        verify(podcastPersistenceService).updateEpisode(mockedEpisode);
        verify(mockedEpisode).setStatus(PodcastStatus.ERROR);
        verify(mockedEpisode).setErrorMessage("test");
    }

    @ParameterizedTest
    @ValueSource(ints = { 400, 401, 403, 404, 500, 503 })
    public void testDoDonwloadEpisodeWithNonOKStatusShouldSetErrorStatus(int status) throws Exception {

        // given
        when(podcastPersistenceService.prepareDownloadEpisode(1)).thenReturn(mockedEpisode);
        when(mockedEpisode.getChannel()).thenReturn(mockedChannel);
        when(mockedEpisode.getUrl()).thenReturn("http://test.com/test.mp3");
        when(mockedEpisode.getChannel()).thenReturn(mockedChannel);
        when(mockedChannel.getMediaFile()).thenReturn(mockedChannelMediaFile);
        when(mockedChannelMediaFile.getFolder()).thenReturn(mockedMusicFolder);
        when(mockedMusicFolder.getPath()).thenReturn(tempFolder);
        when(mockedChannelMediaFile.getFullPath()).thenReturn(tempFolder);
        when(securityService.isWriteAllowed(Paths.get("test.mp3"), mockedMusicFolder)).thenReturn(true);
        when(versionService.getLocalVersion()).thenReturn(new Version("1.0.0"));

        // when
        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class,
                Mockito.CALLS_REAL_METHODS)) {
            mockedHttpClients.when(() -> HttpClients.createDefault()).thenReturn(mockedHttpClient);
            when(mockedHttpClient.execute(any())).thenReturn(mockedHttpResponse);
            StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), status, "test");
            when(mockedHttpResponse.getStatusLine()).thenReturn(statusLine);
            HttpEntity entity = new ByteArrayEntity("Error".getBytes());
            when(mockedHttpResponse.getEntity()).thenReturn(entity);
            podcastDownloadClient.downloadEpisode(1).get();
        }

        // then
        verify(podcastPersistenceService).updateEpisode(mockedEpisode);
        verify(mockedEpisode).setStatus(PodcastStatus.ERROR);
        verify(mockedEpisode).setErrorMessage(
                String.format("Failed to download Podcast from http://test.com/test.mp3. Status code: %d", status));
    }

}
