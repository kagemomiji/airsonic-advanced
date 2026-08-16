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
package org.airsonic.player.service;

import org.apache.commons.lang.StringUtils;
import org.airsonic.player.config.AirsonicScanConfig;
import org.airsonic.player.domain.*;
import org.airsonic.player.domain.CoverArt.EntityType;
import org.airsonic.player.service.search.IndexManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.subsonic.restapi.ScanStatus;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Provides services for scanning the music library.
 *
 * @author Sindre Mehus
 */
@Service
public class MediaScannerService {

    private static final Logger LOG = LoggerFactory.getLogger(MediaScannerService.class);

    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private final AtomicBoolean mediaScaninng = new AtomicBoolean(false);

    public MediaScannerService(
        SettingsService settingsService,
        IndexManager indexManager,
        PlaylistFileService playlistFileService,
        MediaFileService mediaFileService,
        MediaFolderService mediaFolderService,
        CoverArtService coverArtService,
        ArtistService artistService,
        AlbumService albumService,
        TaskSchedulingService taskService,
        SimpMessagingTemplate messagingTemplate,
        AirsonicScanConfig scanConfig
    ) {
        this.settingsService = settingsService;
        this.indexManager = indexManager;
        this.playlistFileService = playlistFileService;
        this.mediaFileService = mediaFileService;
        this.mediaFolderService = mediaFolderService;
        this.coverArtService = coverArtService;
        this.artistService = artistService;
        this.albumService = albumService;
        this.taskService = taskService;
        this.messagingTemplate = messagingTemplate;
        this.scanConfig = scanConfig;
        init();
    }

    private final SettingsService settingsService;
    private final IndexManager indexManager;
    private final PlaylistFileService playlistFileService;
    private final MediaFileService mediaFileService;
    private final MediaFolderService mediaFolderService;
    private final CoverArtService coverArtService;
    private final ArtistService artistService;
    private final AlbumService albumService;
    private final TaskSchedulingService taskService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AirsonicScanConfig scanConfig;

    private int scannerParallelism;
    private AtomicInteger scanCount = new AtomicInteger(0);

    public void init() {
        this.scannerParallelism = scanConfig.getParallelism();
        indexManager.initializeIndexDirectory();
        schedule();
    }

    public void initNoSchedule() throws IOException {
        indexManager.deleteOldIndexFiles();
    }

    /**
     * Schedule background execution of media library scanning.
     */
    public synchronized void schedule() {
        long daysBetween = settingsService.getIndexCreationInterval();
        int hour = settingsService.getIndexCreationHour();

        if (daysBetween == -1) {
            LOG.info("Automatic media scanning disabled.");
            taskService.unscheduleTask("mediascanner-IndexingTask");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(hour).withMinute(0).withSecond(0);
        if (now.compareTo(nextRun) > 0)
            nextRun = nextRun.plusDays(1);

        long initialDelayMillis = ChronoUnit.MILLIS.between(now, nextRun);
        Instant firstTime = Instant.now().plusMillis(initialDelayMillis);

        taskService.scheduleAtFixedRate("mediascanner-IndexingTask", () -> scanLibrary(), firstTime, Duration.ofDays(daysBetween), true);

        LOG.info("Automatic media library scanning scheduled to run every {} day(s), starting at {}", daysBetween, nextRun);

        // In addition, create index immediately if it doesn't exist on disk.
        if (neverScanned()) {
            LOG.info("Media library never scanned. Doing it now.");
            scanLibrary();
        }
    }

    boolean neverScanned() {
        return indexManager.getStatistics() == null;
    }

    /**
     * Returns whether the media library is currently being scanned.
     */
    public boolean isScanning() {
        return scanning.get();
    }

    /**
     * Returns whether the media library is currently being scanned.
     */
    public boolean isMediaScanning() {
        return mediaScaninng.get();
    }

    private void setScanning(boolean scanning) {
        this.scanning.set(scanning);
        broadcastScanStatus();
    }

    private void setMediaScanning(boolean mediaScaninng) {
        this.mediaScaninng.set(mediaScaninng);
    }

    private void broadcastScanStatus() {
        CompletableFuture.runAsync(() -> {
            ScanStatus status = new ScanStatus();
            status.setCount(scanCount.longValue());
            status.setScanning(scanning.get());
            messagingTemplate.convertAndSend("/topic/scanStatus", status);
        });
    }

    /**
     * Returns the number of files scanned so far.
     */
    public int getScanCount() {
        return scanCount.get();
    }

    private static ForkJoinWorkerThreadFactory mediaScannerThreadFactory = new ForkJoinWorkerThreadFactory() {
        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            worker.setName("MediaLibraryScanner-" + worker.getPoolIndex());
            worker.setPriority(Thread.MIN_PRIORITY);
            return worker;
        }
    };

