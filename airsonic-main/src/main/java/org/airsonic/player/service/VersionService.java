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
package org.airsonic.player.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.airsonic.player.domain.Version;
import org.airsonic.player.domain.dto.GitHubRelease;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

/**
 * Provides version-related services, including functionality for determining whether a newer
 * version of Airsonic is available.
 *
 * @author Sindre Mehus
 */
@Service
public class VersionService {
    private static final Logger LOG = LoggerFactory.getLogger(VersionService.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("UTC"));

    private final Properties build;

    private Version localVersion;
    private Version latestFinalVersion;
    private Version latestBetaVersion;
    private Instant localBuildDate;
    private String localBuildNumber;

    public VersionService() throws IOException {
        build = PropertiesLoaderUtils.loadAllProperties("build.properties");
    }

    /**
     * Time when latest version was fetched (in milliseconds).
     */
    private long lastVersionFetched;

    /**
     * Only fetch last version this often (in milliseconds.).
     */
    private static final long LAST_VERSION_FETCH_INTERVAL = 7L * 24L * 3600L * 1000L; // One week

    /**
     * Returns the version number for the locally installed Airsonic version.
     *
     * @return The version number for the locally installed Airsonic version.
     */
    public Version getLocalVersion() {
        if (localVersion == null) {
            try {
                localVersion = new Version(build.getProperty("version") + "." + build.getProperty("timestamp"));
                LOG.info("Resolved local Airsonic version to: {}", localVersion);
            } catch (Exception x) {
                LOG.warn("Failed to resolve local Airsonic version.", x);
            }
        }
        return localVersion;
    }

    /**
     * Returns the version number for the latest available Airsonic final version.
     *
     * @return The version number for the latest available Airsonic final version, or <code>null</code>
     *         if the version number can't be resolved.
     */
    public synchronized Version getLatestFinalVersion() {
        refreshLatestVersion();
        return latestFinalVersion;
    }

    /**
     * Returns the version number for the latest available Airsonic beta version.
     *
     * @return The version number for the latest available Airsonic beta version, or <code>null</code>
     *         if the version number can't be resolved.
     */
    public synchronized Version getLatestBetaVersion() {
        refreshLatestVersion();
        return latestBetaVersion;
    }

    /**
     * Returns the build date for the locally installed Airsonic version.
     *
     * @return The build date for the locally installed Airsonic version, or <code>null</code>
     *         if the build date can't be resolved.
     */
    public Instant getLocalBuildDate() {
        if (localBuildDate == null) {
            try {
                String date = build.getProperty("timestamp");
                localBuildDate = DATE_FORMAT.parse(date, Instant::from);
            } catch (Exception x) {
                LOG.warn("Failed to resolve local Airsonic build date.", x);
            }
        }
        return localBuildDate;
    }

    /**
     * Returns the build number for the locally installed Airsonic version.
     *
     * @return The build number for the locally installed Airsonic version, or <code>null</code>
     *         if the build number can't be resolved.
     */
    public String getLocalBuildNumber() {
        if (localBuildNumber == null) {
            try {
                localBuildNumber = build.getProperty("revision");
            } catch (Exception x) {
                LOG.warn("Failed to resolve local Airsonic build commit.", x);
            }
        }
        return localBuildNumber;
    }

    /**
     * Returns whether a new final version of Airsonic is available.
     *
     * @return Whether a new final version of Airsonic is available.
     */
    public boolean isNewFinalVersionAvailable() {
        Version latest = getLatestFinalVersion();
        Version local = getLocalVersion();

        if (latest == null || local == null) {
            return false;
        }

        return local.compareTo(latest) < 0;
    }

    /**
     * Returns whether a new beta version of Airsonic is available.
     *
     * @return Whether a new beta version of Airsonic is available.
     */
    public boolean isNewBetaVersionAvailable() {
        Version latest = getLatestBetaVersion();
        Version local = getLocalVersion();

        if (latest == null || local == null) {
            return false;
        }

        return local.compareTo(latest) < 0;
    }

