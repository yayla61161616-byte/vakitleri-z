package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.CityOption
import com.example.domain.CountryOption
import com.example.domain.DistrictOption
import com.example.domain.toTurkishTitleCase
import com.example.ui.LocationPickerUiState
import com.example.ui.LocationStep
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    uiState: LocationPickerUiState,
    onSearchQueryChange: (String) -> Unit,
    onSelectCountry: (CountryOption) -> Unit,
    onSelectCity: (CityOption) -> Unit,
    onSelectDistrict: (DistrictOption) -> Unit,
    onStepBack: () -> Boolean,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        if (!onStepBack()) {
            onExit()
        }
    }

    val trLocale = remember { Locale.forLanguageTag("tr-TR") }
    val normalizedQuery = uiState.searchQuery.trim().lowercase(trLocale)

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = when (uiState.step) {
                        LocationStep.COUNTRY -> "Ülke Seçin"
                        LocationStep.CITY -> uiState.pendingCountry?.name?.toTurkishTitleCase() ?: "Şehir Seçin"
                        LocationStep.DISTRICT -> uiState.pendingCity?.name?.toTurkishTitleCase() ?: "İlçe Seçin"
                    },
                    style = MaterialTheme.typography.titleLarge
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (!onStepBack()) {
                            onExit()
                        }
                    },
                    modifier = Modifier.testTag("location_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .padding(horizontal = 16.dp)
            ) {
                StepBreadcrumbs(step = uiState.step)

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(stringResource(R.string.location_search_hint)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "Temizle"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("location_search_input")
                )

                if (uiState.isLoadingList) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (uiState.step) {
                        LocationStep.COUNTRY -> {
                            val filtered = uiState.countries.filter {
                                normalizedQuery.isEmpty() ||
                                    it.name.lowercase(trLocale).contains(normalizedQuery) ||
                                    it.nameEn.lowercase(Locale.US).contains(normalizedQuery)
                            }
                            items(filtered, key = { it.id }) { country ->
                                LocationRowCard(
                                    title = country.name.toTurkishTitleCase(),
                                    subtitle = country.nameEn.toTurkishTitleCase(),
                                    tag = "location_item_${country.id}",
                                    onClick = { onSelectCountry(country) }
                                )
                            }
                        }

                        LocationStep.CITY -> {
                            val filtered = uiState.cities.filter {
                                normalizedQuery.isEmpty() ||
                                    it.name.lowercase(trLocale).contains(normalizedQuery) ||
                                    it.nameEn.lowercase(Locale.US).contains(normalizedQuery)
                            }
                            items(filtered, key = { it.id }) { city ->
                                LocationRowCard(
                                    title = city.name.toTurkishTitleCase(),
                                    subtitle = uiState.pendingCountry?.name?.toTurkishTitleCase(),
                                    tag = "location_item_${city.id}",
                                    onClick = { onSelectCity(city) }
                                )
                            }
                        }

                        LocationStep.DISTRICT -> {
                            val filtered = uiState.districts.filter {
                                normalizedQuery.isEmpty() ||
                                    it.name.lowercase(trLocale).contains(normalizedQuery) ||
                                    it.nameEn.lowercase(Locale.US).contains(normalizedQuery)
                            }
                            items(filtered, key = { it.id }) { district ->
                                LocationRowCard(
                                    title = district.name.toTurkishTitleCase(),
                                    subtitle = uiState.pendingCity?.name?.toTurkishTitleCase(),
                                    tag = "location_item_${district.id}",
                                    onClick = { onSelectDistrict(district) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepBreadcrumbs(step: LocationStep) {
    val steps = listOf(
        LocationStep.COUNTRY to stringResource(R.string.location_step_country),
        LocationStep.CITY to stringResource(R.string.location_step_city),
        LocationStep.DISTRICT to stringResource(R.string.location_step_district)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEach { (itemStep, label) ->
            val active = itemStep == step
            Surface(
                shape = RoundedCornerShape(50),
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationRowCard(
    title: String,
    subtitle: String?,
    tag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag(tag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrBlank() && !subtitle.equals(title, ignoreCase = true)) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