    /**
     * Scans the media library.
     * The scanning is done asynchronously, i.e., this method returns immediately.
     */
    public synchronized void scanLibrary() {
        if (isScanning()) {
            return;
        }
        setScanning(true);
        setMediaScanning(true);

        ForkJoinPool pool = new ForkJoinPool(scannerParallelism, mediaScannerThreadFactory, null, true);

        boolean isFullScan = settingsService.getFullScan();
        long timeoutSeconds = isFullScan ? scanConfig.getFullTimeout() : scanConfig.getTimeout();
        MediaLibraryStatistics statistics = new MediaLibraryStatistics();
        LOG.info("Starting media library scan with timeout {} seconds.", timeoutSeconds);
        CompletableFuture.runAsync(() -> {
            doScanLibrary(pool, statistics);
        }, pool)
                .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .whenComplete((r,e) -> {
                    if (e instanceof TimeoutException) {
                        LOG.warn("Media library scan timed out after {} seconds.", timeoutSeconds);
                    } else if (e != null) {
                        LOG.error("Media library scan failed.", e);
                    } else {
                        LOG.info("Media library scan completed.");
                    }
                    setMediaScanning(false);
                })
                .thenRunAsync(() -> playlistFileService.importPlaylists(), pool)
                .whenComplete((r,e) -> {
                    indexManager.stopIndexing(statistics);
                    LOG.info("Indexing complete.");
                    setScanning(false);
                    pool.shutdown();
                });
    }

