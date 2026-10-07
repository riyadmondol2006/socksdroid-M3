package com.riyadm.socksdroid.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.riyadm.socksdroid.R
import com.riyadm.socksdroid.vpn.VpnState
import kotlin.math.roundToInt

// Springs in the spirit of Material 3 Expressive motion: spatial changes overshoot slightly,
// color (effects) changes settle without bounce.
private val SpatialSpec = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
private val SlowSpatialSpec = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)
private val FastSpatialSpec = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
private val EffectsSpec = spring<Color>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

private enum class ConnectVisual { Off, Busy, On, Failed }

private val VpnState.visual: ConnectVisual
    get() = when (this) {
        VpnState.Disconnected -> ConnectVisual.Off
        is VpnState.Connecting, VpnState.Disconnecting -> ConnectVisual.Busy
        is VpnState.Connected -> ConnectVisual.On
        is VpnState.Error -> ConnectVisual.Failed
    }

/**
 * The large power toggle. Its shape morphs from a rounded square (off) into a circle (on),
 * spinning while the tunnel is being set up or torn down, with a progress ring around it.
 */
@Composable
fun ConnectButton(
    state: VpnState,
    statusDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val visual = state.visual
    val busy = visual == ConnectVisual.Busy

    val containerColor = animateColorAsState(
        targetValue = when (visual) {
            ConnectVisual.Off -> colors.surfaceContainerHighest
            ConnectVisual.Busy -> colors.secondaryContainer
            ConnectVisual.On -> colors.primary
            ConnectVisual.Failed -> colors.errorContainer
        },
        animationSpec = EffectsSpec,
        label = "container",
    )
    val contentColor by animateColorAsState(
        targetValue = when (visual) {
            ConnectVisual.Off -> colors.onSurfaceVariant
            ConnectVisual.Busy -> colors.onSecondaryContainer
            ConnectVisual.On -> colors.onPrimary
            ConnectVisual.Failed -> colors.onErrorContainer
        },
        animationSpec = EffectsSpec,
        label = "content",
    )
    val cornerPercent = animateFloatAsState(
        targetValue = when (visual) {
            ConnectVisual.Off, ConnectVisual.Failed -> 30f
            ConnectVisual.Busy -> 38f
            ConnectVisual.On -> 50f
        },
        animationSpec = SlowSpatialSpec,
        label = "corner",
    )
    val haloScale = animateFloatAsState(
        targetValue = if (visual == ConnectVisual.On) 1f else 0.7f,
        animationSpec = SlowSpatialSpec,
        label = "halo",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = FastSpatialSpec,
        label = "press",
    )

    // Spin while busy, then settle on the nearest quarter turn (the shape is 4-fold symmetric).
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(busy) {
        if (busy) {
            while (true) {
                rotation.animateTo(rotation.value + 360f, tween(durationMillis = 2400, easing = LinearEasing))
            }
        } else {
            val settled = (rotation.value / 90f).roundToInt() * 90f
            rotation.animateTo(settled, SpatialSpec)
            rotation.snapTo(settled % 360f)
        }
    }

    val actionLabel = stringResource(
        if (state.isActive) R.string.home_action_disconnect else R.string.home_action_connect,
    )
    val buttonDescription = stringResource(R.string.home_connect_button)

    Box(modifier = modifier.size(232.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(228.dp)
                .graphicsLayer {
                    scaleX = haloScale.value
                    scaleY = haloScale.value
                    alpha = ((haloScale.value - 0.7f) / 0.3f).coerceIn(0f, 1f)
                }
                .background(colors.primary.copy(alpha = 0.14f), CircleShape),
        )
        AnimatedVisibility(visible = busy, enter = fadeIn(), exit = fadeOut()) {
            CircularProgressIndicator(
                modifier = Modifier.size(212.dp),
                color = colors.primary,
                trackColor = Color.Transparent,
                strokeWidth = 4.dp,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(176.dp)
                .graphicsLayer {
                    scaleX = pressScale.value
                    scaleY = pressScale.value
                    rotationZ = rotation.value
                    // Corner radius in px from the layer size, so 50% is a true circle.
                    shape = RoundedCornerShape(size.minDimension * cornerPercent.value / 100f)
                    clip = true
                }
                .drawBehind { drawRect(containerColor.value) }
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = contentColor),
                    enabled = state != VpnState.Disconnecting,
                    role = Role.Button,
                    onClickLabel = actionLabel,
                    onClick = onClick,
                )
                .semantics {
                    contentDescription = buttonDescription
                    stateDescription = statusDescription
                },
        ) {
            Icon(
                imageVector = Icons.Rounded.PowerSettingsNew,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer { rotationZ = -rotation.value },
            )
        }
    }
}
