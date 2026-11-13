package pl.aleks.starttv

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updatePadding
import pl.aleks.starttv.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyKeylineInsets()
        configureTiles()
    }

    override fun onResume() {
        super.onResume()
        if (currentFocus == null) {
            binding.tileMagentaTv.requestFocus()
        }
    }

    private fun configureTiles() {
        binding.tileMagentaTv.apply {
            disableDefaultFocusHighlight()
            setOnClickListener { openMagentaTv() }
            doOnAttach { it.requestFocus() }
        }
        binding.tileWpolsce24.apply {
            disableDefaultFocusHighlight()
            setOnClickListener { openWPolsce24() }
        }
    }

    private fun applyKeylineInsets() {
        val keyline = resources.getDimensionPixelSize(R.dimen.screen_keyline)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val safeArea = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(
                left = keyline + safeArea.left,
                top = keyline + safeArea.top,
                right = keyline + safeArea.right,
                bottom = keyline + safeArea.bottom
            )
            insets
        }
    }

    private fun openMagentaTv() {
        val launchIntent = packageManager.getLaunchIntentForPackage(MAGENTA_TV_PACKAGE)
        if (launchIntent != null && startActivityIfPossible(launchIntent)) return

        Toast.makeText(this, R.string.error_magenta_tv_not_installed, Toast.LENGTH_LONG).show()
        val storeIntent = Intent(Intent.ACTION_VIEW, "$MARKET_URI_PREFIX$MAGENTA_TV_PACKAGE".toUri())
        val marketFallbackIntent = Intent(Intent.ACTION_VIEW, PLAY_STORE_FALLBACK_URL.toUri())
        if (!startActivityIfPossible(storeIntent) && !startActivityIfPossible(marketFallbackIntent)) {
            Toast.makeText(this, R.string.error_no_app_to_open_store, Toast.LENGTH_LONG).show()
        }
    }

    private fun openWPolsce24() {
        val streamUri = WPOLSCE24_STREAM_URL.toUri()
        val youtubeTvIntent = Intent(Intent.ACTION_VIEW, streamUri).setPackage(YOUTUBE_TV_PACKAGE)
        if (startActivityIfPossible(youtubeTvIntent)) return

        if (!startActivityIfPossible(Intent(Intent.ACTION_VIEW, streamUri))) {
            Toast.makeText(this, R.string.error_no_app_to_open_link, Toast.LENGTH_LONG).show()
        }
    }

    private fun startActivityIfPossible(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    private fun View.disableDefaultFocusHighlight() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
    }

    private companion object {
        const val MAGENTA_TV_PACKAGE = "com.pl.tmobile.magentatv"
        const val YOUTUBE_TV_PACKAGE = "com.google.android.youtube.tv"
        const val WPOLSCE24_STREAM_URL = "https://www.youtube.com/@TelewizjawPolsce24/live"
        const val MARKET_URI_PREFIX = "market://details?id="
        const val PLAY_STORE_FALLBACK_URL =
            "https://play.google.com/store/apps/details?id=$MAGENTA_TV_PACKAGE"
    }
}
