package app.exteraless.appearance;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Color;
import android.view.animation.Interpolator;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

import tw.nekomimi.nekogram.config.ConfigItem;

/**
 * Верхняя плашка папок в стиле M3 Expressive.
 *
 * Островок остаётся стеклянным, меняется то, что внутри: выбранная вкладка — тональная
 * пилюля под текстом (а не полупрозрачная заливка поверх), которая при переходе тянется
 * за направлением движения, сплющивается и доезжает с пружинным перелётом. Неактивные
 * вкладки по желанию лежат на своих чипах. Всё настраивается; при выключенном флаге
 * FilterTabsView ведёт себя как раньше.
 */
public final class ExpressiveTabs {

    private ExpressiveTabs() {
    }

    public static final int PILL_HEIGHT_MIN = 22, PILL_HEIGHT_MAX = 34;

    public static final int INDICATOR_TONAL = 0;
    public static final int INDICATOR_ACCENT = 1;
    public static final int INDICATOR_GLASS = 2;

    public static final int SPEED_SLOW = 0;
    public static final int SPEED_NORMAL = 1;
    public static final int SPEED_FAST = 2;

    /** Высота островка без внешних отступов, dp — та же, что задаёт DialogsActivity. */
    public static final float ISLAND_INNER_HEIGHT_DP = 32f;
    /** Разница между подложкой островка и обрезкой его содержимого, dp. */
    public static final float ISLAND_BACKGROUND_OUTSET_DP = 2.333f;

    private static final Interpolator SPRING = new CubicBezierInterpolator(0.34, 1.32, 0.64, 1);

    private static int intOf(ConfigItem item, int min, int max) {
        AppearanceConfig.ensureLoaded();
        return Math.max(min, Math.min(max, item.Int()));
    }

    private static boolean boolOf(ConfigItem item) {
        AppearanceConfig.ensureLoaded();
        return item.Bool();
    }

    public static boolean enabled() {
        return AppearanceConfig.expressiveTabs();
    }

    public static int pillHeightDp() {
        return intOf(AppearanceConfig.expressiveTabsPillHeight, PILL_HEIGHT_MIN, PILL_HEIGHT_MAX);
    }

    public static float pillRadius(float height) {
        return height / 2f * intOf(AppearanceConfig.expressiveTabsPillRoundness, 0, 100) / 100f;
    }

    /** Скругление обрезки содержимого островка, px. */
    public static float islandClipRadius() {
        return dp(ISLAND_INNER_HEIGHT_DP / 2f) * intOf(AppearanceConfig.expressiveTabsIslandRoundness, 0, 100) / 100f;
    }

    /** Скругление стеклянной подложки островка, px: чуть больше обрезки, она шире на отступ. */
    public static float islandBackgroundRadius() {
        return islandClipRadius() + AndroidUtilities.dpf2(ISLAND_BACKGROUND_OUTSET_DP);
    }

    public static int indicator() {
        return intOf(AppearanceConfig.expressiveTabsIndicator, INDICATOR_TONAL, INDICATOR_GLASS);
    }

    public static boolean chips() {
        return boolOf(AppearanceConfig.expressiveTabsChips);
    }

    public static boolean stretch() {
        return boolOf(AppearanceConfig.expressiveTabsStretch);
    }

    public static boolean spring() {
        return boolOf(AppearanceConfig.expressiveTabsSpring);
    }

    public static boolean pressBounce() {
        return boolOf(AppearanceConfig.expressiveTabsPressBounce);
    }

    /** Длительность перехода по нажатию, мс. */
    public static float durationMs() {
        switch (intOf(AppearanceConfig.expressiveTabsSpeed, SPEED_SLOW, SPEED_FAST)) {
            case SPEED_SLOW:
                return 480f;
            case SPEED_FAST:
                return 220f;
            default:
                return 340f;
        }
    }

    /**
     * Ход пилюли при переходе по нажатию. С пружиной она проскакивает цель и
     * возвращается; на страницы и цвета это не влияет — им остаётся обычный прогресс.
     */
    public static float springProgress(float time) {
        final float t = Math.max(0f, Math.min(1f, time));
        return spring() ? SPRING.getInterpolation(t) : CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(t);
    }

    /** Передний край пилюли: уходит вперёд быстрее. */
    public static float leadingProgress(float p) {
        if (!stretch()) {
            return p;
        }
        final float c = Math.max(0f, Math.min(1f, p));
        final float lead = 1f - (1f - c) * (1f - c);
        return p > 1f || p < 0f ? p : lead;
    }

    /** Задний край пилюли: подтягивается позже. */
    public static float trailingProgress(float p) {
        if (!stretch()) {
            return p;
        }
        final float c = Math.max(0f, Math.min(1f, p));
        return p > 1f || p < 0f ? p : c * c;
    }

    // ---- цвета ----

    private static boolean isDark(Theme.ResourcesProvider resourcesProvider) {
        return resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
    }

    public static int accent(Theme.ResourcesProvider resourcesProvider) {
        return Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
    }

    /** Цвет пилюли выбранной вкладки. */
    public static int indicatorColor(Theme.ResourcesProvider resourcesProvider) {
        final int accent = accent(resourcesProvider);
        switch (indicator()) {
            case INDICATOR_ACCENT:
                return accent;
            case INDICATOR_GLASS:
                return Theme.multAlpha(accent, isDark(resourcesProvider) ? 0.20f : 0.12f);
            default:
                final int bg = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
                return ColorUtils.blendARGB(bg, accent, isDark(resourcesProvider) ? 0.34f : 0.20f) | 0xFF000000;
        }
    }

    /** Текст и иконка на пилюле. */
    public static int selectedContentColor(Theme.ResourcesProvider resourcesProvider) {
        if (indicator() == INDICATOR_ACCENT) {
            return AndroidUtilities.computePerceivedBrightness(accent(resourcesProvider)) > 0.721f
                    ? Color.BLACK : Color.WHITE;
        }
        return accent(resourcesProvider);
    }

    /** Подложка неактивной вкладки. */
    public static int chipColor(Theme.ResourcesProvider resourcesProvider) {
        final int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        return Theme.multAlpha(text, isDark(resourcesProvider) ? 0.08f : 0.05f);
    }
}
