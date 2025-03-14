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
 */
package org.airsonic.player.controller;

import com.google.common.util.concurrent.RateLimiter;
import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.TransferStatus;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.StatusService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
@WebMvcTest
@ContextConfiguration(classes = {UploadController.class}, initializers = {ConfigDataApplicationContextInitializer.class})
@EnableConfigurationProperties({AirsonicHomeConfig.class})
@SuppressWarnings("unchecked")
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatusService statusService;

    @MockitoBean
    private PlayerService playerService;

    @MockitoBean
    private SettingsService settingsService;

    @MockitoBean
    private SimpMessagingTemplate brokerTemplate;

    @MockitoBean
    private SecurityService securityService;

    @TempDir
    private static Path tempDir;

    @TempDir
    Path tempUploadDir;

    @Mock
    User mockedUser;

    @Mock
    Player mockedPlayer;

    @Mock
    TransferStatus mockedStatus;

    @BeforeAll
    public static void setup() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @AfterAll
    public static void tearDown() {
        System.clearProperty("airsonic.home");
    }

    @ParameterizedTest
    @CsvSource({
        "true, false",
        "true, true",
        "false, true"
    })
    @WithMockUser(username = "user")
    @MockitoSettings(strictness = Strictness.LENIENT)
    void testHandleRequestInternalIfUserHasValidRole(boolean isAdmin, boolean isUploadRole) throws Exception {

        // Mocks
        UUID callback = UUID.randomUUID();
        when(playerService.getPlayer(any(), any(), eq("user"), eq(false), eq(false))).thenReturn(mockedPlayer);
        when(statusService.createUploadStatus(eq(mockedPlayer))).thenReturn(mockedStatus);
        when(mockedStatus.getId()).thenReturn(UUID.randomUUID());
        when(mockedStatus.getBytesTotal()).thenReturn(0L);
        when(mockedStatus.getBytesTransferred()).thenReturn(0L);
        when(mockedStatus.getPlayer()).thenReturn(mockedPlayer);
        when(mockedPlayer.getUsername()).thenReturn("user");
        when(mockedUser.getUsername()).thenReturn("user");
        when(mockedUser.isAdminRole()).thenReturn(isAdmin);
        when(mockedUser.isUploadRole()).thenReturn(isUploadRole);
        when(securityService.getCurrentUser(any())).thenReturn(mockedUser);
        when(settingsService.getUploadBitrateLimiter()).thenReturn(RateLimiter.create(10.0));

        // Create request
        MockMultipartFile file1 = new MockMultipartFile("file", "test.txt", "text/plain", "Test file".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "test2.txt", "text/plain", "Test file 2".getBytes());
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders
                .multipart("/upload")
                .file(file1)
                .file(file2)
                .with(csrf())
                .param("dir", tempUploadDir.resolve("test").toString())
                .param("unzip", "false")
                .param("callback", callback.toString());

        // Perform request
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(view().name("upload"))
                .andExpect(model().attributeExists("model"))
                .andReturn();
        Map<String, List<Object>> model = (Map<String, List<Object>>) result.getModelAndView().getModel().get("model");

        // Check that model contains expected values
        assertEquals(List.of(), model.get("exceptions"));
        assertEquals(List.of(), model.get("unzippedFiles"));
        assertEquals(2, model.get("uploadedFiles").size());
        assertTrue(model.get("uploadedFiles").contains(tempUploadDir.resolve("test").resolve("test.txt")));
        assertTrue(model.get("uploadedFiles").contains(tempUploadDir.resolve("test").resolve("test2.txt")));

        // Check that files were uploaded
        assertTrue(Files.exists(tempUploadDir.resolve("test").resolve("test.txt")));
        assertTrue(Files.exists(tempUploadDir.resolve("test").resolve("test2.txt")));
    }

    @Test
    @WithMockUser(username = "user")
    @MockitoSettings(strictness = Strictness.STRICT_STUBS)
    void testHandleRequestInternalIfUserDoesNotHaveValidRole() throws Exception {

        // Mocks
        UUID callback = UUID.randomUUID();
        when(playerService.getPlayer(any(), any(), eq("user"), eq(false), eq(false))).thenReturn(mockedPlayer);
        when(statusService.createUploadStatus(eq(mockedPlayer))).thenReturn(mockedStatus);
        when(mockedStatus.getId()).thenReturn(UUID.randomUUID());
        when(mockedStatus.getBytesTotal()).thenReturn(0L);
        when(mockedStatus.getBytesTransferred()).thenReturn(0L);
        when(mockedStatus.getPlayer()).thenReturn(mockedPlayer);
        when(mockedPlayer.getUsername()).thenReturn("user");
        when(mockedUser.getUsername()).thenReturn("user");
        when(mockedUser.isAdminRole()).thenReturn(false);
        when(mockedUser.isUploadRole()).thenReturn(false);
        when(securityService.getCurrentUser(any())).thenReturn(mockedUser);
        when(settingsService.getUploadBitrateLimiter()).thenReturn(RateLimiter.create(10.0));

        // Create request
        MockMultipartFile file1 = new MockMultipartFile("file", "test.txt", "text/plain", "Test file".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "test2.txt", "text/plain", "Test file 2".getBytes());
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders
                .multipart("/upload")
                .file(file1)
                .file(file2)
                .with(csrf())
                .param("dir", tempUploadDir.resolve("test").toString())
                .param("unzip", "false")
                .param("callback", callback.toString());

        // Perform request
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(view().name("upload"))
                .andExpect(model().attributeExists("model"))
                .andReturn();
        Map<String, List<Object>> model = (Map<String, List<Object>>) result.getModelAndView().getModel().get("model");

        // Check that model contains expected values
        assertEquals(1, model.get("exceptions").size());
        assertEquals("User does not have privileges to upload", model.get("exceptions").get(0));
        assertEquals(List.of(), model.get("unzippedFiles"));
        assertEquals(List.of(), model.get("uploadedFiles"));

        // Check that folder was not created
        assertFalse(Files.exists(tempUploadDir.resolve("test")));
    }

    @ParameterizedTest
    @WithMockUser(username = "user")
    @MockitoSettings(strictness = Strictness.LENIENT)
    @CsvSource({
        "false, test",
        "false, ../test_airsonic",
        "true, ../../test_airsonic"
    })
    void testHandleRequestInternalIfFolderIsNotAllowedToUpload(boolean isAdmin, String uploadFolder) throws Exception {

        Path uploadPath = tempDir.resolve(uploadFolder).resolve(tempUploadDir.getFileName()).normalize();

        // Mocks
        UUID callback = UUID.randomUUID();
        when(playerService.getPlayer(any(), any(), eq("user"), eq(false), eq(false))).thenReturn(mockedPlayer);
        when(statusService.createUploadStatus(eq(mockedPlayer))).thenReturn(mockedStatus);
        when(mockedStatus.getId()).thenReturn(UUID.randomUUID());
        when(mockedStatus.getBytesTotal()).thenReturn(0L);
        when(mockedStatus.getBytesTransferred()).thenReturn(0L);
        when(mockedStatus.getPlayer()).thenReturn(mockedPlayer);
        when(mockedPlayer.getUsername()).thenReturn("user");
        when(mockedUser.getUsername()).thenReturn("user");
        when(mockedUser.isAdminRole()).thenReturn(isAdmin);
        when(mockedUser.isUploadRole()).thenReturn(true);
        when(securityService.getCurrentUser(any())).thenReturn(mockedUser);
        doThrow(new AccessDeniedException(uploadPath.toString(), null, "Specified location is not in writable music folder")).when(securityService).checkUploadAllowed(eq(uploadPath), eq(false));
        when(settingsService.getUploadBitrateLimiter()).thenReturn(RateLimiter.create(10.0));

        // Create request
        MockMultipartFile file1 = new MockMultipartFile("file", "test.txt", "text/plain", "Test file".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "test2.txt", "text/plain", "Test file 2".getBytes());
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders
                .multipart("/upload")
                .file(file1)
                .file(file2)
                .with(csrf())
                .param("dir", uploadPath.toString())
                .param("unzip", "false")
                .param("callback", callback.toString());

        // Perform request
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(view().name("upload"))
                .andExpect(model().attributeExists("model"))
                .andReturn();
        Map<String, List<Object>> model = (Map<String, List<Object>>) result.getModelAndView().getModel().get("model");

        // Check that model contains expected values
        assertEquals(1, model.get("exceptions").size());
        assertEquals(uploadPath.toString() + ": Specified location is not in writable music folder", model.get("exceptions").get(0));
        assertEquals(List.of(), model.get("unzippedFiles"));
        assertEquals(List.of(), model.get("uploadedFiles"));

        // Check that folder was not created
        assertFalse(Files.exists(uploadPath));
    }

    @Test
    @WithMockUser(username = "user")
    @MockitoSettings(strictness = Strictness.STRICT_STUBS)
    void testHandleRequestInternalIfFolderIsAccessDeniedButUploadable() throws Exception {

        Path uploadPath = tempDir.resolve("test").normalize();

        // Mocks
        UUID callback = UUID.randomUUID();
        when(playerService.getPlayer(any(), any(), eq("user"), eq(false), eq(false))).thenReturn(mockedPlayer);
        when(statusService.createUploadStatus(eq(mockedPlayer))).thenReturn(mockedStatus);
        when(mockedStatus.getId()).thenReturn(UUID.randomUUID());
        when(mockedStatus.getBytesTotal()).thenReturn(0L);
        when(mockedStatus.getBytesTransferred()).thenReturn(0L);
        when(mockedStatus.getPlayer()).thenReturn(mockedPlayer);
        when(mockedPlayer.getUsername()).thenReturn("user");
        when(mockedUser.getUsername()).thenReturn("user");
        when(mockedUser.isAdminRole()).thenReturn(true);
        when(securityService.getCurrentUser(any())).thenReturn(mockedUser);
        doThrow(new AccessDeniedException(uploadPath.toString(), null, "Specified location is not in writable music folder")).when(securityService).checkUploadAllowed(eq(uploadPath), eq(false));
        when(settingsService.getUploadBitrateLimiter()).thenReturn(RateLimiter.create(10.0));

        // Create request
        MockMultipartFile file1 = new MockMultipartFile("file", "test.txt", "text/plain", "Test file".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "test2.txt", "text/plain", "Test file 2".getBytes());
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders
                .multipart("/upload")
                .file(file1)
                .file(file2)
                .with(csrf())
                .param("dir", uploadPath.toString())
                .param("unzip", "false")
                .param("callback", callback.toString());

        // Perform request
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(view().name("upload"))
                .andExpect(model().attributeExists("model"))
                .andReturn();
        Map<String, List<Object>> model = (Map<String, List<Object>>) result.getModelAndView().getModel().get("model");

        // Check that model contains expected values
        assertEquals(List.of(), model.get("exceptions"));
        assertEquals(List.of(), model.get("unzippedFiles"));
        assertEquals(2, model.get("uploadedFiles").size());
        assertTrue(model.get("uploadedFiles").contains(uploadPath.resolve("test.txt")));
        assertTrue(model.get("uploadedFiles").contains(uploadPath.resolve("test2.txt")));

        // Check that files were uploaded
        assertTrue(Files.exists(uploadPath.resolve("test.txt")));
        assertTrue(Files.exists(uploadPath.resolve("test2.txt")));
    }

    @Test
    @WithMockUser(username = "user")
    @MockitoSettings(strictness = Strictness.STRICT_STUBS)
    void testHandleRequestInternalIfFileAlreadyExist() throws Exception {

        Path uploadPath = tempUploadDir.resolve("test.txt");

        // Mocks
        UUID callback = UUID.randomUUID();
        when(playerService.getPlayer(any(), any(), eq("user"), eq(false), eq(false))).thenReturn(mockedPlayer);
        when(statusService.createUploadStatus(eq(mockedPlayer))).thenReturn(mockedStatus);
        when(mockedStatus.getId()).thenReturn(UUID.randomUUID());
        when(mockedStatus.getBytesTotal()).thenReturn(0L);
        when(mockedStatus.getBytesTransferred()).thenReturn(0L);
        when(mockedStatus.getPlayer()).thenReturn(mockedPlayer);
        when(mockedPlayer.getUsername()).thenReturn("user");
        when(mockedUser.getUsername()).thenReturn("user");
        when(mockedUser.isAdminRole()).thenReturn(true);
        when(securityService.getCurrentUser(any())).thenReturn(mockedUser);
        when(settingsService.getUploadBitrateLimiter()).thenReturn(RateLimiter.create(10.0));
        doThrow(new FileAlreadyExistsException(uploadPath.toString(), null, "File already exists")).when(securityService).checkUploadAllowed(eq(uploadPath), eq(true));

        // Create request
        MockMultipartFile file1 = new MockMultipartFile("file", "test.txt", "text/plain", "Test file".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "test2.txt", "text/plain", "Test file 2".getBytes());
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders
                .multipart("/upload")
                .file(file1)
                .file(file2)
                .with(csrf())
                .param("dir", tempUploadDir.toString())
                .param("unzip", "false")
                .param("callback", callback.toString());

        // Perform request
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(view().name("upload"))
                .andExpect(model().attributeExists("model"))
                .andReturn();
        Map<String, List<Object>> model = (Map<String, List<Object>>) result.getModelAndView().getModel().get("model");

        // Check that model contains expected values
        assertEquals(1, model.get("exceptions").size());
        assertEquals(uploadPath.toString() + ": File already exists", model.get("exceptions").get(0));
        assertEquals(List.of(), model.get("unzippedFiles"));
        assertEquals(1, model.get("uploadedFiles").size());
        assertTrue(model.get("uploadedFiles").contains(tempUploadDir.resolve("test2.txt")));

        // Check that files were uploaded
        assertFalse(Files.exists(uploadPath.resolve("test.txt")));
        assertTrue(Files.exists(tempUploadDir.resolve("test2.txt")));
    }

    @ParameterizedTest
    @WithMockUser(username = "user")
    @ValueSource(strings = {"test.zip", "test.7z", "test.tar","test.cpio", "test.rar", "test.jar" })
    @MockitoSettings(strictness = Strictness.STRICT_STUBS)
    void testHandleRequestInternalWithArchiveFile(String fileName) throws Exception {

        ClassPathResource resource = new ClassPathResource(Paths.get("archives", fileName).toString());

        // Mocks
        UUID callback = UUID.randomUUID();
        when(playerService.getPlayer(any(), any(), eq("user"), eq(false), eq(false))).thenReturn(mockedPlayer);
        when(statusService.createUploadStatus(eq(mockedPlayer))).thenReturn(mockedStatus);
        when(mockedStatus.getId()).thenReturn(UUID.randomUUID());
        when(mockedStatus.getBytesTotal()).thenReturn(0L);
        when(mockedStatus.getBytesTransferred()).thenReturn(0L);
        when(mockedStatus.getPlayer()).thenReturn(mockedPlayer);
        when(mockedPlayer.getUsername()).thenReturn("user");
        when(mockedUser.getUsername()).thenReturn("user");
        when(mockedUser.isAdminRole()).thenReturn(true);
        when(securityService.getCurrentUser(any())).thenReturn(mockedUser);
        when(settingsService.getUploadBitrateLimiter()).thenReturn(RateLimiter.create(1000.0));

        // Create request
        MockMultipartFile file1 = new MockMultipartFile("file", fileName, "text/plain", resource.getInputStream().readAllBytes());
        MockMultipartFile file2 = new MockMultipartFile("file", "test2.txt", "text/plain", "Test file 2".getBytes());
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders
                .multipart("/upload")
                .file(file1)
                .file(file2)
                .with(csrf())
                .param("dir", tempUploadDir.toString())
                .param("unzip", "true")
                .param("callback", callback.toString());

        // Perform request
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(view().name("upload"))
                .andExpect(model().attributeExists("model"))
                .andReturn();
        Map<String, List<Object>> model = (Map<String, List<Object>>) result.getModelAndView().getModel().get("model");

        // Check that model contains expected values
        assertEquals(List.of(), model.get("exceptions"));
        assertEquals(2, model.get("uploadedFiles").size());
        assertTrue(model.get("unzippedFiles").contains(tempUploadDir.resolve("test").resolve("test.txt")));
        assertTrue(model.get("uploadedFiles").contains(tempUploadDir.resolve("test2.txt")));
        assertTrue(model.get("uploadedFiles").contains(tempUploadDir.resolve(fileName)));

        // Check that files were uploaded
        assertFalse(Files.exists(tempUploadDir.resolve(fileName)));
        assertTrue(Files.exists(tempUploadDir.resolve("test").resolve("test.txt")));
        assertTrue(Files.exists(tempUploadDir.resolve("test2.txt")));
    }




}
