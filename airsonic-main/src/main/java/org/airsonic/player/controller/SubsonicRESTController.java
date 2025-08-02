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
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.controller;

import com.google.common.primitives.Ints;
import org.airsonic.player.ajax.LyricsInfo;
import org.airsonic.player.ajax.LyricsWSController;
import org.airsonic.player.command.UserSettingsCommand;
import org.airsonic.player.domain.*;
import org.airsonic.player.domain.Bookmark;
import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.domain.User;
import org.airsonic.player.i18n.LocaleResolver;
import org.airsonic.player.service.*;
import org.airsonic.player.service.podcast.PodcastDownloadClient;
import org.airsonic.player.service.search.IndexType;
import org.airsonic.player.util.NetworkUtil;
import org.airsonic.player.util.StringUtil;
import org.airsonic.player.util.Util;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.ServletWebRequest;
import org.subsonic.restapi.*;
import org.subsonic.restapi.PodcastStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import javax.xml.datatype.XMLGregorianCalendar;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.airsonic.player.security.RESTRequestParameterProcessingFilter.decrypt;
import static org.springframework.web.bind.ServletRequestUtils.*;

/**
 * Multi-controller used for the REST API.
 * <p/>
 * For documentation, please refer to api.jsp.
 * <p/>
 * Note: Exceptions thrown from the methods are intercepted by RESTFilter.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class SubsonicRESTController {

    private static final Logger LOG = LoggerFactory.getLogger(SubsonicRESTController.class);

    @Autowired
    private SettingsService settingsService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private MediaFileService mediaFileService;
    @Autowired
    private LastFmService lastFmService;
    @Autowired
    private MusicIndexService musicIndexService;
    @Autowired
    private DownloadController downloadController;
    @Autowired
    private CoverArtController coverArtController;
    @Autowired
    private AvatarController avatarController;
    @Autowired
    private UserSettingsController userSettingsController;
    @Autowired
    private StatusService statusService;
    @Autowired
    private StreamController streamController;
    @Autowired
    private HLSController hlsController;
    @Autowired
    private ShareService shareService;
    @Autowired
    private PlaylistService playlistService;
    @Autowired
    private LyricsWSController lyricsWSController;
    @Autowired
    private PlayQueueService playQueueService;
    @Autowired
    private JukeboxService jukeboxService;
    @Autowired
    private AudioScrobblerService audioScrobblerService;
    @Autowired
    private PodcastPersistenceService podcastPersistenceService;
    @Autowired
    private PodcastManagementService podcastManagementService;
    @Autowired
    private PodcastDownloadClient podcastDownloadClient;
    @Autowired
    private RatingService ratingService;
    @Autowired
    private SearchService searchService;
    @Autowired
    private ArtistService artistService;
    @Autowired
    private AlbumService albumService;
    @Autowired
    private BookmarkService bookmarkService;
    @Autowired
    private MediaScannerService mediaScannerService;
    @Autowired
    private MediaFolderService mediaFolderService;
    @Autowired
    private LocaleResolver localeResolver;
    @Autowired
    private UserService userService;
    @Autowired
    private PersonalSettingsService personalSettingsService;
    @Autowired
    private InternetRadioService internetRadioService;
    @Autowired
    private LibraryStatusService libraryStatusService;
    @Autowired
    private JaxbContentService jaxbContentService;


    private final JAXBWriter jaxbWriter = new JAXBWriter();

    private static final String NOT_YET_IMPLEMENTED = "Not yet implemented";
    private static final String NO_LONGER_SUPPORTED = "No longer supported";

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void handleMissingRequestParam(HttpServletRequest request,
                                          HttpServletResponse response,
                                          MissingServletRequestParameterException exception) {
        error(request, response, ErrorCode.MISSING_PARAMETER, "Required param (" + exception.getParameterName() + ") is missing");
    }

    @RequestMapping({"/ping", "/ping.view"})
    public void ping(HttpServletRequest request, HttpServletResponse response) {
        Response res = createResponse();
        jaxbWriter.writeResponse(request, response, res);
    }


    /**
     * CAUTION : this method is required by mobile applications and must not be removed.
     */
    @RequestMapping({"/getLicense", "/getLicense.view"})
    public void getLicense(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        License license = new License();

        license.setEmail("airsonic@github.com");
        license.setValid(true);
        XMLGregorianCalendar farFuture = jaxbWriter.convertDate(Instant.now().plus(ChronoUnit.YEARS.getDuration().multipliedBy(100)));
        license.setLicenseExpires(farFuture);
        license.setTrialExpires(farFuture);

        Response res = createResponse();
        res.setLicense(license);
        jaxbWriter.writeResponse(request, response, res);
    }


    @RequestMapping({"/getMusicFolders", "/getMusicFolders.view"})
    public void getMusicFolders(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);

        MusicFolders musicFolders = new MusicFolders();
        String username = securityService.getCurrentUsername(request);
        for (org.airsonic.player.domain.MusicFolder musicFolder : mediaFolderService.getMusicFoldersForUser(username)) {
            org.subsonic.restapi.MusicFolder mf = new org.subsonic.restapi.MusicFolder();
            mf.setId(musicFolder.getId());
            mf.setName(musicFolder.getName());
            musicFolders.getMusicFolder().add(mf);
        }
        Response res = createResponse();
        res.setMusicFolders(musicFolders);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getIndexes", "/getIndexes.view"})
    public void getIndexes(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Response res = createResponse();
        String username = securityService.getCurrentUser(request).getUsername();
        Integer musicFolderId = getIntParameter(request, "musicFolderId");

        long ifModifiedSince = getLongParameter(request, "ifModifiedSince", 0L);
        long lastModified = libraryStatusService.getLastModified(username, musicFolderId);

        if (lastModified <= ifModifiedSince) {
            jaxbWriter.writeResponse(request, response, res);
            return;
        }

        Indexes indexes = new Indexes();
        indexes.setLastModified(lastModified);
        indexes.setIgnoredArticles(settingsService.getIgnoredArticles());

        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);
        if (musicFolderId != null) {
            for (org.airsonic.player.domain.MusicFolder musicFolder : musicFolders) {
                if (musicFolderId.equals(musicFolder.getId())) {
                    musicFolders = Collections.singletonList(musicFolder);
                    break;
                }
            }
        }

        for (MediaFile shortcut : musicIndexService.getShortcuts(musicFolders)) {
            indexes.getShortcut().add(jaxbContentService.createJaxbArtist(shortcut, username));
        }

        MusicFolderContent musicFolderContent = musicIndexService.getMusicFolderContent(musicFolders, false);

        for (Map.Entry<MusicIndex, List<MusicIndex.SortableArtistWithMediaFiles>> entry : musicFolderContent.getIndexedArtists().entrySet()) {
            Index index = new Index();
            indexes.getIndex().add(index);
            index.setName(entry.getKey().getIndex());

            for (MusicIndex.SortableArtistWithMediaFiles artist : entry.getValue()) {
                for (MediaFile mediaFile : artist.getMediaFiles()) {
                    if (mediaFile.isDirectory()) {
                        Instant starredDate = mediaFileService.getMediaFileStarredDate(mediaFile, username);
                        org.subsonic.restapi.Artist a = new org.subsonic.restapi.Artist();
                        index.getArtist().add(a);
                        a.setId(String.valueOf(mediaFile.getId()));
                        a.setName(artist.getName());
                        a.setStarred(jaxbWriter.convertDate(starredDate));

                        if (mediaFile.isAlbum()) {
                            a.setAverageRating(ratingService.getAverageRating(mediaFile));
                            a.setUserRating(ratingService.getRatingForUser(username, mediaFile));
                        }
                    }
                }
            }
        }

        // Add children
        Player player = playerService.getPlayer(request, response, username);

        for (MediaFile singleSong : musicFolderContent.getSingleSongs()) {
            indexes.getChild().add(jaxbContentService.createJaxbChild(player, singleSong, username));
        }

        res.setIndexes(indexes);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getGenres", "/getGenres.view"})
    public void getGenres(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        org.subsonic.restapi.Genres genres = new org.subsonic.restapi.Genres();

        for (org.airsonic.player.domain.Genre genre : mediaFileService.getGenres(false)) {
            org.subsonic.restapi.Genre g = new org.subsonic.restapi.Genre();
            genres.getGenre().add(g);
            g.setContent(genre.getName());
            g.setAlbumCount(genre.getAlbumCount());
            g.setSongCount(genre.getSongCount());
        }
        Response res = createResponse();
        res.setGenres(genres);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getSongsByGenre", "/getSongsByGenre.view"})
    public void getSongsByGenre(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        Songs songs = new Songs();

        String genre = getRequiredStringParameter(request, "genre");
        int offset = getIntParameter(request, "offset", 0);
        int count = getIntParameter(request, "count", 10);
        count = Math.max(0, Math.min(count, 500));
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);

        for (MediaFile mediaFile : mediaFileService.getSongsByGenre(offset, count, genre, musicFolders)) {
            songs.getSong().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
        }
        Response res = createResponse();
        res.setSongsByGenre(songs);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getArtists", "/getArtists.view"})
    public void getArtists(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);

        ArtistsID3 result = new ArtistsID3();
        result.setIgnoredArticles(settingsService.getIgnoredArticles());
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);

        List<org.airsonic.player.domain.Artist> artists = artistService.getAlphabeticalArtists(musicFolders);
        SortedMap<MusicIndex, List<MusicIndex.SortableArtistWithArtist>> indexedArtists = musicIndexService.getIndexedArtists(artists);
        for (Map.Entry<MusicIndex, List<MusicIndex.SortableArtistWithArtist>> entry : indexedArtists.entrySet()) {
            IndexID3 index = new IndexID3();
            result.getIndex().add(index);
            index.setName(entry.getKey().getIndex());
            for (MusicIndex.SortableArtistWithArtist sortableArtist : entry.getValue()) {
                index.getArtist().add(jaxbContentService.createJaxbArtist(new ArtistID3(), sortableArtist.getArtist(), username));
            }
        }

        Response res = createResponse();
        res.setArtists(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getSimilarSongs", "/getSimilarSongs.view"})
    public void getSimilarSongs(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);

        int id = getRequiredIntParameter(request, "id");
        int count = getIntParameter(request, "count", 50);

        SimilarSongs result = new SimilarSongs();

        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            error(request, response, ErrorCode.NOT_FOUND, "Media file not found.");
            return;
        }
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);
        List<MediaFile> similarSongs = lastFmService.getSimilarSongsByMediaFile(mediaFile, count, musicFolders);
        Player player = playerService.getPlayer(request, response, username);
        for (MediaFile similarSong : similarSongs) {
            result.getSong().add(jaxbContentService.createJaxbChild(player, similarSong, username));
        }

        Response res = createResponse();
        res.setSimilarSongs(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getSimilarSongs2", "/getSimilarSongs2.view"})
    public void getSimilarSongs2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);

        int id = getRequiredIntParameter(request, "id");
        int count = getIntParameter(request, "count", 50);

        SimilarSongs2 result = new SimilarSongs2();

        org.airsonic.player.domain.Artist artist = artistService.getArtist(id);
        if (artist == null) {
            error(request, response, ErrorCode.NOT_FOUND, "Artist not found.");
            return;
        }

        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);
        List<MediaFile> similarSongs = lastFmService.getSimilarSongs(artist, count, musicFolders);
        Player player = playerService.getPlayer(request, response, username);
        for (MediaFile similarSong : similarSongs) {
            result.getSong().add(jaxbContentService.createJaxbChild(player, similarSong, username));
        }

        Response res = createResponse();
        res.setSimilarSongs2(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getTopSongs", "/getTopSongs.view"})
    public void getTopSongs(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);

        String artist = getRequiredStringParameter(request, "artist");
        int count = getIntParameter(request, "count", 50);

        TopSongs result = new TopSongs();

        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);
        List<MediaFile> topSongs = lastFmService.getTopSongs(artist, count, musicFolders);
        Player player = playerService.getPlayer(request, response, username);
        for (MediaFile topSong : topSongs) {
            result.getSong().add(jaxbContentService.createJaxbChild(player, topSong, username));
        }

        Response res = createResponse();
        res.setTopSongs(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getArtistInfo", "/getArtistInfo.view"})
    public void getArtistInfo(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);

        int id = getRequiredIntParameter(request, "id");
        int count = getIntParameter(request, "count", 20);
        boolean includeNotPresent = ServletRequestUtils.getBooleanParameter(request, "includeNotPresent", false);

        ArtistInfo result = new ArtistInfo();

        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            error(request, response, ErrorCode.NOT_FOUND, "Media file not found.");
            return;
        }
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);
        List<MediaFile> similarArtists = lastFmService.getSimilarArtistsByMediaFile(mediaFile, count, includeNotPresent, musicFolders);
        for (MediaFile similarArtist : similarArtists) {
            result.getSimilarArtist().add(jaxbContentService.createJaxbArtist(similarArtist, username));
        }
        ArtistBio artistBio = lastFmService.getArtistBioByMediaFile(mediaFile, localeResolver.resolveLocale(request));
        if (artistBio != null) {
            result.setBiography(artistBio.getBiography());
            result.setMusicBrainzId(artistBio.getMusicBrainzId());
            result.setLastFmUrl(artistBio.getLastFmUrl());
        }
        // extract base url
        String baseUrl = NetworkUtil.getBaseUrl(request);
        result.setSmallImageUrl(artistService.getArtistImageUrlByMediaFile(baseUrl, mediaFile, 34, username));
        result.setMediumImageUrl(artistService.getArtistImageUrlByMediaFile(baseUrl, mediaFile, 64, username));
        result.setLargeImageUrl(artistService.getArtistImageUrlByMediaFile(baseUrl, mediaFile, 300, username));

        Response res = createResponse();
        res.setArtistInfo(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getArtistInfo2", "/getArtistInfo2.view"})
    public void getArtistInfo2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);

        int id = getRequiredIntParameter(request, "id");
        int count = getIntParameter(request, "count", 20);
        boolean includeNotPresent = ServletRequestUtils.getBooleanParameter(request, "includeNotPresent", false);

        ArtistInfo2 result = new ArtistInfo2();

        org.airsonic.player.domain.Artist artist = artistService.getArtist(id);
        if (artist == null) {
            error(request, response, ErrorCode.NOT_FOUND, "Artist not found.");
            return;
        }

        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);
        List<org.airsonic.player.domain.Artist> similarArtists = lastFmService.getSimilarArtists(artist, count, includeNotPresent, musicFolders);
        for (org.airsonic.player.domain.Artist similarArtist : similarArtists) {
            result.getSimilarArtist().add(jaxbContentService.createJaxbArtist(new ArtistID3(), similarArtist, username));
        }
        ArtistBio artistBio = lastFmService.getArtistBio(artist, localeResolver.resolveLocale(request));
        if (artistBio != null) {
            result.setBiography(artistBio.getBiography());
            result.setMusicBrainzId(artistBio.getMusicBrainzId());
            result.setLastFmUrl(artistBio.getLastFmUrl());
        }
        String baseUrl = NetworkUtil.getBaseUrl(request);
        result.setSmallImageUrl(artistService.getArtistImageURL(baseUrl, artist.getName(), 34, username));
        result.setMediumImageUrl(artistService.getArtistImageURL(baseUrl, artist.getName(), 64, username));
        result.setLargeImageUrl(artistService.getArtistImageURL(baseUrl, artist.getName(), 300, username));
        Response res = createResponse();
        res.setArtistInfo2(result);
        jaxbWriter.writeResponse(request, response, res);
    }




    @RequestMapping({"/getArtist", "/getArtist.view"})
    public void getArtist(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        String username = securityService.getCurrentUsername(request);
        int id = getRequiredIntParameter(request, "id");
        org.airsonic.player.domain.Artist artist = artistService.getArtist(id);
        if (artist == null) {
            error(request, response, ErrorCode.NOT_FOUND, "Artist not found.");
            return;
        }

        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);
        ArtistWithAlbumsID3 result = jaxbContentService.createJaxbArtist(new ArtistWithAlbumsID3(), artist, username);
        for (Album album : albumService.getAlbumsByArtist(artist.getName(), musicFolders)) {
            result.getAlbum().add(jaxbContentService.createJaxbAlbum(new AlbumID3(), album, username));
        }

        Response res = createResponse();
        res.setArtist(result);
        jaxbWriter.writeResponse(request, response, res);
    }


    @RequestMapping({"/getAlbum", "/getAlbum.view"})
    public void getAlbum(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        int id = getRequiredIntParameter(request, "id");
        Album album = albumService.getAlbum(id);
        if (album == null) {
            error(request, response, ErrorCode.NOT_FOUND, "Album not found.");
            return;
        }

        AlbumWithSongsID3 result = jaxbContentService.createJaxbAlbum(new AlbumWithSongsID3(), album, username);
        for (MediaFile mediaFile : mediaFileService.getSongsForAlbum(album.getArtist(), album.getName())) {
            result.getSong().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
        }

        Response res = createResponse();
        res.setAlbum(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getSong", "/getSong.view"})
    public void getSong(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        int id = getRequiredIntParameter(request, "id");
        MediaFile song = mediaFileService.getMediaFile(id);
        if (song == null || song.isDirectory()) {
            error(request, response, ErrorCode.NOT_FOUND, "Song not found.");
            return;
        }
        if (!securityService.isFolderAccessAllowed(song, username)) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, "Access denied");
            return;
        }

        Response res = createResponse();
        res.setSong(jaxbContentService.createJaxbChild(player, song, username));
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getMusicDirectory", "/getMusicDirectory.view"})
    public void getMusicDirectory(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        int id = getRequiredIntParameter(request, "id");
        MediaFile dir = mediaFileService.getMediaFile(id);
        if (dir == null) {
            error(request, response, ErrorCode.NOT_FOUND, "Directory not found");
            return;
        }
        if (!securityService.isFolderAccessAllowed(dir, username)) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, "Access denied");
            return;
        }

        MediaFile parent = mediaFileService.getParentOf(dir);
        Directory directory = new Directory();
        directory.setId(String.valueOf(id));
        try {
            if (Objects.nonNull(parent) && !mediaFileService.isRoot(parent)) {
                directory.setParent(String.valueOf(parent.getId()));
            }
        } catch (SecurityException x) {
            // Ignored.
        }
        directory.setName(dir.getName());
        directory.setStarred(jaxbWriter.convertDate(mediaFileService.getMediaFileStarredDate(dir, username)));
        directory.setPlayCount((long) dir.getPlayCount());

        if (dir.isAlbum()) {
            directory.setAverageRating(ratingService.getAverageRating(dir));
            directory.setUserRating(ratingService.getRatingForUser(username, dir));
        }

        for (MediaFile child : mediaFileService.getVisibleChildrenOf(dir, true, true)) {
            directory.getChild().add(jaxbContentService.createJaxbChild(player, child, username));
        }

        Response res = createResponse();
        res.setDirectory(directory);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/search", "/search.view"})
    public void search(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        String any = request.getParameter("any");
        String artist = request.getParameter("artist");
        String album = request.getParameter("album");
        String title = request.getParameter("title");

        StringBuilder query = new StringBuilder();
        if (any != null) {
            query.append(any).append(" ");
        }
        if (artist != null) {
            query.append(artist).append(" ");
        }
        if (album != null) {
            query.append(album).append(" ");
        }
        if (title != null) {
            query.append(title);
        }

        SearchCriteria criteria = new SearchCriteria();
        criteria.setQuery(query.toString().trim());
        criteria.setCount(getIntParameter(request, "count", 20));
        criteria.setOffset(getIntParameter(request, "offset", 0));
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);

        org.airsonic.player.domain.SearchResult result = searchService.search(criteria, musicFolders, IndexType.SONG);
        org.subsonic.restapi.SearchResult searchResult = new org.subsonic.restapi.SearchResult();
        searchResult.setOffset(result.getOffset());
        searchResult.setTotalHits(result.getTotalHits());

        for (MediaFile mediaFile : result.getMediaFiles()) {
            searchResult.getMatch().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
        }
        Response res = createResponse();
        res.setSearchResult(searchResult);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/search2", "/search2.view"})
    public void search2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);

        SearchResult2 searchResult = new SearchResult2();

        String query = request.getParameter("query");
        SearchCriteria criteria = new SearchCriteria();
        criteria.setQuery(StringUtils.trimToEmpty(query));
        criteria.setCount(getIntParameter(request, "artistCount", 20));
        criteria.setOffset(getIntParameter(request, "artistOffset", 0));
        org.airsonic.player.domain.SearchResult artists = searchService.search(criteria, musicFolders, IndexType.ARTIST);
        for (MediaFile mediaFile : artists.getMediaFiles()) {
            searchResult.getArtist().add(jaxbContentService.createJaxbArtist(mediaFile, username));
        }

        criteria.setCount(getIntParameter(request, "albumCount", 20));
        criteria.setOffset(getIntParameter(request, "albumOffset", 0));
        org.airsonic.player.domain.SearchResult albums = searchService.search(criteria, musicFolders, IndexType.ALBUM);
        for (MediaFile mediaFile : albums.getMediaFiles()) {
            searchResult.getAlbum().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
        }

        criteria.setCount(getIntParameter(request, "songCount", 20));
        criteria.setOffset(getIntParameter(request, "songOffset", 0));
        org.airsonic.player.domain.SearchResult songs = searchService.search(criteria, musicFolders, IndexType.SONG);
        for (MediaFile mediaFile : songs.getMediaFiles()) {
            searchResult.getSong().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
        }

        Response res = createResponse();
        res.setSearchResult2(searchResult);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/search3", "/search3.view"})
    public void search3(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);

        SearchResult3 searchResult = new SearchResult3();

        String query = request.getParameter("query");
        // replace empty string with null
        query = "\"\"".equals(query) ? null : query;
        int songCount = getIntParameter(request, "songCount", 20);
        int songOffset = getIntParameter(request, "songOffset", 0);
        int albumCount = getIntParameter(request, "albumCount", 20);
        int albumOffset = getIntParameter(request, "albumOffset", 0);
        int artistCount = getIntParameter(request, "artistCount", 20);
        int artistOffset = getIntParameter(request, "artistOffset", 0);
        if (StringUtils.isEmpty(query)) {
            if (artistCount > 0) {
                artistService.getArtists(musicFolders, artistCount, artistOffset).forEach(artist -> searchResult.getArtist().add(jaxbContentService.createJaxbArtist(new ArtistID3(), artist, username)));
            }
            if (albumCount > 0) {
                albumService.getAlbums(musicFolders, albumCount, albumOffset).forEach(album -> searchResult.getAlbum().add(jaxbContentService.createJaxbAlbum(new AlbumID3(), album, username)));
            }
            if (songCount > 0) {
                mediaFileService.getSongs(musicFolders, songCount, songOffset).forEach(song -> searchResult.getSong().add(jaxbContentService.createJaxbChild(player, song, username)));
            }
        } else {
            SearchCriteria criteria = new SearchCriteria();
            criteria.setQuery(StringUtils.trimToEmpty(query));
            criteria.setCount(artistCount);
            criteria.setOffset(artistOffset);
            org.airsonic.player.domain.SearchResult result = searchService.search(criteria, musicFolders, IndexType.ARTIST_ID3);
            for (org.airsonic.player.domain.Artist artist : result.getArtists()) {
                searchResult.getArtist().add(jaxbContentService.createJaxbArtist(new ArtistID3(), artist, username));
            }

            criteria.setCount(albumCount);
            criteria.setOffset(albumOffset);
            result = searchService.search(criteria, musicFolders, IndexType.ALBUM_ID3);
            for (Album album : result.getAlbums()) {
                searchResult.getAlbum().add(jaxbContentService.createJaxbAlbum(new AlbumID3(), album, username));
            }

            criteria.setCount(songCount);
            criteria.setOffset(songOffset);
            result = searchService.search(criteria, musicFolders, IndexType.SONG);
            for (MediaFile song : result.getMediaFiles()) {
                searchResult.getSong().add(jaxbContentService.createJaxbChild(player, song, username));
            }
        }

        Response res = createResponse();
        res.setSearchResult3(searchResult);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getPlaylists", "/getPlaylists.view"})
    public void getPlaylists(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);

        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        String authenticatedUsername = user.getUsername();
        String requestedUsername = request.getParameter("username");

        if (requestedUsername == null) {
            requestedUsername = authenticatedUsername;
        } else if (!user.isAdminRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, authenticatedUsername + " is not authorized to get playlists for " + requestedUsername);
            return;
        }

        Playlists result = new Playlists();

        for (org.airsonic.player.domain.Playlist playlist : playlistService.getReadablePlaylistsForUser(requestedUsername)) {
            result.getPlaylist().add(jaxbContentService.createJaxbPlaylist(new org.subsonic.restapi.Playlist(), playlist));
        }

        Response res = createResponse();
        res.setPlaylists(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getPlaylist", "/getPlaylist.view"})
    public void getPlaylist(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        int id = getRequiredIntParameter(request, "id");

        org.airsonic.player.domain.Playlist playlist = playlistService.getPlaylist(id);
        if (playlist == null) {
            error(request, response, ErrorCode.NOT_FOUND, "Playlist not found: " + id);
            return;
        }
        if (!playlistService.isReadAllowed(playlist, username)) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, "Permission denied for playlist " + id);
            return;
        }
        PlaylistWithSongs result = jaxbContentService.createJaxbPlaylist(new PlaylistWithSongs(), playlist);
        for (MediaFile mediaFile : playlistService.getFilesInPlaylist(id)) {
            if (securityService.isFolderAccessAllowed(mediaFile, username)) {
                result.getEntry().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
            }
        }

        Response res = createResponse();
        res.setPlaylist(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/jukeboxControl", "/jukeboxControl.view"})
    public void jukeboxControl(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request, true);

        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isJukeboxRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to use jukebox.");
            return;
        }

        Player player = playerService.getPlayer(request, response, user.getUsername());

        boolean returnPlaylist = false;
        String action = getRequiredStringParameter(request, "action");

        switch (action) {
            case "start":
                playQueueService.start(player);
                break;
            case "stop":
                playQueueService.stop(player);
                break;
            case "skip":
                int index = getRequiredIntParameter(request, "index");
                long offset = getLongParameter(request, "offset", 0) * 1000;
                playQueueService.skip(player, index, offset);
                break;
            case "add":
                int[] ids = getIntParameters(request, "id");
                playQueueService.add(player, Ints.asList(ids), null, true, true);
                break;
            case "set":
                ids = getIntParameters(request, "id");
                playQueueService.reset(player, Ints.asList(ids), true);
                break;
            case "clear":
                playQueueService.clear(player);
                break;
            case "remove":
                index = getRequiredIntParameter(request, "index");
                playQueueService.remove(player, Arrays.asList(index));
                break;
            case "shuffle":
                playQueueService.shuffle(player);
                break;
            case "setGain":
                float gain = getRequiredFloatParameter(request, "gain");
                playQueueService.setJukeboxGain(player, gain);
                break;
            case "get":
                returnPlaylist = true;
                break;
            case "status":
                // No action necessary.
                break;
            default:
                throw new Exception("Unknown jukebox action: '" + action + "'.");
        }

        String username = securityService.getCurrentUsername(request);
        PlayQueue playQueue = player.getPlayQueue();

        // this variable is only needed for the JukeboxLegacySubsonicService. To be removed.
        boolean controlsJukebox = jukeboxService.canControl(player);

        int currentIndex = controlsJukebox && !playQueue.isEmpty() ? playQueue.getIndex() : -1;
        boolean playing = controlsJukebox && !playQueue.isEmpty() && playQueue.getStatus() == PlayQueue.Status.PLAYING;
        float gain;
        int position;
        gain = jukeboxService.getGain(player);
        position = controlsJukebox && !playQueue.isEmpty() ? jukeboxService.getPosition(player) : 0;

        Response res = createResponse();
        if (returnPlaylist) {
            JukeboxPlaylist result = new JukeboxPlaylist();
            res.setJukeboxPlaylist(result);
            result.setCurrentIndex(currentIndex);
            result.setPlaying(playing);
            result.setGain(gain);
            result.setPosition(position);
            for (MediaFile mediaFile : playQueue.getFiles()) {
                result.getEntry().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
            }
        } else {
            JukeboxStatus result = new JukeboxStatus();
            res.setJukeboxStatus(result);
            result.setCurrentIndex(currentIndex);
            result.setPlaying(playing);
            result.setGain(gain);
            result.setPosition(position);
        }

        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/createPlaylist", "/createPlaylist.view"})
    public void createPlaylist(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request, true);
        String username = securityService.getCurrentUsername(request);

        Integer playlistId = getIntParameter(request, "playlistId");
        String name = request.getParameter("name");
        if (playlistId == null && name == null) {
            error(request, response, ErrorCode.MISSING_PARAMETER, "Playlist ID or name must be specified.");
            return;
        }

        org.airsonic.player.domain.Playlist playlist;
        if (playlistId != null) {
            playlist = playlistService.getPlaylist(playlistId);
            if (playlist == null) {
                error(request, response, ErrorCode.NOT_FOUND, "Playlist not found: " + playlistId);
                return;
            }
            if (!playlistService.isWriteAllowed(playlist, username)) {
                error(request, response, ErrorCode.NOT_AUTHORIZED, "Permission denied for playlist " + playlistId);
                return;
            }
        } else {
            playlist = playlistService.createPlaylist(name, false, username);
            playlistService.broadcast(playlist);
        }

        List<MediaFile> songs = new ArrayList<MediaFile>();
        for (int id : getIntParameters(request, "songId")) {
            MediaFile song = mediaFileService.getMediaFile(id);
            if (song != null) {
                songs.add(song);
            }
        }
        playlistService.setFilesInPlaylist(playlist.getId(), songs);
        playlistService.broadcastFileChange(playlist.getId(), false, true);

        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/updatePlaylist", "/updatePlaylist.view"})
    public void updatePlaylist(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request, true);
        String username = securityService.getCurrentUsername(request);

        int id = getRequiredIntParameter(request, "playlistId");
        org.airsonic.player.domain.Playlist playlist = playlistService.getPlaylist(id);
        if (playlist == null) {
            error(request, response, ErrorCode.NOT_FOUND, "Playlist not found: " + id);
            return;
        }
        if (!playlistService.isWriteAllowed(playlist, username)) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, "Permission denied for playlist " + id);
            return;
        }

        // create new object to not mutate the cache
        String name = request.getParameter("name");
        String comment = request.getParameter("comment");
        Boolean shared = getBooleanParameter(request, "public");
        playlistService.updatePlaylist(id, name, comment, shared);

        // TODO: Add later
