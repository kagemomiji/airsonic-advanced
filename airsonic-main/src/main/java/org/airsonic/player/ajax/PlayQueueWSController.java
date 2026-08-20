package org.airsonic.player.ajax;

import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.PlayQueueService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.spring.WebsocketConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.List;

@Controller
@MessageMapping("/playqueues/{playerId}")
public class PlayQueueWSController {
    @Autowired
    private PlayerService playerService;
    @Autowired
    private PlayQueueService playQueueService;
    @Autowired
    private SecurityService securityService;

    @SubscribeMapping("/get")
    public PlayQueueInfo getPlayQueue(@DestinationVariable("playerId") Integer playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        String baseUrl = (String)headers.getSessionAttributes().get(WebsocketConfiguration.BASE_URL);
        return playQueueService.getPlayQueueInfo(player, baseUrl);
    }

    @MessageMapping("/start")
    public void start(@DestinationVariable("playerId") Integer playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.start(player);
    }

    @MessageMapping("/stop")
    public void stop(@DestinationVariable("playerId") Integer playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.stop(player);
    }

    @MessageMapping("/endMedia")
    public void endMedia(@DestinationVariable("playerId") Integer playerId, Integer mediaFileId, SimpMessageHeaderAccessor headers) throws Exception {
        if (mediaFileId == null) {
            return;
        }
        Player player = getPlayer(playerId, headers);
        playQueueService.endMedia(player, mediaFileId);
    }

    @MessageMapping("/toggleStartStop")
    public void toggleStartStop(@DestinationVariable("playerId") Integer playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.toggleStartStop(player);
    }

    @MessageMapping("/skip")
    public void skip(@DestinationVariable("playerId") Integer playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.skip(player, req.getIndex(), req.getOffset());
    }

    @MessageMapping("/reloadsearch")
    public void reloadSearchCriteria(@DestinationVariable("playerId") Integer playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.reloadSearchCriteria(player, headers.getSessionId());
    }

    @MessageMapping("/save")
    @SendToUser
    public int savePlayQueue(@DestinationVariable("playerId") Integer playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        return playQueueService.savePlayQueue(player, req.getIndex(), req.getOffset());
    }

    @MessageMapping("/play/saved")
    public void loadSavedPlayQueue(@DestinationVariable("playerId") Integer playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.loadSavedPlayQueue(player, headers.getSessionId());
    }

    @MessageMapping("/play/mediafile")
    public void playMediaFile(@DestinationVariable("playerId") Integer playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.playMediaFile(player, req.getId(), req.getStartPositionMillis(), headers.getSessionId());
    }

    @MessageMapping("/play/radio")
    public void playInternetRadio(@DestinationVariable("playerId") Integer playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.playInternetRadio(player, req.getId(), req.getIndex(), headers.getSessionId());
    }

    @MessageMapping("/play/playlist")
    public void playPlaylist(@DestinationVariable("playerId") Integer playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.playPlaylist(player, req.getId(), req.getIndex(), headers.getSessionId());
    }

    @MessageMapping("/play/topsongs")
    public void playTopSong(@DestinationVariable("playerId") Integer playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.playTopSong(player, req.getId(), req.getIndex(), headers.getSessionId());
    }

    @MessageMapping("/play/podcastchannel")
    public void playPodcastChannel(@DestinationVariable("playerId") Integer playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.playPodcastChannel(player, req.getId(), headers.getSessionId());
    }

    @MessageMapping("/play/podcastepisode")
    public void playPodcastEpisode(@DestinationVariable("playerId") Integer playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.playPodcastEpisode(player, req.getId(), headers.getSessionId());
    }

    @MessageMapping("/play/starred")
    public void playStarred(@DestinationVariable("playerId") Integer playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.playStarred(player, headers.getSessionId());
    }

    @MessageMapping("/play/shuffle")
    public void playShuffle(@DestinationVariable("playerId") Integer playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.playShuffle(player, req.getAlbumListType(), (int) req.getOffset(), req.getCount(),
                req.getGenre(), req.getDecade(), headers.getSessionId());
    }

    @MessageMapping("/play/random")
    public void playRandom(@DestinationVariable("playerId") Integer playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.playRandom(player, req.getId(), req.getCount(), headers.getSessionId());
    }

