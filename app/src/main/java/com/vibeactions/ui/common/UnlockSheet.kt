package com.vibeactions.ui.common

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibeactions.R
import com.vibeactions.billing.BillingManager
import com.vibeactions.billing.License
import com.vibeactions.billing.LicenseStatus
import com.vibeactions.billing.TRIAL_DAYS
import com.vibeactions.ui.theme.*

/** The app's [BillingManager], provided by MainActivity; null in previews. */
val LocalBilling = staticCompositionLocalOf<BillingManager?> { null }

/** "Unlock for 49,00 kr." with Play's localized price, or the USD fallback until Play answers. */
@Composable
fun unlockLabel(): String {
    val price by (LocalBilling.current?.price ?: return "Unlock TextFox").collectAsState()
    return "Unlock for ${price ?: BillingManager.FALLBACK_PRICE}"
}

/** The purchase sheet: what the trial means, the price, and the Play purchase button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val billing = LocalBilling.current
    val status by License.status.collectAsState()
    val pending by (billing?.pending ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    LaunchedEffect(Unit) { License.refresh(context); billing?.refresh() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151A17),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FoxBadge(72.dp)
            Spacer(Modifier.height(18.dp))
            val (title, body) = when (val s = status) {
                LicenseStatus.Expired -> "Your macros are paused" to
                    "The $TRIAL_DAYS-day free trial has ended. Your macros, folders and log are all still here. " +
                    "Unlock TextFox and they start running again."
                is LicenseStatus.Trial -> (if (s.daysLeft == 1) "Last day of your free trial" else "${s.daysLeft} days left of your free trial") to
                    "Everything works during the trial. Unlock TextFox once to keep your macros running after it."
                LicenseStatus.Unlocked -> "TextFox is unlocked" to
                    "Thank you for supporting TextFox. Every feature and future update is yours."
            }
            Text(title, color = OnSurface, fontFamily = DmSans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(body, color = OnSurfaceVariant, fontSize = 14.5.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)

            if (status != LicenseStatus.Unlocked) {
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Perk("One-time purchase. No subscription.")
                    Perk("Every feature and every future update.")
                    Perk("AI runs on your own free Gemini key, so there are no AI fees.")
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { (context as? Activity)?.let { billing?.launchPurchase(it) } },
                    enabled = billing != null && !pending,
                    colors = ButtonDefaults.buttonColors(containerColor = FoxGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomStart = 6.dp, bottomEnd = 18.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Text(
                        if (pending) "Payment pending…" else unlockLabel(),
                        fontFamily = DmSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                    )
                }
                if (pending) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "TextFox unlocks as soon as Google confirms the payment.",
                        color = OnSurfaceVariant, fontSize = 12.5.sp, textAlign = TextAlign.Center
                    )
                }
                TextButton(onClick = { billing?.refresh(announce = true) }) {
                    Text("Restore purchase", color = OnSurfaceVariant)
                }
                Text(
                    "Paid through Google Play. The purchase belongs to your Google account, so it moves with you to a new phone.",
                    color = OnSurfaceVariant.copy(alpha = 0.7f), fontSize = 11.5.sp, lineHeight = 16.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = onDismiss) { Text("Close", color = FoxGreen) }
            }
        }
    }
}

@Composable
private fun Perk(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(FoxGreen.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Check, null, tint = FoxGreen, modifier = Modifier.size(14.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(text, color = OnSurface, fontSize = 14.sp, lineHeight = 19.sp)
    }
}

/** The launcher icon's fox on its green disc, at [size]. */
@Composable
fun FoxBadge(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(FoxGreen), contentAlignment = Alignment.Center) {
        // The adaptive-icon foreground is 108 units with the visible disc at 72: scale to match.
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(size * 1.5f))
    }
}

/**
 * The list's trial banner: in the last [License.WARN_DAYS] days, and in red once the trial is over.
 * Nothing before that, and nothing once bought.
 */
@Composable
fun TrialBanner(onUnlock: () -> Unit) {
    val status by License.status.collectAsState()
    when (val s = status) {
        LicenseStatus.Expired ->
            LicenseBanner("Free trial ended. Your macros are paused until you unlock TextFox.", ErrorRed, onUnlock)
        is LicenseStatus.Trial -> if (s.daysLeft <= License.WARN_DAYS) LicenseBanner(
            if (s.daysLeft == 1) "Last day of your free trial." else "${s.daysLeft} days left of your free trial.",
            FoxGreen, onUnlock
        )
        LicenseStatus.Unlocked -> Unit
    }
}

@Composable
private fun LicenseBanner(text: String, accent: Color, onUnlock: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            // Solid, like the cards: a tint alone vanishes on the green backgrounds.
            .background(Color(0xF0151A17))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onUnlock)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ic_stat_textfox), null, tint = accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = OnSurface, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        TextButton(onClick = onUnlock) { Text("Unlock", color = FoxGreen) }
    }
}
