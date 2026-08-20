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
package org.airsonic.player.service.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.util.Util;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parses meta data from video files using FFmpeg (http://ffmpeg.org/).
 * <p/>
 * Currently duration, bitrate and dimension are supported.
 *
 * @author Sindre Mehus
 */
@Service("ffmpegParser")
@Order(100)
public class FFmpegParser extends MetaDataParser {

    private static final Logger LOG = LoggerFactory.getLogger(FFmpegParser.class);
    private static final String[] FFPROBE_OPTIONS = {
        "-v", "quiet", "-print_format", "json", "-show_format", "-show_streams", "-show_chapters"
    };

    @Autowired
    private MediaFolderService mediaFolderService;

    @Autowired
    private SettingsService settingsService;

    /**
     * Parses meta data for the given music file. No guessing or reformatting is done.
     *
     *
     * @param file The music file to parse.
     * @return Meta data for the file.
     */
    @Override
    public MetaData getRawMetaData(Path file) {

        MetaData metaData = new MetaData();

        try {
            // Use `ffprobe` in the transcode directory if it exists, otherwise let the system sort it out.
            String ffprobe = settingsService.resolveTranscodeExecutable("ffprobe", "ffprobe");

            List<String> command = new ArrayList<>();
            command.add(ffprobe);
            command.addAll(Arrays.asList(FFPROBE_OPTIONS));
            command.add(file.toAbsolutePath().toString());

            Process process = Runtime.getRuntime().exec(command.toArray(new String[0]));
            JsonNode result = null;
            try (InputStream in = process.getInputStream();
                BufferedInputStream bin = new BufferedInputStream(in); ) {
                result = Util.getObjectMapper().readTree(bin);
            }

            metaData.setDuration(result.at("/format/duration").asDouble());
            // Bitrate is in Kb/s
            metaData.setBitRate(result.at("/format/bit_rate").asInt() / 1000);

            metaData.setAlbumArtist(getData(result, "album_artist"));
            metaData.setArtist(getData(result, "artist"));
            metaData.setAlbumName(getData(result, "album"));
            metaData.setGenre(getData(result, "genre"));
            metaData.setTitle(getData(result, "title"));
            String data = getData(result, "track");
            if (data != null) {
                data = data.replaceFirst("^[\\s\\p{C}]*0+(?!$)", "");
                if (NumberUtils.isCreatable(data)) {
                    metaData.setTrackNumber(NumberUtils.createInteger(data));
                }
            }
            data = getData(result, "disc");
            if (data != null) {
                data = data.replaceFirst("^[\\s\\p{C}]*0+(?!$)", "");
                if (NumberUtils.isCreatable(data)) {
                    metaData.setDiscNumber(NumberUtils.createInteger(data));
                }
            }

            data = getData(result, "discnumber");
            if (data != null) {
                data = data.replaceFirst("^[\\s\\p{C}]*0+(?!$)", "");
                if (NumberUtils.isCreatable(data)) {
                    metaData.setDiscNumber(NumberUtils.createInteger(data));
                }
            }
            data = getData(result, "date");
            if (NumberUtils.isCreatable(data)) {
                metaData.setYear(NumberUtils.createInteger(data));
            }

            // Find the first (if any) stream that has dimensions and use those.
            // 'width' and 'height' are display dimensions; compare to 'coded_width', 'coded_height'.
            for (JsonNode stream : result.at("/streams")) {
                JsonNode indexNode = stream.get("index");
                JsonNode codecTypeNode = stream.get("codec_type");
                JsonNode codecNameNode = stream.get("codec_name");
                if (indexNode == null || codecTypeNode == null || codecNameNode == null) {
                    continue;
                }
                Track track = new Track(indexNode.asInt(), codecTypeNode.asText(), stream.at("/tags/language").asText(), codecNameNode.asText());
                metaData.addTrack(track);

                if (track.isVideo() && stream.has("width") && stream.has("height")) {
                    metaData.setWidth(stream.get("width").asInt());
                    metaData.setHeight(stream.get("height").asInt());
                }
            }
            ObjectMapper mapper = Util.getObjectMapper();
            for (JsonNode chapterJson : result.at("/chapters")) {
                Chapter chapter = mapper.convertValue(chapterJson, Chapter.class);
                metaData.addChapter(chapter);
            }
        } catch (Throwable x) {
            LOG.warn("Error when parsing metadata in {}", file, x);
        }

        return metaData;
    }

    private static String getData(JsonNode node, String keyName) {
        // Create a list of key variations to handle different cases
        List<String> keyVariations = ImmutableList.of(
            keyName.toLowerCase(),
            keyName.toUpperCase(),
            StringUtils.capitalize(keyName)  // Capitalizes only the first letter
        );
        // Try to find data in /format/tags/ with different key cases
        for (String key : keyVariations) {
            String path = "/format/tags/" + key;
            String value = node.at(path).asText();
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        // If not found in /format/tags/, check each stream's tags
        if (node.has("streams")) {
            for (JsonNode stream : node.at("/streams")) {
                for (String key : keyVariations) {
                    String tagPath = "/tags/" + key;
                    String value = stream.at(tagPath).asText();
                    if (StringUtils.isNotBlank(value)) {
                        return value;
                    }
                }
            }
        }
        return null;  // Return null if no matching data is found
    }

    /**
     * Not supported.
     */
    @Override
    public void setMetaData(MediaFile file, MetaData metaData) {
        throw new RuntimeException("setMetaData() not supported in " + getClass().getSimpleName());
    }

    /**
     * Returns whether this parser supports tag editing (using the {@link #setMetaData} method).
     *
     * @return Always false.
     */
    @Override
    public boolean isEditingSupported() {
        return false;
    }

    /**
     * Returns whether this parser is applicable to the given file.
     *
     * @param path The path to file in question.
     * @return Whether this parser is applicable to the given file.
     */
    @Override
    public boolean isApplicable(Path path) {
        return Files.isRegularFile(path);
    }

    @Override
    MediaFolderService getMediaFolderService() {
        return mediaFolderService;
    }
}
