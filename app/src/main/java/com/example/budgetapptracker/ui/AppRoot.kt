package com.example.budgetapptracker.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.budgetapptracker.ui.home.HomeAction
import com.example.budgetapptracker.ui.theme.LocalAppColors
import com.example.budgetapptracker.ui.home.HomeScreen
import com.example.budgetapptracker.ui.insights.InsightsScreen
import com.example.budgetapptracker.ui.transactions.TransactionsScreen
import androidx.compose.material.icons.rounded.Group
import com.example.budgetapptracker.ui.people.PeopleScreen
import androidx.compose.runtime.LaunchedEffect

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Activity("Activity", Icons.AutoMirrored.Rounded.ReceiptLong),
    People("People", Icons.Rounded.Group),
    Insights("Insights", Icons.Rounded.Insights),
}

@Composable
fun AppRoot(
    expenseToOpen: String? = null,
    onExpenseOpened: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var pendingAction by remember { mutableStateOf<HomeAction?>(null) }

    // A tapped notification jumps to Activity, which then opens that expense.
    LaunchedEffect(expenseToOpen) {
        if (expenseToOpen != null) tab = Tab.Activity
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            BottomBar(
                selected = tab,
                onSelect = { tab = it },
                onAction = { action ->
                    tab = Tab.Home
                    pendingAction = action
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)) {
            Crossfade(targetState = tab, animationSpec = tween(200), label = "tab") { current ->
                when (current) {
                    Tab.Home -> HomeScreen(
                        action = pendingAction,
                        onActionHandled = { pendingAction = null },
                    )
                    Tab.Activity -> TransactionsScreen(
                        expenseToOpen = expenseToOpen,
                        onExpenseOpened = onExpenseOpened,
                    )
                    Tab.People -> PeopleScreen()
                    Tab.Insights -> InsightsScreen()
                }
            }
        }
    }
}

@Composable
private fun BottomBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    onAction: (HomeAction) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(color = LocalAppColors.current.navBar) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarItem(tab = Tab.Home, selected = selected == Tab.Home, onClick = { onSelect(Tab.Home) })
            BarItem(
                tab = Tab.Activity,
                selected = selected == Tab.Activity,
                onClick = { onSelect(Tab.Activity) },
            )

            Box {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(role = Role.Button, onClickLabel = "Add") { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(30.dp),
                    )
                }
                if (menuOpen) {
                    AddSheet(
                        onDismiss = { menuOpen = false },
                        onPick = { action ->
                            menuOpen = false
                            onAction(action)
                        },
                    )
                }
            }
            BarItem(
                tab = Tab.People,
                selected = selected == Tab.People,
                onClick = { onSelect(Tab.People) },
            )

            BarItem(
                tab = Tab.Insights,
                selected = selected == Tab.Insights,
                onClick = { onSelect(Tab.Insights) },
            )
        }
    }
}

@Composable
private fun BarItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(imageVector = tab.icon, contentDescription = null, tint = color)
        Text(text = tab.label, color = color, style = MaterialTheme.typography.labelMedium)
    }
}