package net.osmand.plus.plugins.weather.dialogs;

import static com.google.android.material.slider.LabelFormatter.LABEL_FLOATING;
import static net.osmand.plus.routepreparationmenu.ChooseRouteFragment.BACK_TO_LOC_BUTTON_ID;
import static net.osmand.plus.routepreparationmenu.ChooseRouteFragment.ZOOM_IN_BUTTON_ID;
import static net.osmand.plus.routepreparationmenu.ChooseRouteFragment.ZOOM_OUT_BUTTON_ID;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.slider.LabelFormatter;

import net.osmand.PlatformUtil;
import net.osmand.core.jni.WeatherLayer;
import net.osmand.core.jni.WeatherTileResourcesManager;
import net.osmand.core.jni.WeatherType;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.base.BaseOsmAndFragment;
import net.osmand.plus.helpers.AndroidUiHelper;
import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.plugins.weather.WeatherBand;
import net.osmand.plus.plugins.weather.WeatherContour;
import net.osmand.plus.plugins.weather.WeatherHelper;
import net.osmand.plus.plugins.weather.WeatherPlugin;
import net.osmand.plus.plugins.weather.WeatherRasterLayer;
import net.osmand.plus.plugins.weather.WeatherUtils;
import net.osmand.plus.plugins.weather.widgets.WeatherWidgetsPanel;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.ColorUtilities;
import net.osmand.plus.utils.OsmAndFormatter;
import net.osmand.plus.utils.OsmAndFormatter.TimeFormatter;
import net.osmand.plus.utils.UiUtilities;
import net.osmand.plus.views.MapLayers;
import net.osmand.plus.views.OsmandMapTileView;
import net.osmand.plus.views.controls.maphudbuttons.MyLocationButton;
import net.osmand.plus.views.controls.maphudbuttons.ZoomInButton;
import net.osmand.plus.views.controls.maphudbuttons.ZoomOutButton;
import net.osmand.plus.views.layers.MapControlsLayer;
import net.osmand.plus.views.layers.MapInfoLayer;
import net.osmand.plus.views.mapwidgets.widgets.RulerWidget;
import net.osmand.plus.widgets.TextViewEx;
import net.osmand.plus.widgets.chips.ChipItem;
import net.osmand.plus.widgets.chips.HorizontalChipsView;
import net.osmand.plus.widgets.popup.PopUpMenu;
import net.osmand.plus.widgets.popup.PopUpMenuDisplayData;
import net.osmand.plus.widgets.popup.PopUpMenuItem;
import net.osmand.plus.widgets.popup.PopUpMenuWidthMode;

import org.apache.commons.logging.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

public class WeatherForecastFragment extends BaseOsmAndFragment {

	public static final String TAG = WeatherForecastFragment.class.getSimpleName();
	private final Log log = PlatformUtil.getLog(WeatherForecastFragment.class);

	private static final String PREVIOUS_WEATHER_CONTOUR_KEY = "previous_weather_contour";
	private static final long MIN_UTC_HOURS_OFFSET = 24 * 60 * 60 * 1000;
	public static final int ANIM_DELAY_MILLIS = 70;
	public static final int WAIT_FOR_NEW_DOWNLOAD_START_DELAY = 1000;
	private static final int MAX_FORECAST_DAYS = 7;

	private WeatherHelper weatherHelper;
	private WeatherPlugin plugin;

	private TimeSlider timeSlider;
	private RulerWidget rulerWidget;
	private WeatherWidgetsPanel widgetsPanel;
	private Handler progressUpdateHandler;
	private Handler animateForecastHandler;

	private final Calendar currentDate = getDefaultCalendar();
	private final Calendar selectedDate = getDefaultCalendar();
	private final Calendar utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

	private final TimeFormatter timeFormatter = new TimeFormatter(Locale.getDefault(), "HH:mm", "h:mm a");

	private WeatherContour previousWeatherContour;
	private boolean isAnimatingForecast;
	private ImageView playForecastBtnIcon;
	private int currentStep;
	private int animateStepCount;

	private ImageButton chooseLayersBtn;
	private ImageButton chooseContoursBtn;

	@Override
	public int getStatusBarColorId() {
		AndroidUiHelper.setStatusBarContentColor(getView(), nightMode);
		return ColorUtilities.getStatusBarSecondaryColorId(nightMode);
	}

	public boolean getContentStatusBarNightMode() {
		return nightMode;
	}

