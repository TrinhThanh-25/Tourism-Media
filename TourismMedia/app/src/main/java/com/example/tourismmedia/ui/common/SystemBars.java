package com.example.tourismmedia.ui.common;

import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * The app draws edge to edge from Android 15 onwards, so screens have to keep their
 * own content clear of the status bar. New shared helper - no existing file changes.
 */
public final class SystemBars {

    private SystemBars() {
    }

    /** Adds the status bar height on top of whatever padding the view already declares. */
    public static void padTop(View view) {
        int basePadding = view.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            target.setPadding(target.getPaddingLeft(), basePadding + bars.top,
                    target.getPaddingRight(), target.getPaddingBottom());
            return windowInsets;
        });
    }

    /** Same idea for floating controls that sit on top of a full-bleed image. */
    public static void marginTop(View view) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        int baseMargin = params.topMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (target, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams updated = (ViewGroup.MarginLayoutParams) target.getLayoutParams();
            updated.topMargin = baseMargin + bars.top;
            target.setLayoutParams(updated);
            return windowInsets;
        });
    }
}
