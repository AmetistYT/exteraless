package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedLinearLayout;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.glass.GlassTabView;

import app.exteraless.appearance.ExpressiveDock;
import app.exteraless.appearance.MainTabsUiHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.util.ClickHelper;

import tw.nekomimi.nekogram.NekoConfig;
import xyz.nextalone.nagram.NaConfig;

@SuppressLint("ViewConstructor")
public class MainTabsLayout extends AnimatedLinearLayout {

    public static final int BOTTOM_NAVIGATION_MODE_SHOW = 0;
    public static final int BOTTOM_NAVIGATION_MODE_HIDE = 1;
    public static final int BOTTOM_NAVIGATION_MODE_FLOATING = 2;

    private static final String KEY_BOTTOM_NAVIGATION_FLOATING = "OEAppearanceBottomNavigationFloating";

    private static Boolean bottomNavigationFloating;

    public static int getBottomNavigationMode() {
        if (isBottomNavigationHidden()) {
            return BOTTOM_NAVIGATION_MODE_HIDE;
        }
        return isBottomNavigationFloating() ? BOTTOM_NAVIGATION_MODE_FLOATING : BOTTOM_NAVIGATION_MODE_SHOW;
    }

    public static void setBottomNavigationMode(int mode) {
        NaConfig.INSTANCE.getHideBottomNavigationBar().setConfigBool(mode == BOTTOM_NAVIGATION_MODE_HIDE);
        final boolean floating = mode == BOTTOM_NAVIGATION_MODE_FLOATING;
        bottomNavigationFloating = floating;
        final SharedPreferences preferences = getBottomNavigationPreferences();
        if (preferences != null) {
            preferences.edit().putBoolean(KEY_BOTTOM_NAVIGATION_FLOATING, floating).apply();
        }
    }

    public static boolean isBottomNavigationHidden() {
        return NaConfig.INSTANCE.getHideBottomNavigationBar().Bool();
    }

    public static boolean isBottomNavigationVisible() {
        return !isBottomNavigationHidden();
    }

    public static boolean isBottomNavigationFloating() {
        if (isBottomNavigationHidden()) {
            return false;
        }
        if (bottomNavigationFloating == null) {
            final SharedPreferences preferences = getBottomNavigationPreferences();
            if (preferences == null) {
                return false;
            }
            bottomNavigationFloating = preferences.getBoolean(KEY_BOTTOM_NAVIGATION_FLOATING, false);
        }
        return bottomNavigationFloating;
    }

    private static SharedPreferences getBottomNavigationPreferences() {
        return ApplicationLoader.applicationContext == null ? null : NekoConfig.getPreferences();
    }

    private final Theme.ResourcesProvider resourcesProvider;

    public MainTabsLayout(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
    }

    private static final float[] PASS_TEXT_SIZES_DP = {12f, 12f, 10f};
    private static final int[] PASS_PADDINGS_DP = {16, 8, 4};

    private int maxWidthPx;

    public void setMaxWidth(int maxWidthPx) {
        if (this.maxWidthPx != maxWidthPx) {
            this.maxWidthPx = maxWidthPx;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isExpressive()) {
            measureExpressive(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int tabHeight = height - getPaddingTop() - getPaddingBottom();

        if (maxWidthPx > 0 && width > maxWidthPx) {
            width = maxWidthPx;
        }

        final boolean fillWidth = MainTabsUiHelper.isMaterial3NavigationBar() || MainTabsUiHelper.isIosNavigationBar();
        final int maxTotalWidthForTabs = width - getPaddingLeft() - getPaddingRight();
        final int minTotalWidthForTabs = fillWidth
                ? maxTotalWidthForTabs
                : Math.min(dp(320), maxTotalWidthForTabs);

        int chosenPass = PASS_TEXT_SIZES_DP.length - 1;
        float lastMeasuredTextSize = -1;
        for (int pass = 0; pass < PASS_TEXT_SIZES_DP.length; pass++) {
            if (PASS_TEXT_SIZES_DP[pass] != lastMeasuredTextSize) {
                measureTabTexts(PASS_TEXT_SIZES_DP[pass]);
                lastMeasuredTextSize = PASS_TEXT_SIZES_DP[pass];
            }
            final int padding = dp(PASS_PADDINGS_DP[pass]);
            float total = 0;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                if (!isViewVisible(getChildAt(a))) continue;
                final float withMargin = tabsTextWidth[a] + padding * 2;
                total += withMargin;
            }
            final boolean fits = total <= maxTotalWidthForTabs;
            if (fits || pass == PASS_TEXT_SIZES_DP.length - 1) {
                chosenPass = pass;
                break;
            }
        }

        applyPassTextSize(chosenPass);

        final int tabPadding = dp(PASS_PADDINGS_DP[chosenPass]);
        final int maxTabTextWidthIfEq = (maxTotalWidthForTabs / Math.max(1, visibleChildCount)) - tabPadding * 2;

        float totalWidth = 0;
        int totalWeight = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (!isViewVisible(child)) {
                tabsTextWidth[a] = tabsTextWidthWithMargin[a] = 0;
                tabsWeight[a] = 0;
                continue;
            }

            tabsTextWidthWithMargin[a] = tabsTextWidth[a] + tabPadding * 2;
            tabsWeight[a] = tabsTextWidthWithMargin[a] > (maxTabTextWidthIfEq + tabPadding * 2) ? 0 : 1;

            totalWidth += tabsTextWidthWithMargin[a];
            totalWeight += tabsWeight[a];
        }

        if (totalWeight == 0) {
            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsWeight[a] = isViewVisible(getChildAt(a)) ? 1 : 0;
            }
            totalWeight = visibleChildCount;
        }

