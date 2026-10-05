package app.exteraless.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.appearance.AvatarCornersSeekBar;
import app.exteraless.appearance.ExpressiveChat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import tw.nekomimi.nekogram.config.ConfigItem;

/**
 * Настройки Expressive-чата: общий выключатель, зоны экрана и форма пузырей.
 * Сверху превью настоящими ячейками сообщений. Экран чата лежит в стеке под настройками,
 * поэтому вьюхи пересобираются один раз — при уходе отсюда.
 */
public class ExpressiveChatSettingsActivity extends BaseFragment {

    private static final int ID_PREVIEW = 1;
    private static final int ID_MASTER = 2;
    private static final int ID_BUBBLES = 3;
    private static final int ID_BUBBLE_RADIUS = 4;
    private static final int ID_NEAR_RADIUS = 5;
    private static final int ID_HEADER = 6;
    private static final int ID_PANELS = 7;
    private static final int ID_SERVICE = 8;
    private static final int ID_REPLIES = 9;
    private static final int ID_VOICE = 10;
    private static final int ID_REACTIONS = 11;
    private static final int ID_BUTTONS = 12;
    private static final int ID_INPUT = 13;
    private static final int ID_EMOJI = 14;
    private static final int ID_MENU = 15;
    private static final int ID_MENU_RADIUS = 16;
    private static final int ID_SELECTION = 17;