//            for (String usernameToAdd : ServletRequestUtils.getStringParameters(request, "usernameToAdd")) {
//                if (securityService.getUserByName(usernameToAdd) != null) {
//                    playlistService.addPlaylistUser(id, usernameToAdd);
//                }
//            }
//            for (String usernameToRemove : ServletRequestUtils.getStringParameters(request, "usernameToRemove")) {
//                if (securityService.getUserByName(usernameToRemove) != null) {
//                    playlistService.deletePlaylistUser(id, usernameToRemove);
//                }
//            }
        List<MediaFile> songs = playlistService.getFilesInPlaylist(id);
        boolean songsChanged = false;

        SortedSet<Integer> tmp = new TreeSet<Integer>();
        for (int songIndexToRemove : getIntParameters(request, "songIndexToRemove")) {
            tmp.add(songIndexToRemove);
        }
        List<Integer> songIndexesToRemove = new ArrayList<Integer>(tmp);
        Collections.reverse(songIndexesToRemove);
        for (Integer songIndexToRemove : songIndexesToRemove) {
            songs.remove(songIndexToRemove.intValue());
            songsChanged = true;
        }
        for (int songToAdd : getIntParameters(request, "songIdToAdd")) {
            MediaFile song = mediaFileService.getMediaFile(songToAdd);
            if (song != null) {
                songs.add(song);
                songsChanged = true;
            }
        }
        if (songsChanged) {
            playlistService.setFilesInPlaylist(id, songs);
        }
        playlistService.broadcastFileChange(id, playlist.getShared(), songsChanged);

        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/deletePlaylist", "/deletePlaylist.view"})
    public void deletePlaylist(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request, true);
        String username = securityService.getCurrentUsername(request);

        int id = getRequiredIntParameter(request, "id");
        if (!playlistService.isExist(id)) {
            error(request, response, ErrorCode.NOT_FOUND, "Playlist not found: " + id);
            return;
        }
        if (!playlistService.isWriteAllowed(id, username)) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, "Permission denied for playlist " + id);
            return;
        }
        playlistService.deletePlaylist(id);
        playlistService.broadcastDeleted(id);

        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/getAlbumList", "/getAlbumList.view"})
    public void getAlbumList(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        int size = getIntParameter(request, "size", 10);
        int offset = getIntParameter(request, "offset", 0);
        Integer musicFolderId = getIntParameter(request, "musicFolderId");

        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);

        size = Math.max(0, Math.min(size, 500));
        String type = getRequiredStringParameter(request, "type");

        List<MediaFile> albums;
        if ("highest".equals(type)) {
            albums = ratingService.getHighestRatedAlbums(offset, size, musicFolders);
        } else if ("frequent".equals(type)) {
            albums = mediaFileService.getMostFrequentlyPlayedAlbums(offset, size, musicFolders);
        } else if ("recent".equals(type)) {
            albums = mediaFileService.getMostRecentlyPlayedAlbums(offset, size, musicFolders);
        } else if ("newest".equals(type)) {
            albums = mediaFileService.getNewestAlbums(offset, size, musicFolders);
        } else if ("starred".equals(type)) {
            albums = mediaFileService.getStarredAlbums(offset, size, username, musicFolders);
        } else if ("alphabeticalByArtist".equals(type)) {
            albums = mediaFileService.getAlphabeticalAlbums(offset, size, true, musicFolders);
        } else if ("alphabeticalByName".equals(type)) {
            albums = mediaFileService.getAlphabeticalAlbums(offset, size, false, musicFolders);
        } else if ("byGenre".equals(type)) {
            albums = mediaFileService.getAlbumsByGenre(offset, size, getRequiredStringParameter(request, "genre"), musicFolders);
        } else if ("byYear".equals(type)) {
            albums = mediaFileService.getAlbumsByYear(offset, size, getRequiredIntParameter(request, "fromYear"),
                    getRequiredIntParameter(request, "toYear"), musicFolders);
        } else if ("random".equals(type)) {
            albums = searchService.getRandomAlbums(size, musicFolders);
        } else {
            throw new Exception("Invalid list type: " + type);
        }

        AlbumList result = new AlbumList();
        for (MediaFile album : albums) {
            result.getAlbum().add(jaxbContentService.createJaxbChild(player, album, username));
        }

        Response res = createResponse();
        res.setAlbumList(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getAlbumList2", "/getAlbumList2.view"})
    public void getAlbumList2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        int size = getIntParameter(request, "size", 10);
        int offset = getIntParameter(request, "offset", 0);
        size = Math.max(0, Math.min(size, 500));
        String type = getRequiredStringParameter(request, "type");
        String username = securityService.getCurrentUsername(request);
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);

        List<Album> albums;
        if ("frequent".equals(type)) {
            albums = albumService.getMostFrequentlyPlayedAlbums(offset, size, musicFolders);
        } else if ("recent".equals(type)) {
            albums = albumService.getMostResentlyPlayedAlbums(offset, size, musicFolders);
        } else if ("newest".equals(type)) {
            albums = albumService.getRecentlyAddedAlbums(offset, size, musicFolders);
        } else if ("alphabeticalByArtist".equals(type)) {
            albums = albumService.getAlphabeticalAlbums(offset, size, true, false, musicFolders);
        } else if ("alphabeticalByName".equals(type)) {
            albums = albumService.getAlphabeticalAlbums(offset, size, false, false, musicFolders);
        } else if ("byGenre".equals(type)) {
            albums = albumService.getAlbumsByGenre(offset, size, getRequiredStringParameter(request, "genre"), musicFolders);
        } else if ("byYear".equals(type)) {
            albums = albumService.getAlbumsByYear(offset, size, getRequiredIntParameter(request, "fromYear"),
                                              getRequiredIntParameter(request, "toYear"), musicFolders);
        } else if ("starred".equals(type)) {
            albums = albumService.getStarredAlbums(offset, size, username, musicFolders);
        } else if ("random".equals(type)) {
            albums = searchService.getRandomAlbumsId3(size, musicFolders);
        } else {
            throw new Exception("Invalid list type: " + type);
        }
        AlbumList2 result = new AlbumList2();
        for (Album album : albums) {
            result.getAlbum().add(jaxbContentService.createJaxbAlbum(new AlbumID3(), album, username));
        }
        Response res = createResponse();
        res.setAlbumList2(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getRandomSongs", "/getRandomSongs.view"})
    public void getRandomSongs(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        int size = getIntParameter(request, "size", 10);
        size = Math.max(0, Math.min(size, 500));
        String genre = getStringParameter(request, "genre");
        Integer fromYear = getIntParameter(request, "fromYear");
        Integer toYear = getIntParameter(request, "toYear");
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);
        RandomSearchCriteria criteria = new RandomSearchCriteria(size, genre, fromYear, toYear, musicFolders);

        Songs result = new Songs();
        for (MediaFile mediaFile : searchService.getRandomSongs(criteria)) {
            result.getSong().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
        }
        Response res = createResponse();
        res.setRandomSongs(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getVideos", "/getVideos.view"})
    public void getVideos(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        int size = getIntParameter(request, "size", Integer.MAX_VALUE);
        int offset = getIntParameter(request, "offset", 0);
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);

        Videos result = new Videos();
        for (MediaFile mediaFile : mediaFileService.getVideos(musicFolders, size, offset)) {
            result.getVideo().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
        }
        Response res = createResponse();
        res.setVideos(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getNowPlaying", "/getNowPlaying.view"})
    public void getNowPlaying(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        NowPlaying result = new NowPlaying();

        Stream.concat(statusService.getActivePlays().parallelStream(),
                statusService.getInactivePlays().parallelStream())
            .map(info -> info.fromPlayStatus())
            .forEach(s -> {
                NowPlayingEntry entry = new NowPlayingEntry();
                entry.setUsername(s.getPlayer().getUsername());
                entry.setPlayerId(s.getPlayer().getId());
                entry.setPlayerName(s.getPlayer().getName());
                entry.setMinutesAgo((int) s.getMinutesAgo());
                result.getEntry().add(jaxbContentService.createJaxbChild(entry, s.getPlayer(), s.getMediaFile(), entry.getUsername()));
            });

        Response res = createResponse();
        res.setNowPlaying(result);
        jaxbWriter.writeResponse(request, response, res);
    }


    @RequestMapping({"/download", "/download.view"})
    public ResponseEntity<Resource> download(Principal p,
            @RequestParam(required = false, name = "id") String id,
            @RequestParam(required = false, name = "playlist") Integer playlist,
            @RequestParam(required = false, name = "player") Integer player,
            @RequestParam(required = false, name = "i") List<Integer> indices,
            ServletWebRequest swr) throws Exception {
        HttpServletRequest request = wrapRequest(swr.getRequest());
        final Integer playerId = Optional.ofNullable(request.getParameter("player")).map(Integer::valueOf).orElse(null);
        Optional<Integer> idInt = Optional.ofNullable(id).map(this::mapId).filter(StringUtils::isNumeric).map(Integer::valueOf);

        User user = securityService.getUserByName(p.getName());
        if (!user.isDownloadRole()) {
            throw new APIException(ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to download files.");
        }
        return downloadController.handleRequest(p, idInt, playlist, playerId, indices,
                new ServletWebRequest(request, swr.getResponse()));
    }

    public static class APIException extends Exception {
        private String message;
        private ErrorCode error;

        public APIException(ErrorCode error, String message) {
            this.message = message;
            this.error = error;
        }

        public APIException(ErrorCode error) {
            this(error, error.getMessage());
        }

        @Override
        public String getMessage() {
            return message;
        }

        public ErrorCode getError() {
            return error;
        }
    }

    @ExceptionHandler(APIException.class)
    public ResponseEntity<String> apiException(ServletWebRequest swr, APIException exception) {
        Entry<String, String> exceptionResponse = jaxbWriter.serializeForType(swr.getRequest(),
                jaxbWriter.createErrorResponse(exception));
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(exceptionResponse.getKey()))
                .body(exceptionResponse.getValue());
    }

    @RequestMapping({"/stream", "/stream.view"})
    public ResponseEntity<Resource> stream(Authentication authentication,
            @RequestParam(required = false, name = "playlist") Integer playlist,
            @RequestParam(required = false, name = "format") String format,
            @RequestParam(required = false, name = "suffix") String suffix,
            @RequestParam("maxBitRate") Optional<Integer> maxBitRate,
            @RequestParam("id") Optional<Integer> id,
            @RequestParam("path") Optional<String> path,
            @RequestParam(required = false, name = "timeOffset") Double timeOffset,
            ServletWebRequest swr) throws Exception {
        HttpServletRequest request = wrapRequest(swr.getRequest());
        User user = securityService.getUserByName(authentication.getName());
        if (!user.isStreamRole()) {
            throw new APIException(ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to play files.");
        }

        return streamController.handleRequest(authentication, playlist, format, suffix, maxBitRate, id, path,
                timeOffset, new ServletWebRequest(request, swr.getResponse()));
    }

    @RequestMapping({"/hls", "/hls.view"})
    public void hls(Authentication authentication, @RequestParam Integer id, HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isStreamRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to play files.");
            return;
        }

        hlsController.handleHlsRequest(authentication, id, request, response);
    }

    @RequestMapping({"/scrobble", "/scrobble.view"})
    public void scrobble(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        boolean submission = getBooleanParameter(request, "submission", true);
        int[] ids = getRequiredIntParameters(request, "id");
        long[] times = getLongParameters(request, "time");
        if (times.length > 0 && times.length != ids.length) {
            error(request, response, ErrorCode.GENERIC, "Wrong number of timestamps: " + times.length);
            return;
        }

        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            MediaFile file = mediaFileService.getMediaFile(id);
            if (file == null) {
                LOG.warn("File to scrobble not found: " + id);
                continue;
            }
            Instant time = times.length == 0 ? null : Instant.ofEpochMilli(times[i]);

            statusService.addRemotePlay(new PlayStatus(UUID.randomUUID(), file, player, time == null ? Instant.now() : time));
            mediaFileService.incrementPlayCount(player, file);
            audioScrobblerService.register(file, player.getUsername(), submission, time);
        }

        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/star", "/star.view"})
    public void star(HttpServletRequest request, HttpServletResponse response) {
        starOrUnstar(request, response, true);
    }

    @RequestMapping({"/unstar", "/unstar.view"})
    public void unstar(HttpServletRequest request, HttpServletResponse response) {
        starOrUnstar(request, response, false);
    }

    private void starOrUnstar(HttpServletRequest request, HttpServletResponse response, boolean star) {
        request = wrapRequest(request);

        String username = securityService.getCurrentUser(request).getUsername();
        for (int id : getIntParameters(request, "id")) {
            MediaFile mediaFile = mediaFileService.getMediaFile(id);
            if (mediaFile == null) {
                error(request, response, ErrorCode.NOT_FOUND, "Media file not found: " + id);
                return;
            }
            if (star) {
                mediaFileService.starMediaFiles(List.of(id), username);
            } else {
                mediaFileService.unstarMediaFiles(List.of(id), username);
            }
        }
        for (int albumId : getIntParameters(request, "albumId")) {
            if (!albumService.starOrUnstar(albumId, username, star)) {
                error(request, response, ErrorCode.NOT_FOUND, "Album not found: " + albumId);
                return;
            }
        }
        for (int artistId : getIntParameters(request, "artistId")) {
            if (!artistService.starOrUnstar(artistId, username, star)) {
                error(request, response, ErrorCode.NOT_FOUND, "Artist not found: " + artistId);
                return;
            }
        }
        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/getStarred", "/getStarred.view"})
    public void getStarred(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);

        Starred result = new Starred();
        for (MediaFile artist : mediaFileService.getStarredArtists(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getArtist().add(jaxbContentService.createJaxbArtist(artist, username));
        }
        for (MediaFile album : mediaFileService.getStarredAlbums(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getAlbum().add(jaxbContentService.createJaxbChild(player, album, username));
        }
        for (MediaFile song : mediaFileService.getStarredSongs(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getSong().add(jaxbContentService.createJaxbChild(player, song, username));
        }
        Response res = createResponse();
        res.setStarred(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getStarred2", "/getStarred2.view"})
    public void getStarred2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);

        Starred2 result = new Starred2();
        for (org.airsonic.player.domain.Artist artist : artistService.getStarredArtists(username, musicFolders)) {
            result.getArtist().add(jaxbContentService.createJaxbArtist(new ArtistID3(), artist, username));
        }
        for (Album album : albumService.getStarredAlbums(username, musicFolders)) {
            result.getAlbum().add(jaxbContentService.createJaxbAlbum(new AlbumID3(), album, username));
        }
        for (MediaFile song : mediaFileService.getStarredSongs(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getSong().add(jaxbContentService.createJaxbChild(player, song, username));
        }
        Response res = createResponse();
        res.setStarred2(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getPodcasts", "/getPodcasts.view"})
    public void getPodcasts(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);
        boolean includeEpisodes = getBooleanParameter(request, "includeEpisodes", true);
        Integer channelId = getIntParameter(request, "id");

        Podcasts result = new Podcasts();

        for (org.airsonic.player.domain.PodcastChannel channel : podcastPersistenceService.getAllChannels()) {
            if (channelId == null || channelId.equals(channel.getId())) {

                org.subsonic.restapi.PodcastChannel c = new org.subsonic.restapi.PodcastChannel();
                result.getChannel().add(c);

                c.setId(String.valueOf(channel.getId()));
                c.setUrl(channel.getUrl());
                c.setStatus(PodcastStatus.valueOf(channel.getStatus().name()));
                c.setTitle(channel.getTitle());
                c.setDescription(channel.getDescription());
                c.setCoverArt(CoverArtController.PODCAST_COVERART_PREFIX + channel.getId());
                c.setOriginalImageUrl(channel.getImageUrl());
                c.setErrorMessage(channel.getErrorMessage());

                if (includeEpisodes) {
                    List<org.airsonic.player.domain.PodcastEpisode> episodes = podcastPersistenceService.getEpisodes(channel.getId());
                    for (org.airsonic.player.domain.PodcastEpisode episode : episodes) {
                        c.getEpisode().add(createJaxbPodcastEpisode(player, username, episode));
                    }
                }
            }
        }
        Response res = createResponse();
        res.setPodcasts(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getNewestPodcasts", "/getNewestPodcasts.view"})
    public void getNewestPodcasts(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        int count = getIntParameter(request, "count", 20);
        NewestPodcasts result = new NewestPodcasts();

        for (org.airsonic.player.domain.PodcastEpisode episode : podcastPersistenceService.getNewestEpisodes(count)) {
            result.getEpisode().add(createJaxbPodcastEpisode(player, username, episode));
        }

        Response res = createResponse();
        res.setNewestPodcasts(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    private org.subsonic.restapi.PodcastEpisode createJaxbPodcastEpisode(Player player, String username, org.airsonic.player.domain.PodcastEpisode episode) {
        org.subsonic.restapi.PodcastEpisode e = new org.subsonic.restapi.PodcastEpisode();

        if (episode.getMediaFile() != null) {
            MediaFile mediaFile = episode.getMediaFile();
            e = jaxbContentService.createJaxbChild(new org.subsonic.restapi.PodcastEpisode(), player, mediaFile, username);
            e.setStreamId(String.valueOf(mediaFile.getId()));
        }

        e.setId(String.valueOf(episode.getId()));  // Overwrites the previous "id" attribute.
        e.setChannelId(String.valueOf(episode.getChannel().getId()));
        e.setStatus(PodcastStatus.valueOf(episode.getStatus().name()));
        e.setTitle(episode.getTitle());
        e.setDescription(episode.getDescription());
        e.setPublishDate(jaxbWriter.convertDate(episode.getPublishDate()));
        return e;
    }

    @RequestMapping({"/refreshPodcasts", "/refreshPodcasts.view"})
    public void refreshPodcasts(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isPodcastRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to administrate podcasts.");
            return;
        }
        podcastManagementService.refreshAllChannels(true);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/createPodcastChannel", "/createPodcastChannel.view"})
    public void createPodcastChannel(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isPodcastRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to administrate podcasts.");
            return;
        }

        String url = getRequiredStringParameter(request, "url");
        podcastManagementService.createChannel(url);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/deletePodcastChannel", "/deletePodcastChannel.view"})
    public void deletePodcastChannel(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isPodcastRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to administrate podcasts.");
            return;
        }

        int id = getRequiredIntParameter(request, "id");
        podcastManagementService.deleteChannel(id);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/deletePodcastEpisode", "/deletePodcastEpisode.view"})
    public void deletePodcastEpisode(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isPodcastRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to administrate podcasts.");
            return;
        }

        int id = getRequiredIntParameter(request, "id");
        podcastPersistenceService.deleteEpisode(id, true);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/downloadPodcastEpisode", "/downloadPodcastEpisode.view"})
    public void downloadPodcastEpisode(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isPodcastRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to administrate podcasts.");
            return;
        }

        int id = getRequiredIntParameter(request, "id");
        org.airsonic.player.domain.PodcastEpisode episode = podcastPersistenceService.getEpisode(id, true);
        if (episode == null) {
            error(request, response, ErrorCode.NOT_FOUND, "Podcast episode " + id + " not found.");
            return;
        }

        podcastDownloadClient.downloadEpisode(episode.getId());
        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/exportPodcasts/opml", "/exportPodcasts/opml.view"})
    public ResponseEntity<PodcastExportOPML> exportPodcastOPML(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isPodcastRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED,
                    user.getUsername() + " is not authorized to administrate podcasts.");
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.builder("attachment").filename("airsonic.opml", StandardCharsets.UTF_8).build());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_XML);

        return ResponseEntity.ok().headers(headers).body(podcastPersistenceService.exportAllChannels());
    }

    @RequestMapping({"/getInternetRadioStations", "/getInternetRadioStations.view"})
    public void getInternetRadioStations(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);

        InternetRadioStations result = new InternetRadioStations();
        for (InternetRadio radio : internetRadioService.getEnabledInternetRadios()) {
            InternetRadioStation i = new InternetRadioStation();
            i.setId(String.valueOf(radio.getId()));
            i.setName(radio.getName());
            i.setStreamUrl(radio.getStreamUrl());
            i.setHomePageUrl(radio.getHomepageUrl());
            result.getInternetRadioStation().add(i);
        }
        Response res = createResponse();
        res.setInternetRadioStations(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getBookmarks", "/getBookmarks.view"})
    public void getBookmarks(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        Bookmarks result = new Bookmarks();
        for (Bookmark bookmark : bookmarkService.getBookmarks(username)) {
            org.subsonic.restapi.Bookmark b = new org.subsonic.restapi.Bookmark();
            result.getBookmark().add(b);
            b.setPosition(bookmark.getPositionMillis());
            b.setUsername(bookmark.getUsername());
            b.setComment(bookmark.getComment());
            b.setCreated(jaxbWriter.convertDate(bookmark.getCreated()));
            b.setChanged(jaxbWriter.convertDate(bookmark.getChanged()));

            MediaFile mediaFile = mediaFileService.getMediaFile(bookmark.getMediaFileId());
            b.setEntry(jaxbContentService.createJaxbChild(player, mediaFile, username));
        }

        Response res = createResponse();
        res.setBookmarks(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/createBookmark", "/createBookmark.view"})
    public void createBookmark(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        int mediaFileId = getRequiredIntParameter(request, "id");
        long position = getRequiredLongParameter(request, "position");
        String comment = request.getParameter("comment");

        bookmarkService.setBookmark(username, mediaFileId, position, comment);

        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/deleteBookmark", "/deleteBookmark.view"})
    public void deleteBookmark(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        String username = securityService.getCurrentUsername(request);
        int mediaFileId = getRequiredIntParameter(request, "id");
        bookmarkService.deleteBookmark(username, mediaFileId);

        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/getPlayQueue", "/getPlayQueue.view"})
    public void getPlayQueue(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        SavedPlayQueue playQueue = playQueueService.loadSavedPlayQueueForRest(username);
        if (playQueue == null) {
            writeEmptyResponse(request, response);
            return;
        }

        org.subsonic.restapi.PlayQueue restPlayQueue = new org.subsonic.restapi.PlayQueue();
        restPlayQueue.setUsername(playQueue.getUsername());
        restPlayQueue.setCurrent(Optional.ofNullable(playQueue.getCurrentMediaFile()).map(MediaFile::getId).orElse(null));
        restPlayQueue.setPosition(playQueue.getPositionMillis());
        restPlayQueue.setChanged(jaxbWriter.convertDate(playQueue.getChanged()));
        restPlayQueue.setChangedBy(playQueue.getChangedBy());

        for (MediaFile mediaFile : playQueue.getMediaFiles()) {
            if (mediaFile != null) {
                restPlayQueue.getEntry().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
            }
        }

        Response res = createResponse();
        res.setPlayQueue(restPlayQueue);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/savePlayQueue", "/savePlayQueue.view"})
    public void savePlayQueue(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        List<Integer> mediaFileIds = Arrays.stream(getIntParameters(request, "id")).boxed().collect(Collectors.toList());
        Integer current = getIntParameter(request, "current");
        Long position = getLongParameter(request, "position");
        String changedBy = getRequiredStringParameter(request, "c");

        if (!mediaFileIds.contains(current)) {
            error(request, response, ErrorCode.GENERIC, "Current track is not included in play queue");
            return;
        }

        playQueueService.savePlayQueue(username, mediaFileIds, current, position, changedBy);

        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/getShares", "/getShares.view"})
    public void getShares(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);

        Shares result = new Shares();
        for (org.airsonic.player.domain.entity.Share share : shareService.getSharesForUser(user)) {
            org.subsonic.restapi.Share s = createJaxbShare(request, share);
            result.getShare().add(s);

            for (MediaFile mediaFile : shareService.getSharedFiles(share.getId(), musicFolders)) {
                s.getEntry().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
            }
        }
        Response res = createResponse();
        res.setShares(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/createShare", "/createShare.view"})
    public void createShare(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isShareRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to share media.");
            return;
        }

        List<MediaFile> files = new ArrayList<MediaFile>();
        for (int id : getRequiredIntParameters(request, "id")) {
            files.add(mediaFileService.getMediaFile(id));
        }

        org.airsonic.player.domain.entity.Share share = shareService.createShare(username, files);
        share.setDescription(request.getParameter("description"));
        long expires = getLongParameter(request, "expires", 0L);
        if (expires != 0) {
            share.setExpires(Instant.ofEpochMilli(expires));
        }
        shareService.updateShare(share);

        Shares result = new Shares();
        org.subsonic.restapi.Share s = createJaxbShare(request, share);
        result.getShare().add(s);

        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);

        for (MediaFile mediaFile : shareService.getSharedFiles(share.getId(), musicFolders)) {
            s.getEntry().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
        }

        Response res = createResponse();
        res.setShares(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/deleteShare", "/deleteShare.view"})
    public void deleteShare(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        int id = getRequiredIntParameter(request, "id");

        org.airsonic.player.domain.entity.Share share = shareService.getShareById(id);
        if (share == null) {
            error(request, response, ErrorCode.NOT_FOUND, "Shared media not found.");
            return;
        }
        if (!user.isAdminRole() && !share.getUsername().equals(user.getUsername())) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, "Not authorized to delete shared media.");
            return;
        }

        shareService.deleteShare(id);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/updateShare", "/updateShare.view"})
    public void updateShare(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        int id = getRequiredIntParameter(request, "id");

        org.airsonic.player.domain.entity.Share share = shareService.getShareById(id);
        if (share == null) {
            error(request, response, ErrorCode.NOT_FOUND, "Shared media not found.");
            return;
        }
        if (!user.isAdminRole() && !share.getUsername().equals(user.getUsername())) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, "Not authorized to modify shared media.");
            return;
        }

        share.setDescription(request.getParameter("description"));
        String expiresString = request.getParameter("expires");
        if (expiresString != null) {
            long expires = Long.parseLong(expiresString);
            share.setExpires(expires == 0L ? null : Instant.ofEpochMilli(expires));
        }
        shareService.updateShare(share);
        writeEmptyResponse(request, response);
    }

    private org.subsonic.restapi.Share createJaxbShare(HttpServletRequest request, org.airsonic.player.domain.entity.Share share) {
        org.subsonic.restapi.Share result = new org.subsonic.restapi.Share();
        result.setId(String.valueOf(share.getId()));
        result.setUrl(shareService.getShareUrl(request, share));
        result.setUsername(share.getUsername());
        result.setCreated(jaxbWriter.convertDate(share.getCreated()));
        result.setVisitCount(share.getVisitCount());
        result.setDescription(share.getDescription());
        result.setExpires(jaxbWriter.convertDate(share.getExpires()));
        result.setLastVisited(jaxbWriter.convertDate(share.getLastVisited()));
        return result;
    }

    @RequestMapping({"/getCoverArt", "/getCoverArt.view"})
    public void getCoverArt(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        coverArtController.get(
                ServletRequestUtils.getStringParameter(request, "id"),
                ServletRequestUtils.getIntParameter(request, "size"),
                ServletRequestUtils.getIntParameter(request, "offset", 60),
                request, response);
    }

    @RequestMapping({"/getAvatar", "/getAvatar.view"})
    public void getAvatar(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Integer id = ServletRequestUtils.getIntParameter(request, "id");
        String username = ServletRequestUtils.getStringParameter(request, "username");
        boolean forceCustom = ServletRequestUtils.getBooleanParameter(request, "forceCustom", false);
        avatarController.handleRequest(id, username, forceCustom, response);
    }

    @RequestMapping({"/changePassword", "/changePassword.view"})
    public void changePassword(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        String username = getRequiredStringParameter(request, "username");
        String password = decrypt(getRequiredStringParameter(request, "password"));

        org.airsonic.player.domain.User authUser = securityService.getCurrentUser(request);

        boolean allowed = authUser.isAdminRole()
                || username.equals(authUser.getUsername()) && authUser.isSettingsRole();

        if (!allowed) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, authUser.getUsername() + " is not authorized to change password for " + username);
            return;
        }

        securityService.createAirsonicCredential(username, password, "Created via Subsonic REST API");

        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/getUser", "/getUser.view"})
    public void getUser(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        String username = getRequiredStringParameter(request, "username");

        org.airsonic.player.domain.User currentUser = securityService.getCurrentUser(request);
        if (!username.equals(currentUser.getUsername()) && !currentUser.isAdminRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, currentUser.getUsername() + " is not authorized to get details for other users.");
            return;
        }

        org.airsonic.player.domain.User requestedUser = securityService.getUserByName(username);
        if (requestedUser == null) {
            error(request, response, ErrorCode.NOT_FOUND, "No such user: " + username);
            return;
        }

        Response res = createResponse();
        res.setUser(createJaxbUser(requestedUser));
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getUsers", "/getUsers.view"})
    public void getUsers(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);

        org.airsonic.player.domain.User currentUser = securityService.getCurrentUser(request);
        if (!currentUser.isAdminRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, currentUser.getUsername() + " is not authorized to get details for other users.");
            return;
        }

        Users result = new Users();
        for (org.airsonic.player.domain.User user : userService.getAllUsers()) {
            result.getUser().add(createJaxbUser(user));
        }

        Response res = createResponse();
        res.setUsers(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    private org.subsonic.restapi.User createJaxbUser(org.airsonic.player.domain.User user) {
        UserSettings userSettings = personalSettingsService.getUserSettings(user.getUsername());

        org.subsonic.restapi.User result = new org.subsonic.restapi.User();
        result.setUsername(user.getUsername());
        result.setEmail(user.getEmail());
        result.setScrobblingEnabled(userSettings.getLastFmEnabled());
        result.setAdminRole(user.isAdminRole());
        result.setSettingsRole(user.isSettingsRole());
        result.setDownloadRole(user.isDownloadRole());
        result.setUploadRole(user.isUploadRole());
        result.setPlaylistRole(true);  // Since 1.8.0
        result.setCoverArtRole(user.isCoverArtRole());
        result.setCommentRole(user.isCommentRole());
        result.setPodcastRole(user.isPodcastRole());
        result.setStreamRole(user.isStreamRole());
        result.setJukeboxRole(user.isJukeboxRole());
        result.setShareRole(user.isShareRole());
        // currently this role isn't supported by airsonic
        result.setVideoConversionRole(false);
        // Useless
        result.setAvatarLastChanged(null);

        TranscodeScheme transcodeScheme = userSettings.getTranscodeScheme();
        if (transcodeScheme != null && transcodeScheme != TranscodeScheme.OFF) {
            result.setMaxBitRate(transcodeScheme.getMaxBitRate());
        }

        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(user.getUsername());
        for (org.airsonic.player.domain.MusicFolder musicFolder : musicFolders) {
            result.getFolder().add(musicFolder.getId());
        }
        return result;
    }

    @RequestMapping({"/createUser", "/createUser.view"})
    public void createUser(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isAdminRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to create new users.");
            return;
        }

        UserSettingsCommand command = new UserSettingsCommand();
        command.setUsername(getRequiredStringParameter(request, "username"));
        command.setPassword(decrypt(getRequiredStringParameter(request, "password")));
        command.setEmail(getRequiredStringParameter(request, "email"));
        command.setLdapAuthenticated(getBooleanParameter(request, "ldapAuthenticated", false));
        command.setAdminRole(getBooleanParameter(request, "adminRole", false));
        command.setCommentRole(getBooleanParameter(request, "commentRole", false));
        command.setCoverArtRole(getBooleanParameter(request, "coverArtRole", false));
        command.setDownloadRole(getBooleanParameter(request, "downloadRole", false));
        command.setStreamRole(getBooleanParameter(request, "streamRole", true));
        command.setUploadRole(getBooleanParameter(request, "uploadRole", false));
        command.setJukeboxRole(getBooleanParameter(request, "jukeboxRole", false));
        command.setPodcastRole(getBooleanParameter(request, "podcastRole", false));
        command.setSettingsRole(getBooleanParameter(request, "settingsRole", true));
        command.setShareRole(getBooleanParameter(request, "shareRole", false));
        command.setTranscodeSchemeName(TranscodeScheme.OFF.name());

        int[] folderIds = ServletRequestUtils.getIntParameters(request, "musicFolderId");
        if (folderIds.length == 0) {
            folderIds = Util.toIntArray(org.airsonic.player.domain.MusicFolder.toIdList(mediaFolderService.getAllMusicFolders()));
        }
        command.setAllowedMusicFolderIds(folderIds);

        userSettingsController.createUser(command);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/updateUser", "/updateUser.view"})
    public void updateUser(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isAdminRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to update users.");
            return;
        }

        String username = getRequiredStringParameter(request, "username");
        org.airsonic.player.domain.User u = securityService.getUserByName(username);
        UserSettings s = personalSettingsService.getUserSettings(username);

        if (u == null) {
            error(request, response, ErrorCode.NOT_FOUND, "No such user: " + username);
            return;
        } else if (org.airsonic.player.domain.User.USERNAME_ADMIN.equals(username)) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, "Not allowed to change admin user");
            return;
        }

        UserSettingsCommand command = new UserSettingsCommand();
        command.setUsername(username);
        command.setEmail(getStringParameter(request, "email", u.getEmail()));
        command.setLdapAuthenticated(getBooleanParameter(request, "ldapAuthenticated", u.isLdapAuthenticated()));
        command.setAdminRole(getBooleanParameter(request, "adminRole", u.isAdminRole()));
        command.setCommentRole(getBooleanParameter(request, "commentRole", u.isCommentRole()));
        command.setCoverArtRole(getBooleanParameter(request, "coverArtRole", u.isCoverArtRole()));
        command.setDownloadRole(getBooleanParameter(request, "downloadRole", u.isDownloadRole()));
        command.setStreamRole(getBooleanParameter(request, "streamRole", u.isDownloadRole()));
        command.setUploadRole(getBooleanParameter(request, "uploadRole", u.isUploadRole()));
        command.setJukeboxRole(getBooleanParameter(request, "jukeboxRole", u.isJukeboxRole()));
        command.setPodcastRole(getBooleanParameter(request, "podcastRole", u.isPodcastRole()));
        command.setSettingsRole(getBooleanParameter(request, "settingsRole", u.isSettingsRole()));
        command.setShareRole(getBooleanParameter(request, "shareRole", u.isShareRole()));

        int maxBitRate = getIntParameter(request, "maxBitRate", s.getTranscodeScheme().getMaxBitRate());
        command.setTranscodeSchemeName(Optional.ofNullable(TranscodeScheme.fromMaxBitRate(maxBitRate)).map(TranscodeScheme::name).orElse(null));

        if (hasParameter(request, "password")) {
            command.setPassword(decrypt(getRequiredStringParameter(request, "password")));
            command.setPasswordChange(true);
        }

        int[] folderIds = ServletRequestUtils.getIntParameters(request, "musicFolderId");
        if (folderIds.length == 0) {
            folderIds = Util.toIntArray(org.airsonic.player.domain.MusicFolder.toIdList(mediaFolderService.getMusicFoldersForUser(username)));
        }
        command.setAllowedMusicFolderIds(folderIds);

        userSettingsController.updateUser(command);
        writeEmptyResponse(request, response);
    }

    private boolean hasParameter(HttpServletRequest request, String name) {
        return request.getParameter(name) != null;
    }

    @RequestMapping({"/deleteUser", "/deleteUser.view"})
    public void deleteUser(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isAdminRole()) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to delete users.");
            return;
        }

        String username = getRequiredStringParameter(request, "username");
        try {
            String currentUsername = securityService.getCurrentUsername(request);
            securityService.deleteUser(username, currentUsername);
        } catch (SecurityService.SelfDeletionException sde) {
            error(request, response, ErrorCode.NOT_AUTHORIZED, sde.getMessage());
            return;
        }

        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/getChatMessages", "/getChatMessages.view"})
    public ResponseEntity<String> getChatMessages(HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.status(HttpStatus.SC_GONE).body(NO_LONGER_SUPPORTED);
    }

    @RequestMapping({"/addChatMessage", "/addChatMessage.view"})
    public ResponseEntity<String> addChatMessage(HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.status(HttpStatus.SC_GONE).body(NO_LONGER_SUPPORTED);
    }

    @RequestMapping({"/getLyrics", "/getLyrics.view"})
    public void getLyrics(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        String artist = request.getParameter("artist");
        String title = request.getParameter("title");
        LyricsInfo lyrics = lyricsWSController.getLyrics(artist, title);

        Lyrics result = new Lyrics();
        result.setArtist(lyrics.getArtist());
        result.setTitle(lyrics.getTitle());
        result.setContent(lyrics.getLyrics());

        Response res = createResponse();
        res.setLyrics(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/setRating", "/setRating.view"})
    public void setRating(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Integer rating = getRequiredIntParameter(request, "rating");
        if (rating == 0) {
            rating = null;
        }

        int id = getRequiredIntParameter(request, "id");
        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            error(request, response, ErrorCode.NOT_FOUND, "File not found: " + id);
            return;
        }

        String username = securityService.getCurrentUsername(request);
        ratingService.setRatingForUser(username, mediaFile, rating);

        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/getAlbumInfo", "/getAlbumInfo.view"})
    public void getAlbumInfo(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        int id = ServletRequestUtils.getRequiredIntParameter(request, "id");

        MediaFile mediaFile = this.mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Media file not found.");
            return;
        }
        AlbumNotes albumNotes = this.lastFmService.getAlbumNotesByMediaFile(mediaFile);

        AlbumInfo result = getAlbumInfoInternal(albumNotes);
        Response res = createResponse();
        res.setAlbumInfo(result);
        this.jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getAlbumInfo2", "/getAlbumInfo2.view"})
    public void getAlbumInfo2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        int id = ServletRequestUtils.getRequiredIntParameter(request, "id");

        Album album = albumService.getAlbum(id);
        if (album == null) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Album not found.");
            return;
        }
        AlbumNotes albumNotes = this.lastFmService.getAlbumNotesByAlbum(album);

        AlbumInfo result = getAlbumInfoInternal(albumNotes);
        Response res = createResponse();
        res.setAlbumInfo(result);
        this.jaxbWriter.writeResponse(request, response, res);
    }

    private AlbumInfo getAlbumInfoInternal(AlbumNotes albumNotes) {
        AlbumInfo result = new AlbumInfo();
        if (albumNotes != null) {
            result.setNotes(albumNotes.getNotes());
            result.setMusicBrainzId(albumNotes.getMusicBrainzId());
            result.setLastFmUrl(albumNotes.getLastFmUrl());
            result.setSmallImageUrl(albumNotes.getSmallImageUrl());
            result.setMediumImageUrl(albumNotes.getMediumImageUrl());
            result.setLargeImageUrl(albumNotes.getLargeImageUrl());
        }
        return result;
    }

    @RequestMapping({"/getVideoInfo", "/getVideoInfo.view"})
    public ResponseEntity<String> getVideoInfo() {
        return ResponseEntity.status(HttpStatus.SC_NOT_IMPLEMENTED).body(NOT_YET_IMPLEMENTED);
    }

    @RequestMapping({"/getCaptions", "/getCaptions.view"})
    public ResponseEntity<String> getCaptions() {
        return ResponseEntity.status(HttpStatus.SC_NOT_IMPLEMENTED).body(NOT_YET_IMPLEMENTED);
    }

    @RequestMapping({"/startScan", "/startScan.view"})
    public void startScan(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        mediaScannerService.scanLibrary();
        getScanStatus(request, response);
    }

    @RequestMapping({"/getScanStatus", "/getScanStatus.view"})
    public void getScanStatus(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        ScanStatus scanStatus = new ScanStatus();
        scanStatus.setScanning(this.mediaScannerService.isScanning());
        scanStatus.setCount((long) this.mediaScannerService.getScanCount());

        Response res = createResponse();
        res.setScanStatus(scanStatus);
        this.jaxbWriter.writeResponse(request, response, res);
    }

    private HttpServletRequest wrapRequest(HttpServletRequest request) {
        return wrapRequest(request, false);
    }

    private HttpServletRequest wrapRequest(final HttpServletRequest request, boolean jukebox) {
        final Integer playerId = createPlayerIfNecessary(request, jukebox);
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                // Returns the correct player to be used in PlayerService.getPlayer()
                if ("player".equals(name)) {
                    return playerId == null ? null : String.valueOf(playerId);
                }

                // Support old style ID parameters.
                if ("id".equals(name)) {
                    return mapId(request.getParameter("id"));
                }

                return super.getParameter(name);
            }
        };
    }

    private String mapId(String id) {
        if (id == null || id.startsWith(CoverArtController.ALBUM_COVERART_PREFIX) ||
                id.startsWith(CoverArtController.ARTIST_COVERART_PREFIX) || StringUtils.isNumeric(id)) {
            return id;
        }

        try {
            String path = StringUtil.utf8HexDecode(id);
            MediaFile mediaFile = mediaFileService.getMediaFile(path);
            return String.valueOf(mediaFile.getId());
        } catch (Exception x) {
            return id;
        }
    }

    private Response createResponse() {
        return jaxbWriter.createResponse(true);
    }

    private void writeEmptyResponse(HttpServletRequest request, HttpServletResponse response) {
        jaxbWriter.writeResponse(request, response, createResponse());
    }

    public void error(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message) {
        jaxbWriter.writeErrorResponse(request, response, code, message);
    }

    private Integer createPlayerIfNecessary(HttpServletRequest request, boolean jukebox) {
        String username = request.getRemoteUser();
        String clientId = request.getParameter("c");
        if (jukebox) {
            clientId += "-jukebox";
        }

        List<Player> players = playerService.getPlayersForUserAndClientId(username, clientId);

        // If not found, create it.
        if (players.isEmpty()) {
            Player player = new Player();
            player.setIpAddress(request.getRemoteAddr());
            player.setUsername(username);
            player.setClientId(clientId);
            player.setName(clientId);
            player.setTechnology(jukebox ? PlayerTechnology.JUKEBOX : PlayerTechnology.EXTERNAL_WITH_PLAYLIST);
            playerService.createPlayer(player);
            players = playerService.getPlayersForUserAndClientId(username, clientId);
        }

        // Return the player ID.
        return !players.isEmpty() ? players.get(0).getId() : null;
    }

    public enum ErrorCode {

        GENERIC(0, "A generic error."),
        MISSING_PARAMETER(10, "Required parameter is missing."),
        PROTOCOL_MISMATCH_CLIENT_TOO_OLD(20, "Incompatible Airsonic REST protocol version. Client must upgrade."),
        PROTOCOL_MISMATCH_SERVER_TOO_OLD(30, "Incompatible Airsonic REST protocol version. Server must upgrade."),
        NOT_AUTHENTICATED(40, "Wrong username or password."),
        NOT_AUTHENTICATED_UPGRADE_TO_NON_HASHED(41, "Wrong username or password, but try authenticating via non-hashed password."),
        NOT_AUTHORIZED(50, "User is not authorized for the given operation."),
        NOT_FOUND(70, "Requested data was not found.");

        private final int code;
        private final String message;

        ErrorCode(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
