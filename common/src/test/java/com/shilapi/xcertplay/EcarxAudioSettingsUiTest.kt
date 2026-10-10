package com.shilapi.xcertplay

import android.content.Intent
import android.os.Looper
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.media.AndroidMediaSink
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.vehicle.ecarx.EcarxAudioMode
import com.shilapi.xcertplay.vehicle.ecarx.EcarxAudioPreferences
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "en-w600dp-h900dp")
class EcarxAudioSettingsUiTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private var activity: DiPlayActivity? = null

    @After fun clear() {
        CarPlayBackgroundSession.clear()
        PendingReconnect.clear()
        activity?.finish()
        AppAppearanceRuntime.resetForTest()
        EcarxAudioPreferences.open(context).save(EcarxAudioMode.UPSTREAM)
        context.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
    }

    /** Cancel must not save the selected item or request reconnection. */
    @Test fun cancellingTheProfilePickerDoesNotSave() {
        val screen = openCategory(R.string.settings_advanced)
        openPicker(screen)
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        shadowOf(dialog).clickOnItem(EcarxAudioMode.E03.ordinal)
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(dialog.isShowing)
        assertEquals(EcarxAudioMode.UPSTREAM, EcarxAudioPreferences.open(context).load())
        assertNull(shadowOf(screen).nextStartedActivity)
    }

    /** An active connection is left running until the driver uses the existing reconnect bar. */
    @Test fun savingMarksReconnectWithoutStoppingTheSessionOrOverwritingCustomRoutes() {
        val screen = openCategory(R.string.settings_advanced)
        val controller = mock(CarPlayController::class.java)
        var stops = 0
        CarPlayBackgroundSession.store(controller, mock(AndroidMediaSink::class.java), 800, 480, Any(),
            CarPlaySessionDisplay(800, 480, Surface.ROTATION_0, false, false, 800, 480)) { stops++ }
        CarPlayBackgroundSession.active = true
        AirPlayPersistence.saveMediaAudioChannel(context, ORIGINAL_MUSIC_STREAM)
        AirPlayPersistence.saveNavigationAudioChannel(context, ORIGINAL_NAVIGATION_STREAM)
        openPicker(screen)
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        shadowOf(dialog).clickOnItem(EcarxAudioMode.E03.ordinal)
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        // AlertDialog dispatches the save listener through a queued main-thread message.
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(EcarxAudioMode.E03, EcarxAudioPreferences.open(context).load())
        assertTrue(PendingReconnect.isPending(controller))
        assertSame(controller, CarPlayBackgroundSession.snapshot()?.controller)
        assertEquals(0, stops)
        assertNull(shadowOf(screen).nextStartedActivity)
        assertEquals(ORIGINAL_MUSIC_STREAM, AirPlayPersistence.loadMediaAudioChannel(context))
        assertEquals(ORIGINAL_NAVIGATION_STREAM, AirPlayPersistence.loadNavigationAudioChannel(context))
        assertTrue(texts(screen).any { it.text == screen.getString(R.string.settings_reconnect_now) })
    }

    /** Do not display editable numbers that E03 mode would ignore at session creation. */
    @Test fun e03ShowsFixedRoutesInAudioAndItsSelectorOnlyInAdvanced() {
        EcarxAudioPreferences.open(context).save(EcarxAudioMode.E03)
        val screen = openCategory(R.string.audio)
        assertTrue(texts(screen).any { it.text == screen.getString(R.string.settings_ecarx_audio_fixed_routes) })
        assertFalse(texts(screen).any { it.text.startsWith(screen.getString(R.string.contrib_audio_home_media_channel_label) + " · ") })
        assertFalse(texts(screen).any { it.text.startsWith(screen.getString(R.string.contrib_audio_home_nav_channel_label) + " · ") })
        assertFalse(texts(screen).any { it.text.startsWith(screen.getString(R.string.settings_ecarx_audio_profile) + " · ") })
    }

    /** Build the real Settings activity and navigate through the same category link as a driver. */
    private fun openCategory(title: Int): DiPlayActivity {
        val screen = Robolectric.buildActivity(DiPlayActivity::class.java,
            Intent(context, DiPlayActivity::class.java).putExtra("page", "settings")).setup().get()
        activity = screen
        descendants(screen.window.decorView).first {
            it.contentDescription == screen.getString(R.string.settings_open_category, screen.getString(title))
        }.performClick()
        return screen
    }

    private fun openPicker(screen: DiPlayActivity) {
        texts(screen).single { it.text.startsWith(screen.getString(R.string.settings_ecarx_audio_profile) + " · ") }.performClick()
    }

    private fun texts(screen: DiPlayActivity) = descendants(screen.window.decorView).filterIsInstance<TextView>()

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }

    private companion object {
        const val ORIGINAL_MUSIC_STREAM = 7
        const val ORIGINAL_NAVIGATION_STREAM = 14
    }
}