	@Override
	protected boolean isUsedOnMap() {
		return false;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		progressUpdateHandler = new Handler(Objects.requireNonNull(Looper.myLooper()));
		animateForecastHandler = new Handler(Objects.requireNonNull(Looper.myLooper()));
		weatherHelper = app.getWeatherHelper();
		weatherHelper.setDownloadStateListener(this::onDownloadStateChanged);
		plugin = PluginsHelper.getPlugin(WeatherPlugin.class);

		currentDate.setTimeInMillis(WeatherUtils.roundForecastTimeToHour(System.currentTimeMillis()));
		selectedDate.setTime(currentDate.getTime());

		if (savedInstanceState != null) {
			previousWeatherContour = WeatherContour.valueOf(savedInstanceState.getString(PREVIOUS_WEATHER_CONTOUR_KEY, WeatherContour.TEMPERATURE.name()));
		} else {
			previousWeatherContour = plugin.getSelectedContoursType();
			zoomOutToMaxLayersZoom();
		}
		plugin.setContoursType(plugin.getSelectedForecastContoursType());
	}

	private void showProgressBar(boolean show) {
		if (getView() != null) {
			View progressBar = getView().findViewById(R.id.load_forecast_progress);
			app.runInUIThread(() -> AndroidUiHelper.setVisibility(show ? View.VISIBLE : View.INVISIBLE, progressBar));
		}
	}

	private void zoomOutToMaxLayersZoom() {
		WeatherTileResourcesManager weatherResourcesManager = weatherHelper.getWeatherResourcesManager();
		if (weatherResourcesManager == null) {
			return;
		}

		int maxWeatherLayerZoom = -1;

		int maxContoursZoom = weatherResourcesManager.getMaxTileZoom(WeatherType.Contour, WeatherLayer.High).swigValue();
		int maxContoursOverZoom = weatherResourcesManager.getMaxMissingDataZoomShift(WeatherType.Contour, WeatherLayer.High);
		maxWeatherLayerZoom = Math.max(maxWeatherLayerZoom, maxContoursZoom + maxContoursOverZoom);

		int maxRasterZoom = weatherResourcesManager.getMaxTileZoom(WeatherType.Raster, WeatherLayer.High).swigValue();
		int maxRasterOverZoom = weatherResourcesManager.getMaxMissingDataZoomShift(WeatherType.Raster, WeatherLayer.High);
		maxWeatherLayerZoom = Math.max(maxWeatherLayerZoom, maxRasterZoom + maxRasterOverZoom);

		OsmandMapTileView mapView = app.getOsmandMap().getMapView();
		if (maxWeatherLayerZoom != -1 && maxWeatherLayerZoom < mapView.getZoom()) {
			mapView.setIntZoom(maxWeatherLayerZoom);
		}
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		updateNightMode();
		MapActivity activity = requireMapActivity();
		View view = themedInflater.inflate(R.layout.fragment_weather_forecast, container, false);
		AndroidUtils.addStatusBarPadding21v(activity, view);

		widgetsPanel = view.findViewById(R.id.weather_widgets_panel);
		widgetsPanel.setupWidgets(activity, nightMode);
		widgetsPanel.isNightMode = nightMode;

		setupPLayForecastButton(view);
		setupToolBar(view);
		setupWeatherButtons(view);
		setupDatesView(view);
		setupTimeSlider(view);
		buildZoomButtons(view);
		moveCompassButton(view);

		return view;
	}

	private void setupPLayForecastButton(View view) {
		View playForecastBtn = view.findViewById(R.id.play_forecast_button);
		playForecastBtnIcon = view.findViewById(R.id.play_forecast_button_icon);
		playForecastBtn.setOnClickListener((v) -> onPlayForecastClicked());
		updatePlayForecastButton();
	}

	private void updatePlayForecastButton() {
		int iconResId = isAnimatingForecast ? R.drawable.ic_pause : R.drawable.ic_play_dark;
		Drawable iconDrawable = app.getUIUtilities().getIcon(iconResId, ColorUtilities.getActiveIconColorId(nightMode));
		playForecastBtnIcon.setImageDrawable(iconDrawable);
	}

