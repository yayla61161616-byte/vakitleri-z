package com.example.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.QiblaCalculator
import com.example.ui.QiblaUiState
import com.example.ui.theme.GoldAccent
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class CompassSensorMode(val label: String) {
    FUSION("Füzyon Sensör"),
    MAGNETIC("Manyetik Pusula")
}

@Composable
fun QiblaScreen(
    qiblaState: QiblaUiState,
    hasLocationPermission: Boolean,
    onRequestLocationAndRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current

    val sensorAvailability = remember(context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val hasRotation = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
        val hasMagAccel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null &&
            sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
        hasRotation to hasMagAccel
    }
    val hasRotationSensor = sensorAvailability.first
    val hasMagAccelSensors = sensorAvailability.second
    val hasCompassSensor = hasRotationSensor || hasMagAccelSensors

    var sensorMode by remember(hasRotationSensor) {
        mutableStateOf(if (hasRotationSensor) CompassSensorMode.FUSION else CompassSensorMode.MAGNETIC)
    }
    var magneticAzimuthDeg by remember { mutableFloatStateOf(0f) }
    var magnetAccuracy by remember { mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH) }
    var isAligned by remember { mutableStateOf(false) }

    // Fetch GPS coordinates automatically when entering Qibla screen if permission granted
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && qiblaState.coordinates == null && !qiblaState.isFetchingGps) {
            onRequestLocationAndRefresh()
        }
    }

    // Real device compass sensors with tilt compensation and display rotation remapping
    DisposableEffect(context, sensorMode, hasCompassSensor) {
        if (!hasCompassSensor) {
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

            val useFusion = sensorMode == CompassSensorMode.FUSION && rotationSensor != null
            val rawMatrix = FloatArray(9)
            val remappedMatrix = FloatArray(9)
            val smoothedAccel = FloatArray(3)
            val smoothedMagnet = FloatArray(3)
            var hasAccel = false
            var hasMagnet = false
            var smoothedAzimuth = 0f
            var initialized = false

            fun remapAndUpdate(matrix: FloatArray) {
                val rotation = view.display?.rotation ?: Surface.ROTATION_0
                val remapped = when (rotation) {
                    Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                        matrix,
                        SensorManager.AXIS_Y,
                        SensorManager.AXIS_MINUS_X,
                        remappedMatrix
                    )
                    Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                        matrix,
                        SensorManager.AXIS_MINUS_X,
                        SensorManager.AXIS_MINUS_Y,
                        remappedMatrix
                    )
                    Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                        matrix,
                        SensorManager.AXIS_MINUS_Y,
                        SensorManager.AXIS_X,
                        remappedMatrix
                    )
                    else -> {
                        System.arraycopy(matrix, 0, remappedMatrix, 0, 9)
                        true
                    }
                }
                val targetMatrix = if (remapped) remappedMatrix else matrix
                val rawDeg = QiblaCalculator.azimuthFromRotationMatrix(targetMatrix).toFloat()
                val normalized = ((rawDeg % 360f) + 360f) % 360f
                if (!initialized) {
                    smoothedAzimuth = normalized
                    initialized = true
                } else {
                    val delta = QiblaCalculator.shortestAngleDiffDegrees(smoothedAzimuth, normalized)
                    val alpha = if (abs(delta) > 25f) 0.45f else 0.22f
                    smoothedAzimuth = ((smoothedAzimuth + delta * alpha) % 360f + 360f) % 360f
                }
                magneticAzimuthDeg = smoothedAzimuth
            }

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_ROTATION_VECTOR -> if (useFusion) {
                            SensorManager.getRotationMatrixFromVector(rawMatrix, event.values)
                            remapAndUpdate(rawMatrix)
                        }
                        Sensor.TYPE_ACCELEROMETER -> if (!useFusion) {
                            if (!hasAccel) {
                                System.arraycopy(event.values, 0, smoothedAccel, 0, 3)
                                hasAccel = true
                            } else {
                                for (i in 0..2) {
                                    smoothedAccel[i] = smoothedAccel[i] * 0.80f + event.values[i] * 0.20f
                                }
                            }
                        }
                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            magnetAccuracy = event.accuracy
                            if (!useFusion) {
                                if (!hasMagnet) {
                                    System.arraycopy(event.values, 0, smoothedMagnet, 0, 3)
                                    hasMagnet = true
                                } else {
                                    for (i in 0..2) {
                                        smoothedMagnet[i] = smoothedMagnet[i] * 0.80f + event.values[i] * 0.20f
                                    }
                                }
                            }
                        }
                    }

                    if (!useFusion && hasAccel && hasMagnet) {
                        if (SensorManager.getRotationMatrix(rawMatrix, null, smoothedAccel, smoothedMagnet)) {
                            remapAndUpdate(rawMatrix)
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                    if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD ||
                        (sensor?.type == Sensor.TYPE_ROTATION_VECTOR && magnetSensor == null)
                    ) {
                        magnetAccuracy = accuracy
                    }
                }
            }

            if (useFusion && rotationSensor != null) {
                sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
                if (magnetSensor != null) {
                    sensorManager.registerListener(listener, magnetSensor, SensorManager.SENSOR_DELAY_UI)
                }
            } else if (accelSensor != null && magnetSensor != null) {
                sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_GAME)
                sensorManager.registerListener(listener, magnetSensor, SensorManager.SENSOR_DELAY_GAME)
            }

            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    val trueAzimuthDeg = ((magneticAzimuthDeg + qiblaState.magneticDeclinationDegrees) % 360f + 360f) % 360f
    val qiblaBearing = qiblaState.qiblaBearingDegrees?.toFloat()
    val signedDiffDeg = qiblaBearing?.let { QiblaCalculator.shortestAngleDiffDegrees(trueAzimuthDeg, it) }
    val absAngleDiff = signedDiffDeg?.let { abs(it) }

    LaunchedEffect(hasCompassSensor, absAngleDiff) {
        val nextAligned = if (!hasCompassSensor || absAngleDiff == null) {
            false
        } else if (isAligned) {
            absAngleDiff <= 3.0f
        } else {
            absAngleDiff <= 2.0f
        }
        if (nextAligned && !isAligned) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        isAligned = nextAligned
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Kıble Bulucu",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Gerçek kuzeye göre sapma düzeltmeli Kıble yönü",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(
                        onClick = onRequestLocationAndRefresh,
                        enabled = !qiblaState.isFetchingGps,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("qibla_gps_button")
                    ) {
                        if (qiblaState.isFetchingGps) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "GPS Yenile", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (!hasLocationPermission) {
                item {
                    QiblaPermissionPromptCard(onGrantClick = onRequestLocationAndRefresh)
                }
            } else if (qiblaState.errorMessage != null && qiblaState.coordinates == null) {
                item {
                    QiblaErrorCard(
                        message = qiblaState.errorMessage,
                        onRetry = onRequestLocationAndRefresh
                    )
                }
            }

            if (hasCompassSensor && magnetAccuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Pusula hassasiyeti düşük. Doğru ölçüm için telefonu havada 8 (∞) çizerek kalibre edin ve metal/mıknatıstan uzak tutun.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                CompassCard(
                    trueAzimuthDeg = trueAzimuthDeg,
                    qiblaBearingDeg = qiblaBearing,
                    signedDiffDeg = signedDiffDeg,
                    declinationDeg = qiblaState.magneticDeclinationDegrees,
                    hasCompassSensor = hasCompassSensor,
                    isAligned = isAligned,
                    isFetchingGps = qiblaState.isFetchingGps
                )
            }

            if (hasRotationSensor && hasMagAccelSensors) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompassSensorMode.entries.forEach { mode ->
                            FilterChip(
                                selected = sensorMode == mode,
                                onClick = { sensorMode = mode },
                                label = { Text(text = mode.label) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("qibla_mode_${mode.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            if (qiblaState.coordinates != null && qiblaState.qiblaBearingDegrees != null) {
                item {
                    QiblaMetricsCard(qiblaState = qiblaState)
                }
            }

            if (!hasCompassSensor) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Bu cihazda manyetometre (pusula) sensörü bulunamadı. GPS ile hesaplanan Kıble açısını referans alabilirsiniz.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun CompassCard(
    trueAzimuthDeg: Float,
    qiblaBearingDeg: Float?,
    signedDiffDeg: Float?,
    declinationDeg: Float,
    hasCompassSensor: Boolean,
    isAligned: Boolean,
    isFetchingGps: Boolean
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val kaabaPainter = rememberVectorPainter(ImageVector.vectorResource(id = R.drawable.ic_kaaba))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAligned) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (isAligned) 2.dp else 1.dp,
            color = if (isAligned) primaryColor else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isAligned) primaryColor else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isAligned) Icons.Rounded.CheckCircle else Icons.Rounded.Explore,
                        contentDescription = null,
                        tint = if (isAligned) MaterialTheme.colorScheme.onPrimary else primaryColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = when {
                            isFetchingGps -> "GPS konumu alınıyor…"
                            qiblaBearingDeg == null -> "GPS konumu bekleniyor"
                            !hasCompassSensor -> "Kıble Açısı: ${QiblaCalculator.formatBearing(qiblaBearingDeg.toDouble())}"
                            isAligned -> "Kıble Yönündesiniz"
                            signedDiffDeg != null -> {
                                val degInt = abs(signedDiffDeg).roundToInt().coerceAtLeast(1)
                                if (signedDiffDeg > 0f) "$degInt° Sağa Dönün" else "$degInt° Sola Dönün"
                            }
                            else -> "Altın oku takip edin"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isAligned) MaterialTheme.colorScheme.onPrimary else onSurface
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.84f)
                    .widthIn(max = 310.dp)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                QiblaCompassCanvas(
                    trueAzimuthDeg = if (hasCompassSensor) trueAzimuthDeg else 0f,
                    qiblaBearingDeg = qiblaBearingDeg,
                    isAligned = isAligned,
                    primaryColor = primaryColor,
                    ringTrackColor = surfaceVariant,
                    labelColor = onSurface,
                    tickColor = onSurfaceVariant,
                    textMeasurer = textMeasurer,
                    kaabaPainter = kaabaPainter,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("qibla_compass_canvas")
                )
            }

            if (qiblaBearingDeg != null && hasCompassSensor) {
                Text(
                    text = String.format(
                        Locale.US,
                        "Cihaz: %d°  •  Kıble: %d°  •  Manyetik Sapma: %+.1f°",
                        trueAzimuthDeg.roundToInt(),
                        qiblaBearingDeg.roundToInt(),
                        declinationDeg
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun QiblaCompassCanvas(
    trueAzimuthDeg: Float,
    qiblaBearingDeg: Float?,
    isAligned: Boolean,
    primaryColor: Color,
    ringTrackColor: Color,
    labelColor: Color,
    tickColor: Color,
    textMeasurer: TextMeasurer,
    kaabaPainter: Painter,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension / 2f - 10.dp.toPx()

        // Outer compass bezel
        drawCircle(
            color = ringTrackColor,
            radius = outerRadius,
            center = center,
            style = Stroke(width = 14.dp.toPx())
        )

        if (isAligned) {
            drawCircle(
                color = primaryColor.copy(alpha = 0.20f),
                radius = outerRadius - 8.dp.toPx(),
                center = center
            )
        }

        // Fixed top phone target pointer
        val targetPath = Path().apply {
            val notchY = center.y - outerRadius
            moveTo(center.x, notchY + 8.dp.toPx())
            lineTo(center.x - 7.dp.toPx(), notchY - 8.dp.toPx())
            lineTo(center.x + 7.dp.toPx(), notchY - 8.dp.toPx())
            close()
        }
        drawPath(
            path = targetPath,
            color = if (isAligned) GoldAccent else primaryColor
        )

        // Rotating compass dial (-trueAzimuthDeg)
        rotate(degrees = -trueAzimuthDeg, pivot = center) {
            for (deg in 0 until 360 step 15) {
                val rad = Math.toRadians(deg.toDouble() - 90.0)
                val isMajor = deg % 45 == 0
                val innerR = outerRadius - if (isMajor) 18.dp.toPx() else 10.dp.toPx()
                val start = Offset(
                    center.x + (innerR * cos(rad)).toFloat(),
                    center.y + (innerR * sin(rad)).toFloat()
                )
                val end = Offset(
                    center.x + (outerRadius * cos(rad)).toFloat(),
                    center.y + (outerRadius * sin(rad)).toFloat()
                )
                drawLine(
                    color = if (deg == 0) primaryColor else tickColor.copy(alpha = if (isMajor) 0.8f else 0.4f),
                    start = start,
                    end = end,
                    strokeWidth = if (isMajor) 2.5.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Cardinal letters K, D, G, B
            val cardinals = listOf(0 to "K", 90 to "D", 180 to "G", 270 to "B")
            cardinals.forEach { (deg, letter) ->
                val rad = Math.toRadians(deg.toDouble() - 90.0)
                val labelRadius = outerRadius - 32.dp.toPx()
                val style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = if (deg == 0) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (deg == 0) primaryColor else labelColor
                )
                val layout = textMeasurer.measure(letter, style)
                val x = center.x + (labelRadius * cos(rad)).toFloat() - layout.size.width / 2f
                val y = center.y + (labelRadius * sin(rad)).toFloat() - layout.size.height / 2f
                drawText(layout, topLeft = Offset(x, y))
            }

            // Gold Qibla Arrow with Kaaba Artwork
            if (qiblaBearingDeg != null) {
                rotate(degrees = qiblaBearingDeg, pivot = center) {
                    val tipY = center.y - (outerRadius - 12.dp.toPx())
                    val badgeCenterY = center.y - (outerRadius * 0.50f)
                    val tailY = center.y + 30.dp.toPx()
                    val shaftHalfW = 5.dp.toPx()

                    // Arrow shaft & tail
                    val shaftPath = Path().apply {
                        moveTo(center.x, tipY + 14.dp.toPx())
                        lineTo(center.x - shaftHalfW, center.y)
                        lineTo(center.x, tailY)
                        lineTo(center.x + shaftHalfW, center.y)
                        close()
                    }
                    drawPath(path = shaftPath, color = GoldAccent)

                    // Sharp golden arrowhead pointing to outer bezel notch
                    val headPath = Path().apply {
                        moveTo(center.x, tipY)
                        lineTo(center.x - 12.dp.toPx(), tipY + 20.dp.toPx())
                        lineTo(center.x, tipY + 14.dp.toPx())
                        lineTo(center.x + 12.dp.toPx(), tipY + 20.dp.toPx())
                        close()
                    }
                    drawPath(
                        path = headPath,
                        color = GoldAccent
                    )
                    drawPath(
                        path = headPath,
                        color = primaryColor,
                        style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round)
                    )

                    // Kaaba Emblem on the Qibla Arrow (counter-rotated so Kaaba stays upright)
                    val badgeCenter = Offset(center.x, badgeCenterY)
                    val badgeRadius = 21.dp.toPx()
                    drawCircle(
                        color = GoldAccent,
                        radius = badgeRadius + 2.5.dp.toPx(),
                        center = badgeCenter
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = badgeRadius,
                        center = badgeCenter,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    val kaabaSize = 36.dp.toPx()
                    rotate(degrees = trueAzimuthDeg - qiblaBearingDeg, pivot = badgeCenter) {
                        translate(
                            left = badgeCenter.x - kaabaSize / 2f,
                            top = badgeCenter.y - kaabaSize / 2f
                        ) {
                            with(kaabaPainter) {
                                draw(size = Size(kaabaSize, kaabaSize))
                            }
                        }
                    }
                }
            }
        }

        // Center pivot
        drawCircle(
            color = primaryColor,
            radius = 8.dp.toPx(),
            center = center
        )
        drawCircle(
            color = GoldAccent,
            radius = 4.dp.toPx(),
            center = center
        )
    }
}

@Composable
private fun QiblaMetricsCard(qiblaState: QiblaUiState) {
    val coords = qiblaState.coordinates ?: return
    val bearing = qiblaState.qiblaBearingDegrees ?: return
    val distance = qiblaState.distanceKm ?: return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kıble Açısı",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = QiblaCalculator.formatBearing(bearing),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Kâbe Uzaklığı",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${distance.roundToInt()} km",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Text(
                text = buildString {
                    if (!coords.areaLabel.isNullOrBlank()) {
                        append(coords.areaLabel)
                        append(" • ")
                    }
                    append(
                        String.format(
                            Locale.US,
                            "GPS: %.4f, %.4f (±%dm)",
                            coords.latitude,
                            coords.longitude,
                            coords.accuracyMeters.roundToInt()
                        )
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QiblaPermissionPromptCard(onGrantClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "GPS ile Kıble Hesaplama",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Bulunduğunuz noktaya göre tam Kıble açısını hesaplamak için konum izni verin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onGrantClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("qibla_grant_location_button")
            ) {
                Icon(imageVector = Icons.Rounded.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Konum İzni Ver ve Kıbleyi Bul")
            }
        }
    }
}

@Composable
private fun QiblaErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = onRetry,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Tekrar")
            }
        }
    }
}
