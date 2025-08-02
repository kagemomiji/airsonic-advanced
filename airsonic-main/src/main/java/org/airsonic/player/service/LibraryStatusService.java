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
package org.airsonic.player.service;

import org.airsonic.player.domain.*;
import org.airsonic.player.util.FileUtil;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@Service
public class LibraryStatusService {

    // Update this time if you want to force a refresh in clients.
    private static final Instant LAST_COMPATIBILITY_TIME = Instant.parse("2012-03-06T00:00:00.00Z");

    private final MediaScannerService mediaScannerService;
    private final SettingsService settingsService;
    private final PersonalSettingsService personalSettingsService;
    private final MediaFolderService mediaFolderService;
    private final InternetRadioService internetRadioService;

    LibraryStatusService(
        MediaScannerService mediaScannerService,
        SettingsService settingsService,
        PersonalSettingsService personalSettingsService,
        MediaFolderService mediaFolderService,
        InternetRadioService internetRadioService
    ) {
        this.mediaScannerService = mediaScannerService;
        this.settingsService = settingsService;
        this.personalSettingsService = personalSettingsService;
        this.mediaFolderService = mediaFolderService;
        this.internetRadioService = internetRadioService;
    }

    /**
     * Note: This class intentionally does not implement org.springframework.web.servlet.mvc.LastModified
     * as we don't need browser-side caching of left.html.  This method is only used by RESTController.
     */
    public long getLastModified(String username, Integer musicFolderId) throws Exception {
        if (musicFolderId != null) {
            // Note: UserSettings.setChanged() is intentionally not called. This would break browser caching
            // of the left frame.
            personalSettingsService.updateSelectedMusicFolderId(username, musicFolderId);
        }

        if (mediaScannerService.isScanning()) {
            return -1L;
        }

        long lastModified = LAST_COMPATIBILITY_TIME.toEpochMilli();
        UserSettings userSettings = personalSettingsService.getUserSettings(username);

        // When was settings last changed?
        lastModified = Math.max(lastModified, settingsService.getSettingsChanged());

        // When was music folder(s) on disk last changed?

        List<MusicFolder> allMusicFolders = mediaFolderService.getMusicFoldersForUser(username);
        MusicFolder selectedMusicFolder = allMusicFolders.stream()
                .filter(f -> f.getId().equals(userSettings.getSelectedMusicFolderId()))
                .findAny().orElse(null);
        if (selectedMusicFolder != null) {
            Path file = selectedMusicFolder.getPath();
            lastModified = Math.max(lastModified, FileUtil.lastModified(file).toEpochMilli());
        } else {
            for (MusicFolder musicFolder : allMusicFolders) {
                Path file = musicFolder.getPath();
                lastModified = Math.max(lastModified, FileUtil.lastModified(file).toEpochMilli());
            }
        }

        // When was music folder table last changed?
        for (MusicFolder musicFolder : allMusicFolders) {
            lastModified = Math.max(lastModified, musicFolder.getChanged().toEpochMilli());
        }

        // When was internet radio table last changed?
        for (InternetRadio internetRadio : internetRadioService.getEnabledInternetRadios()) {
            lastModified = Math.max(lastModified, internetRadio.getChanged().toEpochMilli());
        }

        // When was user settings last changed?
        lastModified = Math.max(lastModified, userSettings.getChanged().toEpochMilli());

        return lastModified;
    }

}
