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
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.ParamSearchResult;
import org.airsonic.player.repository.AlbumRepository;
import org.airsonic.player.repository.ArtistRepository;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.SearchService;
import org.jupnp.support.model.BrowseResult;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.SortCriterion;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.container.MusicArtist;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;

/**
 * @author Allen Petersen
 * @version $Id$
 */
@Service
@Transactional(readOnly = true)
public class ArtistUpnpProcessor extends UpnpContentProcessor <Artist, Album> {

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private MediaFolderService mediaFolderService;

    @Autowired
    private UpnpProcessorRouter router;

    @Autowired
    private SearchService searchService;

    public ArtistUpnpProcessor() {
        setRootId(ProcessorType.ARTIST);
        setRootTitle("Artists");
    }

    @Override
    public Container createContainer(Artist artist) {
        MusicArtist container = new MusicArtist();
        container.setId(getRootId() + DispatchingContentDirectory.SEPARATOR + artist.getId());
        container.setParentID(getRootId());
        container.setTitle(artist.getName());
        container.setChildCount(artist.getAlbumCount());

        return container;
    }

    @Override
    public List<Artist> getAllItems() {
        List<MusicFolder> allFolders = mediaFolderService.getAllMusicFolders();
        if (CollectionUtils.isEmpty(allFolders)) {
            return Collections.emptyList();
        }
        List<Artist> allArtists = artistRepository.findByFolderInAndPresentTrue(allFolders, Sort.by(Sort.Direction.ASC, "name"));
        // alpha artists doesn't quite work :P
        allArtists.sort((Artist o1, Artist o2) -> o1.getName().replaceAll("\\W", "").compareToIgnoreCase(o2.getName().replaceAll("\\W", "")));

        return allArtists;
    }

    @Override
    public Artist getItemById(String id) {
        try {
            return artistRepository.findById(Integer.parseInt(id)).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<Album> getChildren(Artist artist) {
        List<MusicFolder> allFolders = mediaFolderService.getAllMusicFolders();
        List<Album> allAlbums = albumRepository.findByArtistAndFolderInAndPresentTrue(artist.getName(), allFolders);
        if (allAlbums.size() > 1) {
            // if the artist has more than one album, add in an option to
            // view the tracks in all the albums together
            Album viewAll = new Album();
            viewAll.setName("- All Albums -");
            viewAll.setId(-1);
            viewAll.setComment(AlbumUpnpProcessor.ALL_BY_ARTIST + "_" + artist.getId());
            allAlbums.add(0, viewAll);
        }
        return allAlbums;
    }

    @Override
    public void addChild(DIDLContent didl, Album album) {
        didl.addContainer(router.getAlbumProcessor().createContainer(album));
    }


    public BrowseResult searchByName(String name,
                                     long firstResult, long maxResults,
                                     SortCriterion[] orderBy) {
        DIDLContent didl = new DIDLContent();
        try {
            List<MusicFolder> allFolders = mediaFolderService.getAllMusicFolders();
            ParamSearchResult<Artist> result = searchService.searchByName(name, Ints.saturatedCast(firstResult), Ints.saturatedCast(maxResults), allFolders, Artist.class);
            List<Artist> selectedItems = result.getItems();
            for (Artist item : selectedItems) {
                addItem(didl, item);
            }

            return createBrowseResult(didl, didl.getCount(), result.getTotalHits());
        } catch (Exception e) {
            return null;
        }
    }

}
