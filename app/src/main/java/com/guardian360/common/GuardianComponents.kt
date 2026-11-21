package com.guardian360.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.RoyalCobaltMid
import com.example.ui.theme.RoyalCobaltStart
import com.example.ui.theme.RoyalIndigoEnd
import com.example.ui.theme.SapphireTrust
import com.example.ui.theme.SecureGreen

@Composable
fun GuardianTopHeaderBar(
    title: String,
    subtitle: String = "Advanced Parental Control",
    showBackButton: Boolean = false,
    showDrawerButton: Boolean = false,
    showAlertsButton: Boolean = true,
    unreadAlertsCount: Int = 0,
    onBackClick: () -> Unit = {},
    onDrawerClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onAlertsClick: () -> Unit = {},
    onEmergencyClick: (() -> Unit)? = null,
    extraActions: @Composable RowScope.() -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(RoyalCobaltStart, RoyalCobaltMid, RoyalIndigoEnd)
                ),
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
            )
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(48.dp).testTag("header_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            } else if (showDrawerButton) {
                IconButton(
                    onClick = onDrawerClick,
                    modifier = Modifier.size(48.dp).testTag("parent_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = "Open Navigation Menu",
                        tint = Color.White
                    )
                }
            }

            // Shield + Verified Checkmark emblem matching Guardian360 brand header
            Box(modifier = Modifier.padding(end = 10.dp)) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = SecureGreen,
                    border = BorderStroke(1.5.dp, Color.White),
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onEmergencyClick != null) {
                IconButton(
                    onClick = onEmergencyClick,
                    modifier = Modifier.size(48.dp).testTag("header_emergency_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = "Emergency Control",
                        tint = Color(0xFFFCA5A5)
                    )
                }
            }

            if (showAlertsButton) {
                IconButton(
                    onClick = onAlertsClick,
                    modifier = Modifier.size(48.dp).testTag("parent_topbar_notifications")
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadAlertsCount > 0) {
                                Badge(containerColor = DangerCrimson, contentColor = Color.White) {
                                    Text(unreadAlertsCount.coerceAtMost(99).toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = "Notifications",
                            tint = Color.White
                        )
                    }
                }
            }

            if (showBackButton) {
                IconButton(
                    onClick = onHomeClick,
                    modifier = Modifier.size(48.dp).testTag("header_home_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Home,
                        contentDescription = "Home Dashboard",
                        tint = Color.White
                    )
                }
            }

            extraActions()
        }
    }
}

@Composable
fun CircularUsageRing(
    usedMinutes: Long,
    limitMinutes: Long,
    centerPrimaryText: String,
    centerSecondaryText: String,
    ringSize: Dp = 120.dp,
    strokeWidth: Dp = 12.dp,
    activeColor: Color = SapphireTrust,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val ratio = if (limitMinutes > 0L) (usedMinutes.toFloat() / limitMinutes.toFloat()).coerceIn(0f, 1f) else 0f
    Box(
        modifier = Modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(ringSize)) {
            val strokePx = strokeWidth.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = 360f * ratio,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerPrimaryText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = centerSecondaryText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun GuardianCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmptyStatePanel(
    icon: ImageVector = Icons.Rounded.Shield,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    GuardianCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorBanner(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
fun LoadingPanel(label: String = "Loading Guardian360...") {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun QrCodeView(
    payload: String,
    size: Dp = 200.dp,
    modifier: Modifier = Modifier
) {
    val matrix = remember(payload) { QrCodeGenerator.generateMatrix(payload) }
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Canvas(
            modifier = Modifier
                .padding(14.dp)
                .size(size - 28.dp)
        ) {
            val n = matrix.size
            val cell = this.size.width / n
            for (r in 0 until n) {
                for (c in 0 until n) {
                    if (matrix[r][c]) {
                        drawRect(
                            color = Color(0xFF0F172A),
                            topLeft = Offset(c * cell, r * cell),
                            size = Size(cell + 0.5f, cell + 0.5f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Custom 0-9 Numeric Keypad Dialog matching Guardian360 Emergency Override & Security PIN UI.
 */
@Composable
fun PinKeypadDialog(
    title: String = "Enter Parent PIN",
    subtitle: String = "Verify Parent PIN to unlock settings or trigger override",
    storedPinHash: String?,
    onVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun verifyAndSubmit() {
        if (storedPinHash.isNullOrBlank() || SecurityUtils.verifyPin(pinInput, storedPinHash)) {
            onVerified()
        } else {
            errorMsg = "Incorrect PIN. Please try again."
            pinInput = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Lock, contentDescription = "Security PIN", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (storedPinHash.isNullOrBlank()) {
                        "No Security PIN configured yet. Set a 4-6 digit PIN in Settings -> Security PIN."
                    } else subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!storedPinHash.isNullOrBlank()) {
                    // PIN Indicator Dots
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (i in 0 until 6) {
                            val filled = i < pinInput.length
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (filled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                            )
                        }
                    }

                    if (errorMsg != null) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    // 0-9 Numeric Keypad Grid
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("DEL", "0", "OK")
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                            ) {
                                row.forEach { key ->
                                    val isOk = key == "OK"
                                    val isDel = key == "DEL"
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = when {
                                            isOk -> SecureGreen
                                            isDel -> MaterialTheme.colorScheme.surfaceVariant
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                        },
                                        modifier = Modifier
                                            .size(width = 72.dp, height = 50.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable {
                                                errorMsg = null
                                                when (key) {
                                                    "DEL" -> if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                                                    "OK" -> verifyAndSubmit()
                                                    else -> if (pinInput.length < 6) pinInput += key
                                                }
                                            }
                                            .testTag("pin_key_$key")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            when (key) {
                                                "DEL" -> Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Backspace")
                                                "OK" -> Icon(Icons.Rounded.Check, contentDescription = "Verify", tint = Color.White)
                                                else -> Text(key, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (storedPinHash.isNullOrBlank()) {
                Button(onClick = onVerified, modifier = Modifier.testTag("pin_verify_confirm")) {
                    Text("Continue")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PinVerificationDialog(
    title: String = "Parent Security Verification",
    storedPinHash: String?,
    onVerified: () -> Unit,
    onDismiss: () -> Unit
) {
    PinKeypadDialog(
        title = title,
        storedPinHash = storedPinHash,
        onVerified = onVerified,
        onDismiss = onDismiss
    )
}
