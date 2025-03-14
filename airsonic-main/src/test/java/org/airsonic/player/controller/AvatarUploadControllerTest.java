package org.airsonic.player.controller;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.Avatar;
import org.airsonic.player.service.PersonalSettingsService;
import org.airsonic.player.service.SecurityService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
@WebMvcTest
@ContextConfiguration(classes = {AvatarUploadController.class}, initializers = ConfigDataApplicationContextInitializer.class)
@EnableConfigurationProperties(AirsonicHomeConfig.class)
@SuppressWarnings("unchecked")
public class AvatarUploadControllerTest {

    private static final String USERNAME = "testuser";
    private static final String FILE_NAME = "testimage.png";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PersonalSettingsService personalSettingsService;

    @MockitoBean
    private SecurityService securityService;

    @TempDir
    private static Path tempDir;

    @BeforeAll
    public static void setup() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @AfterAll
    public static void teardown() {
        System.clearProperty("airsonic.home");
    }


    @BeforeEach
    void setUp() {
        when(securityService.getCurrentUsername(any())).thenReturn(USERNAME);
    }

    @Test
    @WithMockUser(username = USERNAME)
    void handleRequestInternalShouldReturnErrorWhenNoFileSpecified() throws Exception {
        // given
        MockMultipartFile multipartFile = new MockMultipartFile("file", FILE_NAME, "text/plain", "".getBytes());

        // execute
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.multipart("/avatarUpload").file(multipartFile).with(csrf());
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(view().name("avatarUploadResult"))
                .andReturn();
        Map<String, Object> actual = (Map<String, Object>) result.getModelAndView().getModel().get("model");

        // assert
        assertNull(actual.get("avatar"));
        assertEquals("Missing file.", ((Exception)actual.get("error")).getMessage());
        assertEquals(USERNAME, actual.get("username"));
    }

    @ParameterizedTest
    @WithMockUser(username = USERNAME)
    @ValueSource(strings = {"small.png", "large.png", "small.jpg", "large.jpg", "small.gif", "large.gif", "small.bmp", "large.bmp"})
    void handleRequestInternalShouldUploadImageAndReturnModel(String fileName) throws Exception {
        // given
        ClassPathResource resource = new ClassPathResource(Paths.get("avatars", fileName).toString());
        byte[] imageData = resource.getInputStream().readAllBytes();
        MockMultipartFile multipartFile = new MockMultipartFile("file", FILE_NAME, "text/plain", imageData);
        Avatar expectedAvatar = new Avatar(0, USERNAME, Instant.now(), fileName, 0, 0, tempDir);
        when(personalSettingsService.getCustomAvatar(eq(USERNAME))).thenReturn(expectedAvatar);

        // when
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.multipart("/avatarUpload")
                .file(multipartFile)
                .with(csrf());
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(view().name("avatarUploadResult"))
                .andReturn();

        Map<String, Object> actual = (Map<String, Object>) result.getModelAndView().getModel().get("model");

        // assert
        assertNull(actual.get("error"));
        assertEquals(expectedAvatar, actual.get("avatar"));
        assertEquals(USERNAME, actual.get("username"));
        verify(personalSettingsService).createCustomAvatar(eq(FILE_NAME), eq(imageData), eq(USERNAME));
    }

}