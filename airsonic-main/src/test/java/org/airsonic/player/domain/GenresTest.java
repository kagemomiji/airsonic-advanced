package org.airsonic.player.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class GenresTest {

    @Test
    void testIncrementAlbumCountSingleGenre() {
        Genres genres = new Genres();
        genres.incrementAlbumCount("Rock", ";");
        List<Genre> result = genres.getGenres();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Rock");
        assertThat(result.get(0).getAlbumCount()).isEqualTo(1);
    }

    @Test
    void testIncrementAlbumCountSplitGenre() {
        Genres genres = new Genres();
        genres.incrementAlbumCount("Rock;Pop", ";");
        Map<String, Genre> byName = genres.getGenres().stream()
                .collect(Collectors.toMap(Genre::getName, g -> g));
        // Only split components indexed, NOT combined "Rock;Pop"
        assertThat(byName).containsKeys("Rock", "Pop");
        assertThat(byName).doesNotContainKey("Rock;Pop");
        assertThat(byName.get("Rock").getAlbumCount()).isEqualTo(1);
        assertThat(byName.get("Pop").getAlbumCount()).isEqualTo(1);
    }

    @Test
    void testIncrementSongCountSingleGenre() {
        Genres genres = new Genres();
        genres.incrementSongCount("Jazz", ";");
        List<Genre> result = genres.getGenres();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Jazz");
        assertThat(result.get(0).getSongCount()).isEqualTo(1);
    }

    @Test
    void testIncrementSongCountSplitGenre() {
        Genres genres = new Genres();
        genres.incrementSongCount("Jazz; Blues", ";");
        Map<String, Genre> byName = genres.getGenres().stream()
                .collect(Collectors.toMap(Genre::getName, g -> g));
        // "Jazz" and "Blues" (trimmed), NOT "Jazz; Blues"
        assertThat(byName).containsKeys("Jazz", "Blues");
        assertThat(byName).doesNotContainKey("Jazz; Blues");
        assertThat(byName.get("Jazz").getSongCount()).isEqualTo(1);
        assertThat(byName.get("Blues").getSongCount()).isEqualTo(1);
    }

    @Test
    void testBlankSplitComponentsIgnored() {
        Genres genres = new Genres();
        genres.incrementAlbumCount("Rock;;Pop", ";");
        Map<String, Genre> byName = genres.getGenres().stream()
                .collect(Collectors.toMap(Genre::getName, g -> g));
        assertThat(byName).containsOnlyKeys("Rock", "Pop");
        assertThat(byName.get("Rock").getAlbumCount()).isEqualTo(1);
        assertThat(byName.get("Pop").getAlbumCount()).isEqualTo(1);
    }
}
