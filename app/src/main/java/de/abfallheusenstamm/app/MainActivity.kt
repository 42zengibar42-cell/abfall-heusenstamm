package de.abfallheusenstamm.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> refreshJs() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Reminders.ensureChannel(this)
        window.statusBarColor = Color.parseColor("#3F4F8C")
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#F4F2EC"))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.textZoom = 100
        web.addJavascriptInterface(Bridge(), "Android")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.scheme == "file") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, url)) } catch (_: Exception) {}
                return true
            }
        }
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("window.goBack && window.goBack()") { r ->
                    if (r != "true") finish()
                }
            }
        })
        Reminders.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        refreshJs()
    }

    private fun refreshJs() {
        if (::web.isInitialized) web.post { web.evaluateJavascript("window.onResume && window.onResume()", null) }
    }

    inner class Bridge {
        @JavascriptInterface
        fun getData(): String = assets.open("data.json").bufferedReader(Charsets.UTF_8).use { it.readText() }

        @JavascriptInterface
        fun getSettings(): String = Reminders.settingsJson(this@MainActivity) ?: "{}"

        @JavascriptInterface
        fun saveSettings(json: String) {
            Reminders.saveSettings(this@MainActivity, json)
            Reminders.schedule(this@MainActivity)
        }

        @JavascriptInterface
        fun notificationsAllowed(): Boolean = Reminders.notificationsAllowed(this@MainActivity)

        @JavascriptInterface
        fun requestNotifications() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= 33 && !Reminders.notificationsAllowed(this@MainActivity) &&
                    shouldAskRuntime()
                ) {
                    permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (!Reminders.notificationsAllowed(this@MainActivity)) {
                    openNotificationSettings()
                }
            }
        }

        @JavascriptInterface
        fun canExactAlarm(): Boolean = Reminders.canExact(this@MainActivity)

        @JavascriptInterface
        fun openExactAlarmSettings() {
            if (Build.VERSION.SDK_INT >= 31) runOnUiThread {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                } catch (_: Exception) {}
            }
        }

        @JavascriptInterface
        fun testNotification(title: String, text: String) {
            if (!Reminders.notificationsAllowed(this@MainActivity)) { requestNotifications(); return }
            Reminders.notify(this@MainActivity, title, text, 200)
        }

        @JavascriptInterface
        fun appVersion(): String = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (_: Exception) { "" }
    }

    // Beim ersten Mal den System-Dialog zeigen; wurde er schon abgelehnt, die Einstellungen öffnen
    private fun shouldAskRuntime(): Boolean {
        val p = getSharedPreferences(Reminders.PREFS, MODE_PRIVATE)
        val asked = p.getInt("permAsked", 0)
        if (asked < 2 || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            p.edit().putInt("permAsked", asked + 1).apply()
            return true
        }
        return false
    }

    private fun openNotificationSettings() {
        try {
            val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(i)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }
}
