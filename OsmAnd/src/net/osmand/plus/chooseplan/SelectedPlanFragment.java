package net.osmand.plus.chooseplan;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import net.osmand.AndroidUtils;
import net.osmand.PlatformUtil;
import net.osmand.plus.R;
import net.osmand.plus.UiUtilities;
import net.osmand.plus.Version;
import net.osmand.plus.inapp.InAppPurchases.InAppSubscription;
import net.osmand.plus.inapp.InAppPurchases.InAppSubscriptionIntroductoryInfo;
import net.osmand.plus.widgets.TextViewEx;
import net.osmand.plus.wikipedia.WikipediaDialogFragment;
import net.osmand.util.Algorithms;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.osmand.plus.chooseplan.ChoosePlanDialogFragment.subscribe;
import static net.osmand.plus.liveupdates.LiveUpdatesSettingsBottomSheet.getActiveColorId;

public abstract class SelectedPlanFragment extends BasePurchaseFragment {

	public static final String TAG = SelectedPlanFragment.class.getSimpleName();
	private static final Log LOG = PlatformUtil.getLog(SelectedPlanFragment.class);

	private static final String PURCHASES_INFO = "https://docs.osmand.net/en/main@latest/osmand/purchases/android";

	protected List<OsmAndFeature> includedFeatures = new ArrayList<>();
	protected List<OsmAndFeature> noIncludedFeatures = new ArrayList<>();
	private String selectedItemTag;

	@Override
	protected int getLayoutId() {
		return R.layout.fragment_selected_plan;
	}

	@Override
	protected void initView() {
		setupToolbar();
		setupHeader();
		createFeaturesPreview();
		setupLearnMoreButton();
		setupPriceButtons();
		setupApplyButton();
		setupDescription();
		setupRestoreButton();
		createIncludesList();
		fullUpdate();
	}

	private void fullUpdate() {
		updateToolbar();
	}

	private void setupToolbar() {
		ImageView backBtn = view.findViewById(R.id.button_back);
		backBtn.setImageResource(AndroidUtils.getNavigationIconResId(app));
		backBtn.setOnClickListener(v -> dismiss());

		ImageView helpBtn = view.findViewById(R.id.button_help);
		helpBtn.setOnClickListener(v ->
				WikipediaDialogFragment.showFullArticle(requireActivity(), Uri.parse(PURCHASES_INFO), nightMode));
	}

