package com.example.juki.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import com.example.juki.R
import kotlin.math.atan2
import kotlin.math.min

/** Рисует снимок раунда и передаёт действия пользователя владельцу состояния. */
class BugsGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var onFieldTap: ((Float, Float) -> Unit)? = null
    var onFieldSizeChanged: ((Float, Float) -> Unit)? = null
    private var state: GameState? = null
    private var inputEnabled = false
    private val fieldPaint = Paint()
    private val goldGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 193, 7)
    }
    private val bonusDrawable by lazy {
        requireNotNull(AppCompatResources.getDrawable(context, R.drawable.ic_tilt_bonus)).mutate()
    }
    private val drawables by lazy {
        listOf(R.drawable.common, R.drawable.fast, R.drawable.rare, R.drawable.ic_gold_bug)
            .associateWith { requireNotNull(AppCompatResources.getDrawable(context, it)).mutate() }
    }

    init {
        contentDescription = context.getString(R.string.lab4_field_description)
        isClickable = true
    }

    fun render(game: GameState) {
        if (state == game) return
        state = game
        invalidate()
    }

    fun setInputEnabled(enabled: Boolean) {
        inputEnabled = enabled
        if (!enabled) parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val scale = min(w, h) / Bug.FIELD_SIZE
        onFieldSizeChanged?.invoke(w / scale, h / scale)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(245, 245, 245))
        val game = state ?: return
        val side = min(width, height).toFloat()
        if (side <= 0f) return
        canvas.save()
        canvas.scale(side / Bug.FIELD_SIZE, side / Bug.FIELD_SIZE)
        val effectElapsed = TiltBonus.EFFECT_DURATION_SEC - game.tiltRemainingSec
        val blinkOn = game.tiltRemainingSec > 0f &&
            (effectElapsed / BONUS_BLINK_INTERVAL_SEC).toInt() % 2 == 0
        fieldPaint.color = if (blinkOn) Color.rgb(129, 212, 250) else Color.rgb(232, 245, 233)
        canvas.drawRect(0f, 0f, game.fieldWidth, game.fieldHeight, fieldPaint)
        game.bugs.forEach { bug ->
            val drawable = drawables.getValue(bug.drawableRes)
            val radius = bug.radius.toInt()
            val angle = Math.toDegrees(atan2(bug.vy, bug.vx).toDouble()).toFloat() + 90f
            canvas.save()
            canvas.translate(bug.x, bug.y)
            canvas.rotate(angle)
            if (bug.type == BugType.GOLD) canvas.drawCircle(0f, 0f, bug.radius + 10f, goldGlowPaint)
            drawable.setBounds(-radius, -radius, radius, radius)
            drawable.draw(canvas)
            canvas.restore()
        }
        game.bonus?.let { bonus ->
            bonusDrawable.setBounds(
                (bonus.x - bonus.radius).toInt(), (bonus.y - bonus.radius).toInt(),
                (bonus.x + bonus.radius).toInt(), (bonus.y + bonus.radius).toInt()
            )
            bonusDrawable.draw(canvas)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!inputEnabled) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val side = min(width, height).toFloat()
                if (side <= 0f) return true
                parent?.requestDisallowInterceptTouchEvent(true)
                val scale = side / Bug.FIELD_SIZE
                onFieldTap?.invoke(event.x / scale, event.y / scale)
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    companion object {
        private const val BONUS_BLINK_INTERVAL_SEC = 0.5f
    }
}
