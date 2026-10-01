package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;

public class LyricsView extends FrameLayout {

    public static final int STATE_LYRICS = 0;
    public static final int STATE_LOADING = 1;
    public static final int STATE_OFFER = 2;
    public static final int STATE_NOT_FOUND = 3;
    public static final int STATE_ERROR = 4;
    public static final int STATE_INSTRUMENTAL = 5;

    public interface Delegate {
        void onAction(int state);

        void onSeek(long ms);
    }

    private final RecyclerListView list;
    private final LinearLayoutManager layoutManager;
    private final Adapter adapter = new Adapter();
    private final LinearLayout empty;
    private final FrameLayout tile;
    private final PlayerIcon tileIcon = PlayerIcon.stroke(PlayerIcon.NOTE, 28);
    private final TextView emptyTitle;
    private final TextView emptyText;
    private final TextView actionButton;
    private final RadialProgressView progress;
    private final GradientDrawable tileBg = new GradientDrawable();
    private final GradientDrawable buttonBg = new GradientDrawable();
    private final Paint fadePaint = new Paint();
    private LinearGradient topFade;
    private LinearGradient bottomFade;
    private Delegate delegate;
    private Lyrics lyrics;
    private int state = -1;
    private int active = -1;
    private long userScrollAt;
    private boolean userDragging;
    private int colorActive;
    private int colorInactive;

