package com.example.kailashmasale;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * A circular ring progress bar. Draws a background ring and a progress arc in light blue.
 * Progress 0-100 fills the ring clockwise from the top.
 */
public class CircularProgressView extends View {

    private static final int DEFAULT_SIZE_DP = 100;
    private static final int TRACK_COLOR = 0xFFE8F0F8;
    private static final int PROGRESS_COLOR = 0xFFBBD6FC;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private int progress = 0; // 0-100
    private float strokeWidthPx;

    public CircularProgressView(Context context) {
        super(context);
        init();
    }

    public CircularProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircularProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(TRACK_COLOR);

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(PROGRESS_COLOR);

        float density = getResources().getDisplayMetrics().density;
        strokeWidthPx = 10f * density;
        trackPaint.setStrokeWidth(strokeWidthPx);
        progressPaint.setStrokeWidth(strokeWidthPx);
    }

    /**
     * Set progress 0-100. The ring fills clockwise from the top.
     */
    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(100, progress));
        invalidate();
    }

    public int getProgress() {
        return progress;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int sizePx = (int) (DEFAULT_SIZE_DP * density);
        int size = resolveSize(sizePx, widthMeasureSpec);
        size = resolveSize(size, heightMeasureSpec);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float inset = strokeWidthPx / 2f + 2;
        arcRect.set(inset, inset, w - inset, h - inset);

        // Background ring (full circle)
        canvas.drawArc(arcRect, 0, 360, false, trackPaint);

        // Progress arc: start at top (-90°) and sweep clockwise
        if (progress > 0) {
            float sweep = 360f * progress / 100f;
            canvas.drawArc(arcRect, -90, sweep, false, progressPaint);
        }
    }
}
