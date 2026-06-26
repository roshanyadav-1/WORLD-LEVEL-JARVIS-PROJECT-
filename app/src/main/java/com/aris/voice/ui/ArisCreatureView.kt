package com.aris.voice.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import com.aris.voice.utilities.ArisState
import kotlin.math.cos
import kotlin.math.sin

/**
 * A highly-customizable procedural holographic animated Aris creature
 * designed with futuristic glassmorphism, dynamic glowing states, and interactive physics.
 */
enum class CreatureAccessory {
    NONE,
    NEKO_EARS,
    DEVIL_HORNS,
    TECH_ANTENNA,
    CUTE_HALO,
    SMART_GLASSES
}

class ArisCreatureView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentState: ArisState = ArisState.IDLE
    private var animateValue = 0f
    private var rotationAngle = 0f
    private var scanOffset = 0f
    private var waveValue = 0f
    private var animationPhase = 0f
    
    // Blinking metrics
    private var lastBlinkTime = 0L
    private var isBlinking = false
    private val blinkDuration = 120L // ms
    
    // Accessories Customization (Saving and loaded automatically for customisable design!)
    var activeAccessory: CreatureAccessory = CreatureAccessory.NONE
        private set
    private var accessoryScale = 1f
    
    // 3D Rendering Components
    private val mCamera = Camera()
    private val mMatrix = Matrix()
    private var tiltX = 0f
    private var tiltY = 0f
    private var isPulseGrowing = true

    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#CBD5E1") // Chrome silver scan line
    }

    private var animator: ValueAnimator? = null
    private var xAnimator: ValueAnimator? = null

    // Horizontal sliding offset of the organism for scanning movement
    var currentXOffset = 0f
        private set

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        loadSavedAccessory()
        initAnimations()
    }

    private fun loadSavedAccessory() {
        try {
            val prefs = context.getSharedPreferences("aris_prefs", Context.MODE_PRIVATE)
            val savedName = prefs.getString("active_accessory", CreatureAccessory.NONE.name)
            activeAccessory = CreatureAccessory.valueOf(savedName ?: CreatureAccessory.NONE.name)
        } catch (e: Exception) {
            activeAccessory = CreatureAccessory.NONE
        }
    }

    fun cycleAccessory() {
        val accessories = CreatureAccessory.values()
        val nextIndex = (activeAccessory.ordinal + 1) % accessories.size
        activeAccessory = accessories[nextIndex]
        
        try {
            val prefs = context.getSharedPreferences("aris_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("active_accessory", activeAccessory.name).apply()
        } catch (e: Exception) {}
        
        startAccessoryChangeAnimation()
        invalidate()
        
        // Physical rumble feedback on accessory toggle
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50)
            }
        } catch (e: Exception) {}
        
        android.widget.Toast.makeText(context, "Accessory: ${activeAccessory.name.replace("_", " ")} ✨", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun startAccessoryChangeAnimation() {
        ValueAnimator.ofFloat(0.5f, 1.2f, 1.0f).apply {
            duration = 500
            interpolator = BounceInterpolator()
            addUpdateListener { anim ->
                accessoryScale = anim.animatedValue as Float
                invalidate()
            }
        }.start()
    }

    private fun initAnimations() {
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                animateValue = animation.animatedValue as Float
                rotationAngle += 2f
                if (rotationAngle >= 360f) {
                    rotationAngle -= 360f
                }
                
                // Continuous 3D floating 
                tiltX = sin(System.currentTimeMillis() / 800.0).toFloat() * 12f
                tiltY = cos(System.currentTimeMillis() / 1200.0).toFloat() * 15f
                
                // Dynamic smart random blink logic (Live feel)
                val currentTime = System.currentTimeMillis()
                if (!isBlinking && currentTime - lastBlinkTime > 3000 + (Math.random() * 4000).toLong()) {
                    isBlinking = true
                    lastBlinkTime = currentTime
                }
                if (isBlinking && currentTime - lastBlinkTime > blinkDuration) {
                    isBlinking = false
                }
                
                if (currentState == ArisState.PROCESSING) {
                    rotationAngle += 8f // intense calculation spin
                    tiltY = cos(System.currentTimeMillis() / 300.0).toFloat() * 30f
                } else if (currentState == ArisState.SPEAKING) {
                    scanOffset += 0.04f
                    if (scanOffset > 1.0f) scanOffset = 0f
                    tiltX = sin(System.currentTimeMillis() / 400.0).toFloat() * 20f
                }
                invalidate()
            }
        }
        animator?.start()
    }

    fun setState(state: ArisState) {
        val oldState = currentState
        currentState = state
        
        if (state == ArisState.SPEAKING) {
            startHorizontalScanMovement()
        } else {
            xAnimator?.cancel()
            currentXOffset = 0f
        }

        if (state == ArisState.IDLE && oldState == ArisState.SPEAKING) {
            startByeWavingAnimation()
        }
        
        invalidate()
    }

    private fun startHorizontalScanMovement() {
        xAnimator?.cancel()
        xAnimator = ValueAnimator.ofFloat(-120f, 120f).apply {
            duration = 3200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                currentXOffset = animation.animatedValue as Float
                invalidate()
            }
        }
        xAnimator?.start()
    }

    private fun startByeWavingAnimation() {
        waveValue = 0f
        val waveAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            interpolator = BounceInterpolator()
            addUpdateListener { anim ->
                waveValue = anim.animatedValue as Float
                invalidate()
            }
        }
        waveAnim.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w / 2f + currentXOffset
        val cy = h / 2f + 10f

        canvas.save()
        
        // 3D Perspective Transformation
        mCamera.save()
        mCamera.rotateX(tiltX)
        mCamera.rotateY(tiltY)
        // Add a breathing depth effect
        val zDepth = -50f * animateValue
        mCamera.translate(0f, 0f, zDepth)
        
        mCamera.getMatrix(mMatrix)
        mCamera.restore()
        mMatrix.preTranslate(-cx, -cy)
        mMatrix.postTranslate(cx, cy)
        canvas.concat(mMatrix)
        
        // 1. Full-screen Holographic Grid / Scan Line
        if (currentState == ArisState.SPEAKING) {
            val scanY = h * scanOffset
            scanPaint.shader = LinearGradient(0f, scanY, w, scanY,
                intArrayOf(Color.TRANSPARENT, Color.parseColor("#CBD5E1"), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP)
            canvas.drawLine(0f, scanY, w, scanY, scanPaint)
            
            val scanGlowPaint = Paint().apply {
                style = Paint.Style.FILL
                shader = LinearGradient(0f, scanY - 60f, 0f, scanY + 60f,
                    intArrayOf(Color.TRANSPARENT, Color.parseColor("#33CBD5E1"), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, scanY - 60f, w, scanY + 60f, scanGlowPaint)
        }

        // 2. Render holographic creature with size-adaptive scaling
        // By using 0.16f scale, the maximum outer orbital shell fits perfectly with safety padding for the 3D blur shadows
        val baseRadiusScale = Math.min(w, h) * 0.16f
        val scaleFactor = baseRadiusScale / 55f
        
        canvas.save()
        canvas.scale(scaleFactor, scaleFactor, cx, cy)
        drawArisCreature(canvas, cx, cy)
        canvas.restore()

        canvas.restore()
    }

    private fun drawArisCreature(canvas: Canvas, cx: Float, cy: Float) {
        val baseRadius = 55f
        val glowColor = getGlowColorForState()
        
        mainPaint.setShadowLayer(40f, 0f, 0f, glowColor)
        fillPaint.setShadowLayer(45f, 0f, 0f, glowColor)

        // Geometric background core shadow
        fillPaint.color = Color.parseColor("#121212")
        canvas.drawCircle(cx, cy, baseRadius * 1.5f, fillPaint)

        // Draw cute floating miniature cyber-energy bio-particles around the core (Alive feel!)
        fillPaint.color = glowColor
        fillPaint.alpha = 140
        for (i in 0..3) {
            val phase = System.currentTimeMillis() / (600.0 + i * 150.0) + (i * 45.0)
            val orbitRadius = baseRadius * 1.3f
            val px = cx + orbitRadius * cos(phase).toFloat()
            val py = cy + orbitRadius * sin(phase).toFloat() / 2.5f - 10f
            canvas.drawCircle(px, py, 3.5f + i % 2, fillPaint)
        }
        fillPaint.alpha = 255

        // Draw 3D Energy Orbits
        drawOrbitalShells(canvas, cx, cy, glowColor)

        // Draw mechanical floating core
        val intensity = 1.0f + (0.2f * animateValue)
        val coreRadius = baseRadius * intensity
        
        fillPaint.color = Color.parseColor("#101018") // deep cyber metallic
        canvas.drawCircle(cx, cy, coreRadius, fillPaint)
        
        // Glowing atomic nucleus with neon ring
        fillPaint.color = glowColor
        canvas.drawCircle(cx, cy, coreRadius * 0.55f, fillPaint)
        mainPaint.style = Paint.Style.STROKE
        mainPaint.strokeWidth = 2f
        canvas.drawCircle(cx, cy, coreRadius * 0.75f, mainPaint)

        // Draw Customizable Accessory on top of the head
        drawAccessory(canvas, cx, cy, coreRadius)

        // Eye state animations
        drawFaceExpression(canvas, cx, cy)

        // Draw the dizzy halo above the head in custom state
        if (currentState == ArisState.DIZZY) {
            drawDizzyStarsAndHalo(canvas, cx, cy)
        }

        // Wave bye-bye ✋ hand animation
        if (waveValue > 0f && waveValue < 0.99f) {
            drawWavingHand(canvas, cx + coreRadius + 30f, cy - 20f, glowColor)
        }
    }

    private fun getGlowColorForState(): Int {
        return when (currentState) {
            ArisState.LISTENING -> Color.parseColor("#E2E8F0")  // Platinum Slate - listening
            ArisState.PROCESSING -> Color.parseColor("#CBD5E1") // Chrome Silver - thinking/processing
            ArisState.SPEAKING -> Color.parseColor("#F8FAFC")   // Pure Bright Silver - speaking
            ArisState.ERROR -> Color.parseColor("#FF5252")      // Radiant Crimson - error state
            ArisState.IDLE -> Color.parseColor("#94A3B8")       // Sleek Metallic Slate - idle state
            ArisState.DIZZY -> Color.parseColor("#64748B")      // Dull Brushed Titanium - dizzy state
        }
    }

    private fun drawOrbitalShells(canvas: Canvas, cx: Float, cy: Float, glowColor: Int) {
        mainPaint.style = Paint.Style.STROKE
        mainPaint.color = glowColor
        
        canvas.save()
        // Create 3D spherical orbits by rotating individual rings
        val rings = 5
        for (i in 0 until rings) {
            val ringScale = (animateValue + i / rings.toFloat()) % 1.0f
            val r = 65f + ringScale * 90f
            
            canvas.save()
            canvas.rotate(rotationAngle * (if (i % 2 == 0) 1 else -1) + (i * 30), cx, cy)
            
            // Give 3D perspective to individual rings!
            if (currentState == ArisState.PROCESSING || currentState == ArisState.LISTENING) {
                val scaleY = Math.abs(cos(rotationAngle / 30f))
                canvas.scale(1f, scaleY + 0.3f, cx, cy)
            }

            mainPaint.strokeWidth = 4f * (1.0f - ringScale)
            mainPaint.alpha = ((1.0f - ringScale) * 255).toInt()
            
            if (i % 2 == 0) {
                // Dashed ring for high tech feel
                mainPaint.pathEffect = DashPathEffect(floatArrayOf(20f, 10f), rotationAngle)
            } else {
                mainPaint.pathEffect = null
            }

            canvas.drawCircle(cx, cy, r, mainPaint)
            canvas.restore()
        }
        mainPaint.pathEffect = null
        canvas.restore()
    }

    private fun drawFaceExpression(canvas: Canvas, cx: Float, cy: Float) {
        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            setShadowLayer(10f, 0f, 0f, Color.WHITE) // Glowing eyes
        }
        
        val dpMultiplier = resources.displayMetrics.density

        // Draw cute rosy cheeks (blush, always visible except when error or dizzy - adds massive cuteness!)
        if (currentState != ArisState.ERROR && currentState != ArisState.DIZZY) {
            val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FF4081") // Warm pink blush color
                style = Paint.Style.FILL
                alpha = 90 // semi-transparent
                setShadowLayer(12f, 0f, 0f, Color.parseColor("#FF4081"))
            }
            canvas.drawCircle(cx - 18f, cy + 3f, 4.5f, blushPaint)
            canvas.drawCircle(cx + 18f, cy + 3f, 4.5f, blushPaint)
        }

        when (currentState) {
            ArisState.LISTENING -> {
                if (isBlinking) {
                    canvas.drawLine(cx - 18f, cy - 6f, cx - 6f, cy - 6f, eyePaint)
                    canvas.drawLine(cx + 6f, cy - 6f, cx + 18f, cy - 6f, eyePaint)
                } else {
                    // Extremely cute smiling arcs (^ _ ^) for listening feedback!
                    val leftPath = Path().apply {
                        moveTo(cx - 20f, cy - 4f)
                        quadTo(cx - 13f, cy - 13f, cx - 6f, cy - 4f)
                    }
                    val rightPath = Path().apply {
                        moveTo(cx + 6f, cy - 4f)
                        quadTo(cx + 13f, cy - 13f, cx + 20f, cy - 4f)
                    }
                    canvas.drawPath(leftPath, eyePaint)
                    canvas.drawPath(rightPath, eyePaint)
                }
            }
            ArisState.PROCESSING -> {
                canvas.save()
                canvas.rotate(-rotationAngle * 3, cx, cy)
                eyePaint.style = Paint.Style.FILL
                // Futuristic rotating tech reticle
                canvas.drawRect(cx - 15f, cy - 5f, cx - 7f, cy + 5f, eyePaint)
                canvas.drawRect(cx + 7f, cy - 5f, cx + 15f, cy + 5f, eyePaint)
                canvas.restore()
            }
            ArisState.SPEAKING -> {
                if (isBlinking) {
                    canvas.drawLine(cx - 18f, cy - 6f, cx - 6f, cy - 6f, eyePaint)
                    canvas.drawLine(cx + 6f, cy - 6f, cx + 18f, cy - 6f, eyePaint)
                } else {
                    eyePaint.style = Paint.Style.FILL
                    eyePaint.color = Color.parseColor("#000000") // Black core mask
                    val visor = RectF(cx - 22f, cy - (8f * dpMultiplier), cx + 22f, cy + (2f * dpMultiplier))
                    canvas.drawRoundRect(visor, 8f, 8f, eyePaint)
                    
                    // Floating sound wave in speaking state
                    eyePaint.color = getGlowColorForState()
                    val width = 12f * animateValue
                    canvas.drawRect(cx - width, cy - 2f, cx + width, cy + 2f, eyePaint)
                }
            }
            ArisState.ERROR -> {
                canvas.drawLine(cx - 18f, cy - 12f, cx - 6f, cy - 0f, eyePaint)
                canvas.drawLine(cx - 6f, cy - 12f, cx - 18f, cy - 0f, eyePaint)
                canvas.drawLine(cx + 6f, cy - 12f, cx + 18f, cy - 0f, eyePaint)
                canvas.drawLine(cx + 18f, cy - 12f, cx + 6f, cy - 0f, eyePaint)
            }
            ArisState.DIZZY -> {
                // Dizzy eyes (crosses) plus a squiggly dizzy mouth
                canvas.drawLine(cx - 18f, cy - 12f, cx - 6f, cy - 0f, eyePaint)
                canvas.drawLine(cx - 6f, cy - 12f, cx - 18f, cy - 0f, eyePaint)
                canvas.drawLine(cx + 6f, cy - 12f, cx + 18f, cy - 0f, eyePaint)
                canvas.drawLine(cx + 18f, cy - 12f, cx + 6f, cy - 0f, eyePaint)
                
                val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                }
                val mouthPath = Path().apply {
                    moveTo(cx - 8f, cy + 12f)
                    quadTo(cx, cy + 6f, cx + 8f, cy + 12f)
                }
                canvas.drawPath(mouthPath, mouthPaint)
            }
            ArisState.IDLE -> {
                if (isBlinking) {
                    canvas.drawLine(cx - 18f, cy - 6f, cx - 6f, cy - 6f, eyePaint)
                    canvas.drawLine(cx + 6f, cy - 6f, cx + 18f, cy - 6f, eyePaint)
                } else {
                    eyePaint.style = Paint.Style.FILL
                    eyePaint.color = Color.WHITE
                    
                    // Intelligent eye movement (looking around)
                    val lookOffsetX = (sin(System.currentTimeMillis() / 1500.0) * 3f).toFloat()
                    val lookOffsetY = (cos(System.currentTimeMillis() / 2200.0) * 2f).toFloat()
                    
                    // Round glowing anime-style cute eyes (Life/Cuteness feel!)
                    canvas.drawCircle(cx - 12f + lookOffsetX, cy - 6f + lookOffsetY, 6.5f, eyePaint)
                    canvas.drawCircle(cx + 12f + lookOffsetX, cy - 6f + lookOffsetY, 6.5f, eyePaint)
                    
                    // Cute eye reflection sparkles
                    val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#101018") // Matches base visor
                        style = Paint.Style.FILL
                    }
                    canvas.drawCircle(cx - 10f + lookOffsetX, cy - 4f + lookOffsetY, 2.2f, shinePaint)
                    canvas.drawCircle(cx + 10f + lookOffsetX, cy - 4f + lookOffsetY, 2.2f, shinePaint)
                }
            }
        }
    }

    private fun drawAccessory(canvas: Canvas, cx: Float, cy: Float, coreRadius: Float) {
        if (activeAccessory == CreatureAccessory.NONE) return

        canvas.save()
        // Apply customizable bouncy accessory scale centered on the head
        canvas.scale(accessoryScale, accessoryScale, cx, cy - 35f)

        val accPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * accessoryScale
            strokeCap = Paint.Cap.ROUND
        }
        val fillAccPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        
        val baseGlowColor = getGlowColorForState()
        accPaint.setShadowLayer(15f, 0f, 0f, baseGlowColor)
        fillAccPaint.setShadowLayer(15f, 0f, 0f, baseGlowColor)

        when (activeAccessory) {
            CreatureAccessory.NEKO_EARS -> {
                accPaint.color = baseGlowColor
                fillAccPaint.color = Color.parseColor("#FF80AB") // Cute Pink
                fillAccPaint.alpha = 150
                
                // Left Ear Path
                val leftEar = Path().apply {
                    moveTo(cx - 28f, cy - 38f)
                    lineTo(cx - 38f, cy - 60f)
                    lineTo(cx - 10f, cy - 47f)
                    close()
                }
                canvas.drawPath(leftEar, fillAccPaint)
                canvas.drawPath(leftEar, accPaint)

                // Right Ear Path
                val rightEar = Path().apply {
                    moveTo(cx + 10f, cy - 47f)
                    lineTo(cx + 38f, cy - 60f)
                    lineTo(cx + 28f, cy - 38f)
                    close()
                }
                canvas.drawPath(rightEar, fillAccPaint)
                canvas.drawPath(rightEar, accPaint)
            }
            CreatureAccessory.DEVIL_HORNS -> {
                accPaint.color = Color.parseColor("#FF3D00") // Neon Red
                fillAccPaint.color = Color.parseColor("#FF3D00")
                fillAccPaint.alpha = 180

                // Left Horn Path
                val leftHorn = Path().apply {
                    moveTo(cx - 20f, cy - 41f)
                    cubicTo(cx - 28f, cy - 48f, cx - 35f, cy - 50f, cx - 33f, cy - 62f)
                    cubicTo(cx - 22f, cy - 54f, cx - 18f, cy - 49f, cx - 12f, cy - 44f)
                    close()
                }
                canvas.drawPath(leftHorn, fillAccPaint)
                canvas.drawPath(leftHorn, accPaint)

                // Right Horn Path
                val rightHorn = Path().apply {
                    moveTo(cx + 12f, cy - 44f)
                    cubicTo(cx + 18f, cy - 49f, cx + 22f, cy - 54f, cx + 33f, cy - 62f)
                    cubicTo(cx + 35f, cy - 50f, cx + 28f, cy - 48f, cx + 20f, cy - 41f)
                    close()
                }
                canvas.drawPath(rightHorn, fillAccPaint)
                canvas.drawPath(rightHorn, accPaint)
            }
            CreatureAccessory.TECH_ANTENNA -> {
                accPaint.color = baseGlowColor
                accPaint.strokeWidth = 4f
                // Main antenna rod
                canvas.drawLine(cx, cy - 43f, cx, cy - 68f, accPaint)
                
                // Pulsing bulb
                val pulseVal = (sin(System.currentTimeMillis() / 200.0).toFloat() + 1f) / 2f
                fillAccPaint.color = baseGlowColor
                fillAccPaint.alpha = (100 + pulseVal * 155).toInt()
                
                val bulbRadius = 6f + pulseVal * 3f
                canvas.drawCircle(cx, cy - 70f, bulbRadius, fillAccPaint)
                
                // Mini tech rings on antenna
                accPaint.strokeWidth = 2f
                canvas.drawArc(RectF(cx - 6f, cy - 54f, cx + 6f, cy - 50f), 0f, 360f, false, accPaint)
            }
            CreatureAccessory.CUTE_HALO -> {
                accPaint.color = Color.parseColor("#FFD700") // Golden Halo
                accPaint.strokeWidth = 5f
                
                val haloRect = RectF(cx - 25f, cy - 72f, cx + 25f, cy - 60f)
                canvas.drawOval(haloRect, accPaint)
                
                // Add a subtle glowing yellow fill
                fillAccPaint.color = Color.parseColor("#FFD700")
                fillAccPaint.alpha = 50
                canvas.drawOval(haloRect, fillAccPaint)
            }
            CreatureAccessory.SMART_GLASSES -> {
                accPaint.color = Color.parseColor("#00E5FF") // Electric Cyan Glass
                accPaint.strokeWidth = 3f
                fillAccPaint.color = Color.parseColor("#00E5FF")
                fillAccPaint.alpha = 80
                
                val leftLens = RectF(cx - 22f, cy - 14f, cx - 4f, cy - 2f)
                val rightLens = RectF(cx + 4f, cy - 14f, cx + 22f, cy - 2f)
                
                canvas.drawRoundRect(leftLens, 4f, 4f, fillAccPaint)
                canvas.drawRoundRect(leftLens, 4f, 4f, accPaint)
                canvas.drawRoundRect(rightLens, 4f, 4f, fillAccPaint)
                canvas.drawRoundRect(rightLens, 4f, 4f, accPaint)
                
                // Bridge
                canvas.drawLine(cx - 4f, cy - 8f, cx + 4f, cy - 8f, accPaint)
                
                // Stylish reflection lines inside lens
                accPaint.color = Color.WHITE
                accPaint.strokeWidth = 1.5f
                canvas.drawLine(cx - 20f, cy - 12f, cx - 14f, cy - 4f, accPaint)
                canvas.drawLine(cx + 6f, cy - 12f, cx + 12f, cy - 4f, accPaint)
            }
            CreatureAccessory.NONE -> {}
        }
        
        canvas.restore()
    }

    private fun drawDizzyStarsAndHalo(canvas: Canvas, cx: Float, cy: Float) {
        // Draw gold/yellow spinning stars halo
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD700") // Gold
            style = Paint.Style.FILL
            setShadowLayer(10f, 0f, 0f, Color.parseColor("#FFD700"))
        }
        // Oval outline for the dizzy ring
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#80FFD700") // Translucent gold
            style = Paint.Style.STROKE
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), rotationAngle)
        }
        
        val oval = RectF(cx - 65f, cy - 90f, cx + 65f, cy - 70f)
        canvas.drawOval(oval, ringPaint)
        
        // Draw 3 spinning stars on the oval path
        val numStars = 3
        for (i in 0 until numStars) {
            val angleRad = Math.toRadians((rotationAngle + i * (360f / numStars)).toDouble())
            // Project around the oval
            val sx = cx + 65f * cos(angleRad).toFloat()
            val sy = cy - 80f + 10f * sin(angleRad).toFloat()
            drawStar(canvas, sx, sy, 5, 12f, 6f, starPaint)
        }
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, spikes: Int, outerRadius: Float, innerRadius: Float, paint: Paint) {
        val path = Path()
        var rot = Math.PI / 2 * 3
        val step = Math.PI / spikes
        
        path.moveTo(cx, cy - outerRadius)
        for (i in 0 until spikes) {
            var x = cx + cos(rot).toFloat() * outerRadius
            var y = cy + sin(rot).toFloat() * outerRadius
            path.lineTo(x, y)
            rot += step
            
            x = cx + cos(rot).toFloat() * innerRadius
            y = cy + sin(rot).toFloat() * innerRadius
            path.lineTo(x, y)
            rot += step
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawWavingHand(canvas: Canvas, hx: Float, hy: Float, handColor: Int) {
        val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = handColor
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
            strokeCap = Paint.Cap.ROUND
        }
        
        canvas.save()
        val degrees = sin(System.currentTimeMillis().toDouble() / 100.0).toFloat() * 25f
        canvas.rotate(degrees, hx, hy)
        
        canvas.drawCircle(hx, hy, 10f, handPaint)
        
        for (i in 0..4) {
            val angle = -120f + i * 18f
            val rad = Math.toRadians(angle.toDouble())
            val xStart = hx + 10f * cos(rad).toFloat()
            val yStart = hy + 10f * sin(rad).toFloat()
            
            val len = if (i == 2) 20f else if (i == 1 || i == 3) 17f else 13f
            val xEnd = hx + (10f + len) * cos(rad).toFloat()
            val yEnd = hy + (10f + len) * sin(rad).toFloat()
            
            canvas.drawLine(xStart, yStart, xEnd, yEnd, handPaint)
        }
        canvas.restore()
    }
}
