package app.exteraless.appearance;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Color;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;

import xyz.nextalone.nagram.NaConfig;

/**
 * Expressive-док — нижняя панель из Mesh Expressive (ExpressiveFloatingDock), переложенная
 * на вьюхи Telegram.
 *
 * Капсула висит над контентом, у неактивных вкладок только иконка, активная раскрывается
 * в пилюлю «иконка + подпись». По доку можно вести пальцем: за ним тянется капля, которая
 * растягивается по скорости и на отпускании перетекает в выбранную вкладку.
 *
 * Здесь только чтение настроек и цвета; раскладку делает MainTabsLayout, отрисовку
 * вкладки — GlassTabView. Все значения зажаты в разумные пределы, чтобы битый конфиг
 * (например, из чужого бэкапа) не ломал панель.
 */
public final class ExpressiveDock {

    private ExpressiveDock() {
    }

    /** Предел ширины дока, dp: на планшете капсула во всю ширину выглядит нелепо. */
    public static final int MAX_WIDTH_DP = 600;

    public static final int LABELS_SELECTED = 0;
    public static final int LABELS_ALL = 1;
    public static final int LABELS_NONE = 2;

    public static final int INDICATOR_TONAL = 0;
    public static final int INDICATOR_ACCENT = 1;
    public static final int INDICATOR_GLASS = 2;

    public static final int SWIPE_OFF = 0;
    public static final int SWIPE_FROM_ACTIVE = 1;
    public static final int SWIPE_ANYWHERE = 2;

    public static final int SPEED_SLOW = 0;
    public static final int SPEED_NORMAL = 1;
    public static final int SPEED_FAST = 2;

    public static final int HEIGHT_MIN = 44, HEIGHT_MAX = 80;
    public static final int MARGIN_MAX = 24;
    public static final int SIDE_MARGIN_MAX = 64;
    public static final int INNER_PADDING_MAX = 12;
    public static final int SPACING_MAX = 16;
    public static final int ICON_MIN = 18, ICON_MAX = 32;

    /** Размер подписи, dp — labelMedium из M3, как в Mesh. */
    public static final float LABEL_TEXT_SIZE_DP = 13f;
    /** Зазор между иконкой и подписью, dp. */
    public static final float LABEL_GAP_DP = 6f;
    /** Самая длинная подпись, dp: длиннее — многоточие. */
    public static final float LABEL_MAX_WIDTH_DP = 120f;

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int intOf(tw.nekomimi.nekogram.config.ConfigItem item, int min, int max) {
        AppearanceConfig.ensureLoaded();
        return clamp(item.Int(), min, max);
    }

    private static boolean boolOf(tw.nekomimi.nekogram.config.ConfigItem item) {
        AppearanceConfig.ensureLoaded();
        return item.Bool();
    }

    // ---- размеры ----

    /** Высота капсулы без внешних отступов, dp. */
    public static int height() {
        return intOf(AppearanceConfig.expressiveDockHeight, HEIGHT_MIN, HEIGHT_MAX);
    }

    /** Отступ капсулы снизу и сверху, dp. */
    public static int margin() {
        return intOf(AppearanceConfig.expressiveDockBottomMargin, 0, MARGIN_MAX);
    }

    /** Отступ капсулы от боковых краёв экрана, dp. Не меньше нижнего — подложка его и так держит. */
    public static int sideMargin() {
        return intOf(AppearanceConfig.expressiveDockSideMargin, 0, SIDE_MARGIN_MAX);
    }

    public static int innerPadding() {
        return intOf(AppearanceConfig.expressiveDockInnerPadding, 0, INNER_PADDING_MAX);
    }

    public static int spacing() {
        return intOf(AppearanceConfig.expressiveDockSpacing, 0, SPACING_MAX);
    }

    public static int iconSize() {
        return intOf(AppearanceConfig.expressiveDockIconSize, ICON_MIN, ICON_MAX);
    }

    /** Боковой отступ внутри пилюли, dp: растёт вместе с иконкой. */
    public static float tabPadding() {
        return 10f + (iconSize() - ICON_MIN) * 0.25f;
    }

    public static float roundness() {
        return intOf(AppearanceConfig.expressiveDockRoundness, 0, 100) / 100f;
    }

    /** Радиус самой капсулы, px. */
    public static float dockRadius() {
        return dp(height()) / 2f * roundness();
    }

    /** Радиус пилюли высотой [pillHeight] px: та же доля, что и у капсулы. */
    public static float pillRadius(float pillHeight) {
        return pillHeight / 2f * roundness();
    }

    public static boolean fullWidth() {
        return boolOf(AppearanceConfig.expressiveDockFullWidth);
    }

    /** Режим подписей. Скрытые подписи NagramX тоже гасят их в доке. */
    public static int labels() {
        if (NaConfig.INSTANCE.getMainTabsHideTitles().Bool()) {
            return LABELS_NONE;
        }
        return intOf(AppearanceConfig.expressiveDockLabels, LABELS_SELECTED, LABELS_NONE);
    }

