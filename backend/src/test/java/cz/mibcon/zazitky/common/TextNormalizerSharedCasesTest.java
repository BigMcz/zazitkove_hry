package cz.mibcon.zazitky.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Cases shared with the frontend (test-fixtures/text-normalizer.json): the offline game normalizes answers
 * in the phone and must get the same result as the server.
 */
class TextNormalizerSharedCasesTest {

    private static final JsonNode CASES = new ObjectMapper()
            .readTree(Path.of("..", "test-fixtures", "text-normalizer.json").toFile());

    @ParameterizedTest
    @MethodSource("textCases")
    void normalizesText(String input, String expected) {
        assertThat(TextNormalizer.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("numberCases")
    void normalizesNumbers(String input, String expected) {
        assertThat(TextNormalizer.normalizeNumber(input)).isEqualTo(expected);
    }

    static Stream<Arguments> textCases() {
        return cases("text");
    }

    static Stream<Arguments> numberCases() {
        return cases("number");
    }

    private static Stream<Arguments> cases(String kind) {
        return CASES.get(kind).valueStream()
                .map(pair -> Arguments.of(pair.get(0).asString(), pair.get(1).asString()));
    }
}
