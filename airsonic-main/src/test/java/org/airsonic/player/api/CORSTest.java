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

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


public class CORSTest extends AbstractRESTTest {

    private static final String CLIENT_NAME = "airsonic";

    @Test
    public void corsHeadersShouldBeAddedToSuccessResponses() throws Exception {
        mvc.perform(get("/rest/ping")
                .param("v", AIRSONIC_API_VERSION)
                .param("c", CLIENT_NAME)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("f", EXPECTED_FORMAT)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    @Test
    public void corsHeadersShouldBeAddedToErrorResponses() throws Exception {
        mvc.perform(get("/rest/ping")
                .header("Access-Control-Request-Method", "GET")
                .header("Origin", "https://example.com")
                .param("v", AIRSONIC_API_VERSION)
                .param("c", CLIENT_NAME)
                .param("u", AIRSONIC_USER)
                .param("p", "incorrect password")
                .param("f", EXPECTED_FORMAT)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.subsonic-response.status").value("failed"))
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    public void corsShouldNotBeEnabledForOtherPaths() throws Exception {
        mvc.perform(get("/login")
                .header("Access-Control-Request-Method", "GET")
                .header("Origin", "https://example.com"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    public void testOptionRequest() throws Exception {
        mvc.perform(options("/rest/ping")
                .header("Access-Control-Request-Method", "GET")
                .header("Origin", "https://example.com")
                .param("v", AIRSONIC_API_VERSION)
                .param("c", CLIENT_NAME)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

}
