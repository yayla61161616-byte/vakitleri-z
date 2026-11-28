package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.PrayerDay
import com.example.domain.PrayerTimeCalculator
import com.example.domain.PrayerType
import com.example.ui.HomeUiState
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.HeroGradientEnd
import com.example.ui.theme.HeroGradientMid
import com.example.ui.theme.HeroGradientStart

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    isDetectingLocation: Boolean,
    locationDetectMessage: String?,
    onOpenLocationPicker: () -> Unit,
    onDetectLocationClick: () -> Unit,
    onDismissLocationMessage: () -> Unit,
    onRefresh: () -> Unit,
    onTogglePrayerBell: (PrayerType, Boolean) -> Unit,
    onStopSound: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val isWide = maxWidth >= 600.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                HeroCountdownCard(
                    uiState = uiState,
                    isDetectingLocation = isDetectingLocation,
                    onOpenLocationPicker = onOpenLocationPicker,
                    onDetectLocationClick = onDetectLocationClick,
                    onRefresh = onRefresh
                )
            }

            if (locationDetectMessage != null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onDismissLocationMessage() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = locationDetectMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (uiState.isSoundPlaying) {
                item {
                    PlayingSoundBanner(onStopSound = onStopSound)
                }
            }

            if (uiState.statusMessage != null) {
                item {
                    OfflineNoticeBanner(
                        message = uiState.statusMessage,
                        onRetry = onRefresh
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    item {
                        LoadingPrayerStateCard()
                    }
                }

                uiState.today == null -> {
                    item {
                        EmptyPrayerStateCard(onRetry = onRefresh)
                    }
                }

                else -> {
                    item {
                        SectionHeaderRow(
                            sourceLabel = uiState.today.sourceLabel,
                            usedCache = uiState.usedCacheFallback
                        )
                    }

                    val prayers = PrayerType.entries
                    if (isWide) {
                        val rows = prayers.chunked(2)
                        items(rows.size) { rowIndex ->
                            val rowItems = rows[rowIndex]
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { type ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        PrayerTimeCard(
                                            type = type,
                                            today = uiState.today,
                                            isCurrent = uiState.countdown?.currentPrayer == type,
                                            isNext = uiState.countdown?.nextPrayer == type,
                                            isBellEnabled = uiState.preferences.isEnabled(type),
                                            onToggleBell = { onTogglePrayerBell(type, !uiState.preferences.isEnabled(type)) }
                                        )
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(prayers.size) { index ->
                            val type = prayers[index]
                            PrayerTimeCard(
                                type = type,
                                today = uiState.today,
                                isCurrent = uiState.countdown?.currentPrayer == type,
                                isNext = uiState.countdown?.nextPrayer == type,
                                isBellEnabled = uiState.preferences.isEnabled(type),
                                onToggleBell = { onTogglePrayerBell(type, !uiState.preferences.isEnabled(type)) }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun HeroCountdownCard(
    uiState: HomeUiState,
    isDetectingLocation: Boolean,
    onOpenLocationPicker: () -> Unit,
    onDetectLocationClick: () -> Unit,
    onRefresh: () -> Unit
) {
    val countdown = uiState.countdown
    val animatedProgress by animateFloatAsState(
        targetValue = countdown?.intervalProgress ?: 0f,
        animationSpec = spring(),
        label = "countdown_progress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(HeroGradientStart, HeroGradientMid, HeroGradientEnd)
                    )
                )
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_crescent),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.36f,
                modifier = Modifier.matchParentSize()
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                HeroGradientStart.copy(alpha = 0.85f),
                                HeroGradientMid.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Location Pill + Refresh
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onOpenLocationPicker,
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .minimumInteractiveComponentSize()
                            .testTag("location_selector_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = stringResource(R.string.action_select_location),
                                tint = GoldAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = uiState.location.formattedDisplay(),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.14f)
                        ) {
                            IconButton(
                                onClick = onDetectLocationClick,
                                enabled = !isDetectingLocation,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("home_gps_button")
                            ) {
                                if (isDetectingLocation) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = GoldAccent,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.MyLocation,
                                        contentDescription = stringResource(R.string.action_detect_location),
                                        tint = GoldAccent
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.14f)
                        ) {
                            IconButton(
                                onClick = onRefresh,
                                enabled = !uiState.isRefreshing,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("refresh_button")
                            ) {
                                if (uiState.isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = GoldAccent,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = stringResource(R.string.action_refresh),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // Miladi & Hicri Dates
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = uiState.today?.miladiLong ?: "—",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.94f),
                        fontWeight = FontWeight.Medium
                    )
                    if (!uiState.today?.hicriLong.isNullOrBlank()) {
                        Text(
                            text = uiState.today?.hicriLong.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = GoldAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Live Countdown
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val countdownTitle = if (countdown != null) {
                        if (countdown.isNextTomorrow) {
                            stringResource(R.string.countdown_tomorrow_format, countdown.nextPrayer.displayName)
                        } else {
                            stringResource(R.string.countdown_label_format, countdown.nextPrayer.displayName)
                        }
                    } else {
                        "Vakit bekleniyor"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = countdownTitle,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.80f)
                            )
                            Text(
                                text = countdown?.let { PrayerTimeCalculator.formatCountdown(it.remainingMillis) }
                                    ?: "--:--:--",
                                style = MaterialTheme.typography.displayLarge,
                                color = Color.White,
                                modifier = Modifier.testTag("countdown_timer_text")
                            )
                        }

                        if (countdown != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = GoldAccent.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.45f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = countdown.nextPrayer.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GoldAccent
                                    )
                                    Text(
                                        text = countdown.nextPrayerTimeText,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = GoldAccent,
                        trackColor = Color.White.copy(alpha = 0.18f),
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayingSoundBanner(onStopSound: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.sound_playing_banner),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Button(
                onClick = onStopSound,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("home_stop_sound_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.StopCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.action_stop_sound))
            }
        }
    }
}

@Composable
private fun OfflineNoticeBanner(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(text = "Tekrar Dene", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    sourceLabel: String,
    usedCache: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Günün Namaz Vakitleri",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Surface(
            shape = RoundedCornerShape(50),
            color = if (usedCache) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (usedCache) Icons.Rounded.CloudOff else Icons.Rounded.Verified,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (usedCache) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
                Text(
                    text = if (usedCache) stringResource(R.string.badge_offline_cache) else sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (usedCache) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PrayerTimeCard(
    type: PrayerType,
    today: PrayerDay,
    isCurrent: Boolean,
    isNext: Boolean,
    isBellEnabled: Boolean,
    onToggleBell: () -> Unit
) {
    val containerColor = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        isNext -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderStroke = when {
        isCurrent -> BorderStroke(1.8.dp, MaterialTheme.colorScheme.primary)
        isNext -> BorderStroke(1.2.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    val contentColor = when {
        isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("prayer_card_${type.key}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = borderStroke,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconForPrayer(type),
                        contentDescription = type.displayName,
                        tint = if (isCurrent) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = type.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold
                    )

                    AnimatedVisibility(visible = isCurrent || isNext) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        ) {
                            Text(
                                text = if (isCurrent) {
                                    stringResource(R.string.status_current_prayer)
                                } else {
                                    stringResource(R.string.status_next_prayer)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSecondary
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = today.timeFor(type),
                style = MaterialTheme.typography.headlineSmall,
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    contentColor
                },
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onToggleBell,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("prayer_bell_${type.key}")
            ) {
                Icon(
                    imageVector = if (isBellEnabled) {
                        Icons.Filled.NotificationsActive
                    } else {
                        Icons.Outlined.NotificationsOff
                    },
                    contentDescription = "${type.displayName} bildirimi ${if (isBellEnabled) "açık" else "kapalı"}",
                    tint = if (isBellEnabled) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun LoadingPrayerStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = "Diyanet namaz vakitleri yükleniyor…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyPrayerStateCard(onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(42.dp)
            )
            Text(
                text = stringResource(R.string.error_no_cached_data),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(text = stringResource(R.string.action_refresh))
            }
        }
    }
}

private fun iconForPrayer(type: PrayerType): ImageVector = when (type) {
    PrayerType.IMSAK -> Icons.Rounded.NightsStay
    PrayerType.GUNES -> Icons.Rounded.WbTwilight
    PrayerType.OGLE -> Icons.Rounded.WbSunny
    PrayerType.IKINDI -> Icons.Rounded.LightMode
    PrayerType.AKSAM -> Icons.Rounded.Bedtime
    PrayerType.YATSI -> Icons.Rounded.DarkMode
}
