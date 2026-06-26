package com.aris.voice

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import kotlin.math.*

class HoloWorkspaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paints
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0A2342")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 2.0f
        style = Paint.Style.STROKE
    }

    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FFDE")
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 0f, Color.parseColor("#00FFDE"))
    }

    private val skeletonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        strokeWidth = 4f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }

    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8BC34A")
        style = Paint.Style.FILL
        setShadowLayer(10f, 0f, 0f, Color.parseColor("#8BC34A"))
    }

    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF0055")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FFDE")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    // 3D Sphere/Globe Points (Tony Stark Hologram)
    private class Point3D(val x: Float, val y: Float, val z: Float)
    private val sphereNodes = ArrayList<Point3D>()
    private val ringNodes = ArrayList<Point3D>()

    // Transform parameters
    var holoRotationY = 0f
    var holoRotationX = 0f
    var scaleFactor = 1.0f

    // Hand tracking coordinates (Simulated or Real camera tracked)
    var pointerX = 300f
    var pointerY = 300f
    var pointerZ = 200f
    var activeHandCount = 1 // 0, 1 or 2

    // Real detected hands' landmarks
    class HandLandmark(val x: Float, val y: Float, val z: Float)
    var detectedHands: List<List<HandLandmark>> = emptyList()

    // Ripples triggered on "Double tap / Click"
    private class Ripple(val x: Float, val y: Float) {
        var radius = 10f
        var alpha = 255
    }
    private val activeRipples = ArrayList<Ripple>()

    // Animation loops for rotating sphere automatically
    private var lastTime = System.currentTimeMillis()
    private var rotateAnimator: ValueAnimator? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // Required for shadowLayer drawing natively
        generate3DModels()
        startAutomaticPulsing()
    }

    private fun generate3DModels() {
        // Generate a 3D Sphere matching Latitude/Longitude divisions
        val latitudeDivisions = 12
        val longitudeDivisions = 18
        val radius = 120f

        for (i in 0..latitudeDivisions) {
            val lat = Math.PI * i / latitudeDivisions - Math.PI / 2
            val cosLat = cos(lat)
            val sinLat = sin(lat)

            for (j in 0 until longitudeDivisions) {
                val lon = 2 * Math.PI * j / longitudeDivisions
                val cosLon = cos(lon)
                val sinLon = sin(lon)

                val x = (radius * cosLat * cosLon).toFloat()
                val y = (radius * sinLat).toFloat()
                val z = (radius * cosLat * sinLon).toFloat()

                sphereNodes.add(Point3D(x, y, z))
            }
        }

        // Generate custom surrounding orbital tech rings
        val ringCount = 36
        val ringRadius = 160f
        for (i in 0 until ringCount) {
            val angle = 2 * Math.PI * i / ringCount
            val x = (ringRadius * cos(angle)).toFloat()
            val y = 0f
            val z = (ringRadius * sin(angle)).toFloat()
            ringNodes.add(Point3D(x, y, z))
        }
    }

    private fun startAutomaticPulsing() {
        // Slow continuous ambient rotate spinner
        rotateAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 16000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val angleVal = animator.animatedValue as Float
                holoRotationY = angleVal
                invalidate()
            }
            start()
        }
    }

    // Capture user touch-drag to rotate the hologram with hand or finger directly inside workspace card
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var initialRotationY = 0f
    private var initialRotationX = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        pointerX = event.x
        pointerY = event.y
        invalidate()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.x
                dragStartY = event.y
                initialRotationY = holoRotationY
                initialRotationX = holoRotationX
                triggerAirClick(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - dragStartX
                val deltaY = event.y - dragStartY
                holoRotationY = initialRotationY + deltaX * 0.5f
                holoRotationX = initialRotationX - deltaY * 0.5f // Invert Y navigation
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // Public method to trigger Tony Stark ripple pings!
    fun triggerAirClick(x: Float, y: Float) {
        val newRipple = Ripple(x, y)
        synchronized(activeRipples) {
            activeRipples.add(newRipple)
            if (activeRipples.size > 8) {
                activeRipples.removeAt(0)
            }
        }
        animateRipple(newRipple)
    }

    private fun animateRipple(ripple: Ripple) {
        val anim = ValueAnimator.ofFloat(10f, 120f).apply {
            duration = 600
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                ripple.radius = animator.animatedValue as Float
                ripple.alpha = (255 * (1f - (ripple.radius - 10f) / 110f)).toInt().coerceIn(0, 255)
                invalidate()
            }
        }
        anim.start()
    }

    // Public method to trigger hands rotation matrix
    fun simulateRotationSwipe() {
        val anim = ValueAnimator.ofFloat(holoRotationY, holoRotationY + 90f).apply {
            duration = 800
            addUpdateListener { animator ->
                holoRotationY = animator.animatedValue as Float
                invalidate()
            }
        }
        anim.start()
    }

    // Public method to zoom pinch hologram
    fun simulatePinchScale() {
        val anim = ValueAnimator.ofFloat(scaleFactor, 1.6f, 1.0f).apply {
            duration = 1200
            addUpdateListener { animator ->
                scaleFactor = animator.animatedValue as Float
                invalidate()
            }
        }
        anim.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cX = width / 2f
        val cY = height / 2f

        // Draw techy workspace background grid
        drawGridPattern(canvas)

        // Draw 3D projected orbiting tech-ring (rotated in 3D)
        drawRotatedPoints(canvas, ringNodes, cX, cY, ringPaint, isLine = true)

        // Draw 3D projected sphere globe nodes (rotated in 3D)
        drawRotatedPoints(canvas, sphereNodes, cX, cY, nodePaint, isLine = false)

        // Draw real-time hand-joint tracking lines (Tony Stark HUD overlay outline)
        if (detectedHands.isNotEmpty()) {
            drawRealHandSkeleton(canvas)
        } else if (activeHandCount > 0) {
            drawHandSkeletonHUD(canvas, cX, cY)
        }

        // Draw tracking pointer cursor (Air mouse pointer)
        drawAirPointer(canvas)

        // Draw active radar click ripples
        drawRipples(canvas)
    }

    private fun drawGridPattern(canvas: Canvas) {
        val step = 40f
        // Vertical lines
        var x = 0f
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += step
        }
        // Horizontal lines
        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += step
        }

        // Concentric target circles on bottom grid
        gridPaint.alpha = 150
        canvas.drawCircle(width / 2f, height / 2f, 180f, gridPaint)
        canvas.drawCircle(width / 2f, height / 2f, 280f, gridPaint)
        gridPaint.alpha = 255
    }

    private fun drawRotatedPoints(
        canvas: Canvas,
        points: List<Point3D>,
        cX: Float,
        cY: Float,
        paint: Paint,
        isLine: Boolean
    ) {
        val radY = Math.toRadians(holoRotationY.toDouble())
        val radX = Math.toRadians(holoRotationX.toDouble())

        val cosY = cos(radY)
        val sinY = sin(radY)
        val cosX = cos(radX)
        val sinX = sin(radX)

        val projectedPoints = ArrayList<PointF>()
        val depths = ArrayList<Float>()

        for (p in points) {
            // Y-rotation
            val x1 = p.x * cosY - p.z * sinY
            val z1 = p.x * sinY + p.z * cosY

            // X-rotation
            val y2 = p.y * cosX - z1 * sinX
            val z2 = p.y * sinX + z1 * cosX

            // Scale factor applying depth (orthographic projection with perspective scaling)
            val dScale = scaleFactor * (1.1f + z2.toFloat() / 500f)
            val projX = (cX + x1 * dScale).toFloat()
            val projY = (cY + y2 * dScale).toFloat()

            projectedPoints.add(PointF(projX, projY))
            depths.add(z2.toFloat())
        }

        if (isLine) {
            val path = Path()
            if (projectedPoints.isNotEmpty()) {
                path.moveTo(projectedPoints[0].x, projectedPoints[0].y)
                for (i in 1 until projectedPoints.size) {
                    path.lineTo(projectedPoints[i].x, projectedPoints[i].y)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
        } else {
            // Draw spherical nodes with particle size proportional to depth (back/front particles)
            for (i in projectedPoints.indices) {
                val pt = projectedPoints[i]
                val zDepth = depths[i]
                // Only draw nodes in workspace screen limits safely
                if (pt.x >= 0 && pt.x <= width && pt.y >= 0 && pt.y <= height) {
                    // Size proportional to depth
                    val size = (zDepth + 150f) / 60f
                    val sizeClamped = size.coerceIn(1.5f, 6.0f)
                    
                    // Modify transparency for back nodes to heighten 3D depth illusion
                    if (zDepth < 0) {
                        paint.alpha = 110
                    } else {
                        paint.alpha = 255
                    }
                    canvas.drawCircle(pt.x, pt.y, sizeClamped, paint)
                }
            }
            paint.alpha = 255
        }
    }

    private fun drawRealHandSkeleton(canvas: Canvas) {
        val connections = listOf(
            // Thumb
            Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4),
            // Index
            Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8),
            // Middle
            Pair(9, 10), Pair(10, 11), Pair(11, 12),
            // Ring
            Pair(13, 14), Pair(14, 15), Pair(15, 16),
            // Pinky
            Pair(0, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20),
            // Palm bases
            Pair(5, 9), Pair(9, 13), Pair(13, 17)
        )

        for (hand in detectedHands) {
            // Draw bones
            for (conn in connections) {
                if (conn.first < hand.size && conn.second < hand.size) {
                    val p1 = hand[conn.first]
                    val p2 = hand[conn.second]
                    
                    val x1 = p1.x * width
                    val y1 = p1.y * height
                    val x2 = p2.x * width
                    val y2 = p2.y * height
                    
                    canvas.drawLine(x1, y1, x2, y2, skeletonPaint)
                }
            }
            
            // Draw joints
            for (i in hand.indices) {
                val pt = hand[i]
                val x = pt.x * width
                val y = pt.y * height
                
                // Highlight tip of index finger (tracker pointer)
                if (i == 8) {
                    canvas.drawCircle(x, y, 12f, cursorPaint)
                    canvas.drawCircle(x, y, 4f, nodePaint)
                } else if (i == 4) {
                    // Highlight thumb tip
                    canvas.drawCircle(x, y, 8f, jointPaint)
                } else {
                    canvas.drawCircle(x, y, 6f, jointPaint)
                }
            }
        }
    }

    private fun drawHandSkeletonHUD(canvas: Canvas, cX: Float, cY: Float) {
        // Draw elegant joints and skeleton structure following pointerX, pointerY
        // Simulated: We generate a detailed futuristic green hand vector centered around current coordinate
        val wristX = pointerX + 40f
        val wristY = pointerY + 160f

        // Let's draw bones
        val path = Path().apply {
            // Wrist base
            moveTo(wristX - 30f, wristY)
            lineTo(wristX + 30f, wristY)
            // Palm bone mappings
            lineTo(pointerX, pointerY)
            moveTo(wristX - 30f, wristY)
            lineTo(pointerX - 45f, pointerY + 30f) // Thumb base
            lineTo(pointerX - 35f, pointerY - 40f) // Thumb tip
            
            // Index Finger (the active mouse tracking tip!)
            moveTo(pointerX, pointerY)
            lineTo(pointerX - 10f, pointerY - 40f)
            lineTo(pointerX - 15f, pointerY - 90f) // Index tip is exactly here!
            
            // Middle Finger
            moveTo(pointerX, pointerY)
            lineTo(pointerX + 15f, pointerY - 45f)
            lineTo(pointerX + 20f, pointerY - 95f)

            // Ring Finger
            moveTo(pointerX, pointerY)
            lineTo(pointerX + 40f, pointerY - 40f)
            lineTo(pointerX + 45f, pointerY - 85f)

            // Pinky Finger
            moveTo(pointerX, pointerY)
            lineTo(pointerX + 60f, pointerY - 30f)
            lineTo(pointerX + 65f, pointerY - 70f)
        }

        // Draw bones outline dashed line
        canvas.drawPath(path, skeletonPaint)

        // Draw shining neon joint nods
        canvas.drawCircle(wristX, wristY, 8f, jointPaint)
        canvas.drawCircle(pointerX, pointerY, 6f, jointPaint)
        canvas.drawCircle(pointerX - 15f, pointerY - 90f, 9f, jointPaint) // Index Pointer Tip is larger to represent main driver
        canvas.drawCircle(pointerX + 20f, pointerY - 95f, 6f, jointPaint)
        canvas.drawCircle(pointerX + 45f, pointerY - 85f, 6f, jointPaint)
        canvas.drawCircle(pointerX + 65f, pointerY - 70f, 6f, jointPaint)

        // Link Index TIP to target cursor with a dynamic scanning indicator vector line
        val scanPaint = Paint().apply {
            color = Color.parseColor("#444CAF50")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(pointerX - 15f, pointerY - 90f, pointerX, pointerY, scanPaint)
    }

    private fun drawAirPointer(canvas: Canvas) {
        // Futuristic pointer coordinates design (Crosshair target locator)
        canvas.drawCircle(pointerX, pointerY, 30f, cursorPaint)
        canvas.drawCircle(pointerX, pointerY, 4f, cursorPaint)
        // Crosshair reticle lines
        canvas.drawLine(pointerX - 45f, pointerY, pointerX - 15f, pointerY, cursorPaint)
        canvas.drawLine(pointerX + 15f, pointerY, pointerX + 45f, pointerY, cursorPaint)
        canvas.drawLine(pointerX, pointerY - 45f, pointerX, pointerY - 15f, cursorPaint)
        canvas.drawLine(pointerX, pointerY + 15f, pointerX, pointerY + 45f, cursorPaint)
    }

    private fun drawRipples(canvas: Canvas) {
        synchronized(activeRipples) {
            for (ripple in activeRipples) {
                ripplePaint.alpha = ripple.alpha
                canvas.drawCircle(ripple.x, ripple.y, ripple.radius, ripplePaint)
                canvas.drawCircle(ripple.x, ripple.y, ripple.radius * 0.7f, ripplePaint)
            }
        }
    }

    // Clean animations off lifecycle
    fun stopAnimations() {
        rotateAnimator?.cancel()
    }
}
