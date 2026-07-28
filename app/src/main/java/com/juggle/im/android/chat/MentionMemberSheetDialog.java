package com.juggle.im.android.chat;

import android.graphics.Color;
import android.icu.text.Transliterator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.juggle.im.android.utils.LogUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.app.CreateGroupListAdapter;
import com.juggle.im.android.chat.widget.IndexBar;
import com.juggle.im.android.server.beans.FriendBean;
import com.juggle.im.android.server.beans.FriendsListData;
import com.juggle.im.android.server.beans.GroupDetailBean;
import com.juggle.im.android.server.beans.GroupMemberBean;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import com.juggle.im.android.i18n.LanguageManager;

public class MentionMemberSheetDialog extends BottomSheetDialogFragment {

    public static final String TAG = "MentionMemberSheetDialog";
    public static final String REQUEST_KEY = "mention_member_sheet_request";
    public static final String RESULT_TYPE = "result_type";
    public static final String RESULT_SELECTED_IDS = "result_selected_ids";
    public static final String RESULT_SELECTED_NAMES = "result_selected_names";
    public static final String RESULT_TYPE_SELECTED = "selected";
    public static final String RESULT_TYPE_CANCELLED = "cancelled";

    private static final String ARG_CONVERSATION_ID = "arg_conversation_id";
    private static final String ARG_IS_GROUP = "arg_is_group";

    private static final int FRIEND_PAGE_SIZE = 50;
    private static final long SEARCH_DEBOUNCE_MS = 250L;
    private static final Transliterator HAN_TO_LATIN = Transliterator.getInstance(
            "Han-Latin; NFD; [:Nonspacing Mark:] Remove; NFC");
    private static final List<String> INDEX_LETTERS = Collections.unmodifiableList(Arrays.asList(
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#"));

    private final List<MemberEntry> allMembers = new ArrayList<>();
    private final List<CreateGroupListAdapter.RowItem> currentRows = new ArrayList<>();
    private final LinkedHashMap<String, MemberEntry> selectedMap = new LinkedHashMap<>();
    private final Collator collator = Collator.getInstance(LanguageManager.currentLocale());

    private final Handler handler = new Handler(Looper.getMainLooper());

    private EditText etSearch;
    private TextView tvAction;
    private TextView tvEmpty;
    private RecyclerView rvMembers;
    private IndexBar indexBar;

    private LinearLayoutManager layoutManager;
    private CreateGroupListAdapter adapter;
    private Runnable searchRunnable;

    private boolean multiMode;
    private boolean resultDispatched;
    private String activeIndexLetter = "";

    public static MentionMemberSheetDialog newInstance(String conversationId, boolean isGroup) {
        MentionMemberSheetDialog dialog = new MentionMemberSheetDialog();
        Bundle args = new Bundle();
        args.putString(ARG_CONVERSATION_ID, conversationId);
        args.putBoolean(ARG_IS_GROUP, isGroup);
        dialog.setArguments(args);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_mention_member_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        initList();
        bindEvents();
        refreshActionButton();
        loadMembers();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!(getDialog() instanceof BottomSheetDialog)) {
            return;
        }
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            bottomSheet.setBackgroundColor(Color.TRANSPARENT);
            ViewGroup.LayoutParams lp = bottomSheet.getLayoutParams();
            int screenHeight = requireContext().getResources().getDisplayMetrics().heightPixels;
            lp.height = (int) (screenHeight * 0.5f);
            bottomSheet.setLayoutParams(lp);

            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
        if (dialog.getWindow() != null) {
            dialog.getWindow().setDimAmount(0.5f);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (searchRunnable != null) {
            handler.removeCallbacks(searchRunnable);
            searchRunnable = null;
        }
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        if (!resultDispatched) {
            dispatchCancelled();
        }
        super.onDismiss(dialog);
    }

    private void initViews(@NonNull View root) {
        etSearch = root.findViewById(R.id.et_search);
        tvAction = root.findViewById(R.id.tv_action);
        tvEmpty = root.findViewById(R.id.tv_empty);
        rvMembers = root.findViewById(R.id.rv_members);
        indexBar = root.findViewById(R.id.index_bar);

        TextView tvClose = root.findViewById(R.id.tv_close);
        tvClose.setOnClickListener(v -> dismissAllowingStateLoss());
    }

    private void initList() {
        layoutManager = new LinearLayoutManager(requireContext());
        rvMembers.setLayoutManager(layoutManager);
        adapter = new CreateGroupListAdapter(this::onMemberClick);
        adapter.setShowCheckBox(false);
        rvMembers.setAdapter(adapter);

        rvMembers.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                syncIndexByFirstVisibleSection();
            }
        });

