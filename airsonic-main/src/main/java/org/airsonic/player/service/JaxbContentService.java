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
 */
package org.airsonic.player.service;

import org.airsonic.player.controller.CoverArtController;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.util.StringUtil;
import org.springframework.stereotype.Service;
import org.subsonic.restapi.AlbumID3;
import org.subsonic.restapi.ArtistID3;
import org.subsonic.restapi.Child;

import java.time.Instant;
import java.util.Objects;

@Service
public class JaxbContentService {

    private final JAXBWriter jaxbWriter = new JAXBWriter();
    private final ArtistService artistService;
    private final CoverArtService coverArtService;
    private final PlaylistService playlistService;
    private final AlbumService albumService;
    private final MediaFileService mediaFileService;
    private final TranscodingService transcodingService;
    private final RatingService ratingService;

    JaxbContentService(
            ArtistService artistService,
            CoverArtService coverArtService,
            PlaylistService playlistService,
            AlbumService albumService,
            MediaFileService mediaFileService,
            TranscodingService transcodingService,
            RatingService ratingService) {
        this.artistService = artistService;
        this.coverArtService = coverArtService;
        this.playlistService = playlistService;
        this.albumService = albumService;
        this.mediaFileService = mediaFileService;
        this.transcodingService = transcodingService;
        this.ratingService = ratingService;
    }

    public <T extends ArtistID3> T createJaxbArtist(T jaxbArtist, org.airsonic.player.domain.Artist artist, String username) {
        jaxbArtist.setId(String.valueOf(artist.getId()));
        jaxbArtist.setName(artist.getName());
        jaxbArtist.setStarred(jaxbWriter.convertDate(artistService.getStarredDate(artist.getId(), username)));
        jaxbArtist.setAlbumCount(artist.getAlbumCount());
        if (!CoverArt.NULL_ART.equals(coverArtService.getArtistArt(artist.getId()))) {
            jaxbArtist.setCoverArt(CoverArtController.ARTIST_COVERART_PREFIX + artist.getId());
        }
        return jaxbArtist;
    }

    public org.subsonic.restapi.Artist createJaxbArtist(MediaFile artist, String username) {
        org.subsonic.restapi.Artist result = new org.subsonic.restapi.Artist();
        result.setId(String.valueOf(artist.getId()));
        result.setName(artist.getTitle() != null ? artist.getTitle() : artist.getArtist());
        Instant starred = mediaFileService.getMediaFileStarredDate(artist, username);
        result.setStarred(jaxbWriter.convertDate(starred));
        // TODO: add rating. https://opensubsonic.netlify.app/docs/responses/artist/
        return result;
    }

    public <T extends AlbumID3> T createJaxbAlbum(T jaxbAlbum, Album album, String username) {
        jaxbAlbum.setId(String.valueOf(album.getId()));
        jaxbAlbum.setName(album.getName());
        if (album.getArtist() != null) {
            jaxbAlbum.setArtist(album.getArtist());
            org.airsonic.player.domain.Artist artist = artistService.getArtist(album.getArtist());
            if (artist != null) {
                jaxbAlbum.setArtistId(String.valueOf(artist.getId()));
            }
        }
        if (!CoverArt.NULL_ART.equals(coverArtService.getAlbumArt(album.getId()))) {
            jaxbAlbum.setCoverArt(CoverArtController.ALBUM_COVERART_PREFIX + album.getId());
        }
        jaxbAlbum.setSongCount(album.getSongCount());
        jaxbAlbum.setDuration((int) Math.round(album.getDuration()));
        jaxbAlbum.setCreated(jaxbWriter.convertDate(album.getCreated()));
        jaxbAlbum.setStarred(jaxbWriter.convertDate(albumService.getAlbumStarredDate(album.getId(), username)));
        jaxbAlbum.setYear(album.getYear());
        jaxbAlbum.setGenre(album.getGenre());
        return jaxbAlbum;
    }

