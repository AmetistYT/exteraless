package app.exteraless.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import java.util.ArrayList;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.appearance.AvatarCornersSeekBar;
import app.exteraless.appearance.ExpressiveTabs;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import tw.nekomimi.nekogram.config.ConfigItem;

/**
 * Настройки Expressive-вкладок папок. Сверху превью настоящим FilterTabsView с
 * выдуманными папками: в нём работают нажатия, пружина и растяжение пилюли.
 * После каждой правки превью пересобирается; список чатов — при уходе с экрана.
 */
public class ExpressiveTabsSettingsActivity extends BaseFragment {

    private static final int ID_PREVIEW = 1;
    private static final int ID_MASTER = 2;
    private static final int ID_PILL_HEIGHT = 3;
    private static final int ID_PILL_ROUNDNESS = 4;
    private static final int ID_ISLAND_ROUNDNESS = 5;
    private static final int ID_INDICATOR = 6;
    private static final int ID_CHIPS = 7;
    private static final int ID_STRETCH = 8;
    private static final int ID_SPRING = 9;
    private static final int ID_PRESS = 10;
    private static final int ID_SPEED = 11;

    private UniversalRecyclerView listView;
    private PreviewView previewView;
    private ActionBarMenuItem resetItem;
    private boolean changed;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.OEExpressiveTabs));
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

        previewView = new PreviewView(context);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        listView.adapter.setApplyBackground(false);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);

        resetItem = actionBar.createMenu().addItem(0, R.drawable.msg_reset);
        resetItem.setContentDescription(getString(R.string.Reset));
        resetItem.setOnClickListener(v -> {
            AppearanceConfig.resetExpressiveTabs();
            onChanged(true);
        });
        updateResetButtonVisibility(false);

        fragmentView = contentView;
        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(ID_PREVIEW, previewView));
        items.add(UItem.asCheck(ID_MASTER, getString(R.string.OEExpressiveTabsEnable))
                .setChecked(AppearanceConfig.expressiveTabs.Bool()));
        items.add(UItem.asShadow(getString(R.string.OEExpressiveTabsEnableInfo)));
        if (!AppearanceConfig.expressiveTabs.Bool()) {
            return;
        }

        items.add(UItem.asHeader(getString(R.string.OEExpressiveTabsShape)));
        items.add(UItem.asCustom(ID_PILL_HEIGHT, createSlider(AppearanceConfig.expressiveTabsPillHeight,
                ExpressiveTabs.PILL_HEIGHT_MIN, ExpressiveTabs.PILL_HEIGHT_MAX, R.string.OEExpressiveTabsPillHeight, "dp")));
        items.add(UItem.asCustom(ID_PILL_ROUNDNESS, createSlider(AppearanceConfig.expressiveTabsPillRoundness,
                0, 100, R.string.OEExpressiveTabsPillRoundness, "%")));
        items.add(UItem.asCustom(ID_ISLAND_ROUNDNESS, createSlider(AppearanceConfig.expressiveTabsIslandRoundness,
                0, 100, R.string.OEExpressiveTabsIslandRoundness, "%")));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.OEExpressiveTabsLook)));
        items.add(UItem.asButton(ID_INDICATOR, getString(R.string.OEExpressiveTabsIndicator),
                indicatorModes()[AppearanceConfig.expressiveTabsIndicator.Int() % indicatorModes().length]));
        items.add(check(ID_CHIPS, R.string.OEExpressiveTabsChips, AppearanceConfig.expressiveTabsChips));
        items.add(UItem.asShadow(getString(R.string.OEExpressiveTabsChipsInfo)));

        items.add(UItem.asHeader(getString(R.string.OEExpressiveTabsMotion)));
        items.add(check(ID_STRETCH, R.string.OEExpressiveTabsStretch, AppearanceConfig.expressiveTabsStretch));
        items.add(check(ID_SPRING, R.string.OEExpressiveTabsSpring, AppearanceConfig.expressiveTabsSpring));
        items.add(check(ID_PRESS, R.string.OEExpressiveTabsPress, AppearanceConfig.expressiveTabsPressBounce));
        items.add(UItem.asButton(ID_SPEED, getString(R.string.OEExpressiveTabsSpeed),
                speedModes()[AppearanceConfig.expressiveTabsSpeed.Int() % speedModes().length]));
        items.add(UItem.asShadow(getString(R.string.OEExpressiveTabsMotionInfo)));
    }

    private static UItem check(int id, int textRes, ConfigItem item) {
        return UItem.asCheck(id, getString(textRes)).setChecked(item.Bool());
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
        switch (item.id) {
            case ID_MASTER:
                toggle(AppearanceConfig.expressiveTabs);
                break;
            case ID_CHIPS:
                toggle(AppearanceConfig.expressiveTabsChips);
                break;
            case ID_STRETCH:
                toggle(AppearanceConfig.expressiveTabsStretch);
                break;
            case ID_SPRING:
                toggle(AppearanceConfig.expressiveTabsSpring);
                break;
            case ID_PRESS:
                toggle(AppearanceConfig.expressiveTabsPressBounce);
                break;
            case ID_INDICATOR:
                choose(R.string.OEExpressiveTabsIndicator, indicatorModes(), AppearanceConfig.expressiveTabsIndicator);
                break;
            case ID_SPEED:
                choose(R.string.OEExpressiveTabsSpeed, speedModes(), AppearanceConfig.expressiveTabsSpeed);
                break;
        }
    }

    private void toggle(ConfigItem item) {
        item.setConfigBool(!item.Bool());
        onChanged(true);
    }

    private void choose(int titleRes, CharSequence[] options, ConfigItem item) {
        if (getParentActivity() == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(titleRes));
        builder.setItems(options, (dialog, which) -> {
            item.setConfigInt(which);
            onChanged(true);
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

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
        final boolean show = !AppearanceConfig.isExpressiveTabsDefault();
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

    private CharSequence[] indicatorModes() {
        return new CharSequence[]{
                getString(R.string.OEExpressiveDockIndicatorTonal),
                getString(R.string.OEExpressiveDockIndicatorAccent),
                getString(R.string.OEExpressiveDockIndicatorGlass),
        };
    }

    private CharSequence[] speedModes() {
        return new CharSequence[]{
                getString(R.string.OEExpressiveDockSpeedSlow),
                getString(R.string.OEExpressiveDockSpeedNormal),
                getString(R.string.OEExpressiveDockSpeedFast),
        };
    }

    /**
     * Превью: островок рисуется здесь же (настоящий — стеклянный и требует источник
     * размытия), внутри — настоящий FilterTabsView с теми же отступами, что в списке чатов.
     */
    @SuppressLint("ViewConstructor")
    private class PreviewView extends FrameLayout {

        private final Paint islandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint islandStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF islandRect = new RectF();
        private FilterTabsView tabsView;
        private int selectedId;

        PreviewView(Context context) {
            super(context);
            setWillNotDraw(false);
            islandStrokePaint.setStyle(Paint.Style.STROKE);
            islandStrokePaint.setStrokeWidth(AndroidUtilities.dpf2(0.66f));
            rebuild();
        }

        void rebuild() {
            if (tabsView != null) {
                selectedId = tabsView.getCurrentTabStableId();
                removeView(tabsView);
            }
            tabsView = new FilterTabsView(getContext(), getResourceProvider());
            tabsView.setDelegate(new FilterTabsView.FilterTabsViewDelegate() {
                @Override
                public void onPageSelected(FilterTabsView.Tab tab, boolean forward) {
                }

                @Override
                public void onPageScrolled(float progress) {
                }

                @Override
                public void onSamePageSelected() {
                }

                @Override
                public int getTabCounter(int tabId) {
                    return tabId == 1 ? 3 : tabId == 3 ? 12 : 0;
                }

                @Override
                public boolean didSelectTab(FilterTabsView.TabView tabView, boolean selected) {
                    return false;
                }

                @Override
                public boolean isTabMenuVisible() {
                    return false;
                }

                @Override
                public void onDeletePressed(int id) {
                }

                @Override
                public void onPageReorder(int fromId, int toId) {
                }

                @Override
                public boolean canPerformActions() {
                    return true;
                }
            });
            tabsView.addTab(0, 0, getString(R.string.FilterAllChats), "💬", false, true, false);
            tabsView.addTab(1, 1, getString(R.string.FilterContacts), "👤", false, false, false);
            tabsView.addTab(2, 2, getString(R.string.FilterGroups), "👥", false, false, false);
            tabsView.addTab(3, 3, getString(R.string.FilterChannels), "📢", false, false, false);
            tabsView.addTab(4, 4, getString(R.string.FilterBots), "🤖", false, false, false);
            tabsView.selectTabWithStableId(selectedId);
            tabsView.finishAddingTabs(false);
            tabsView.checkTabsCounter();
            tabsView.setPadding(0, dp(7), 0, dp(7));
            addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36 + 7 + 7, Gravity.CENTER_VERTICAL, 4, 0, 4, 0));
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(84), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (tabsView == null) {
                return;
            }
            final float inset = AndroidUtilities.dpf2(6.666f);
            islandRect.set(tabsView.getLeft() + inset, tabsView.getTop() + inset,
                    tabsView.getRight() - inset, tabsView.getBottom() - inset);
            final float radius = ExpressiveTabs.enabled() ? ExpressiveTabs.islandBackgroundRadius() : dp(18);
            final int bg = Theme.getColor(Theme.key_windowBackgroundWhite, getResourceProvider());
            final int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider());
            islandPaint.setColor(ColorUtils.blendARGB(bg, text, 0.04f));
            islandStrokePaint.setColor(Theme.multAlpha(text, 0.10f));
            canvas.drawRoundRect(islandRect, radius, radius, islandPaint);
            canvas.drawRoundRect(islandRect, radius, radius, islandStrokePaint);
        }
    }
}
