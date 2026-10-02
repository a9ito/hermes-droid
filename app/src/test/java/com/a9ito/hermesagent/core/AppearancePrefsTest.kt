package com.a9ito.hermesagent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearancePrefsTest {

    // -- resolveDark -----------------------------------------------------------

    @Test fun systemModeFollowsOsDarkFlag() {
        val prefs = AppearancePrefs(themeMode = ThemeMode.SYSTEM)
        assertTrue(prefs.resolveDark(systemInDark = true))
        assertFalse(prefs.resolveDark(systemInDark = false))
    }

    @Test fun lightModeForcesLightRegardlessOfSystem() {
        val prefs = AppearancePrefs(themeMode = ThemeMode.LIGHT)
        assertFalse(prefs.resolveDark(systemInDark = true))
        assertFalse(prefs.resolveDark(systemInDark = false))
    }

    @Test fun darkModeForcesDarkRegardlessOfSystem() {
        val prefs = AppearancePrefs(themeMode = ThemeMode.DARK)
        assertTrue(prefs.resolveDark(systemInDark = false))
        assertTrue(prefs.resolveDark(systemInDark = true))
    }

    // -- usePureBlack ----------------------------------------------------------

    @Test fun pureBlackOnlyAppliesInResolvedDark() {
        val on = AppearancePrefs(pureBlack = true, themeMode = ThemeMode.SYSTEM)
        assertTrue(on.usePureBlack(systemInDark = true))
        assertFalse(on.usePureBlack(systemInDark = false))
    }

    @Test fun pureBlackForcedDarkAppliesEvenInLightSystem() {
        val on = AppearancePrefs(pureBlack = true, themeMode = ThemeMode.DARK)
        assertTrue(on.usePureBlack(systemInDark = false))
    }

    @Test fun pureBlackOffNeverApplies() {
        val off = AppearancePrefs(pureBlack = false, themeMode = ThemeMode.DARK)
        assertFalse(off.usePureBlack(systemInDark = true))
    }

    // -- enum key round-trip (DataStore persistence contract) ------------------

    @Test fun themeModeFromKeyRoundTripsAndFallsBackToDefault() {
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromKey(it.key)) }
        assertEquals(ThemeMode.DEFAULT, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.DEFAULT, ThemeMode.fromKey("bogus"))
    }

    @Test fun accentFromKeyRoundTripsAndFallsBackToDefault() {
        AccentPreset.entries.forEach { assertEquals(it, AccentPreset.fromKey(it.key)) }
        assertEquals(AccentPreset.DEFAULT, AccentPreset.fromKey(null))
        assertEquals(AccentPreset.DEFAULT, AccentPreset.fromKey("bogus"))
    }

    @Test fun defaultsReproduceOriginalLook() {
        val d = AppearancePrefs.DEFAULT
        assertEquals(ThemeMode.SYSTEM, d.themeMode)
        assertTrue(d.dynamicColor)
        assertFalse(d.pureBlack)
        assertEquals(AccentPreset.PLUM, d.accent)
    }
}
