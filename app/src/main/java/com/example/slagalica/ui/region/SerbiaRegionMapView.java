package com.example.slagalica.ui.region;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.RegionPlayerMarker;
import com.example.slagalica.model.SerbiaRegion;
import com.example.slagalica.utils.SerbiaMapProjection;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

public class SerbiaRegionMapView extends FrameLayout implements OnMapReadyCallback {

    public interface RegionClickListener {
        void onRegionClicked(@NonNull SerbiaRegion region);
    }

    private final List<Circle> markerObjects = new ArrayList<>();
    private final List<RegionPlayerMarker> pendingMarkers = new ArrayList<>();

    private MapView mapView;
    private GoogleMap googleMap;
    private RegionClickListener regionClickListener;
    private boolean mapReady;

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
        mapView = new MapView(getContext());
        addView(mapView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        mapView.getMapAsync(this);
    }

    public void onCreate(@Nullable Bundle savedInstanceState) {
        mapView.onCreate(savedInstanceState);
    }

    public void onResume() {
        mapView.onResume();
    }

    public void onPause() {
        mapView.onPause();
    }

    public void onDestroy() {
        mapView.onDestroy();
    }

    public void onLowMemory() {
        mapView.onLowMemory();
    }

    public void onSaveInstanceState(@NonNull Bundle outState) {
        mapView.onSaveInstanceState(outState);
    }

    public void setRegionClickListener(@Nullable RegionClickListener listener) {
        this.regionClickListener = listener;
    }

    public void setSelectedRegion(@Nullable SerbiaRegion region) {
        // No visual overlay on the map.
    }

    public void setMarkers(@NonNull List<RegionPlayerMarker> playerMarkers) {
        pendingMarkers.clear();
        pendingMarkers.addAll(playerMarkers);
        if (mapReady) {
            drawMarkers();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                getParent().requestDisallowInterceptTouchEvent(true);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        mapReady = true;

        googleMap.getUiSettings().setMapToolbarEnabled(false);
        googleMap.getUiSettings().setRotateGesturesEnabled(false);
        googleMap.getUiSettings().setTiltGesturesEnabled(false);
        googleMap.getUiSettings().setCompassEnabled(false);

        googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(
                SerbiaMapProjection.getSerbiaBounds(),
                48
        ));

        drawMarkers();

        googleMap.setOnMapClickListener(latLng -> {
            if (regionClickListener == null) {
                return;
            }
            SerbiaRegion region = SerbiaRegion.findAtLatLng(latLng);
            if (region != null) {
                regionClickListener.onRegionClicked(region);
            }
        });
    }

    private void drawMarkers() {
        if (googleMap == null) {
            return;
        }
        clearMarkers();
        for (RegionPlayerMarker marker : pendingMarkers) {
            LatLng position = SerbiaMapProjection.normalizedToLatLng(
                    marker.getMapPointX(),
                    marker.getMapPointY()
            );
            int color = marker.isCurrentUser()
                    ? Color.parseColor("#1565C0")
                    : Color.RED;
            CircleOptions options = new CircleOptions()
                    .center(position)
                    .radius(1200)
                    .fillColor(color)
                    .strokeColor(color)
                    .strokeWidth(1f)
                    .clickable(false);
            markerObjects.add(googleMap.addCircle(options));
        }
    }

    private void clearMarkers() {
        for (Circle marker : markerObjects) {
            marker.remove();
        }
        markerObjects.clear();
    }

    @NonNull
    public PointF mapPointToView(float mapPointX, float mapPointY) {
        return new PointF(mapPointX * getWidth(), mapPointY * getHeight());
    }
}