    private void doScanLibrary(ForkJoinPool pool, MediaLibraryStatistics statistics) {
        LOG.info("Starting to scan media library.");
        LOG.debug("New last scan date is {}", statistics.getScanDate());

        Map<String, AtomicInteger> albumCount = new ConcurrentHashMap<>();
        Map<String, Artist> artists = new ConcurrentHashMap<>();
        Map<String, Album> albums = new ConcurrentHashMap<>();
        Set<Integer> albumsInDb = Collections.synchronizedSet(new HashSet<>());
        try {
            // Maps from artist name to album count.
            Genres genres = new Genres();

            scanCount.set(0);

            indexManager.startIndexing();
            mediaFileService.setMemoryCacheEnabled(false);

            // Recurse through all files on disk.
            pool.submit(() -> {
                mediaFolderService.getAllMusicFolders()
                        .parallelStream()
                        .forEach(musicFolder -> scanFile(pool, null, null, mediaFileService.getMediaFile(Paths.get(""), musicFolder, false),
                                musicFolder, statistics, albumCount, artists, albums, albumsInDb, genres));
                // Update statistics
                statistics.incrementArtists(albumCount.size());
                statistics.incrementAlbums(albumCount.values().parallelStream().mapToInt(x -> x.get()).sum());
            }).join();

            LOG.info("Scanned media library with {} entries.", scanCount.get());

            if (!isMediaScanning()) {
                LOG.info("Scan cancelled.");
                return;
            }

            LOG.info("Persisting albums");
            CompletableFuture<Void> albumPersistence = CompletableFuture
                    .allOf(albums.values().stream()
                            .distinct()
                            .map(a -> CompletableFuture.supplyAsync(() -> {
                                return albumService.save(a);
                            }, pool).thenAcceptAsync(coverArtService::persistIfNeeded))
                            .toArray(CompletableFuture[]::new))
                    .thenRunAsync(() -> {
                        LOG.info("Marking non-present albums.");
                        albumService.markNonPresent(statistics.getScanDate());
                    }, pool)
                    .thenRunAsync(() -> LOG.info("Album persistence complete"), pool);

            LOG.info("Persisting artists");
            CompletableFuture<Void> artistPersistence = CompletableFuture
                    .allOf(artists.values().stream()
                            .distinct()
                            .map(a -> CompletableFuture.supplyAsync(() -> {
                                return artistService.save(a);
                            }, pool).thenAcceptAsync(coverArtService::persistIfNeeded))
                            .toArray(CompletableFuture[]::new))
                    .thenRunAsync(() -> {
                        LOG.info("Marking non-present artists.");
                        artistService.markNonPresent(statistics.getScanDate());
                    }, pool)
                    .thenRunAsync(() -> LOG.info("Artist persistence complete"), pool);

            LOG.info("Persisting media files");
            CompletableFuture<Void> mediaFilePersistence = CompletableFuture
                    .runAsync(() -> {
                        LOG.info("Marking non-present media files.");
                        mediaFileService.markNonPresent(statistics.getScanDate());
                    }, pool)
                    .thenRunAsync(() -> LOG.info("Media file persistence complete"), pool);

            LOG.info("Persisting genres");
            CompletableFuture<Void> genrePersistence = CompletableFuture
                    .runAsync(() -> {
                        LOG.info("Updating genres");
                        long count = mediaFileService.updateGenres(genres.getGenres()).size();
                        boolean genresSuccessful = count == genres.getGenres().size();
                        LOG.info("Genre persistence successfully complete: {}", genresSuccessful);
                    }, pool);

            CompletableFuture.allOf(albumPersistence, artistPersistence, mediaFilePersistence, genrePersistence).join();
            LOG.info("Completed media library scan.");

        } catch (Throwable x) {
            LOG.error("Failed to scan media library.", x);
        } finally {
            mediaFileService.setMemoryCacheEnabled(true);
            if (settingsService.getClearFullScanSettingAfterScan()) {
                settingsService.setClearFullScanSettingAfterScan(null);
                settingsService.setFullScan(null);
                settingsService.save();
            }
            //clearing cache
            albumCount.clear();
            artists.clear();
            albumsInDb.clear();
            albums.clear();
            LOG.info("Media library scan took {}s", ChronoUnit.SECONDS.between(statistics.getScanDate(), Instant.now()));
        }
    }

