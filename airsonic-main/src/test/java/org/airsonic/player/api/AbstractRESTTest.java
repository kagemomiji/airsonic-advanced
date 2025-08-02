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

 Copyright 2025 (C) Y.Tory
 */
package org.airsonic.player.api;

import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.service.LibraryStatusService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractRESTTest {

    protected static final String AIRSONIC_USER = "admin";
    protected static final String AIRSONIC_PASSWORD = "admin";
    protected static final String EXPECTED_FORMAT = "json";

    protected static String AIRSONIC_API_VERSION = TestCaseUtils.restApiVersion();

    protected MusicFolder testFolder = new MusicFolder(1, Paths.get("/test/folder"), "Test Folder", MusicFolder.Type.MEDIA, true, Instant.now());

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected PlayerService playerService;

    @MockitoBean
    protected MediaFolderService mediaFolderService;

    @MockitoBean
    protected LibraryStatusService libraryStatusService;

    @TempDir
    private static Path tempAirsonicHome;

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("airsonic.home", tempAirsonicHome.toString());
    }

}
