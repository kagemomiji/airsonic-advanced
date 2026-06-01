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
  Copyright 2017 (C) Airsonic Authors
  Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
*/
package org.airsonic.player.service.upnp;

import com.google.common.primitives.Ints;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.ParamSearchResult;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.SearchService;
import org.jupnp.support.model.BrowseResult;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.MusicAlbum;
import org.jupnp.support.model.item.Item;
import org.jupnp.support.model.item.MusicTrack;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * @author Allen Petersen
 * @version $Id$
 */
@Service
public class MediaFileUpnpProcessor extends UpnpContentProcessor <MediaFile, MediaFile> {

    public MediaFileUpnpProcessor() {
        setRootId(ProcessorType.FOLDER);
        setRootTitle("Folders");
    }

    @Autowired
    private UpnpProcessorRouter router;

    @Autowired
    private MediaFileService mediaFileService;

    @Autowired
    private MediaFolderService mediaFolderService;

    @Autowired
    private UpnpUtil upnpUtil;

    @Autowired
    private SearchService searchService;

    @Override
    // overriding for the case of browsing a file
    public BrowseResult browseObjectMetadata(String id) throws Exception {
        MediaFile item = getItemById(id);
        DIDLContent didl = new DIDLContent();
        addChild(didl, item);
        return createBrowseResult(didl, 1, 1);
    }

    @Override
    public Container createContainer(MediaFile item) {
        MusicAlbum container = new MusicAlbum();
        if (item.isAlbum()) {
            container.setAlbumArtURIs(new URI[] { upnpUtil.getAlbumArtURI(item.getId())});

            if (item.getArtist() != null) {
                container.setArtists(router.getAlbumProcessor().getAlbumArtists(item.getArtist()));
            }
            container.setDescription(item.getComment());
        }
        container.setId(getRootId() + DispatchingContentDirectory.SEPARATOR + item.getId());
        container.setTitle(item.getName());
        List<MediaFile> children = getChildren(item);
        container.setChildCount(children.size());

        if (! mediaFileService.isRoot(item)) {
            MediaFile parent = mediaFileService.getParentOf(item);
            if (parent != null) {
                container.setParentID(String.valueOf(parent.getId()));
            }
        } else {
            container.setParentID(getRootId());
        }
        return container;
    }

    @Override
    public List<MediaFile> getAllItems() {
        List<MusicFolder> allFolders = mediaFolderService.getAllMusicFolders();

        if (allFolders.size() == 1) {
            // if there's only one root folder just return it
            return getChildren(mediaFileService.getMediaFile("", allFolders.get(0)));
        } else {
            return allFolders.stream().map(f -> mediaFileService.getMediaFile("", f)).collect(toList());
        }
    }

    @Override
    public MediaFile getItemById(String id) {
        return mediaFileService.getMediaFile(Integer.parseInt(id));
    }

    @Override
    public List<MediaFile> getChildren(MediaFile item) {
        List<MediaFile> children = mediaFileService.getVisibleChildrenOf(item, true, true);
        children.sort((MediaFile o1, MediaFile o2) -> o1.getPath().replaceAll("\\W", "").compareToIgnoreCase(o2.getPath().replaceAll("\\W", "")));
        return children;
    }

    @Override
    public void addItem(DIDLContent didl, MediaFile item) {
        if (item.isFile()) {
            didl.addItem(createItem(item));
        } else {
            didl.addContainer(createContainer(item));
        }
    }

    @Override
    public void addChild(DIDLContent didl, MediaFile child) {
        if (child.isFile()) {
            didl.addItem(createItem(child));
        } else {
            didl.addContainer(createContainer(child));
        }
    }

    public Item createItem(MediaFile song) {
        MediaFile parent = mediaFileService.getParentOf(song);
        MusicTrack item = new MusicTrack();
        item.setId(String.valueOf(song.getId()));
        item.setParentID(String.valueOf(parent.getId()));
        item.setTitle(song.getTitle());
        item.setAlbum(song.getAlbumName());
        if (song.getArtist() != null) {
            item.setArtists(router.getAlbumProcessor().getAlbumArtists(song.getArtist()));
        }
        Integer year = song.getYear();
        if (year != null) {
            item.setDate(year + "-01-01");
        }
        item.setOriginalTrackNumber(song.getTrackNumber());
        if (song.getGenre() != null) {
            item.setGenres(new String[]{song.getGenre()});
        }
        item.setResources(Arrays.asList(upnpUtil.createResourceForSong(song)));
        item.setDescription(song.getComment());
        item.addProperty(new DIDLObject.Property.UPNP.ALBUM_ART_URI(upnpUtil.getAlbumArtURI(parent.getId())));

        return item;
    }

    public BrowseResult searchByName(String name,
                                     long firstResult, long maxResults,
                                     SortCriterion[] orderBy) {
        DIDLContent didl = new DIDLContent();
        try {
            List<MusicFolder> allFolders = mediaFolderService.getAllMusicFolders();
            ParamSearchResult<MediaFile> result = searchService.searchByName(name, Ints.saturatedCast(firstResult), Ints.saturatedCast(maxResults), allFolders, MediaFile.class);
            List<MediaFile> selectedItems = result.getItems();
            for (MediaFile item : selectedItems) {
                addItem(didl, item);
            }

            return createBrowseResult(didl, didl.getCount(), result.getTotalHits());
        } catch (Exception e) {
            return null;
        }
    }

}
