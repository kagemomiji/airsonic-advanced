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
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.api;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.airsonic.player.domain.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.transaction.Transactional;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
public class MusicFolderAPITest extends AbstractRESTTest {

    private static final String CLIENT_NAME = "musicFolderApiTest";

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getMusicFolders", "/rest/getMusicFolders.view"})
    public void getMusicFolders_returnsFolders(String endpoint) throws Exception {
        when(mediaFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER))).thenReturn(List.of(testFolder));
        String response = mvc.perform(get(endpoint)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", CLIENT_NAME)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("f", EXPECTED_FORMAT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                .andExpect(jsonPath("$.subsonic-response.version").value(AIRSONIC_API_VERSION))
                .andDo(print())
                .andReturn().getResponse().getContentAsString();

        DocumentContext json = JsonPath.parse(response);
        List<String> folderNames = json.read("$.subsonic-response.musicFolders.musicFolder[*].name");
        List<Integer> folderIds = json.read("$.subsonic-response.musicFolders.musicFolder[*].id");
        assertEquals(1, folderIds.size());
        assertEquals(1, folderNames.size());
        assertEquals(testFolder.getName(), folderNames.get(0));
        assertEquals(testFolder.getId(), folderIds.get(0));

        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, CLIENT_NAME);
        assertEquals(1, players.size());
        Player player = players.get(0);
        WrapRequestUtil.assertRestAPIPlayer(player, CLIENT_NAME, AIRSONIC_USER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getMusicFolders", "/rest/getMusicFolders.view"})
    public void getMusicFolders_emptyList(String endpoint) throws Exception {
        when(mediaFolderService.getMusicFoldersForUser(eq(AIRSONIC_USER))).thenReturn(Collections.emptyList());
        mvc.perform(get(endpoint)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", CLIENT_NAME)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("f", EXPECTED_FORMAT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
                .andExpect(jsonPath("$.subsonic-response.musicFolders").isEmpty())
                .andDo(print());

        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, CLIENT_NAME);
        assertEquals(1, players.size());
        Player player = players.get(0);
        WrapRequestUtil.assertRestAPIPlayer(player, CLIENT_NAME, AIRSONIC_USER);
    }
}