    private void scanFile(ForkJoinPool pool, MediaFile grandParent, MediaFile parent, MediaFile file, MusicFolder musicFolder, MediaLibraryStatistics statistics,
            Map<String, AtomicInteger> albumCount, Map<String, Artist> artists, Map<String, Album> albums,
            Set<Integer> albumsInDb, Genres genres) {

        if (!isMediaScanning()) {
            LOG.debug("Scan cancelled.");
            return;
        }

        if (scanCount.incrementAndGet() % 250 == 0) {
            broadcastScanStatus();
            LOG.info("Scanned media library with {} entries.", scanCount.get());
        }

        // Update the root folder if it has changed
        if (!musicFolder.getId().equals(file.getFolder().getId())) {
            file.setFolder(musicFolder);
            mediaFileService.updateMediaFile(file);
        }

        indexManager.index(file, musicFolder);

        try {
            pool.submit(() -> {
                if (file.isDirectory()) {
                    try (Stream<MediaFile> children = mediaFileService.getChildrenOf(file, true, true, false, false)
                            .parallelStream()) {
                        children.forEach(child -> scanFile(pool, parent, file, child, musicFolder, statistics, albumCount,
                                artists, albums, albumsInDb, genres));
                    }
                } else {
                    if (musicFolder.getType() == MusicFolder.Type.MEDIA) {
                        updateAlbum(parent, file, musicFolder, statistics.getScanDate(), albumCount, albums, albumsInDb);
                        updateArtist(grandParent, file, musicFolder, statistics.getScanDate(), albumCount, artists);
                    }
                    statistics.incrementSongs(1);
                }

                if (file.isPresent() && (file.getLastScanned() == null || file.getLastScanned().isBefore(statistics.getScanDate()))) {
                    file.setLastScanned(statistics.getScanDate());
                    mediaFileService.updateMediaFile(file);
                }
                updateGenres(file, genres);

                // don't add indexed tracks to the total duration to avoid double-counting
                if ((file.getDuration() != null) && (!file.isIndexedTrack())) {
                    statistics.incrementTotalDurationInSeconds(file.getDuration());
                }
                // don't add indexed tracks to the total size to avoid double-counting
                if ((file.getFileSize() != null) && (!file.isIndexedTrack())) {
                    statistics.incrementTotalLengthInBytes(file.getFileSize());
                }
            }).join();
        } catch (Exception e) {
            LOG.warn("scan file failed : {} in {}", file.getPath(), musicFolder.getPath(), e);
        }
    }

    private void updateGenres(MediaFile file, Genres genres) {
        String genre = file.getGenre();
        if (genre == null) {
            return;
        }
        if (file.isAlbum()) {
            genres.incrementAlbumCount(genre, settingsService.getGenreSeparators());
        } else if (file.isAudio()) {
            genres.incrementSongCount(genre, settingsService.getGenreSeparators());
        }
    }

    /**
     * update album stats
     *
     * @param file media file
     * @param musicFolder music folder
     * @param lastScanned last scanned time
     * @param albumCount album count
     * @param albums albums
     * @param albumsInDb albums in db
     */
    private void updateAlbum(MediaFile parent, MediaFile file, MusicFolder musicFolder,
            Instant lastScanned, Map<String, AtomicInteger> albumCount, Map<String, Album> albums,
            Set<Integer> albumsInDb) {

        String artist = file.getAlbumArtist() != null ? file.getAlbumArtist() : file.getArtist();
        if (file.getAlbumName() == null || artist == null || file.getParentPath() == null || !file.isAudio()) {
            return;
        }

        final AtomicBoolean firstEncounter = new AtomicBoolean(false);
        Album album = albums.compute(file.getAlbumName() + "|" + artist, (k, v) -> {
            Album a = v;

            if (a == null) {
                a = albumService.getAlbumByArtistAndName(artist, file.getAlbumName()).map(dbAlbum -> {
                    if (!albumsInDb.contains(dbAlbum.getId())) {
                        albumsInDb.add(dbAlbum.getId());
                        dbAlbum.setDuration(0);
                        dbAlbum.setSongCount(0);
                        if (dbAlbum.getPath() != file.getParentPath()) {
                            dbAlbum.setPath(file.getParentPath());
                        }
                    }
                    return dbAlbum;
                }).orElse(null);
            }

            if (a == null) {
                a = new Album();
                a.setPath(file.getParentPath());
                a.setName(file.getAlbumName());
                a.setArtist(artist);
                a.setCreated(file.getChanged());
            }

            firstEncounter.set(!lastScanned.equals(a.getLastScanned()));

            if (file.getDuration() != null) {
                a.incrementDuration(file.getDuration());
            }
            if (file.isAudio()) {
                a.incrementSongCount();
            }

            a.setLastScanned(lastScanned);
            a.setPresent(true);

            return a;
        });

        if (file.getMusicBrainzReleaseId() != null) {
            album.setMusicBrainzReleaseId(file.getMusicBrainzReleaseId());
        }
        if (file.getYear() != null) {
            album.setYear(file.getYear());
        }
        if (file.getGenre() != null) {
            album.setGenre(file.getGenre());
        }

        if (album.getArt() == null && parent != null) {
            CoverArt art = coverArtService.getMediaFileArt(parent.getId());
            if (!CoverArt.NULL_ART.equals(art)) {
                album.setArt(new CoverArt(-1, EntityType.ALBUM, art.getPath(), art.getFolder(), false));
            }
        }

        if (firstEncounter.get()) {
            album.setFolder(musicFolder);
            albumService.save(album);
            albumCount.computeIfAbsent(artist, k -> new AtomicInteger(0)).incrementAndGet();
            indexManager.index(album);
        }

        // Update the file's album artist, if necessary.
        if (!Objects.equals(album.getArtist(), file.getAlbumArtist())) {
            file.setAlbumArtist(album.getArtist());
            mediaFileService.updateMediaFile(file);
        }
    }

