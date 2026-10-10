package com.shilapi.xcertplay

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.vehicle.ecarx.EcarxAudioMode
import com.shilapi.xcertplay.vehicle.ecarx.EcarxAudioPreferences
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

/** Actual Android view rendering, not a hand-drawn mockup of the new setting. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EcarxAudioSettingsScreenshotTest {
    private var activity: DiPlayActivity? = null

    @After fun clear() {
        activity?.finish()
        AppAppearanceRuntime.resetForTest()
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("diplay", 0).edit().clear().commit()
        EcarxAudioPreferences.open(context).save(EcarxAudioMode.UPSTREAM)
        RuntimeEnvironment.setFontScale(DEFAULT_FONT_SCALE)
    }

    @Test
    @Config(qualifiers = "en-w600dp-h900dp")
    fun compactLargeFont() { capture("compact-large-font", full = false) }

    @Test
    @Config(qualifiers = "en-w1400dp-h900dp")
    fun fullLargeFont() { capture("full-large-font", full = true) }

    @Test
    @Config(qualifiers = "ar-w600dp-h900dp")
    fun arabicLargeFont() { capture("arabic-large-font", full = false) }

    /** The same real setting is rendered in compact/full layouts and right-to-left Arabic. */
    private fun capture(name: String, full: Boolean) {
        RuntimeEnvironment.setFontScale(LARGE_FONT_SCALE)
        val context = RuntimeEnvironment.getApplication()
        SettingsLayoutPreferences.saveForceFull(context, full)
        EcarxAudioPreferences.open(context).save(EcarxAudioMode.E03)
        val screen = Robolectric.buildActivity(DiPlayActivity::class.java,
            Intent(context, DiPlayActivity::class.java).putExtra("page", "settings")).setup().get()
        activity = screen
        descendants(screen.window.decorView).first {
            it.contentDescription == screen.getString(R.string.settings_open_category, screen.getString(R.string.settings_advanced))
        }.performClick()
        val root = screen.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        layout(screen, root)
        val profile = descendants(root).filterIsInstance<TextView>().single {
            it.text.startsWith(screen.getString(R.string.settings_ecarx_audio_profile) + " · ")
        }
        scrollToProfile(screen, profile)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Profile row must have a real measured size", profile.width > 0 && profile.height > 0)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val output = File("build/reports/ecarx-ui/$name.png")
        check(output.parentFile.mkdirs() || output.parentFile.isDirectory)
        output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it)) }
        bitmap.recycle()
    }

    /** Use the configured window dimensions, not a hard-coded pixel canvas. */
    private fun layout(screen: DiPlayActivity, root: View) {
        val metrics = screen.resources.displayMetrics
        root.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Bring the new profile and its explanatory text into the screenshot. */
    private fun scrollToProfile(screen: DiPlayActivity, profile: View) {
        val scroll = ReflectionHelpers.getField<ScrollView>(screen, "rootScroll")
        val location = IntArray(LOCATION_COORDINATES)
        val scrollLocation = IntArray(LOCATION_COORDINATES)
        profile.getLocationInWindow(location)
        scroll.getLocationInWindow(scrollLocation)
        scroll.scrollTo(0, scroll.scrollY + location[Y_COORDINATE] - scrollLocation[Y_COORDINATE])
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }

    private companion object {
        const val LARGE_FONT_SCALE = 1.5f
        const val DEFAULT_FONT_SCALE = 1f
        const val PNG_QUALITY = 100
        const val LOCATION_COORDINATES = 2
        const val Y_COORDINATE = 1
    }
}