    public <T extends org.subsonic.restapi.Playlist> T createJaxbPlaylist(T jaxbPlaylist, Playlist playlist) {
        jaxbPlaylist.setId(String.valueOf(playlist.getId()));
        jaxbPlaylist.setName(playlist.getName());
        jaxbPlaylist.setComment(playlist.getComment());
        jaxbPlaylist.setOwner(playlist.getUsername());
        jaxbPlaylist.setPublic(playlist.getShared());
        jaxbPlaylist.setSongCount(playlist.getFileCount());
        jaxbPlaylist.setDuration((int) Math.round(playlist.getDuration()));
        jaxbPlaylist.setCreated(jaxbWriter.convertDate(playlist.getCreated()));
        jaxbPlaylist.setChanged(jaxbWriter.convertDate(playlist.getChanged()));
        jaxbPlaylist.setCoverArt(CoverArtController.PLAYLIST_COVERART_PREFIX + playlist.getId());

        for (String username : playlistService.getPlaylistUsers(playlist.getId())) {
            jaxbPlaylist.getAllowedUser().add(username);
        }
        return jaxbPlaylist;
    }

    public Child createJaxbChild(Player player, MediaFile mediaFile, String username) {
        return createJaxbChild(new Child(), player, mediaFile, username);
    }

    public <T extends Child> T createJaxbChild(T child, Player player, MediaFile mediaFile, String username) {
        MediaFile parent = mediaFileService.getParentOf(mediaFile);
        child.setId(String.valueOf(mediaFile.getId()));
        try {
            if (Objects.nonNull(parent) && !mediaFileService.isRoot(parent)) {
                child.setParent(String.valueOf(parent.getId()));
            }
        } catch (SecurityException x) {
            // Ignored.
        }
        child.setTitle(mediaFile.getName());
        child.setAlbum(mediaFile.getAlbumName());
        child.setArtist(mediaFile.getArtist());
        child.setIsDir(mediaFile.isDirectory());
        child.setCoverArt(findCoverArt(mediaFile, parent));
        child.setYear(mediaFile.getYear());
        child.setGenre(mediaFile.getGenre());
        child.setCreated(jaxbWriter.convertDate(mediaFile.getCreated()));
        child.setStarred(jaxbWriter.convertDate(mediaFileService.getMediaFileStarredDate(mediaFile, username)));
        child.setUserRating(ratingService.getRatingForUser(username, mediaFile));
        child.setAverageRating(ratingService.getAverageRating(mediaFile));
        child.setPlayCount((long) mediaFile.getPlayCount());

        if (mediaFile.isFile()) {
            Double mediaFileDuration = mediaFile.getDuration();
            child.setDuration((int) Math.round(mediaFileDuration == null ? 0 : mediaFileDuration));
            child.setBitRate(mediaFile.getBitRate());
            child.setTrack(mediaFile.getTrackNumber());
            child.setDiscNumber(mediaFile.getDiscNumber());
            child.setSize(mediaFile.getFileSize());
            String suffix = mediaFile.getFormat();
            child.setSuffix(suffix);
            child.setContentType(StringUtil.getMimeType(suffix));
            child.setIsVideo(mediaFile.isVideo());
            child.setPath(mediaFile.getPath());

            Album album = albumService.getAlbumByMediaFile(mediaFile);

            if (album != null) {
                child.setAlbumId(String.valueOf(album.getId()));
            }
            org.airsonic.player.domain.Artist artist = artistService.getArtist(mediaFile.getArtist());
            if (artist != null) {
                child.setArtistId(String.valueOf(artist.getId()));
            }
            switch (mediaFile.getMediaType()) {
                case MUSIC:
                    child.setType(org.subsonic.restapi.MediaType.MUSIC);
                    break;
                case PODCAST:
                    child.setType(org.subsonic.restapi.MediaType.PODCAST);
                    break;
                case AUDIOBOOK:
                    child.setType(org.subsonic.restapi.MediaType.AUDIOBOOK);
                    break;
                case VIDEO:
                    child.setType(org.subsonic.restapi.MediaType.VIDEO);
                    child.setOriginalWidth(mediaFile.getWidth());
                    child.setOriginalHeight(mediaFile.getHeight());
                    break;
                default:
                    break;
            }

            if (transcodingService.isTranscodingRequired(mediaFile, player)) {
                String transcodedSuffix = transcodingService.getSuffix(player, mediaFile, null);
                child.setTranscodedSuffix(transcodedSuffix);
                child.setTranscodedContentType(StringUtil.getMimeType(transcodedSuffix));
            }
        }
        return child;
    }

    private String findCoverArt(MediaFile mediaFile, MediaFile parent) {
        MediaFile dir = mediaFile.isDirectory() ? mediaFile : parent;
        if (dir != null && !CoverArt.NULL_ART.equals(coverArtService.getMediaFileArt(dir.getId()))) {
            return String.valueOf(dir.getId());
        }
        return null;
    }
}
