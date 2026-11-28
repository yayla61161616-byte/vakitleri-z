package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.PrayerDay
import com.example.domain.PrayerTimeCalculator
import com.example.domain.ReligiousDay
import com.example.domain.ReligiousDayCategory
import com.example.domain.ReligiousDaysProvider
import com.example.domain.SelectedLocation

private enum class ImsakiyeSubTab(val label: String) {
    MONTHLY("Aylık İmsakiye"),
    RELIGIOUS_DAYS("Dini Günler")
}

private enum class ReligiousFilter(val label: String) {
    UPCOMING("Yaklaşanlar"),
    YEAR_2026("2026"),
    YEAR_2027("2027")
}

@Composable
fun ImsakiyeScreen(
    location: SelectedLocation,
    monthlyDays: List<PrayerDay>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    var religiousFilter by remember { mutableStateOf(ReligiousFilter.UPCOMING) }
    val todayIso = remember { PrayerTimeCalculator.todayIsoDate() }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 900.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selectedSubTab == 0) "Aylık İmsakiye" else "Dini Günler Takvimi",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (selectedSubTab == 0) {
                                "${location.formattedDisplay()} • ${monthlyDays.size} günlük takvim"
                            } else {
                                "Diyanet İşleri Başkanlığı 2026 – 2027"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (selectedSubTab == 0) {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("imsakiye_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "İmsakiyeyi Yenile",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ImsakiyeSubTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = selectedSubTab == index,
                        onClick = { selectedSubTab = index },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ImsakiyeSubTab.entries.size
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag(if (index == 0) "tab_imsakiye" else "tab_religious_days")
                    ) {
                        Text(text = tab.label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (selectedSubTab == 0) {
                MonthlyImsakiyeList(
                    monthlyDays = monthlyDays,
                    todayIso = todayIso
                )
            } else {
                ReligiousDaysList(
                    selectedFilter = religiousFilter,
                    onFilterChange = { religiousFilter = it }
                )
            }
        }
    }
}

@Composable
private fun MonthlyImsakiyeList(
    monthlyDays: List<PrayerDay>,
    todayIso: String
) {
    if (monthlyDays.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "Aylık imsakiye verisi henüz yüklenmedi.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(monthlyDays, key = { "${it.districtId}_${it.isoDate}" }) { day ->
            val isToday = day.isoDate == todayIso
            val isPast = day.isoDate < todayIso
            MonthlyDayCard(
                day = day,
                isToday = isToday,
                isPast = isPast
            )
        }
    }
}

@Composable
private fun MonthlyDayCard(
    day: PrayerDay,
    isToday: Boolean,
    isPast: Boolean
) {
    val containerColor = when {
        isToday -> MaterialTheme.colorScheme.primaryContainer
        isPast -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.surface
    }
    val borderStroke = if (isToday) {
        BorderStroke(1.6.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }
    val textAlpha = if (isPast) 0.65f else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("imsakiye_day_${day.isoDate}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = borderStroke
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = day.miladiLong,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha)
                    )
                    if (day.hicriLong.isNotBlank()) {
                        Text(
                            text = day.hicriLong,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = textAlpha)
                        )
                    }
                }

                if (isToday) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "Bugün",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PrayerMiniColumn("İmsak", day.imsak, isToday, textAlpha, Modifier.weight(1f))
                PrayerMiniColumn("Güneş", day.gunes, isToday, textAlpha, Modifier.weight(1f))
                PrayerMiniColumn("Öğle", day.ogle, isToday, textAlpha, Modifier.weight(1f))
                PrayerMiniColumn("İkindi", day.ikindi, isToday, textAlpha, Modifier.weight(1f))
                PrayerMiniColumn("Akşam", day.aksam, isToday, textAlpha, Modifier.weight(1f))
                PrayerMiniColumn("Yatsı", day.yatsi, isToday, textAlpha, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PrayerMiniColumn(
    label: String,
    time: String,
    isToday: Boolean,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = time,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            },
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ReligiousDaysList(
    selectedFilter: ReligiousFilter,
    onFilterChange: (ReligiousFilter) -> Unit
) {
    val nowMillis = remember { System.currentTimeMillis() }
    val filteredDays = remember(selectedFilter) {
        ReligiousDaysProvider.allDays.filter { item ->
            when (selectedFilter) {
                ReligiousFilter.UPCOMING -> {
                    val diff = ReligiousDaysProvider.daysBetweenTodayAnd(item.isoDate, nowMillis) ?: -1L
                    diff >= 0L
                }
                ReligiousFilter.YEAR_2026 -> item.year == 2026
                ReligiousFilter.YEAR_2027 -> item.year == 2027
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReligiousFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.minimumInteractiveComponentSize()
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Dini geceler, takvim gününde akşam ezanıyla birlikte başlar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredDays, key = { it.id }) { item ->
                val daysDiff = ReligiousDaysProvider.daysBetweenTodayAnd(item.isoDate, nowMillis) ?: -1L
                ReligiousDayCard(day = item, daysDiff = daysDiff)
            }
        }
    }
}

@Composable
private fun ReligiousDayCard(
    day: ReligiousDay,
    daysDiff: Long
) {
    val isToday = daysDiff == 0L
    val isPast = daysDiff < 0L

    val icon = when (day.category) {
        ReligiousDayCategory.KANDIL -> Icons.Rounded.NightsStay
        ReligiousDayCategory.BAYRAM -> Icons.Rounded.Celebration
        ReligiousDayCategory.MUBAREK -> Icons.Rounded.AutoAwesome
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("religious_day_${day.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isToday) 1.6.dp else 1.dp,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isToday) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = day.category.label,
                        tint = if (isToday) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = day.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isPast) 0.65f else 1f)
                )
                Text(
                    text = day.formattedDateTr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isPast) 0.65f else 1f)
                )
                Text(
                    text = "${day.hijriLabel} • ${day.category.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = if (isPast) 0.65f else 1f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(50),
                color = when {
                    isToday -> MaterialTheme.colorScheme.primary
                    isPast -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
            ) {
                Text(
                    text = ReligiousDaysProvider.relativeLabel(daysDiff),
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        isToday -> MaterialTheme.colorScheme.onPrimary
                        isPast -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}
