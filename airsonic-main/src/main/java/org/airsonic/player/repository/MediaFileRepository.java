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
 */
package org.airsonic.player.repository;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MediaFileRepository extends JpaRepository<MediaFile, Integer> {

    public Optional<MediaFile> findByIdAndFolderInAndMediaTypeAndPresentTrue(Integer id, Iterable<MusicFolder> folders, MediaType mediaType);

    public List<MediaFile> findByIdInAndFolderInAndMediaTypeAndPresentTrue(Iterable<Integer> ids, Iterable<MusicFolder> folders, MediaType mediaType);

    public List<MediaFile> findByPath(String path);

    public List<MediaFile> findByLastScannedBeforeAndPresentTrue(Instant lastScanned);

    public List<MediaFile> findByMediaTypeAndPresentFalse(MediaType mediaType);

    public List<MediaFile> findByMediaTypeInAndPresentFalse(Iterable<MediaType> mediaTypes);

    public List<MediaFile> findByMediaTypeInAndArtistAndPresentTrue(List<MediaType> mediaTypes, String artist, Pageable page);

    public List<MediaFile> findByFolder(MusicFolder folder);

    public List<MediaFile> findByFolderAndPresentTrue(MusicFolder folder);

    public List<MediaFile> findByFolderAndPath(MusicFolder folder, String path);

    public List<MediaFile> findByFolderAndPathStartsWith(MusicFolder folder, String path);

    // be carefull, this method can return more than Integer.MAX_VALUE results
    public List<MediaFile> findByFolderAndPathIn(MusicFolder folder, Iterable<String> path);

    public List<MediaFile> findByFolderInAndMediaTypeAndPresentTrue(List<MusicFolder> folders, MediaType mediaType, Pageable page);

    public List<MediaFile> findByFolderInAndMediaTypeAndGenreAndPresentTrue(List<MusicFolder> folders, MediaType mediaType, String genre, Pageable page);

    public List<MediaFile> findByFolderInAndMediaTypeInAndGenreAndPresentTrue(List<MusicFolder> folders, Iterable<MediaType> mediaType, String genre, Pageable page);

    public List<MediaFile> findByFolderInAndMediaTypeAndYearBetweenAndPresentTrue(List<MusicFolder> folders, MediaType mediaType, Integer startYear,
            Integer endYear, Pageable page);

    public List<MediaFile> findByFolderInAndMediaTypeAndPlayCountGreaterThanAndPresentTrue(List<MusicFolder> folders, MediaType mediaType, Integer playCount, Pageable page);

    public List<MediaFile> findByFolderAndParentPath(MusicFolder folder, String parentPath, Sort sort);

    public List<MediaFile> findByFolderAndParentPathAndPresentTrue(MusicFolder folder, String parentPath);

    public List<MediaFile> findByFolderAndParentPathAndPresentTrue(MusicFolder folder, String parentPath, Sort sort);

    public List<MediaFile> findByFolderInAndMediaTypeAndArtistAndPresentTrue(List<MusicFolder> folders, MediaType mediaType, String artist);

    public List<MediaFile> findByFolderInAndMediaTypeAndArtistAndTitleAndPresentTrue(List<MusicFolder> folders, MediaType mediaType, String artist, String title);

    public List<MediaFile> findByAlbumArtistAndAlbumNameAndMediaTypeInAndPresentTrue(String albumArtist, String albumName,
            List<MediaType> mediaTypes, Sort sort);

    public Optional<MediaFile> findByIsrc(String isrc);

    public List<MediaFile> findByIsrcIn(Iterable<String> isrcs);

    public Optional<MediaFile> findByPathAndFolderAndStartPosition(String path, MusicFolder folder, Double startPosition);

    public int countByFolder(MusicFolder folder);

    public int countByFolderInAndMediaTypeAndPresentTrue(List<MusicFolder> folders, MediaType mediaType);

    public int countByFolderInAndMediaTypeAndPlayCountGreaterThanAndPresentTrue(List<MusicFolder> folders, MediaType mediaType, Integer playCount);

    public List<MediaFile> findAll(Specification<MediaFile> spec, Pageable page);

    @Transactional
    public void deleteAllByPresentFalse();

    public List<MediaFile> findByFolderInAndMediaTypeInAndPresentTrue(List<MusicFolder> folders,
            Iterable<MediaType> playableTypes, Pageable offsetBasedPageRequest);

    @Modifying
    @Transactional
    @Query("UPDATE MediaFile m SET m.present = true, m.lastScanned = :lastScanned WHERE m.folder = :folder AND m.path IN :paths")
    public int markPresent(@Param("folder") MusicFolder folder, @Param("paths") Iterable<String> paths, @Param("lastScanned") Instant lastScanned);

    @Modifying
    @Transactional
    @Query("UPDATE MediaFile m SET m.present = false, m.childrenLastUpdated = :childrenLastUpdated WHERE m.lastScanned < :lastScanned")
    public void markNonPresent(@Param("childrenLastUpdated") Instant childrenLastUpdated, @Param("lastScanned") Instant lastScanned);

}
