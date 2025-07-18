package org.airsonic.player.service;

import chameleon.playlist.*;
import org.airsonic.player.domain.InternetRadio;
import org.airsonic.player.domain.InternetRadioSource;
import org.airsonic.player.repository.InternetRadioRepository;
import org.apache.commons.io.input.BoundedInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.*;

@Service
public class InternetRadioService {

    private static final Logger LOG = LoggerFactory.getLogger(InternetRadioService.class);

    @Autowired
    private InternetRadioRepository internetRadioRepository;

    /**
     * The maximum number of source URLs in a remote playlist.
     */
    private static final int PLAYLIST_REMOTE_MAX_LENGTH = 250;

    /**
     * The maximum size, in bytes, for a remote playlist response.
     */
    private static final long PLAYLIST_REMOTE_MAX_BYTE_SIZE = 100 * 1024;  // 100 kB

    /**
     * The maximum number of redirects for a remote playlist response.
     */
    private static final int PLAYLIST_REMOTE_MAX_REDIRECTS = 20;

    /**
     * A list of cached source URLs for remote playlists.
     */
    private final Map<Integer, List<InternetRadioSource>> cachedSources;

    /**
     * Generic exception class for playlists.
     */
    private class PlaylistException extends Exception {
        public PlaylistException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when the remote playlist is too large to be parsed completely.
     */
    private class PlaylistTooLarge extends PlaylistException {
        public PlaylistTooLarge(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when the remote playlist format cannot be determined.
     */
    private class PlaylistFormatUnsupported extends PlaylistException {
        public PlaylistFormatUnsupported(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when too many redirects occurred when retrieving a remote playlist.
     */
    private class PlaylistHasTooManyRedirects extends PlaylistException {
        public PlaylistHasTooManyRedirects(String message) {
            super(message);
        }
    }

    public InternetRadioService() {
        this.cachedSources = new HashMap<>();
    }

    /**
     * Clear the radio source cache.
     */
    public void clearInternetRadioSourceCache() {
        cachedSources.clear();
    }

    /**
     * Clear the radio source cache for the given radio id
     * @param internetRadioId a radio id
     */
    public void clearInternetRadioSourceCache(Integer internetRadioId) {
        if (internetRadioId != null) {
            cachedSources.remove(internetRadioId);
        }
    }

    /**
     * Retrieve a list of sources for the given internet radio.
     *
     * This method caches the sources using the InternetRadio.getId
     * method as a key, until clearInternetRadioSourceCache is called.
     *
     * @param radio an internet radio
     * @return a list of internet radio sources
     */
    public List<InternetRadioSource> getInternetRadioSources(InternetRadio radio) {
        List<InternetRadioSource> sources;
        if (cachedSources.containsKey(radio.getId())) {
            LOG.debug("Got cached sources for internet radio {}!", radio.getStreamUrl());
            sources = cachedSources.get(radio.getId());
        } else {
            LOG.debug("Retrieving sources for internet radio {}...", radio.getStreamUrl());
            try {
                sources = retrieveInternetRadioSources(radio);
                if (sources.isEmpty()) {
                    LOG.warn("No entries found for internet radio {}.", radio.getStreamUrl());
                } else {
                    LOG.info("Retrieved playlist for internet radio {}, got {} sources.", radio.getStreamUrl(), sources.size());
                }
            } catch (Exception e) {
                LOG.error("Failed to retrieve sources for internet radio {}.", radio.getStreamUrl(), e);
                sources = new ArrayList<>();
            }
            cachedSources.put(radio.getId(), sources);
        }
        return sources;
    }

    private static final Set<String> DIRECT_PLAYABLE_TYPES = Set.of("audio/mpeg", "audio/aac", "audio/aacp");

    /**
     * Retrieve a list of sources from the given internet radio
     *
     * This method uses a default maximum limit of PLAYLIST_REMOTE_MAX_LENGTH sources.
     *
     * @param radio an internet radio
     * @return a list of internet radio sources
     */
    private List<InternetRadioSource> retrieveInternetRadioSources(InternetRadio radio) throws Exception {
        return retrieveInternetRadioSources(
            radio,
            PLAYLIST_REMOTE_MAX_LENGTH,
            PLAYLIST_REMOTE_MAX_BYTE_SIZE,
            PLAYLIST_REMOTE_MAX_REDIRECTS
        );
    }

    /**
     * Retrieve a list of sources from the given internet radio.
     *
     * @param radio an internet radio
     * @param maxCount the maximum number of items to read from the remote playlist, or 0 if unlimited
     * @param maxByteSize maximum size of the response, in bytes, or 0 if unlimited
     * @param maxRedirects maximum number of redirects, or 0 if unlimited
     * @return a list of internet radio sources
     */
    private List<InternetRadioSource> retrieveInternetRadioSources(InternetRadio radio, int maxCount, long maxByteSize, int maxRedirects) throws Exception {
        String streamUrl = radio.getStreamUrl();
        LOG.debug("Parsing internet radio (playlist) at {}...", streamUrl);

        SpecificPlaylist inputPlaylist = null;
        URL url = URI.create(streamUrl).toURL();
        HttpURLConnection urlConnection = connectToURLWithRedirects(url, maxRedirects);
        try (InputStream in = urlConnection.getInputStream();
                BoundedInputStream bin = BoundedInputStream.builder().setInputStream(in).setMaxCount(maxByteSize).get();) {
            String contentType = urlConnection.getContentType();
            if (contentType != null && DIRECT_PLAYABLE_TYPES.contains(contentType)) {
                //for direct binary streams, just return a collection with a single internet radio source
                LOG.debug("Got direct source media at {}", streamUrl);
                return Collections.singletonList(new InternetRadioSource(streamUrl));
            }
            String contentEncoding = urlConnection.getContentEncoding();
            inputPlaylist = SpecificPlaylistFactory.getInstance().readFrom(bin, contentEncoding);
        } finally {
            urlConnection.disconnect();
        }
        if (inputPlaylist == null) {
            throw new PlaylistFormatUnsupported("Unsupported playlist format " + streamUrl);
        }

        // Retrieve stream URLs
        List<InternetRadioSource> entries = new ArrayList<>();
        try {
            inputPlaylist.toPlaylist().acceptDown(new BasePlaylistVisitor() {
                @Override
                public void beginVisitMedia(Media media) throws Exception {
                    // Since we're dealing with remote content, we place a hard
                    // limit on the maximum number of items to load from the playlist,
                    // in order to avoid parsing erroneous data.
                    if (maxCount > 0 && entries.size() >= maxCount) {
                        throw new PlaylistTooLarge("Remote playlist has too many sources (maximum " + maxCount + ")");
                    }
                    String streamUrl = media.getSource().getURI().toString();
                    LOG.debug("Got source media at {}", streamUrl);
                    entries.add(new InternetRadioSource(streamUrl));
                }
            });
        } catch (PlaylistTooLarge e) {
            // Ignore if playlist is too large, but truncate the rest and log a warning.
            LOG.warn(e.getMessage());
        }

        return entries;
    }

    /**
     * Start a new connection to a remote URL, and follow redirects.
     *
     * @param url the remote URL
     * @param maxRedirects maximum number of redirects, or 0 if unlimited
     * @return an open connection
     */
    protected HttpURLConnection connectToURLWithRedirects(URL url, int maxRedirects) throws IOException, PlaylistException {

        int redirectCount = 0;
        URL currentURL = url;

        // Start a first connection.
        HttpURLConnection connection = connectToURL(currentURL);

        // While it redirects, follow redirects in new connections.
        while (connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM ||
               connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP ||
               connection.getResponseCode() == HttpURLConnection.HTTP_SEE_OTHER) {

            // Check if redirect count is not too large.
            redirectCount += 1;
            if (maxRedirects > 0 && redirectCount > maxRedirects) {
                connection.disconnect();
                throw new PlaylistHasTooManyRedirects(String.format("Too many redirects (%d) for URL %s", redirectCount, url));
            }

            // Reconnect to the new URL.
            currentURL = URI.create(connection.getHeaderField("Location")).toURL();
            connection.disconnect();
            connection = connectToURL(currentURL);
        }

        // Return the last connection that did not redirect.
        return connection;
    }

    /**
     * Retrieve all internet radios. This method is read-only.
     *
     * @return a list of internet radios
     */
    @Transactional(readOnly = true)
    public List<InternetRadio> getAllInternetRadios() {
        return internetRadioRepository.findAll();
    }

    /**
     * Retrieve all enabled internet radios.
     *
     * @return a list of enabled internet radios
     */
    public List<InternetRadio> getEnabledInternetRadios() {
        return internetRadioRepository.findAllByEnabled(true);
    }

    /**
     * Delete an internet radio by its id.
     *
     * @param id an internet radio id
     */
    @Transactional
    public void deleteInternetRadioById(Integer id) {
        internetRadioRepository.findById(id).ifPresentOrElse(radio -> {
            internetRadioRepository.delete(radio);
            LOG.info("Deleted internet radio with id {}", id);
        },
            () -> {
                LOG.warn("Internet radio with id {} not found", id);
            });
    }

    /**
     * Update an internet radio.
     *
     * @param id          an internet radio id
     * @param name        an internet radio name
     * @param streamUrl   an internet radio stream url
     * @param homepageUrl an internet radio homepage url (optional)
     * @param isEnabled   whether the internet radio is enabled
     */
    @Transactional
    public void updateInternetRadio(Integer id, String name, String streamUrl, String homepageUrl, boolean isEnabled) {
        internetRadioRepository.findById(id).ifPresentOrElse(radio -> {
            radio.setName(name);
            radio.setStreamUrl(streamUrl);
            radio.setHomepageUrl(homepageUrl);
            radio.setEnabled(isEnabled);
            radio.setChanged(Instant.now());
            internetRadioRepository.save(radio);
            LOG.info("Updated internet radio with id {}", id);
        },
            () -> {
                LOG.warn("Internet radio with id {} not found", id);
            });
    }

    /**
     * Create an internet radio.
     *
     * @param name        an internet radio name
     * @param streamUrl   an internet radio stream url
     * @param homepageUrl an internet radio homepage url (optional)
     * @param isEnabled   whether the internet radio is enabled
     */
    @Transactional
    public void createInternetRadio(String name, String streamUrl, String homepageUrl, boolean isEnabled) {
        internetRadioRepository.save(new InternetRadio(name, streamUrl, homepageUrl, isEnabled, Instant.now()));
        LOG.info("Created internet radio station with name {}", name);
    }

    /**
     * Start a new connection to a remote URL.
     *
     * @param url the remote URL
     * @return an open connection
     */
    protected HttpURLConnection connectToURL(URL url) throws IOException {
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setAllowUserInteraction(false);
        urlConnection.setConnectTimeout(10000);
        urlConnection.setDoInput(true);
        urlConnection.setDoOutput(false);
        urlConnection.setReadTimeout(60000);
        urlConnection.setUseCaches(true);
        urlConnection.connect();
        return urlConnection;
    }
}
