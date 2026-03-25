package com.juggle.im.android.chat.view;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.util.Log;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.util.TypedValue;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.juggle.im.android.R;
import com.juggle.im.android.chat.mention.MentionCallback;
import com.juggle.im.android.chat.mention.MentionConfig;
import com.juggle.im.android.chat.mention.MentionManager;
import com.juggle.im.android.chat.mention.MentionModel;
import com.juggle.im.android.chat.plugin.CameraPlugin;
import com.juggle.im.android.chat.plugin.ImagePlugin;
import com.juggle.im.android.chat.plugin.MorePlugin;
import com.juggle.im.android.chat.plugin.TimedDeletePlugin;
import com.juggle.im.android.chat.plugin.VideoCallPlugin;
import com.juggle.im.android.chat.plugin.VoiceCallPlugin;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat input action bar supporting text, voice, emoji and more panels.
 * Implements smooth switching by tracking keyboard height and reserving panel space.
 */
public class ChatInputActionBar extends LinearLayout {
    private ImageView btnVoice, btnEmoji, btnMore;
    private EditText editTextInput;
    private FrameLayout panelContainer;
    private ViewGroup inputArea;
    private View morePanel, emptyPanel, emojiPanel;
    // plugin system
    private List<MorePlugin> morePlugins = new ArrayList<>();
    private Map<Integer, MorePlugin> activityResultHandlers = new HashMap<>();
    private Map<String, MorePlugin> pluginRegistry = new HashMap<>();
    private final PluginPermissionDispatcher pluginPermissionDispatcher = new PluginPermissionDispatcher();
    private final List<EmojiTabConfig> emojiTabs = new ArrayList<>();
    private int emojiTabIndex = 0;

    private enum InputMode {TEXT, VOICE, EMOJI, MORE}

    private InputMode currentMode = InputMode.TEXT;

    private int keyboardHeight = 0, difference = 0;
    private boolean keyboardVisible = false;
    // Keep a reference to the global layout listener so we can unregister it on detach
    private ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;
    // the view whose ViewTreeObserver we attached to (decorView or fallback root)
    private View keyboardListenView;
    // fallback runnable used when onGlobalLayout is disabled: measure keyboard after show
    private Runnable keyboardShowAdjustRunnable;

    private Listener listener;

    // keep a reference to the new voice action view when active
    private VoiceInputAction voiceActionView;
    private static final String PREFS_NAME = "chat_input_prefs";
    private boolean isKeyboardShowing = false;
    private boolean panelSwitched = false;
    private MentionManager mentionManager;

    private int imeMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

    public interface Listener {
        void onSend(String text, String msgId, List<MentionModel> mentionModelList, int sendType);

        void onRequestVoice(); // legacy

        void onStartVoiceRecord();

        void onFinishRecord(String voiceUrl, long duration);

        void onCancelVoiceRecord();

        /**
         * A more-panel action was requested. pluginId identifies the plugin (may be null for legacy actions),
         * action is the logical action string like "photo", "camera", "file".
         */
        void onMoreAction(String pluginId, String action, Object data);

        // panel visibility changed: true = a bottom panel is visible (emoji/more), false = hidden
        void onPanelVisibilityChanged(boolean visible);

        void onKeyboardVisibilityChanged(boolean visible);

        void onKeyboardCreated(int h);

        /**
         * at 触发
         *
         * @param mentionManager MentionManager
         */
        void onMentionTrigger(MentionManager mentionManager);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void setInputHint(String hint) {
        if (editTextInput == null) {
            return;
        }
        if (TextUtils.isEmpty(hint)) {
            editTextInput.setHint(R.string.input_msg);
            return;
        }
        editTextInput.setHint(hint);
    }

