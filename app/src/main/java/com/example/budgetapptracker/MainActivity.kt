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

// FragmentActivity (instead of ComponentActivity) is required by the fingerprint prompt.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

                // Lock again whenever the app leaves the screen, but not on rotation.
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                    if (!isChangingConfigurations) unlocked = false
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
                    else -> AppRoot()
                }
            }
        }
    }
}
