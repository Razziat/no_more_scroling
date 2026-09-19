package com.antiscroll.mobile.blocking

import com.antiscroll.mobile.detection.BlockedSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockCoordinatorTest {
    private val instagram = "com.instagram.android"
    private val youtube = "com.google.android.youtube"
    private val blockedUntil = 1_800_000L

    @Test
    fun `background events cannot display or renew a punitive overlay`() {
        val environment = FakeEnvironment(activePackage = "launcher")
        val coordinator = BlockCoordinator(environment)

        coordinator.startPunitiveLock(instagram, blockedUntil)
        repeat(10) {
            environment.advanceBy(1_000L)
            coordinator.enforcePunitiveLock(instagram, blockedUntil, showOverlay = true)
        }

        assertEquals(0, environment.punitiveShowCount)
        assertEquals(0, environment.backCount)
        assertNull(environment.overlayPackage)
        assertTrue(environment.pending.isEmpty())
    }

    @Test
    fun `notice stays readable after exit then expires despite background events`() {
        val environment = FakeEnvironment(activePackage = instagram, backsBeforeExit = 1)
        val coordinator = BlockCoordinator(environment)

        coordinator.startPunitiveLock(instagram, blockedUntil)
        assertEquals(instagram, environment.overlayPackage)
        environment.advanceBy(260L)

        assertEquals(1, environment.backCount)
        assertEquals(instagram, environment.overlayPackage)
        coordinator.enforcePunitiveLock(instagram, blockedUntil, showOverlay = true)
        assertEquals(1, environment.punitiveShowCount)
        environment.advanceBy(2_739L)
        assertEquals(instagram, environment.overlayPackage)
        environment.advanceBy(1L)
        assertNull(environment.overlayPackage)
        coordinator.enforcePunitiveLock(instagram, blockedUntil, showOverlay = true)
        assertEquals(1, environment.punitiveShowCount)
        assertNull(environment.overlayPackage)
        assertTrue(environment.pending.isEmpty())
    }

    @Test
    fun `two Back actions leave the notice visible only until its deadline`() {
        val environment = FakeEnvironment(activePackage = instagram, backsBeforeExit = 2)
        val coordinator = BlockCoordinator(environment)

        coordinator.startPunitiveLock(instagram, blockedUntil)
        environment.advanceBy(520L)

        assertEquals(2, environment.backCount)
        assertEquals(instagram, environment.overlayPackage)
        environment.advanceBy(2_480L)
        assertNull(environment.overlayPackage)
        assertTrue(environment.pending.isEmpty())
    }

    @Test
    fun `a foreground change cancels the Back but keeps the brief notice`() {
        val environment = FakeEnvironment(activePackage = instagram)
        val coordinator = BlockCoordinator(environment)

        coordinator.startPunitiveLock(instagram, blockedUntil)
        environment.activePackage = "another.app"
        environment.advanceBy(0L)

        assertEquals(0, environment.backCount)
        assertEquals(instagram, environment.overlayPackage)
        environment.advanceBy(3_000L)
        assertNull(environment.overlayPackage)
    }

    @Test
    fun `a real reopening can show the remaining penalty again`() {
        val environment = FakeEnvironment(activePackage = instagram, backsBeforeExit = 1)
        val coordinator = BlockCoordinator(environment)

        coordinator.startPunitiveLock(instagram, blockedUntil)
        assertTrue(environment.newPenalty)
        environment.advanceBy(4_000L)
        assertNull(environment.overlayPackage)
        environment.activePackage = instagram
        coordinator.enforcePunitiveLock(instagram, blockedUntil, showOverlay = true)

        assertEquals(instagram, environment.overlayPackage)
        assertEquals(blockedUntil, environment.shownBlockedUntil)
        assertEquals(2, environment.punitiveShowCount)
        assertEquals(false, environment.newPenalty)
        environment.advanceBy(3_000L)
        assertNull(environment.overlayPackage)
    }

    @Test
    fun `an event from another background platform does not remove the current overlay`() {
        val environment = FakeEnvironment(activePackage = instagram)
        val coordinator = BlockCoordinator(environment)

        coordinator.startPunitiveLock(instagram, blockedUntil)
        coordinator.enforcePunitiveLock(youtube, blockedUntil, showOverlay = true)

        assertEquals(instagram, environment.overlayPackage)
        assertEquals(1, environment.punitiveShowCount)
    }

    @Test
    fun `cleanup does not add a third Back if the application stays open`() {
        val environment = FakeEnvironment(activePackage = instagram)
        val coordinator = BlockCoordinator(environment)

        coordinator.startPunitiveLock(instagram, blockedUntil)
        environment.advanceBy(3_000L)

        assertEquals(2, environment.backCount)
        assertTrue(environment.pending.isEmpty())
        coordinator.dispose()
        assertNull(environment.overlayPackage)
    }

    @Test
    fun `disposing cancels queued exit checks`() {
        val environment = FakeEnvironment(activePackage = instagram)
        val coordinator = BlockCoordinator(environment)

        coordinator.startPunitiveLock(instagram, blockedUntil)
        coordinator.dispose()
        environment.advanceBy(1_000L)

        assertEquals(0, environment.backCount)
        assertTrue(environment.pending.isEmpty())
        assertNull(environment.overlayPackage)
    }

    @Test
    fun `repeated foreground requests cannot extend the notice deadline`() {
        val environment = FakeEnvironment(activePackage = instagram)
        val coordinator = BlockCoordinator(environment)
        coordinator.startPunitiveLock(instagram, blockedUntil)
        repeat(3) {
            environment.advanceBy(800L)
            coordinator.enforcePunitiveLock(instagram, blockedUntil, showOverlay = true)
        }
        assertEquals(1, environment.punitiveShowCount)
        environment.advanceBy(600L)
        assertNull(environment.overlayPackage)
    }

    @Test
    fun `a notice for another platform gets its own full lifetime`() {
        val environment = FakeEnvironment(activePackage = instagram)
        val coordinator = BlockCoordinator(environment)
        coordinator.startPunitiveLock(instagram, blockedUntil)
        environment.advanceBy(2_000L)
        environment.activePackage = youtube
        coordinator.enforcePunitiveLock(youtube, blockedUntil, showOverlay = true)
        environment.advanceBy(1_000L)
        assertEquals(youtube, environment.overlayPackage)
        environment.advanceBy(2_000L)
        assertNull(environment.overlayPackage)
    }

    private class FakeEnvironment(
        var activePackage: String?,
        private val backsBeforeExit: Int = Int.MAX_VALUE,
    ) : BlockEnvironment {
        var now = 0L
        var backCount = 0
        var punitiveShowCount = 0
        var overlayPackage: String? = null
        var shownBlockedUntil = 0L
        var newPenalty = false
        val pending = mutableMapOf<Runnable, Long>()

        override fun isPackageActive(packageName: String) = activePackage == packageName
        override fun performBack(): Boolean {
            backCount += 1
            if (backCount >= backsBeforeExit) activePackage = "launcher"
            return true
        }
        override fun showNormal(surface: BlockedSurface) = Unit
        override fun showPunitive(packageName: String, blockedUntilMillis: Long, newPenalty: Boolean) {
            this.newPenalty = newPenalty
            overlayPackage = packageName
            shownBlockedUntil = blockedUntilMillis
            punitiveShowCount += 1
        }
        override fun dismissPunitive(packageName: String) {
            if (overlayPackage == packageName) overlayPackage = null
        }
        override fun dismiss() { overlayPackage = null }
        override fun uptimeMillis() = now
        override fun postDelayed(callback: Runnable, delayMillis: Long) {
            pending[callback] = now + delayMillis
        }
        override fun removeCallback(callback: Runnable) { pending.remove(callback) }

        fun advanceBy(deltaMillis: Long) {
            val target = now + deltaMillis
            while (true) {
                val next = pending.minByOrNull { it.value } ?: break
                if (next.value > target) break
                now = next.value
                pending.remove(next.key)
                next.key.run()
            }
            now = target
        }
    }
}
