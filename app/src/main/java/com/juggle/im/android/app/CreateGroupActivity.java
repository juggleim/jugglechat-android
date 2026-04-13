package com.juggle.im.android.app;

import android.content.Context;
import android.content.Intent;
import android.icu.text.Transliterator;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.juggle.im.android.component.AbsAppActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.android.R;
import com.juggle.im.android.server.beans.CreateGroupResult;
import com.juggle.im.android.server.beans.FriendBean;
import com.juggle.im.android.server.beans.FriendsListData;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CreateGroupActivity extends AbsAppActivity {

    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_GROUP_ID = "extra_group_id";
    public static final String EXTRA_DISABLE_USER_IDS = "extra_disable_user_ids";
    public static final String EXTRA_SELECTED_USER_IDS = "extra_selected_user_ids";

    public static final int MODE_CREATE_GROUP = 1;
    public static final int MODE_ADD_MEMBER = 2;

    private static final int PAGE_SIZE = 50;
    private static final Transliterator HAN_TO_LATIN = Transliterator.getInstance(
            "Han-Latin; NFD; [:Nonspacing Mark:] Remove; NFC");
    private static final List<String> INDEX_LETTERS = Collections.unmodifiableList(Arrays.asList(
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#"));

    private final List<FriendEntry> allFriends = new ArrayList<>();
    private final LinkedHashMap<String, FriendEntry> selectedMap = new LinkedHashMap<>();
    private final List<CreateGroupListAdapter.RowItem> currentRows = new ArrayList<>();
    private final Collator nameCollator = Collator.getInstance(Locale.CHINA);
    private final Map<String, TextView> indexViewMap = new HashMap<>();
    private final Set<String> disabledUserIds = new HashSet<>();
    private final List<String> visibleIndexLetters = new ArrayList<>();

    private EditText searchInput;
    private TextView btnConfirm;
    private TextView tvPageTitle;
    private ImageView searchIcon;
    private HorizontalScrollView searchContentScroll;
    private LinearLayout selectedChipsContainer;
    private LinearLayout indexBar;
    private LinearLayoutManager layoutManager;
    private CreateGroupListAdapter adapter;

    private boolean creating;
    private String activeIndexLetter = "A";
    private int mode = MODE_CREATE_GROUP;
    private String groupId;

    public static Intent newIntent(Context context, int mode, String groupId, ArrayList<String> disableUserIds) {
        Intent intent = new Intent(context, CreateGroupActivity.class);
        intent.putExtra(EXTRA_MODE, mode);
        intent.putExtra(EXTRA_GROUP_ID, groupId);
        intent.putStringArrayListExtra(EXTRA_DISABLE_USER_IDS, disableUserIds);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);
        parseIntent();
        setupWindowStyle();
        initViews();
        initList();
        initIndexBar();
        bindEvents();
        refreshSelectedChips();
        loadFriendsRecursively(1, new ArrayList<>());
    }

    private void parseIntent() {
        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_CREATE_GROUP);
        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        ArrayList<String> disableList = getIntent().getStringArrayListExtra(EXTRA_DISABLE_USER_IDS);
        if (disableList != null) {
            disabledUserIds.addAll(disableList);
        }
    }

    private void setupWindowStyle() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.white));
        window.setNavigationBarColor(getColor(R.color.conversation_page_bg));
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }

    private void initViews() {
        searchInput = findViewById(R.id.et_search);
        btnConfirm = findViewById(R.id.btn_confirm);
        searchIcon = findViewById(R.id.iv_search_icon);
        searchContentScroll = findViewById(R.id.hsv_search_content);
        selectedChipsContainer = findViewById(R.id.ll_selected_chips);
        indexBar = findViewById(R.id.ll_index_bar);
        tvPageTitle = findViewById(R.id.tv_page_title);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // 根据模式更新标题
        if (mode == MODE_ADD_MEMBER) {
            tvPageTitle.setText("添加成员");
        } else {
            tvPageTitle.setText(R.string.create_group_page_title);
        }
        updateConfirmButtonState();
    }

    private void initList() {
        RecyclerView recyclerView = findViewById(R.id.rv_members);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new CreateGroupListAdapter(this::toggleMemberSelection);
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                syncIndexByFirstVisibleSection();
            }
        });
    }

    private void bindEvents() {
        btnConfirm.setOnClickListener(v -> {
            if (mode == MODE_ADD_MEMBER) {
                doAddMember();
            } else {
                doCreateGroup();
            }
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void initIndexBar() {
        rebuildIndexBar();
        renderIndexHighlight();
    }

    /**
     * 根据当前列表可见分组重建侧边字母索引，只展示实际存在的分组。
     */
    private void rebuildIndexBar() {
        indexBar.removeAllViews();
        indexViewMap.clear();

        for (String letter : visibleIndexLetters) {
            TextView tv = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(11), dpToPx(16));
            if (indexBar.getChildCount() > 0) {
                lp.topMargin = dpToPx(2);
            }
            tv.setLayoutParams(lp);
            tv.setGravity(Gravity.CENTER);
            tv.setText(letter);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tv.setOnClickListener(v -> {
                activeIndexLetter = letter;
                renderIndexHighlight();
                scrollToSection(letter);
            });
            indexBar.addView(tv);
            indexViewMap.put(letter, tv);
        }
    }

    private void renderIndexHighlight() {
        for (String letter : visibleIndexLetters) {
            TextView tv = indexViewMap.get(letter);
            if (tv == null) {
                continue;
            }
            boolean active = TextUtils.equals(letter, activeIndexLetter);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tv.getLayoutParams();
            lp.width = dpToPx(active ? 16 : 11);
            lp.height = dpToPx(16);
            tv.setLayoutParams(lp);
            tv.setTextColor(getColor(active ? R.color.white : R.color.conversation_primary_text));
            tv.setBackgroundResource(active ? R.drawable.bg_create_group_index_active : android.R.color.transparent);
        }
    }

    private void syncIndexByFirstVisibleSection() {
        if (currentRows.isEmpty()) {
            return;
        }
        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        if (firstVisible == RecyclerView.NO_POSITION) {
            return;
        }

        for (int i = Math.min(firstVisible, currentRows.size() - 1); i >= 0; i--) {
            CreateGroupListAdapter.RowItem rowItem = currentRows.get(i);
            if (rowItem instanceof CreateGroupListAdapter.SectionRow) {
                String section = ((CreateGroupListAdapter.SectionRow) rowItem).section;
                if (!TextUtils.equals(activeIndexLetter, section)) {
                    activeIndexLetter = section;
                    renderIndexHighlight();
                }
                return;
            }
        }
    }

    private void loadFriendsRecursively(int page, List<FriendEntry> container) {
        ServiceManager.getUserService().getFriendsList(page, PAGE_SIZE, null, new ApiCallback<FriendsListData>() {
            @Override
            public void onSuccess(FriendsListData data) {
                List<FriendBean> items = data == null ? null : data.getItems();
                if (items != null) {
                    for (FriendBean item : items) {
                        FriendEntry entry = toFriendEntry(item);
                        if (entry != null) {
                            container.add(entry);
                        }
                    }
                }

                if (items != null && items.size() >= PAGE_SIZE) {
                    loadFriendsRecursively(page + 1, container);
                    return;
                }

                allFriends.clear();
                allFriends.addAll(container);
                applyFilter(searchInput.getText() == null ? "" : searchInput.getText().toString());
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(CreateGroupActivity.this,
                        getString(R.string.create_group_load_failed, String.valueOf(message)),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Nullable
    private FriendEntry toFriendEntry(@Nullable FriendBean friendBean) {
        if (friendBean == null || TextUtils.isEmpty(friendBean.getUser_id())) {
            return null;
        }

        String displayName = safeDisplayName(friendBean.getNickname(), friendBean.getUser_id());
        String section = resolveSection(displayName);
        return new FriendEntry(friendBean.getUser_id(), displayName, friendBean.getAvatar(), section);
    }

    private String safeDisplayName(@Nullable String nickname, @Nullable String userId) {
        if (!TextUtils.isEmpty(nickname)) {
            return nickname.trim();
        }
        return userId == null ? "" : userId;
    }

    private void applyFilter(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();

        List<FriendEntry> filtered = new ArrayList<>();
        for (FriendEntry item : allFriends) {
            if (matchesKeyword(item, normalized)) {
                filtered.add(item);
            }
        }

        filtered.sort(friendComparator());
        renderRows(buildRows(filtered));
    }

    private Comparator<FriendEntry> friendComparator() {
        return (left, right) -> {
            int sectionDiff = sectionOrder(left.section) - sectionOrder(right.section);
            if (sectionDiff != 0) {
                return sectionDiff;
            }

            int nameDiff = nameCollator.compare(left.displayName, right.displayName);
            if (nameDiff != 0) {
                return nameDiff;
            }
            return nameCollator.compare(left.userId, right.userId);
        };
    }

    /**
     * 把过滤后的成员列表平铺为“分组头 + 成员行”，并计算每行是否展示分割线。
     */
    private List<CreateGroupListAdapter.RowItem> buildRows(List<FriendEntry> filtered) {
        List<CreateGroupListAdapter.RowItem> rows = new ArrayList<>();
        String currentSection = null;
        for (int i = 0; i < filtered.size(); i++) {
            FriendEntry item = filtered.get(i);
            if (!TextUtils.equals(currentSection, item.section)) {
                currentSection = item.section;
                rows.add(new CreateGroupListAdapter.SectionRow(currentSection));
            }
            boolean showDivider = i < filtered.size() - 1
                    && TextUtils.equals(item.section, filtered.get(i + 1).section);
            rows.add(new CreateGroupListAdapter.MemberRow(
                    item.userId,
                    item.displayName,
                    item.avatar,
                    item.section,
                    showDivider));
        }
        return rows;
    }

    private void renderRows(List<CreateGroupListAdapter.RowItem> rows) {
        currentRows.clear();
        currentRows.addAll(rows);
        adapter.submit(currentRows, selectedMap.keySet(), disabledUserIds);
        updateVisibleIndexLetters();

        if (adapter.findSectionPosition(activeIndexLetter) < 0) {
            String firstSection = findFirstSectionLetter();
            activeIndexLetter = firstSection == null ? "A" : firstSection;
        }
        rebuildIndexBar();
        renderIndexHighlight();
        syncIndexByFirstVisibleSection();
        updateConfirmButtonState();
    }

    /**
     * 根据当前渲染结果提取可见索引字母，保证侧边栏与列表内容保持一致。
     * tips：搜索过滤后需要同步收缩索引字母，避免展示无数据的字母入口。
     */
    private void updateVisibleIndexLetters() {
        visibleIndexLetters.clear();
        for (CreateGroupListAdapter.RowItem row : currentRows) {
            if (row instanceof CreateGroupListAdapter.SectionRow) {
                String section = ((CreateGroupListAdapter.SectionRow) row).section;
                if (!TextUtils.isEmpty(section) && !visibleIndexLetters.contains(section)) {
                    visibleIndexLetters.add(section);
                }
            }
        }
        indexBar.setVisibility(visibleIndexLetters.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Nullable
    private String findFirstSectionLetter() {
        for (CreateGroupListAdapter.RowItem row : currentRows) {
            if (row instanceof CreateGroupListAdapter.SectionRow) {
                return ((CreateGroupListAdapter.SectionRow) row).section;
            }
        }
        return null;
    }

    private void toggleMemberSelection(CreateGroupListAdapter.MemberRow row) {
        if (row == null || TextUtils.isEmpty(row.userId)) {
            return;
        }
        // 禁用成员不可选择
        if (disabledUserIds.contains(row.userId)) {
            return;
        }
        boolean willSelect = !selectedMap.containsKey(row.userId);
        if (willSelect) {
            FriendEntry entry = findFriendByUserId(row.userId);
            if (entry != null) {
                selectedMap.put(row.userId, entry);
            }
        } else {
            selectedMap.remove(row.userId);
        }
        // 使用局部更新避免闪烁
        adapter.updateMemberSelection(row.userId, willSelect);
        refreshSelectedChips();
        updateConfirmButtonState();
    }

    private void refreshSelectedChips() {
        selectedChipsContainer.removeAllViews();

        boolean hasSelected = !selectedMap.isEmpty();
        searchIcon.setVisibility(hasSelected ? View.GONE : View.VISIBLE);

        if (!hasSelected) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        int index = 0;
        for (FriendEntry item : selectedMap.values()) {
            View chip = inflater.inflate(R.layout.item_create_group_selected_chip, selectedChipsContainer, false);
            ImageView avatar = chip.findViewById(R.id.iv_chip_avatar);
            TextView name = chip.findViewById(R.id.tv_chip_name);

            AvatarUtils.loadAvatar(avatar, item.avatar, item.displayName);
            name.setText(item.displayName);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(28));
            if (index > 0) {
                lp.leftMargin = dpToPx(6);
            }
            selectedChipsContainer.addView(chip, lp);
            index++;
        }

        searchContentScroll.post(() -> searchContentScroll.fullScroll(View.FOCUS_RIGHT));
    }

    @Nullable
    private FriendEntry findFriendByUserId(String userId) {
        for (FriendEntry item : allFriends) {
            if (TextUtils.equals(item.userId, userId)) {
                return item;
            }
        }
        return null;
    }

    private void scrollToSection(String letter) {
        int position = findNearestSectionPosition(letter);
        if (position >= 0) {
            layoutManager.scrollToPositionWithOffset(position, 0);
        }
    }

    private int findNearestSectionPosition(String letter) {
        int exact = adapter.findSectionPosition(letter);
        if (exact >= 0) {
            return exact;
        }

        int start = INDEX_LETTERS.indexOf(letter);
        if (start < 0) {
            return -1;
        }

        for (int i = start + 1; i < INDEX_LETTERS.size(); i++) {
            int candidate = adapter.findSectionPosition(INDEX_LETTERS.get(i));
            if (candidate >= 0) {
                return candidate;
            }
        }

        for (int i = start - 1; i >= 0; i--) {
            int candidate = adapter.findSectionPosition(INDEX_LETTERS.get(i));
            if (candidate >= 0) {
                return candidate;
            }
        }
        return -1;
    }

    /**
     * 计算联系人分组字母：优先英文首字母，中文通过 ICU 转拼音后取首字母，其它字符归类到 #。
     */
    private String resolveSection(String name) {
        if (TextUtils.isEmpty(name)) {
            return "#";
        }

        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "#";
        }

        String upperFirst = toUpperAsciiLetter(trimmed.charAt(0));
        if (!TextUtils.isEmpty(upperFirst)) {
            return upperFirst;
        }

        String latin = HAN_TO_LATIN.transliterate(trimmed);
        for (int i = 0; i < latin.length(); i++) {
            String letter = toUpperAsciiLetter(latin.charAt(i));
            if (!TextUtils.isEmpty(letter)) {
                return letter;
            }
        }
        return "#";
    }

    @Nullable
    private String toUpperAsciiLetter(char c) {
        char upper = Character.toUpperCase(c);
        if (upper >= 'A' && upper <= 'Z') {
            return String.valueOf(upper);
        }
        return null;
    }

    private int sectionOrder(String section) {
        int index = INDEX_LETTERS.indexOf(section);
        return index >= 0 ? index : INDEX_LETTERS.size() - 1;
    }

    private boolean matchesKeyword(FriendEntry item, String keyword) {
        if (TextUtils.isEmpty(keyword)) {
            return true;
        }
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        return item.displayName.toLowerCase(Locale.ROOT).contains(lowerKeyword)
                || item.userId.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    private void updateConfirmButtonState() {
        boolean enabled = !selectedMap.isEmpty() && !creating;
        btnConfirm.setEnabled(enabled);
    }

    private void doCreateGroup() {
        if (selectedMap.isEmpty()) {
            Toast.makeText(this, R.string.create_group_select_required, Toast.LENGTH_SHORT).show();
            return;
        }

        List<Map<String, String>> members = new ArrayList<>();
        for (FriendEntry member : selectedMap.values()) {
            Map<String, String> map = new HashMap<>();
            map.put("user_id", member.userId);
            members.add(map);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("group_name", buildGroupName());
        body.put("group_portrait", "");
        body.put("members", members);

        creating = true;
        updateConfirmButtonState();
        ServiceManager.getUserService().createGroup(body, new ApiCallback<CreateGroupResult>() {
            @Override
            public void onSuccess(CreateGroupResult data) {
                creating = false;
                updateConfirmButtonState();
                Toast.makeText(CreateGroupActivity.this,
                        R.string.create_group_create_success,
                        Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(int code, String message) {
                creating = false;
                updateConfirmButtonState();
                Toast.makeText(CreateGroupActivity.this,
                        getString(R.string.create_group_create_failed, String.valueOf(message)),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void doAddMember() {
        if (selectedMap.isEmpty()) {
            Toast.makeText(this, R.string.create_group_select_required, Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> memberIds = new ArrayList<>(selectedMap.keySet());

        creating = true;
        updateConfirmButtonState();
        ServiceManager.getUserService().inviteJoinGroup(groupId, memberIds, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                creating = false;
                updateConfirmButtonState();
                Toast.makeText(CreateGroupActivity.this, "邀请成功", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(int code, String message) {
                creating = false;
                updateConfirmButtonState();
                Toast.makeText(CreateGroupActivity.this, "邀请失败：" + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String buildGroupName() {
        List<String> names = new ArrayList<>();
        for (FriendEntry member : selectedMap.values()) {
            names.add(member.displayName);
        }
        if (names.size() <= 6) {
            return TextUtils.join("、", names);
        }
        return TextUtils.join("、", names.subList(0, 6)) + "等" + names.size() + "人";
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private static final class FriendEntry {
        final String userId;
        final String displayName;
        final String avatar;
        final String section;

        FriendEntry(String userId, String displayName, String avatar, String section) {
            this.userId = userId;
            this.displayName = displayName;
            this.avatar = avatar;
            this.section = section;
        }
    }
}
