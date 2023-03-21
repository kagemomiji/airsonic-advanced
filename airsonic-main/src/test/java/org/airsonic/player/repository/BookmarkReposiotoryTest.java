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

import org.airsonic.player.dao.DaoTestCaseBean2;
import org.airsonic.player.dao.UserDao;
import org.airsonic.player.domain.Bookmark;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.User.Role;
import org.airsonic.player.domain.UserCredential;
import org.airsonic.player.domain.UserCredential.App;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BookmarkReposiotoryTest extends DaoTestCaseBean2 {

    @Autowired
    private BookmarkRepository bookmarkRepository;

    @Autowired
    private UserDao userDao;

    private final String TEST_USER_NAME = "testUserForBookmark";

    @Before
    public void setup() {
        getJdbcTemplate().execute("delete from user_credentials");
        getJdbcTemplate().execute("delete from users");
        User user = new User(TEST_USER_NAME, "sindre@activeobjects.no", false, 1000L, 2000L, 3000L, Set.of(Role.ADMIN, Role.COMMENT, Role.COVERART, Role.PLAYLIST, Role.PODCAST, Role.STREAM, Role.JUKEBOX, Role.SETTINGS));
        UserCredential uc = new UserCredential(TEST_USER_NAME, TEST_USER_NAME, "secret", "noop", App.AIRSONIC);
        userDao.createUser(user, uc);
    }
    @After
    public void clean() {
        getJdbcTemplate().execute("delete from user_credentials");
        getJdbcTemplate().execute("delete from users");
    }


    @Test
    public void testSaveBookmark() {
        Bookmark bookmark = new Bookmark();
        bookmark.setMediaFileId(1);
        bookmark.setPositionMillis(1000L);
        bookmark.setUsername(TEST_USER_NAME);
        bookmark.setComment("test comment");
        bookmark.setCreated(Instant.now());
        bookmark.setChanged(Instant.now());

        Bookmark savedBookmark = bookmarkRepository.save(bookmark);
        assertNotNull(savedBookmark.getId());
        assertEquals(1, savedBookmark.getMediaFileId());
        assertEquals(1000L, (long) savedBookmark.getPositionMillis());
        assertEquals(TEST_USER_NAME, savedBookmark.getUsername());
        assertEquals("test comment", savedBookmark.getComment());
    }

    @Test
    public void testFindById() {
        Bookmark bookmark = new Bookmark();
        bookmark.setMediaFileId(1);
        bookmark.setPositionMillis(2000L);
        bookmark.setUsername(TEST_USER_NAME);
        bookmark.setComment("test comment");
        bookmark.setCreated(Instant.now());
        bookmark.setChanged(Instant.now());

        Bookmark savedBookmark = bookmarkRepository.save(bookmark);

        Optional<Bookmark> optionalBookmark = bookmarkRepository.findById(savedBookmark.getId());
        assertTrue(optionalBookmark.isPresent());

        Bookmark foundBookmark = optionalBookmark.get();
        assertEquals(1, foundBookmark.getMediaFileId());
        assertEquals(2000L, (long) foundBookmark.getPositionMillis());
        assertEquals(TEST_USER_NAME, foundBookmark.getUsername());
        assertEquals("test comment", foundBookmark.getComment());
    }

    @Test
    public void testUpdateBookmark() {
        Bookmark bookmark = new Bookmark();
        bookmark.setMediaFileId(1);
        bookmark.setPositionMillis(3000L);
        bookmark.setUsername(TEST_USER_NAME);
        bookmark.setComment("test comment");
        bookmark.setCreated(Instant.now());
        bookmark.setChanged(Instant.now());

        Bookmark savedBookmark = bookmarkRepository.save(bookmark);
        savedBookmark.setPositionMillis(4000L);
        bookmarkRepository.save(savedBookmark);

        Optional<Bookmark> optionalBookmark = bookmarkRepository.findById(savedBookmark.getId());
        assertTrue(optionalBookmark.isPresent());

        Bookmark updatedBookmark = optionalBookmark.get();
        assertEquals(1, updatedBookmark.getMediaFileId());
        assertEquals(4000L, (long) updatedBookmark.getPositionMillis());
        assertEquals(TEST_USER_NAME, updatedBookmark.getUsername());
        assertEquals("test comment", updatedBookmark.getComment());
    }

    @Test
    public void testDeleteBookmark() {
        Bookmark bookmark = new Bookmark();
        bookmark.setMediaFileId(1);
        bookmark.setPositionMillis(3000L);
        bookmark.setUsername(TEST_USER_NAME);
        bookmark.setComment("test comment");
        bookmark.setCreated(Instant.now());
        bookmark.setChanged(Instant.now());

        Bookmark savedBookmark = bookmarkRepository.save(bookmark);

        bookmarkRepository.deleteById(savedBookmark.getId());

        Optional<Bookmark> optionalBookmark = bookmarkRepository.findById(savedBookmark.getId());
        assertTrue(optionalBookmark.isEmpty());
    }

    @Test
    public void testFindOptByUsernameAndMediaFileId() {
        int mediaFileId = 10;

        Optional<Bookmark> foundBookmark = bookmarkRepository.findOptByUsernameAndMediaFileId(TEST_USER_NAME, mediaFileId);
        assertTrue(foundBookmark.isEmpty());

        Bookmark bookmark = new Bookmark();
        bookmark.setMediaFileId(mediaFileId);
        bookmark.setPositionMillis(2000L);
        bookmark.setUsername(TEST_USER_NAME);
        bookmark.setComment("test comment");
        bookmark.setCreated(Instant.now());
        bookmark.setChanged(Instant.now());
        bookmarkRepository.save(bookmark);

        Optional<Bookmark> optionalBookmark = bookmarkRepository.findOptByUsernameAndMediaFileId(TEST_USER_NAME, mediaFileId);
        assertTrue(optionalBookmark.isPresent());

        Optional<Bookmark> foundBookmark2 = bookmarkRepository.findOptByUsernameAndMediaFileId(TEST_USER_NAME, mediaFileId);
        assertEquals(mediaFileId, foundBookmark2.get().getMediaFileId());
        assertEquals(2000L, (long) foundBookmark2.get().getPositionMillis());
        assertEquals(TEST_USER_NAME, foundBookmark2.get().getUsername());
        assertEquals("test comment", foundBookmark2.get().getComment());
    }

    @Test
    public void testFindByUsername() {
        List<Bookmark> bookmarks = new ArrayList<>();

        for (int i = 1; i <= 5; i++) {
            Bookmark bookmark = new Bookmark();
            bookmark.setMediaFileId(i);
            bookmark.setPositionMillis(i * 1000L);
            bookmark.setUsername(TEST_USER_NAME);
            bookmark.setComment("test comment " + i);
            bookmark.setCreated(Instant.now());
            bookmark.setChanged(Instant.now());
            bookmarks.add(bookmarkRepository.save(bookmark));
        }

        List<Bookmark> foundBookmarks = bookmarkRepository.findByUsername(TEST_USER_NAME);
        assertEquals(5, foundBookmarks.size());

        for (int i = 0; i < 5; i++) {
            Bookmark foundBookmark = foundBookmarks.get(i);
            assertEquals(i + 1, foundBookmark.getMediaFileId());
            assertEquals((i + 1) * 1000L, (long) foundBookmark.getPositionMillis());
            assertEquals(TEST_USER_NAME, foundBookmark.getUsername());
            assertEquals("test comment " + (i + 1), foundBookmark.getComment());
        }
    }

    @Test
    public void testDeleteByUsernameAndMediaFileId() {
        int mediaFileId = 10;

        Optional<Bookmark> foundBookmark = bookmarkRepository.findOptByUsernameAndMediaFileId(TEST_USER_NAME, mediaFileId);
        assertTrue(foundBookmark.isEmpty());

        Bookmark bookmark = new Bookmark();
        bookmark.setMediaFileId(mediaFileId);
        bookmark.setPositionMillis(2000L);
        bookmark.setUsername(TEST_USER_NAME);
        bookmark.setComment("test comment");
        bookmark.setCreated(Instant.now());
        bookmark.setChanged(Instant.now());
        bookmarkRepository.save(bookmark);

        Optional<Bookmark> optionalBookmark = bookmarkRepository.findOptByUsernameAndMediaFileId(TEST_USER_NAME, mediaFileId);
        assertTrue(optionalBookmark.isPresent());

        bookmarkRepository.deleteByUsernameAndMediaFileId(TEST_USER_NAME, mediaFileId);

        Optional<Bookmark> deletedBookmark = bookmarkRepository.findOptByUsernameAndMediaFileId(TEST_USER_NAME, mediaFileId);
        assertTrue(deletedBookmark.isEmpty());
    }
}
