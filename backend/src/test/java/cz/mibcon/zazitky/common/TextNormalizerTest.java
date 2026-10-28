package cz.mibcon.zazitky.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TextNormalizerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "jáma        | jama",
            "JÁMA        | jama",
            "'  jama  '  | jama",
            "Díra!       | dira",
            "Klíč        | klic",
            "ŽLUŤOUČKÝ KŮŇ | zlutoucky kun",
            "brána.      | brana",
            "'„Brána“'   | brana",
            "'Mistr   Jakub' | mistr jakub",
            "'mistr, jakub?' | mistr jakub",
            "'rok 1621'  | rok 1621",
    })
    void normalizesText(String input, String expected) {
        assertThat(TextNormalizer.normalize(input)).isEqualTo(expected);
    }

    @Test
    void treatsTabsAndNewlinesAsWordSeparators() {
        assertThat(TextNormalizer.normalize("mistr\tJakub\nkat")).isEqualTo("mistr jakub kat");
    }

    @Test
    void doesNotLeaveSpacesAfterRemovedPunctuation() {
        assertThat(TextNormalizer.normalize("kat - mistr")).isEqualTo("kat mistr");
        assertThat(TextNormalizer.normalize("!!! klíč ???")).isEqualTo("klic");
    }

    @Test
    void returnsEmptyStringForNullOrBlank() {
        assertThat(TextNormalizer.normalize(null)).isEmpty();
        assertThat(TextNormalizer.normalize("   ")).isEmpty();
        assertThat(TextNormalizer.normalize("?!")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "56          | 56",
            "' 56 '      | 56",
            "56 schodů   | 56",
            "'1 621'     | 1621",
            "1.621       | 1621",
            "-56         | 56",
    })
    void normalizesNumbers(String input, String expected) {
        assertThat(TextNormalizer.normalizeNumber(input)).isEqualTo(expected);
    }

    @Test
    void numberWithoutDigitsIsEmpty() {
        assertThat(TextNormalizer.normalizeNumber("padesát šest")).isEmpty();
        assertThat(TextNormalizer.normalizeNumber(null)).isEmpty();
    }
}
