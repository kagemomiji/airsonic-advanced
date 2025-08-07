package org.airsonic.player.startup;

import org.airsonic.player.config.StartupOptimizationConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to validate that startup optimizations are working correctly.
 * This test ensures the application can start with the new lazy initialization
 * and other optimization configurations.
 */
@SpringBootTest(
    classes = StartupOptimizationConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
    "spring.main.lazy-initialization=true",
    "spring.jpa.defer-datasource-initialization=true",
    "spring.datasource.url=jdbc:hsqldb:mem:testdb",
    "spring.datasource.driver-class-name=org.hsqldb.jdbc.JDBCDriver",
    "spring.liquibase.enabled=false"
})
public class StartupOptimizationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeAll
    public static void setUp() throws IOException {
        Path tempDir = Files.createTempDirectory("airsonic-startup-test");
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @Test
    public void contextLoadsWithOptimizations() {
        // This test validates that the Spring context can load successfully
        // with all the startup optimizations enabled
        assertNotNull(applicationContext, "Application context should load");
        assertTrue(applicationContext.containsBean("eagerBeanPostProcessor"),
                "EagerBeanPostProcessor should be loaded");
    }
}
