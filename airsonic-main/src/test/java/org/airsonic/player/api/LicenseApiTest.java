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

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.airsonic.player.domain.Player;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import jakarta.transaction.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
public class LicenseApiTest extends AbstractRESTTest {

    private static final String CLIENT_NAME = "licenseApiTest";

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getLicense", "/rest/getLicense.view"})
    void getLicense_returnsValidLicense(String endpoint) throws Exception {
        String response = mvc.perform(get(endpoint)
            .param("v", AIRSONIC_API_VERSION)
            .param("c", CLIENT_NAME)
            .param("u", AIRSONIC_USER)
            .param("p", AIRSONIC_PASSWORD)
            .param("f", EXPECTED_FORMAT)
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
            .andExpect(jsonPath("$.subsonic-response.version").value(AIRSONIC_API_VERSION))
            .andDo(print())
            .andReturn().getResponse().getContentAsString();

        DocumentContext json = JsonPath.parse(response);
        String licenseEmail = json.read("$.subsonic-response.license.email");
        boolean licenseValid = json.read("$.subsonic-response.license.valid");

        // assertions
        assertEquals("airsonic@github.com",licenseEmail);
        assertTrue(licenseValid);
        assertTrue(response.contains("licenseExpires"));
        assertTrue(response.contains("trialExpires"));

        // Check if the players list is not empty
        List<Player> players = playerService.getPlayersForUserAndClientId(AIRSONIC_USER, CLIENT_NAME);
        assertEquals(1, players.size());
        Player player = players.get(0);
        WrapRequestUtil.assertRestAPIPlayer(player, CLIENT_NAME, AIRSONIC_USER);
    }
}
