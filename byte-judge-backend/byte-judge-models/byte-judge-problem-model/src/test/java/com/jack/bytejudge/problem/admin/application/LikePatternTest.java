package com.jack.bytejudge.problem.admin.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikePatternTest {

    @Test
    void wildcardsAndTheEscapeCharacterMatchLiterally() {
        assertThat(LikePattern.contains(" 50%_! ")).isEqualTo("%50!%!_!!%");
        assertThat(LikePattern.escape("a%b_c!d"))
                .isEqualTo("a" + "!%" + "b" + "!_" + "c" + "!!" + "d");
    }

    @Test
    void blankFiltersAreAbsent() {
        assertThat(LikePattern.contains(null)).isNull();
        assertThat(LikePattern.contains("   ")).isNull();
        assertThat(LikePattern.contains("Two Sum")).isEqualTo("%Two Sum%");
    }
}
