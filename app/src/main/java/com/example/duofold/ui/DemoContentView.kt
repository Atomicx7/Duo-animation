package com.example.duofold.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ScreenBg = Color(0xFFF7F6FB)
private val InkBlack = Color(0xFF14141A)
private val InkGray = Color(0xFF8A8A94)
private val AccentBlue = Color(0xFF2E7CF6)

/**
 * "Today" dashboard demo surface (health/lifestyle style): header, category
 * chips, gradient hero card, stats grid, recent list — plus a small Calibrate
 * button at the bottom. All Compose-native so it rasterizes cleanly into the
 * graphicsLayer texture consumed by the fold shader, and full of color/text
 * so the frost/blur reads clearly.
 */
@Composable
fun DemoContentView(
    modifier: Modifier = Modifier,
    onCalibrateClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val categories = listOf("All", "Health", "Work", "Reading", "Travel")
    var selected by remember { mutableStateOf("All") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScreenBg)
            .verticalScroll(rememberScrollState())
            // Edge-to-edge: background stays full-bleed behind the system bars
            // (it is drawn before these paddings), content keeps clear of them.
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        // Header: date + Today, gear + avatar on the right.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Thursday, 09 May", color = InkGray, fontSize = 12.sp)
                Text("Today", color = InkBlack, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = InkGray)
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF2662E)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Category chips.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            items(categories) { cat ->
                if (cat == selected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(AccentBlue)
                            .clickable { selected = cat }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(cat, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Text(
                        cat,
                        color = InkGray,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable { selected = cat }
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Hero gradient card.
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF7B5CFF), Color(0xFFC44EDD), Color(0xFFF0564A))
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "FOCUS SCORE",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "2,000",
                                color = Color.White,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(Icons.Filled.NorthEast, contentDescription = null, tint = Color.White)
                    }
                    Text(
                        "Small steps every day compound into big results. Keep the streak alive.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(18, 34, 28, 42, 30, 46, 26, 38, 50, 36, 28, 20).forEach { h ->
                            Box(
                                modifier = Modifier
                                    .size(width = 16.dp, height = h.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color.White.copy(alpha = 0.92f))
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Stats grid.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCell(
                label = "Steps",
                value = "8,412",
                icon = Icons.Filled.DirectionsRun,
                tint = Color(0xFF22C55E),
                modifier = Modifier.weight(1f)
            )
            StatCell(
                label = "Sleep",
                value = "7h 20m",
                icon = Icons.Filled.Bedtime,
                tint = Color(0xFF8B5CF6),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCell(
                label = "Focus",
                value = "3h 05m",
                icon = Icons.Filled.Whatshot,
                tint = Color(0xFFF59E0B),
                modifier = Modifier.weight(1f)
            )
            StatCell(
                label = "Water",
                value = "1.8 L",
                icon = Icons.Filled.WaterDrop,
                tint = Color(0xFF3B82F6),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(18.dp))
        Text("Recent", color = InkBlack, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        RecentRow(
            title = "Morning run",
            subtitle = "5.2 km · 6:30 AM",
            icon = Icons.Filled.DirectionsRun,
            tint = Color(0xFF22C55E),
            chipBg = Color(0xFFE9F9EE)
        )
        Spacer(Modifier.height(10.dp))
        RecentRow(
            title = "Design review",
            subtitle = "10:00 AM · Figma",
            icon = Icons.Filled.Palette,
            tint = Color(0xFFEF4444),
            chipBg = Color(0xFFFDECEC)
        )
        Spacer(Modifier.height(10.dp))
        RecentRow(
            title = "Flight to Lisbon",
            subtitle = "Friday · 2 seats",
            icon = Icons.Filled.Flight,
            tint = Color(0xFF3B82F6),
            chipBg = Color(0xFFE8F1FE)
        )
        Spacer(Modifier.height(10.dp))
        RecentRow(
            title = "Read 20 pages",
            subtitle = "Atomic Habits · p.120",
            icon = Icons.Filled.MenuBook,
            tint = Color(0xFFB45309),
            chipBg = Color(0xFFF7EDDC)
        )

        // Small calibrate button at the bottom.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp),
            contentAlignment = Alignment.Center
        ) {
            OutlinedButton(
                onClick = onCalibrateClick,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Calibrate", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = InkGray, fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, color = InkBlack, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecentRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    chipBg: Color
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(chipBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = InkBlack, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = InkGray, fontSize = 13.sp)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = InkGray)
        }
    }
}
