package com.a9ito.hermesagent.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavior contract for the navigation routing table that drives the hybrid
 * shell: three primary destinations in the bottom bar, the rest behind "More".
 * These pin the classification the UI relies on (which tab is active, whether a
 * route belongs in the sheet) without needing an Android runtime.
 */
class NavRoutesTest {

    @Test fun primaryIsExactlyThreeInOrder() {
        // The bottom bar is primary + a "More" item = 4 slots, inside M3's 3-5 range.
        assertEquals(listOf("chat", "sessions", "runs"), NavRoutes.PRIMARY)
    }

    @Test fun secondaryHoldsTheRest() {
        assertEquals(listOf("tools", "jobs", "status", "settings"), NavRoutes.SECONDARY)
    }

    @Test fun primaryAndSecondaryAreDisjoint() {
        assertTrue(NavRoutes.PRIMARY.none { it in NavRoutes.SECONDARY })
    }

    @Test fun allIsPrimaryThenSecondaryWithNoDuplicates() {
        assertEquals(NavRoutes.PRIMARY + NavRoutes.SECONDARY, NavRoutes.ALL)
        assertEquals(NavRoutes.ALL.size, NavRoutes.ALL.distinct().size)
    }

    @Test fun startIsAPrimaryDestination() {
        assertTrue(NavRoutes.isPrimary(NavRoutes.START))
    }

    @Test fun isSecondaryOnlyForMenuRoutes() {
        assertTrue(NavRoutes.isSecondary("tools"))
        assertTrue(NavRoutes.isSecondary("settings"))
        assertFalse(NavRoutes.isSecondary("chat"))
        assertFalse(NavRoutes.isSecondary("runs"))
    }

    @Test fun detailRouteIsNeitherTopLevelNorSecondary() {
        // The per-session chat detail route must not light up "More" or any tab.
        assertFalse(NavRoutes.isTopLevel("session_chat/abc"))
        assertFalse(NavRoutes.isSecondary("session_chat/abc"))
        assertFalse(NavRoutes.isPrimary("session_chat/abc"))
    }

    @Test fun nullRouteIsSafe() {
        assertFalse(NavRoutes.isSecondary(null))
        assertFalse(NavRoutes.isPrimary(null))
        assertFalse(NavRoutes.isTopLevel(null))
    }

    @Test fun everyTopLevelRouteClassifiesAsExactlyOne() {
        NavRoutes.ALL.forEach { route ->
            assertTrue("$route should be primary xor secondary",
                NavRoutes.isPrimary(route) != NavRoutes.isSecondary(route))
        }
    }
}