    private UniversalRecyclerView listView;
    private ThemePreviewMessagesCell previewCell;
    private ActionBarMenuItem resetItem;
    private boolean changed;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.OEExpressiveChat));
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

        previewCell = new ThemePreviewMessagesCell(context, getParentLayout(), 0);
        previewCell.fragment = this;
        previewCell.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        listView.adapter.setApplyBackground(false);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        actionBar.setAdaptiveBackground(listView);

        resetItem = actionBar.createMenu().addItem(0, R.drawable.msg_reset);
        resetItem.setContentDescription(getString(R.string.Reset));
        resetItem.setOnClickListener(v -> {
            AppearanceConfig.resetExpressiveChat();
            onChanged(true);
        });
        updateResetButtonVisibility(false);

        fragmentView = contentView;
        return fragmentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(ID_PREVIEW, previewCell));
        items.add(UItem.asCheck(ID_MASTER, getString(R.string.OEExpressiveChatEnable))
                .setChecked(AppearanceConfig.expressiveChat.Bool()));
        items.add(UItem.asShadow(getString(R.string.OEExpressiveChatEnableInfo)));
        if (!AppearanceConfig.expressiveChat.Bool()) {
            return;
        }

        items.add(UItem.asHeader(getString(R.string.OEExpressiveChatBubblesHeader)));
        items.add(check(ID_BUBBLES, R.string.OEExpressiveChatBubbles, AppearanceConfig.expressiveChatBubbles));
        if (AppearanceConfig.expressiveChatBubbles.Bool()) {
            items.add(UItem.asCustom(ID_BUBBLE_RADIUS, createSlider(AppearanceConfig.expressiveChatBubbleRadius,
                    ExpressiveChat.BUBBLE_RADIUS_MIN, ExpressiveChat.BUBBLE_RADIUS_MAX, R.string.OEExpressiveChatBubbleRadius)));
            items.add(UItem.asCustom(ID_NEAR_RADIUS, createSlider(AppearanceConfig.expressiveChatBubbleNearRadius,
                    0, ExpressiveChat.NEAR_RADIUS_MAX, R.string.OEExpressiveChatNearRadius)));
        }
        items.add(check(ID_REPLIES, R.string.OEExpressiveChatReplies, AppearanceConfig.expressiveChatReplies));
        items.add(check(ID_REACTIONS, R.string.OEExpressiveChatReactions, AppearanceConfig.expressiveChatReactions));
        items.add(check(ID_VOICE, R.string.OEExpressiveChatVoice, AppearanceConfig.expressiveChatVoice));
        items.add(check(ID_SERVICE, R.string.OEExpressiveChatServicePills, AppearanceConfig.expressiveChatServicePills));
        items.add(UItem.asShadow(getString(R.string.OEExpressiveChatBubblesInfo)));

        items.add(UItem.asHeader(getString(R.string.OEExpressiveChatScreenHeader)));
        items.add(check(ID_HEADER, R.string.OEExpressiveChatHeader, AppearanceConfig.expressiveChatHeader));
        items.add(check(ID_PANELS, R.string.OEExpressiveChatPanels, AppearanceConfig.expressiveChatPanels));
        items.add(check(ID_BUTTONS, R.string.OEExpressiveChatButtons, AppearanceConfig.expressiveChatButtons));
        items.add(check(ID_INPUT, R.string.OEExpressiveChatInput, AppearanceConfig.expressiveChatInput));
        items.add(check(ID_EMOJI, R.string.OEExpressiveChatEmojiPanel, AppearanceConfig.expressiveChatEmojiPanel));
        items.add(UItem.asShadow(getString(R.string.OEExpressiveChatScreenInfo)));

        items.add(UItem.asHeader(getString(R.string.OEExpressiveChatActionsHeader)));
        items.add(check(ID_MENU, R.string.OEExpressiveChatMenu, AppearanceConfig.expressiveChatMenu));
        if (AppearanceConfig.expressiveChatMenu.Bool()) {
            items.add(UItem.asCustom(ID_MENU_RADIUS, createSlider(AppearanceConfig.expressiveChatMenuRadius,
                    ExpressiveChat.MENU_RADIUS_MIN, ExpressiveChat.MENU_RADIUS_MAX, R.string.OEExpressiveChatMenuRadius)));
        }
        items.add(check(ID_SELECTION, R.string.OEExpressiveChatSelection, AppearanceConfig.expressiveChatSelection));
        items.add(UItem.asShadow(getString(R.string.OEExpressiveChatActionsInfo)));
    }

    private static UItem check(int id, int textRes, ConfigItem item) {
        return UItem.asCheck(id, getString(textRes)).setChecked(item.Bool());
    }

    private View createSlider(ConfigItem item, int min, int max, int titleRes) {
        final AvatarCornersSeekBar slider = new AvatarCornersSeekBar(getContext(),
                value -> {
                    item.setConfigInt(value);
                    onChanged(false);
                },
                min, max, getString(titleRes), min + " dp", max + " dp");
        slider.setValueSuffix("dp");
        slider.setValue(Math.max(min, Math.min(max, item.Int())));
        return slider;
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        final ConfigItem config = configFor(item.id);
        if (config == null) {
            return;
        }
        config.setConfigBool(!config.Bool());
        onChanged(true);
    }

    private static ConfigItem configFor(int id) {
        switch (id) {
            case ID_MASTER: return AppearanceConfig.expressiveChat;
            case ID_BUBBLES: return AppearanceConfig.expressiveChatBubbles;
            case ID_HEADER: return AppearanceConfig.expressiveChatHeader;
            case ID_PANELS: return AppearanceConfig.expressiveChatPanels;
            case ID_SERVICE: return AppearanceConfig.expressiveChatServicePills;
            case ID_REPLIES: return AppearanceConfig.expressiveChatReplies;
            case ID_VOICE: return AppearanceConfig.expressiveChatVoice;
            case ID_REACTIONS: return AppearanceConfig.expressiveChatReactions;
            case ID_BUTTONS: return AppearanceConfig.expressiveChatButtons;
            case ID_INPUT: return AppearanceConfig.expressiveChatInput;
            case ID_EMOJI: return AppearanceConfig.expressiveChatEmojiPanel;
            case ID_MENU: return AppearanceConfig.expressiveChatMenu;
            case ID_SELECTION: return AppearanceConfig.expressiveChatSelection;
            default: return null;
        }
    }

    /**
     * @param updateList перечитать список — для переключателей; слайдеры сами показывают
     *                   своё значение, и пересобирать их на каждом шаге незачем
     */
    private void onChanged(boolean updateList) {
        changed = true;
        ExpressiveChat.bumpShapeVersion();
        invalidatePreview();
        if (updateList && listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
        updateResetButtonVisibility(true);
    }

    private void invalidatePreview() {
        if (previewCell == null) {
            return;
        }
        final ChatMessageCell[] cells = previewCell.getCells();
        if (cells != null) {
            for (ChatMessageCell cell : cells) {
                if (cell != null) {
                    cell.requestLayout();
                    cell.invalidate();
                }
            }
        }
        previewCell.invalidate();
    }

    private void updateResetButtonVisibility(boolean animated) {
        if (resetItem == null) {
            return;
        }
        final boolean show = !AppearanceConfig.isExpressiveChatDefault();
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
}
