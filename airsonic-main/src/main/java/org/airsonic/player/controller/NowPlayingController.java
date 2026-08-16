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
package org.airsonic.player.controller;

import org.airsonic.player.domain.AvatarScheme;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.PlayStatus;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.PersonalSettingsService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.StatusService;
import org.airsonic.player.view.NowPlayingView;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Controller for showing what's currently playing.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({"/nowPlaying", "/nowPlaying.view"})
public class NowPlayingController {

    @Autowired
    private PlayerService playerService;
    @Autowired
    private StatusService statusService;
    @Autowired
    private MediaFileService mediaFileService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private PersonalSettingsService personalSettingsService;

    @GetMapping
    protected ModelAndView get(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String username = securityService.getCurrentUsername(request);

        Player player = playerService.getPlayer(request, response, username);
        TransferStatus status = statusService.getStreamStatusesForPlayer(player).stream().findFirst()
                .orElseGet(() -> statusService.getInactiveStreamStatusForPlayer(player));

        String url = Optional.ofNullable(status)
                .map(s -> {
                    if (s.getMediaFile() != null) {
                        return s.getMediaFile();
                    }
                    return mediaFileService.getMediaFile(s.getExternalFile());
                })
                .map(mediaFileService::getParentOf)
                .filter(dir -> Objects.nonNull(dir) && !mediaFileService.isRoot(dir))
                .map(dir -> "main?id=" + dir.getId())
                .orElse("home");

        return new ModelAndView(new RedirectView(url));
    }

    @GetMapping("/status")
    public ModelAndView getStatus(@RequestParam(value = "id", required = true) UUID transferId) {
        NowPlayingView info = Stream.concat(
                statusService.getActivePlayStatuses().stream(),
                statusService.getInactivePlayStatuses().stream())
                .filter(status -> status.getTransferId().equals(transferId))
                .findFirst()
                .map(s -> createForBroadcast(s))
                .orElse(null);

        return new ModelAndView("nowplaying/_status::status", "np", info);

    }
    /**
     * Creates a NowPlayingView object for the given play status.
     *
     * @param status the play status
     * @return the NowPlayingView object, or null if the status is too old or the user has disabled now playing
     */
    private NowPlayingView createForBroadcast(PlayStatus status) {

        Player player = status.getPlayer();
        MediaFile mediaFile = status.getMediaFile();
        String username = player.getUsername();
        long minutesAgo = status.getMinutesAgo();
        UserSettings userSettings = personalSettingsService.getUserSettings(username);

        boolean showLyrics = !mediaFile.isVideo();
        boolean hasCustomAvatar = userSettings.getAvatarScheme() == AvatarScheme.CUSTOM
                && personalSettingsService.getCustomAvatar(username) != null;

        // avatarUsername stays as the real Airsonic username for avatar URL lookups
        String avatarUsername = username;

        // generate display name (may include player name for external players)
        if (StringUtils.isNotBlank(player.getName())) {
            username += "@" + player.getName();
        }

        // generate abbreviated artist/title
        String artist = StringUtils.abbreviate(mediaFile.getArtist(), 25);
        String title = StringUtils.abbreviate(mediaFile.getTitle(), 25);

        return new NowPlayingView(
            status.getTransferId(),
            player.getId(),
            mediaFile.getId(),
            username,
            avatarUsername,
            artist,
            title,
            minutesAgo,
            showLyrics,
            userSettings.getAvatarScheme().name(),
            userSettings.getSystemAvatarId(),
            hasCustomAvatar
        );

    }
}
