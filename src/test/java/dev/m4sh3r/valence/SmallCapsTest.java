package dev.m4sh3r.valence;

import dev.m4sh3r.valence.util.SmallCaps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmallCapsTest {

    @Test
    void convertsLettersOnly() {
        assertEquals("ᴛᴇᴀᴍ 42!", SmallCaps.convert("Team 42!"));
    }

    @Test
    void leavesTagsAlone() {
        assertEquals("<hl>ʜɪ</hl> <player>", SmallCaps.convertMiniMessage("<hl>hi</hl> <player>"));
    }

    @Test
    void keepsNoSmallCapsSections() {
        assertEquals("ᴜꜱᴇ <nosc>/team ff</nosc> ɴᴏᴡ", SmallCaps.convertMiniMessage("use <nosc>/team ff</nosc> now"));
    }

    @Test
    void handlesEscapesAndBrokenTags() {
        assertEquals("\\<ᴀ <ʙ", SmallCaps.convertMiniMessage("\\<a <b"));
    }
}