        if (totalWidth > maxTotalWidthForTabs) {
            final float m = maxTotalWidthForTabs / totalWidth;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsTextWidthWithMargin[a] *= m;
            }
        } else if (totalWidth < minTotalWidthForTabs) {
            final float growW = minTotalWidthForTabs - totalWidth;
            final float growP = growW / totalWeight;

            for (int a = 0, N = getChildCount(); a < N; a++) {
                tabsTextWidthWithMargin[a] += growP * tabsWeight[a];
            }
        }

        if (MainTabsUiHelper.isMaterial3NavigationBar() && visibleChildCount > 0) {
            final float share = (float) maxTotalWidthForTabs / visibleChildCount;
            boolean fitsEqually = true;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                if (isViewVisible(getChildAt(a)) && tabsTextWidth[a] + tabPadding * 2 > share) {
                    fitsEqually = false;
                    break;
                }
            }
            if (fitsEqually) {
                for (int a = 0, N = getChildCount(); a < N; a++) {
                    if (isViewVisible(getChildAt(a))) {
                        tabsTextWidthWithMargin[a] = share;
                    }
                }
            }
        }

        int l = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            if (!isViewVisible(getChildAt(a))) {
                continue;
            }

            tabsWidth[a] = Math.round(tabsTextWidthWithMargin[a]);
            tabsLeftPos[a] = l;
            l += tabsWidth[a];
        }
        setMeasuredDimension(l + getPaddingLeft() + getPaddingRight(), height);
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            child.measure(
                MeasureSpec.makeMeasureSpec(tabsWidth[a], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));
        }

        calculateTotalSizesAfterMeasure();
    }

    // ---- Expressive-док: раскладка ----

    private int[] expressiveCollapsed;
    private int[] expressiveExpanded;

    /** Док включён и вкладки собраны под него (после переключения стиля вьюхи пересобираются). */
    private boolean isExpressive() {
        if (!MainTabsUiHelper.isExpressiveNavigationBar()) {
            return false;
        }
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child instanceof GlassTabView) {
                return ((GlassTabView) child).isMainTabExpressive();
            }
        }
        return false;
    }

    /**
     * Ширины вкладок дока. У выбранной — по содержимому (иконка + подпись), остаток делят
     * остальные. Во всю ширину остаток — это свободное место, «по содержимому» — запас под
     * самую длинную подпись, поэтому капсула не прыгает при смене вкладки: перестраиваются
     * только вкладки внутри, и AnimatedLinearLayout анимирует их переезд.
     */
    private void measureExpressive(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int tabHeight = Math.max(0, height - getPaddingTop() - getPaddingBottom());
        if (maxWidthPx > 0 && width > maxWidthPx) {
            width = maxWidthPx;
        }
        final int available = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        setAnimationDuration(ExpressiveDock.animationDuration());

        final int childCount = getChildCount();
        if (tabsWidth == null || tabsWidth.length < childCount) {
            tabsTextWidth = new float[childCount];
            tabsTextWidthWithMargin = new float[childCount];
            tabsWeight = new int[childCount];
            tabsLeftPos = new int[childCount];
            tabsWidth = new int[childCount];
        }
        if (expressiveCollapsed == null || expressiveCollapsed.length < childCount) {
            expressiveCollapsed = new int[childCount];
            expressiveExpanded = new int[childCount];
        }

        int count = 0;
        int selected = -1;
        int sumCollapsed = 0;
        int maxExtra = 0;
        for (int a = 0; a < childCount; a++) {
            final View child = getChildAt(a);
            if (!isViewVisible(child)) {
                expressiveCollapsed[a] = expressiveExpanded[a] = 0;
                continue;
            }
            if (child instanceof GlassTabView) {
                final GlassTabView tab = (GlassTabView) child;
                expressiveCollapsed[a] = tab.getExpressiveCollapsedWidth();
                expressiveExpanded[a] = tab.getExpressiveExpandedWidth();
                if (selected < 0 && tab.isTabSelected()) {
                    selected = a;
                }
            } else {
                expressiveCollapsed[a] = expressiveExpanded[a] = dp(56);
            }
            sumCollapsed += expressiveCollapsed[a];
            maxExtra = Math.max(maxExtra, expressiveExpanded[a] - expressiveCollapsed[a]);
            count++;
        }
        visibleChildCount = count;

        final int total = ExpressiveDock.fullWidth() ? available : Math.min(available, sumCollapsed + maxExtra);
        final int selectedExtra = selected >= 0 ? expressiveExpanded[selected] - expressiveCollapsed[selected] : 0;
        final int natural = sumCollapsed + selectedExtra;
        final int others = count - (selected >= 0 ? 1 : 0);
        final float free = total - natural;

        float acc = 0;
        int l = 0;
        int lastVisible = -1;
        for (int a = 0; a < childCount; a++) {
            if (!isViewVisible(getChildAt(a))) {
                tabsWidth[a] = 0;
                continue;
            }
            float w = expressiveCollapsed[a] + (a == selected ? selectedExtra : 0);
            if (free < 0) {
                // Не влезает — ужимаем всё пропорционально, подписи обрежутся сами.
                w *= natural > 0 ? total / (float) natural : 0;
            } else if (others > 0) {
                if (a != selected) {
                    w += free / others;
                }
            } else {
                w += free;
            }
            acc += w;
            final int rounded = Math.round(acc) - l;
            tabsWidth[a] = rounded;
            tabsLeftPos[a] = l;
            l += rounded;
            lastVisible = a;
        }
        if (lastVisible >= 0 && l != total && free >= 0) {
            tabsWidth[lastVisible] += total - l;
            l = total;
        }

        setMeasuredDimension(l + getPaddingLeft() + getPaddingRight(), height);
        for (int a = 0; a < childCount; a++) {
            final View child = getChildAt(a);
            child.measure(
                MeasureSpec.makeMeasureSpec(tabsWidth[a], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));
        }
        calculateTotalSizesAfterMeasure();
    }

    public interface Tab {
        float measureTextWidth();
        default float measureTextWidth(float textSizeDp) { return measureTextWidth(); }
        default void setTextSizeDp(float textSizeDp) {}
    }



    // fills tabsTextWidth[] and return visible child count;

    private float[] tabsTextWidth;
    private float[] tabsTextWidthWithMargin;
    private int[] tabsWeight;
    private int[] tabsWidth;

    private int[] tabsLeftPos;


    private int visibleChildCount;
    private int biggestTabTextWidth;

    private void measureTabTexts(float textSizeDp) {
        final int childCount = getChildCount();
        if (tabsTextWidth == null || tabsTextWidth.length < childCount) {
            tabsTextWidth = new float[childCount];
            tabsTextWidthWithMargin = new float[childCount];
            tabsWeight = new int[childCount];
            tabsLeftPos = new int[childCount];
            tabsWidth = new int[childCount];
        }

        float maxTabWidthF = 0;
        int index = 0;

        for (int a = 0; a < childCount; a++) {
            final View child = getChildAt(a);
            if (!isViewVisible(child)) {
                tabsTextWidth[a] = -1;
                continue;
            }

            final float tabWidth;
            if (child instanceof MainTabsLayout.Tab) {
                tabWidth = ((MainTabsLayout.Tab) child).measureTextWidth(textSizeDp);
            } else {
                tabWidth = 0;
            }

            tabsTextWidth[a] = tabWidth;
            maxTabWidthF = Math.max(maxTabWidthF, tabWidth);
            index++;
        }

        biggestTabTextWidth = (int) Math.ceil(maxTabWidthF);
        visibleChildCount = index;
    }

    private void applyPassTextSize(int pass) {
        final float textSizeDp = PASS_TEXT_SIZES_DP[pass];
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child instanceof MainTabsLayout.Tab) {
                ((MainTabsLayout.Tab) child).setTextSizeDp(textSizeDp);
            }
        }
    }

    @Override
    protected void setChildVisibilityFactor(View view, float factor) {
        final float s = lerp(0.7f, 1f, factor);
        view.setAlpha(factor);
        view.setScaleX(s);
        view.setScaleY(s);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        checkVisualWidth();
    }

    @Override
    protected void onItemsChanged() {
        super.onItemsChanged();
        checkVisualWidth();
    }

    private void checkVisualWidth() {
        for (int a = 0, N = getEntriesCount(); a < N; a++) {
            final ListAnimator.Entry<Holder> entry = getEntry(a);
            final float width = entry.getRectF().width();
            ((GlassTabView) entry.item.view).setVisualWidth(width);
        }
    }








    public void setTabSelected(View tab, boolean animated) {
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setSelected(child == tab, animated);
            }
        }
    }

    private View findSelectedTab() {
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }

            if (child instanceof GlassTabView) {
                if (((GlassTabView) child).isTabSelected()) {
                    return child;
                }
            }
        }
        return null;
    }

    private final Runnable restoreDrawSelector = () -> setSkipDrawSelector(false);

    private boolean drawCustomSelector;
    private void setSkipDrawSelector(boolean skipDrawSelector) {
        drawCustomSelector = skipDrawSelector;
        if (drawCustomSelector) {
            selectorPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider), 0.09f));
        }
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }

            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setSkipDrawSelector(skipDrawSelector);
            }
        }
        invalidate();
    }







    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (expressiveDragging || expressiveSettling) {
            drawExpressiveDroplet(canvas);
        }
        if (drawCustomSelector) {
            final float x = animatedLongSelectedViewCenterX + animatedLongSelectedViewOffsetX;
            final float sWidth = getInterpolatedWidthByX(x, this);
            if (MainTabsUiHelper.isMaterial3NavigationBar()) {
                MainTabsUiHelper.setMainTabSelectedIndicatorBounds(selectorRect, sWidth,
                        getHeight() - getPaddingTop() - getPaddingBottom());
                selectorRect.offset(x - sWidth / 2f, getPaddingTop());
                final float r = selectorRect.height() / 2f;
                canvas.drawRoundRect(selectorRect, r, r, selectorPaint);
            } else {
                final float sHeight = getHeight() - getPaddingTop() - getPaddingBottom();
                canvas.drawRoundRect(
                        x - sWidth / 2f, (getHeight() - sHeight) / 2f,
                        x + sWidth / 2f, (getHeight() + sHeight) / 2f,
                        sHeight / 2f, sHeight / 2f, selectorPaint);
            }
        }

        super.dispatchDraw(canvas);

        if (drawTopDivider && MainTabsUiHelper.isMaterial3NavigationBar()) {
            dividerPaint.setColor(Theme.getDividerColor(resourcesProvider));
            canvas.drawLine(0, 1, getMeasuredWidth(), 1, dividerPaint);
        }
    }

    private boolean drawTopDivider = true;

    public void setDrawTopDivider(boolean value) {
        if (drawTopDivider != value) {
            drawTopDivider = value;
            invalidate();
        }
    }

    final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF selectorRect = new android.graphics.RectF();
    final SpringAnimation scaleX = new SpringAnimation(this, DynamicAnimation.SCALE_X, 1f);
    final SpringAnimation scaleY = new SpringAnimation(this, DynamicAnimation.SCALE_Y, 1f);

    final SpringAnimation selectedTabPositionOffsetX = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("selectedTabPositionOffsetX") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.animatedLongSelectedViewOffsetX;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.animatedLongSelectedViewOffsetX = value;
            object.invalidate();
        }
    });
    final SpringAnimation selectedTabPositionX = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("selectedTabPositionX") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.animatedLongSelectedViewCenterX;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.animatedLongSelectedViewCenterX = value;
            object.invalidate();
        }
    });

    {
        selectedTabPositionOffsetX.setSpring(new SpringForce(1)
            .setStiffness(SpringForce.STIFFNESS_MEDIUM)
            .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY));
        scaleX.setSpring(new SpringForce(1f)
            .setStiffness(250)
            .setDampingRatio(0.25f));
        scaleY.setSpring(new SpringForce(1f)
            .setStiffness(250)
            .setDampingRatio(0.25f));
        selectedTabPositionX.setSpring(new SpringForce(1f)
            .setStiffness(SpringForce.STIFFNESS_MEDIUM)
            .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY));
    }

    private float animatedLongSelectedViewCenterX;
    private float animatedLongSelectedViewOffsetX;

    private boolean isInLongPress;
    private float lastLongSelectedViewCenterX;
    private float lastLongSelectedViewWidth;
    private View lastLongSelectedView;




    public static View findChildUnder(ViewGroup parent, float x, float y) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);

            if (child.getVisibility() != View.VISIBLE) continue;

            if (x >= child.getLeft() && x <= child.getRight()
                    && y >= child.getTop() && y <= child.getBottom()) {
                return child;
            }
        }
        return null;
    }

    private void checkLongMove(float x_, float y, boolean start, boolean end) {
        final float x = clampXToChildrenCenters(x_, this);
        final View found = findNearestVisibleChildByX(x, this);
        if (start) {
            View selected = findSelectedTab();
            if (selected != null) {
                animatedLongSelectedViewCenterX = selected.getX() + selected.getWidth() / 2f;
                animatedLongSelectedViewOffsetX = animatedLongSelectedViewCenterX - x;
                selectedTabPositionOffsetX.animateToFinalPosition(0);
                if (selected != found && found != null) {
                    found.performClick();
                }
            }
            selectedTabPositionX.cancel();
        }

        if (!end) {
            animatedLongSelectedViewCenterX = x;
            invalidate();
        }

        if (found != null) {
            lastLongSelectedView = found;
            setTabSelected(found, true);

            if (end) {
                final float vw = found.getWidth();
                final float cx = found.getX() + vw / 2f;
                if (lastLongSelectedViewWidth != vw || lastLongSelectedViewCenterX != cx) {
                    selectedTabPositionX.animateToFinalPosition(cx);
                }
            }
        }
    }

    private final Set<View> tabsWithIgnoreClick = new HashSet<>();
    public void addTabToIgnoreClick(View v) {
        tabsWithIgnoreClick.add(v);
    }

    private final BoolAnimator animatorIsScaled = new BoolAnimator(0, (a, factor, c, g) -> {
        setScaleX(lerp(1, 1.019f, factor));
        setScaleY(lerp(1, 1.019f, factor));
    }, CubicBezierInterpolator.EASE_OUT_QUINT, 380);

    private final ClickHelper clickHelper = new ClickHelper(new ClickHelper.Delegate() {
        @Override
        public boolean needClickAt(View view, float x, float y) {
            lastLongSelectedView = null;
            final View found = findChildUnder(MainTabsLayout.this, x, y);
            return found != null && !tabsWithIgnoreClick.contains(found);
        }

        @Override
        public void onClickAt(View view, float x, float y) {
        }

        @Override
        public boolean needLongPress(float x, float y) {
            return true;
        }

        @Override
        public boolean needCancelTouchBySlopMove() {
            return false;
        }


        @Override
        public boolean onLongPressRequestedAt(View view, float x, float y) {
            checkPivot(view, x, y);
            isInLongPress = true;
            AndroidUtilities.cancelRunOnUIThread(restoreDrawSelector);
            setSkipDrawSelector(true);
            checkLongMove(x, y, true, false);
            invalidate();
            longTouchStart();
            return true;
        }

        @Override
        public void onLongPressMove(View view, MotionEvent e, float x, float y, float startX, float startY) {
            checkPivot(view, x, y);
            checkLongMove(x, y, false, false);
            invalidate();
        }

        @Override
        public long getLongPressDuration() {
            return ClickHelper.Delegate.super.getLongPressDuration() * 750 / 1000;
        }

        @Override
        public void onLongPressFinish(View view, float x, float y) {
            checkPivot(view, x, y);
            checkLongMove(x, y, false, true);
            isInLongPress = false;
            AndroidUtilities.runOnUIThread(restoreDrawSelector, 450);
            if (lastLongSelectedView != null) {
                lastLongSelectedView.performClick();
            }
            lastLongSelectedView = null;
            invalidate();
            longTouchEnd();
        }

        @Override
        public void onLongPressCancelled(View view, float x, float y) {
            checkPivot(view, x, y);
            checkLongMove(x, y, false, true);
            isInLongPress = false;
            AndroidUtilities.runOnUIThread(restoreDrawSelector, 450);
            lastLongSelectedView = null;
            invalidate();
            longTouchEnd();
        }

        private void longTouchStart() {
            animatorIsScaled.setValue(true, true);

            /*
            if (!scaleX.isRunning()) {
                scaleX.setStartVelocity(-0.45f);
                scaleY.setStartVelocity(-0.45f);
            }
            scaleX.animateToFinalPosition(1.012f);
            scaleY.animateToFinalPosition(1.012f);
            */
        }

        private void longTouchEnd() {
            animatorIsScaled.setValue(false, true);

            /*
            if (!scaleX.isRunning()) {
                scaleX.setStartVelocity(0.25f);
                scaleY.setStartVelocity(0.25f);
            }
            scaleX.animateToFinalPosition(1f);
            scaleY.animateToFinalPosition(1f);
            */
        }
    });

    @Override
    public void setScaleY(float scaleY) {
        super.setScaleY(scaleY);
        checkLayerType();
    }

    @Override
    public void setScaleX(float scaleX) {
        super.setScaleX(scaleX);
        checkLayerType();
    }

    private void checkLayerType() {
        final int layerType = Math.abs(getScaleX() - 1f) < 0.0001f && Math.abs(getScaleY() - 1f) < 0.0001f ?
            View.LAYER_TYPE_NONE : View.LAYER_TYPE_HARDWARE;

        if (getLayerType() != layerType) {
            setLayerType(layerType, null);
            invalidate();
        }
    }


    private void checkPivot(View view, float x, float y) {
        float w = view.getWidth();
        float h = view.getHeight();

        if (w <= 0f || h <= 0f) {
            return;
        }

        float cx = w * 0.5f;
        float cy = h * 0.5f;

        float dx = x - cx;
        float dy = y - cy;

        float halfW = w * 0.5f;
        float halfH = h * 0.5f;

        float nx = dx / halfW;
        float ny = dy / halfH;

        float r = (float) Math.sqrt(nx * nx + ny * ny);

        float pivotX;
        float pivotY;

        if (r > 1e-4f) {
            float mappedR = 1.5f * r / (r + 0.5f);

            float scale = mappedR / r;
            pivotX = cx + dx * scale;
            pivotY = cy + dy * scale;
        } else {
            pivotX = cx;
            pivotY = cy;
        }

        pivotX = lerp(cx, pivotX, 1f);
        pivotY = lerp(cy, pivotY, 3f);

        view.setPivotX(pivotX);
        view.setPivotY(pivotY);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (isExpressive()) {
            // Старая стеклянная капля по долгому нажатию доку не нужна — у него своя.
            if (handleExpressiveTouch(ev)) {
                return true;
            }
            final boolean handled = super.dispatchTouchEvent(ev);
            // «Откуда угодно» — жест может начаться и мимо вкладок, на подложке.
            return handled || ev.getActionMasked() == MotionEvent.ACTION_DOWN && expressiveCandidate;
        }
        clickHelper.onTouchEvent(this, ev);
        return super.dispatchTouchEvent(ev);
    }

    // ---- Expressive-док: капля ----

    private boolean expressiveCandidate;
    private boolean expressiveDragging;
    private boolean expressiveSettling;
    private float expressiveDownX, expressiveDownY;
    private long expressiveDownTime;
    private float expressiveFingerX;
    private float expressiveVelocity;
    private View expressiveStartTab;
    private View expressiveHoverTab;

    private float expressiveReleaseCenterX, expressiveReleaseWidth, expressiveReleaseStretch;
    private float expressiveSettleProgress;
    private final android.graphics.RectF expressiveDropletRect = new android.graphics.RectF();
    private final Paint expressivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Подъём капли: пока палец на доке, капля чуть больше и с тенью. */
    private final BoolAnimator expressiveLift = new BoolAnimator(0, (id, factor, fraction, callee) -> invalidate(),
            CubicBezierInterpolator.EASE_OUT_QUINT, 220L);

    private final SpringAnimation expressiveSettle = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("expressiveSettle") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.expressiveSettleProgress * 1000f;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.expressiveSettleProgress = value / 1000f;
            object.invalidate();
        }
    });

    {
        expressiveSettle.setSpring(new SpringForce(1000f).setDampingRatio(0.76f).setStiffness(380f));
        expressiveSettle.addEndListener((animation, canceled, value, velocity) -> {
            if (!canceled) {
                expressiveSettling = false;
                setChildrenSkipDrawSelector(false);
                invalidate();
            }
        });
    }

    private void setChildrenSkipDrawSelector(boolean skip) {
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setSkipDrawSelector(skip);
            }
        }
    }

    private float getExpressiveTabHeight() {
        return getHeight() - getPaddingTop() - getPaddingBottom();
    }

    /** Ширина капли во время перетаскивания — чуть шире вкладки без подписи, как 68dp в Mesh. */
    private float getExpressiveDropletWidth() {
        return (dp(ExpressiveDock.iconSize()) + AndroidUtilities.dpf2(ExpressiveDock.tabPadding()) * 2) * 1.45f;
    }

    /** Видимые вкладки в порядке слева направо. */
    private ArrayList<View> getExpressiveVisibleTabs() {
        final ArrayList<View> list = new ArrayList<>();
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child.getVisibility() == VISIBLE && isViewVisible(child)) {
                list.add(child);
            }
        }
        java.util.Collections.sort(list, (x, y) -> Integer.compare(x.getLeft(), y.getLeft()));
        return list;
    }

    /**
     * Вкладка под пальцем по равным «слотам», как в Mesh: ширины вкладок меняются прямо во
     * время жеста, и выбор по их центрам дрожал бы на границе.
     */
    private View findExpressiveSlot(float x) {
        final ArrayList<View> tabs = getExpressiveVisibleTabs();
        if (tabs.isEmpty()) {
            return null;
        }
        final float content = getWidth() - getPaddingLeft() - getPaddingRight();
        if (content <= 0) {
            return tabs.get(0);
        }
        final int index = (int) ((x - getPaddingLeft()) / (content / tabs.size()));
        return tabs.get(Math.max(0, Math.min(tabs.size() - 1, index)));
    }

    private View findSelectedVisibleTab() {
        for (View tab : getExpressiveVisibleTabs()) {
            if (tab instanceof GlassTabView && ((GlassTabView) tab).isTabSelected()) {
                return tab;
            }
        }
        return null;
    }

    private boolean handleExpressiveTouch(MotionEvent ev) {
        final int swipe = ExpressiveDock.swipe();
        if (swipe == ExpressiveDock.SWIPE_OFF && !expressiveDragging) {
            expressiveCandidate = false;
            return false;
        }
        final float x = ev.getX();
        final float y = ev.getY();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                expressiveDragging = false;
                expressiveDownX = x;
                expressiveDownY = y;
                expressiveDownTime = ev.getEventTime();
                if (swipe == ExpressiveDock.SWIPE_ANYWHERE) {
                    expressiveCandidate = true;
                } else {
                    final View selected = findSelectedVisibleTab();
                    final float slack = dp(12);
                    expressiveCandidate = selected != null
                            && x >= selected.getX() - slack
                            && x <= selected.getX() + getTabVisualWidth(selected) + slack;
                }
                return false;
            }
            case MotionEvent.ACTION_MOVE: {
                if (expressiveDragging) {
                    moveExpressiveDrag(x);
                    return true;
                }
                if (!expressiveCandidate) {
                    return false;
                }
                // Долгое нажатие уже ушло вкладке (меню папок, аккаунтов) — не перехватываем.
                if (ev.getEventTime() - expressiveDownTime > ViewConfiguration.getLongPressTimeout()) {
                    expressiveCandidate = false;
                    return false;
                }
                final float dx = x - expressiveDownX;
                final float dy = y - expressiveDownY;
                final int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) {
                    expressiveCandidate = false;
                    return false;
                }
                if (Math.abs(dx) > slop) {
                    final MotionEvent cancel = MotionEvent.obtain(ev);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    startExpressiveDrag(x);
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                expressiveCandidate = false;
                if (expressiveDragging) {
                    finishExpressiveDrag(ev.getActionMasked() == MotionEvent.ACTION_UP);
                    return true;
                }
                return false;
            }
        }
        return expressiveDragging;
    }

    private static float getTabVisualWidth(View view) {
        return view instanceof GlassTabView ? ((GlassTabView) view).getVisualWidth() : view.getWidth();
    }

    private void startExpressiveDrag(float x) {
        expressiveSettle.cancel();
        expressiveSettling = false;
        expressiveDragging = true;
        expressiveFingerX = x;
        expressiveVelocity = 0;
        expressiveStartTab = findSelectedVisibleTab();
        expressiveHoverTab = expressiveStartTab;
        setChildrenSkipDrawSelector(true);
        expressiveLift.setValue(true, true);
        performExpressiveHaptic(HapticFeedbackConstants.LONG_PRESS);
        moveExpressiveDrag(x);
    }

    private void moveExpressiveDrag(float x) {
        final float delta = x - expressiveFingerX;
        expressiveFingerX = x;
        // Сглаженная скорость — по ней капля растягивается.
        expressiveVelocity = expressiveVelocity * 0.6f + delta * 0.4f;

        final View found = findExpressiveSlot(x);
        if (found != null && found != expressiveHoverTab) {
            expressiveHoverTab = found;
            performExpressiveHaptic(HapticFeedbackConstants.CLOCK_TICK);
            if (ExpressiveDock.liveSwitch()) {
                found.performClick();
            }
            setTabSelected(found, true);
        }
        invalidate();
    }

    private void finishExpressiveDrag(boolean commit) {
        expressiveDragging = false;
        // С переключением на лету страница уже там, где палец, — откатывать нечего.
        final View target = commit || ExpressiveDock.liveSwitch() ? expressiveHoverTab : expressiveStartTab;
        if (target != null) {
            setTabSelected(target, true);
            if (commit && target != expressiveStartTab && !ExpressiveDock.liveSwitch()) {
                target.performClick();
            }
        }
        expressiveStartTab = null;
        expressiveHoverTab = null;

        expressiveReleaseCenterX = getExpressiveDropletCenterX();
        expressiveReleaseWidth = getExpressiveDropletWidth();
        expressiveReleaseStretch = getExpressiveStretch();
        expressiveVelocity = 0;
        expressiveLift.setValue(false, true);

        expressiveSettling = true;
        expressiveSettleProgress = 0;
        expressiveSettle.getSpring().setStiffness(ExpressiveDock.dropletStiffness());
        expressiveSettle.setStartValue(0);
        expressiveSettle.animateToFinalPosition(1000f);
        invalidate();
    }

    private float getExpressiveDropletCenterX() {
        final float half = getExpressiveDropletWidth() / 2f;
        final float min = getPaddingLeft() + half;
        final float max = getWidth() - getPaddingRight() - half;
        return max < min ? getWidth() / 2f : Math.max(min, Math.min(max, expressiveFingerX));
    }

    private float getExpressiveStretch() {
        if (!ExpressiveDock.liquid()) {
            return 1f;
        }
        return 1f + Math.abs(Math.max(-20f, Math.min(20f, expressiveVelocity))) / 90f;
    }

    private void performExpressiveHaptic(int constant) {
        if (ExpressiveDock.haptics() && !NekoConfig.disableVibration.Bool()) {
            try {
                performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * Капля: пока палец на доке — фиксированной ширины у пальца, приподнята (×1.12) и
     * растянута по скорости; после отпускания пружиной перетекает в пилюлю выбранной вкладки.
     */
    private void drawExpressiveDroplet(Canvas canvas) {
        final float tabHeight = getExpressiveTabHeight();
        if (tabHeight <= 0) {
            return;
        }
        float cx;
        float width;
        float stretch;
        if (expressiveDragging) {
            cx = getExpressiveDropletCenterX();
            width = getExpressiveDropletWidth();
            stretch = getExpressiveStretch();
        } else {
            final View target = findSelectedVisibleTab();
            final float inset = dp(ExpressiveDock.spacing()) / 2f;
            final float targetWidth = target != null ? getTabVisualWidth(target) - inset * 2 : expressiveReleaseWidth;
            final float targetCx = target != null ? target.getX() + getTabVisualWidth(target) / 2f : expressiveReleaseCenterX;
            final float p = expressiveSettleProgress;
            cx = lerp(expressiveReleaseCenterX, targetCx, p);
            width = Math.max(0, lerp(expressiveReleaseWidth, targetWidth, p));
            stretch = lerp(expressiveReleaseStretch, 1f, Math.min(1f, p));
        }
        final float lift = expressiveLift.getFloatValue();
        final float scale = lerp(1f, 1.12f, lift);
        final float sx = scale * stretch;
        final float sy = scale / (float) Math.sqrt(stretch);
        final float top = getPaddingTop();
        expressiveDropletRect.set(cx - width / 2f, top, cx + width / 2f, top + tabHeight);

        canvas.save();
        canvas.scale(sx, sy, expressiveDropletRect.centerX(), expressiveDropletRect.centerY());
        final float r = ExpressiveDock.pillRadius(expressiveDropletRect.height());
        expressivePaint.setColor(ExpressiveDock.indicatorColor(resourcesProvider));
        if (lift > 0) {
            expressivePaint.setShadowLayer(dp(8) * lift, 0, dp(2) * lift, Theme.multAlpha(0xFF000000, 0.18f * lift));
        } else {
            expressivePaint.clearShadowLayer();
        }
        canvas.drawRoundRect(expressiveDropletRect, r, r, expressivePaint);
        canvas.restore();
    }


    private static float clampXToChildrenCenters(float x, ViewGroup parent) {
        if (parent == null || parent.getChildCount() == 0) {
            return x;
        }

        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        boolean found = false;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }

            float centerX = view.getX() + view.getWidth() * 0.5f;

            if (centerX < min) min = centerX;
            if (centerX > max) max = centerX;

            found = true;
        }

        if (!found) {
            return x;
        }

        if (x < min) return min;
        if (x > max) return max;
        return x;
    }

    @Nullable
    private static View findNearestVisibleChildByX(float x, ViewGroup parent) {
        if (parent == null || parent.getChildCount() == 0) {
            return null;
        }

        View nearest = null;
        float nearestDistance = Float.MAX_VALUE;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }

            float centerX = view.getX() + view.getWidth() * 0.5f;
            float distance = Math.abs(centerX - x);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = view;
            }
        }

        return nearest;
    }

    private static float getInterpolatedWidthByX(float x, ViewGroup parent) {
        if (parent == null || parent.getChildCount() == 0) {
            return 0f;
        }

        View left = null;
        View right = null;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }

            float centerX = view.getX() + view.getWidth() * 0.5f;

            if (centerX <= x && (left == null || centerX > getCenterX(left))) {
                left = view;
            }

            if (centerX >= x && (right == null || centerX < getCenterX(right))) {
                right = view;
            }
        }

        if (left == null && right == null) {
            return 0f;
        }

        if (left == null) {
            return right.getWidth();
        }

        if (right == null) {
            return left.getWidth();
        }

        float leftX = getCenterX(left);
        float rightX = getCenterX(right);

        if (left == right || leftX == rightX) {
            return left.getWidth();
        }

        float ratio = (x - leftX) / (rightX - leftX);
        return lerp(left.getWidth(), right.getWidth(), ratio);
    }

    private static float getCenterX(View v) {
        return v.getX() + v.getWidth() * 0.5f;
    }
}