	@Override
	protected void updateToolbar(int verticalOffset) {
		float absOffset = Math.abs(verticalOffset);
		float totalScrollRange = appBar.getTotalScrollRange();
		boolean collapsed = Math.abs(verticalOffset) == appBar.getTotalScrollRange();

		float alpha = UiUtilities.getProportionalAlpha(0, totalScrollRange * 0.75f, absOffset);
		float inverseAlpha = 1.0f - UiUtilities.getProportionalAlpha(0, totalScrollRange, absOffset);

		int defaultLinksColor = ContextCompat.getColor(app, getToolbarLinksColor());
		int activeLinksColor = ContextCompat.getColor(app, getActiveToolbarLinksColor());
		int headerBgColor = ContextCompat.getColor(app, getHeaderBgColorId());
		int activeColor = ContextCompat.getColor(app, getActiveColorId(nightMode));

		View header = view.findViewById(R.id.header);
		header.setAlpha(alpha);

		ImageView icBack = view.findViewById(R.id.button_back);
		ImageView icInfo = view.findViewById(R.id.button_help);
		int iconsColor = UiUtilities.getProportionalColorMix(defaultLinksColor, activeLinksColor, 0, totalScrollRange, Math.abs(verticalOffset));
		icBack.setColorFilter(iconsColor);
		icInfo.setColorFilter(iconsColor);

		TextView tvTitle = view.findViewById(R.id.toolbar_title);
		tvTitle.setTextColor(activeLinksColor);
		tvTitle.setText(getHeader());
		tvTitle.setAlpha(inverseAlpha);

		int toolbarColor = UiUtilities.getProportionalColorMix(headerBgColor, activeColor, 0, totalScrollRange, Math.abs(verticalOffset));
		appBar.setBackgroundColor(toolbarColor);
		Dialog dialog = getDialog();
		if (Build.VERSION.SDK_INT >= 21 && dialog != null && dialog.getWindow() != null) {
			Window window = dialog.getWindow();
			window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
			window.setStatusBarColor(toolbarColor);
			if (Build.VERSION.SDK_INT >= 23 && !nightMode) {
				window.getDecorView().setSystemUiVisibility(collapsed ?
						view.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR :
						view.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
			}
		}

		View shadow = view.findViewById(R.id.shadowView);
		shadow.setAlpha(inverseAlpha);
	}

	private void setupHeader() {
		View mainPart = view.findViewById(R.id.main_part);
		int bgColor = ContextCompat.getColor(app, getHeaderBgColorId());
		mainPart.setBackgroundColor(bgColor);

		TextView tvTitle = view.findViewById(R.id.header_title);
		tvTitle.setText(getHeader());

		TextView tvDescription = view.findViewById(R.id.header_descr);
		tvDescription.setText(getTagline());

		ImageView ivIcon = view.findViewById(R.id.header_icon);
		ivIcon.setImageResource(getHeaderIconId());
	}

	private void createFeaturesPreview() {
		LinearLayout container = view.findViewById(R.id.features_list);
		for (OsmAndFeature feature : features) {
			View itemView = inflater.inflate(R.layout.purchase_dialog_preview_list_item, container, false);
			bindFeatureItem(itemView, feature);
			container.addView(itemView);
		}
	}

	private void setupLearnMoreButton() {
		View btn = view.findViewById(R.id.learn_more_button);
		btn.setOnClickListener(v -> {
			View includesContainer = view.findViewById(R.id.list_included);
			scrollView.smoothScrollTo(0, includesContainer.getTop());
			appBar.setExpanded(false);
		});
	}

	protected void bindFeatureItem(@NonNull View itemView,
								   @NonNull OsmAndFeature feature) {
		bindFeatureItem(itemView, feature, false);
		ImageView ivCheckmark = itemView.findViewById(R.id.checkmark);
		if (includedFeatures.contains(feature)) {
			ivCheckmark.setVisibility(View.VISIBLE);
			ivCheckmark.setImageDrawable(getPreviewListCheckmark());
		} else {
			ivCheckmark.setVisibility(View.GONE);
		}
	}

	private void setupPriceButtons() {
		LinearLayout container = view.findViewById(R.id.price_block);
		container.removeAllViews();

		View lastBtn = null;
		List<InAppSubscription> visibleSubscriptions = getVisibleSubscriptions();
		InAppSubscription maxDiscountSubscription = null;
		double maxDiscount = 0;
		boolean anyPurchased = false;
		for (final InAppSubscription s : visibleSubscriptions) {
			if (s.isPurchased()) {
				anyPurchased = true;
			}
			double discount = s.getDiscountPercent(purchaseHelper.getMonthlyLiveUpdates());
			if (discount > maxDiscount) {
				maxDiscountSubscription = s;
				maxDiscount = discount;
			}
		}
		boolean maxDiscountAction = maxDiscountSubscription != null && maxDiscountSubscription.hasDiscountOffer();
		for (final InAppSubscription s : visibleSubscriptions) {
			InAppSubscriptionIntroductoryInfo introductoryInfo = s.getIntroductoryInfo();
			boolean hasIntroductoryInfo = introductoryInfo != null;
			CharSequence descriptionText = s.getDescription(themedCtx);
			if (s.isPurchased()) {
				View buttonPurchased = inflater.inflate(R.layout.purchase_dialog_card_button_active_ex, container, false);
				TextViewEx title = (TextViewEx) buttonPurchased.findViewById(R.id.title);
				TextViewEx description = (TextViewEx) buttonPurchased.findViewById(R.id.description);
				TextViewEx descriptionContribute = (TextViewEx) buttonPurchased.findViewById(R.id.description_contribute);
				descriptionContribute.setVisibility(s.isDonationSupported() ? View.VISIBLE : View.GONE);
				TextViewEx buttonTitle = (TextViewEx) buttonPurchased.findViewById(R.id.button_title);
				View buttonView = buttonPurchased.findViewById(R.id.button_view);
				View buttonCancelView = buttonPurchased.findViewById(R.id.button_cancel_view);
				View div = buttonPurchased.findViewById(R.id.div);
				AppCompatImageView rightImage = (AppCompatImageView) buttonPurchased.findViewById(R.id.right_image);

				CharSequence priceTitle = hasIntroductoryInfo ?
						introductoryInfo.getFormattedDescription(themedCtx, buttonTitle.getCurrentTextColor()) : s.getPriceWithPeriod(app);
				title.setText(s.getTitle(app));
				if (Algorithms.isEmpty(descriptionText.toString())) {
					description.setVisibility(View.GONE);
				} else {
					description.setText(descriptionText);
				}
				buttonTitle.setText(priceTitle);
				buttonView.setVisibility(View.VISIBLE);
				buttonCancelView.setVisibility(View.GONE);
				buttonPurchased.setOnClickListener(null);
				div.setVisibility(View.GONE);
				rightImage.setVisibility(View.GONE);
				if (s.isDonationSupported()) {
					buttonPurchased.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
//							showDonationSettings();
							dismiss();
						}
					});
				} else {
					buttonPurchased.setOnClickListener(null);
				}
				container.addView(buttonPurchased);

				View buttonCancel = inflater.inflate(R.layout.purchase_dialog_card_button_active_ex, container, false);
				title = (TextViewEx) buttonCancel.findViewById(R.id.title);
				description = (TextViewEx) buttonCancel.findViewById(R.id.description);
				buttonView = buttonCancel.findViewById(R.id.button_view);
				buttonCancelView = buttonCancel.findViewById(R.id.button_cancel_view);
				div = buttonCancel.findViewById(R.id.div);
				rightImage = (AppCompatImageView) buttonCancel.findViewById(R.id.right_image);

				title.setText(getString(R.string.osm_live_payment_current_subscription));
				description.setText(s.getRenewDescription(themedCtx));
				buttonView.setVisibility(View.GONE);
				buttonCancelView.setVisibility(View.VISIBLE);
				buttonCancelView.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						purchaseHelper.manageSubscription(app, s.getSku());
					}
				});
				div.setVisibility(View.VISIBLE);
				rightImage.setVisibility(View.VISIBLE);
				container.addView(buttonCancel);
				lastBtn = buttonCancel;
			} else {
				View button = inflater.inflate(R.layout.purchase_dialog_card_button_ex, container, false);
				TextViewEx title = (TextViewEx) button.findViewById(R.id.title);
				TextViewEx description = (TextViewEx) button.findViewById(R.id.description);
				TextViewEx descriptionContribute = (TextViewEx) button.findViewById(R.id.description_contribute);
				descriptionContribute.setVisibility(s.isDonationSupported() ? View.VISIBLE : View.GONE);

				View buttonView = button.findViewById(R.id.button_view);
				View buttonExView = button.findViewById(R.id.button_ex_view);
				TextViewEx buttonTitle = (TextViewEx) button.findViewById(R.id.button_title);
				TextViewEx buttonExTitle = (TextViewEx) button.findViewById(R.id.button_ex_title);

				boolean showSolidButton = !anyPurchased
						&& (!maxDiscountAction || hasIntroductoryInfo || maxDiscountSubscription.isUpgrade());
				int descriptionColor = showSolidButton ? buttonExTitle.getCurrentTextColor() : buttonTitle.getCurrentTextColor();
				buttonView.setVisibility(!showSolidButton ? View.VISIBLE : View.GONE);
				buttonExView.setVisibility(showSolidButton ? View.VISIBLE : View.GONE);
				View div = button.findViewById(R.id.div);

				CharSequence priceTitle = hasIntroductoryInfo ?
						introductoryInfo.getFormattedDescription(app, descriptionColor) : s.getPriceWithPeriod(app);
				title.setText(s.getTitle(app));
				if (Algorithms.isEmpty(descriptionText.toString())) {
					description.setVisibility(View.GONE);
				} else {
					description.setText(descriptionText);
				}
				buttonTitle.setText(priceTitle);
				buttonExTitle.setText(priceTitle);

				TextViewEx buttonDiscountTitle = (TextViewEx) button.findViewById(R.id.button_discount_title);
				View buttonDiscountView = button.findViewById(R.id.button_discount_view);
				String discountTitle = s.getDiscountTitle(app, purchaseHelper.getMonthlyLiveUpdates());
				if (!Algorithms.isEmpty(discountTitle)) {
					buttonDiscountTitle.setText(discountTitle);
					buttonDiscountView.setVisibility(View.VISIBLE);
					if (s.equals(maxDiscountSubscription)) {
						int saveTextColor = R.color.color_osm_edit_delete;
						if (hasIntroductoryInfo || maxDiscountSubscription.isUpgrade()) {
							saveTextColor = R.color.active_buttons_and_links_text_light;
							AndroidUtils.setBackground(buttonDiscountView, UiUtilities.tintDrawable(buttonDiscountView.getBackground(),
									ContextCompat.getColor(themedCtx, R.color.color_osm_edit_delete)));
						}
						buttonDiscountTitle.setTextColor(ContextCompat.getColor(app, saveTextColor));
					}
				}
				if (!showSolidButton) {
					buttonView.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							if (getActivity() != null) {
								subscribe(app, getActivity(), purchaseHelper, s.getSku());
							}
						}
					});
				} else {
					buttonExView.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							if (getActivity() != null) {
								subscribe(app, getActivity(), purchaseHelper, s.getSku());
							}
						}
					});
				}
				div.setVisibility(View.VISIBLE);
				container.addView(button);
				lastBtn = button;
			}
		}
		if (lastBtn != null) {
			View div = lastBtn.findViewById(R.id.div);
			if (div != null) {
				div.setVisibility(View.GONE);
			}
		}
		container.setVisibility(View.VISIBLE);
	}

	private View createPurchaseButton(String title,
									  String price,
									  String discount,
									  String description) {
		View itemView = inflater.inflate(R.layout.purchase_dialog_btn_payment, null);

		TextView tvTitle = itemView.findViewById(R.id.title);
		tvTitle.setText(title);

		TextView tvPrice = itemView.findViewById(R.id.price);
		tvPrice.setText(price);

		TextView tvDiscount = itemView.findViewById(R.id.discount);
		if (discount != null) {
			tvDiscount.setText(discount);
			tvDiscount.setVisibility(View.VISIBLE);
		}

		TextView tvDesc = itemView.findViewById(R.id.description);
		if (description != null) {
			tvDesc.setText(description);
			tvDesc.setVisibility(View.VISIBLE);
		}

		itemView.setTag(title);
		itemView.setOnClickListener(v -> {
			selectedItemTag = title;
			updateButtons();
		});

		return itemView;
	}

	private void updateButtons() {
		LinearLayout container = view.findViewById(R.id.price_block);
		for (int i = 0; i < container.getChildCount(); i++) {
			View itemView = container.getChildAt(i);
			ImageView ivCheckmark = itemView.findViewById(R.id.icon);

			int colorNoAlpha = ContextCompat.getColor(app, getActiveColorId(nightMode));
			Drawable normal;
			if (itemView.getTag().equals(selectedItemTag)) {
				ivCheckmark.setImageDrawable(getCheckmark());

				Drawable stroke = getActiveStrokeDrawable();
				int colorWithAlpha = getAlphaColor(colorNoAlpha, 0.1f);

				Drawable bgDrawable = app.getUIUtilities().getPaintedIcon(R.drawable.rectangle_rounded, colorWithAlpha);
				Drawable[] layers = {bgDrawable, stroke};
				normal = new LayerDrawable(layers);
			} else {
				ivCheckmark.setImageDrawable(getEmptyCheckmark());
				normal = new ColorDrawable(Color.TRANSPARENT);
			}
			setupRoundedBackground(itemView, normal, colorNoAlpha);
		}
	}

	private void setupApplyButton() {
		View itemView = view.findViewById(R.id.apply_button);

		TextView tvTitle = itemView.findViewById(R.id.title);
		tvTitle.setText("Complete purchase");

		TextView tvDesc = itemView.findViewById(R.id.description);
		tvDesc.setText("€ 11,99 / year");

		itemView.setOnClickListener(v -> app.showShortToastMessage("Purchase"));

		int activeColor = ContextCompat.getColor(app, getActiveColorId(nightMode));
		Drawable normal = new ColorDrawable(activeColor);
		setupRoundedBackground(itemView, normal, activeColor);
	}

	private void setupDescription() {
		String cancelDesc = getString(Version.isHuawei() ?
				R.string.cancel_anytime_in_huawei_appgallery :
				R.string.cancel_anytime_in_gplay);

		String commonDesc = getString(Version.isHuawei() ?
				R.string.osm_live_payment_subscription_management_hw :
				R.string.osm_live_payment_subscription_management);

		((TextView) view.findViewById(R.id.cancel_description)).setText(cancelDesc);
		((TextView) view.findViewById(R.id.plan_info_description)).setText(commonDesc);
	}

	private void setupRestoreButton() {
		View button = view.findViewById(R.id.button_restore);
		button.setOnClickListener(v -> purchaseHelper.requestInventory());
		setupRoundedBackground(button);
	}

	private void createIncludesList() {
		LinearLayout container = view.findViewById(R.id.list_included);
		Map<String, List<OsmAndFeature>> chapters = new LinkedHashMap<>();
		chapters.put(getString(R.string.ltr_or_rtl_combine_via_colon, getString(R.string.shared_string_includes), ""), includedFeatures);
		chapters.put(getString(R.string.ltr_or_rtl_combine_via_colon, getString(R.string.shared_string_not_included), ""), noIncludedFeatures);

		for (String key : chapters.keySet()) {
			List<OsmAndFeature> features = chapters.get(key);
			if (features != null && features.size() > 0) {
				View v = inflater.inflate(R.layout.purchase_dialog_includes_block_header, container, false);
				TextView tvTitle = v.findViewById(R.id.title);
				tvTitle.setText(key);
				container.addView(v);
				for (OsmAndFeature feature : features) {
					View itemView = inflater.inflate(R.layout.purchase_dialog_includes_block_item, container, false);
					if (features.indexOf(feature) == 0) {
						itemView.findViewById(R.id.top_padding).setVisibility(View.GONE);
					}
					bindIncludesFeatureItem(itemView, feature);
					container.addView(itemView);
				}
			}
		}
	}

	private void bindIncludesFeatureItem(View itemView, OsmAndFeature feature) {
		bindFeatureItem(itemView, feature, true);

		TextView tvDescription = itemView.findViewById(R.id.description);
		tvDescription.setText(getString(feature.getDescriptionId()));

		int iconBgColor = ContextCompat.getColor(app, feature.isAvailableInMapsPlus() ?
				R.color.maps_plus_item_bg :
				R.color.osmand_pro_item_bg);
		setupIconBackground(itemView.findViewById(R.id.icon_background), getAlphaColor(iconBgColor, 0.2f));
	}

	protected Drawable getCheckmark() {
		return getIcon(nightMode ?
				R.drawable.ic_action_checkmark_colored_night :
				R.drawable.ic_action_checkmark_colored_day);
	}

	protected Drawable getEmptyCheckmark() {
		return getIcon(nightMode ?
				R.drawable.ic_action_radio_button_night :
				R.drawable.ic_action_radio_button_day);
	}

	@ColorRes
	protected int getToolbarLinksColor() {
		return nightMode ?
				R.color.purchase_sc_toolbar_active_dark :
				R.color.purchase_sc_toolbar_active_light;
	}

	@ColorRes
	protected int getActiveToolbarLinksColor() {
		return nightMode ?
				R.color.active_buttons_and_links_text_dark :
				R.color.active_buttons_and_links_text_light;
	}

	protected abstract List<InAppSubscription> getVisibleSubscriptions();

	protected abstract Drawable getPreviewListCheckmark();

	protected abstract int getHeaderBgColorId();

	protected abstract String getHeader();

	protected abstract String getTagline();

	protected abstract int getHeaderIconId();

}