    public static int indicator() {
        return intOf(AppearanceConfig.expressiveDockIndicator, INDICATOR_TONAL, INDICATOR_GLASS);
    }

    public static float opacity() {
        return intOf(AppearanceConfig.expressiveDockOpacity, 0, 100) / 100f;
    }

    public static boolean outline() {
        return boolOf(AppearanceConfig.expressiveDockOutline);
    }

    public static boolean shadow() {
        return boolOf(AppearanceConfig.expressiveDockShadow);
    }

    public static int swipe() {
        return intOf(AppearanceConfig.expressiveDockSwipe, SWIPE_OFF, SWIPE_ANYWHERE);
    }

    public static boolean liveSwitch() {
        return boolOf(AppearanceConfig.expressiveDockLiveSwitch);
    }

    public static boolean liquid() {
        return boolOf(AppearanceConfig.expressiveDockLiquid);
    }

    public static boolean haptics() {
        return boolOf(AppearanceConfig.expressiveDockHaptics);
    }

    /** Длительность перестройки вкладок, мс. */
    public static long animationDuration() {
        switch (intOf(AppearanceConfig.expressiveDockAnimationSpeed, SPEED_SLOW, SPEED_FAST)) {
            case SPEED_SLOW:
                return 600L;
            case SPEED_FAST:
                return 240L;
            default:
                return 400L;
        }
    }

    /** Жёсткость пружины капли: чем быстрее анимации, тем жёстче. */
    public static float dropletStiffness() {
        switch (intOf(AppearanceConfig.expressiveDockAnimationSpeed, SPEED_SLOW, SPEED_FAST)) {
            case SPEED_SLOW:
                return 220f;
            case SPEED_FAST:
                return 700f;
            default:
                return 380f;
        }
    }

    // ---- цвета ----

    private static boolean isDark(Theme.ResourcesProvider resourcesProvider) {
        return AndroidUtilities.computePerceivedBrightness(
                Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)) < 0.5f;
    }

    private static int accent(Theme.ResourcesProvider resourcesProvider) {
        return Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider);
    }

    /** Непрозрачный цвет капсулы — surfaceContainerHigh: фон, чуть подкрашенный акцентом. */
    public static int surfaceColor(Theme.ResourcesProvider resourcesProvider) {
        final int bg = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        return ColorUtils.blendARGB(bg, accent(resourcesProvider), isDark(resourcesProvider) ? 0.08f : 0.05f) | 0xFF000000;
    }

    public static int backgroundColor(Theme.ResourcesProvider resourcesProvider) {
        return Theme.multAlpha(surfaceColor(resourcesProvider), opacity());
    }

    /** Обводка — outlineVariant с прозрачностью 0.4, как в Mesh. */
    public static int outlineColor(Theme.ResourcesProvider resourcesProvider) {
        final int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        return Theme.multAlpha(text, isDark(resourcesProvider) ? 0.16f : 0.10f);
    }

    /** Цвет пилюли активной вкладки. */
    public static int indicatorColor(Theme.ResourcesProvider resourcesProvider) {
        final int accent = accent(resourcesProvider);
        switch (indicator()) {
            case INDICATOR_ACCENT:
                return accent;
            case INDICATOR_GLASS:
                return Theme.multAlpha(accent, 0.12f);
            default:
                // primaryContainer
                return ColorUtils.blendARGB(surfaceColor(resourcesProvider), accent,
                        isDark(resourcesProvider) ? 0.32f : 0.20f);
        }
    }

    /** Цвет иконки и подписи на пилюле. */
    public static int selectedContentColor(Theme.ResourcesProvider resourcesProvider) {
        if (indicator() == INDICATOR_ACCENT) {
            return AndroidUtilities.computePerceivedBrightness(accent(resourcesProvider)) > 0.721f
                    ? Color.BLACK : Color.WHITE;
        }
        return accent(resourcesProvider);
    }

    public static int unselectedContentColor(Theme.ResourcesProvider resourcesProvider) {
        return Theme.getColor(Theme.key_glass_tabUnselected, resourcesProvider);
    }

    /** Фон, обводка и тень капсулы в стеклянной подложке вкладок. */
    public static BlurredBackgroundProviderBuilder applyBackground(BlurredBackgroundProviderBuilder builder) {
        builder.setBackgroundColor((r, isDark) -> backgroundColor(r));
        if (outline()) {
            builder.setStrokeColorTop((r, isDark) -> outlineColor(r))
                    .setStrokeColorBottom((r, isDark) -> outlineColor(r))
                    .setStrokeColorFull((r, isDark) -> outlineColor(r))
                    .setStrokeWidth(dpf2(1), dpf2(1));
        } else {
            builder.setStrokeColorTop(0, 0)
                    .setStrokeColorBottom(0, 0)
                    .setStrokeColorFull(0, 0)
                    .setStrokeWidth(0, 0);
        }
        if (shadow()) {
            builder.setShadowColor(0x30000000, 0x40000000)
                    .setShadowLayer(dpf2(8), 0, dpf2(2));
        } else {
            builder.setShadowColor(0, 0)
                    .setShadowLayer(0, 0, 0);
        }
        return builder;
    }
}
