package com.example.tourismmedia.ui.common;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/** Draws a scannable Code 128-B barcode and its human-readable voucher code. */
public class BarcodeView extends View {
    private static final int QUIET_ZONE_MODULES = 10;
    private final Paint bars = new Paint();
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String code = "";
    private boolean[] modules = new boolean[0];

    public BarcodeView(Context context) { this(context, null); }
    public BarcodeView(Context context, @Nullable AttributeSet attributes) {
        super(context, attributes);
        bars.setColor(Color.BLACK);
        bars.setAntiAlias(false);
        label.setColor(Color.rgb(23,51,43));
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.MONOSPACE);
        label.setTextSize(dp(15));
        setBackgroundColor(Color.WHITE);
    }

    public void setCode(String code) {
        this.code = code == null ? "" : code.trim();
        try { modules = Code128.modules(this.code); }
        catch (IllegalArgumentException ignored) { modules = new boolean[0]; }
        setContentDescription("Mã vạch voucher " + this.code);
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(resolveSize(dp(320),widthMeasureSpec),resolveSize(dp(132),heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (modules.length == 0) return;
        float labelSpace = dp(30);
        float barHeight = Math.max(0,getHeight() - getPaddingTop() - getPaddingBottom() - labelSpace);
        int totalModules = modules.length + QUIET_ZONE_MODULES * 2;
        float moduleWidth = (getWidth() - getPaddingLeft() - getPaddingRight()) / (float) totalModules;
        float startX = getPaddingLeft() + QUIET_ZONE_MODULES * moduleWidth;
        for (int index = 0; index < modules.length; index++) {
            if (modules[index]) {
                float left = startX + index * moduleWidth;
                canvas.drawRect(left,getPaddingTop(),left + moduleWidth, getPaddingTop() + barHeight,bars);
            }
        }
        canvas.drawText(code,getWidth() / 2f,getHeight() - getPaddingBottom() - dp(6),label);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }
}
