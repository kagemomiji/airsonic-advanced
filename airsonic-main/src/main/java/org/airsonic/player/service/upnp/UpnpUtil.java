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

  Copyright 2024 (C) Y.Tory
  Copyright 2017 (C) Airsonic Authors
  Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
*/
package org.airsonic.player.service.upnp;

import org.airsonic.player.domain.CoverArtScheme;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.TranscodingService;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jupnp.support.model.Res;
import org.jupnp.util.MimeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
public class UpnpUtil {

    @Autowired
    private SecurityService securityService;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private JWTSecurityService jwtSecurityService;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private TranscodingService transcodingService;

    public Res createResourceForSong(MediaFile song) {
        // Create a guest user if necessary
        securityService.createGuestUserIfNotExists();
        Player player = playerService.getGuestPlayer(null);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString("ext/stream")
                .queryParam("id", song.getId())
                .queryParam("player", player.getId());

        if (song.isVideo()) {
            builder.queryParam("format", TranscodingService.FORMAT_RAW);
        }

        builder = jwtSecurityService.addJWTToken(User.USERNAME_ANONYMOUS, builder);

        String url = getBaseUrl() + builder.toUriString();

        String suffix = song.isVideo() ? FilenameUtils.getExtension(song.getPath()) : transcodingService.getSuffix(player, song, null);
        String mimeTypeString = StringUtil.getMimeType(suffix);
        MimeType mimeType = mimeTypeString == null ? null : MimeType.valueOf(mimeTypeString);

        Res res = new Res(mimeType, null, url);
        res.setDuration(formatDuration(song.getDuration()));
        return res;
    }

    private String formatDuration(Double seconds) {
        if (seconds == null) {
            return null;
        }
        return StringUtil.formatDuration((long) (seconds * 1000), true);
    }

    public String getBaseUrl() {
        String dlnaBaseLANURL = settingsService.getDlnaBaseLANURL();
        if (StringUtils.isBlank(dlnaBaseLANURL)) {
            throw new RuntimeException("DLNA Base LAN URL is not set correctly");
        }
        return Strings.CS.appendIfMissing(dlnaBaseLANURL, "/");
    }

    public URI getAlbumArtURI(int albumId) {
        return UriComponentsBuilder
                .fromUriString(getBaseUrl())
                .uriComponents(jwtSecurityService
                        .addJWTToken(
                                User.USERNAME_ANONYMOUS,
                                UriComponentsBuilder.fromUriString("ext/coverArt.view")
                                        .queryParam("id", albumId)
                                        .queryParam("size", CoverArtScheme.LARGE.getSize()))
                        .build())
                .build().encode().toUri();
    }

}
