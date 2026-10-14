package com.example.budgetapptracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.budgetapptracker.BudgetApp
import com.example.budgetapptracker.MainActivity
import com.example.budgetapptracker.data.EnvelopeSummary
import com.example.budgetapptracker.util.toRupeeString
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import java.time.YearMonth

// The app's colours, fixed here because a widget is drawn by the home screen, not by the app.
private val Plum = Color(0xFF2A1245)
private val Mango = Color(0xFFFFB547)
private val Pink = Color(0xFFFF6B9A)
private val Soft = Color(0xCCFFFFFF)

// Everything the widget shows, worked out from this month's envelopes.
private data class WidgetState(
    val locked: Boolean,
    val leftPaise: Long,
    val safeTodayPaise: Long,
    val daysLeft: Int,
    val envelopes: List<EnvelopeSummary>,
)

// "Left to spend" on the home screen, built with Jetpack Glance (Compose-style code for widgets).
class BudgetWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as BudgetApp
        val envelopes = app.repository.observeEnvelopes(YearMonth.now()).first()
        val today = LocalDate.now()
        val daysLeft = today.lengthOfMonth() - today.dayOfMonth + 1
        val left = envelopes.sumOf { it.monthlyBudgetPaise } - envelopes.sumOf { it.spentPaise }
        val state = WidgetState(
            // With the app lock on, amounts stay off the home screen.
            locked = app.userPrefs.lockEnabled.value,
            leftPaise = left,
            safeTodayPaise = if (left > 0) left / daysLeft else 0,
            daysLeft = daysLeft,
            envelopes = envelopes.filter { it.monthlyBudgetPaise > 0 }.sortedBy { it.remainingPaise }.take(3),
        )
        provideContent { WidgetContent(state) }
    }
}

@Composable
private fun WidgetContent(state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Plum)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text("Left to spend", style = TextStyle(color = ColorProvider(Soft), fontSize = 12.sp))
        if (state.locked) {
            Text(
                "Locked · tap to open",
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
            return@Column
        }
        Text(
            state.leftPaise.toRupeeString(),
            style = TextStyle(
                color = ColorProvider(if (state.leftPaise >= 0) Mango else Pink),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            "${state.safeTodayPaise.toRupeeString()} a day · ${state.daysLeft} days left",
            style = TextStyle(color = ColorProvider(Soft), fontSize = 12.sp),
        )
        Spacer(GlanceModifier.height(8.dp))
        // The three envelopes closest to running out.
        state.envelopes.forEach { envelope ->
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    envelope.name,
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp),
                )
                Text(
                    envelope.remainingPaise.toRupeeString(),
                    style = TextStyle(
                        color = ColorProvider(if (envelope.remainingPaise >= 0) Color.White else Pink),
                        fontSize = 13.sp,
                    ),
                )
            }
        }
    }
}

// Android finds the widget through this receiver, named in the manifest.
class BudgetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BudgetWidget()
}