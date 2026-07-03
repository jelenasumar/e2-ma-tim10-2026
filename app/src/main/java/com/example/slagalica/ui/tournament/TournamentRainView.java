package com.example.slagalica.ui.tournament;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

public class TournamentRainView extends View {

    private static final int DROP_COUNT = 55;
    private static final long DURATION_MS = 2200L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(23L);
    private final float[] xRatios = new float[DROP_COUNT];
    private final float[] yStarts = new float[DROP_COUNT];
    private final float[] speeds = new float[DROP_COUNT];
    private final float[] lengths = new float[DROP_COUNT];

    private long startedAt;
    private boolean running;

    public TournamentRainView(Context context) {
        super(context);
        initDrops();
    }

    public TournamentRainView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initDrops();
    }

    public TournamentRainView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initDrops();
    }

    public void start() {
        startedAt = SystemClock.uptimeMillis();
        running = true;
        setVisibility(VISIBLE);
        invalidate();
    }

    private void initDrops() {
        setWillNotDraw(false);
        paint.setStrokeWidth(5f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xAA42A5F5);
        for (int i = 0; i < DROP_COUNT; i++) {
            xRatios[i] = random.nextFloat();
            yStarts[i] = -random.nextInt(260) - 20;
            speeds[i] = 260f + random.nextFloat() * 260f;
            lengths[i] = 16f + random.nextFloat() * 18f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!running) {
            return;
        }

        long elapsedMs = SystemClock.uptimeMillis() - startedAt;
        float elapsedSeconds = elapsedMs / 1000f;
        int width = getWidth();
        int height = Math.max(1, getHeight());

        for (int i = 0; i < DROP_COUNT; i++) {
            float x = xRatios[i] * width;
            float y = (yStarts[i] + speeds[i] * elapsedSeconds) % (height + 120);
            canvas.drawLine(x, y, x - 8f, y + lengths[i], paint);
        }

        if (elapsedMs < DURATION_MS) {
            postInvalidateOnAnimation();
        } else {
            running = false;
            setVisibility(INVISIBLE);
        }
    }
}
