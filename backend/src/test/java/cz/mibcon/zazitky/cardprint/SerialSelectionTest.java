package cz.mibcon.zazitky.cardprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cz.mibcon.zazitky.common.BadRequestException;
import org.junit.jupiter.api.Test;

class SerialSelectionTest {

    @Test
    void numbersAndRanges() {
        SerialSelection selection = SerialSelection.parse("42, 57, 100-110", 200);
        assertThat(selection.serials()).hasSize(13).startsWith(42, 57, 100).endsWith(110);
        assertThat(selection.canonical()).isEqualTo("42,57,100-110");
    }

    @Test
    void spacesDashesReversedRangesAndDuplicates() {
        SerialSelection selection = SerialSelection.parse("  5 3 – 1 ; 4,4  ", 10);
        assertThat(selection.serials()).containsExactly(1, 2, 3, 4, 5);
        assertThat(selection.canonical()).isEqualTo("1-5");
    }

    @Test
    void outsideTheBatch() {
        assertThatThrownBy(() -> SerialSelection.parse("99-101", 100))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("101");
        assertThatThrownBy(() -> SerialSelection.parse("0", 100)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void notANumber() {
        assertThatThrownBy(() -> SerialSelection.parse("42, abc", 100))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("abc");
        assertThatThrownBy(() -> SerialSelection.parse(" , ", 100)).isInstanceOf(BadRequestException.class);
    }
}
