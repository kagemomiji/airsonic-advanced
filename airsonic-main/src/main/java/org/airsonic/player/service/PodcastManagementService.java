package org.airsonic.player.service;

import org.airsonic.player.command.PodcastSettingsCommand.PodcastRule;
import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastChannelRule;
import org.airsonic.player.domain.PodcastStatus;
import org.airsonic.player.service.podcast.PodcastDownloadClient;
import org.airsonic.player.service.podcast.PodcastRefresher;
import org.airsonic.player.service.websocket.AsyncWebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class PodcastManagementService {

    private static final Logger LOG = LoggerFactory.getLogger(PodcastManagementService.class);

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private PodcastPersistenceService podcastPersistenceService;

    @Autowired
    private TaskSchedulingService taskService;

    @Autowired
    private PodcastRefresher podcastRefresher;

    @Autowired
    private PodcastDownloadClient podcastDownloadClient;

    @Autowired
    private AsyncWebSocketClient asyncWebSocketClient;

    private Runnable defaultTask;

    @EventListener
    public void onApplicationEvent(ApplicationReadyEvent event) {
        defaultTask = () -> {
            LOG.info("Starting scheduled default Podcast refresh.");

            List<PodcastChannel> channelsWithoutRules = podcastPersistenceService.getChannelsWithoutRule();
            refreshChannels(channelsWithoutRules, true);
            LOG.info("Completed scheduled default Podcast refresh.");
        };
        init();
    }

    private void init() {
        try {
            // Clean up partial downloads and reset status
            podcastPersistenceService.cleanDownloadingEpisodes();
            List<Integer> resetedChannelIds = podcastPersistenceService.resetChannelStatus(PodcastStatus.DOWNLOADING);
            for (Integer channelId: resetedChannelIds) {
                asyncWebSocketClient.send("/topic/podcasts/updated", channelId);
            }
            schedule();
        } catch (Throwable x) {
            LOG.error("Failed to initialize PodcastService", x);
        }
    }

    public synchronized void schedule() {
        // schedule for podcasts with rules
        podcastPersistenceService.getAllChannelRules().forEach(this::schedule);

        // default refresh for rest of the podcasts
        scheduleDefault();
    }

    private synchronized void schedule(PodcastChannelRule r) {
        int hoursBetween = r.getCheckInterval();

        if (hoursBetween == -1) {
            LOG.info("Automatic Podcast update disabled for podcast id {}", r.getId());
            unschedule(r.getId());
            return;
        }

        long initialDelayMillis = 5L * 60L * 1000L;
        Instant firstTime = Instant.now().plusMillis(initialDelayMillis);

        Runnable task = () -> {
            LOG.info("Starting scheduled Podcast refresh for podcast id {}.", r.getId());
            refreshChannel(r.getId(), true);
            LOG.info("Completed scheduled Podcast refresh for podcast id {}.", r.getId());
        };

        taskService.scheduleAtFixedRate("podcast-channel-refresh-" + r.getId(), task, firstTime, Duration.ofHours(hoursBetween), true);

        LOG.info("Automatic Podcast update for podcast id {} scheduled to run every {} hour(s), starting at {}", r.getId(), hoursBetween, firstTime);
    }

    public synchronized void scheduleDefault() {
        int hoursBetween = settingsService.getPodcastUpdateInterval();

        if (hoursBetween == -1) {
            LOG.info("Automatic default Podcast update disabled for podcasts");
            unschedule(-1);
            return;
        }

        long initialDelayMillis = 5L * 60L * 1000L;
        Instant firstTime = Instant.now().plusMillis(initialDelayMillis);

        taskService.scheduleAtFixedRate("podcast-channel-refresh--1", defaultTask, firstTime, Duration.ofHours(hoursBetween), true);

        LOG.info("Automatic default Podcast update scheduled to run every {} hour(s), starting at {}", hoursBetween, firstTime);
    }

    private void unschedule(Integer id) {
        taskService.unscheduleTask("podcast-channel-refresh-" + id);
    }


    public void refreshChannel(int channelId, boolean downloadEpisodes) {
        refreshChannels(Arrays.asList(podcastPersistenceService.getChannel(channelId)), downloadEpisodes);
    }

    public void refreshAllChannels(boolean downloadEpisodes) {
        refreshChannels(podcastPersistenceService.getAllChannels(), downloadEpisodes);
    }

    public void refreshChannelIds(final List<Integer> channelIds, final boolean downloadEpisodes) {
        List<PodcastChannel> channels = channelIds.stream().map(channelId -> podcastPersistenceService.getChannel(channelId))
            .filter(channel -> channel != null)
            .collect(Collectors.toList());
        refreshChannels(channels, downloadEpisodes);
    }

    /**
     * Create a channel from the given url
     *
     * @param url the url to create the channel from
     * @return the created channel
     */
    public PodcastChannel createChannel(String url) {
        PodcastChannel channel = podcastPersistenceService.createChannel(url);
        if (channel != null) {
            final int channelId = channel.getId();
            podcastRefresher.refresh(channelId, true).whenComplete((result, x) -> {
                if (x != null || Boolean.FALSE.equals(result)) {
                    // Initial refresh failed: feed URL is invalid or unreachable.
                    // Auto-delete so the user does not end up with a broken null-title channel.
                    LOG.warn("Removing podcast channel {} because initial refresh failed", channelId);
                    deleteChannel(channelId);
                    return;
                }
                List<CompletableFuture<Void>> episodeFutures = podcastPersistenceService.getEpisodes(channelId)
                    .stream()
                    .filter(episode -> episode.getStatus() == PodcastStatus.NEW && episode.getUrl() != null)
                    .map(ep -> podcastDownloadClient.downloadEpisode(ep.getId()))
                    .collect(Collectors.toList());
                CompletableFuture.allOf(episodeFutures.toArray(new CompletableFuture[0])).join();
                podcastPersistenceService.setChannelCompleted(channel);
                asyncWebSocketClient.send("/topic/podcasts/updated", channelId);
            });
        }
        return channel;
    }

    /**
     * delete a channel.
     *
     * @param channelId the channel id
     */
    public void deleteChannel(int channelId) {
        if (podcastPersistenceService.deleteChannel(channelId)) {
            asyncWebSocketClient.send("/topic/podcasts/deleted", channelId);
        }
    }

    /**
     * Refresh the given channels
     *
     * @param channels the channels to refresh (must be persisted)
     * @param downloadEpisodes if true, download episodes
     */
    public void refreshChannels(final List<PodcastChannel> channels, final boolean downloadEpisodes) {
        channels.stream().forEach(
            channel -> {
                podcastRefresher.refresh(channel.getId(), downloadEpisodes).whenComplete((result, x) -> {
                    if (x != null) {
                        LOG.error("Failed to refresh channel {}", channel.getId(), x);
                        podcastPersistenceService.setChannelError(channel, x.getMessage());
                        asyncWebSocketClient.send("/topic/podcasts/updated", channel.getId());
                        return;
                    }
                    if (!Boolean.TRUE.equals(result)) {
                        // refresh() already called setChannelError; do not overwrite with COMPLETED
                        return;
                    }
                    if (downloadEpisodes) {
                        List<CompletableFuture<Void>> episodeFutures = podcastPersistenceService.getEpisodes(channel.getId())
                            .stream()
                            .filter(episode -> episode.getStatus() == PodcastStatus.NEW && episode.getUrl() != null)
                            .map(ep -> podcastDownloadClient.downloadEpisode(ep.getId()))
                            .collect(Collectors.toList());
                        CompletableFuture.allOf(episodeFutures.toArray(new CompletableFuture[0])).join();
                    }
                    podcastPersistenceService.setChannelCompleted(channel);
                    asyncWebSocketClient.send("/topic/podcasts/updated", channel.getId());
                });
            }
        );
    }

    /**
     * Create or update a channel rule
     * @param comamnd the command
     * @return the created or updated rule
     */
    public PodcastChannelRule createOrUpdateChannelRuleByCommand(PodcastRule comamnd) {
        PodcastChannelRule rule = podcastPersistenceService.createOrUpdateChannelRuleByCommand(comamnd);
        if (rule != null) {
            schedule(rule);
        }
        return rule;
    }

    /**
     * Delete a channel rule
     * @param Id the rule id
     */
    public void deleteChannelRule(Integer id) {
        if (podcastPersistenceService.deleteChannelRule(id))
            unschedule(id);
    }

}
