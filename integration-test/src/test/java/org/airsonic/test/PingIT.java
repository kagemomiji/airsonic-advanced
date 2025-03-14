package org.airsonic.test;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;
import org.xmlunit.builder.Input;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.xmlunit.matchers.CompareMatcher.isIdenticalTo;

public class PingIT {
    @Test
    public void pingMissingAuthTest() {
        ResponseEntity<String> response = Scanner.rest.getForEntity(
                UriComponentsBuilder.fromUriString(Scanner.SERVER + "/rest/ping").toUriString(),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        assertThat(response.getBody(),
                isIdenticalTo(Input.fromStream(getClass().getResourceAsStream("/blobs/ping/missing-auth.xml")))
                        .ignoreWhitespace());
    }
}
