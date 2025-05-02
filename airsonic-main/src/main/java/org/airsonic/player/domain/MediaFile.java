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

 Copyright 2023 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.airsonic.player.util.FileUtil;
import org.apache.commons.io.FilenameUtils;

import jakarta.persistence.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A media file (audio, video or directory) with an assortment of its meta data.
 *
 * @author Sindre Mehus
 * @version $Id$
 */
@Entity
@Table(name = "media_file")
public class MediaFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "path", nullable = false)
    private String path;

    @ManyToOne
    @JoinColumn(name = "folder_id", referencedColumnName = "id")
    private MusicFolder folder;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private MediaType mediaType;

    @Column(name = "start_position", nullable = true)
    private Double startPosition = NOT_INDEXED; // i.e. not an indexed track

    @Column(name = "format", nullable = true)
    private String format;

    @Column(name = "title", nullable = true)
    private String title;

    @Column(name = "album", nullable = true)
    private String albumName;

    @Column(name = "artist", nullable = true)
    private String artist;

    @Column(name = "album_artist", nullable = true)
    private String albumArtist;

    @Column(name = "disc_number", nullable = true)
    private Integer discNumber;

    @Column(name = "track_number", nullable = true)
    private Integer trackNumber;

    @Column(name = "year", nullable = true)
    private Integer year;

    @Column(name = "genre", nullable = true)
    private String genre;

    @Column(name = "bit_rate", nullable = true)
    private Integer bitRate;

    @Column(name = "variable_bit_rate", nullable = false)
    private boolean variableBitRate;

    @Column(name = "duration", nullable = true)
    private Double duration;

    @Column(name = "file_size", nullable = true)
    private Long fileSize;

    @Column(name = "width", nullable = true)
    private Integer width;

    @Column(name = "height", nullable = true)
    private Integer height;

    @Column(name = "parent_path", nullable = true)
    private String parentPath;

    @Column(name = "index_path", nullable = true)
    private String indexPath;

    @Column(name = "play_count", nullable = false)
    private int playCount;

    @Column(name = "last_played", nullable = true)
    private Instant lastPlayed;

    @Column(name = "comment", nullable = true)
    private String comment;

    @Column(name = "created", nullable = false)
    private Instant created;

    @Column(name = "changed", nullable = false)
    private Instant changed;

    @Column(name = "last_scanned", nullable = false)
    private Instant lastScanned;

    @Transient
    private Instant starredDate;

    @Column(name = "children_last_updated", nullable = false)
    private Instant childrenLastUpdated;

    @Column(name = "present", nullable = false)
    private boolean present;

    @Column(name = "version", nullable = false)
    private int version = VERSION;

    @Column(name = "mb_release_id", nullable = true)
    private String musicBrainzReleaseId;

    @Column(name = "mb_recording_id", nullable = true)
    private String musicBrainzRecordingId;

    @Column(name = "isrc", nullable = true)
    private String isrc;

    @Transient
    private Double averageRating = 0.0;

    public MediaFile() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public MusicFolder getFolder() {
        return folder;
    }

    public void setFolder(MusicFolder folder) {
        this.folder = folder;
    }

    public Path getRelativePath() {
        return Paths.get(path);
    }

    @JsonIgnore
    public Path getFullPath() {
        return folder.getPath().resolve(path);
    }

    @JsonIgnore
    public boolean isExist() {
        return Files.exists(getFullPath());
    }

    /**
     * Returns true if the file has been changed since the last time it was scanned.
     *
     * @return true if the file has been changed since the last time it was scanned. True if the file does not exist.
     */
    @JsonIgnore
    public boolean isChanged() {
        Path fullPath = this.getFullPath();
        return !Files.exists(fullPath) || this.changed.truncatedTo(ChronoUnit.MICROS)
                .compareTo(FileUtil.lastModified(fullPath).truncatedTo(ChronoUnit.MICROS)) < 0;
    }

    public String getIndexPath() {
        return indexPath;
    }

    public void setIndexPath(String path) {
        this.indexPath = path;
    }

    public Path getRelativeIndexPath() {
        return indexPath == null ? null : Paths.get(indexPath);
    }

    @JsonIgnore
    public Path getFullIndexPath() {
        return folder.getPath().resolve(indexPath);
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    public Double getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(Double startPosition) {
        this.startPosition = startPosition;
    }

    public boolean hasIndex() {
        return indexPath != null;
    }

    public boolean isVideo() {
        return mediaType == MediaType.VIDEO;
    }

    public boolean isAudio() {
        return MediaType.audioTypes().contains(mediaType);
    }

    public boolean isIndexedTrack() {
        return startPosition >= 0.0;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public boolean isDirectory() {
        return !isFile();
    }

    public boolean isFile() {
        return mediaType != MediaType.DIRECTORY && mediaType != MediaType.ALBUM;
    }

    public boolean isAlbum() {
        return mediaType == MediaType.ALBUM;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAlbumName() {
        return albumName;
    }

    public void setAlbumName(String album) {
        this.albumName = album;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbumArtist() {
        return albumArtist;
    }

    public void setAlbumArtist(String albumArtist) {
        this.albumArtist = albumArtist;
    }

    public String getName() {
        return title != null ? title : isDirectory() ? FilenameUtils.getName(path) : FilenameUtils.getBaseName(path);
    }

    public Integer getDiscNumber() {
        return discNumber;
    }

    public void setDiscNumber(Integer discNumber) {
        this.discNumber = discNumber;
    }

    public Integer getTrackNumber() {
        return trackNumber;
    }

    public void setTrackNumber(Integer trackNumber) {
        this.trackNumber = trackNumber;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public Integer getBitRate() {
        return bitRate;
    }

    public void setBitRate(Integer bitRate) {
        this.bitRate = bitRate;
    }

    public boolean isVariableBitRate() {
        return variableBitRate;
    }

    public void setVariableBitRate(boolean variableBitRate) {
        this.variableBitRate = variableBitRate;
    }

    public Double getDuration() {
        return duration;
    }

    public void setDuration(Double duration) {
        this.duration = duration;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public String getParentPath() {
        return parentPath;
    }

    public Path getRelativeParentPath() {
        return parentPath == null ? null : Paths.get(parentPath);
    }

    public void setParentPath(String parentPath) {
        this.parentPath = parentPath;
    }

    public int getPlayCount() {
        return playCount;
    }

    public void setPlayCount(int playCount) {
        this.playCount = playCount;
    }

    public Instant getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(Instant lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getChanged() {
        return changed;
    }

    public void setChanged(Instant changed) {
        this.changed = changed;
    }

    public Instant getLastScanned() {
        return lastScanned;
    }

    public void setLastScanned(Instant lastScanned) {
        this.lastScanned = lastScanned;
    }

    public Instant getStarredDate() {
        return starredDate;
    }

    public void setStarredDate(Instant starredDate) {
        this.starredDate = starredDate;
    }

    public String getMusicBrainzReleaseId() {
        return musicBrainzReleaseId;
    }

    public void setMusicBrainzReleaseId(String musicBrainzReleaseId) {
        this.musicBrainzReleaseId = musicBrainzReleaseId;
    }

    public String getMusicBrainzRecordingId() {
        return musicBrainzRecordingId;
    }

    public void setMusicBrainzRecordingId(String musicBrainzRecordingId) {
        this.musicBrainzRecordingId = musicBrainzRecordingId;
    }

    public String getISRC() {
        return isrc;
    }

    public void setISRC(String isrc) {
        this.isrc = isrc;
    }

    /**
     * Returns when the children was last updated in the database.
     */
    public Instant getChildrenLastUpdated() {
        return childrenLastUpdated;
    }

    public void setChildrenLastUpdated(Instant childrenLastUpdated) {
        this.childrenLastUpdated = childrenLastUpdated;
    }

    public boolean isPresent() {
        return present;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }

    public int getVersion() {
        return version;
    }

    public Double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(Double averageRating) {
        this.averageRating = averageRating;
    }

    // placeholder to use prior to persistence
    @Transient
    private CoverArt art;

    public CoverArt getArt() {
        return art;
    }

    public void setArt(CoverArt art) {
        this.art = art;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        MediaFile other = (MediaFile) obj;
        if (path == null) {
            if (other.path != null) {
                return false;
            }
        } else if (!path.equals(other.path)) {
            return false;
        }
        if (!folder.equals(other.folder)) {
            return false;
        }
        if (!startPosition.equals(other.startPosition)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, folder, startPosition);
    }

    @Override
    public String toString() {
        return getName();
    }

    public static List<Integer> toIdList(List<MediaFile> from) {
        return from.stream().map(toId()).collect(Collectors.toList());
    }

    public static Function<MediaFile, Integer> toId() {
        return from -> from.getId();
    }

    public static final double NOT_INDEXED = -1.0;

    public static final int VERSION = 4;

    public static enum MediaType {
        MUSIC,
        PODCAST,
        AUDIOBOOK,
        VIDEO,
        DIRECTORY,
        ALBUM;

        private static final List<MediaType> AUDIO_TYPES = Arrays.asList(MUSIC,AUDIOBOOK,PODCAST);
        private static final List<MediaType> PLAYABLE_TYPES = Arrays.asList(MUSIC, AUDIOBOOK, PODCAST, VIDEO);

        public static List<MediaType> audioTypes() {
            return AUDIO_TYPES;
        }

        public static List<MediaType> playableTypes() {
            return PLAYABLE_TYPES;
        }
    }
}
