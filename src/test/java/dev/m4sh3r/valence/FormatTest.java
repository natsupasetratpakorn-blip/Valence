package dev.m4sh3r.valence;

import dev.m4sh3r.valence.util.Format;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FormatTest {

    @Test
    void parsesShortAmounts() {
        assertEquals(5000.0, Format.parseAmount("5k"));
        assertEquals(2_500_000.0, Format.parseAmount("2.5m"));
        assertEquals(1000.0, Format.parseAmount("1,000"));
        assertEquals(12.34, Format.parseAmount("12.349"));
    }

    @Test
    void rejectsBadAmounts() {
        assertNull(Format.parseAmount("-5"));
        assertNull(Format.parseAmount("0"));
        assertNull(Format.parseAmount("abc"));
        assertNull(Format.parseAmount("NaN"));
        assertNull(Format.parseAmount(null));
    }

    @Test
    void formatsMoneyAndTime() {
        assertEquals("$12,500", Format.money(12500));
        assertEquals("$1.5", Format.money(1.5));
        assertEquals("2h 5m", Format.duration(7500));
        assertEquals("3d 1h", Format.duration(3 * 86400 + 3600));
    }
}
