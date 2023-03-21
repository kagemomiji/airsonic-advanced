package org.airsonic.player.service;

import org.airsonic.player.domain.Bookmark;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.repository.BookmarkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class BookmarkService {
    private static final Logger LOG = LoggerFactory.getLogger(BookmarkService.class);
    private final BookmarkRepository repository;
    private final MediaFileService mediaFileService;
    private final SimpMessagingTemplate brokerTemplate;

    @Autowired
    public BookmarkService(BookmarkRepository repository, MediaFileService mediaFileService, SimpMessagingTemplate brokerTemplate) {
        this.repository = repository;
        this.mediaFileService = mediaFileService;
        this.brokerTemplate = brokerTemplate;
    }

    @Transactional
    public Optional<Bookmark> getBookmark(String username, int mediaFileId) {
        return repository.findOptByUsernameAndMediaFileId(username, mediaFileId);
    }

    @Transactional
    public boolean setBookmark(String username, int mediaFileId, long positionMillis, String comment) {
        MediaFile mediaFile = this.mediaFileService.getMediaFile(mediaFileId);
        if (mediaFile == null) {
            return false;
        }
        long durationMillis = mediaFile.getDuration() == null ? 0L : mediaFile.getDuration().longValue() * 1000L;
        if (durationMillis > 0L && durationMillis - positionMillis < 60000L) {
            LOG.debug("Deleting bookmark for {} because it's close to the end", mediaFileId);
            deleteBookmark(username, mediaFileId);
            return false;
        }
        LOG.debug("Setting bookmark for {}: {}", mediaFileId, positionMillis);
        Instant now = Instant.now();
        Bookmark bookmark = this.getBookmark(username, mediaFileId).orElse(
            new Bookmark(mediaFileId, username)
        );
        bookmark.setChanged(now);
        bookmark.setComment(comment);
        bookmark.setPositionMillis(positionMillis);
        repository.saveAndFlush(bookmark);

        brokerTemplate.convertAndSendToUser(username, "/queue/bookmarks/added", mediaFileId);

        return true;
    }

    @Transactional
    public void deleteBookmark(String username, int mediaFileId) {
        repository.deleteByUsernameAndMediaFileId(username, mediaFileId);
        brokerTemplate.convertAndSendToUser(username, "/queue/bookmarks/deleted", mediaFileId);
    }

    @Transactional
    public List<Bookmark> getBookmarks(String username) {
        return repository.findByUsername(username);
    }
}
