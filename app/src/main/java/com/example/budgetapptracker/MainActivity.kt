package com.example.budgetapptracker

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            BudgetAppTrackerTheme {
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