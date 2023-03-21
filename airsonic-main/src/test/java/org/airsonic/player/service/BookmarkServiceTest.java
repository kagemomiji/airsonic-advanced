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

package org.airsonic.player.service;

import org.airsonic.player.domain.Bookmark;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.repository.BookmarkRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
public class BookmarkServiceTest {

    private BookmarkService bookmarkService;

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private MediaFileService mediaFileService;

    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;

    private Bookmark bookmark;

    @Before
    public void setUp() throws Exception {
        bookmarkService = new BookmarkService(bookmarkRepository, mediaFileService, simpMessagingTemplate);
        bookmark = new Bookmark(1, 2, 5000L, "testUser", "test comment", null, null);
    }

    @Test
    public void testGetBookmark() {
        when(bookmarkRepository.findOptByUsernameAndMediaFileId("testUser", 2)).thenReturn(Optional.of(bookmark));
        Optional<Bookmark> retrievedBookmark = bookmarkService.getBookmark("testUser", 2);
        assertTrue(retrievedBookmark.isPresent());
        assertEquals(retrievedBookmark.get().getId(), bookmark.getId());
    }

    @Test
    public void testSetBookmark() {
        MediaFile mediaFile = new MediaFile();
        mediaFile.setDuration((double) 10000);
        when(mediaFileService.getMediaFile(2)).thenReturn(mediaFile);
        when(bookmarkRepository.findOptByUsernameAndMediaFileId("testUser", 2)).thenReturn(Optional.empty());
        when(bookmarkRepository.saveAndFlush(any(Bookmark.class))).thenReturn(bookmark);

        boolean result = bookmarkService.setBookmark("testUser", 2, 5000L, "test comment");
        assertTrue(result);
    }

    @Test
    public void testSetBookmarkReturnsFalseIfMediaFileIsNull() {
        // Arrange
        int mediaFileId = 123;
        String username = "testUser";
        long positionMillis = 10000L;
        String comment = "testComment";

        when(mediaFileService.getMediaFile(anyInt())).thenReturn(null);

        // Act
        boolean result = bookmarkService.setBookmark(username, mediaFileId, positionMillis, comment);

        // Assert
        assertFalse(result);
        verify(bookmarkRepository, never()).findOptByUsernameAndMediaFileId(anyString(), anyInt());
        verify(bookmarkRepository, never()).saveAndFlush(any());
        verify(simpMessagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    public void testSetBookmarkReturnsFalseIfCloseToEnd() {
        // Arrange
        int mediaFileId = 123;
        String username = "testUser";
        String comment = "testComment";
        int durationSeconds = 120; // 2 minutes

        MediaFile mediaFile = new MediaFile();
        mediaFile.setDuration((double)durationSeconds);

        when(mediaFileService.getMediaFile(anyInt())).thenReturn(mediaFile);
        when(bookmarkRepository.findOptByUsernameAndMediaFileId(anyString(), anyInt())).thenReturn(Optional.empty());

        // Act
        boolean result = bookmarkService.setBookmark(username, mediaFileId, durationSeconds * 1000L - 50000L, comment);

        // Assert
        assertFalse(result);
        verify(bookmarkRepository, never()).findOptByUsernameAndMediaFileId(eq(username), eq(mediaFileId));
        verify(bookmarkRepository, never()).saveAndFlush(any());
        verify(simpMessagingTemplate, times(1)).convertAndSendToUser(anyString(), anyString(), anyInt());
        verify(bookmarkRepository, times(1)).deleteByUsernameAndMediaFileId(eq(username), eq(mediaFileId));
    }

    @Test
    public void testDeleteBookmark() {
        bookmarkService.deleteBookmark("testUser", 2);
        assertEquals(0, bookmarkRepository.findByUsername("testUser").size());
    }

    @Test
    public void testGetBookmarks() {
        List<Bookmark> bookmarks = new ArrayList<>();
        bookmarks.add(bookmark);
        when(bookmarkRepository.findByUsername("testUser")).thenReturn(bookmarks);
        List<Bookmark> retrievedBookmarks = bookmarkService.getBookmarks("testUser");
        assertEquals(1, retrievedBookmarks.size());
        assertEquals(retrievedBookmarks.get(0).getId(), bookmark.getId());
    }
}