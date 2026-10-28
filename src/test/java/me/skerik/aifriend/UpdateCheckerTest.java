package me.skerik.aifriend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.skerik.aifriend.util.UpdateChecker;
import org.junit.jupiter.api.Test;

class UpdateCheckerTest {

    @Test
    void comparesDottedVersions() {
        assertTrue(UpdateChecker.isNewer("v1.0.1", "1.0.0"));
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.9"));
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"));
        assertFalse(UpdateChecker.isNewer("v1.0.0", "1.0.0"));
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.1"));
        assertFalse(UpdateChecker.isNewer("v0.1.0", "0.1.0-SNAPSHOT"));
        assertTrue(UpdateChecker.isNewer("v0.2.0", "0.1.0-SNAPSHOT"));
        assertFalse(UpdateChecker.isNewer("garbage", "1.0.0"));
    }

    @Test
    void comparesSemanticallyNotAlphabetically() {
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.0"));
        assertFalse(UpdateChecker.isNewer("1.9.0", "1.10.0"));
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.4.1"));
        assertTrue(UpdateChecker.isNewer("V1.4.2", "1.4.1-SNAPSHOT"));
        assertFalse(UpdateChecker.isNewer("1.4.1", "1.4.1-SNAPSHOT"));
    }
}
