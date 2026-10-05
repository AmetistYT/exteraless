package app.exteraless.appearance;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;

import java.util.WeakHashMap;

import tw.nekomimi.nekogram.config.ConfigItem;

/**
 * Экран чата в стиле Material 3 Expressive.
 *
 * Каждая зона (шапка, пузыри, сервисные плашки, ответы, голосовые, реакции, круглые
 * кнопки, поле ввода, панель эмодзи, меню сообщения, выделение) включается отдельно,
 * но только при включённом общем флаге. Здесь только чтение настроек, цвета и
 * мелкие помощники для отрисовки; сами хуки стоят в штатных классах Telegram и
 * при выключенном флаге оставляют их поведение прежним.
 */
public final class ExpressiveChat {

    private ExpressiveChat() {
    }

    public static final int BUBBLE_RADIUS_MIN = 6, BUBBLE_RADIUS_MAX = 28;
    public static final int NEAR_RADIUS_MAX = 16;
    public static final int MENU_RADIUS_MIN = 8, MENU_RADIUS_MAX = 32;

    /** Скругление плавающих панелей (закреп, плашки над полем ввода), dp. */
    public static final int PANEL_RADIUS_DP = 22;
    /** Доля стороны, которая уходит в скругление «квадратных» кнопок: 44dp → 14dp. */
    public static final float BUTTON_ROUNDNESS = 0.32f;

    /**
     * Растёт при каждой правке формы пузырей: кэши путей в MessageDrawable сверяются с ним
     * и пересобираются, иначе новое скругление появилось бы только после прокрутки.
     */
    private static volatile int shapeVersion;

    public static int shapeVersion() {
        return shapeVersion;
    }

    public static void bumpShapeVersion() {
        shapeVersion++;
    }

    private static boolean zone(ConfigItem item) {
        return AppearanceConfig.expressiveChat() && item.Bool();
    }

    private static int intOf(ConfigItem item, int min, int max) {
        AppearanceConfig.ensureLoaded();
        return Math.max(min, Math.min(max, item.Int()));
    }

    // ---- зоны ----

    public static boolean enabled() {
        return AppearanceConfig.expressiveChat();
    }

    public static boolean header() {
        return zone(AppearanceConfig.expressiveChatHeader);
    }

    public static boolean panels() {
        return zone(AppearanceConfig.expressiveChatPanels);
    }

    public static boolean bubbles() {
        return zone(AppearanceConfig.expressiveChatBubbles);
    }

    public static boolean servicePills() {
        return zone(AppearanceConfig.expressiveChatServicePills);
    }

    public static boolean replies() {
        return zone(AppearanceConfig.expressiveChatReplies);
    }

    public static boolean voice() {
        return zone(AppearanceConfig.expressiveChatVoice);
    }

    public static boolean reactions() {
        return zone(AppearanceConfig.expressiveChatReactions);
    }

    public static boolean buttons() {
        return zone(AppearanceConfig.expressiveChatButtons);
    }

    public static boolean input() {
        return zone(AppearanceConfig.expressiveChatInput);
    }

    public static boolean emojiPanel() {
        return zone(AppearanceConfig.expressiveChatEmojiPanel);
    }

    public static boolean menu() {
        return zone(AppearanceConfig.expressiveChatMenu);
    }

    public static boolean selection() {
        return zone(AppearanceConfig.expressiveChatSelection);
    }

    // ---- размеры ----

    public static int bubbleRadiusDp() {
        return intOf(AppearanceConfig.expressiveChatBubbleRadius, BUBBLE_RADIUS_MIN, BUBBLE_RADIUS_MAX);
    }

    /** Скругление на стыке не больше внешнего, иначе группа выглядит наоборот. */
    public static int bubbleNearRadiusDp() {
        return Math.min(bubbleRadiusDp(), intOf(AppearanceConfig.expressiveChatBubbleNearRadius, 0, NEAR_RADIUS_MAX));
    }

    public static int menuRadiusDp() {
        return intOf(AppearanceConfig.expressiveChatMenuRadius, MENU_RADIUS_MIN, MENU_RADIUS_MAX);
    }

    /** Радиус плавающих панелей с поправкой на их высоту: низкая панель остаётся пилюлей. */
    public static float panelRadius(float height) {
        return Math.min(dp(PANEL_RADIUS_DP), height / 2f);
    }

    /** Радиус круглой кнопки размером [sizePx]: в доке это скруглённый квадрат. */
    public static float buttonRadius(float sizePx) {
        return sizePx * BUTTON_ROUNDNESS;
    }

    /**
     * Реакция: обычная — пилюля, выбранная сжимается в скруглённый квадрат, как
     * переключаемая кнопка в M3 Expressive.
     */
    public static float reactionRadius(float height, boolean selected, float fallback) {
        if (!reactions()) {
            return fallback;
        }
        return selected ? height * 0.3f : height / 2f;
    }

    // ---- цвета ----

    public static int accent(Theme.ResourcesProvider resourcesProvider) {
        return Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
    }

    private static boolean isDark(Theme.ResourcesProvider resourcesProvider) {
        return resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
    }

