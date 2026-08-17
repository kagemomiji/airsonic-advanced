package org.airsonic.player.repository;

import org.airsonic.player.domain.Album;
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
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public interface AlbumRepository extends JpaRepository<Album, Integer>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Album> {

    public Optional<Album> findByArtistAndName(String artist, String name);

    public List<Album> findByName(String artist);

    public List<Album> findByArtistAndFolderInAndPresentTrue(String artist, Iterable<MusicFolder> musicFolders);

    public List<Album> findByArtistAndFolderInAndPresentTrue(String artist, Iterable<MusicFolder> musicFolders, Pageable pageable);

    public int countByFolderInAndPresentTrue(Iterable<MusicFolder> musicFolders);

    public List<Album> findByFolderInAndPresentTrue(Iterable<MusicFolder> musicFolders, Sort sort);

    public List<Album> findByFolderInAndPresentTrue(Iterable<MusicFolder> musicFolders, Pageable pageable);

    public List<Album> findByGenreAndFolderInAndPresentTrue(String genre, Iterable<MusicFolder> musicFolders, Pageable pageable);

    public List<Album> findByFolderInAndPlayCountGreaterThanAndPresentTrue(Iterable<MusicFolder> musicFolders, AtomicInteger playCount, Pageable pageable);

    public List<Album> findByFolderInAndLastPlayedNotNullAndPresentTrue(Iterable<MusicFolder> musicFolders, Pageable pageable);

    public List<Album> findByFolderInAndYearBetweenAndPresentTrue(Iterable<MusicFolder> musicFolders, int startYear, int endYear, Pageable pageable);

    public List<Album> findByPresentFalse();

    public Optional<Album> findByIdAndStarredAlbumsUsername(Integer id, String username);

    public boolean existsByLastScannedBeforeAndPresentTrue(Instant lastScanned);

    @Transactional
    public void deleteAllByPresentFalse();

    @Transactional
    @Modifying
    @Query("UPDATE Album a SET a.present = false WHERE a.lastScanned < :lastScanned")
    public void markNonPresent(@Param("lastScanned") Instant lastScanned);

}