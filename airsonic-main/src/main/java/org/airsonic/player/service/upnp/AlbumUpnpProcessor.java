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
  Copyright 2017 (C) Airsonic Authors
  Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
*/
package org.airsonic.player.service.upnp;

import com.google.common.primitives.Ints;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.ParamSearchResult;
import org.airsonic.player.service.AlbumService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.SearchService;
import org.jupnp.support.model.BrowseResult;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.PersonWithRole;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.MusicAlbum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Allen Petersen
 * @version $Id$
 */
@Component
public class AlbumUpnpProcessor extends UpnpContentProcessor <Album, MediaFile> {

    public static final String ALL_BY_ARTIST = "allByArtist";
    public static final String ALL_RECENT = "allRecent";

    @Autowired
    private AlbumService albumService;

    @Autowired
    SearchService searchService;

    @Autowired
    private MediaFileService mediaFileService;

    @Autowired
    private MediaFolderService mediaFolderService;

    @Autowired
    private UpnpProcessorRouter router;

    @Autowired
    private UpnpUtil upnpUtil;

    public AlbumUpnpProcessor() {
        setRootId(ProcessorType.ALBUM);
        setRootTitle("Albums");
    }

    /**
     * Browses the top-level content of a type.
     */
    @Override
    public BrowseResult browseRoot(String filter, long firstResult, long maxResults, SortCriterion[] orderBy) throws Exception {
        DIDLContent didl = new DIDLContent();

        List<MusicFolder> allFolders = mediaFolderService.getAllMusicFolders();
        List<Album> selectedItems = albumService.getAlphabeticalAlbums(Ints.saturatedCast(firstResult), Ints.saturatedCast(maxResults), false, true, allFolders);
        for (Album item : selectedItems) {
            addItem(didl, item);
        }

        return createBrowseResult(didl, didl.getCount(), getAllItemsSize());
    }
    @Override
    public Container createContainer(Album album) {
        MusicAlbum container = new MusicAlbum();

        if (album.getId() == -1) {
            container.setId(getRootId() + DispatchingContentDirectory.SEPARATOR + album.getComment());
        } else {
            container.setId(getRootId() + DispatchingContentDirectory.SEPARATOR + album.getId());
            container.setAlbumArtURIs(new URI[] { upnpUtil.getAlbumArtURI(album.getId()) });
            container.setDescription(album.getComment());
        }
        container.setParentID(getRootId());
        container.setTitle(album.getName());
        // TODO: correct artist?
        if (album.getArtist() != null) {
            container.setArtists(getAlbumArtists(album.getArtist()));
        }
        return container;
    }

    @Override
    public List<Album> getAllItems() {
        List<MusicFolder> allFolders = mediaFolderService.getAllMusicFolders();
        return albumService.getAlphabeticalAlbums(false, true, allFolders);
    }

    @Override
    public Album getItemById(String id) {
        Album returnValue = null;
        if (id.startsWith(ALL_BY_ARTIST) || id.equalsIgnoreCase(ALL_RECENT)) {
            returnValue = new Album();
            returnValue.setId(-1);
            returnValue.setComment(id);
        } else {
            returnValue = albumService.getAlbum(Integer.parseInt(id));
        }
        return returnValue;
    }

    @Override
    public List<MediaFile> getChildren(Album album) {
        List<MediaFile> allFiles = mediaFileService.getSongsForAlbum(album.getArtist(), album.getName());
        if (album.getId() == -1) {
            List<Album> albumList = null;
            if (album.getComment().startsWith(ALL_BY_ARTIST)) {
                ArtistUpnpProcessor ap = router.getArtistProcessor();
                albumList = ap.getChildren(ap.getItemById(album.getComment().replaceAll(ALL_BY_ARTIST + "_", "")));
            } else if (album.getComment().equalsIgnoreCase(ALL_RECENT)) {
                albumList = router.getRecentProcessor().getAllItems();
            } else {
                albumList = new ArrayList<>();
            }
            for (Album a: albumList) {
                if (a.getId() != -1) {
                    allFiles.addAll(mediaFileService.getSongsForAlbum(a.getArtist(), a.getName()));
                }
            }
        } else {
            allFiles = mediaFileService.getSongsForAlbum(album.getArtist(), album.getName());
        }
        return allFiles;
    }

    @Override
    public int getAllItemsSize() {
        List<MusicFolder> allFolders = mediaFolderService.getAllMusicFolders();
        return albumService.getAlbumCount(allFolders);
    }


    @Override
    public void addChild(DIDLContent didl, MediaFile child) {
        didl.addItem(router.getMediaFileProcessor().createItem(child));
    }

    public PersonWithRole[] getAlbumArtists(String artist) {
        return new PersonWithRole[] { new PersonWithRole(artist) };
    }

    public BrowseResult searchByName(String name,
                                     long firstResult, long maxResults,
                                     SortCriterion[] orderBy) {
        DIDLContent didl = new DIDLContent();
        try {
            List<MusicFolder> allFolders = mediaFolderService.getAllMusicFolders();
            ParamSearchResult<Album> result = searchService.searchByName(name, Ints.saturatedCast(firstResult), Ints.saturatedCast(maxResults), allFolders, Album.class);
            List<Album> selectedItems = result.getItems();
            for (Album item : selectedItems) {
                addItem(didl, item);
            }

            return createBrowseResult(didl, didl.getCount(), result.getTotalHits());
        } catch (Exception e) {
            return null;
        }
    }


}
