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

 Copyright 2025 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.controller;

import org.airsonic.player.domain.*;
import org.airsonic.player.service.*;
import org.airsonic.player.service.search.IndexManager;
import org.airsonic.player.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.*;

/**
 * Controller for the left index frame.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({"/left", "/left.view"})
public class LeftController {


    @Autowired
    private MediaScannerService mediaScannerService;
    @Autowired
    private IndexManager indexManager;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private MusicIndexService musicIndexService;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private MediaFolderService mediaFolderService;
    @Autowired
    private PersonalSettingsService personalSettingsService;
    @Autowired
    private InternetRadioService internetRadioService;


    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {
        boolean musicFolderChanged = saveSelectedMusicFolder(request);
        Map<String, Object> map = new HashMap<>();

        MediaLibraryStatistics statistics = Optional.ofNullable(indexManager.getStatistics()).orElse(new MediaLibraryStatistics());
        Locale locale = RequestContextUtils.getLocale(request);

        boolean refresh = ServletRequestUtils.getBooleanParameter(request, "refresh", false);
        if (refresh) {
            mediaFolderService.clearMusicFolderCache();
        }

        String username = securityService.getCurrentUsername(request);
        UserSettings userSettings = personalSettingsService.getUserSettings(username);
        List<MusicFolder> allMusicFolders = mediaFolderService.getMusicFoldersForUser(username);
        List<MusicFolder> musicFoldersToUse = mediaFolderService.getMusicFoldersForUser(username, userSettings.getSelectedMusicFolderId());
        MusicFolder selectedMusicFolder = musicFoldersToUse.stream()
                .filter(f -> f.getId().equals(userSettings.getSelectedMusicFolderId()))
                .findAny().orElse(null);
        MusicFolderContent musicFolderContent = musicIndexService.getMusicFolderContent(musicFoldersToUse, refresh);

        map.put("player", playerService.getPlayer(request, response, username));
        map.put("scanning", mediaScannerService.isScanning());
        map.put("musicFolders", allMusicFolders);
        map.put("selectedMusicFolder", selectedMusicFolder);
        map.put("radios", internetRadioService.getEnabledInternetRadios());
        map.put("shortcuts", musicIndexService.getShortcuts(musicFoldersToUse));
        map.put("partyMode", userSettings.getPartyModeEnabled());
        map.put("organizeByFolderStructure", settingsService.isOrganizeByFolderStructure());
        map.put("musicFolderChanged", musicFolderChanged);

        if (statistics != null) {
            map.put("statistics", statistics);
            long bytes = statistics.getTotalLengthInBytes();
            long hours = (long) (statistics.getTotalDurationInSeconds() / 3600L);
            map.put("hours", hours);
            map.put("bytes", StringUtil.formatBytes(bytes, locale));
        }

        map.put("indexedArtists", musicFolderContent.getIndexedArtists());
        map.put("singleSongs", musicFolderContent.getSingleSongs());
        map.put("indexes", musicFolderContent.getIndexedArtists().keySet());
        map.put("user", securityService.getCurrentUser(request));

        return new ModelAndView("left","model",map);
    }

    private boolean saveSelectedMusicFolder(HttpServletRequest request) throws Exception {
        Integer musicFolderId = ServletRequestUtils.getIntParameter(request, "musicFolderId");
        if (musicFolderId == null) {
            return false;
        }
        // Note: UserSettings.setChanged() is intentionally not called. This would break browser caching
        // of the left frame.
        personalSettingsService.updateSelectedMusicFolderId(securityService.getCurrentUsername(request), musicFolderId);
        return true;
    }
}
