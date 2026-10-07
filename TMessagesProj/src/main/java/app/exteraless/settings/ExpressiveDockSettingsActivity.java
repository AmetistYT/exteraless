package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.appearance.AvatarCornersSeekBar;
import app.exteraless.appearance.ExpressiveDock;
import app.exteraless.appearance.ExpressiveDockPreviewView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import tw.nekomimi.nekogram.config.ConfigItem;

/**
 * Настройки Expressive-дока: размеры, вид и жесты, сверху — живое превью.
 *
 * Сам док живёт в MainTabsActivity, который лежит в стеке под этим экраном, поэтому
 * правки копятся, а вьюхи пересобираются один раз — при уходе с экрана.
 */
public class ExpressiveDockSettingsActivity extends BaseFragment {

    private static final int ID_PREVIEW = 1;
    private static final int ID_HEIGHT = 2;
    private static final int ID_ROUNDNESS = 3;
    private static final int ID_ICON_SIZE = 4;
    private static final int ID_INNER_PADDING = 5;
    private static final int ID_SPACING = 6;
    private static final int ID_BOTTOM_MARGIN = 7;
    private static final int ID_SIDE_MARGIN = 8;
    private static final int ID_OPACITY = 9;
    private static final int ID_FULL_WIDTH = 10;
    private static final int ID_LABELS = 11;
    private static final int ID_INDICATOR = 12;
    private static final int ID_OUTLINE = 13;
    private static final int ID_SHADOW = 14;
    private static final int ID_SWIPE = 15;
    private static final int ID_LIVE_SWITCH = 16;
    private static final int ID_LIQUID = 17;
    private static final int ID_HAPTICS = 18;
    private static final int ID_SPEED = 19;

