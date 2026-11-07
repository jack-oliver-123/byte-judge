package com.jack.bytejudge.problem.artifact.domain;

import com.jack.bytejudge.problem.artifact.domain.model.ArtifactKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactRulesTest {

    @Test
    void kindNamesAreExactAndCaseSensitive() {
        assertThat(ArtifactKind.parse("SAMPLE_INPUT")).isEqualTo(ArtifactKind.SAMPLE_INPUT);
        assertThat(ArtifactKind.parse("HIDDEN_OUTPUT")).isEqualTo(ArtifactKind.HIDDEN_OUTPUT);
        assertThatThrownBy(() -> ArtifactKind.parse("sample_input"))
                .isInstanceOf(InvalidArtifactException.class);
        assertThatThrownBy(() -> ArtifactKind.parse(null))
                .isInstanceOf(InvalidArtifactException.class);
    }

    @Test
    void orderAndRevisionRangesAreEnforced() {
        assertThatCode(() -> ArtifactRules.validateMetadata(ArtifactKind.SAMPLE_INPUT, 1, 0, 0))
                .doesNotThrowAnyException();
        assertThatCode(() -> ArtifactRules.validateMetadata(ArtifactKind.HIDDEN_OUTPUT, 20, 10_000, 7))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> ArtifactRules.validateMetadata(ArtifactKind.SAMPLE_INPUT, 0, 0, 0))
                .isInstanceOf(InvalidArtifactException.class);
        assertThatThrownBy(() -> ArtifactRules.validateMetadata(ArtifactKind.SAMPLE_INPUT, 21, 0, 0))
                .isInstanceOf(InvalidArtifactException.class);
        assertThatThrownBy(() -> ArtifactRules.validateMetadata(ArtifactKind.SAMPLE_INPUT, 1, -1, 0))
                .isInstanceOf(InvalidArtifactException.class);
        assertThatThrownBy(() -> ArtifactRules.validateMetadata(ArtifactKind.SAMPLE_INPUT, 1, 0, -1))
                .isInstanceOf(InvalidArtifactException.class);
    }

    @Test
    void declaredSizeAcceptsTheEmptyCaseAndRejectsOversize() {
        ArtifactRules.validateDeclaredSize(0);
        ArtifactRules.validateDeclaredSize(1);
        ArtifactRules.validateDeclaredSize(ArtifactRules.MAX_BYTES);

        assertThatThrownBy(() -> ArtifactRules.validateDeclaredSize(-1))
                .isInstanceOf(InvalidArtifactException.class);
        assertThatThrownBy(() -> ArtifactRules.validateDeclaredSize(ArtifactRules.MAX_BYTES + 1))
                .isInstanceOf(ArtifactTooLargeException.class);
    }

    @Test
    void fileNameMetadataNeverCarriesPathSegmentsOrControlCharacters() {
        assertThat(ArtifactRules.safeFileName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(ArtifactRules.safeFileName("C:\\temp\\cases\\input.txt")).isEqualTo("input.txt");
        assertThat(ArtifactRules.safeFileName("..")).isEqualTo("artifact");
        assertThat(ArtifactRules.safeFileName(".")).isEqualTo("artifact");
        assertThat(ArtifactRules.safeFileName("   ")).isEqualTo("artifact");
        assertThat(ArtifactRules.safeFileName(null)).isEqualTo("artifact");
        assertThat(ArtifactRules.safeFileName("a\u0000b\u001fc.txt")).isEqualTo("abc.txt");
        assertThat(ArtifactRules.safeFileName("  spaced.txt  ")).isEqualTo("spaced.txt");
    }

    @Test
    void fileNameIsTruncatedByCodePointNotCharUnit() {
        String longName = "\uD83D\uDE00".repeat(300);
        String safe = ArtifactRules.safeFileName(longName);

        assertThat(safe.codePointCount(0, safe.length())).isEqualTo(255);
        assertThat(safe).doesNotContain("\uFFFD");
    }
}
