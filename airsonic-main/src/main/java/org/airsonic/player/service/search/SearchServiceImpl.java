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

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */

package org.airsonic.player.service.search;

import org.airsonic.player.domain.*;
import org.airsonic.player.service.SearchService;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static org.airsonic.player.service.search.IndexType.*;
import static org.springframework.util.ObjectUtils.isEmpty;

@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger LOG = LoggerFactory.getLogger(SearchServiceImpl.class);

    @Autowired
    private QueryFactory queryFactory;
    @Autowired
    private IndexManager indexManager;
    @Autowired
    private SearchServiceUtilities util;

    // TODO Should be changed to SecureRandom?
    private final Random random = new Random(System.currentTimeMillis());

    /**
     * Extracts the integer value from a TotalHits string.
     * The string is expected to be in the format "123 hits" or "123+ hits".
     * If the string ends with "+", it indicates GREATER_THAN_OR_EQUAL_TO.
     *
     * @param totalHitsString The TotalHits string to extract the value from.
     * @return The extracted integer value.
     */

    private Long extractValue(String totalHitsString) {
        if (totalHitsString == null || totalHitsString.isEmpty()) {
            throw new IllegalArgumentException("Input string cannot be null or empty");
        }

        // Remove " hits" from the end of the string
        String cleanedString = totalHitsString.replace(" hits", "");

        // Check if the string contains a "+" (indicating GREATER_THAN_OR_EQUAL_TO)
        if (cleanedString.endsWith("+")) {
            cleanedString = cleanedString.substring(0, cleanedString.length() - 1);
        }

        // Parse the remaining string as a long value
        try {
            return Long.parseLong(cleanedString);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid format for TotalHits string: " + totalHitsString, e);
        }
    }

    @Override
    public SearchResult search(SearchCriteria criteria, List<MusicFolder> musicFolders,
            IndexType indexType) {

        SearchResult result = new SearchResult();
        int offset = criteria.getOffset();
        int count = criteria.getCount();
        result.setOffset(offset);

        if (count <= 0)
            return result;

        IndexSearcher searcher = indexManager.getSearcher(indexType);
        if (isEmpty(searcher)) {
            return result;
        }

        try {
            Query query = queryFactory.search(criteria, musicFolders, indexType);

            TopDocs topDocs = searcher.search(query, offset + count);
            int totalHits = util.round.apply(extractValue(topDocs.totalHits.toString()));
            result.setTotalHits(totalHits);
            int start = Math.min(offset, totalHits);
            int end = Math.min(start + count, totalHits);
            StoredFields storedFields = searcher.storedFields();
            for (int i = start; i < end; i++) {
                util.addIfAnyMatch(result, indexType, storedFields.document(topDocs.scoreDocs[i].doc));
            }

        } catch (IOException e) {
            LOG.error("Failed to execute Lucene search.", e);
        } finally {
            indexManager.release(indexType, searcher);
        }
        return result;
    }

    /**
     * Common processing of random method.
     *
     * @param count Number of albums to return.
     * @param id2ListCallBack Callback to get D from id and store it in List
     */
    private final <D> List<D> createRandomDocsList(
            int count, IndexSearcher searcher, Query query, BiConsumer<List<D>, Integer> id2ListCallBack)
            throws IOException {

        List<Integer> docs = Arrays
                .stream(searcher.search(query, Integer.MAX_VALUE).scoreDocs)
                .map(sd -> sd.doc)
                .collect(Collectors.toList());

        List<D> result = new ArrayList<>();
        StoredFields storedFields = searcher.storedFields();
        while (!docs.isEmpty() && result.size() < count) {
            int randomPos = random.nextInt(docs.size());
            Document document = storedFields.document(docs.get(randomPos));
            id2ListCallBack.accept(result, util.getId.apply(document));
            docs.remove(randomPos);
        }

        return result;
    }

    @Override
    public List<MediaFile> getRandomSongs(RandomSearchCriteria criteria) {

        IndexSearcher searcher = indexManager.getSearcher(SONG);
        if (isEmpty(searcher)) {
            // At first start
            return Collections.emptyList();
        }

        try {

            Query query = queryFactory.getRandomSongs(criteria);
            return createRandomDocsList(criteria.getCount(), searcher, query,
                (dist, id) -> util.addMediaFileIgnoreNull(dist, SONG, id));

        } catch (IOException e) {
            LOG.error("Failed to search or random songs.", e);
        } finally {
            indexManager.release(IndexType.SONG, searcher);
        }
        return Collections.emptyList();
    }

    @Override
    public List<MediaFile> getRandomAlbums(int count, List<MusicFolder> musicFolders) {

        IndexSearcher searcher = indexManager.getSearcher(IndexType.ALBUM);
        if (isEmpty(searcher)) {
            return Collections.emptyList();
        }

        Query query = queryFactory.getRandomAlbums(musicFolders);

        try {

            return createRandomDocsList(count, searcher, query,
                (dist, id) -> util.addMediaFileIgnoreNull(dist, ALBUM, id));

        } catch (IOException e) {
            LOG.error("Failed to search for random albums.", e);
        } finally {
            indexManager.release(IndexType.ALBUM, searcher);
        }
        return Collections.emptyList();
    }

    @Override
    public List<Album> getRandomAlbumsId3(int count, List<MusicFolder> musicFolders) {

        IndexSearcher searcher = indexManager.getSearcher(IndexType.ALBUM_ID3);
        if (isEmpty(searcher)) {
            return Collections.emptyList();
        }

        Query query = queryFactory.getRandomAlbumsId3(musicFolders);

        try {

            return createRandomDocsList(count, searcher, query,
                (dist, id) -> util.addAlbumIgnoreNull(dist, ALBUM_ID3, id));

        } catch (IOException e) {
            LOG.error("Failed to search for random albums.", e);
        } finally {
            indexManager.release(IndexType.ALBUM_ID3, searcher);
        }
        return Collections.emptyList();
    }

    @Override
    public <T> ParamSearchResult<T> searchByName(String name, int offset, int count,
            List<MusicFolder> folderList, Class<T> assignableClass) {

        // we only support album, artist, and song for now
        IndexType indexType = util.getIndexType.apply(assignableClass);
        String fieldName = util.getFieldName.apply(assignableClass);

        ParamSearchResult<T> result = new ParamSearchResult<T>();
        result.setOffset(offset);

        if (isEmpty(indexType) || isEmpty(fieldName) || count <= 0) {
            return result;
        }

        IndexSearcher searcher = indexManager.getSearcher(indexType);
        if (isEmpty(searcher)) {
            return result;
        }

        try {

            Query query = queryFactory.searchByName(fieldName, name);

            SortField[] sortFields = Arrays
                    .stream(indexType.getFields())
                    .map(n -> new SortField(n, SortField.Type.STRING))
                    .toArray(i -> new SortField[i]);
            Sort sort = new Sort(sortFields);

            TopDocs topDocs = searcher.search(query, offset + count, sort);

            int totalHits = util.round.apply(extractValue(topDocs.totalHits.toString()));
            result.setTotalHits(totalHits);
            int start = Math.min(offset, totalHits);
            int end = Math.min(start + count, totalHits);
            StoredFields storedFields = searcher.storedFields();

            for (int i = start; i < end; i++) {
                Document doc = storedFields.document(topDocs.scoreDocs[i].doc);
                util.addIgnoreNull(result, indexType, util.getId.apply(doc), assignableClass);
            }

        } catch (IOException e) {
            LOG.error("Failed to execute Lucene search.", e);
        } finally {
            indexManager.release(indexType, searcher);
        }
        return result;
    }

}