	private void onPlayForecastClicked() {
		isAnimatingForecast = !isAnimatingForecast;
		if (isAnimatingForecast) {
			Calendar calendar = getDefaultCalendar();
			calendar.setTime(selectedDate.getTime());
			int hour = (int) timeSlider.getValue();
			calendar.set(Calendar.HOUR_OF_DAY, hour);
			calendar.set(Calendar.MINUTE, (int) ((timeSlider.getValue() - (float) hour) * 60.0f));
			plugin.prepareForDayAnimation(calendar.getTime());
			requireMapActivity().refreshMap();
			currentStep = (int) (timeSlider.getValue() / timeSlider.getStepSize()) + 1;
			animateStepCount = (int) (WeatherRasterLayer.FORECAST_ANIMATION_DURATION_HOURS / timeSlider.getStepSize()) - 1;
			updateSliderValue();
			showProgressBar(true);
			scheduleAnimationStart();
		} else {
			animateForecastHandler.removeCallbacksAndMessages(null);
		}
		updatePlayForecastButton();
	}

	private void moveToNextForecastFrame() {
		animateForecastHandler.removeCallbacksAndMessages(null);
		if (currentStep + 1 > getStepsCount() || animateStepCount <= 0) {
			isAnimatingForecast = false;
			updatePlayForecastButton();
		} else {
			currentStep++;
			animateStepCount--;
			updateSliderValue();
			if (isAnimatingForecast) {
				animateForecastHandler.postDelayed(this::moveToNextForecastFrame, ANIM_DELAY_MILLIS);
			}
		}
	}

	private void updateSliderValue() {
		float newValue = timeSlider.getValueFrom() + currentStep * timeSlider.getStepSize();
		timeSlider.setValue(Math.min(newValue, timeSlider.getValueTo()));
	}

	private int getStepsCount() {
		if (timeSlider != null) {
			return (int) ((timeSlider.getValueTo() - timeSlider.getValueFrom()) / timeSlider.getStepSize());
		} else {
			return 0;
		}
	}

	@Override
	public void onStop() {
		super.onStop();
		isAnimatingForecast = false;
		animateForecastHandler.removeCallbacksAndMessages(null);
	}

	private void setupTimeSlider(@NonNull View view) {
		timeSlider = view.findViewById(R.id.time_slider);
		timeSlider.setValueTo(24);
		timeSlider.setValueFrom(0);
		timeSlider.setLabelFormatter(getLabelFormatter());

		Calendar calendar = getDefaultCalendar();
		timeSlider.addOnChangeListener((slider, value, fromUser) -> {
			calendar.setTime(selectedDate.getTime());
			int hour = (int) value;
			calendar.set(Calendar.HOUR_OF_DAY, hour);
			calendar.set(Calendar.MINUTE, (int) ((value - (float) hour) * 60.0f));

			updateSelectedDate(calendar.getTime(), fromUser);
		});
		UiUtilities.setupSlider(timeSlider, nightMode, ColorUtilities.getActiveColor(app, nightMode), true);
		timeSlider.setLabelBehavior(LABEL_FLOATING);
		timeSlider.setTrackActiveTintList(timeSlider.getTrackInactiveTintList());

		updateTimeSlider();
	}

	private LabelFormatter getLabelFormatter() {
		Calendar calendar = getDefaultCalendar();
		boolean twelveHoursFormat = !DateFormat.is24HourFormat(app);
		return value -> {
			calendar.set(Calendar.HOUR_OF_DAY, (int) value);
			return timeFormatter.format(calendar.getTime(), twelveHoursFormat);
		};
	}

	private void updateTimeSlider() {
		boolean today = OsmAndFormatter.isSameDay(selectedDate, currentDate);
		timeSlider.setValue(today ? currentDate.get(Calendar.HOUR_OF_DAY) : 9);
		timeSlider.setStepSize(today ? 1.0f / 12.0f : 3.0f / 9.0f); // today ? 10 minutes : 20 minutes
	}

	private void buildZoomButtons(@NonNull View view) {
		View zoomButtonsView = view.findViewById(R.id.map_hud_controls);

		MapActivity activity = requireMapActivity();
		MapLayers mapLayers = activity.getMapLayers();
		MapControlsLayer layer = mapLayers.getMapControlsLayer();

		ImageView zoomInBtn = view.findViewById(R.id.map_zoom_in_button);
		if (zoomInBtn != null) {
			layer.addMapButton(new ZoomInButton(activity, zoomInBtn, ZOOM_IN_BUTTON_ID));
		}
		ImageView zoomOutBtn = view.findViewById(R.id.map_zoom_out_button);
		if (zoomInBtn != null) {
			layer.addMapButton(new ZoomOutButton(activity, zoomOutBtn, ZOOM_OUT_BUTTON_ID));
		}
		ImageView myLocationBtn = view.findViewById(R.id.map_my_location_button);
		if (zoomInBtn != null) {
			layer.addMapButton(new MyLocationButton(activity, myLocationBtn, BACK_TO_LOC_BUTTON_ID, false));
		}

		AndroidUiHelper.updateVisibility(zoomButtonsView, true);

		MapInfoLayer mapInfoLayer = mapLayers.getMapInfoLayer();
		rulerWidget = mapInfoLayer.setupRulerWidget(view.findViewById(R.id.map_ruler_layout));
	}

