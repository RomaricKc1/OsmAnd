package net.osmand.plus.views;

import android.content.Context;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewStub;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.core.android.AtlasMapRendererView;
import net.osmand.core.android.MapRendererContext;
import net.osmand.core.android.MapRendererView;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.auto.NavigationSession;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.inapp.InAppPurchaseUtils;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.plus.views.OsmandMap.OsmandMapListener;
import net.osmand.plus.views.corenative.NativeCoreContext;

public class MapViewWithLayers extends FrameLayout {

	private static final int SYMBOLS_UPDATE_INTERVAL = 2000;

	private final OsmandApplication app;
	private final OsmandSettings settings;
	private final OsmandMapTileView mapView;

	private OsmandMapListener mapListener;
	private AtlasMapRendererView atlasMapRendererView;

	public MapViewWithLayers(@NonNull Context context) {
		this(context, null);
	}

	public MapViewWithLayers(@NonNull Context context, @Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public MapViewWithLayers(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public MapViewWithLayers(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);

		app = getMyApplication();
		settings = app.getSettings();

		OsmandMap osmandMap = app.getOsmandMap();
		osmandMap.addListener(getMapListener());

		mapView = osmandMap.getMapView();
		mapView.setupTouchDetectors(getContext());

		boolean nightMode = app.getDaynightHelper().isNightMode();
		inflate(UiUtilities.getThemedContext(context, nightMode), R.layout.map_view_with_layers, this);
	}

	public void setupRenderingView() {
		NavigationSession carNavigationSession = app.getCarNavigationSession();
		View androidAutoPlaceholder = findViewById(R.id.AndroidAutoPlaceholder);
		boolean useAndroidAuto = carNavigationSession != null && carNavigationSession.hasStarted()
				&& InAppPurchaseUtils.isAndroidAutoAvailable(app);

		OsmAndMapSurfaceView surfaceView = findViewById(R.id.MapView);
		OsmAndMapLayersView mapLayersView = findViewById(R.id.MapLayersView);

		boolean useOpenglRender = app.useOpenGlRenderer();
		surfaceView.setMapView(!useOpenglRender && !useAndroidAuto ? mapView : null);
		if (useOpenglRender && !useAndroidAuto) {
			setupAtlasMapRendererView();
			mapLayersView.setMapView(mapView);
			app.getMapViewTrackingUtilities().setMapView(mapView);
			mapView.setMapRenderer(atlasMapRendererView);
		} else if (!useAndroidAuto) {
			mapView.setMapRenderer(null);
			resetMapRendererView();
		}
		AndroidUiHelper.updateVisibility(surfaceView, !useAndroidAuto && !useOpenglRender);
		AndroidUiHelper.updateVisibility(mapLayersView, !useAndroidAuto && useOpenglRender);
		AndroidUiHelper.updateVisibility(atlasMapRendererView, !useAndroidAuto && useOpenglRender);
		AndroidUiHelper.updateVisibility(androidAutoPlaceholder, useAndroidAuto);
	}

	private void resetMapRendererView() {
		MapRendererContext mapRendererContext = NativeCoreContext.getMapRendererContext();
		if (mapRendererContext != null && atlasMapRendererView != null && mapRendererContext.getMapRendererView() == atlasMapRendererView)
			mapRendererContext.setMapRendererView(null);
	}

	private void setupAtlasMapRendererView() {
		ViewStub stub = findViewById(R.id.atlasMapRendererViewStub);
		MapRendererView mapRendererView = null;
		MapRendererContext mapRendererContext = NativeCoreContext.getMapRendererContext();
		if (mapRendererContext != null) {
			if (atlasMapRendererView != null && mapRendererContext.getMapRendererView() == atlasMapRendererView)
				return;
			if (mapView.getMapRenderer() != null)
				mapView.setMapRenderer(null);
			if (mapRendererContext.getMapRendererView() != null) {
				mapRendererView = mapRendererContext.getMapRendererView();
				mapRendererContext.setMapRendererView(null);
			}
		}
		WindowManager mgr = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
		DisplayMetrics dm = new DisplayMetrics();
		mgr.getDefaultDisplay().getMetrics(dm);
		NativeCoreContext.setMapRendererContext(app, dm.density);
		mapRendererContext = NativeCoreContext.getMapRendererContext();
		if (mapRendererContext != null) {
			if (atlasMapRendererView == null)
                atlasMapRendererView = (AtlasMapRendererView) stub.inflate();
			else
				atlasMapRendererView.handleOnCreate(null);
			atlasMapRendererView.setupRenderer(getContext(), 0, 0, mapRendererView);
			atlasMapRendererView.setAzimuth(0);
			float elevationAngle = mapView.normalizeElevationAngle(settings.getLastKnownMapElevation());
			atlasMapRendererView.setElevationAngle(elevationAngle);
			atlasMapRendererView.setSymbolsUpdateInterval(SYMBOLS_UPDATE_INTERVAL);
			mapRendererContext.setMapRendererView(atlasMapRendererView);
		}
	}

	public void onCreate(Bundle savedInstanceState) {
		if (atlasMapRendererView != null) {
			atlasMapRendererView.handleOnCreate(savedInstanceState);
		}
	}

	public void onResume() {
		if (atlasMapRendererView != null) {
			atlasMapRendererView.handleOnResume();
		}
	}

	public void onPause() {
		if (atlasMapRendererView != null) {
			atlasMapRendererView.handleOnPause();
		}
	}

	public void onDestroy() {
		if (atlasMapRendererView != null) {
			NavigationSession carNavigationSession = app.getCarNavigationSession();
			if (carNavigationSession == null || !carNavigationSession.hasStarted()) {
				mapView.setMapRenderer(null);
				resetMapRendererView();
			}
			atlasMapRendererView.handleOnDestroy();
		}
		mapView.clearTouchDetectors();
		app.getOsmandMap().removeListener(getMapListener());
	}

	@NonNull
	private OsmandApplication getMyApplication() {
		return ((OsmandApplication) getContext().getApplicationContext());
	}

	@NonNull
	private OsmandMapListener getMapListener() {
		if (mapListener == null) {
			mapListener = new OsmandMapListener() {

				@Override
				public void onChangeZoom(int stp) {
					mapView.showAndHideMapPosition();
				}

				@Override
				public void onSetMapElevation(float angle) {
					MapRendererView mapRenderer = mapView.getMapRenderer();
					if (mapRenderer != null) {
						mapRenderer.setElevationAngle(angle);
					}
				}

				@Override
				public void onSetupRenderingView() {
					setupRenderingView();
				}
			};
		}
		return mapListener;
	}
}
