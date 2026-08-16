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
package org.airsonic.player.view;

import java.util.UUID;

/**
 * Details about what a user is currently listening to.
 *
 * @author Y.Tory
 */
public class NowPlayingView {

    private final UUID transferId;
    private final Integer playerId;
    private final Integer mediaFileId;

    private final String username;
    private final String avatarUsername;
    private final String artist;
    private final String title;
    private final long minutesAgo;

    private final boolean showLyrics;
    private final String avatarScheme;
    private final Integer systemAvatarId;
    private final boolean hasCustomAvatar;

    public NowPlayingView(UUID transferId, Integer playerId, Integer mediaFileId,
            String username, String avatarUsername, String artist, String title, long minutesAgo,
            boolean showLyrics, String avatarScheme, Integer systemAvatarId,
            boolean hasCustomAvatar) {
        this.transferId = transferId;
        this.playerId = playerId;
        this.mediaFileId = mediaFileId;
        this.username = username;
        this.avatarUsername = avatarUsername;
        this.artist = artist;
        this.title = title;
        this.minutesAgo = minutesAgo;
        this.showLyrics = showLyrics;
        this.avatarScheme = avatarScheme;
        this.systemAvatarId = systemAvatarId;
        this.hasCustomAvatar = hasCustomAvatar;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public Integer getPlayerId() {
        return playerId;
    }

    public Integer getMediaFileId() {
        return mediaFileId;
    }

    public String getUsername() {
        return username;
    }

    public String getAvatarUsername() {
        return avatarUsername;
    }

    public String getArtist() {
        return artist;
    }

    public String getTitle() {
        return title;
    }

    public long getMinutesAgo() {
        return minutesAgo;
    }

    public boolean isShowLyrics() {
        return showLyrics;
    }

    public String getAvatarScheme() {
        return avatarScheme;
    }

    public Integer getSystemAvatarId() {
        return systemAvatarId;
    }

    public boolean hasCustomAvatar() {
        return hasCustomAvatar;
    }
}