    private UniversalRecyclerView listView;
    private ExpressiveDockPreviewView previewView;
    private ActionBarMenuItem resetItem;
    private boolean changed;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.OEExpressiveDock));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        final FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        previewView = new ExpressiveDockPreviewView(context, getResourceProvider());

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        listView.adapter.setApplyBackground(false);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);

        resetItem = actionBar.createMenu().addItem(0, R.drawable.msg_reset);
        resetItem.setContentDescription(getString(R.string.Reset));
        resetItem.setOnClickListener(v -> {
            AppearanceConfig.resetExpressiveDock();
            onChanged(true);
        });
        updateResetButtonVisibility(false);

        fragmentView = contentView;
        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.OEExpressiveDockPreview)));
        items.add(UItem.asCustom(ID_PREVIEW, previewView));
        items.add(UItem.asShadow(getString(R.string.OEExpressiveDockPreviewInfo)));

        items.add(UItem.asHeader(getString(R.string.OEExpressiveDockSize)));
        items.add(UItem.asCustom(ID_HEIGHT, createSlider(AppearanceConfig.expressiveDockHeight,
                ExpressiveDock.HEIGHT_MIN, ExpressiveDock.HEIGHT_MAX, R.string.OEExpressiveDockHeight, "dp")));
        items.add(UItem.asCustom(ID_ROUNDNESS, createSlider(AppearanceConfig.expressiveDockRoundness,
                0, 100, R.string.OEExpressiveDockRoundness, "%")));
        items.add(UItem.asCustom(ID_ICON_SIZE, createSlider(AppearanceConfig.expressiveDockIconSize,
                ExpressiveDock.ICON_MIN, ExpressiveDock.ICON_MAX, R.string.OEExpressiveDockIconSize, "dp")));
        items.add(UItem.asCustom(ID_INNER_PADDING, createSlider(AppearanceConfig.expressiveDockInnerPadding,
                0, ExpressiveDock.INNER_PADDING_MAX, R.string.OEExpressiveDockInnerPadding, "dp")));
        items.add(UItem.asCustom(ID_SPACING, createSlider(AppearanceConfig.expressiveDockSpacing,
                0, ExpressiveDock.SPACING_MAX, R.string.OEExpressiveDockSpacing, "dp")));
        items.add(UItem.asCustom(ID_BOTTOM_MARGIN, createSlider(AppearanceConfig.expressiveDockBottomMargin,
                0, ExpressiveDock.MARGIN_MAX, R.string.OEExpressiveDockBottomMargin, "dp")));
        items.add(UItem.asCustom(ID_SIDE_MARGIN, createSlider(AppearanceConfig.expressiveDockSideMargin,
                0, ExpressiveDock.SIDE_MARGIN_MAX, R.string.OEExpressiveDockSideMargin, "dp")));
        items.add(UItem.asCheck(ID_FULL_WIDTH, getString(R.string.OEExpressiveDockFullWidth))
                .setChecked(AppearanceConfig.expressiveDockFullWidth.Bool()));
        items.add(UItem.asShadow(getString(R.string.OEExpressiveDockFullWidthInfo)));

        items.add(UItem.asHeader(getString(R.string.OEExpressiveDockLook)));
        items.add(UItem.asCustom(ID_OPACITY, createSlider(AppearanceConfig.expressiveDockOpacity,
                0, 100, R.string.OEExpressiveDockOpacity, "%")));
        items.add(UItem.asButton(ID_LABELS, getString(R.string.OEExpressiveDockLabels),
                labelModes()[AppearanceConfig.expressiveDockLabels.Int() % labelModes().length]));
        items.add(UItem.asButton(ID_INDICATOR, getString(R.string.OEExpressiveDockIndicator),
                indicatorModes()[AppearanceConfig.expressiveDockIndicator.Int() % indicatorModes().length]));
        items.add(UItem.asCheck(ID_OUTLINE, getString(R.string.OEExpressiveDockOutline))
                .setChecked(AppearanceConfig.expressiveDockOutline.Bool()));
        items.add(UItem.asCheck(ID_SHADOW, getString(R.string.OEExpressiveDockShadow))
                .setChecked(AppearanceConfig.expressiveDockShadow.Bool()));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.OEExpressiveDockGestures)));
        items.add(UItem.asButton(ID_SWIPE, getString(R.string.OEExpressiveDockSwipe),
                swipeModes()[AppearanceConfig.expressiveDockSwipe.Int() % swipeModes().length]));
        if (AppearanceConfig.expressiveDockSwipe.Int() != ExpressiveDock.SWIPE_OFF) {
            items.add(UItem.asCheck(ID_LIVE_SWITCH, getString(R.string.OEExpressiveDockLiveSwitch))
                    .setChecked(AppearanceConfig.expressiveDockLiveSwitch.Bool()));
            items.add(UItem.asCheck(ID_LIQUID, getString(R.string.OEExpressiveDockLiquid))
                    .setChecked(AppearanceConfig.expressiveDockLiquid.Bool()));
            items.add(UItem.asCheck(ID_HAPTICS, getString(R.string.OEExpressiveDockHaptics))
                    .setChecked(AppearanceConfig.expressiveDockHaptics.Bool()));
        }
        items.add(UItem.asButton(ID_SPEED, getString(R.string.OEExpressiveDockSpeed),
                speedModes()[AppearanceConfig.expressiveDockAnimationSpeed.Int() % speedModes().length]));
        items.add(UItem.asShadow(getString(R.string.OEExpressiveDockGesturesInfo)));
    }

    private View createSlider(ConfigItem item, int min, int max, int titleRes, String suffix) {
        final AvatarCornersSeekBar slider = new AvatarCornersSeekBar(getContext(),
                value -> {
                    item.setConfigInt(value);
                    onChanged(false);
                },
                min, max, getString(titleRes), min + " " + suffix, max + " " + suffix);
        slider.setValueSuffix(suffix);
        slider.setValue(Math.max(min, Math.min(max, item.Int())));
        return slider;
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        final int id = item.id;
        if (id == ID_FULL_WIDTH) {
            toggle(AppearanceConfig.expressiveDockFullWidth);
        } else if (id == ID_OUTLINE) {
            toggle(AppearanceConfig.expressiveDockOutline);
        } else if (id == ID_SHADOW) {
            toggle(AppearanceConfig.expressiveDockShadow);
        } else if (id == ID_LIVE_SWITCH) {
            toggle(AppearanceConfig.expressiveDockLiveSwitch);
        } else if (id == ID_LIQUID) {
            toggle(AppearanceConfig.expressiveDockLiquid);
        } else if (id == ID_HAPTICS) {
            toggle(AppearanceConfig.expressiveDockHaptics);
        } else if (id == ID_LABELS) {
            choose(R.string.OEExpressiveDockLabels, labelModes(), AppearanceConfig.expressiveDockLabels);
        } else if (id == ID_INDICATOR) {
            choose(R.string.OEExpressiveDockIndicator, indicatorModes(), AppearanceConfig.expressiveDockIndicator);
        } else if (id == ID_SWIPE) {
            choose(R.string.OEExpressiveDockSwipe, swipeModes(), AppearanceConfig.expressiveDockSwipe);
        } else if (id == ID_SPEED) {
            choose(R.string.OEExpressiveDockSpeed, speedModes(), AppearanceConfig.expressiveDockAnimationSpeed);
        }
    }

    private void toggle(ConfigItem item) {
        item.setConfigBool(!item.Bool());
        onChanged(true);
    }

    private void choose(int titleRes, CharSequence[] options, ConfigItem item) {
        showChoice(getString(titleRes), options, which -> {
            item.setConfigInt(which);
            onChanged(true);
        });
    }

    /**
     * @param updateList перечитать список — нужно для переключателей и выбора; слайдеры
     *                   сами показывают своё значение, и пересобирать их на каждом шаге незачем
     */
    private void onChanged(boolean updateList) {
        changed = true;
        if (previewView != null) {
            previewView.rebuild();
        }
        if (updateList && listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        updateResetButtonVisibility(true);
    }

    private void updateResetButtonVisibility(boolean animated) {
        if (resetItem == null) {
            return;
        }
        final boolean show = !AppearanceConfig.isExpressiveDockDefault();
        if (show != (resetItem.getVisibility() == View.VISIBLE)) {
            AndroidUtilities.updateViewVisibilityAnimated(resetItem, show, 0.5f, animated);
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (changed && getParentLayout() != null) {
            getParentLayout().rebuildAllFragmentViews(false, false);
        }
    }

    private void showChoice(String title, CharSequence[] options, Utilities.Callback<Integer> onSelected) {
        if (getParentActivity() == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setItems(options, (dialog, which) -> onSelected.run(which));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private CharSequence[] labelModes() {
        return new CharSequence[]{
                getString(R.string.OEExpressiveDockLabelsSelected),
                getString(R.string.OEExpressiveDockLabelsAll),
                getString(R.string.OEExpressiveDockLabelsNone),
        };
    }

    private CharSequence[] indicatorModes() {
        return new CharSequence[]{
                getString(R.string.OEExpressiveDockIndicatorTonal),
                getString(R.string.OEExpressiveDockIndicatorAccent),
                getString(R.string.OEExpressiveDockIndicatorGlass),
        };
    }

    private CharSequence[] swipeModes() {
        return new CharSequence[]{
                getString(R.string.OEExpressiveDockSwipeOff),
                getString(R.string.OEExpressiveDockSwipeActive),
                getString(R.string.OEExpressiveDockSwipeAnywhere),
        };
    }

    private CharSequence[] speedModes() {
        return new CharSequence[]{
                getString(R.string.OEExpressiveDockSpeedSlow),
                getString(R.string.OEExpressiveDockSpeedNormal),
                getString(R.string.OEExpressiveDockSpeedFast),
        };
    }
}
