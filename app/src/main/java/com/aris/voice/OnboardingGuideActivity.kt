package com.aris.voice

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.aris.voice.utilities.OnboardingManager
import com.aris.voice.utilities.UserProfileManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

class OnboardingGuideActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            OnboardingGuideScreen(
                onFinishGuide = {
                    completeGuideAndRoute()
                }
            )
        }
    }

    private fun completeGuideAndRoute() {
        val onboardingManager = OnboardingManager(this)
        onboardingManager.setGuideCompleted(true)

        val profileManager = UserProfileManager(this)
        val intent = if (!profileManager.isProfileComplete()) {
            Intent(this, LoginActivity::class.java)
        } else if (!onboardingManager.isOnboardingCompleted()) {
            Intent(this, OnboardingPermissionsActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

// Data models for the Slide-by-Slide feature guide
data class GuideSlide(
    val title: String,
    val subtitle: String,
    val description: String,
    val items: List<String>,
    val accentColor: Color
)

@Composable
fun OnboardingGuideScreen(onFinishGuide: () -> Unit) {
    var isShowcaseActive by remember { mutableStateOf(true) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121212) // Deep charcoal backplate
    ) {
        Crossfade(targetState = isShowcaseActive, label = "layout_change") { showcase ->
            if (showcase) {
                ShowcaseAnimationView(
                    onShowcaseComplete = {
                        isShowcaseActive = false
                    },
                    onSkipShowcase = {
                        isShowcaseActive = false
                    }
                )
            } else {
                InteractiveGuideCarouselView(onFinishGuide = onFinishGuide)
            }
        }
    }
}

/**
 * 1. 10-Second High-Fidelity Animated Showcase Preview Screen
 */
@Composable
fun ShowcaseAnimationView(
    onShowcaseComplete: () -> Unit,
    onSkipShowcase: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(0f) }
    var currentPhase by remember { mutableStateOf(1) } // Phase 1: 0-3s, Phase 2: 3-6s, Phase 3: 6-10s

    // Drive the 10-second animation timeline linearly
    LaunchedEffect(Unit) {
        val durationMs = 10000L
        val intervalMs = 20L
        val increment = intervalMs.toFloat() / durationMs.toFloat()
        
        while (progress < 1f) {
            delay(intervalMs)
            progress += increment
            
            // Set chapters based on percentage progress
            currentPhase = when {
                progress < 0.33f -> 1
                progress < 0.66f -> 2
                else -> 3
            }
        }
        onShowcaseComplete()
    }

    // Dynamic wave values driven by infinite loop
    val infiniteTransition = rememberInfiniteTransition(label = "accent_loop")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * java.lang.Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF121212), // Deep charcoal background
                        Color(0xFF1C1C1F), // Obsidian carbon
                        Color(0xFF0F0F10)  // Sleek dark graphite
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP HEADER: Story-style progress timeline indicator
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Segment 1 (Phase 1: 0%-33%)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (progress >= 0.33f) Color(0xFFE2E8F0)
                            else if (progress > 0f) Color(0xFFE2E8F0).copy(alpha = progress / 0.33f)
                            else Color.White.copy(alpha = 0.15f)
                        )
                )
                // Segment 2 (Phase 2: 33%-66%)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (progress >= 0.66f) Color(0xFF94A3B8)
                            else if (progress > 0.33f) Color(0xFF94A3B8).copy(alpha = (progress - 0.33f) / 0.33f)
                            else Color.White.copy(alpha = 0.15f)
                        )
                )
                // Segment 3 (Phase 3: 66%-100%)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (progress >= 1f) Color(0xFFCBD5E1)
                            else if (progress > 0.66f) Color(0xFFCBD5E1).copy(alpha = (progress - 0.66f) / 0.34f)
                            else Color.White.copy(alpha = 0.15f)
                        )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "INTRODUCING A.R.I.S",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp
                )

                Text(
                    text = "Skip Preview",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onSkipShowcase() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // CENTER: Dynamic interactive Stage View depending on simulated chapter
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentPhase,
                transitionSpec = {
                    slideInVertically { height -> height } + fadeIn() togetherWith
                            slideOutVertically { height -> -height } + fadeOut()
                },
                label = "phase_panels"
            ) { phase ->
                when (phase) {
                    1 -> PhaseOneSynthesizer(waveOffset)
                    2 -> PhaseTwoAwakening()
                    else -> PhaseThreeOverlayReady()
                }
            }
        }

        // BOTTOM STATUS CAPTION CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161922).copy(alpha = 0.85f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (currentPhase) {
                                    1 -> Color(0xFFE2E8F0) // Silver
                                    2 -> Color(0xFF94A3B8) // Steel
                                    else -> Color(0xFFCBD5E1) // Chrome
                                }
                            )
                    )
                    Text(
                        text = when (currentPhase) {
                            1 -> "PHASE 1: INITIALIZING C-LAE OFFLINE ENGINE"
                            2 -> "PHASE 2: ASSEMBLING LOCAL KNOWLEDGE GRAPH"
                            else -> "PHASE 3: ESTABLISHING COGNITIVE ROUTING"
                        },
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = when (currentPhase) {
                        1 -> "Mapping GPU Vulkan memory threads, caching off-grid LLM registries (Qwen-3 / Gemma-3), and loading fast on-device parameters."
                        2 -> "Assembling offline memory indices, private local context profiles, and localized sensor receptors for zero-latency operation."
                        else -> "Standby. Live overlay dashboard and cognitive gestures are armed. Seamless auto-failover executes requests off-grid flawlessly."
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * PHASE 1 VISUALIZERS: Dynamic glowing soundwave canvas
 */
@Composable
fun PhaseOneSynthesizer(waveOffset: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "MOUNTING C-LAE ENGINE...",
            color = Color(0xFFE2E8F0),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp
        )

        Spacer(modifier = Modifier.height(30.dp))

        // Simulated Boot Logs list
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(130.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                Text(
                    text = ">> local@aris_clae:~/vulkan",
                    color = Color(0xFF94A3B8).copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Spawning GPU Vulkan pipelines: DONE\n• Caching model registry (Qwen3 / Gemma3): LOADED\n• Initializing hands-free Hindi alarm parsing: SECURED\n• System off-grid telemetry logic: 100% OPERATIONAL",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Soundwave Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(60.dp)
        ) {
            val width = size.width
            val height = size.height
            val points = 60
            val pointGap = width / points

            for (i in 0 until points) {
                val x = i * pointGap
                val factor = sin(i.toFloat() * 0.25f + waveOffset)
                val amplitude = height * 0.4f * sin(i.toFloat() * Math.PI.toFloat() / points.toFloat())
                val waveY = (height / 2f) + (factor * amplitude)

                drawLine(
                    color = Color(0xFFE2E8F0).copy(alpha = 0.8f - (sin(i.toFloat() * 0.1f) * 0.3f)),
                    start = androidx.compose.ui.geometry.Offset(x, height / 2f),
                    end = androidx.compose.ui.geometry.Offset(x, waveY),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }
    }
}

/**
 * PHASE 2 VISUALIZERS: Colorful breathing AI circle representation
 */
@Composable
fun PhaseTwoAwakening() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scaleFactor by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "spin_pulse"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "AWAKENING LOCAL AGENT CHARACTER...",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Breath AI Core
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(110.dp * scaleFactor)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFE2E8F0).copy(alpha = 0.3f),
                            Color(0xFF64748B).copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
            }

            Canvas(modifier = Modifier.size(80.dp * scaleFactor)) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFE2E8F0),
                            Color(0xFF94A3B8),
                            Color(0xFFE2E8F0)
                        )
                    ),
                    style = Stroke(width = 6.dp.toPx())
                )
                // Center core
                drawCircle(
                    color = Color.White,
                    radius = 8.dp.toPx()
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = "A.R.I.S: \"On-device memory and LLM layers integrated!\"",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * PHASE 3 VISUALIZERS: Beautiful mock mockup showing overlay trigger
 */
@Composable
fun PhaseThreeOverlayReady() {
    val floatAnim = rememberInfiniteTransition(label = "orb_float")
    val floatOffset by floatAnim.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "val"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "DEPLOYING PORTABLE ASSISTANT...",
            color = Color(0xFFCBD5E1),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Device Mockup Container
        Box(
            modifier = Modifier
                .size(180.dp, 220.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF14161F))
                .padding(12.dp)
        ) {
            // Simulated Home Wallpapers
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)))
                    Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)))
                    Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                )
            }

            // Simulated Floating Aris/Overlay Widget
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .align(Alignment.Center)
                    .offset(y = floatOffset.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF252936))
                    .clickable {  },
                contentAlignment = Alignment.Center
            ) {
                // Outer ring glowing neon
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color(0xFFE2E8F0),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                // Simulated Creature Face/Logo
                Icon(
                    painter = painterResource(id = R.drawable.ic_frozen_creature),
                    contentDescription = "Widget Icon",
                    modifier = Modifier.size(32.dp),
                    tint = Color.Unspecified
                )
            }

            // Custom caption over mockup
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE2E8F0).copy(alpha = 0.15f))
                    .padding(4.dp)
            ) {
                Text(
                    text = "A.R.I.S Live Floating Trigger",
                    color = Color(0xFFE2E8F0),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 2. Slide-by-Slide Multi-Page Carousel Onboarding Guide Cards
 */
@Composable
fun InteractiveGuideCarouselView(onFinishGuide: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    val slides = listOf(
        GuideSlide(
            title = "C-LAE Offline Core",
            subtitle = "ON-DEVICE MULTI-MODEL RUNTIMES",
            description = "Map and execute lightweight open-weight models (Qwen-3, Gemma-3, Phi-4 Mini) completely offline. Enjoy lightning-fast local responses, zero API quotas, and fully private local context loops.",
            items = listOf("GPU Vulkan pipeline acceleration", "Direct multi-model registry swap", "Fully private offline contextual state"),
            accentColor = Color(0xFFCBD5E1) // Silver
        ),
        GuideSlide(
            title = "Smart Local Routines",
            subtitle = "HANDS-FREE ON-DEVICE SERVICES",
            description = "Buffering and connection lags are gone. Easily command ARIS to take screenshots, change volume parameters, drag notification shades, or set Hindi-adapted baje/minute alarms offline.",
            items = listOf("Direct accessibility haptic routine", "Precisely timed hour, minute, second alarms", "Intuitive localized voice triggers"),
            accentColor = Color(0xFF94A3B8) // Steel Gray
        ),
        GuideSlide(
            title = "Hybrid Cloud Gateway",
            subtitle = "HIGH RELIABILITY AUTO GATEWAY ROUTER",
            description = "Get the ultimate of both. When online, requests are routed to powerful Gemini Cloud models. When offline or during high quotas, ARIS auto-failover routes queries to local C-LAE layers.",
            items = listOf("Dynamic failover routing pipeline", "Seamless chat transcript continuity", "Ultra-safe localized offline memory logs"),
            accentColor = Color(0xFFE2E8F0) // Platinum Chrome
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Upper Headers
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "A.R.I.S COGNITIVE MANUAL",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Carousel Pager Selector dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                repeat(3) { index ->
                    val color = if (pagerState.currentPage == index) slides[index].accentColor else Color.White.copy(alpha = 0.2f)
                    val width = if (pagerState.currentPage == index) 24.dp else 8.dp
                    Box(
                        modifier = Modifier
                            .size(width, 8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }

        // Horizontal Slide Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { index ->
            val slide = slides[index]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Subtitle representation
                Text(
                    text = slide.subtitle,
                    color = slide.accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Heading Title
                Text(
                    text = slide.title,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Card Body
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161922)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = slide.description,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Divider(color = Color.White.copy(alpha = 0.1f))

                        // Features bullets list
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            slide.items.forEach { bullet ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Bullet Point Checked",
                                        tint = slide.accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = bullet,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Control Buttons section (Back, Next/Start)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Button: Back / Skip
            if (pagerState.currentPage > 0) {
                Text(
                    text = "Back",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                        .padding(16.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(60.dp))
            }

            // Right Button: Next or Finish
            val isLastPage = pagerState.currentPage == 2
            val buttonColor = if (isLastPage) Color(0xFFF1F5F9) else Color(0xFF1E212A)

            Button(
                onClick = {
                    if (isLastPage) {
                        onFinishGuide()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = if (isLastPage) Color(0xFF0A0B0E) else Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(52.dp)
                    .widthIn(min = 140.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isLastPage) "AWAKEN A.R.I.S" else "NEXT",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    if (!isLastPage) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Page Button"
                        )
                    }
                }
            }
        }
    }
}
