package my.edu.unikl.tapsafe.victim

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * TapSafe Victim Lab.
 *
 * The screen below is styled to resemble a generic banking app ("JuneBank (Demo)") so the
 * tapjacking demo feels realistic to a test user. JuneBank (Demo) is entirely fictional: no
 * real institution, logo, account data or backend is involved. Every figure on screen is dummy
 * data, and the "Confirm Transfer" button performs no real transaction - it only records a local,
 * non-sensitive test event (see ResultStore). This activity, the overlay in `overlay-demo`, and
 * the scope restrictions are unchanged from the project's Rules of Engagement: local dummy apps
 * only, no credential capture, no third-party target.
 */
class MainActivity : Activity() {

    // ---- Palette for the fictional "Meridian Trust Bank (Demo)" theme -------------------------------
    private val bankPrimary = Color.rgb(13, 71, 66)       // deep teal
    private val bankAccent = Color.rgb(27, 158, 119)      // green CTA
    private val bankBackground = Color.rgb(241, 245, 244) // light neutral
    private val cardWhite = Color.WHITE
    private val warnOrange = Color.rgb(176, 90, 12)
    private val textDark = Color.rgb(25, 35, 45)
    private val textMuted = Color.rgb(110, 122, 120)

    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var actionArea: LinearLayout
    private var protectedMode = false
    private var hideOverlays = false
    private var actionCount = 0
    private var receiverRegistered = false

