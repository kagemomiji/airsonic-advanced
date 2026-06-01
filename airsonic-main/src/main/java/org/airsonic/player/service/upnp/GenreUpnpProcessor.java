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

import org.airsonic.player.domain.Genre;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.util.Util;
import org.jupnp.support.model.BrowseResult;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.GenreContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Allen Petersen
 * @version $Id$
 */
@Service
public class GenreUpnpProcessor extends UpnpContentProcessor <Genre, MediaFile> {

    public GenreUpnpProcessor() {
        setRootId(ProcessorType.GENRE);
        setRootTitle("Genres");
    }

    @Autowired
    private UpnpProcessorRouter router;

    @Autowired
    private MediaFileService mediaFileService;

    @Autowired
    private MediaFolderService mediaFolderService;

    /**
     * Browses the top-level content of a type.
     */
    @Override
    public BrowseResult browseRoot(String filter, long firstResult, long maxResults, SortCriterion[] orderBy) throws Exception {
        // we have to override this to do an index-based id.
        DIDLContent didl = new DIDLContent();
        List<Genre> allItems = getAllItems();
        if (filter != null) {
            // filter items
        }
        if (orderBy != null) {
            // sort items
        }
        List<Genre> selectedItems = Util.subList(allItems, firstResult, maxResults);
        for (int i = 0; i < selectedItems.size(); i++) {
            Genre item = selectedItems.get(i);
            didl.addContainer(createContainer(item, (int) (i + firstResult)));
        }
        return createBrowseResult(didl, didl.getCount(), allItems.size());
    }

    @Override
    public Container createContainer(Genre item) {
        // genre uses index because we don't have a proper id
        return null;
    }

    public Container createContainer(Genre item, int index) {
        GenreContainer container = new GenreContainer();
        container.setId(getRootId() + DispatchingContentDirectory.SEPARATOR + index);
        container.setParentID(getRootId());
        container.setTitle(item.getName());
        container.setChildCount(item.getAlbumCount());

        return container;
    }

    @Override
    public List<Genre> getAllItems() {
        return mediaFileService.getGenres(false);
    }

    @Override
    public Genre getItemById(String id) {
        int index = Integer.parseInt(id);
        List<Genre> allGenres = getAllItems();
        if (allGenres.size() > index) {
            return allGenres.get(index);
        }
        return null;
    }

    @Override
    public List<MediaFile> getChildren(Genre item) {
        List<MusicFolder> allFolders = mediaFolderService.getAllMusicFolders();
        return mediaFileService.getSongsByGenre(0, Integer.MAX_VALUE, item.getName(), allFolders);
    }

    @Override
    public void addChild(DIDLContent didl, MediaFile child) {
        didl.addItem(router.getMediaFileProcessor().createItem(child));
    }
}