    /**
     * Refreshes the latest final and beta versions.
     */
    private void refreshLatestVersion() {
        long now = System.currentTimeMillis();
        boolean isOutdated = now - lastVersionFetched > LAST_VERSION_FETCH_INTERVAL;

        if (isOutdated) {
            try {
                lastVersionFetched = now;
                readLatestVersion();
            } catch (Exception x) {
                LOG.warn("Failed to resolve latest Airsonic version.", x);
            }
        }
    }

    private static final String VERSION_URL = "https://api.github.com/repos/airsonic-ce/airsonic-ce/releases";

    private static Function<GitHubRelease, Version> releaseToVersionMapper = r ->
            new Version(
                    r.getTagName(),
                    r.getTargetCommitish(),
                    r.isDraft() || r.isPrerelease(),
                    r.getHtmlUrl(),
                    r.getPublishedAt(),
                    r.getCreatedAt(),
                    r.getAssets()
                    );

    /**
     * Resolves the latest available Airsonic version by inspecting github.
     */
    private void readLatestVersion() throws IOException {

        LOG.debug("Starting to read latest version");
        // Set up the RestClient to fetch the latest version from GitHub
        // Use HttpComponentsClientHttpRequestFactory for better control over timeouts
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(10000);
        // Set up the ObjectMapper to handle Java 8 time types
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModules(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        RestClient restClient = RestClient.builder()
            .defaultHeaders(
                httpHeaders -> {
                    httpHeaders.set("Accept", "application/vnd.github.v3+json");
                    httpHeaders.set("User-Agent", "Airsonic/" + getLocalVersion());
                }
            )
            .messageConverters(converters -> {
                converters.clear();
                converters.add(converter);
            })
            .baseUrl(VERSION_URL)
            .requestFactory(factory)
            .build();
        List<GitHubRelease> releases = new ArrayList<>();
        int maxRetries = 3;
        long backoffMillis = 2000;
        for (int i = 1; i <= 5; i++) { // Limit to 5 pages to avoid infinite loops
            final int pageNum = i;
            int attempt = 0;
            while (attempt < maxRetries) {
                try {
                    List<GitHubRelease> response = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                            .queryParam("per_page", "100")
                            .queryParam("page", String.valueOf(pageNum))
                            .build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<GitHubRelease>>() {});
                    if (response.isEmpty()) {
                        break;
                    }
                    releases.addAll(response);
                    break; // Success, exit retry loop
                } catch (Exception e) {
                    attempt++;
                    if (attempt >= maxRetries) {
                        LOG.warn("Failed to fetch page {} from GitHub after {} attempts: {}", pageNum, attempt, e.getMessage());
                        break;
                    }
                    try {
                        Thread.sleep(backoffMillis);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // Sort releases by published date
        releases.sort((a, b) -> {
            if (a.getPublishedAt() == null || b.getPublishedAt() == null) {
                return 0; // Can't compare, so treat as equal
            }
            return b.getPublishedAt().compareTo(a.getPublishedAt()); // Descending order
        });

        Optional<GitHubRelease> betaR = releases.stream().findFirst();
        Optional<GitHubRelease> finalR = releases.stream().filter(x -> !(x.isDraft()) && !(x.isPrerelease())).findFirst();
        Optional<GitHubRelease> currentR = releases.stream().filter(x ->
            Strings.CS.equals(build.getProperty("version") + "." + build.getProperty("timestamp"), x.getTagName()) ||
            Strings.CS.equals(build.getProperty("version"), x.getTagName())).findAny();

        LOG.debug("Got {} for beta version", betaR.map(GitHubRelease::getTagName).orElse(null));
        LOG.debug("Got {} for final version", finalR.map(GitHubRelease::getTagName).orElse(null));

        latestBetaVersion = betaR.map(releaseToVersionMapper).orElse(null);
        latestFinalVersion = finalR.map(releaseToVersionMapper).orElse(null);
        localVersion = currentR.map(releaseToVersionMapper).orElse(localVersion);
    }
}
