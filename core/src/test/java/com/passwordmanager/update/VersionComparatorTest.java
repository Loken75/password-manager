package com.passwordmanager.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionComparatorTest {

    @Test
    void compare_equalVersions() {
        assertEquals(0, VersionComparator.compare("1.2.3", "1.2.3"));
    }

    @Test
    void compare_majorDifference() {
        assertTrue(VersionComparator.compare("2.0.0", "1.9.9") > 0);
        assertTrue(VersionComparator.compare("1.0.0", "2.0.0") < 0);
    }

    @Test
    void compare_minorDifference() {
        assertTrue(VersionComparator.compare("1.3.0", "1.2.9") > 0);
    }

    @Test
    void compare_patchDifference() {
        assertTrue(VersionComparator.compare("1.2.4", "1.2.3") > 0);
    }

    @Test
    void compare_stripsLeadingV() {
        assertEquals(0, VersionComparator.compare("v1.2.3", "1.2.3"));
        assertEquals(0, VersionComparator.compare("V1.2.3", "v1.2.3"));
    }

    @Test
    void compare_stripsPreReleaseSuffix() {
        assertEquals(0, VersionComparator.compare("2.0.0-rc1", "2.0.0"));
        assertEquals(0, VersionComparator.compare("2.0.0-beta", "2.0.0"));
    }

    @Test
    void compare_handlesNullAndEmpty() {
        assertEquals(0, VersionComparator.compare(null, null));
        assertEquals(0, VersionComparator.compare("", ""));
        assertTrue(VersionComparator.compare("1.0.0", null) > 0);
        assertTrue(VersionComparator.compare(null, "1.0.0") < 0);
    }

    @Test
    void compare_handlesPartialVersions() {
        assertEquals(0, VersionComparator.compare("1", "1.0.0"));
        assertEquals(0, VersionComparator.compare("1.2", "1.2.0"));
    }

    @Test
    void isNewer_trueWhenGreater() {
        assertTrue(VersionComparator.isNewer("1.3.2", "1.3.1"));
    }

    @Test
    void isNewer_falseWhenEqual() {
        assertFalse(VersionComparator.isNewer("1.3.1", "1.3.1"));
    }

    @Test
    void isNewer_falseWhenOlder() {
        assertFalse(VersionComparator.isNewer("1.3.0", "1.3.1"));
    }

    @Test
    void isNewer_preReleaseNotNewerThanSameBase() {
        assertFalse(VersionComparator.isNewer("2.0.0-rc1", "2.0.0"));
    }

    @Test
    void isPreRelease_detectsSuffix() {
        assertTrue(VersionComparator.isPreRelease("2.0.0-rc1"));
        assertTrue(VersionComparator.isPreRelease("v1.0.0-beta"));
        assertFalse(VersionComparator.isPreRelease("1.2.3"));
        assertFalse(VersionComparator.isPreRelease(null));
    }
}