        indexBar.setOnIndexSelectedListener(letter -> {
            activeIndexLetter = letter;
            indexBar.setActiveLetter(letter);
            scrollToSection(letter);
        });
    }

    private void bindEvents() {
        tvAction.setOnClickListener(v -> {
            if (!multiMode) {
                multiMode = true;
                adapter.setShowCheckBox(true);
                refreshActionButton();
                return;
            }
            if (selectedMap.isEmpty()) {
                return;
            }
            ArrayList<String> ids = new ArrayList<>();
            ArrayList<String> names = new ArrayList<>();
            for (MemberEntry entry : selectedMap.values()) {
                ids.add(entry.userId);
                names.add(entry.displayName);
            }
            dispatchSelected(ids, names);
            dismissAllowingStateLoss();
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) {
                    handler.removeCallbacks(searchRunnable);
                }
                String keyword = s == null ? "" : s.toString();
                searchRunnable = () -> applyFilter(keyword);
                handler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void onMemberClick(CreateGroupListAdapter.MemberRow row) {
        if (row == null || TextUtils.isEmpty(row.userId)) {
            return;
        }
        MemberEntry entry = findMemberById(row.userId);
        if (entry == null) {
            return;
        }

        if (!multiMode) {
            ArrayList<String> ids = new ArrayList<>();
            ArrayList<String> names = new ArrayList<>();
            ids.add(entry.userId);
            names.add(entry.displayName);
            dispatchSelected(ids, names);
            dismissAllowingStateLoss();
            return;
        }

        if (selectedMap.containsKey(entry.userId)) {
            selectedMap.remove(entry.userId);
            adapter.updateMemberSelection(entry.userId, false);
        } else {
            selectedMap.put(entry.userId, entry);
            adapter.updateMemberSelection(entry.userId, true);
        }
        refreshActionButton();
    }

    private void loadMembers() {
        Bundle args = getArguments();
        String conversationId = args == null ? null : args.getString(ARG_CONVERSATION_ID);
        boolean isGroup = args != null && args.getBoolean(ARG_IS_GROUP, false);

        if (isGroup && !TextUtils.isEmpty(conversationId)) {
            loadGroupMembers(conversationId);
        } else {
            loadFriendMembersRecursively(1, new ArrayList<>());
        }
    }

    private void loadGroupMembers(String groupId) {
        ServiceManager.getUserService().getGroupInfo(groupId, new ApiCallback<GroupDetailBean>() {
            @Override
            public void onSuccess(GroupDetailBean data) {
                List<MemberEntry> members = new ArrayList<>();
                List<GroupMemberBean> list = data == null ? null : data.getMembers();
                String currentUserId = JIM.getInstance().getCurrentUserId();
                if (list != null) {
                    for (GroupMemberBean item : list) {
                        if (item == null || TextUtils.isEmpty(item.getUserId())) {
                            continue;
                        }
                        if (TextUtils.equals(currentUserId, item.getUserId())) {
                            continue;
                        }
                        String displayName = safeDisplayName(item.getNickname(), item.getUserId());
                        members.add(new MemberEntry(
                                item.getUserId(),
                                displayName,
                                item.getAvatar(),
                                resolveSection(displayName)));
                    }
                }
                onMembersLoaded(members);
            }

            @Override
            public void onError(int code, String message) {
                LogUtils.serverError("chat", "loadMentionMembers", code, message);
                Toast.makeText(requireContext(),
                        R.string.create_group_load_failed,
                        Toast.LENGTH_SHORT).show();
                onMembersLoaded(new ArrayList<>());
            }
        });
    }

    private void loadFriendMembersRecursively(int page, List<MemberEntry> container) {
        ServiceManager.getUserService().getFriendsList(page, FRIEND_PAGE_SIZE, null, new ApiCallback<FriendsListData>() {
            @Override
            public void onSuccess(FriendsListData data) {
                List<FriendBean> items = data == null ? null : data.getItems();
                if (items != null) {
                    for (FriendBean item : items) {
                        if (item == null || TextUtils.isEmpty(item.getUser_id())) {
                            continue;
                        }
                        String displayName = safeDisplayName(item.getNickname(), item.getUser_id());
                        container.add(new MemberEntry(
                                item.getUser_id(),
                                displayName,
                                item.getAvatar(),
                                resolveSection(displayName)));
                    }
                }

                if (items != null && items.size() >= FRIEND_PAGE_SIZE) {
                    loadFriendMembersRecursively(page + 1, container);
                    return;
                }
                onMembersLoaded(container);
            }

            @Override
            public void onError(int code, String message) {
                LogUtils.serverError("chat", "loadMentionMembers", code, message);
                Toast.makeText(requireContext(),
                        R.string.create_group_load_failed,
                        Toast.LENGTH_SHORT).show();
                onMembersLoaded(new ArrayList<>());
            }
        });
    }

    private void onMembersLoaded(List<MemberEntry> members) {
        allMembers.clear();
        if (members != null) {
            allMembers.addAll(members);
        }
        String keyword = etSearch.getText() == null ? "" : etSearch.getText().toString();
        applyFilter(keyword);
    }

    private void applyFilter(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        List<MemberEntry> filtered = new ArrayList<>();

        for (MemberEntry item : allMembers) {
            if (matchesKeyword(item, normalized)) {
                filtered.add(item);
            }
        }

        filtered.sort(memberComparator());
        List<CreateGroupListAdapter.RowItem> rows = buildRows(filtered);

        currentRows.clear();
        currentRows.addAll(rows);
        adapter.submit(rows, selectedMap.keySet(), Collections.emptySet());

        List<String> letters = collectSectionLetters(rows);
        indexBar.setLetters(letters);
        indexBar.setVisibility(letters.isEmpty() ? View.GONE : View.VISIBLE);

        if (!letters.isEmpty()) {
            if (TextUtils.isEmpty(activeIndexLetter) || !letters.contains(activeIndexLetter)) {
                activeIndexLetter = letters.get(0);
            }
            indexBar.setActiveLetter(activeIndexLetter);
        }

        tvEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private Comparator<MemberEntry> memberComparator() {
        return (left, right) -> {
            int sectionDiff = sectionOrder(left.section) - sectionOrder(right.section);
            if (sectionDiff != 0) {
                return sectionDiff;
            }
            int nameDiff = collator.compare(left.displayName, right.displayName);
            if (nameDiff != 0) {
                return nameDiff;
            }
            return collator.compare(left.userId, right.userId);
        };
    }

    private List<CreateGroupListAdapter.RowItem> buildRows(List<MemberEntry> members) {
        List<CreateGroupListAdapter.RowItem> rows = new ArrayList<>();
        String currentSection = null;
        for (int i = 0; i < members.size(); i++) {
            MemberEntry item = members.get(i);
            if (!TextUtils.equals(currentSection, item.section)) {
                currentSection = item.section;
                rows.add(new CreateGroupListAdapter.SectionRow(currentSection));
            }
            boolean showDivider = i < members.size() - 1
                    && TextUtils.equals(item.section, members.get(i + 1).section);
            rows.add(new CreateGroupListAdapter.MemberRow(
                    item.userId,
                    item.displayName,
                    item.avatar,
                    item.section,
                    showDivider));
        }
        return rows;
    }

    private List<String> collectSectionLetters(List<CreateGroupListAdapter.RowItem> rows) {
        LinkedHashSet<String> letters = new LinkedHashSet<>();
        for (CreateGroupListAdapter.RowItem row : rows) {
            if (row instanceof CreateGroupListAdapter.SectionRow) {
                letters.add(((CreateGroupListAdapter.SectionRow) row).section);
            }
        }
        return new ArrayList<>(letters);
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
            CreateGroupListAdapter.RowItem row = currentRows.get(i);
            if (row instanceof CreateGroupListAdapter.SectionRow) {
                String section = ((CreateGroupListAdapter.SectionRow) row).section;
                if (!TextUtils.equals(activeIndexLetter, section)) {
                    activeIndexLetter = section;
                    indexBar.setActiveLetter(section);
                }
                return;
            }
        }
    }

    private void scrollToSection(String letter) {
        if (TextUtils.isEmpty(letter)) {
            return;
        }
        int position = adapter.findSectionPosition(letter);
        if (position >= 0) {
            layoutManager.scrollToPositionWithOffset(position, 0);
        }
    }

    private void refreshActionButton() {
        if (!multiMode) {
            tvAction.setText(R.string.mention_sheet_multi_select);
            tvAction.setEnabled(true);
            tvAction.setTextColor(requireContext().getColor(R.color.group_primary));
            tvAction.setBackground(null);
            return;
        }

        tvAction.setText(R.string.mention_sheet_done);
        boolean enabled = !selectedMap.isEmpty();
        tvAction.setEnabled(enabled);
        tvAction.setTextColor(requireContext().getColor(enabled ? R.color.white : R.color.group_text_auxiliary));
        tvAction.setBackgroundResource(enabled
                ? R.drawable.bg_mention_sheet_done_enabled
                : R.drawable.bg_mention_sheet_done_disabled);
    }

    private void dispatchSelected(ArrayList<String> ids, ArrayList<String> names) {
        Bundle result = new Bundle();
        result.putString(RESULT_TYPE, RESULT_TYPE_SELECTED);
        result.putStringArrayList(RESULT_SELECTED_IDS, ids);
        result.putStringArrayList(RESULT_SELECTED_NAMES, names);
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        resultDispatched = true;
    }

    private void dispatchCancelled() {
        Bundle result = new Bundle();
        result.putString(RESULT_TYPE, RESULT_TYPE_CANCELLED);
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        resultDispatched = true;
    }

    @Nullable
    private MemberEntry findMemberById(String userId) {
        for (MemberEntry entry : allMembers) {
            if (TextUtils.equals(entry.userId, userId)) {
                return entry;
            }
        }
        return null;
    }

    private boolean matchesKeyword(MemberEntry item, String keyword) {
        if (TextUtils.isEmpty(keyword)) {
            return true;
        }
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        return item.displayName.toLowerCase(Locale.ROOT).contains(lowerKeyword)
                || item.userId.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    private int sectionOrder(String section) {
        int idx = INDEX_LETTERS.indexOf(section);
        return idx >= 0 ? idx : INDEX_LETTERS.size() - 1;
    }

    private String safeDisplayName(String nickname, String userId) {
        if (!TextUtils.isEmpty(nickname)) {
            return nickname.trim();
        }
        return userId == null ? "" : userId;
    }

    private String resolveSection(String name) {
        if (TextUtils.isEmpty(name)) {
            return "#";
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "#";
        }

        String upper = toUpperAsciiLetter(trimmed.charAt(0));
        if (!TextUtils.isEmpty(upper)) {
            return upper;
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

    private static class MemberEntry {
        final String userId;
        final String displayName;
        final String avatar;
        final String section;

        MemberEntry(String userId, String displayName, String avatar, String section) {
            this.userId = userId;
            this.displayName = displayName;
            this.avatar = avatar;
            this.section = section;
        }
    }
}
