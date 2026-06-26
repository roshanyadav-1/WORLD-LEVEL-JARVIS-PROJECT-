package com.aris.voice

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.aris.voice.utilities.OnboardingManager
import com.aris.voice.utilities.UserProfileManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            SplashScreenContent()
        }

        // Logic for routing after splash animation (2.5 seconds)
        lifecycleScope.launch {
            delay(2500)
            navigateToNextScreen()
        }
    }

    private fun navigateToNextScreen() {
        val onboardingManager = OnboardingManager(this)
        val profileManager = UserProfileManager(this)

        if (!onboardingManager.isGuideCompleted()) {
            // Unvisited install, launch beautiful interactive onboarding showcase
            startActivity(Intent(this, OnboardingGuideActivity::class.java))
        } else if (!profileManager.isProfileComplete()) {
            // Require login
            startActivity(Intent(this, LoginActivity::class.java))
        } else if (!onboardingManager.isOnboardingCompleted()) {
            // Require permissions
            startActivity(Intent(this, OnboardingPermissionsActivity::class.java))
        } else {
            // All complete, start MainActivity
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

@Composable
fun SplashScreenContent() {
    // Dynamic pulsing animation for our AI orb
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteTransitionSpec(),
        label = "scale"
    )

    val rotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteTransitionSpec(duration = 6000),
        label = "rotate"
    )

    val opacity by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteTransitionSpec(duration = 2000),
        label = "opacity"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF07080A), // Black obsidian core
                        Color(0xFF14161F), // Dark titanium plate
                        Color(0xFF020203)  // Clean dark graphite
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Cosmic Pulsing AI core
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(240.dp)
            ) {
                // Nebula glow layering
                Canvas(modifier = Modifier.size(160.dp * scale)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFE2E8F0).copy(alpha = 0.35f * opacity), // Silver-chrome core
                                Color(0xFF64748B).copy(alpha = 0.15f),          // Metallic slate ring
                                Color.Transparent
                            )
                        ),
                        radius = size.minDimension / 1.5f
                    )
                }

                // Interactive Neon ring representing AI energy
                Canvas(modifier = Modifier.size(120.dp * scale)) {
                    val radius = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0xFFF1F5F9), // Ice white
                                Color(0xFF94A3B8), // Charcoal silver
                                Color(0xFFE2E8F0), // Platinum silver
                                Color(0xFFF1F5F9)
                            )
                        ),
                        radius = radius,
                        style = Stroke(width = 4.dp.toPx())
                    )
                }

                // Decorative orbital stars/dots
                Canvas(modifier = Modifier.size(180.dp)) {
                    drawCircle(
                        color = Color(0xFF94A3B8).copy(alpha = 0.5f),
                        radius = 4.dp.toPx()
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Text Typography Branding (Modern Elegant Space aesthetic)
            Text(
                text = "A R I S",
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 10.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "HYBRID OFFLINE INTELLIGENCE",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
        }

        // Subtitle loader at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        ) {
            Text(
                text = "C-LAE v2 MULTI-MODEL CORE ENGINE ACTIVE",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
        }
    }
}

private fun infiniteTransitionSpec(duration: Int = 1800): InfiniteRepeatableSpec<Float> {
    return infiniteRepeatable(
        animation = tween(durationMillis = duration, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse
    )
}
