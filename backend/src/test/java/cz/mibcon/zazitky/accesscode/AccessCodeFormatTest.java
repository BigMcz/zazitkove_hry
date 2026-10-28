package cz.mibcon.zazitky.accesscode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AccessCodeFormatTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ABCD-2345     | ABCD2345",
            "abcd-2345     | ABCD2345",
            "' abcd 2345 ' | ABCD2345",
            "ABCD2345      | ABCD2345",
            "ab–cd_23.45   | ABCD2345",
    })
    void normalizesPlayerInput(String input, String expected) {
        assertThat(AccessCodeFormat.normalize(input)).isEqualTo(expected);
    }

    @Test
    void normalizesNullToEmpty() {
        assertThat(AccessCodeFormat.normalize(null)).isEmpty();
    }

    @Test
    void formatsWithDash() {
        assertThat(AccessCodeFormat.format("ABCD2345")).isEqualTo("ABCD-2345");
        assertThat(AccessCodeFormat.format("ABC")).isEqualTo("ABC");
    }

    @Test
    void generatesCodesFromUnambiguousAlphabet() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String code = AccessCodeFormat.generate();
            assertThat(code).hasSize(8).matches("[A-HJKMNP-Z2-9]{8}").doesNotContain("0", "O", "1", "I", "L");
            assertThat(AccessCodeFormat.normalize(code)).isEqualTo(code);
            codes.add(code);
        }
        assertThat(codes).hasSize(2000);
    }
}