    public LyricsView(Context context) {
        super(context);

        list = new RecyclerListView(context);
        layoutManager = new LinearLayoutManager(context);
        list.setLayoutManager(layoutManager);
        list.setAdapter(adapter);
        list.setItemAnimator(null);
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(OVER_SCROLL_NEVER);
        list.setSelectorDrawableColor(0);
        list.setOnItemClickListener((view, position) -> {
            if (lyrics != null && lyrics.synced && position >= 0 && position < lyrics.lines.size() && delegate != null) {
                delegate.onSeek(lyrics.lines.get(position).time);
                userScrollAt = 0;
            }
        });
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                userDragging = newState == RecyclerView.SCROLL_STATE_DRAGGING;
                if (newState != RecyclerView.SCROLL_STATE_IDLE && userDragging) {
                    userScrollAt = SystemClock.elapsedRealtime();
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE && userScrollAt != 0) {
                    userScrollAt = SystemClock.elapsedRealtime();
                }
            }
        });
        addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        empty = new LinearLayout(context);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        tile = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);
                tileIcon.setBounds(0, 0, getWidth(), getHeight());
                tileIcon.draw(canvas);
            }
        };
        tile.setWillNotDraw(false);
        fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        tileBg.setCornerRadius(dp(22));
        tile.setBackground(tileBg);
        empty.addView(tile, LayoutHelper.createLinear(64, 64, Gravity.CENTER_HORIZONTAL));
        progress = new RadialProgressView(context);
        progress.setSize(dp(36));
        empty.addView(progress, LayoutHelper.createLinear(48, 48, Gravity.CENTER_HORIZONTAL));
        emptyTitle = new TextView(context);
        emptyTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        emptyTitle.setTypeface(AndroidUtilities.bold());
        emptyTitle.setGravity(Gravity.CENTER);
        empty.addView(emptyTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));
        emptyText = new TextView(context);
        emptyText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyText.setLineSpacing(0, 1.2f);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setMaxWidth(dp(260));
        empty.addView(emptyText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));
        actionButton = new TextView(context);
        actionButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        actionButton.setTypeface(AndroidUtilities.bold());
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setPadding(dp(20), 0, dp(20), 0);
        buttonBg.setCornerRadius(dp(22));
        actionButton.setBackground(buttonBg);
        actionButton.setOnClickListener(v -> {
            if (delegate != null) {
                delegate.onAction(state);
            }
        });
        empty.addView(actionButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 44, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));
        addView(empty, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 16, 0, 16, 0));
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void setColors(PlayerColors c) {
        colorActive = c.onSurface;
        colorInactive = c.onSurfaceVariant;
        tileBg.setColor(c.secondaryContainer);
        tileIcon.setColor(c.onSecondaryContainer);
        tile.invalidate();
        emptyTitle.setTextColor(c.onSurface);
        emptyText.setTextColor(c.onSurfaceVariant);
        actionButton.setTextColor(c.primary);
        buttonBg.setStroke(dp(1), c.outlineVariant);
        buttonBg.setColor(0);
        progress.setProgressColor(c.primary);
        for (int i = 0; i < list.getChildCount(); i++) {
            list.getChildAt(i).invalidate();
        }
    }

    public boolean canScrollUp() {
        return list.getVisibility() == VISIBLE && list.canScrollVertically(-1);
    }

    public int getState() {
        return state;
    }

    public Lyrics getLyrics() {
        return lyrics;
    }

    public void showState(int newState) {
        state = newState;
        if (newState == STATE_LYRICS) {
            list.setVisibility(VISIBLE);
            empty.setVisibility(GONE);
            return;
        }
        lyrics = null;
        active = -1;
        adapter.notifyDataSetChanged();
        list.setVisibility(GONE);
        empty.setVisibility(VISIBLE);
        boolean loading = newState == STATE_LOADING;
        progress.setVisibility(loading ? VISIBLE : GONE);
        tile.setVisibility(loading ? GONE : VISIBLE);
        String title;
        String text;
        String action = null;
        if (loading) {
            title = null;
            text = LocaleController.getString(R.string.OEPlayerLyricsSearching);
        } else if (newState == STATE_OFFER) {
            title = LocaleController.getString(R.string.OEPlayerNoLyricsTitle);
            text = LocaleController.getString(R.string.OEPlayerNoLyricsText);
            action = LocaleController.getString(R.string.OEPlayerFindLyrics);
        } else if (newState == STATE_NOT_FOUND) {
            title = LocaleController.getString(R.string.OEPlayerLyricsNotFound);
            text = LocaleController.getString(R.string.OEPlayerLyricsNotFoundText);
        } else if (newState == STATE_ERROR) {
            title = LocaleController.getString(R.string.OEPlayerLyricsError);
            text = LocaleController.getString(R.string.OEPlayerLyricsErrorText);
            action = LocaleController.getString(R.string.OEPlayerRetry);
        } else {
            title = LocaleController.getString(R.string.OEPlayerInstrumental);
            text = LocaleController.getString(R.string.OEPlayerInstrumentalText);
        }
        emptyTitle.setText(title);
        emptyTitle.setVisibility(title == null ? GONE : VISIBLE);
        emptyText.setText(text);
        actionButton.setText(action);
        actionButton.setVisibility(action == null ? GONE : VISIBLE);
    }

    public void setLyrics(Lyrics value, long positionMs) {
        lyrics = value;
        state = STATE_LYRICS;
        list.setVisibility(VISIBLE);
        empty.setVisibility(GONE);
        active = value != null ? value.indexAt(positionMs) : -1;
        userScrollAt = 0;
        applyPadding();
        adapter.notifyDataSetChanged();
        list.post(() -> scrollToActive(false));
    }

    public void setPosition(long ms) {
        if (lyrics == null || !lyrics.synced) {
            return;
        }
        int index = lyrics.indexAt(ms);
        if (index == active) {
            return;
        }
        int old = active;
        active = index;
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child instanceof LineView) {
                int pos = list.getChildAdapterPosition(child);
                ((LineView) child).setRole(roleFor(pos), true);
            }
        }
        boolean following = !userDragging && (userScrollAt == 0 || SystemClock.elapsedRealtime() - userScrollAt > 3500);
        if (following) {
            userScrollAt = 0;
            scrollToActive(old >= 0 && Math.abs(index - old) <= 3);
        }
    }

    private int roleFor(int position) {
        if (lyrics == null || !lyrics.synced) {
            return LineView.ROLE_PLAIN;
        }
        if (position == active) {
            return LineView.ROLE_ACTIVE;
        }
        return position < active ? LineView.ROLE_PAST : LineView.ROLE_FUTURE;
    }

    private void scrollToActive(boolean smooth) {
        if (lyrics == null || list.getHeight() == 0) {
            return;
        }
        if (!lyrics.synced) {
            return;
        }
        int target = Math.max(active, 0);
        if (target >= adapter.getItemCount()) {
            return;
        }
        if (smooth && layoutManager.findViewByPosition(target) != null) {
            LinearSmoothScroller scroller = new LinearSmoothScroller(getContext()) {
                @Override
                public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                    return boxStart - viewStart;
                }

                @Override
                protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                    return 400f / displayMetrics.densityDpi;
                }

                @Override
                protected int calculateTimeForDeceleration(int dx) {
                    return Math.max(380, Math.min(700, super.calculateTimeForDeceleration(dx)));
                }
            };
            scroller.setTargetPosition(target);
            layoutManager.startSmoothScroll(scroller);
        } else {
            list.stopScroll();
            layoutManager.scrollToPositionWithOffset(target, 0);
        }
    }

    private void applyPadding() {
        int h = getHeight();
        if (lyrics != null && !lyrics.synced) {
            list.setPadding(0, dp(8), 0, dp(24));
        } else if (h > 0) {
            int offset = Math.min(dp(108), (int) (h * 0.3f));
            list.setPadding(0, offset, 0, Math.max(0, h - offset - dp(72)));
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (list.getVisibility() != VISIBLE || getWidth() == 0 || getHeight() == 0 || topFade == null) {
            super.dispatchDraw(canvas);
            return;
        }
        int w = getWidth();
        int h = getHeight();
        int save = canvas.saveLayer(0, 0, w, h, null);
        super.dispatchDraw(canvas);
        fadePaint.setShader(topFade);
        canvas.drawRect(0, 0, w, dp(32), fadePaint);
        fadePaint.setShader(bottomFade);
        canvas.drawRect(0, h - dp(40), w, h, fadePaint);
        canvas.restoreToCount(save);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        topFade = new LinearGradient(0, 0, 0, dp(32), 0xff000000, 0x00000000, Shader.TileMode.CLAMP);
        bottomFade = new LinearGradient(0, h - dp(40), 0, h, 0x00000000, 0xff000000, Shader.TileMode.CLAMP);
        if (h != oldh) {
            applyPadding();
            list.post(() -> scrollToActive(false));
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return lyrics != null && lyrics.synced;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LineView view = new LineView(parent.getContext());
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            LineView view = (LineView) holder.itemView;
            String text = lyrics.lines.get(position).text;
            view.setText(TextUtils.isEmpty(text) ? "♪" : text, !lyrics.synced);
            view.setRole(roleFor(position), false);
        }

        @Override
        public int getItemCount() {
            return lyrics == null ? 0 : lyrics.lines.size();
        }
    }

    private class LineView extends View {

        static final int ROLE_PAST = 0;
        static final int ROLE_ACTIVE = 1;
        static final int ROLE_FUTURE = 2;
        static final int ROLE_PLAIN = 3;

        private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Spring activeness = new Spring(0, 260f, 1f, 0.004f);
        private final Spring opacity = new Spring(0.72f, 260f, 1f, 0.004f);
        private String text;
        private boolean plain;
        private StaticLayout layout;
        private int layoutWidth;
        private int role = ROLE_FUTURE;
        private long lastFrame;

        LineView(Context context) {
            super(context);
            paint.setTypeface(AndroidUtilities.bold());
        }

        void setText(String value, boolean isPlain) {
            if (!TextUtils.equals(text, value) || plain != isPlain) {
                text = value;
                plain = isPlain;
                layout = null;
                requestLayout();
            }
        }

        void setRole(int value, boolean animated) {
            role = value;
            float targetActive = value == ROLE_ACTIVE || value == ROLE_PLAIN ? 1f : 0f;
            float targetOpacity = value == ROLE_PAST ? 0.4f : value == ROLE_FUTURE ? 0.72f : 1f;
            if (animated) {
                activeness.target = targetActive;
                opacity.target = targetOpacity;
                lastFrame = 0;
            } else {
                activeness.snap(targetActive);
                opacity.snap(targetOpacity);
            }
            invalidate();
        }

        private void ensureLayout(int width) {
            if (layout != null && layoutWidth == width) {
                return;
            }
            layoutWidth = width;
            paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, plain ? 20 : 24, getResources().getDisplayMetrics()));
            layout = StaticLayout.Builder.obtain(text == null ? "" : text, 0, text == null ? 0 : text.length(), paint, Math.max(1, width))
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0, 1.1f)
                    .setIncludePad(false)
                    .build();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            ensureLayout(width);
            int pad = plain ? dp(6) : dp(10);
            setMeasuredDimension(width, layout.getHeight() + pad * 2);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            long now = SystemClock.elapsedRealtime();
            float dt = lastFrame == 0 ? 0.016f : (now - lastFrame) / 1000f;
            lastFrame = now;
            boolean animating = activeness.step(dt);
            animating |= opacity.step(dt);
            ensureLayout(getWidth());
            int color = ColorUtils.blendARGB(colorInactive, colorActive, Math.max(0f, Math.min(1f, activeness.value)));
            paint.setColor(ColorUtils.setAlphaComponent(color, (int) (Math.max(0f, Math.min(1f, opacity.value)) * (color >>> 24))));
            canvas.save();
            canvas.translate(0, plain ? dp(6) : dp(10));
            layout.draw(canvas);
            canvas.restore();
            if (animating) {
                postInvalidateOnAnimation();
            } else {
                lastFrame = 0;
            }
        }
    }
}
