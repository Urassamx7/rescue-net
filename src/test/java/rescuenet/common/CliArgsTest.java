package rescuenet.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CliArgsTest {
    @Test
    void parsesKeyValuePairsAndFlags() {
        CliArgs args = CliArgs.parse(new String[]{
                "--id", "DR-001",
                "--battery", "90",
                "--verbose"
        });
        assertEquals("DR-001", args.req("id"));
        assertEquals(90.0, args.reqDouble("battery"));
        assertEquals("true", args.opt("verbose", "false"));
        assertEquals("Base", args.opt("base", "Base"));
        assertEquals(1099, args.optInt("port", 1099));
    }

    @Test
    void rejectsUnexpectedTokens() {
        assertThrows(IllegalArgumentException.class, () -> CliArgs.parse(new String[]{"DR-001"}));
    }

    @Test
    void reqFailsWhenArgumentIsMissing() {
        CliArgs args = CliArgs.parse(new String[]{"--id", "DR-001"});
        assertThrows(IllegalArgumentException.class, () -> args.req("central"));
    }
}
