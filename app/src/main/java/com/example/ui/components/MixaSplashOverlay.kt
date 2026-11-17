package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.MixaHaptics
import kotlinx.coroutines.delay

@Composable
fun MixaSplashOverlay(
    visible: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var revealLogo by remember { mutableStateOf(false) }

    val logoScale by animateFloatAsState(
        targetValue = if (revealLogo) 1f else 0.90f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "mixa_splash_scale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (revealLogo) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "mixa_splash_alpha"
    )

    LaunchedEffect(visible) {
        if (visible) {
            revealLogo = true
            delay(180L)
            MixaHaptics.confirm(view)
            delay(760L)
            onFinished()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(280, easing = FastOutSlowInEasing)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("mixa_splash_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_mixa_splash_logo),
                contentDescription = "Mixa",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .fillMaxWidth(0.84f)
                    .padding(horizontal = 16.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha)
            )
        }
    }
}