    /** Hears a tap the overlay app's hidden button intercepted - logged, never a real transaction. */
    private val hiddenTapReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            ResultStore.record(
                this@MainActivity,
                "OVERLAY_INTERCEPTED_TAP",
                "A tap meant for the visible Confirm Transfer button was captured by the lab overlay's hidden button instead"
            )
            status.text = "TAPJACKED: your tap went to the hidden overlay button, not Confirm Transfer"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(ACTION_HIDDEN_TAP)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(hiddenTapReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(hiddenTapReceiver, filter)
        }
        receiverRegistered = true
        renderScreen()
        showAuthorizationNotice()
    }

    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(hiddenTapReceiver)
        super.onDestroy()
    }

    private fun showAuthorizationNotice() {
        AlertDialog.Builder(this)
            .setTitle("TapSafe authorized lab only")
            .setMessage(
                "This tool is restricted to the TapSafe dummy application and an emulator/device " +
                    "you own or are explicitly authorized to test. \"$BANK_NAME\" shown below " +
                    "is entirely fictional - it is not a real bank, and nothing here performs a real " +
                    "transaction. Do not target third-party apps. Malaysia's Computer Crimes Act 1997 " +
                    "may apply to unauthorized access. Continue?"
            )
            .setCancelable(false)
            .setPositiveButton("I UNDERSTAND AND AUTHORIZE THIS LAB") { _, _ ->
                ResultStore.record(this, "CONSENT", "User confirmed local TapSafe lab scope")
                status.text = "Authorized scope: TapSafe dummy app only"
            }
            .setNegativeButton("EXIT") { _, _ -> finish() }
            .show()
    }

    // --------------------------------------------------------------------------------------
    // Screen layout
    // --------------------------------------------------------------------------------------

    private fun renderScreen() {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bankBackground)
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(buildBankHeader())
        root.addView(buildBalanceCard())
        root.addView(buildTransferCard())
        root.addView(buildLabControls())
    }

    /** Fictional bank app header bar - "JuneBank (Demo)" is not a real institution. */
    private fun buildBankHeader(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bankPrimary)
            setPadding(32, 48, 32, 28)

            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                addView(TextView(this@MainActivity).apply {
                    text = BANK_NAME.take(1)
                    textSize = 20f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(bankPrimary)
                    gravity = Gravity.CENTER
                    background = roundedDrawable(Color.WHITE, 999f)
                    layoutParams = LinearLayout.LayoutParams(72, 72)
                })
                addView(TextView(this@MainActivity).apply {
                    text = "  $BANK_NAME"
                    textSize = 20f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                })
            })

            addView(TextView(this@MainActivity).apply {
                text = "ACADEMIC TAPJACKING LAB - FICTIONAL BANK, NOT A REAL APP"
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(255, 214, 153))
                setPadding(0, 10, 0, 0)
            })
        }

    private fun buildBalanceCard(): LinearLayout =
        card().apply {
            addView(textView("AVAILABLE BALANCE (DUMMY DATA)", 11f, textMuted, true))
            addView(textView(DUMMY_BALANCE, 30f, textDark, true))
            addView(textView("Account $DUMMY_ACCOUNT_MASKED (dummy, not real)", 12f, textMuted, false))
        }

    private fun buildTransferCard(): LinearLayout =
        card().apply {
            addView(textView("Send Money (Demo)", 16f, textDark, true))
            addView(textView(
                "Everything below is a dummy form. No network call, bank or recipient is real.",
                12f, textMuted, false
            ))

            addView(fieldLabel("Recipient name"))
            addView(fakeField(DUMMY_RECIPIENT_NAME))
            addView(fieldLabel("Recipient account no."))
            addView(fakeField(DUMMY_RECIPIENT_ACCOUNT, InputType.TYPE_CLASS_NUMBER))
            addView(fieldLabel("Amount (RM)"))
            addView(fakeField(DUMMY_TRANSFER_AMOUNT, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL))

            status = textView("Status: waiting for authorization", 13f, textDark, true).apply {
                setPadding(0, 20, 0, 8)
            }
            addView(status)

            actionArea = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            addView(actionArea)
            renderActionButton()
        }

    private fun buildLabControls(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 48)

            addView(textView(
                "TAPSAFE LAB CONTROLS  -  instrumentation only, not part of the simulated bank UI",
                12f, warnOrange, true
            ).apply { setPadding(0, 0, 0, 12) })

            addView(labButton("Reconfirm authorization") { showAuthorizationNotice() })
            addView(labButton("Launch TapSafe Overlay Demo") { launchOverlayDemo() })
            addView(labButton("Toggle protected mode: ${if (protectedMode) "ON" else "OFF"}") {
                protectedMode = !protectedMode
                status.text = "Protected mode: ${if (protectedMode) "ON" else "OFF"}"
                renderActionButton()
                refreshControlLabels()
            })
            addView(labButton("Toggle hide overlays (Android 12+): ${if (hideOverlays) "ON" else "OFF"}") {
                hideOverlays = !hideOverlays
                applyOverlayHiding()
                status.text = "Hide overlays: ${if (hideOverlays) "ON" else "OFF"}"
                refreshControlLabels()
            })
            addView(labButton("View local test results") { showResults() })
            addView(labButton("Clear local test results") {
                ResultStore.clear(this@MainActivity)
                Toast.makeText(this@MainActivity, "Local results cleared", Toast.LENGTH_SHORT).show()
            })
            addView(labButton("Emergency stop / safe reset") {
                protectedMode = true
                hideOverlays = true
                applyOverlayHiding()
                sendBroadcast(Intent("my.edu.unikl.tapsafe.overlay.STOP_OVERLAY").setPackage("my.edu.unikl.tapsafe.overlay"))
                renderActionButton()
                refreshControlLabels()
                ResultStore.record(this@MainActivity, "EMERGENCY_STOP", "Overlay stop requested; protection enabled")
                status.text = "Emergency stop sent. Protected mode is enabled."
            })
        }

    /** Rebuilds the lab-controls section so toggle button labels reflect current state. */
    private fun refreshControlLabels() {
        root.removeViewAt(root.childCount - 1)
        root.addView(buildLabControls())
    }

    private fun renderActionButton() {
        actionArea.removeAllViews()
        val button: Button = if (protectedMode) {
            SecureActionButton(this) { reason ->
                ResultStore.record(this, reason, "Protected transfer button rejected obscured touch")
                status.text = "BLOCKED: $reason"
            }.apply {
                text = "Confirm Transfer (PROTECTED)"
                setBackgroundColor(bankAccent)
                setTextColor(Color.WHITE)
            }
        } else {
            Button(this).apply {
                text = "Confirm Transfer"
                isAllCaps = false
                setBackgroundColor(bankAccent)
                setTextColor(Color.WHITE)
            }
        }
        button.setOnClickListener {
            actionCount += 1
            ResultStore.record(this, "ACTION_ACCEPTED", "Dummy transfer count=$actionCount; protected=$protectedMode")
            status.text = "Dummy transfer accepted (no real money moved). Count: $actionCount"
        }
        actionArea.addView(button, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 160
        ).apply { topMargin = 20 })
    }

    private fun applyOverlayHiding() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(hideOverlays)
        } else if (hideOverlays) {
            Toast.makeText(this, "Hide overlays requires Android 12 or newer", Toast.LENGTH_LONG).show()
            hideOverlays = false
        }
    }

    private fun launchOverlayDemo() {
        val launch = packageManager.getLaunchIntentForPackage("my.edu.unikl.tapsafe.overlay")
        if (launch == null) {
            Toast.makeText(this, "Install TapSafe Overlay Demo first", Toast.LENGTH_LONG).show()
        } else {
            startActivity(launch)
        }
    }

    private fun showResults() {
        AlertDialog.Builder(this)
            .setTitle("Local TapSafe test results")
            .setMessage(ResultStore.read(this))
            .setPositiveButton("CLOSE", null)
            .show()
    }

    // --------------------------------------------------------------------------------------
    // Small view-building helpers
    // --------------------------------------------------------------------------------------

    private fun card(): LinearLayout {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 28)
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            background = roundedDrawable(cardWhite, 24f)
            elevation = 6f
        }
        outer.addView(inner, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        return outer
    }

    private fun roundedDrawable(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }

    private fun textView(value: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 6, 0, 6)
        }

    private fun fieldLabel(value: String): TextView =
        textView(value, 12f, textMuted, false).apply { setPadding(0, 14, 0, 2) }

    /** A dummy, pre-filled, disabled field: looks real but accepts no real input or submission. */
    private fun fakeField(prefill: String, inputType: Int = InputType.TYPE_CLASS_TEXT): EditText =
        EditText(this).apply {
            setText(prefill)
            isEnabled = false
            this.inputType = inputType
            setTextColor(textDark)
            setBackgroundColor(Color.rgb(246, 248, 247))
            setPadding(20, 16, 20, 16)
        }

    private fun labButton(title: String, action: () -> Unit): Button =
        Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 12f
            setOnClickListener { action() }
        }

    companion object {
        const val ACTION_HIDDEN_TAP = "my.edu.unikl.tapsafe.overlay.HIDDEN_TAP"

        // ---- Edit these to change what the demo bank screen displays ----------------------
        // All values here are dummy/fictional. Nothing on screen triggers a real transaction
        // regardless of what these say.
        const val BANK_NAME = "JuneBank (Demo)"
        const val DUMMY_BALANCE = "RM 10,099.10"
        const val DUMMY_ACCOUNT_MASKED = "**** **** 8899"
        const val DUMMY_RECIPIENT_NAME = "Fahim Syamil (dummy)"
        const val DUMMY_RECIPIENT_ACCOUNT = "123412341234 (dummy)"
        const val DUMMY_TRANSFER_AMOUNT = "99.10"
    }
}

