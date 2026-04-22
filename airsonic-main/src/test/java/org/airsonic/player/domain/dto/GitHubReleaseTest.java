package org.airsonic.player.domain.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubReleaseTest {

    @Test
    public void testObjectMapper() throws JsonMappingException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModules(new JavaTimeModule());
        String json = """
                 {
    "url": "https://api.github.com/repos/airsonic-ce/airsonic-ce/releases/233347166",
    "assets_url": "https://api.github.com/repos/airsonic-ce/airsonic-ce/releases/233347166/assets",
    "upload_url": "https://uploads.github.com/repos/airsonic-ce/airsonic-ce/releases/233347166/assets{?name,label}",
    "html_url": "https://github.com/airsonic-ce/airsonic-ce/releases/tag/11.1.5-SNAPSHOT.20250717232749",
    "id": 233347166,
    "author": {
      "login": "github-actions[bot]",
      "id": 41898282,
      "node_id": "MDM6Qm90NDE4OTgyODI=",
      "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
      "gravatar_id": "",
      "url": "https://api.github.com/users/github-actions%5Bbot%5D",
      "html_url": "https://github.com/apps/github-actions",
      "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
      "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
      "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
      "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
      "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
      "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
      "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
      "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
      "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
      "type": "Bot",
      "user_view_type": "public",
      "site_admin": false
    },
    "node_id": "RE_kwDOIp8CNM4N6Jhe",
    "tag_name": "11.1.5-SNAPSHOT.20250717232749",
    "target_commitish": "main",
    "name": "Edge Release 11.1.5-SNAPSHOT.20250717232749",
    "draft": false,
    "immutable": false,
    "prerelease": true,
    "created_at": "2025-07-17T22:36:30Z",
    "published_at": "2025-07-17T23:45:42Z",
    "assets": [
      {
        "url": "https://api.github.com/repos/airsonic-ce/airsonic-ce/releases/assets/273944990",
        "id": 273944990,
        "node_id": "RA_kwDOIp8CNM4QVBGe",
        "name": "airsonic.war",
        "label": "",
        "uploader": {
          "login": "github-actions[bot]",
          "id": 41898282,
          "node_id": "MDM6Qm90NDE4OTgyODI=",
          "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
          "gravatar_id": "",
          "url": "https://api.github.com/users/github-actions%5Bbot%5D",
          "html_url": "https://github.com/apps/github-actions",
          "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
          "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
          "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
          "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
          "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
          "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
          "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
          "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
          "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
          "type": "Bot",
          "user_view_type": "public",
          "site_admin": false
        },
        "content_type": "application/java-archive",
        "state": "uploaded",
        "size": 150885631,
        "digest": "sha256:146d5d18ede0570fb886ba9b9c566e2c658535d76ffe5f73f57891604cef539e",
        "download_count": 4,
        "created_at": "2025-07-17T23:45:43Z",
        "updated_at": "2025-07-17T23:45:47Z",
        "browser_download_url": "https://github.com/airsonic-ce/airsonic-ce/releases/download/11.1.5-SNAPSHOT.20250717232749/airsonic.war"
      },
      {
        "url": "https://api.github.com/repos/airsonic-ce/airsonic-ce/releases/assets/273944989",
        "id": 273944989,
        "node_id": "RA_kwDOIp8CNM4QVBGd",
        "name": "artifacts-checksums.sha",
        "label": "",
        "uploader": {
          "login": "github-actions[bot]",
          "id": 41898282,
          "node_id": "MDM6Qm90NDE4OTgyODI=",
          "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
          "gravatar_id": "",
          "url": "https://api.github.com/users/github-actions%5Bbot%5D",
          "html_url": "https://github.com/apps/github-actions",
          "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
          "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
          "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
          "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
          "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
          "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
          "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
          "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
          "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
          "type": "Bot",
          "user_view_type": "public",
          "site_admin": false
        },
        "content_type": "application/octet-stream",
        "state": "uploaded",
        "size": 162,
        "digest": "sha256:50171ef16020f03b0a26fd6e57638e34dc795b48a9ab01d86b78f486e3532ded",
        "download_count": 0,
        "created_at": "2025-07-17T23:45:43Z",
        "updated_at": "2025-07-17T23:45:43Z",
        "browser_download_url": "https://github.com/airsonic-ce/airsonic-ce/releases/download/11.1.5-SNAPSHOT.20250717232749/artifacts-checksums.sha"
      }
    ],
    "tarball_url": "https://api.github.com/repos/airsonic-ce/airsonic-ce/tarball/11.1.5-SNAPSHOT.20250717232749",
    "zipball_url": "https://api.github.com/repos/airsonic-ce/airsonic-ce/zipball/11.1.5-SNAPSHOT.20250717232749",
    "body": "## What's Changed\\n* chore(deps): bump org.apache.maven.plugins:maven-checkstyle-plugin from 3.5.0 to 3.6.0 by @dependabot[bot] in https://github.com/kagemomiji/airsonic-advanced/pull/706\\n* chore(deps): bump org.apache.maven.plugins:maven-pmd-plugin from 3.26.0 to 3.27.0 by @dependabot[bot] in https://github.com/kagemomiji/airsonic-advanced/pull/707\\n* chore(deps): bump org.apache.commons:commons-collections4 from 4.4 to 4.5.0 by @dependabot[bot] in https://github.com/kagemomiji/airsonic-advanced/pull/708\\n* chore(deps): bump io.dropwizard.metrics:metrics-core from 4.2.30 to 4.2.33 by @dependabot[bot] in https://github.com/kagemomiji/airsonic-advanced/pull/709\\n* fix(workflows): update artifact download configuration to use pattern for digest files by @kagemomiji in https://github.com/kagemomiji/airsonic-advanced/pull/714\\n* chore(deps): bump org.springframework.boot:spring-boot-starter-parent from 3.4.7 to 3.5.3 by @dependabot[bot] in https://github.com/kagemomiji/airsonic-advanced/pull/718\\n* chore(deps): bump org.eclipse.persistence:org.eclipse.persistence.moxy from 4.0.4 to 4.0.7 by @dependabot[bot] in https://github.com/kagemomiji/airsonic-advanced/pull/715\\n* chore(deps): bump com.mysql:mysql-connector-j from 9.0.0 to 9.3.0 by @dependabot[bot] in https://github.com/kagemomiji/airsonic-advanced/pull/716\\n* chore(deps): bump org.apache.commons:commons-text from 1.12.0 to 1.13.1 by @dependabot[bot] in https://github.com/kagemomiji/airsonic-advanced/pull/717\\n* chore(deps): bump aquasecurity/trivy-action from 0.31.0 to 0.32.0 by @dependabot[bot] in https://github.com/kagemomiji/airsonic-advanced/pull/719\\n* Bump logback to 1.5.18 resolving CVE-2024-12798 and CVE-2024-12801 by @maghiel in https://github.com/kagemomiji/airsonic-advanced/pull/705\\n* Fix deprecated codes by @kagemomiji in https://github.com/kagemomiji/airsonic-advanced/pull/725\\n* chore(deps): bump org.apache.commons:commons-configuration2 from 2.11.0 to 2.12.0 by @dependabot[bot] in https://github.com/kagemomiji/airsonic-advanced/pull/720\\n* chore(deps-dev): bump com.google.guava:guava from 33.3.1-jre to 33.4.8-jre by @dependabot[bot] in https://github.com/kagemomiji/airsonic-advanced/pull/721\\n* chore(deps): bump com.auth0:java-jwt from 4.4.0 to 4.5.0 by @dependabot[bot] in https://github.com/kagemomiji/airsonic-advanced/pull/722\\n* chore(deps): bump org.jsoup:jsoup from 1.18.1 to 1.21.1 by @dependabot[bot] in https://github.com/kagemomiji/airsonic-advanced/pull/723\\n* chore(deps-dev): bump org.apache.commons:commons-lang3 from 3.16.0 to 3.18.0 by @dependabot[bot] in https://github.com/kagemomiji/airsonic-advanced/pull/724\\n* chore(deps): bump com.twelvemonkeys.imageio:imageio-webp from 3.11.0 to 3.12.0 by @dependabot[bot] in https://github.com/kagemomiji/airsonic-advanced/pull/727\\n* chore(deps): bump org.apache.cxf:cxf-xjc-plugin from 4.0.2 to 4.1.0 by @dependabot[bot] in https://github.com/kagemomiji/airsonic-advanced/pull/726\\n* chore(deps-dev): bump commons-io:commons-io from 2.17.0 to 2.19.0 by @dependabot[bot] in https://github.com/kagemomiji/airsonic-advanced/pull/728\\n* chore(deps): bump org.apache.maven:maven-artifact from 3.9.9 to 3.9.11 by @dependabot[bot] in https://github.com/kagemomiji/airsonic-advanced/pull/729\\n\\n\\n**Full Changelog**: https://github.com/kagemomiji/airsonic-advanced/compare/11.1.5-SNAPSHOT.20250702234812...11.1.5-SNAPSHOT.20250717232749",
    "reactions": {
      "url": "https://api.github.com/repos/airsonic-ce/airsonic-ce/releases/233347166/reactions",
      "total_count": 1,
      "+1": 0,
      "-1": 0,
      "laugh": 0,
      "hooray": 0,
      "confused": 0,
      "heart": 1,
      "rocket": 0,
      "eyes": 0
    },
    "mentions_count": 3
    }
            """;
        GitHubRelease release = objectMapper.readValue(json, GitHubRelease.class);
        assertEquals("https://github.com/airsonic-ce/airsonic-ce/releases/tag/11.1.5-SNAPSHOT.20250717232749", release.getHtmlUrl());
        assertEquals("11.1.5-SNAPSHOT.20250717232749", release.getTagName());
        assertEquals("main", release.getTargetCommitish());
        assertFalse(release.isDraft());
        assertTrue(release.isPrerelease());
        assertEquals(Instant.parse("2025-07-17T22:36:30Z"), release.getCreatedAt());
        assertEquals(Instant.parse("2025-07-17T23:45:42Z"), release.getPublishedAt());
        assertNotNull(release.getAssets());
        assertEquals(2, release.getAssets().size());
        assertEquals("airsonic.war", release.getAssets().get(0).getName());
        assertEquals("artifacts-checksums.sha", release.getAssets().get(1).getName());
    }
}