    @MessageMapping("/play/similar")
    public void playSimilar(@DestinationVariable("playerId") Integer playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.playSimilar(player, req.getId(), req.getCount(), headers.getSessionId());
    }

    @MessageMapping("/add")
    public void add(@DestinationVariable("playerId") Integer playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.add(player, req.getIds(), req.getIndex(), player.isWeb(), true);
    }

    @MessageMapping("/add/playlist")
    public void addPlaylist(@DestinationVariable("playerId") Integer playerId, PlayQueueRequest req, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.addPlaylist(player, req.getId(), player.isWeb());
    }

    @MessageMapping("/clear")
    public void clear(@DestinationVariable("playerId") Integer playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.clear(player);
    }

    @MessageMapping("/shuffle")
    public void shuffle(@DestinationVariable("playerId") Integer playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.shuffle(player);
    }

    @MessageMapping("/remove")
    public void remove(@DestinationVariable("playerId") Integer playerId, List<Integer> indices, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.remove(player, indices);
    }

    @MessageMapping("/rearrange")
    public void rearrange(@DestinationVariable("playerId") Integer playerId, List<Integer> indices, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.rearrange(player, indices);
    }

    @MessageMapping("/up")
    public void up(@DestinationVariable("playerId") Integer playerId, int index, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.up(player, index);
    }

    @MessageMapping("/down")
    public void down(@DestinationVariable("playerId") Integer playerId, int index, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.down(player, index);
    }

    @MessageMapping("/toggleRepeat")
    public void toggleRepeat(@DestinationVariable("playerId") Integer playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.toggleRepeat(player);
    }

    @MessageMapping("/undo")
    public void undo(@DestinationVariable("playerId") Integer playerId, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.undo(player);
    }

    @MessageMapping("/sort")
    public void sort(@DestinationVariable("playerId") Integer playerId, PlayQueue.SortOrder order, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.sort(player, order);
    }

    //
    // Methods dedicated to jukebox
    //
    @MessageMapping("/jukebox/gain")
    public void setJukeboxGain(@DestinationVariable("playerId") Integer playerId, float gain, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.setJukeboxGain(player, gain);
    }

    @MessageMapping("/jukebox/position")
    public void setJukeboxPosition(@DestinationVariable("playerId") Integer playerId, int positionInSeconds, SimpMessageHeaderAccessor headers) throws Exception {
        Player player = getPlayer(playerId, headers);
        playQueueService.setJukeboxPosition(player, positionInSeconds);
    }

    //
    // End : Methods dedicated to jukebox
    //

    private Player getPlayer(int playerId, SimpMessageHeaderAccessor headers) throws Exception {
        HttpServletRequest request = (HttpServletRequest) headers.getSessionAttributes().get(WebsocketConfiguration.UNDERLYING_SERVLET_REQUEST);
        String userAgent = (String) headers.getSessionAttributes().get(WebsocketConfiguration.USER_AGENT);
        String username = securityService.getCurrentUsername(request);
        HttpSession session = (HttpSession) headers.getSessionAttributes().get(WebsocketConfiguration.UNDERLYING_HTTP_SESSION);
        Player player = playerService.getPlayer(request, null, playerId, username, userAgent, true, false, true);
        if (player != null) {
            session.setAttribute("player", player.getId());
        }
        return player;
    }

    public static class PlayQueueRequest {
        private int id;
        private Integer index;
        private long offset;
        private int count;
        private long startPositionMillis;

        // used for playShuffle()
        private String albumListType;
        private String genre;
        private String decade;

        // used for add()
        private List<Integer> ids;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
        }
        public long getOffset() {
            return offset;
        }
        public void setOffset(long offset) {
            this.offset = offset;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String getAlbumListType() {
            return albumListType;
        }

        public void setAlbumListType(String albumListType) {
            this.albumListType = albumListType;
        }

        public String getGenre() {
            return genre;
        }

        public void setGenre(String genre) {
            this.genre = genre;
        }

        public String getDecade() {
            return decade;
        }

        public void setDecade(String decade) {
            this.decade = decade;
        }

        public List<Integer> getIds() {
            return ids;
        }

        public void setIds(List<Integer> ids) {
            this.ids = ids;
        }

        public long getStartPositionMillis() {
            return startPositionMillis;
        }

        public void setStartPositionMillis(long startPositionMillis) {
            this.startPositionMillis = startPositionMillis;
        }
    }
}
