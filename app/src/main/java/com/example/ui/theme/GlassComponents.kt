package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SkyBackground,
                        Color(0xFFEDF6FF),
                        SkyBackground
                    )
                )
            )
    ) {
        // Subtle ambient sky-blue glow orbs (no expensive per-frame blur)
        Box(
            modifier = Modifier
                .size(260.dp)
                .offset(x = (-70).dp, y = (-50).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            SkySecondary.copy(alpha = 0.28f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = 120.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            SkyPrimary.copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    )
                )
        )
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    strongSurface: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val fillColor = if (strongSurface) GlassSurfaceStrong else GlassSurface
    val cardModifier = modifier
        .shadow(
            elevation = 8.dp,
            shape = shape,
            ambientColor = SkyPrimary.copy(alpha = 0.14f),
            spotColor = SkyPrimary.copy(alpha = 0.14f)
        )
        .clip(shape)
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    fillColor,
                    Color.White.copy(alpha = if (strongSurface) 0.90f else 0.72f)
                )
            )
        )
        .border(
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        SkySecondary.copy(alpha = 0.35f)
                    )
                )
            ),
            shape = shape
        )
        .let { base ->
            if (onClick != null) base.clickable(onClick = onClick) else base
        }

    Column(
        modifier = cardModifier.padding(16.dp),
        content = content
    )
}

enum class PillTone {
    SUCCESS, WARNING, ERROR, INFO, NEUTRAL
}

@Composable
fun StatusPill(
    label: String,
    tone: PillTone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val (bg, fg) = when (tone) {
        PillTone.SUCCESS -> SuccessGreenContainer to SuccessGreen
        PillTone.WARNING -> WarningAmberContainer to WarningAmber
        PillTone.ERROR -> ErrorCoralContainer to ErrorCoral
        PillTone.INFO -> SkyBackgroundTint to DeepBlueText
        PillTone.NEUTRAL -> NeutralSlateContainer to NeutralSlate
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SkyPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .height(50.dp)
            .minimumInteractiveComponentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SkyPrimary,
            contentColor = Color.White,
            disabledContainerColor = SkySecondary.copy(alpha = 0.45f),
            disabledContentColor = Color.White.copy(alpha = 0.8f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.2.dp,
                color = Color.White
            )
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
    }
}

data class NavTabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

@Composable
fun GlassBottomNavBar(
    items: List<NavTabItem>,
    selectedRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(26.dp),
                    ambientColor = SkyPrimary.copy(alpha = 0.18f),
                    spotColor = SkyPrimary.copy(alpha = 0.18f)
                ),
            shape = RoundedCornerShape(26.dp),
            color = Color.White.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val selected = selectedRoute == item.route
                    val pillColor = if (selected) SkyPrimary.copy(alpha = 0.14f) else Color.Transparent
                    val contentColor = if (selected) DeepBlueText else SecondaryText
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(pillColor)
                            .clickable { onSelect(item.route) }
                            .minimumInteractiveComponentSize()
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                            .testTag(item.testTag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (selected) SkyPrimary else SecondaryText,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MetricValueCard(
    title: String,
    value: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    accentColor: Color = DeepBlueText,
    icon: ImageVector? = null
) {
    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = SecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = SkyPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
