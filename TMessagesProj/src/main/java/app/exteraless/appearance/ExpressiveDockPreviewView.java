package app.exteraless.appearance;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.glass.GlassTabView;
import org.telegram.ui.MainTabsLayout;

/**
 * Живое превью Expressive-дока в настройках: настоящий MainTabsLayout с настоящими
 * вкладками, поэтому работают и нажатия, и свайп с каплей. После каждой правки
 * настроек док пересобирается через {@link #rebuild()}.
 */
@SuppressLint("ViewConstructor")
public class ExpressiveDockPreviewView extends FrameLayout {

    private static final int PREVIEW_HEIGHT_DP = 168;

    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int selectedIndex;

    public ExpressiveDockPreviewView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);
        setClipChildren(false);
        rebuild();
    }

    public void rebuild() {
        removeAllViews();

        final MainTabsLayout tabsView = new MainTabsLayout(getContext(), resourcesProvider);
        tabsView.setClipChildren(false);
        tabsView.setDrawTopDivider(false);
        MainTabsUiHelper.applyTabsLayoutStyle(tabsView, 0);
        tabsView.setBackground(new DockBackground(resourcesProvider));

        final GlassTabView[] tabs = {
                GlassTabView.createMainTab(getContext(), resourcesProvider, GlassTabView.TabAnimation.CHATS, R.string.MainTabsChats),
                GlassTabView.createMainTab(getContext(), resourcesProvider, GlassTabView.TabAnimation.CONTACTS, R.string.MainTabsContacts),
                GlassTabView.createMainTab(getContext(), resourcesProvider, GlassTabView.TabAnimation.CALLS, R.string.MainTabsCalls),
                GlassTabView.createMainTab(getContext(), resourcesProvider, GlassTabView.TabAnimation.SETTINGS, R.string.Settings),
                GlassTabView.createAvatar(getContext(), resourcesProvider, UserConfig.selectedAccount, R.string.MainTabsProfile),
        };
        for (int i = 0; i < tabs.length; i++) {
            final GlassTabView tab = tabs[i];
            final int index = i;
            tab.setOnClickListener(v -> {
                selectedIndex = index;
                tabsView.setTabSelected(v, true);
            });
            tabsView.addView(tab);
            tabsView.setViewVisible(tab, true, false);
            tab.setSelected(i == selectedIndex, false);
        }

        final int side = Math.max(0, ExpressiveDock.sideMargin() - ExpressiveDock.margin());
        addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, MainTabsUiHelper.getTabsViewHeightDp(),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, side, 0, side, 0));
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(PREVIEW_HEIGHT_DP), MeasureSpec.EXACTLY));
    }

    /** Заглушка списка чатов под доком — чтобы было видно прозрачность и тень. */
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        final int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        final float left = dp(21);
        float top = dp(16);
        for (int i = 0; i < 4; i++) {
            placeholderPaint.setColor(Theme.multAlpha(text, 0.08f));
            canvas.drawCircle(left + dp(20), top + dp(20), dp(20), placeholderPaint);
            placeholderPaint.setColor(Theme.multAlpha(text, 0.10f));
            rect.set(left + dp(52), top + dp(8), getWidth() * 0.62f, top + dp(18));
            canvas.drawRoundRect(rect, dp(5), dp(5), placeholderPaint);
            placeholderPaint.setColor(Theme.multAlpha(text, 0.06f));
            rect.set(left + dp(52), top + dp(24), getWidth() - left, top + dp(33));
            canvas.drawRoundRect(rect, dp(5), dp(5), placeholderPaint);
            top += dp(52);
        }
    }

    /**
     * Подложка превью. Настоящая — стеклянная и требует источник размытия, которого в
     * настройках нет, поэтому здесь рисуется та же капсула теми же цветами без блюра.
     */
    private static class DockBackground extends Drawable {
        private final Theme.ResourcesProvider resourcesProvider;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();

        DockBackground(Theme.ResourcesProvider resourcesProvider) {
            this.resourcesProvider = resourcesProvider;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(AndroidUtilities.dpf2(1));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final int inset = MainTabsUiHelper.getBackgroundInset();
            rect.set(getBounds());
            rect.inset(inset, inset);
            final float r = Math.min(MainTabsUiHelper.getBackgroundRadius(), rect.height() / 2f);
            fill.setColor(ExpressiveDock.backgroundColor(resourcesProvider));
            if (ExpressiveDock.shadow()) {
                fill.setShadowLayer(dp(8), 0, dp(2), 0x30000000);
            } else {
                fill.clearShadowLayer();
            }
            canvas.drawRoundRect(rect, r, r, fill);
            if (ExpressiveDock.outline()) {
                stroke.setColor(ExpressiveDock.outlineColor(resourcesProvider));
                canvas.drawRoundRect(rect, r, r, stroke);
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
