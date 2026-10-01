package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class PlayerMiniView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public static final int HEIGHT_DP = 64;
    private static final float TAU = (float) (Math.PI * 2);
    private static final CubicBezierInterpolator EMPHASIZED = new CubicBezierInterpolator(0.2, 0, 0, 1);

    private final BaseFragment fragment;
    private final Theme.ResourcesProvider resourcesProvider;
    private final boolean dark;
    private final int fallbackSeed;
    private final LinearLayout card;
    private final GradientDrawable cardBg = new GradientDrawable();
    private final CoverImage cover;
    private final TextView titleView;
    private final TextView artistView;
    private final RingButton playButton;
    private final ImageView nextButton;
    private final ImageView closeButton;
    private final PlayerIcon nextIcon = PlayerIcon.fill(PlayerIcon.NEXT, 24);
    private final PlayerIcon closeIcon = PlayerIcon.stroke(PlayerIcon.CLOSE, 22);
    private PlayerColors colors;
    private ValueAnimator colorAnimator;
    private ValueAnimator showAnimator;
    private MessageObject current;
    private String currentKey;
    private boolean shown;
    private float showProgress;
    private float hostFactor = 1f;
    private float baseTranslation;
    private Runnable offsetListener;

    public PlayerMiniView(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.fragment = fragment;
        this.resourcesProvider = resourcesProvider;
        dark = PlayerColors.isDark(resourcesProvider);
        fallbackSeed = PlayerColors.fallbackSeed(resourcesProvider);
        colors = PlayerColors.fromSeed(fallbackSeed, dark);
        setClipChildren(false);
        setClipToPadding(false);

        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), 0, dp(6), 0);
        cardBg.setCornerRadius(dp(20));
        card.setBackground(cardBg);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(20));
            }
        });
        card.setClipToOutline(true);
        card.setElevation(dp(6));
        card.setOnClickListener(v -> Md3Player.open(fragment));
        card.setContentDescription(getString(R.string.OEPlayerOpen));
        addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, HEIGHT_DP, Gravity.BOTTOM, 12, 0, 12, 12));

        cover = new CoverImage(context, 22);
        cover.setRadius(dp(12));
        cover.setListener(fallbackSeed, (mo, seed) -> {
            if (mo == current) {
                animateColors(PlayerColors.fromSeed(seed, dark));
            }
        });
        card.addView(cover, LayoutHelper.createLinear(44, 44));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        artistView = new TextView(context);
        artistView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        artistView.setSingleLine(true);
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        artistView.setAlpha(0.8f);
        texts.addView(artistView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        card.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 4, 0));

        playButton = new RingButton(context);
        playButton.setOnClickListener(v -> {
            MediaController mc = MediaController.getInstance();
            MessageObject mo = mc.getPlayingMessageObject();
            if (mo == null || mc.isDownloadingCurrentMessage()) {
                return;
            }
            if (mc.isMessagePaused()) {
                mc.playMessage(mo);
            } else {
                mc.pauseMessage(mo);
            }
        });
        card.addView(playButton, LayoutHelper.createLinear(48, 48));

        nextButton = new ImageView(context);
        nextButton.setScaleType(ImageView.ScaleType.CENTER);
        nextButton.setImageDrawable(nextIcon);
        nextButton.setContentDescription(getString(R.string.Next));
        nextButton.setOnClickListener(v -> MediaController.getInstance().playNextMessage());
        card.addView(nextButton, LayoutHelper.createLinear(44, 48));

        closeButton = new ImageView(context);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setImageDrawable(closeIcon);
        closeButton.setContentDescription(getString(R.string.AccDescrClosePlayer));
        closeButton.setOnClickListener(v -> MediaController.getInstance().cleanupPlayer(true, true));
        card.addView(closeButton, LayoutHelper.createLinear(44, 48));

        applyColors(colors);
        setVisibility(GONE);
    }

    public void setOffsetListener(Runnable listener) {
        offsetListener = listener;
    }

    public float getVisibleOffset() {
        return (dp(HEIGHT_DP) + dp(8)) * showProgress * hostFactor;
    }

    public int getTargetOffset() {
        return shown && hostFactor > 0.5f ? dp(HEIGHT_DP) + dp(8) : 0;
    }

    public void setHostPosition(float translation, float factor) {
        baseTranslation = translation;
        float old = hostFactor;
        hostFactor = factor;
        applyTransform();
        if (old != factor) {
            notifyOffset();
        }
    }

    private void applyTransform() {
        float p = showProgress * hostFactor;
        setTranslationY(baseTranslation + dp(24) * (1f - p));
        setAlpha(p);
        setVisibility(p > 0f ? VISIBLE : GONE);
    }

    private void notifyOffset() {
        if (offsetListener != null) {
            offsetListener.run();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.addObserver(this, NotificationCenter.messagePlayingDidStart);
            nc.addObserver(this, NotificationCenter.messagePlayingDidReset);
            nc.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            nc.addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            nc.addObserver(this, NotificationCenter.fileLoaded);
        }
        update(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.removeObserver(this, NotificationCenter.messagePlayingDidStart);
            nc.removeObserver(this, NotificationCenter.messagePlayingDidReset);
            nc.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            nc.removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            nc.removeObserver(this, NotificationCenter.fileLoaded);
        }
        if (colorAnimator != null) {
            colorAnimator.cancel();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            playButton.invalidate();
        } else if (id == NotificationCenter.fileLoaded) {
            MessageObject mo = current;
            if (mo != null && mo.getDocument() != null && TextUtils.equals((String) args[0], FileLoader.getAttachFileName(mo.getDocument()))) {
                cover.setMessage(mo);
            }
        } else {
            update(true);
        }
    }

    public void update(boolean animated) {
        MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
        boolean want = Md3Player.miniEnabled() && mo != null && mo.isMusic() && mo.getId() != 0;
        if (want) {
            bind(mo);
        }
        playButton.setPlaying(want && !MediaController.getInstance().isMessagePaused(), animated);
        setShown(want, animated);
    }

    private void bind(MessageObject mo) {
        String key = PlayerArt.key(mo);
        boolean changed = !TextUtils.equals(key, currentKey);
        current = mo;
        currentKey = key;
        titleView.setText(mo.getMusicTitle());
        artistView.setText(mo.getMusicAuthor());
        cover.setMessage(mo);
        if (changed) {
            Integer seed = PlayerArt.cachedSeed(mo);
            if (seed != null) {
                animateColors(PlayerColors.fromSeed(seed, dark));
            } else if (PlayerArt.fileCover(mo) == null && PlayerArt.fullLocation(mo) == null && PlayerArt.thumbLocation(mo) == null) {
                animateColors(PlayerColors.fromSeed(fallbackSeed, dark));
            }
        }
        playButton.setContentDescription(getString(MediaController.getInstance().isMessagePaused() ? R.string.AccActionPlay : R.string.AccActionPause));
    }

    private void setShown(boolean value, boolean animated) {
        if (shown == value && (showAnimator != null || showProgress == (value ? 1f : 0f))) {
            return;
        }
        shown = value;
        if (showAnimator != null) {
            showAnimator.cancel();
            showAnimator = null;
        }
        float target = value ? 1f : 0f;
        if (!animated || !isAttachedToWindow()) {
            showProgress = target;
            applyTransform();
            notifyOffset();
            return;
        }
        showAnimator = ValueAnimator.ofFloat(showProgress, target);
        showAnimator.addUpdateListener(a -> {
            showProgress = (float) a.getAnimatedValue();
            applyTransform();
            notifyOffset();
        });
        showAnimator.setDuration(value ? 450 : 300);
        showAnimator.setInterpolator(value ? new CubicBezierInterpolator(0.05, 0.7, 0.1, 1) : new CubicBezierInterpolator(0.3, 0, 0.8, 0.15));
        showAnimator.start();
    }

    private void animateColors(PlayerColors target) {
        if (colorAnimator != null) {
            colorAnimator.cancel();
        }
        PlayerColors from = colors;
        if (!isAttachedToWindow() || showProgress == 0f) {
            applyColors(target);
            return;
        }
        colorAnimator = ValueAnimator.ofFloat(0f, 1f);
        colorAnimator.addUpdateListener(a -> applyColors(PlayerColors.lerp(from, target, (float) a.getAnimatedValue())));
        colorAnimator.setDuration(600);
        colorAnimator.setInterpolator(EMPHASIZED);
        colorAnimator.start();
    }

    private void applyColors(PlayerColors c) {
        colors = c;
        cardBg.setColor(c.primaryContainer);
        if (Build.VERSION.SDK_INT >= 28) {
            card.setOutlineSpotShadowColor(c.shadow());
            card.setOutlineAmbientShadowColor(c.shadow());
        }
        cover.setColors(c.secondaryContainer, c.onSecondaryContainer);
        titleView.setTextColor(c.onPrimaryContainer);
        artistView.setTextColor(c.onPrimaryContainer);
        nextIcon.setColor(c.onPrimaryContainer);
        closeIcon.setColor(c.onPrimaryContainer);
        int ripple = ColorUtils.setAlphaComponent(c.onPrimaryContainer, 0x1f);
        nextButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        closeButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        playButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        card.setForeground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_ALL));
        playButton.invalidate();
    }

    private class RingButton extends View {

        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final PlayerIcon playIcon = PlayerIcon.fill(PlayerIcon.PLAY, 22);
        private final PlayerIcon pauseIcon = PlayerIcon.fill(PlayerIcon.PAUSE, 22);
        private final Spring alt = new Spring(0, 520f, 0.6f, 0.002f);
        private boolean playing;
        private float amplitude;
        private float phase;
        private long lastFrame;

        RingButton(Context context) {
            super(context);
            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeWidth(dp(3));
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(dp(3));
            ringPaint.setStrokeCap(Paint.Cap.ROUND);
            ringPaint.setStrokeJoin(Paint.Join.ROUND);
        }

        void setPlaying(boolean value, boolean animated) {
            if (playing == value && alt.target == (value ? 1f : 0f)) {
                return;
            }
            playing = value;
            if (animated) {
                alt.target = value ? 1f : 0f;
            } else {
                alt.snap(value ? 1f : 0f);
            }
            lastFrame = 0;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            long now = SystemClock.elapsedRealtime();
            float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
            lastFrame = now;
            boolean animating = alt.step(dt);
            float targetAmp = playing ? AndroidUtilities.dpf2(1.3f) : 0f;
            amplitude += (targetAmp - amplitude) * (1f - (float) Math.pow(0.85, dt / 0.04f));
            if (Math.abs(amplitude - targetAmp) < AndroidUtilities.dpf2(0.03f)) {
                amplitude = targetAmp;
            }
            if (amplitude > 0f) {
                phase = (phase + 6f * dt) % TAU;
            }
            int fg = colors.onPrimaryContainer;
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = dp(20);
            trackPaint.setColor(ColorUtils.setAlphaComponent(fg, 46));
            canvas.drawCircle(cx, cy, r, trackPaint);
            MessageObject mo = current;
            float progress = mo != null ? Math.max(0f, Math.min(1f, mo.audioProgress)) : 0f;
            float sweep = 360f * progress;
            if (sweep > 0.5f) {
                path.rewind();
                boolean first = true;
                for (float deg = 0; ; deg += 3f) {
                    boolean last = deg >= sweep;
                    float d = last ? sweep : deg;
                    double th = Math.toRadians(d - 90);
                    float rr = r + amplitude * (float) Math.sin(12 * Math.toRadians(d) - phase);
                    float x = cx + rr * (float) Math.cos(th);
                    float y = cy + rr * (float) Math.sin(th);
                    if (first) {
                        path.moveTo(x, y);
                        first = false;
                    } else {
                        path.lineTo(x, y);
                    }
                    if (last) {
                        break;
                    }
                }
                ringPaint.setColor(fg);
                canvas.drawPath(path, ringPaint);
            }
            float t = Math.max(0f, Math.min(1f, alt.value));
            drawIcon(canvas, playIcon, fg, 1f - t, 1f - 0.4f * alt.value);
            drawIcon(canvas, pauseIcon, fg, t, 0.6f + 0.4f * alt.value);
            if (animating || amplitude > 0f || targetAmp != amplitude) {
                postInvalidateOnAnimation();
            } else {
                lastFrame = 0;
            }
        }

        private void drawIcon(Canvas canvas, PlayerIcon icon, int color, float alpha, float scale) {
            if (alpha <= 0f) {
                return;
            }
            int size = icon.getIntrinsicWidth();
            int cx = getWidth() / 2;
            int cy = getHeight() / 2;
            icon.setColor(color);
            icon.setAlpha((int) (255 * Math.min(1f, alpha)));
            icon.setBounds(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
            canvas.save();
            canvas.scale(scale, scale, cx, cy);
            icon.draw(canvas);
            canvas.restore();
        }
    }
}
