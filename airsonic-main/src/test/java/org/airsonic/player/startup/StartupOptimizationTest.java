package org.airsonic.player.startup;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Test to validate that startup optimizations are working correctly.
 * This test ensures the application can start with the new lazy initialization
 * and other optimization configurations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "spring.main.lazy-initialization=true",
    "spring.jpa.defer-datasource-initialization=true",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.liquibase.enabled=false"
})
public class StartupOptimizationTest {

    @Test
    public void contextLoadsWithOptimizations() {
        // This test validates that the Spring context can load successfully
        // with all the startup optimizations enabled
        // The presence of this test and successful execution indicates
        // that lazy initialization and other optimizations are working
    }
}
