package com.example.slagalica.ui.region;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.RegionPlayerMarker;
import com.example.slagalica.model.SerbiaRegion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SerbiaRegionMapView extends View {

    public interface RegionClickListener {
        void onRegionClicked(@NonNull SerbiaRegion region);
    }

    private final Paint regionFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint regionStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint currentMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Map<SerbiaRegion, Path> regionPaths = new HashMap<>();
    private final List<RegionPlayerMarker> markers = new ArrayList<>();

    private RegionClickListener regionClickListener;
    private SerbiaRegion selectedRegion;
    private float width;
    private float height;

    public SerbiaRegionMapView(Context context) {
        super(context);
        init();
    }

    public SerbiaRegionMapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SerbiaRegionMapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        regionStrokePaint.setStyle(Paint.Style.STROKE);
        regionStrokePaint.setStrokeWidth(4f);
        regionStrokePaint.setColor(Color.DKGRAY);

        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setColor(Color.RED);

        currentMarkerPaint.setStyle(Paint.Style.FILL);
        currentMarkerPaint.setColor(Color.parseColor("#1565C0"));

        labelPaint.setColor(Color.BLACK);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(28f);
    }

    public void setRegionClickListener(@Nullable RegionClickListener listener) {
        this.regionClickListener = listener;
    }

    public void setSelectedRegion(@Nullable SerbiaRegion region) {
        this.selectedRegion = region;
        invalidate();
    }

    public void setMarkers(@NonNull List<RegionPlayerMarker> playerMarkers) {
        markers.clear();
        markers.addAll(playerMarkers);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
        rebuildPaths();
    }

    private void rebuildPaths() {
        regionPaths.clear();
        if (width <= 0f || height <= 0f) {
            return;
        }
        for (SerbiaRegion region : SerbiaRegion.all()) {
            regionPaths.put(region, region.buildPath(width, height));
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (width <= 0f || height <= 0f) {
            return;
        }

        for (SerbiaRegion region : SerbiaRegion.all()) {
            Path path = regionPaths.get(region);
            if (path == null) {
                continue;
            }
            int color = region.getFillColor();
            if (region == selectedRegion) {
                color = Color.argb(255,
                        Math.min(255, Color.red(color) + 30),
                        Math.min(255, Color.green(color) + 30),
                        Math.min(255, Color.blue(color) + 30));
            }
            regionFillPaint.setColor(color);
            regionFillPaint.setAlpha(region == selectedRegion ? 235 : 190);
            canvas.drawPath(path, regionFillPaint);
            canvas.drawPath(path, regionStrokePaint);

            RectF bounds = region.getBounds();
            float centerX = ((bounds.left + bounds.right) / 2f) * width;
            float centerY = ((bounds.top + bounds.bottom) / 2f) * height;
            canvas.drawText(region.getDisplayName(getContext()), centerX, centerY, labelPaint);
        }

        float markerRadius = 7f;
        for (RegionPlayerMarker marker : markers) {
            Paint paint = marker.isCurrentUser() ? currentMarkerPaint : markerPaint;
            float x = marker.getMapPointX() * width;
            float y = marker.getMapPointY() * height;
            canvas.drawCircle(x, y, markerRadius, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP || regionClickListener == null) {
            return true;
        }
        float normalizedX = event.getX() / width;
        float normalizedY = event.getY() / height;
        for (SerbiaRegion region : SerbiaRegion.all()) {
            if (region.containsNormalizedPoint(normalizedX, normalizedY)) {
                selectedRegion = region;
                invalidate();
                regionClickListener.onRegionClicked(region);
                return true;
            }
        }
        return true;
    }

    @NonNull
    public PointF mapPointToView(float mapPointX, float mapPointY) {
        return new PointF(mapPointX * width, mapPointY * height);
    }
}
