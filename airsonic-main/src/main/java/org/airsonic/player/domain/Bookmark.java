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

import javax.persistence.*;

import java.time.Instant;

/**
 * A bookmark within a media file, for a given user.
 *
 * @author Sindre Mehus and Y.Tory
 * @version $Id$
 */
@Entity
@Table(name = "bookmark", uniqueConstraints = @UniqueConstraint(columnNames = {"username","media_file_id"}))
public class Bookmark {

    public Bookmark(){}

    public Bookmark(int mediaFileId, String username) {
        this.mediaFileId = mediaFileId;
        this.username = username;
        Instant now = Instant.now();
        this.created = now;
        this.changed = now;
    }

    public Bookmark(int id, int mediaFileId, long positionMillis, String username, String comment, Instant created, Instant changed) {
        this.id = id;
        this.mediaFileId = mediaFileId;
        this.positionMillis = positionMillis;
        this.username = username;
        this.comment = comment;
        this.created = created;
        this.changed = changed;
    }



    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "media_file_id")
    private int mediaFileId;

    @Column(name = "position_millis")
    private long positionMillis;

    @Column(name = "username")
    private String username;

    @Column(name = "comment")
    private String comment;

    @Column(name = "created")
    private Instant created;

    @Column(name = "changed")
    private Instant changed;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int getMediaFileId() {
        return mediaFileId;
    }

    public void setMediaFileId(int mediaFileId) {
        this.mediaFileId = mediaFileId;
    }

    public long getPositionMillis() {
        return positionMillis;
    }

    public void setPositionMillis(long positionMillis) {
        this.positionMillis = positionMillis;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

}
