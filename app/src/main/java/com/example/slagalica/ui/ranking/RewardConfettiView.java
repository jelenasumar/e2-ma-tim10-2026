package com.example.slagalica.ui.ranking;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

public class RewardConfettiView extends View {

    private static final int PIECE_COUNT = 70;
    private static final long DURATION_MS = 2800L;
    private static final int[] COLORS = {
            0xFFFFC107,
            0xFF7E57C2,
            0xFF26A69A,
            0xFFEF5350,
            0xFF42A5F5
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random(17L);
    private final float[] xRatios = new float[PIECE_COUNT];
    private final float[] yStarts = new float[PIECE_COUNT];
    private final float[] speeds = new float[PIECE_COUNT];
    private final float[] sizes = new float[PIECE_COUNT];
    private final float[] rotations = new float[PIECE_COUNT];
    private final int[] colors = new int[PIECE_COUNT];
    private final RectF piece = new RectF();

    private long startedAt;
    private boolean running;

    public RewardConfettiView(Context context) {
        super(context);
        initPieces();
    }

    public RewardConfettiView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPieces();
    }

    public RewardConfettiView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPieces();
    }

    public void start() {
        startedAt = SystemClock.uptimeMillis();
        running = true;
        setVisibility(VISIBLE);
        invalidate();
    }

    private void initPieces() {
        setWillNotDraw(false);
        for (int i = 0; i < PIECE_COUNT; i++) {
            xRatios[i] = random.nextFloat();
            yStarts[i] = -random.nextInt(180) - 10;
            speeds[i] = 120f + random.nextFloat() * 220f;
            sizes[i] = 9f + random.nextFloat() * 11f;
            rotations[i] = random.nextFloat() * 360f;
            colors[i] = COLORS[random.nextInt(COLORS.length)];
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

        for (int i = 0; i < PIECE_COUNT; i++) {
            float size = sizes[i];
            float wave = (float) Math.sin((elapsedSeconds * 4f) + i) * 18f;
            float x = xRatios[i] * width + wave;
            float y = yStarts[i] + speeds[i] * elapsedSeconds;

            paint.setColor(colors[i]);
            canvas.save();
            canvas.rotate(rotations[i] + elapsedSeconds * 180f, x, y);
            piece.set(x, y, x + size, y + size * 0.55f);
            canvas.drawRoundRect(piece, 2f, 2f, paint);
            canvas.restore();
        }

        if (elapsedMs < DURATION_MS) {
            postInvalidateOnAnimation();
        } else {
            running = false;
            setVisibility(INVISIBLE);
        }
    }
}
