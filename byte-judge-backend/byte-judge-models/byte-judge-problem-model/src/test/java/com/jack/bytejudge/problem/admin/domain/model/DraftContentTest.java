package com.jack.bytejudge.problem.admin.domain.model;

import com.jack.bytejudge.problem.admin.domain.DraftValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DraftContentTest {

    private static DraftContent content(String title, String statement, List<String> tags,
                                        Integer cpu, Integer wall, Integer memory) {
        return DraftContent.of(title, statement, null, null, null, tags, cpu, wall, memory);
    }

    @Test
    void titleIsStrippedAndCountedInUnicodeCodePoints() {
        assertThat(DraftContent.titleOnly("  Two Sum  ").title()).isEqualTo("Two Sum");
        assertThat(DraftContent.titleOnly("a".repeat(100)).title()).hasSize(100);
        assertThat(DraftContent.titleOnly("😀".repeat(100)).title()).hasSize(200);

        assertThatThrownBy(() -> DraftContent.titleOnly("a".repeat(101)))
                .isInstanceOf(DraftValidationException.class);
        assertThatThrownBy(() -> DraftContent.titleOnly("😀".repeat(101)))
                .isInstanceOf(DraftValidationException.class);
        assertThatThrownBy(() -> DraftContent.titleOnly("   "))
                .isInstanceOf(DraftValidationException.class);
        assertThatThrownBy(() -> DraftContent.titleOnly(null))
                .isInstanceOf(DraftValidationException.class);
    }

    @Test
    void unicodeSpaceOnlyTitlesAndTagsAreRejected() {
        for (String space : List.of(" ", " ", " ", "　")) {
            assertThatThrownBy(() -> DraftContent.titleOnly(space))
                    .isInstanceOf(DraftValidationException.class);
            assertThatThrownBy(() -> content("title", null, List.of(space), null, null, null))
                    .isInstanceOf(DraftValidationException.class);
            assertThat(DraftContent.titleOnly(space + "title" + space).title()).isEqualTo("title");
        }
    }

    @Test
    void statementAndDescriptionsShareTheHundredKibibyteUtf8Budget() {
        assertThat(content("t", "a".repeat(100 * 1024), List.of(), null, null, null).statement())
                .hasSize(100 * 1024);
        assertThatThrownBy(() -> content("t", "a".repeat(100 * 1024 + 1), List.of(), null, null, null))
                .isInstanceOf(DraftValidationException.class);

        // UTF-8 bytes, not characters: 25,601 four-byte code points exceed the cap.
        String emoji = "😀".repeat(25_601);
        assertThat(emoji.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(102_404);
        assertThatThrownBy(() -> content("t", emoji, List.of(), null, null, null))
                .isInstanceOf(DraftValidationException.class);
    }

    @Test
    void tagsAreLimitedToFiveStrippedOneToTwentyCodePointValues() {
        assertThat(content("t", null, List.of(" a ", "b", "c", "d", "e"), null, null, null).tags())
                .containsExactly("a", "b", "c", "d", "e");
        assertThat(content("t", null, List.of("😀".repeat(20)), null, null, null).tags())
                .containsExactly("😀".repeat(20));

        assertThatThrownBy(() -> content("t", null, List.of("a", "b", "c", "d", "e", "f"), null, null, null))
                .isInstanceOf(DraftValidationException.class);
        assertThatThrownBy(() -> content("t", null, List.of("😀".repeat(21)), null, null, null))
                .isInstanceOf(DraftValidationException.class);
        assertThatThrownBy(() -> content("t", null, List.of("   "), null, null, null))
                .isInstanceOf(DraftValidationException.class);
    }

    @Test
    void nullableLimitsAreValidatedOnlyWhenPresent() {
        assertThat(content("t", null, List.of(), null, null, null).timeLimitCpuMs()).isNull();
        assertThat(content("t", null, List.of(), 100, 100, 64).timeLimitCpuMs()).isEqualTo(100);
        assertThat(content("t", null, List.of(), 10_000, 30_000, 2_048).memoryLimitMiB())
                .isEqualTo(2_048);

        assertThatThrownBy(() -> content("t", null, List.of(), 99, null, null))
                .isInstanceOf(DraftValidationException.class);
        assertThatThrownBy(() -> content("t", null, List.of(), 10_001, null, null))
                .isInstanceOf(DraftValidationException.class);
        assertThatThrownBy(() -> content("t", null, List.of(), null, 99, null))
                .isInstanceOf(DraftValidationException.class);
        assertThatThrownBy(() -> content("t", null, List.of(), null, 30_001, null))
                .isInstanceOf(DraftValidationException.class);
        assertThatThrownBy(() -> content("t", null, List.of(), 200, 100, null))
                .isInstanceOf(DraftValidationException.class);
        assertThatThrownBy(() -> content("t", null, List.of(), null, null, 63))
                .isInstanceOf(DraftValidationException.class);
        assertThatThrownBy(() -> content("t", null, List.of(), null, null, 2_049))
                .isInstanceOf(DraftValidationException.class);
    }

    @Test
    void difficultyParsingIsCaseInsensitiveAndBlankMeansUnset() {
        assertThat(Difficulty.parse("easy")).isEqualTo(Difficulty.EASY);
        assertThat(Difficulty.parse(" HARD ")).isEqualTo(Difficulty.HARD);
        assertThat(Difficulty.parse(null)).isNull();
        assertThat(Difficulty.parse("  ")).isNull();
        assertThatThrownBy(() -> Difficulty.parse("INSANE"))
                .isInstanceOf(DraftValidationException.class);
    }
}
