package com.example.tripoo.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.content.res.Resources;

import androidx.annotation.ColorInt;

/**
 * Creates circular profile icon drawables with border for bottom nav.
 * Selected state: #F48C25 border; unselected: gray border.
 */
public final class ProfileIconDrawable {

    private static final int ICON_SIZE_PX = 96; // 48dp at 2x density
    private static final float BORDER_WIDTH_PX = 3f;
    private static final int UNSELECTED_BORDER_COLOR = 0xFF9CA3AF; // gray

    /**
     * Create a StateListDrawable for the profile nav item: circle image with
     * orange border when selected, gray border when unselected.
     */
    public static StateListDrawable createProfileIconSelector(
            Resources resources,
            Bitmap profileBitmap,
            @ColorInt int selectedBorderColor) {
        if (profileBitmap == null) {
            return null;
        }
        Bitmap scaled = Bitmap.createScaledBitmap(profileBitmap, ICON_SIZE_PX, ICON_SIZE_PX, true);
        Drawable selected = createCircularDrawable(resources, scaled, selectedBorderColor);
        Drawable unselected = createCircularDrawable(resources, scaled, UNSELECTED_BORDER_COLOR);
        StateListDrawable selector = new StateListDrawable();
        selector.addState(new int[]{android.R.attr.state_checked}, selected);
        selector.addState(new int[]{}, unselected);
        return selector;
    }

    /**
     * Circular drawable with a colored border. Uses clipPath so the image is visible inside the circle.
     */
    private static Drawable createCircularDrawable(Resources resources, Bitmap bitmap, @ColorInt int borderColor) {
        int size = ICON_SIZE_PX;
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Rect rect = new Rect(0, 0, size, size);
        RectF rectF = new RectF(rect);

        // Circular path for clipping
        Path circlePath = new Path();
        circlePath.addOval(rectF, Path.Direction.CW);

        // Draw bitmap clipped to circle
        canvas.save();
        canvas.clipPath(circlePath);
        Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(bitmap, null, rect, imagePaint);
        canvas.restore();

        // Draw border circle on top
        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(borderColor);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(BORDER_WIDTH_PX);
        borderPaint.setDither(true);
        float halfStroke = BORDER_WIDTH_PX / 2f;
        RectF borderRect = new RectF(halfStroke, halfStroke, size - halfStroke, size - halfStroke);
        canvas.drawOval(borderRect, borderPaint);

        return new BitmapDrawable(resources, output);
    }
}
