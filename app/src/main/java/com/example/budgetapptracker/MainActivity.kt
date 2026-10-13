package com.example.budgetapptracker

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.budgetapptracker.data.ThemeMode
import com.example.budgetapptracker.security.canUseAppLock
import com.example.budgetapptracker.security.promptUnlock
import com.example.budgetapptracker.ui.AppRoot
import com.example.budgetapptracker.ui.start.LockScreen
import com.example.budgetapptracker.ui.start.WelcomeScreen
import com.example.budgetapptracker.ui.theme.BudgetAppTrackerTheme
import android.os.SystemClock
import android.content.Intent
import com.example.budgetapptracker.notify.EXTRA_TRANSACTION_ID
private const val LOCK_AFTER_MILLIS = 60 * 1_000L
// FragmentActivity (instead of ComponentActivity) is required by the fingerprint prompt.
class MainActivity : FragmentActivity() {
    // The expense whose notification was tapped, until its envelope chooser has opened.
    private var expenseToOpen by mutableStateOf<String?>(null)

    // Called when a notification is tapped while the app is already open.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readTappedExpense(intent)
    }

    private fun readTappedExpense(intent: Intent) {
        val id = intent.getStringExtra(EXTRA_TRANSACTION_ID) ?: return
        expenseToOpen = id
        // Forget it, so rotating the screen does not open the chooser a second time.
        intent.removeExtra(EXTRA_TRANSACTION_ID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readTappedExpense(intent)
        val userPrefs = (application as BudgetApp).userPrefs

        setContent {
            val themeMode by userPrefs.themeMode.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.System -> systemDark
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            // Keeps the status bar icons readable: light icons on dark, dark icons on light.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
                onDispose {}
            }

            BudgetAppTrackerTheme(darkTheme = darkTheme) {
                val name by userPrefs.name.collectAsStateWithLifecycle()
                val lockEnabled by userPrefs.lockEnabled.collectAsStateWithLifecycle()
                val lockAvailable = remember { canUseAppLock(this@MainActivity) }
                var unlocked by rememberSaveable { mutableStateOf(false) }

                // Lock again after the app has been away for a while. A short trip to the
                // QR scanner or a payment app does not count, and neither does rotation.
                var leftAt by rememberSaveable { mutableStateOf(0L) }
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                    if (!isChangingConfigurations) leftAt = SystemClock.elapsedRealtime()
                }
                LifecycleEventEffect(Lifecycle.Event.ON_START) {
                    if (leftAt != 0L && SystemClock.elapsedRealtime() - leftAt > LOCK_AFTER_MILLIS) {
                        unlocked = false
                    }
                    leftAt = 0L
                }

                when {
                    name == null -> WelcomeScreen(
                        lockAvailable = lockAvailable,
                        onDone = { chosenName, chosenLock ->
                            userPrefs.save(chosenName, chosenLock)
                            unlocked = true
                        },
                    )
                    lockEnabled && lockAvailable && !unlocked -> LockScreen(
                        onUnlock = { promptUnlock { unlocked = true } },
                    )
                    else -> AppRoot(
                        expenseToOpen = expenseToOpen,
                        onExpenseOpened = { expenseToOpen = null },
                    )
                }
            }
        }
    }
}
