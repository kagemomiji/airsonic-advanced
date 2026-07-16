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
  Copyright 2016 (C) Airsonic Authors
  Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
*/
package org.airsonic.player.service.upnp;

import org.fourthline.cling.support.contentdirectory.ContentDirectoryErrorCode;
import org.fourthline.cling.support.contentdirectory.ContentDirectoryException;
import org.fourthline.cling.support.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


/**
 * @author Allen Petersen
 * @author Sindre Mehus
 * @version $Id$
 */
@Service
public class DispatchingContentDirectory extends CustomContentDirectory {

    private static final Logger LOG = LoggerFactory.getLogger(DispatchingContentDirectory.class);

    protected static final String SEPARATOR = "-";

    @Autowired
    private UpnpProcessorRouter router;

    @Override
    public BrowseResult browse(String objectId, BrowseFlag browseFlag,
            String filter, long firstResult,
            long maxResults, SortCriterion[] orderBy)
            throws ContentDirectoryException {

        LOG.info("UPnP request - objectId: " + objectId + ", browseFlag: " + browseFlag + ", filter: " + filter
                + ", firstResult: " + firstResult + ", maxResults: " + maxResults);

        if (objectId == null)
            throw new ContentDirectoryException(ContentDirectoryErrorCode.CANNOT_PROCESS, "objectId is null");

        // maxResult == 0 means all.
        if (maxResults == 0) {
            maxResults = Long.MAX_VALUE;
        }

        BrowseResult returnValue = null;
        try {
            String[] splitId = objectId.split(SEPARATOR);
            String browseRoot = splitId[0];
            String itemId = splitId.length == 1 ? null : splitId[1];

            UpnpContentProcessor<?, ?> processor = router.findProcessor(ProcessorType.toEnum(browseRoot));
            if (processor == null) {
                // if it's null then assume it's a file, and that the id
                // is all that's there.
                itemId = browseRoot;
                processor = router.findProcessor(ProcessorType.MEDIAFILE);
            }

            if (itemId == null) {
                returnValue = browseFlag == BrowseFlag.METADATA ? processor.browseRootMetadata()
                        : processor.browseRoot(filter, firstResult, maxResults, orderBy);
            } else {
                returnValue = browseFlag == BrowseFlag.METADATA ? processor.browseObjectMetadata(itemId)
                        : processor.browseObject(itemId, filter, firstResult, maxResults, orderBy);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("upnp request returning result {}", returnValue.getResult());
            }
            return returnValue;
        } catch (Throwable x) {
            LOG.error("UPnP error: " + x, x);
            throw new ContentDirectoryException(ContentDirectoryErrorCode.CANNOT_PROCESS, x.toString());
        }
    }

    @Override
    public BrowseResult search(String containerId,
            String searchCriteria, String filter,
            long firstResult, long maxResults,
            SortCriterion[] orderBy) throws ContentDirectoryException {
        // i don't see a parser for upnp search criteria anywhere, so this will
        // have to do
        String upnpClass = searchCriteria.replaceAll("^.*upnp:class\\s+[\\S]+\\s+\"([\\S]*)\".*$", "$1");
        String titleSearch = searchCriteria.replaceAll("^.*dc:title\\s+[\\S]+\\s+\"([\\S]*)\".*$", "$1");
        BrowseResult returnValue = null;
        if ("object.container.person.musicArtist".equalsIgnoreCase(upnpClass)) {
            returnValue = router.getArtistProcessor().searchByName(titleSearch, firstResult, maxResults, orderBy);
        } else if ("object.item.audioItem".equalsIgnoreCase(upnpClass)) {
            returnValue = router.getMediaFileProcessor().searchByName(titleSearch, firstResult, maxResults, orderBy);
        } else if ("object.container.album.musicAlbum".equalsIgnoreCase(upnpClass)) {
            returnValue = router.getAlbumProcessor().searchByName(titleSearch, firstResult, maxResults, orderBy);
        }

        return returnValue != null ? returnValue
                : super.search(containerId, searchCriteria, filter, firstResult, maxResults, orderBy);
    }

}