    /**
     * update artist stats
     *
     * @param grandParent grand parent of the file
     * @param file        media file
     * @param musicFolder music folder
     * @param lastScanned last scanned time
     * @param albumCount  album count
     * @param artists     artists
     */
    private void updateArtist(MediaFile grandParent, MediaFile file, MusicFolder musicFolder, Instant lastScanned,
            Map<String, AtomicInteger> albumCount, Map<String, Artist> artists) {
        if (file.getAlbumArtist() == null || !file.isAudio()) {
            return;
        }

        // When an artist separator is configured, split multi-artist tags and index each artist separately.
        String separators = settingsService.getArtistSeparators();
        if (StringUtils.isNotEmpty(separators)) {
            String[] splitArtists = StringUtils.split(file.getAlbumArtist(), separators);
            if (splitArtists.length > 1) {
                for (String splitArtist : splitArtists) {
                    String trimmed = StringUtils.trim(splitArtist);
                    if (StringUtils.isNotBlank(trimmed)) {
                        updateSingleArtist(trimmed, grandParent, file, musicFolder, lastScanned, albumCount, artists);
                    }
                }
                return;
            }
        }

        updateSingleArtist(file.getAlbumArtist(), grandParent, file, musicFolder, lastScanned, albumCount, artists);
    }

    private void updateSingleArtist(String artistName, MediaFile grandParent, MediaFile file, MusicFolder musicFolder,
            Instant lastScanned, Map<String, AtomicInteger> albumCount, Map<String, Artist> artists) {

        final AtomicBoolean firstEncounter = new AtomicBoolean(false);

        Artist artist = artists.compute(artistName, (k, v) -> {
            Artist a = v;

            if (a == null) {
                a = artistService.getArtist(k);
                if (a == null) {
                    a = new Artist(k);
                }
            }

            int n = Math.max(Optional.ofNullable(albumCount.get(a.getName())).map(x -> x.get()).orElse(0),
                    Optional.ofNullable(a.getAlbumCount()).orElse(0));
            a.setAlbumCount(n);

            firstEncounter.set(!lastScanned.equals(a.getLastScanned()));

            a.setLastScanned(lastScanned);
            a.setPresent(true);

            return a;
        });

        if (firstEncounter.get()) {
            artist.setFolder(musicFolder);
            artistService.save(artist);
            indexManager.index(artist, musicFolder);
        }

        // directory structure is /artist/album/track
        // if the artist has no art, use the grand parent's art
        if (grandParent != null && artist.getArt() == null) {
            CoverArt art = coverArtService.getMediaFileArt(grandParent.getId());
            if (!CoverArt.NULL_ART.equals(art)) {
                artist.setArt(new CoverArt(-1, EntityType.ARTIST, art.getPath(), art.getFolder(), false));
                LOG.debug("Artist {} has no art, using grandparent's art", artist.getId());
            }
        }
    }
}