    public ChatInputActionBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.chat_input_action_bar, this, true);
        btnVoice = findViewById(R.id.button_voice);
        btnEmoji = findViewById(R.id.button_emoji);
        btnMore = findViewById(R.id.button_more);
        editTextInput = findViewById(R.id.edit_message);
        panelContainer = findViewById(R.id.panel_container);
        inputArea = findViewById(R.id.input_area);
        setupListeners();
        initKeyboardListener();
        initDefaultPlugins();
        // make top-level controls show touch feedback (ripple) on press
        applySelectableBackground(btnVoice, android.R.attr.selectableItemBackgroundBorderless);
        applySelectableBackground(btnEmoji, android.R.attr.selectableItemBackgroundBorderless);
        applySelectableBackground(btnMore, android.R.attr.selectableItemBackgroundBorderless);
    }

    private void initDefaultPlugins() {
        MorePlugin.Callback cb = new MorePlugin.Callback() {
            @Override
            public void onPluginAction(String pid, String act, Object data) {
                if (listener != null) listener.onMoreAction(pid, act, data);
            }

            @Override
            public void requestPermissions(String[] permissions, int reqCode, String pid) {
                pluginPermissionDispatcher.registerPendingRequest(reqCode, pid, permissions);
                if (getContext() instanceof Activity) {
                    ActivityCompat.requestPermissions((Activity) getContext(), permissions, reqCode);
                }
            }

            @Override
            public void registerForActivityResult(int requestCode, MorePlugin plugin) {
                activityResultHandlers.put(requestCode, plugin);
            }
        };
        morePlugins.clear();
        pluginRegistry.clear();
        registerMorePlugin(new ImagePlugin(cb));
        registerMorePlugin(new CameraPlugin(cb));
        registerMorePlugin(new VoiceCallPlugin(cb));
        registerMorePlugin(new VideoCallPlugin(cb));
        registerMorePlugin(new TimedDeletePlugin(cb));
    }

    /**
     * 插件统一注册入口，保证后续权限分发和渲染都基于同一份注册表。
     * 新增插件时只需要调用该方法即可进入完整生命周期。
     */
    private void registerMorePlugin(MorePlugin plugin) {
        if (plugin == null) {
            return;
        }
        morePlugins.add(plugin);
        String pluginId = plugin.getId();
        if (!TextUtils.isEmpty(pluginId)) {
            pluginRegistry.put(pluginId, plugin);
        }
    }

    private void setupListeners() {
        btnVoice.setOnClickListener(v -> toggleVoiceMode());
        btnEmoji.setOnClickListener(v -> {
            if (currentMode == InputMode.EMOJI) {
                switchMode(InputMode.TEXT);
            } else {
                switchMode(InputMode.EMOJI);
            }
        });
        btnMore.setOnClickListener(v -> {
            if (currentMode == InputMode.MORE) {
                switchMode(InputMode.TEXT);
            } else {
                switchMode(InputMode.MORE);
            }
        });

        editTextInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                editTextInput.setCursorVisible(true);
                switchMode(InputMode.TEXT);
            }
        });

        editTextInput.setOnClickListener(l -> {
            switchMode(InputMode.TEXT);
        });
        mentionManager = new MentionManager();
        mentionManager.init(editTextInput, new MentionCallback() {
            @Override
            public void onMentionTrigger(EditText editText) {
                listener.onMentionTrigger(mentionManager);
            }

            @Override
            public void onMentionInvalidated(String mentionId) {
                Log.d("AT", "Mention removed = " + mentionId);
            }
        }, new MentionConfig());
        editTextInput.setRawInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editTextInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        editTextInput.setImeActionLabel("发送", android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        editTextInput.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                // If Shift is pressed, allow newline insertion; otherwise treat as Send.
                if (!event.isShiftPressed()) {
                    String msg = editTextInput.getText().toString().trim();
                    if (!android.text.TextUtils.isEmpty(msg)) {
                        handleSendMessage(msg);
                    }
                    return true; // consume event
                }
                // let Shift+Enter insert a newline
                return false;
            }
            return false;
        });
        // Some IMEs don't emit key events for Enter in multiline fields, but will invoke
        // onEditorAction with IME_ACTION_SEND. Handle both cases: Editor action and key events.
        editTextInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                String msg = editTextInput.getText().toString().trim();
                if (!TextUtils.isEmpty(msg)) {
                    handleSendMessage(msg);
                    return true;
                }
                return false;
            }

            // Fallback: some IMEs send a key event
            if (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                // If Shift is pressed, allow newline; otherwise treat as send
                if (!event.isShiftPressed()) {
                    String msg = editTextInput.getText().toString().trim();
                    if (!TextUtils.isEmpty(msg)) {
                        handleSendMessage(msg);
                        return true;
                    }
                }
            }
            return false;
        });
    }

    private void handleSendMessage(String text) {
        View referView = findViewById(R.id.refer_msg_container);
        int type = 0;
        String tag = null;
        // 支持编辑消息和回复消息
        if (referView.getVisibility() == VISIBLE) {
            Object replyTag = null, editTag = null;
            replyTag = referView.getTag(R.id.tag_reply_msg);
            if (replyTag != null) {
                type = R.id.tag_reply_msg;
                tag = (String) replyTag;
            }
            editTag = referView.getTag(R.id.tag_edit_msg);
            if (editTag != null) {
                type = R.id.tag_edit_msg;
                tag = (String) editTag;
            }
        }
        List<MentionModel> mentionModelList = mentionManager.collectMentions();
        listener.onSend(text, tag, mentionModelList, type);
        editTextInput.setText("");
        referView.setVisibility(GONE);
    }

    private void initKeyboardListener() {
        // Prefer attaching the listener to the Activity decorView which gives a stable
        // measurement area. Fall back to this view's root if Activity not available.
        View listenView = null;
        if (getContext() instanceof Activity) {
            try {
                Activity act = (Activity) getContext();
                listenView = act.getWindow().getDecorView();
            } catch (Throwable t) {
                listenView = null;
            }
        }
        if (listenView == null) listenView = getRootView();
        keyboardListenView = listenView;


        /**
         * 根据adjustResize计算出键盘高度后，切换为 SOFT_INPUT_ADJUST_NOTHING
         * 保证后面的切换不出现闪屏
         */
        globalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            private final Rect r = new Rect();
            private final int minKeyboardHeightPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 100, getResources().getDisplayMetrics());

            @Override
            public void onGlobalLayout() {
                // 1. 获取当前可见窗口的区域
                keyboardListenView.getWindowVisibleDisplayFrame(r);

                // 2. 计算 DecorView 的总高度
                int screenHeight = keyboardListenView.getRootView().getHeight();

                // 3. 计算键盘弹起导致的高度差
                int heightDifference = screenHeight - r.bottom;

                Log.d("ChatInput", "Keyboard shown, height: " + keyboardHeight + "px,, " + heightDifference);
                if (difference == 0 && heightDifference != 0 && heightDifference != keyboardHeight && keyboardHeight > 0) {
                    difference = heightDifference;
                }

                // 4. 判断键盘是否弹出 (如果高度差大于最小阈值，则认为键盘弹出了)
                if (heightDifference > minKeyboardHeightPx) {
                    // 键盘弹出
                    if (!isKeyboardShowing) {
                        isKeyboardShowing = true;
                        keyboardHeight = heightDifference;
                        Log.d("ChatInput", "Keyboard shown, height: " + keyboardHeight + "px");
                        // 在这里调用你的调整布局方法
                        adjustLayoutForKeyboard(keyboardHeight);
                        if (listener != null) listener.onKeyboardVisibilityChanged(true);
                    }
                } else {
                    // 键盘隐藏
                    if (isKeyboardShowing) {
                        isKeyboardShowing = false;
                        Log.d("ChatInput", "Keyboard hidden");
                        // 在这里调用你的调整布局方法
                        if (listener != null) {
                            imeMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
                            listener.onKeyboardCreated(keyboardHeight);
                        }
                    }
                }
            }
        };
        keyboardListenView.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);
    }

    private void adjustLayoutForKeyboard(int height) {
        Log.d("ChatInputActionBar", "keyboard height=" + height);

    }

    public void insertMention(ArrayList<String> userIds, ArrayList<String> userNames) {
        mentionManager.insertMention(userIds, userNames);
        showKeyboardIfNeed();
    }

    public void showKeyboardIfNeed() {
        editTextInput.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            showKeyboard(imm);
        }, 100);
    }


    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        try {
            if (keyboardListenView != null && globalLayoutListener != null) {
                keyboardListenView.getViewTreeObserver().removeOnGlobalLayoutListener(globalLayoutListener);
            } else {
                View root = getRootView();
                if (root != null && globalLayoutListener != null) {
                    root.getViewTreeObserver().removeOnGlobalLayoutListener(globalLayoutListener);
                }
            }
        } catch (Exception ignored) {
        }
        // Clear any cached panel references to help GC (we generally inflate panels on demand)
        morePanel = null;
        listener = null;
    }

    private void toggleVoiceMode() {
        if (currentMode == InputMode.VOICE) {
            // switch back to text
            switchMode(InputMode.TEXT);
        } else {
            switchMode(InputMode.VOICE);
        }
    }

    private void showKeyboard(InputMethodManager imm) {
        showInputArea(true);
        if (keyboardHeight > 0 && imeMode == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING) {
            panelContainer.setVisibility(VISIBLE);
            showPanel(getEmptyPanel(), InputMode.TEXT);
        } else {
            hidePanel();
        }
        editTextInput.requestFocus();
        imm.showSoftInput(editTextInput, InputMethodManager.SHOW_IMPLICIT);
        ensurePanelHeight(true);
    }


    private void switchMode(InputMode mode) {
        currentMode = mode;
        btnEmoji.setImageResource(mode == InputMode.EMOJI
                ? R.drawable.ic_input_keyboard
                : R.drawable.ic_chat_input_emoji_design);
        btnMore.setImageResource(R.drawable.ic_chat_input_more_design);
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        switch (mode) {
            case TEXT:
                // show keyboard, hide bottom panels and restore input area
                showKeyboard(imm);
                break;
            case VOICE:
                // hide keyboard and panels, show a voice record button in input area
                imm.hideSoftInputFromWindow(editTextInput.getWindowToken(), 0);
                hidePanel();
                showVoiceInInputArea();
                if (listener != null) listener.onRequestVoice();
                break;
            case EMOJI:
                // show emoji panel with reserved height equal to keyboard
                showInputArea(true);
                ensurePanelHeight(false);
                showPanel(getEmojiPanel(), InputMode.EMOJI);
                panelSwitched = true;
                imm.hideSoftInputFromWindow(editTextInput.getWindowToken(), 0);
                break;
            case MORE:
                showInputArea(true);
                ensurePanelHeight(false);
                showPanel(getMorePanel(), InputMode.MORE);
                panelSwitched = true;
                imm.hideSoftInputFromWindow(editTextInput.getWindowToken(), 0);
                break;
        }
    }

    private void ensurePanelHeight(boolean keyboardVisible) {
        // if we know keyboard height, use it; otherwise fallback to configured panel height
        ViewGroup.LayoutParams lp = panelContainer.getLayoutParams();
        if (keyboardHeight > 0 && keyboardVisible) {
            lp.height = keyboardHeight - difference;
        } else {
            lp.height = getResources().getDimensionPixelSize(R.dimen.input_panel_height);
        }
        panelContainer.setLayoutParams(lp);
    }

    /**
     * Apply the theme's selectable background (ripple/pressed) to a view.
     * attr should be android.R.attr.selectableItemBackground or
     * android.R.attr.selectableItemBackgroundBorderless.
     */
    private void applySelectableBackground(View v, int attr) {
        if (v == null) return;
        try {
            TypedValue out = new TypedValue();
            if (getContext().getTheme().resolveAttribute(attr, out, true)) {
                if (out.resourceId != 0) {
                    v.setBackgroundResource(out.resourceId);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void showPanel(View panel, InputMode mode) {
        panelContainer.removeAllViews();
        if (!panelSwitched) {
            panelContainer.postDelayed(() -> {
                panelContainer.addView(panel);
                panelContainer.setVisibility(VISIBLE);
            }, 120);
        } else {
            panelContainer.addView(panel);
            panelContainer.setVisibility(VISIBLE);
        }

        if (listener != null) listener.onPanelVisibilityChanged(true);
        // when panel shows, ensure message list is pushed up by panel height
        int h = mode.equals(InputMode.TEXT)
                ?
                (keyboardHeight - difference)
                :
                (panelContainer.getLayoutParams() != null
                        ? (panelContainer.getLayoutParams().height < 0
                        ? getResources().getDimensionPixelSize(R.dimen.input_panel_height)
                        : panelContainer.getLayoutParams().height)
                        : 0
                );
        adjustMessageListBottom(h);
    }

    private void hidePanel() {
        panelContainer.setVisibility(GONE);
        if (listener != null) listener.onPanelVisibilityChanged(false);
//         restore message list padding
        adjustMessageListBottom(0);
    }

    // public helpers so external controllers (e.g. fragment/activity) can query/collapse panels
    public boolean isPanelVisible() {
        return panelContainer != null && (panelContainer.getVisibility() == VISIBLE || editTextInput.getVisibility() == VISIBLE);
    }

    public void collapsePanel() {
        if (isPanelVisible()) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(editTextInput.getWindowToken(), 0);
            hidePanel();
        }
    }

    public void hideKeyboard() {
        editTextInput.clearFocus();
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(editTextInput.getWindowToken(), 0);
    }

    // Find the message RecyclerView in the activity/fragment and adjust its bottom padding so
    // the messages are not obscured by keyboard or bottom panel. This is resilient: it will
    // search common ids and tolerate absence.
    private void adjustMessageListBottom(int bottomPx) {
        Activity act = (Activity) getContext();
        View root = act.findViewById(android.R.id.content);
        if (root == null) return;
        // look for recycler with id recycler_view_messages
        View rv = root.findViewById(R.id.recycler_view_messages);
        if (rv instanceof androidx.recyclerview.widget.RecyclerView) {
            androidx.recyclerview.widget.RecyclerView r = (androidx.recyclerview.widget.RecyclerView) rv;
            // set padding bottom and preserve existing left/top/right
            int l = r.getPaddingLeft();
            int t = r.getPaddingTop();
            int rgt = r.getPaddingRight();
            r.setPadding(l, t, rgt, bottomPx + getResources().getDimensionPixelSize(R.dimen.chat_input_height));
            // ensure the recycler can scroll into the padded area
            r.setClipToPadding(true);
        }
    }

    private void showInputArea(boolean showEdit) {
        // restore input area: ensure edit visible and remove voice view if present
        btnVoice.setImageResource(R.drawable.ic_chat_input_voice_design);
        editTextInput.setVisibility(VISIBLE);
        if (voiceActionView != null) voiceActionView.hide();
        if (showEdit && listener != null) listener.onKeyboardVisibilityChanged(true);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showVoiceInInputArea() {
        // replace edit with the new VoiceInputAction view
        editTextInput.setVisibility(GONE);
        btnVoice.setImageResource(R.drawable.ic_input_keyboard);
        if (voiceActionView == null) {
            voiceActionView = findViewById(R.id.voice_input_container);
            voiceActionView.attachToContainer(inputArea, panelContainer.getRootView().findViewById(R.id.conversation_container));
            voiceActionView.setCallback(new VoiceInputAction.Callback() {
                @Override
                public void onStart() {
                    if (listener != null) listener.onStartVoiceRecord();
                }

                @Override
                public void onFinish(String filePath, long durationMs) {
                    // notify listener with file path; preserve legacy byte[] API by not breaking
                    if (listener != null) listener.onFinishRecord(filePath, durationMs);
                }

                @Override
                public void onCancel() {
                    if (listener != null) listener.onCancelVoiceRecord();
                }

                @Override
                public void onTooShort() {
                    Toast.makeText(getContext(), R.string.voice_too_short, Toast.LENGTH_SHORT).show();
                    if (listener != null) listener.onCancelVoiceRecord();
                }
            });
        } else {
            voiceActionView.show();
        }
    }

    private View getEmptyPanel() {
        if (emptyPanel != null) return emptyPanel;
        View panel = LayoutInflater.from(getContext()).inflate(R.layout.panel_empty, panelContainer, false);
        emptyPanel = panel;
        return panel;
    }

    private View getEmojiPanel() {
        if (emojiPanel != null) return emojiPanel;
        ensureEmojiTabs();
        View panel = LayoutInflater.from(getContext()).inflate(R.layout.panel_emoji, panelContainer, false);
        GridView grid = panel.findViewById(R.id.emoji_grid);
        LinearLayout tabContainer = panel.findViewById(R.id.emoji_tab_container);

        BaseAdapter adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return getCurrentEmojiList().size();
            }

            @Override
            public Object getItem(int position) {
                List<String> current = getCurrentEmojiList();
                if (position < 0 || position >= current.size()) {
                    return "";
                }
                return current.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv;
                if (convertView instanceof TextView) {
                    tv = (TextView) convertView;
                } else {
                    tv = (TextView) LayoutInflater.from(getContext()).inflate(R.layout.emoji_item, parent, false);
                }
                tv.setText((String) getItem(position));
                tv.setGravity(Gravity.CENTER);
                tv.setFocusable(false);
                tv.setClickable(false);
                return tv;
            }
        };

        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, pos, id) -> {
            Object item = parent.getItemAtPosition(pos);
            if (!(item instanceof String)) {
                return;
            }
            String emoji = (String) item;
            int start = Math.max(editTextInput.getSelectionStart(), 0);
            editTextInput.getText().insert(start, emoji);
        });
        renderEmojiTabs(tabContainer, adapter);

        View deleteBtn = panel.findViewById(R.id.emoji_delete);
        View sendBtn = panel.findViewById(R.id.emoji_send);
        if (deleteBtn != null) {
            deleteBtn.setOnClickListener(v -> safeDeletePreviousCodePoint());
            applySelectableBackground(deleteBtn, android.R.attr.selectableItemBackgroundBorderless);
        }
        if (sendBtn != null) {
            sendBtn.setOnClickListener(v -> {
                String msg = editTextInput.getText().toString().trim();
                if (!TextUtils.isEmpty(msg) && listener != null) {
                    handleSendMessage(msg);
                }
            });
            applySelectableBackground(sendBtn, android.R.attr.selectableItemBackground);
        }
        emojiPanel = panel;
        return panel;
    }

    private void ensureEmojiTabs() {
        if (!emojiTabs.isEmpty()) {
            return;
        }
        List<String> source = buildEmojiListLarge();
        int tabCount = 1;
        int perTab = (int) Math.ceil(source.size() / (double) tabCount);
        for (int i = 0; i < tabCount; i++) {
            int start = i * perTab;
            int end = Math.min(start + perTab, source.size());
            List<String> tabEmojis = new ArrayList<>();
            if (start < end) {
                tabEmojis.addAll(source.subList(start, end));
            } else {
                tabEmojis.addAll(source);
            }
            int iconRes = i == 0
                    ? R.drawable.ic_emoji_tab_default_design
                    : R.drawable.ic_emoji_tab_pack_design;
            emojiTabs.add(new EmojiTabConfig(iconRes, tabEmojis));
        }
    }

    private List<String> getCurrentEmojiList() {
        if (emojiTabs.isEmpty()) {
            return new ArrayList<>();
        }
        int safeIndex = Math.max(0, Math.min(emojiTabIndex, emojiTabs.size() - 1));
        return emojiTabs.get(safeIndex).emojis;
    }

    private void renderEmojiTabs(LinearLayout tabContainer, BaseAdapter adapter) {
        if (tabContainer == null) {
            return;
        }
        tabContainer.removeAllViews();
        for (int i = 0; i < emojiTabs.size(); i++) {
            FrameLayout tabView = new FrameLayout(getContext());
            LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(dp(40), dp(40));
            if (i > 0) tabLp.leftMargin = dp(12);
            tabView.setLayoutParams(tabLp);
            if (i == emojiTabIndex) {
                tabView.setBackgroundResource(R.drawable.bg_emoji_tab_selected);
            } else {
                tabView.setBackgroundColor(getResources().getColor(android.R.color.transparent));
            }

            ImageView tabIcon = new ImageView(getContext());
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER);
            tabIcon.setLayoutParams(iconLp);
            tabIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tabIcon.setImageResource(emojiTabs.get(i).iconRes);
            tabView.addView(tabIcon);

            final int index = i;
            tabView.setOnClickListener(v -> {
                if (emojiTabIndex == index) {
                    return;
                }
                emojiTabIndex = index;
                adapter.notifyDataSetChanged();
                renderEmojiTabs(tabContainer, adapter);
            });
            tabContainer.addView(tabView);
        }
    }

    private List<String> buildEmojiListLarge() {
        String[] arr = new String[]{"\uD83D\uDE00", "\uD83D\uDE01", "\uD83D\uDE02", "\uD83D\uDE03", "\uD83D\uDE04", "\uD83D\uDE05", "\uD83D\uDE06", "\uD83D\uDE09", "\uD83D\uDE0A", "\uD83D\uDE0B", "\uD83D\uDE0D", "\uD83D\uDE0E", "\uD83D\uDE0F", "\uD83D\uDE12", "\uD83D\uDE14", "\uD83D\uDE1C", "\uD83D\uDE1D", "\uD83D\uDE1E", "\uD83D\uDE20", "\uD83D\uDE21", "\uD83D\uDE22", "\uD83D\uDE23", "\uD83D\uDE24", "\uD83D\uDE25", "\uD83D\uDE28", "\uD83D\uDE2A", "\uD83D\uDE2D", "\uD83D\uDE30", "\uD83D\uDE31", "\uD83D\uDE32", "\uD83D\uDE33", "\uD83D\uDE34", "\uD83D\uDE35", "\uD83D\uDE36", "\uD83D\uDE37", "\uD83D\uDE38", "\uD83D\uDE39", "\uD83D\uDE3A", "\uD83D\uDE3B", "\uD83D\uDE3C", "\uD83D\uDE3D", "\uD83D\uDE3E", "\uD83D\uDE3F", "\uD83D\uDE40", "\uD83D\uDE41", "\uD83D\uDE42", "\uD83D\uDE43", "\uD83D\uDE44", "\uD83D\uDE45", "\uD83D\uDE46", "\uD83D\uDE47", "\uD83D\uDE48", "\uD83D\uDE49", "\uD83D\uDE4A", "\uD83D\uDE4B"};
        List<String> list = new ArrayList<>();
        for (String s : arr) list.add(s);
        return list;
    }

    private void safeDeletePreviousCodePoint() {
        int sel = Math.max(editTextInput.getSelectionStart(), 0);
        if (sel == 0) return;
        CharSequence text = editTextInput.getText();
        if (text == null || text.length() == 0) return;
        int deleteFrom = sel - 1;
        // handle surrogate pairs
        if (deleteFrom - 1 >= 0) {
            char c = text.charAt(deleteFrom - 1);
            if (Character.isHighSurrogate(c)) {
                deleteFrom = deleteFrom - 1;
            }
        }
        editTextInput.getText().delete(deleteFrom, sel);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * @param name
     * @param msg
     * @param msgId
     * @param type  1 - reply, 2-edit message
     */
    public void showReferMsgPanel(String name, String msg, String msgId, int type) {
        View referView = findViewById(R.id.refer_msg_container);
        referView.setVisibility(VISIBLE);
        findViewById(R.id.button_del_ref).setOnClickListener((v) -> {
            referView.setVisibility(GONE);
        });
        // clear old tag
        referView.setTag(R.id.tag_edit_msg, null);
        referView.setTag(R.id.tag_reply_msg, null);
        TextView tvContent = findViewById(R.id.message_content);
        tvContent.setText(name + ": " + msg);
        referView.setTag(type, msgId);
        collapsePanel();
    }

    private View getMorePanel() {
        if (morePanel != null) {
            return morePanel;
        }
        morePanel = LayoutInflater.from(getContext()).inflate(R.layout.panel_more, panelContainer, false);
        GridLayout grid = morePanel.findViewById(R.id.grid_more);
        if (grid == null) {
            return morePanel;
        }
        grid.removeAllViews();
        final int columns = 4;
        for (int i = 0; i < morePlugins.size(); i++) {
            MorePlugin plugin = morePlugins.get(i);
            if (getContext() instanceof Activity) {
                try {
                    plugin.setHostActivity((Activity) getContext());
                } catch (Exception ignored) {
                }
            }

            int row = i / columns;
            int col = i % columns;

            LinearLayout item = new LinearLayout(getContext());
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);

            GridLayout.LayoutParams itemLp = new GridLayout.LayoutParams(
                    GridLayout.spec(row),
                    GridLayout.spec(col, 1f)
            );
            // 四列等分剩余宽度（扣除 panel_more 左右 padding），每列内部内容居中
            itemLp.width = 0;
            itemLp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            itemLp.setGravity(Gravity.FILL_HORIZONTAL);
            int topMargin = row == 0 ? 0 : dp(30);
            itemLp.setMargins(0, topMargin, 0, 0);
            item.setLayoutParams(itemLp);

            FrameLayout iconContainer = new FrameLayout(getContext());
            LinearLayout.LayoutParams iconContainerLp = new LinearLayout.LayoutParams(dp(53), dp(53));
            iconContainer.setLayoutParams(iconContainerLp);
            iconContainer.setBackgroundResource(R.drawable.bg_more_item_icon);

            ImageView icon = new ImageView(getContext());
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(27), dp(27), Gravity.CENTER);
            icon.setLayoutParams(iconLp);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImageResource(plugin.getIconRes());
            iconContainer.addView(icon);

            TextView label = new TextView(getContext());
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(dp(53), ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = dp(3);
            label.setLayoutParams(labelLp);
            label.setText(plugin.getLabel(getContext()));
            label.setTextColor(getResources().getColor(R.color.conversation_secondary_text));
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            label.setGravity(Gravity.CENTER);

            item.addView(iconContainer);
            item.addView(label);

            final MorePlugin clickPlugin = plugin;
            item.setClickable(true);
            item.setFocusable(true);
            applySelectableBackground(item, android.R.attr.selectableItemBackground);
            item.setOnClickListener(v -> {
                Activity act = getContext() instanceof Activity ? (Activity) getContext() : null;
                clickPlugin.onClick(act);
            });
            grid.addView(item);
        }
        return morePanel;
    }

    // To be called from Activity's onRequestPermissionsResult
    public void onPluginRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        PluginPermissionDispatcher.PermissionDispatchResult result =
                pluginPermissionDispatcher.consumeResult(requestCode, grantResults);
        if (result == null) {
            return;
        }
        MorePlugin plugin = pluginRegistry.get(result.getPluginId());
        if (plugin == null) {
            return;
        }
        Activity hostActivity = getContext() instanceof Activity ? (Activity) getContext() : null;
        plugin.onRequestPermissionsResult(hostActivity, requestCode, result.getPermissions(), grantResults);
    }

    /**
     * Forward activity result to the plugin that registered for this request code.
     * Returns true if a plugin handled the result.
     */
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        com.juggle.im.android.chat.plugin.MorePlugin plugin = activityResultHandlers.get(requestCode);
        if (plugin != null) {
            boolean handled = plugin.onActivityResult(requestCode, resultCode, data);
            if (handled) {
                activityResultHandlers.remove(requestCode);
            }
            return handled;
        }
        return false;
    }

    private static final class EmojiTabConfig {
        private final int iconRes;
        private final List<String> emojis;

        private EmojiTabConfig(int iconRes, List<String> emojis) {
            this.iconRes = iconRes;
            this.emojis = emojis;
        }
    }

    /**
     * 插件权限请求分发器，负责维护 requestCode -> pluginId 的临时映射。
     *
     * <p>该类不依赖 Android View 生命周期，便于在本地单元测试直接验证
     * 「权限请求登记 -> 权限结果消费」闭环。</p>
     */
    public static final class PluginPermissionDispatcher {
        private final Map<Integer, PendingPermissionRequest> pendingPermissionRequests = new HashMap<>();

        /**
         * 记录一次待处理的权限请求。
         */
        public void registerPendingRequest(int requestCode, String pluginId, String[] permissions) {
            if (pluginId == null || pluginId.trim().isEmpty()) {
                return;
            }
            String[] safePermissions = permissions == null ? new String[0] : permissions.clone();
            pendingPermissionRequests.put(requestCode, new PendingPermissionRequest(pluginId, safePermissions));
        }

        /**
         * 消费一次权限结果。若不存在对应 requestCode，则返回 null。
         */
        @Nullable
        public PermissionDispatchResult consumeResult(int requestCode, int[] grantResults) {
            PendingPermissionRequest pendingPermissionRequest = pendingPermissionRequests.remove(requestCode);
            if (pendingPermissionRequest == null) {
                return null;
            }
            boolean granted = areAllPermissionsGranted(grantResults);
            return new PermissionDispatchResult(
                    pendingPermissionRequest.pluginId,
                    pendingPermissionRequest.permissions,
                    granted);
        }

        private static boolean areAllPermissionsGranted(int[] grantResults) {
            if (grantResults == null || grantResults.length == 0) {
                return false;
            }
            for (int grantResult : grantResults) {
                if (grantResult != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
            return true;
        }

        private static final class PendingPermissionRequest {
            private final String pluginId;
            private final String[] permissions;

            private PendingPermissionRequest(String pluginId, String[] permissions) {
                this.pluginId = pluginId;
                this.permissions = permissions;
            }
        }

        /**
         * 权限结果的标准化输出，供输入面板决定下一步分发策略。
         */
        public static final class PermissionDispatchResult {
            private final String pluginId;
            private final String[] permissions;
            private final boolean granted;

            private PermissionDispatchResult(String pluginId, String[] permissions, boolean granted) {
                this.pluginId = pluginId;
                this.permissions = permissions;
                this.granted = granted;
            }

            public String getPluginId() {
                return pluginId;
            }

            public String[] getPermissions() {
                return permissions.clone();
            }

            public boolean isGranted() {
                return granted;
            }
        }
    }
}
