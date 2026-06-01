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

import org.airsonic.player.service.search.IndexManager;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.WriteStatus;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.StorageFolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Allen Petersen
 * @version $Id$
 */
@Component
public class RootUpnpProcessor extends UpnpContentProcessor <Container, Container> {

    @Autowired
    IndexManager indexManager;

    @Autowired
    private UpnpProcessorRouter router;

    public Container createRootContainer() {
        StorageFolder root = new StorageFolder();
        root.setId(ProcessorType.ROOT.getKeyType());
        root.setParentID("-1");

        // MediaLibraryStatistics statistics = indexManager.getStatistics();
        // returning large storageUsed values doesn't play nicely with
        // some upnp clients
        //root.setStorageUsed(statistics == null ? 0 : statistics.getTotalLengthInBytes());
        root.setStorageUsed(-1L);
        root.setTitle("Airsonic Media");
        root.setRestricted(true);
        root.setSearchable(true);
        root.setWriteStatus(WriteStatus.NOT_WRITABLE);

        root.setChildCount(6);
        return root;
    }

    public Container createContainer(Container item) {
        // the items are the containers in this case.
        return item;
    }

    public List<Container> getAllItems() throws Exception {
        ArrayList<Container> allItems = new ArrayList<Container>();
        allItems.add(router.getAlbumProcessor().createRootContainer());
        allItems.add(router.getArtistProcessor().createRootContainer());
        allItems.add(router.getMediaFileProcessor().createRootContainer());
        allItems.add(router.getGenreProcessor().createRootContainer());
        allItems.add(router.getPlaylistProcessor().createRootContainer());
        allItems.add(router.getRecentProcessor().createRootContainer());
        return allItems;
    }

    public Container getItemById(String id) {
        return createRootContainer();
    }

    public List<Container> getChildren(Container item) throws Exception {
        return getAllItems();
    }

    public void addChild(DIDLContent didl, Container child) {
        // special case; root doesn't have object instances
    }
}
