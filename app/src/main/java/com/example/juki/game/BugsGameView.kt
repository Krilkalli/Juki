package com.example.juki.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import com.example.juki.domain.model.GameSettings
import com.example.juki.domain.model.GoldRate
import com.example.juki.R
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class BugsGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onHit: ((Int) -> Unit)? = null
    var onMiss: (() -> Unit)? = null

    private val bugs = mutableListOf<Bug>()
    private var maxBugs = 5
    private var speedFactor = 1f
    private var inputEnabled = false
    private var bonus: TiltBonus? = null
    private var bonusElapsedSec = 0f
    private var goldElapsedSec = 0f
    private var goldPoints: Int? = null
    private var tiltRemainingSec = 0f
    private var tiltX = 0f
    private var tiltY = 0f
    private var fieldWidth = Bug.FIELD_SIZE
    private var fieldHeight = Bug.FIELD_SIZE

    var onBonusCollected: (() -> Unit)? = null
    var onGoldUnavailable: (() -> Unit)? = null

    private val bonusDrawable by lazy {
        requireNotNull(
            AppCompatResources.getDrawable(context, R.drawable.ic_tilt_bonus)
        ).mutate()
    }

    private val fieldPaint = Paint().apply {
        color = Color.rgb(232, 245, 233)
    }

    private val goldGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 193, 7)
    }

    private val drawables by lazy {
        listOf(R.drawable.common, R.drawable.fast, R.drawable.rare, R.drawable.ic_gold_bug)
            .associateWith { resource ->
                requireNotNull(
                    AppCompatResources.getDrawable(context, resource)
                ).mutate()
            }
    }

    init {
        contentDescription = context.getString(R.string.lab4_field_description)
        isClickable = true
    }

    fun configure(settings: GameSettings) {
        maxBugs = settings.maxTarakani.coerceIn(1, 20)
        speedFactor = 0.4f + settings.gameSpeed.coerceIn(1, 100) / 50f
        while (bugs.size > maxBugs) {
            bugs.removeAt(bugs.lastIndex)
        }
        invalidate()
    }

    fun prepareField() {
        bugs.clear()
        bonus = null
        bonusElapsedSec = 0f
        goldElapsedSec = 0f
        tiltRemainingSec = 0f
        setTilt(0f, 0f)

        repeat(minOf(3, maxBugs)) { spawnBug() }
        invalidate()
    }

    fun setInputEnabled(enabled: Boolean) {
        inputEnabled = enabled
        if (!enabled) setTilt(0f, 0f)
    }


    fun setTilt(x: Float, y: Float) {
        tiltX = if (x.isFinite()) x.coerceIn(-1f, 1f) else 0f
        tiltY = if (y.isFinite()) y.coerceIn(-1f, 1f) else 0f
    }

    fun setGoldRate(rate: GoldRate?) {
        goldPoints = rate?.let(GoldBugRules::pointsFor)
        bugs.indices.forEach { index ->
            val bug = bugs[index]
            if (bug.type == BugType.GOLD) {
                bugs[index] = bug.copy(points = goldPoints ?: 0)
            }
        }
        invalidate()
    }

    fun tick(deltaSeconds: Float) {
        if (!deltaSeconds.isFinite() || deltaSeconds <= 0f) return
//обновление состояния игры:
        bonusElapsedSec += deltaSeconds
        if (bonusElapsedSec >= TiltBonus.SPAWN_INTERVAL_SEC) {
            bonusElapsedSec %= TiltBonus.SPAWN_INTERVAL_SEC
            spawnBonus()
        }

        goldElapsedSec += deltaSeconds
        if (goldElapsedSec >= GoldBugRules.SPAWN_INTERVAL_SEC) {
            goldElapsedSec %= GoldBugRules.SPAWN_INTERVAL_SEC
            spawnGoldBug()
        }

        val step = deltaSeconds.coerceAtMost(0.05f)
        val tiltStep = minOf(step, tiltRemainingSec)
        tiltRemainingSec = (tiltRemainingSec - deltaSeconds).coerceAtLeast(0f)

        bugs.forEach { bug ->
            if (Random.nextFloat() < step * 2.5f) {
                val angle = atan2(bug.vy, bug.vx) +
                    (Random.nextFloat() - 0.5f) * 1.05f
                val speed = bug.speed
                bug.vx = cos(angle) * speed
                bug.vy = sin(angle) * speed
            }

            // Positions and velocities use logical field units, not pixels.
            // Tilt adds drift without permanently changing the bug's own speed.
            bug.x += bug.vx * step + tiltX * TILT_DRIFT_SPEED * tiltStep
            bug.y += bug.vy * step + tiltY * TILT_DRIFT_SPEED * tiltStep

            val lower = bug.radius
            val right = fieldWidth - bug.radius
            val bottom = fieldHeight - bug.radius

            if (bug.x < lower) {
                bug.x = lower
                bug.vx = abs(bug.vx)
            } else if (bug.x > right) {
                bug.x = right
                bug.vx = -abs(bug.vx)
            }

            if (bug.y < lower) {
                bug.y = lower
                bug.vy = abs(bug.vy)
            } else if (bug.y > bottom) {
                bug.y = bottom
                bug.vy = -abs(bug.vy)
            }
        }

        if (bugs.size < maxBugs && Random.nextFloat() < step * 1.5f) {
            spawnBug()
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val scale = min(w, h) / Bug.FIELD_SIZE
        val newWidth = w / scale
        val newHeight = h / scale
        bugs.forEach { bug ->
            bug.x = (bug.x / fieldWidth * newWidth)
                .coerceIn(bug.radius, newWidth - bug.radius)
            bug.y = (bug.y / fieldHeight * newHeight)
                .coerceIn(bug.radius, newHeight - bug.radius)
        }
        bonus = bonus?.let { item ->
            item.copy(
                x = (item.x / fieldWidth * newWidth)
                    .coerceIn(item.radius, newWidth - item.radius),
                y = (item.y / fieldHeight * newHeight)
                    .coerceIn(item.radius, newHeight - item.radius)
            )
        }
        fieldWidth = newWidth
        fieldHeight = newHeight
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(245, 245, 245))

        val side = min(width, height).toFloat()
        if (side <= 0f) return

        val scale = side / Bug.FIELD_SIZE

        // Fill the view with a rectangular field while keeping sprites round.
        // Drawing and taps use the same uniform scale.
        canvas.save()
        canvas.scale(scale, scale)
        val effectElapsedSec = TiltBonus.EFFECT_DURATION_SEC - tiltRemainingSec
        val blinkOn = tiltRemainingSec > 0f &&
            (effectElapsedSec / BONUS_BLINK_INTERVAL_SEC).toInt() % 2 == 0
        fieldPaint.color = if (blinkOn) {
            Color.rgb(129, 212, 250)
        } else {
            Color.rgb(232, 245, 233)
        }
        canvas.drawRect(0f, 0f, fieldWidth, fieldHeight, fieldPaint)

        bugs.forEach { bug ->
            val drawable = drawables.getValue(bug.drawableRes)
            val radius = bug.radius.toInt()
            val angle = Math.toDegrees(atan2(bug.vy, bug.vx).toDouble())
                .toFloat() + 90f

            canvas.save()
            canvas.translate(bug.x, bug.y)
            canvas.rotate(angle)
            if (bug.type == BugType.GOLD) {
                canvas.drawCircle(0f, 0f, bug.radius + 10f, goldGlowPaint)
            }
            drawable.setBounds(-radius, -radius, radius, radius)
            drawable.draw(canvas)
            canvas.restore()
        }

        bonus?.let { item ->
            bonusDrawable.setBounds(
                (item.x - item.radius).toInt(),
                (item.y - item.radius).toInt(),
                (item.x + item.radius).toInt(),
                (item.y + item.radius).toInt()
            )
            bonusDrawable.draw(canvas)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {//нажатие на бонус
        if (!inputEnabled) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val side = min(width, height).toFloat()
                if (side <= 0f) return true

                val scale = side / Bug.FIELD_SIZE
                val touchX = event.x / scale
                val touchY = event.y / scale
                val currentBonus = bonus

                if (currentBonus != null && currentBonus.contains(touchX, touchY)) {
                    bonus = null
                    tiltRemainingSec = TiltBonus.EFFECT_DURATION_SEC
                    onBonusCollected?.invoke()
                } else {
                    val index = bugs.indexOfLast { it.contains(touchX, touchY) }
                    if (index >= 0) {
                        if (bugs[index].type == BugType.GOLD && goldPoints == null) {
                            onGoldUnavailable?.invoke()
                            return true
                        }
                        val bug = bugs.removeAt(index)
                        spawnBug()
                        onHit?.invoke(bug.points)
                    } else {
                        onMiss?.invoke()
                    }
                }
                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                performClick()
                parent?.requestDisallowInterceptTouchEvent(false)
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun spawnBonus() {
        val radius = 55f
        bonus = TiltBonus(
            x = radius + Random.nextFloat() * (fieldWidth - radius * 2),
            y = radius + Random.nextFloat() * (fieldHeight - radius * 2),
            radius = radius
        )
    }

    private fun spawnBug() {
        if (bugs.size >= maxBugs) return

        val type = when (Random.nextInt(100)) {
            in 0..69 -> BugType.NORMAL
            in 70..94 -> BugType.FAST
            else -> BugType.RARE
        }
        bugs += createBug(type)
    }

    private fun spawnGoldBug() {
        // На поле один золотой жук; общий лимит насекомых сохраняется.
        bugs.removeAll { it.type == BugType.GOLD }
        if (bugs.size >= maxBugs) bugs.removeAt(bugs.lastIndex)
        bugs += createBug(BugType.GOLD, goldPoints ?: 0)
    }

    private fun createBug(type: BugType, points: Int = type.points): Bug {
        val resource = when (type) {
            BugType.NORMAL -> R.drawable.common
            BugType.FAST -> R.drawable.fast
            BugType.RARE -> R.drawable.rare
            BugType.GOLD -> R.drawable.ic_gold_bug
        }
        val angle = Random.nextFloat() * (Math.PI * 2).toFloat()
        val speed = type.baseSpeed * speedFactor
        return Bug(
            type = type,
            x = type.radius + Random.nextFloat() * (fieldWidth - type.radius * 2),
            y = type.radius + Random.nextFloat() * (fieldHeight - type.radius * 2),
            vx = cos(angle) * speed,
            vy = sin(angle) * speed,
            drawableRes = resource,
            points = points
        )
    }

    companion object {
        private const val TILT_DRIFT_SPEED = 600f
        private const val BONUS_BLINK_INTERVAL_SEC = 0.5f
    }
}
