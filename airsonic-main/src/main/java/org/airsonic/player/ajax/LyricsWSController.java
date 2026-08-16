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

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.ajax;

import org.airsonic.player.domain.Lyrics;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.LyricsService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Provides lyrics for a song, sourced from local LRC files or the database.
 * The previous implementation fetched from chartlyrics.com, which has been
 * defunct for several years. Searches now use local sources only.
 */
@Controller
@MessageMapping("/lyrics")
public class LyricsWSController {

    private static final Logger LOG = LoggerFactory.getLogger(LyricsWSController.class);

    private final LyricsService lyricsService;
    private final SecurityService securityService;
    private final MediaFileService mediaFileService;

    public LyricsWSController(
        LyricsService lyricsService,
        SecurityService securityService,
        MediaFileService mediaFileService) {
        this.lyricsService = lyricsService;
        this.securityService = securityService;
        this.mediaFileService = mediaFileService;
    }

    /**
     * Returns lyrics for the given song. Checks the local database and any
     * sidecar LRC file. If lyrics are found they are persisted (if not already)
     * and the client is asked to reload the page. If none are found the client
     * receives an empty status so it can show "no lyrics found" instead of the
     * misleading "try again later" message that was produced by the defunct
     * chartlyrics.com integration.
     */
    @MessageMapping("/get")
    @SendToUser(broadcast = false)
    public LyricsStatus getLyrics(Principal user, LyricsGetRequest req) {

        LyricsStatus status = new LyricsStatus();

        if (req.getId() == null) {
            return status;
        }

        MediaFile mediaFile = mediaFileService.getMediaFile(req.getId());
        if (mediaFile == null || !securityService.isFolderAccessAllowed(mediaFile, user.getName())) {
            return status;
        }

        try {
            Lyrics lyrics = lyricsService.getLyricsFromMediaFile(mediaFile);
            if (lyrics != null) {
                // Lyrics were found (possibly just loaded from a sidecar .lrc file into the
                // database for the first time). Signal the client to reload so the new data
                // is displayed via the normal server-side render path.
                status.setPersisted(true);
            }
        } catch (Exception x) {
            LOG.warn("Failed to retrieve lyrics for media file {}", req.getId(), x);
        }

        return status;
    }

    public static class LyricsGetRequest {
        private String artist;
        private String song;
        private Integer id;

        public LyricsGetRequest() {
        }

        public LyricsGetRequest(String artist, String song, Integer id) {
            this.artist = artist;
            this.song = song;
            this.id = id;
        }

        public String getArtist() {
            return artist;
        }

        public void setArtist(String artist) {
            this.artist = artist;
        }

        public String getSong() {
            return song;
        }

        public void setSong(String song) {
            this.song = song;
        }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }
    }
}