    /** Непрозрачная тональная поверхность: фон окна, чуть подкрашенный акцентом. */
    public static int surfaceColor(Theme.ResourcesProvider resourcesProvider) {
        final int bg = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        return ColorUtils.blendARGB(bg, accent(resourcesProvider), isDark(resourcesProvider) ? 0.10f : 0.06f) | 0xFF000000;
    }

    /** primaryContainer: подложка выбранного элемента поверх стекла. */
    public static int tonalSelectorColor(Theme.ResourcesProvider resourcesProvider) {
        return Theme.multAlpha(accent(resourcesProvider), isDark(resourcesProvider) ? 0.26f : 0.16f);
    }

    public static int onSurfaceVariant(Theme.ResourcesProvider resourcesProvider) {
        final int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        return ColorUtils.blendARGB(text, surfaceColor(resourcesProvider), 0.22f) | 0xFF000000;
    }

    // ---- сервисные плашки ----

    private static final Paint servicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint transparentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final WeakHashMap<TextPaint, TextPaint> serviceTextPaints = new WeakHashMap<>();

    static {
        transparentPaint.setColor(Color.TRANSPARENT);
    }

    /** Подложка сервисной плашки. Один экземпляр на всё приложение: цвет обновляется при каждом запросе. */
    public static Paint serviceBackgroundPaint(Theme.ResourcesProvider resourcesProvider) {
        final int color = surfaceColor(resourcesProvider);
        if (servicePaint.getColor() != color) {
            servicePaint.setColor(color);
        }
        return servicePaint;
    }

    /** Вместо «затемнения» стеклянной плашки — ничего. */
    public static Paint serviceDarkenPaint() {
        transparentPaint.setAlpha(0);
        return transparentPaint;
    }

    /**
     * Текст сервисной плашки на непрозрачной подложке: копия штатной краски со своим цветом.
     * Копия одна на каждую исходную краску, чтобы раскладка текста не пересобиралась на
     * каждом кадре — ChatActionCell сравнивает краски по ссылке.
     */
    public static TextPaint serviceTextPaint(TextPaint source, Theme.ResourcesProvider resourcesProvider) {
        if (source == null) {
            return null;
        }
        TextPaint paint = serviceTextPaints.get(source);
        if (paint == null) {
            paint = new TextPaint(source);
            serviceTextPaints.put(source, paint);
        }
        if (paint.getTextSize() != source.getTextSize()) {
            paint.setTextSize(source.getTextSize());
        }
        if (paint.getTypeface() != source.getTypeface()) {
            paint.setTypeface(source.getTypeface());
        }
        final int color = onSurfaceVariant(resourcesProvider);
        if (paint.getColor() != color) {
            paint.setColor(color);
        }
        paint.linkColor = accent(resourcesProvider);
        return paint;
    }

    // ---- меню сообщения ----

    /**
     * Пункты меню сообщения: подсветка нажатия — скруглённая плашка с отступами, а не
     * прямоугольник во всю ширину. Обходит меню рекурсивно, включая подменю swipe-back.
     */
    public static void styleMenuItems(View view, Theme.ResourcesProvider resourcesProvider) {
        if (view instanceof ActionBarMenuSubItem) {
            final int selector = Theme.getColor(Theme.key_dialogButtonSelector, resourcesProvider);
            final Drawable drawable = Theme.createRadSelectorDrawable(selector, 14, 14);
            view.setBackground(new InsetDrawable(drawable, dp(6), dp(1), dp(6), dp(1)));
        } else if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleMenuItems(group.getChildAt(i), resourcesProvider);
            }
        }
    }

    // ---- выделение ----

    /** Отступы и скругление карточки выделенного сообщения. */
    public static int selectionInsetX() {
        return dp(6);
    }

    public static int selectionInsetY() {
        return dp(1);
    }

    public static float selectionRadius() {
        return dp(18);
    }

    private static final android.graphics.Path selectionPath = new android.graphics.Path();
    private static final android.graphics.RectF selectionRect = new android.graphics.RectF();

    private static void setSelectionRect(float left, float top, float right, float bottom) {
        selectionRect.set(left + selectionInsetX(), top + selectionInsetY(), right - selectionInsetX(), bottom - selectionInsetY());
    }

    /** Подсветка сообщения (переход по ответу, поиск) карточкой. */
    public static void drawSelectionCard(android.graphics.Canvas canvas, float left, float top, float right, float bottom, Paint paint) {
        setSelectionRect(left, top, right, bottom);
        final float r = Math.min(selectionRadius(), selectionRect.height() / 2f);
        canvas.drawRoundRect(selectionRect, r, r, paint);
    }

    /** Обрезка под карточку выделения: дальше штатный код рисует свой фон с волной от касания. */
    public static void clipSelectionCard(android.graphics.Canvas canvas, float left, float top, float right, float bottom) {
        setSelectionRect(left, top, right, bottom);
        final float r = Math.min(selectionRadius(), selectionRect.height() / 2f);
        selectionPath.rewind();
        selectionPath.addRoundRect(selectionRect, r, r, android.graphics.Path.Direction.CW);
        canvas.clipPath(selectionPath);
    }
}
