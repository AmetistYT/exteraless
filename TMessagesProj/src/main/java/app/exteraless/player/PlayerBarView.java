package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.ValueAnimator;
import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class PlayerBarView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public static final int HEIGHT_DP = 52;
    private static final CubicBezierInterpolator EMPHASIZED = new CubicBezierInterpolator(0.2, 0, 0, 1);

    private final boolean dark;
    private final int fallbackSeed;
    private final CoverImage cover;
    private final TextView titleView;
    private final TextView artistView;
    private final RingPlayButton playButton;
    private final ImageView nextButton;
    private final ImageView closeButton;
    private final PlayerIcon nextIcon = PlayerIcon.fill(PlayerIcon.NEXT, 22);
    private final PlayerIcon closeIcon = PlayerIcon.stroke(PlayerIcon.CLOSE, 20);
    private PlayerColors colors;
    private ValueAnimator colorAnimator;
    private MessageObject current;
    private String currentKey;

    public PlayerBarView(Context context, Theme.ResourcesProvider resourcesProvider, Runnable onOpen, Runnable onClose) {
        super(context);
        dark = PlayerColors.isDark(resourcesProvider);
        fallbackSeed = PlayerColors.fallbackSeed(resourcesProvider);
        colors = PlayerColors.fromSeed(fallbackSeed, dark);

        setBackground(Theme.getSelectorDrawable(false));
        setOnClickListener(v -> onOpen.run());
        setContentDescription(getString(R.string.OEPlayerOpen));

        cover = new CoverImage(context, 18);
        cover.setRadius(dp(10));
        cover.setListener(fallbackSeed, (mo, seed) -> {
            if (mo == current) {
                animateColors(PlayerColors.fromSeed(seed, dark));
            }
        });
        addView(cover, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.CENTER_VERTICAL, 12, 0, 0, 0));

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
        texts.addView(artistView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));
        addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 60, 0, 6 + 40 * 3 + 4, 0));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        playButton = new RingPlayButton(context, 15, 2.5f, 16, 1.1f, 10);
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
        buttons.addView(playButton, LayoutHelper.createLinear(40, 40));
        nextButton = new ImageView(context);
        nextButton.setScaleType(ImageView.ScaleType.CENTER);
        nextButton.setImageDrawable(nextIcon);
        nextButton.setContentDescription(getString(R.string.Next));
        nextButton.setOnClickListener(v -> MediaController.getInstance().playNextMessage());
        buttons.addView(nextButton, LayoutHelper.createLinear(40, 40));
        closeButton = new ImageView(context);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setImageDrawable(closeIcon);
        closeButton.setContentDescription(getString(R.string.AccDescrClosePlayer));
        closeButton.setOnClickListener(v -> onClose.run());
        buttons.addView(closeButton, LayoutHelper.createLinear(40, 40));
        addView(buttons, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 40, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 6, 0));

        int text = Theme.getColor(Theme.key_inappPlayerPerformer, resourcesProvider);
        int secondary = Theme.getColor(Theme.key_inappPlayerTitle, resourcesProvider);
        int icons = Theme.getColor(Theme.key_inappPlayerClose, resourcesProvider);
        titleView.setTextColor(text);
        artistView.setTextColor(ColorUtils.setAlphaComponent(secondary, 0xbf));
        nextIcon.setColor(icons);
        closeIcon.setColor(icons);
        int ripple = icons & 0x19ffffff;
        nextButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        closeButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        playButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        applyColors(colors);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.addObserver(this, NotificationCenter.messagePlayingDidStart);
            nc.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            nc.addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            nc.addObserver(this, NotificationCenter.fileLoaded);
        }
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.removeObserver(this, NotificationCenter.messagePlayingDidStart);
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
            update();
        }
    }

    public void update() {
        MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
        if (mo == null || !mo.isMusic()) {
            return;
        }
        String key = PlayerArt.key(mo);
        boolean changed = !TextUtils.equals(key, currentKey);
        current = mo;
        currentKey = key;
        titleView.setText(mo.getMusicTitle());
        artistView.setText(mo.getMusicAuthor());
        cover.setMessage(mo);
        playButton.setMessage(mo);
        boolean paused = MediaController.getInstance().isMessagePaused();
        playButton.setPlaying(!paused, isAttachedToWindow());
        playButton.setContentDescription(getString(paused ? R.string.AccActionPlay : R.string.AccActionPause));
        if (changed) {
            Integer seed = PlayerArt.cachedSeed(mo);
            if (seed != null) {
                animateColors(PlayerColors.fromSeed(seed, dark));
            } else if (PlayerArt.fileCover(mo) == null && PlayerArt.fullLocation(mo) == null && PlayerArt.thumbLocation(mo) == null) {
                animateColors(PlayerColors.fromSeed(fallbackSeed, dark));
            }
        }
    }

    private void animateColors(PlayerColors target) {
        if (colorAnimator != null) {
            colorAnimator.cancel();
        }
        PlayerColors from = colors;
        if (!isAttachedToWindow()) {
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
        cover.setColors(c.primaryContainer, c.onPrimaryContainer);
        playButton.setColors(c.primary, ColorUtils.setAlphaComponent(c.primary, 51), c.primary);
    }
}
