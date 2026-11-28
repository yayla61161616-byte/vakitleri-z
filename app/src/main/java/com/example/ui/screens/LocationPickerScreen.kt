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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.CityOption
import com.example.domain.CountryOption
import com.example.domain.DistrictOption
import com.example.domain.PlaceSearchResult
import com.example.domain.toTurkishTitleCase
import com.example.ui.LocationPickerMode
import com.example.ui.LocationPickerUiState
import com.example.ui.LocationStep
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    uiState: LocationPickerUiState,
    isDetectingLocation: Boolean,
    locationDetectMessage: String?,
    onDetectLocationClick: () -> Unit,
    onModeChange: (LocationPickerMode) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchVillagesNow: () -> Unit,
    onSwitchToVillageSearch: (String) -> Unit,
    onSelectCountry: (CountryOption) -> Unit,
    onSelectCity: (CityOption) -> Unit,
    onSelectDistrict: (DistrictOption) -> Unit,
    onSelectPlaceResult: (PlaceSearchResult) -> Unit,
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
                    text = when {
                        uiState.mode == LocationPickerMode.VILLAGE_SEARCH -> "Köy / Mahalle / Semt Ara"
                        uiState.step == LocationStep.COUNTRY -> "Ülke Seçin"
                        uiState.step == LocationStep.CITY -> uiState.pendingCountry?.name?.toTurkishTitleCase() ?: "Şehir Seçin"
                        else -> uiState.pendingCity?.name?.toTurkishTitleCase() ?: "İlçe Seçin"
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
                Button(
                    onClick = onDetectLocationClick,
                    enabled = !isDetectingLocation,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("auto_detect_location_button")
                ) {
                    if (isDetectingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (isDetectingLocation) "Konum bulunuyor…" else stringResource(R.string.action_detect_location),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (!locationDetectMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = locationDetectMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = uiState.mode == LocationPickerMode.DIYANET_LIST,
                        onClick = { onModeChange(LocationPickerMode.DIYANET_LIST) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("mode_diyanet_list")
                    ) {
                        Text("Ülke / Şehir / İlçe")
                    }
                    SegmentedButton(
                        selected = uiState.mode == LocationPickerMode.VILLAGE_SEARCH,
                        onClick = { onModeChange(LocationPickerMode.VILLAGE_SEARCH) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("mode_village_search")
                    ) {
                        Text("Köy / Semt Ara")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (uiState.mode == LocationPickerMode.DIYANET_LIST) {
                    StepBreadcrumbs(step = uiState.step)
                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    Text(
                        text = "Örnek: Ludwigshafen Oggersheim, Pfalz veya herhangi bir köy / mahalle adı yazın.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            if (uiState.mode == LocationPickerMode.VILLAGE_SEARCH) {
                                "Köy, semt, kasaba veya şehir yazın…"
                            } else {
                                stringResource(R.string.location_search_hint)
                            }
                        )
                    },
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
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (uiState.mode == LocationPickerMode.VILLAGE_SEARCH) {
                            ImeAction.Search
                        } else {
                            ImeAction.Done
                        }
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (uiState.mode == LocationPickerMode.VILLAGE_SEARCH) {
                                onSearchVillagesNow()
                            }
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("location_search_input")
                )

                if (uiState.mode == LocationPickerMode.VILLAGE_SEARCH && uiState.searchQuery.trim().length >= 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = onSearchVillagesNow,
                        enabled = !uiState.isSearchingVillages,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("village_search_submit_button")
                    ) {
                        Icon(imageVector = Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Köy / Semt Ara")
                    }
                }

                if (uiState.isLoadingList || uiState.isSearchingVillages) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.mode == LocationPickerMode.VILLAGE_SEARCH) {
                        if (!uiState.villageSearchError.isNullOrBlank() && !uiState.isSearchingVillages) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = uiState.villageSearchError,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            }
                        }
                        items(uiState.villageResults, key = { it.id }) { place ->
                            LocationRowCard(
                                title = place.primaryName,
                                subtitle = buildString {
                                    append(place.subtitle)
                                    append(
                                        String.format(
                                            Locale.US,
                                            " (%.4f, %.4f)",
                                            place.latitude,
                                            place.longitude
                                        )
                                    )
                                },
                                isExactPin = true,
                                tag = "village_item_${place.id}",
                                onClick = { onSelectPlaceResult(place) }
                            )
                        }
                    } else {
                        when (uiState.step) {
                            LocationStep.COUNTRY -> {
                                val filtered = uiState.countries.filter {
                                    normalizedQuery.isEmpty() ||
                                        it.name.lowercase(trLocale).contains(normalizedQuery) ||
                                        it.nameEn.lowercase(Locale.US).contains(normalizedQuery)
                                }
                                if (filtered.isEmpty() && normalizedQuery.length >= 2) {
                                    item {
                                        VillageSearchFallbackCard(
                                            query = uiState.searchQuery.trim(),
                                            onClick = { onSwitchToVillageSearch(uiState.searchQuery.trim()) }
                                        )
                                    }
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
                                if (filtered.isEmpty() && normalizedQuery.length >= 2) {
                                    item {
                                        val countryContext = uiState.pendingCountry?.name?.toTurkishTitleCase().orEmpty()
                                        val combined = listOf(uiState.searchQuery.trim(), countryContext)
                                            .filter { it.isNotBlank() }
                                            .joinToString(", ")
                                        VillageSearchFallbackCard(
                                            query = combined,
                                            onClick = { onSwitchToVillageSearch(combined) }
                                        )
                                    }
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
                                if (filtered.isEmpty() && normalizedQuery.length >= 2) {
                                    item {
                                        val combined = listOfNotNull(
                                            uiState.searchQuery.trim(),
                                            uiState.pendingCity?.name?.toTurkishTitleCase(),
                                            uiState.pendingCountry?.name?.toTurkishTitleCase()
                                        ).filter { it.isNotBlank() }.joinToString(", ")
                                        VillageSearchFallbackCard(
                                            query = combined,
                                            onClick = { onSwitchToVillageSearch(combined) }
                                        )
                                    }
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
}

@Composable
private fun VillageSearchFallbackCard(
    query: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag("village_fallback_search_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Köy / Semt Aramasında Bul",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "\"$query\" için koordinatlı vakit hesapla",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
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
    isExactPin: Boolean = false,
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
                imageVector = if (isExactPin) Icons.Rounded.LocationOn else Icons.Rounded.Public,
                contentDescription = null,
                tint = if (isExactPin) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
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
