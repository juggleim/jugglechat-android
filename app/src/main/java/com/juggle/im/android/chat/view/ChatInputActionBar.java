package com.juggle.im.android.chat.view;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.util.Log;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.util.TypedValue;
import android.widget.ArrayAdapter;
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
import com.juggle.im.android.chat.plugin.CameraPlugin;
import com.juggle.im.android.chat.plugin.FilePlugin;
import com.juggle.im.android.chat.plugin.ImagePlugin;
import com.juggle.im.android.chat.plugin.MorePlugin;
import com.juggle.im.android.chat.plugin.VideoCallPlugin;
import com.juggle.im.android.chat.plugin.VoiceCallPlugin;
import com.juggle.im.model.MessageMentionInfo;

import androidx.core.app.ActivityCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

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
    private EditText editMessage;
    private FrameLayout panelContainer;
    private ViewGroup inputArea;
    private View morePanel;
    // plugin system
    private List<MorePlugin> morePlugins = new ArrayList<>();
    private Map<Integer, MorePlugin> activityResultHandlers = new HashMap<>();

    private enum InputMode {TEXT, VOICE, EMOJI, MORE}

    private InputMode currentMode = InputMode.TEXT;

    private int keyboardHeight = 0;
    private boolean keyboardVisible = false;
    // Keep a reference to the global layout listener so we can unregister it on detach
    private ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;
    // the view whose ViewTreeObserver we attached to (decorView or fallback root)
    private View keyboardListenView;
    // fallback runnable used when onGlobalLayout is disabled: measure keyboard after show
    private Runnable keyboardShowAdjustRunnable;

    private Listener listener;
    private static final int REQUEST_CAMERA = 1001;
    private static final int REQUEST_RECORD_AUDIO = 1002;
    private String pendingMoreAction = null;
    private boolean pendingVoiceStart = false;

    // keep a reference to the new voice action view when active
    private VoiceInputAction voiceActionView;

    private SharedPreferences prefs;
    private static final String PREFS_NAME = "chat_input_prefs";
    private static final String KEY_RECENT = "emoji_recent";

    public interface Listener {
        void onSend(String text, String replyMsgId, MessageMentionInfo mentionInfo);

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
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public ChatInputActionBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.chat_input_action_bar, this, true);
        btnVoice = findViewById(R.id.button_voice);
        btnEmoji = findViewById(R.id.button_emoji);
        btnMore = findViewById(R.id.button_more);
        editMessage = findViewById(R.id.edit_message);
        panelContainer = findViewById(R.id.panel_container);
        inputArea = findViewById(R.id.input_area);

        prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
                ActivityCompat.requestPermissions((Activity) getContext(), permissions, reqCode);
            }

            @Override
            public void registerForActivityResult(int requestCode, MorePlugin plugin) {
                activityResultHandlers.put(requestCode, plugin);
            }
        };
        morePlugins.clear();
        morePlugins.add(new ImagePlugin(cb));
        morePlugins.add(new CameraPlugin(cb));
        morePlugins.add(new FilePlugin(cb));
        morePlugins.add(new VoiceCallPlugin(cb));
        morePlugins.add(new VideoCallPlugin(cb));
    }

    private void setupListeners() {
        btnVoice.setOnClickListener(v -> toggleVoiceMode());
        btnEmoji.setOnClickListener(v -> switchMode(InputMode.EMOJI));
        btnMore.setOnClickListener(v -> switchMode(InputMode.MORE));

        editMessage.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) switchMode(InputMode.TEXT);
        });

        editMessage.setOnClickListener(l -> {
            switchMode(InputMode.TEXT);
        });

        editMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        editMessage.setRawInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editMessage.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        editMessage.setImeActionLabel("发送", android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        editMessage.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                // If Shift is pressed, allow newline insertion; otherwise treat as Send.
                if (!event.isShiftPressed()) {
                    String msg = editMessage.getText().toString().trim();
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
        editMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                String msg = editMessage.getText().toString().trim();
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
                    String msg = editMessage.getText().toString().trim();
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
        listener.onSend(text, referView.getVisibility() == VISIBLE ? (String) referView.getTag() : null, null);
        editMessage.setText("");
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

        globalLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
            private final Rect r = new Rect();

            @Override
            public void onGlobalLayout() {
            }
        };

        // attach to the chosen view's observer
        try {
            keyboardListenView.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);
        } catch (Throwable t) {
            // fallback: attach to this view's observer
            getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);
        }
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

    private void switchMode(InputMode mode) {
        currentMode = mode;
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        switch (mode) {
            case TEXT:
                // show keyboard, hide bottom panels and restore input area
                showInputArea(true);
                hidePanel();
                editMessage.requestFocus();
                imm.showSoftInput(editMessage, InputMethodManager.SHOW_IMPLICIT);
                break;
            case VOICE:
                // hide keyboard and panels, show a voice record button in input area
                imm.hideSoftInputFromWindow(editMessage.getWindowToken(), 0);
                hidePanel();
                showVoiceInInputArea();
                if (listener != null) listener.onRequestVoice();
                break;
            case EMOJI:
                // show emoji panel with reserved height equal to keyboard
                showInputArea(true);
                ensurePanelHeight();
                showPanel(getEmojiPanel(), InputMode.EMOJI);
                imm.hideSoftInputFromWindow(editMessage.getWindowToken(), 0);
                break;
            case MORE:
                showInputArea(true);
                ensurePanelHeight();
                showPanel(getMorePanel(), InputMode.MORE);
                imm.hideSoftInputFromWindow(editMessage.getWindowToken(), 0);
                break;
        }
    }

    private void ensurePanelHeight() {
        // if we know keyboard height, use it; otherwise fallback to 250dp
        ViewGroup.LayoutParams lp = panelContainer.getLayoutParams();
        if (keyboardHeight > 0) {
            lp.height = keyboardHeight;
        } else {
            lp.height = (int) (getResources().getDisplayMetrics().density * 250);
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
        panelContainer.addView(panel);
        panelContainer.setVisibility(VISIBLE);
        if (listener != null) listener.onPanelVisibilityChanged(true);
        // when panel shows, ensure message list is pushed up by panel height
        int h = panelContainer.getLayoutParams() != null ? panelContainer.getLayoutParams().height : 0;
        adjustMessageListBottom(h);
    }

    private void hidePanel() {
        panelContainer.setVisibility(GONE);
        if (listener != null) listener.onPanelVisibilityChanged(false);
        // restore message list padding
        adjustMessageListBottom(0);
    }

    // public helpers so external controllers (e.g. fragment/activity) can query/collapse panels
    public boolean isPanelVisible() {
        return panelContainer != null && (panelContainer.getVisibility() == VISIBLE || editMessage.getVisibility() == VISIBLE);
    }

    public void collapsePanel() {
        if (isPanelVisible()) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(editMessage.getWindowToken(), 0);
            hidePanel();
        }
    }

    // Find the message RecyclerView in the activity/fragment and adjust its bottom padding so
    // the messages are not obscured by keyboard or bottom panel. This is resilient: it will
    // search common ids and tolerate absence.
    private void adjustMessageListBottom(int bottomPx) {
        try {
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
                r.setClipToPadding(false);
            }
        } catch (Exception e) {
            // ignore; best-effort only
        }
    }

    private void showInputArea(boolean showEdit) {
        // restore input area: ensure edit visible and remove voice view if present
        btnVoice.setImageResource(R.drawable.ic_input_voice);
        editMessage.setVisibility(VISIBLE);
        if (voiceActionView != null) voiceActionView.hide();
        if (showEdit && listener != null) listener.onKeyboardVisibilityChanged(true);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showVoiceInInputArea() {
        // replace edit with the new VoiceInputAction view
        editMessage.setVisibility(GONE);
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

    private View getEmojiPanel() {
        final List<String> emojis = buildEmojiListLarge();
        // Always create a fresh panel instance to avoid stale view state after hide/show
        // cycles which can make GridView's onItemClick stop firing.
        View panel = LayoutInflater.from(getContext()).inflate(R.layout.panel_emoji, panelContainer, false);
        GridView grid = panel.findViewById(R.id.emoji_grid);
        // Defensive: ensure the panel itself does not capture clicks or focus
        panel.setClickable(false);
        panel.setFocusable(false);
        panel.setFocusableInTouchMode(false);
        // Diagnostic touch listener: will log touch actions but NOT consume them
        grid.setOnTouchListener((v, event) -> {
            Log.d("ChatInputActionBar", "emoji grid touch action=" + event.getAction() + " viewVisible=" + v.isShown());
            return false; // do not consume; allow normal processing
        });
        // Prevent children from taking focus/clicks
        grid.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        grid.setFocusable(false);
        grid.setFocusableInTouchMode(false);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(), R.layout.emoji_item, emojis) {
            @Override
            public View getView(int pos, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(pos, convertView, parent);
                tv.setText(getItem(pos));
                tv.setGravity(Gravity.CENTER);
                // make sure the item view itself is not focusable/click-blocking
                tv.setFocusable(false);
                tv.setFocusableInTouchMode(false);
                // allow click so ripple shows; GridView still delivers item clicks
                tv.setClickable(false);
                return tv;
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, pos, id) -> {
            Object item = parent.getItemAtPosition(pos);
            if (item instanceof String) {
                String e = (String) item;
                int start = Math.max(editMessage.getSelectionStart(), 0);
                editMessage.getText().insert(start, e);
            }
        });

        // floating delete and send buttons (stay fixed)
        View deleteBtn = panel.findViewById(R.id.emoji_delete);
        View sendBtn = panel.findViewById(R.id.emoji_send);
        if (deleteBtn != null) {
            deleteBtn.setOnClickListener(v -> safeDeletePreviousCodePoint());
            applySelectableBackground(deleteBtn, android.R.attr.selectableItemBackgroundBorderless);
        }
        if (sendBtn != null) {
            sendBtn.setOnClickListener(v -> {
                String msg = editMessage.getText().toString().trim();
                if (!TextUtils.isEmpty(msg) && listener != null) {
                    handleSendMessage(msg);
                }
            });
            applySelectableBackground(sendBtn, android.R.attr.selectableItemBackground);
        }
        return panel;
    }

    private List<String> buildEmojiListLarge() {
        // extended emoji list (sample)
        String[] arr = new String[]{"\uD83D\uDE00", "\uD83D\uDE01", "\uD83D\uDE02", "\uD83D\uDE03", "\uD83D\uDE04", "\uD83D\uDE05", "\uD83D\uDE06", "\uD83D\uDE09", "\uD83D\uDE0A", "\uD83D\uDE0B", "\uD83D\uDE0D", "\uD83D\uDE0E", "\uD83D\uDE0F", "\uD83D\uDE12", "\uD83D\uDE14", "\uD83D\uDE1C", "\uD83D\uDE1D", "\uD83D\uDE1E", "\uD83D\uDE20", "\uD83D\uDE21", "\uD83D\uDE22", "\uD83D\uDE23", "\uD83D\uDE24", "\uD83D\uDE25", "\uD83D\uDE28", "\uD83D\uDE2A", "\uD83D\uDE2D", "\uD83D\uDE30", "\uD83D\uDE31", "\uD83D\uDE32", "\uD83D\uDE33", "\uD83D\uDE34", "\uD83D\uDE35", "\uD83D\uDE36", "\uD83D\uDE37", "\uD83D\uDE38", "\uD83D\uDE39", "\uD83D\uDE3A", "\uD83D\uDE3B", "\uD83D\uDE3C", "\uD83D\uDE3D", "\uD83D\uDE3E", "\uD83D\uDE3F", "\uD83D\uDE40", "\uD83D\uDE41", "\uD83D\uDE42", "\uD83D\uDE43", "\uD83D\uDE44", "\uD83D\uDE45", "\uD83D\uDE46", "\uD83D\uDE47", "\uD83D\uDE48", "\uD83D\uDE49", "\uD83D\uDE4A", "\uD83D\uDE4B"};
        List<String> list = new ArrayList<>();
        for (String s : arr) list.add(s);
        return list;
    }

    private void safeDeletePreviousCodePoint() {
        int sel = Math.max(editMessage.getSelectionStart(), 0);
        if (sel == 0) return;
        CharSequence text = editMessage.getText();
        if (text == null || text.length() == 0) return;
        int deleteFrom = sel - 1;
        // handle surrogate pairs
        if (deleteFrom - 1 >= 0) {
            char c = text.charAt(deleteFrom - 1);
            if (Character.isHighSurrogate(c)) {
                deleteFrom = deleteFrom - 1;
            }
        }
        editMessage.getText().delete(deleteFrom, sel);
    }

    public void showReferMsgPanel(String name, String msg, String msgId) {
        View referView = findViewById(R.id.refer_msg_container);
        referView.setVisibility(VISIBLE);
        findViewById(R.id.button_del_ref).setOnClickListener((v) -> {
            referView.setVisibility(GONE);
        });
        TextView tvContent = findViewById(R.id.message_content);
        tvContent.setText(name + ": " + msg);
        referView.setTag(msgId);
    }

    private View getMorePanel() {
        if (morePanel == null) {
            morePanel = LayoutInflater.from(getContext()).inflate(R.layout.panel_more, panelContainer, false);
            // replace existing grid container with a ViewPager that pages plugins 4x2
            View existing = morePanel.findViewById(R.id.grid_more);
            ViewGroup parent = null;
            int index = -1;
            if (existing != null) {
                parent = (ViewGroup) existing.getParent();
                for (int i = 0; i < parent.getChildCount(); i++) {
                    if (parent.getChildAt(i) == existing) {
                        index = i;
                        break;
                    }
                }
                parent.removeView(existing);
            }

            ViewPager pager = new ViewPager(getContext());
            pager.setId(View.generateViewId());
            int pad = (int) (8 * getResources().getDisplayMetrics().density);
            pager.setPadding(pad, pad, pad, pad);

            // build pages: 4 cols x 2 rows = 8 per page
            final int perPage = 8;
            List<View> pages = new ArrayList<>();
            int total = morePlugins.size();
            int pageCount = (total + perPage - 1) / perPage;
            for (int p = 0; p < pageCount; p++) {
                GridLayout grid = new GridLayout(getContext());
                grid.setColumnCount(4);
                grid.setRowCount(2);
                grid.setUseDefaultMargins(true);
                grid.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                int start = p * perPage;
                int end = Math.min(start + perPage, total);
                for (int i = start; i < end; i++) {
                    com.juggle.im.android.chat.plugin.MorePlugin plugin = morePlugins.get(i);
                    // provide host activity reference to plugin so it can call startActivityForResult
                    if (getContext() instanceof Activity) {
                        try {
                            plugin.setHostActivity((Activity) getContext());
                        } catch (Exception ignored) {
                        }
                    }
                    LinearLayout item = new LinearLayout(getContext());
                    item.setOrientation(LinearLayout.VERTICAL);
                    item.setGravity(Gravity.CENTER);
                    ImageView iv = new ImageView(getContext());
                    iv.setImageResource(plugin.getIconRes());
                    LinearLayout.LayoutParams ivlp = new LinearLayout.LayoutParams((int) (48 * getResources().getDisplayMetrics().density), (int) (48 * getResources().getDisplayMetrics().density));
                    iv.setLayoutParams(ivlp);
                    TextView tv = new TextView(getContext());
                    tv.setText(plugin.getLabel(getContext()));
                    tv.setTextSize(12);
                    tv.setGravity(Gravity.CENTER);
                    GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
                    glp.width = 0;
                    glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                    glp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                    item.addView(iv);
                    item.addView(tv);
                    int padding = (int) (8 * getResources().getDisplayMetrics().density);
                    item.setPadding(padding, padding, padding, padding);
                    final com.juggle.im.android.chat.plugin.MorePlugin pplugin = plugin;
                    // make plugin item show touch feedback
                    item.setClickable(true);
                    item.setFocusable(true);
                    applySelectableBackground(item, android.R.attr.selectableItemBackground);
                    item.setOnClickListener(v -> {
                        Activity act = null;
                        if (getContext() instanceof Activity) act = (Activity) getContext();
                        final Activity activity = act;
                        pplugin.onClick(activity);
                    });
                    grid.addView(item, glp);
                }
                pages.add(grid);
            }

            PagerAdapter adapter = new PagerAdapter() {
                @Override
                public int getCount() {
                    return pages.size();
                }

                @Override
                public boolean isViewFromObject(View view, Object object) {
                    return view == object;
                }

                @Override
                public Object instantiateItem(ViewGroup container, int position) {
                    View v = pages.get(position);
                    container.addView(v);
                    return v;
                }

                @Override
                public void destroyItem(ViewGroup container, int position, Object object) {
                    container.removeView((View) object);
                }
            };
            pager.setAdapter(adapter);

            if (parent != null && index >= 0) {
                parent.addView(pager, index);
            } else {
                // fallback: add pager to root of morePanel
                ((ViewGroup) morePanel).addView(pager);
            }
        }
        return morePanel;
    }

    // To be called from Activity's onRequestPermissionsResult
    public void onPluginRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

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
}