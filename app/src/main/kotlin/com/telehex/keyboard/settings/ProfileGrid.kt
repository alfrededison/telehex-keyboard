package com.telehex.keyboard.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.telehex.keyboard.ime.KeyPage
import com.telehex.keyboard.ime.KeyboardView
import com.telehex.keyboard.ime.Mode
import com.telehex.keyboard.ime.Theme
import com.telehex.layout.Profile
import com.telehex.telex.TelexConfig

/**
 * Lưới chọn profile có hình xem trước, hai thẻ mỗi hàng như trang chính của app iOS: mỗi thẻ là bàn phím thu nhỏ của
 * profile đó (đúng bố cục, màu và ký tự vuốt xuống), tên và kiểu lưới; thẻ đang dùng có viền màu nhấn và dấu ✓.
 */
internal class ProfileGrid(context: Context) : LinearLayout(context) {
    init { orientation = VERTICAL }

    fun show(profiles: List<Profile>, active: String, theme: Theme, onPick: (Profile) -> Unit) {
        removeAllViews()
        val accent = SettingsUi.accentColor(context)
        for (pair in profiles.chunked(2)) {
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for ((i, profile) in pair.withIndex()) {
                row.addView(card(profile, profile.name == active, theme, accent, onPick),
                    LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { if (i == 0) marginEnd = dp(10) })
            }
            // Hàng lẻ: ô trống giữ thẻ cuối cùng bề rộng
            if (pair.size == 1) row.addView(View(context), LayoutParams(0, 0, 1f))
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
        }
    }

    private fun card(profile: Profile, on: Boolean, theme: Theme, accent: Int, onPick: (Profile) -> Unit) =
        LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(8))
            isClickable = true
            isFocusable = true
            isSelected = on
            contentDescription = "${profile.name}, ${profile.spec.title}" + if (on) ", đang dùng" else ""
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor((accent and 0x00FFFFFF) or ((if (on) 0x1C else 0x08) shl 24))
                setStroke(dp(if (on) 2 else 1), if (on) accent else (accent and 0x00FFFFFF) or (0x30 shl 24))
            }
            setOnClickListener { onPick(profile) }
            addView(ScaledPreview(context, PreviewKeyboardView(context).apply {
                this.theme = theme
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                keys = KeyPage.build(profile.letters, Mode.ABC, 1, TelexConfig(), profile.swipe, profile.symbols, "↵")
            }).apply {
                background = GradientDrawable().apply { cornerRadius = dp(9).toFloat(); setColor(theme.background) }
                clipToOutline = true
            })
            addView(TextView(context).apply {
                text = (if (on) "✓ " else "") + profile.name + if (profile.isBuiltIn) "" else " ✎"
                textSize = 15f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(if (on) accent else textColor(android.R.attr.textColorPrimary))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6); marginStart = dp(4) })
            addView(TextView(context).apply {
                text = profile.spec.title
                textSize = 12f
                setTextColor(textColor(android.R.attr.textColorSecondary))
                maxLines = 2
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(4) })
        }

    private fun textColor(attr: Int): Int {
        val a = context.obtainStyledAttributes(intArrayOf(attr))
        try { return a.getColor(0, 0xFF202020.toInt()) } finally { a.recycle() }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}

/** Bàn phím chỉ để xem: không nhận chạm (thẻ chứa nó mới là nút). */
internal class PreviewKeyboardView(context: Context) : KeyboardView(context) {
    // Hình xem trước ở trạng thái thường: chữ thường, ⇧ không bật (thấy đúng màu nhóm ⇧ / ⌫)
    init { shifted = false }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}

/**
 * Vẽ [child] thu nhỏ vừa bề rộng được cấp: đo nó ở bề rộng màn hình (cỡ phím và cỡ chữ thật) rồi co cả hình lại, nên hình
 * xem trước giống hệt bàn phím thật thay vì phím nhỏ mà chữ vẫn to.
 */
internal class ScaledPreview(context: Context, private val child: View) : ViewGroup(context) {
    private val reference = resources.displayMetrics.widthPixels.coerceAtMost(
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 420f, resources.displayMetrics).toInt())
    private var scale = 1f

    init { addView(child); setWillNotDraw(false) }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        child.measure(MeasureSpec.makeMeasureSpec(reference, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        scale = w.toFloat() / reference
        setMeasuredDimension(w, (child.measuredHeight * scale).toInt())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) = child.layout(0, 0, reference, child.measuredHeight)

    override fun dispatchDraw(canvas: Canvas) {
        canvas.save()
        canvas.scale(scale, scale)
        super.dispatchDraw(canvas)
        canvas.restore()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false
    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}