	private void setupDatesView(@NonNull View view) {
		List<ChipItem> chips = createDatesChipItems(currentDate);
		HorizontalChipsView chipsView = view.findViewById(R.id.chips_view);
		chipsView.setItems(chips);
		chipsView.setOnSelectChipListener(chip -> {
			Date date = (Date) chip.tag;
			selectedDate.setTime(date);
			updateSelectedDate(date, true);
			updateTimeSlider();
			requireMapActivity().refreshMap();
			return true;
		});
		ChipItem selected = chipsView.findChipByTag(selectedDate.getTime());
		chipsView.setSelected(selected);
	}

	@NonNull
	private List<ChipItem> createDatesChipItems(@NonNull Calendar currentDate) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(currentDate.getTime());
		List<ChipItem> chipItems = new ArrayList<>();
		SimpleDateFormat formatter = new SimpleDateFormat("E", Locale.getDefault());
		for (int i = 0; i <= MAX_FORECAST_DAYS; i++) {
			String title;
			switch (i) {
				case 0:
					title = app.getString(R.string.today);
					break;
				case 1:
					title = app.getString(R.string.tomorrow);
					break;
				default:
					title = formatter.format(calendar.getTime());
					break;
			}
			ChipItem chip = new ChipItem(title);
			chip.title = title;
			chip.contentDescription = title;
			chip.tag = calendar.getTime();
			chipItems.add(chip);
			calendar.add(Calendar.DAY_OF_MONTH, 1);
		}
		return chipItems;
	}

	private void setupToolBar(@NonNull View view) {
		View toolBar = view.findViewById(R.id.toolbar);
		toolBar.setBackgroundColor(app.getColor(nightMode ? R.color.activity_background_color_dark : R.color.list_background_color_light));

		ImageView backButton = view.findViewById(R.id.close_button);
		backButton.setImageDrawable(getIcon(R.drawable.ic_arrow_back, ColorUtilities.getPrimaryIconColorId(nightMode)));
		backButton.setOnClickListener(v -> {
			MapActivity activity = getMapActivity();
			if (activity != null) {
				activity.onBackPressed();
			}
		});

		TextViewEx title = view.findViewById(R.id.toolbar_title);
		title.setText(R.string.shared_string_weather);
		title.setTextColor(app.getColor(ColorUtilities.getPrimaryTextColorId(nightMode)));

		ImageView optionButton = view.findViewById(R.id.action_button);
		optionButton.setImageDrawable(getIcon(R.drawable.ic_action_settings_outlined, ColorUtilities.getPrimaryIconColorId(nightMode)));
	}

	private void setupWeatherButtons(@NonNull View view) {
		chooseLayersBtn = view.findViewById(R.id.raster_layers_btn);
		chooseLayersBtn.setOnClickListener(this::chooseLayers);
		chooseContoursBtn = view.findViewById(R.id.contour_layers_btn);
		chooseContoursBtn.setOnClickListener(this::chooseContour);
		updateChooseLayersButton();
		updateChooseContoursButton();
	}

	public void updateSelectedDate(@Nullable Date date, boolean updatePeriod) {
		plugin.setForecastDate(date, updatePeriod);
		if (date != null)
			date.setTime(WeatherUtils.roundForecastTimeToHour(date.getTime()));
		checkDateOffset(date);
		widgetsPanel.setSelectedDate(date);
		requireMapActivity().refreshMap();
	}

	@Override
	protected void updateNightMode() {
		super.updateNightMode();
		if (widgetsPanel != null) {
			widgetsPanel.isNightMode = nightMode;
		}
	}

	private void checkDateOffset(@Nullable Date date) {
		if (date != null && (date.getTime() - currentDate.getTimeInMillis() >= MIN_UTC_HOURS_OFFSET)) {
			utcCalendar.setTime(date);
			int hours = utcCalendar.get(Calendar.HOUR_OF_DAY);
			int offset = hours % 3;
			if (offset == 2) {
				utcCalendar.set(Calendar.HOUR_OF_DAY, hours + 1);
			} else if (offset == 1) {
				utcCalendar.set(Calendar.HOUR_OF_DAY, hours - 1);
			}
			date.setTime(utcCalendar.getTimeInMillis());
		}
	}

	private void moveCompassButton(@NonNull View view) {
		int btnSizePx = getDimensionPixelSize(R.dimen.map_small_button_size);
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(btnSizePx, btnSizePx);
		int toolbarHeight = getDimensionPixelSize(R.dimen.toolbar_height);
		int topMargin = getDimensionPixelSize(R.dimen.map_small_button_margin);
		int startMargin = getDimensionPixelSize(R.dimen.map_button_margin);
		AndroidUtils.setMargins(params, startMargin, topMargin + toolbarHeight, 0, 0);

		MapActivity activity = getMapActivity();
		if (activity != null) {
			MapLayers mapLayers = activity.getMapLayers();
			MapControlsLayer mapControlsLayer = mapLayers.getMapControlsLayer();
			mapControlsLayer.moveCompassButton((ViewGroup) view, params);
		}
	}

	@Override
	public void onResume() {
		super.onResume();

		MapActivity mapActivity = requireMapActivity();
		mapActivity.disableDrawer();
		mapActivity.getMapLayers().getMapInfoLayer().addAdditionalWidgetsContainer(widgetsPanel);
		updateWidgetsVisibility(mapActivity, View.GONE);
		updateSelectedDate(selectedDate.getTime(), true);
	}

	@Override
	public void onPause() {
		super.onPause();

		MapActivity mapActivity = requireMapActivity();
		mapActivity.enableDrawer();
		mapActivity.getMapLayers().getMapInfoLayer().removeAdditionalWidgetsContainer(widgetsPanel);
		updateWidgetsVisibility(mapActivity, View.VISIBLE);
		updateSelectedDate(null, true);
	}

	private void updateWidgetsVisibility(@NonNull MapActivity activity, int visibility) {
		AndroidUiHelper.setVisibility(activity, visibility, R.id.map_left_widgets_panel,
				R.id.map_right_widgets_panel, R.id.map_center_info);
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		MapActivity mapActivity = getMapActivity();
		if (mapActivity != null) {
			MapLayers mapLayers = mapActivity.getMapLayers();

			MapControlsLayer layer = mapLayers.getMapControlsLayer();
			layer.removeMapButtons(Arrays.asList(ZOOM_IN_BUTTON_ID, ZOOM_OUT_BUTTON_ID, BACK_TO_LOC_BUTTON_ID));
			layer.restoreCompassButton();

			if (rulerWidget != null) {
				MapInfoLayer mapInfoLayer = mapLayers.getMapInfoLayer();
				mapInfoLayer.removeRulerWidgets(Collections.singletonList(rulerWidget));
			}
		}
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(PREVIOUS_WEATHER_CONTOUR_KEY, previousWeatherContour.name());
	}

	@Override
	public void onDestroy() {
		FragmentActivity activity = getActivity();
		if (activity != null && !activity.isChangingConfigurations()) {
			plugin.setSelectedContoursType(previousWeatherContour);
		}
		super.onDestroy();
	}

	private void chooseContour(@NonNull View view) {
		int activeColor = ColorUtilities.getActiveColor(app, nightMode);
		List<PopUpMenuItem> items = new ArrayList<>();
		items.add(new PopUpMenuItem.Builder(app)
				.setTitleId(R.string.shared_string_none)
				.setIcon(getContentIcon(R.drawable.ic_action_thermometer))
				.showCompoundBtn(activeColor)
				.setOnClickListener(v -> {
					plugin.setSelectedForecastContoursType(null);
					updateChooseContoursButton();
					requireMapActivity().refreshMap();
				})
				.setSelected(plugin.getSelectedForecastContoursType() == null)
				.create()
		);

		for (WeatherContour weatherContour : WeatherContour.values()) {
			items.add(new PopUpMenuItem.Builder(app)
					.setTitleId(weatherContour.getTitleId())
					.setIcon(getContentIcon(weatherContour.getIconId()))
					.showCompoundBtn(activeColor)
					.setOnClickListener(v -> {
						plugin.setSelectedForecastContoursType(weatherContour);
						updateChooseContoursButton();
						requireMapActivity().refreshMap();
					})
					.setSelected(weatherContour == plugin.getSelectedForecastContoursType())
					.create()
			);
		}

		PopUpMenuDisplayData displayData = new PopUpMenuDisplayData();
		displayData.anchorView = view;
		displayData.menuItems = items;
		displayData.nightMode = nightMode;
		displayData.widthMode = PopUpMenuWidthMode.STANDARD;
		PopUpMenu.show(displayData);
	}

	private void chooseLayers(@NonNull View view) {
		int activeColor = ColorUtilities.getActiveColor(app, nightMode);
		List<PopUpMenuItem> menuItems = new ArrayList<>();
		for (WeatherBand band : weatherHelper.getWeatherBands()) {
			boolean selected = band.isForecastBandVisible();
			Drawable icon = selected ? getIcon(band.getIconId(), ColorUtilities.getActiveColorId(nightMode)) : getContentIcon(band.getIconId());
			menuItems.add(new PopUpMenuItem.Builder(app)
					.setTitle(band.getMeasurementName())
					.setIcon(icon)
					.showCompoundBtn(activeColor)
					.setOnClickListener(v -> {
						boolean visible = !band.isForecastBandVisible();
						band.setForecastBandVisible(visible);
						requireMapActivity().refreshMap();
						updateChooseLayersButton();
					})
					.showTopDivider(band.getBandIndex() == WeatherBand.WEATHER_BAND_WIND_ANIMATION)
					.setSelected(selected)
					.create()
			);
		}
		PopUpMenuDisplayData displayData = new PopUpMenuDisplayData();
		displayData.anchorView = view;
		displayData.menuItems = menuItems;
		displayData.nightMode = nightMode;
		displayData.layoutId = R.layout.popup_menu_item_full_divider_check_box;
		displayData.widthMode = PopUpMenuWidthMode.STANDARD;
		PopUpMenu.show(displayData);
	}

	private void updateChooseLayersButton() {
		boolean anyBandSelected = weatherHelper.getVisibleForecastBands().size() > 0;
		int iconResId = anyBandSelected ? R.drawable.ic_layer_top : R.drawable.ic_layer_top;
		int colorId = anyBandSelected ? ColorUtilities.getActiveIconColorId(nightMode) : ColorUtilities.getDefaultIconColorId(nightMode);
		Drawable iconDrawable = app.getUIUtilities().getIcon(iconResId, colorId);
		chooseLayersBtn.setImageDrawable(iconDrawable);
	}

	private void updateChooseContoursButton() {
		boolean contourSelected = plugin.getSelectedForecastContoursType() != null;
		int iconResId = contourSelected ? R.drawable.ic_plugin_srtm : R.drawable.ic_action_contour_lines_disable;
		int colorId = contourSelected ? ColorUtilities.getActiveIconColorId(nightMode) : ColorUtilities.getDefaultIconColorId(nightMode);
		Drawable iconDrawable = app.getUIUtilities().getIcon(iconResId, colorId);
		chooseContoursBtn.setImageDrawable(iconDrawable);
	}

	@Nullable
	public MapActivity getMapActivity() {
		FragmentActivity activity = getActivity();
		if (activity instanceof MapActivity) {
			return (MapActivity) activity;
		} else {
			return null;
		}
	}

	@NonNull
	protected MapActivity requireMapActivity() {
		return ((MapActivity) requireActivity());
	}

	@NonNull
	protected static Calendar getDefaultCalendar() {
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 12);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		return calendar;
	}

	public static void showInstance(@NonNull FragmentManager manager) {
		if (AndroidUtils.isFragmentCanBeAdded(manager, TAG)) {
			manager.beginTransaction()
					.replace(R.id.fragmentContainer, new WeatherForecastFragment(), TAG)
					.addToBackStack(null)
					.commitAllowingStateLoss();
		}
	}

	private void onDownloadStateChanged(boolean isDownloading) {
		log.info("onDownloadStateChanged: " + isDownloading);
		progressUpdateHandler.removeCallbacksAndMessages(null);
		if (isAnimatingForecast) {
			if (isDownloading) {
				showProgressBar(true);
			} else {
				scheduleAnimationStart();
			}
		} else {
			showProgressBar(false);
		}
	}

	private void scheduleAnimationStart() {
		progressUpdateHandler.removeCallbacksAndMessages(null);
		progressUpdateHandler.postDelayed(() -> {
			showProgressBar(false);
			moveToNextForecastFrame();
		}, WAIT_FOR_NEW_DOWNLOAD_START_DELAY);
	}
}