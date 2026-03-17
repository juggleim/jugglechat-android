package com.juggle.im.android.chat;

import static android.view.View.VISIBLE;

import android.content.Intent;
import android.icu.text.Transliterator;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.app.BlockUsersActivity;
import com.juggle.im.android.app.FriendApplicationsActivity;
import com.juggle.im.android.app.MyGroupsActivity;
import com.juggle.im.android.server.beans.FriendBean;
import com.juggle.im.android.server.beans.FriendsListData;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.ConversationInfo;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FriendsFragment extends Fragment {
    private static final String FRIEND_APPLY = "friend_apply";
    private static final int PAGE_SIZE = 50;
    private static final Transliterator HAN_TO_LATIN = Transliterator.getInstance(
            "Han-Latin; NFD; [:Nonspacing Mark:] Remove; NFC");
    private static final List<String> INDEX_LETTERS = Collections.unmodifiableList(Arrays.asList(
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#"));

    private final List<ContactEntry> allFriends = new ArrayList<>();
    private final List<ContactListAdapter.RowItem> currentRows = new ArrayList<>();
    private final Collator nameCollator = Collator.getInstance(Locale.CHINA);
    private final Map<String, TextView> indexViewMap = new HashMap<>();

    private RecyclerView recyclerView;
    private LinearLayout indexBar;
    private LinearLayoutManager layoutManager;
    private ContactListAdapter adapter;
    private String activeIndexLetter = "A";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friends_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.rv_friends_list);
        indexBar = view.findViewById(R.id.ll_contact_index_bar);
        setupRecyclerView();
        initIndexBar();
        bindActionEntries(view);
        loadFriendsRecursively(1, new ArrayList<>());
        checkNewFriend(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        View view = getView();
        if (view != null) {
            checkNewFriend(view);
        }
    }

    private void setupRecyclerView() {
        layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);
        adapter = new ContactListAdapter(this::openFriendConversation);
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                syncIndexByFirstVisibleSection();
            }
        });
    }

    private void bindActionEntries(@NonNull View view) {
        View groupsItem = view.findViewById(R.id.contact_action_groups);
        View newFriendsItem = view.findViewById(R.id.new_friends_item);
        View blacklistItem = view.findViewById(R.id.contact_action_blacklist);

        groupsItem.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), MyGroupsActivity.class)));

        newFriendsItem.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), FriendApplicationsActivity.class)));

        blacklistItem.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), BlockUsersActivity.class)));
    }

    private void openFriendConversation(@NonNull ContactListAdapter.FriendRow row) {
        Conversation conversation = new Conversation(Conversation.ConversationType.PRIVATE, row.userId);
        JIM.getInstance().getConversationManager().clearUnreadCount(conversation, null);
        Intent intent = ConversationActivity.intentFor(
                requireContext(), row.userId, false, row.displayName);
        startActivity(intent);
    }

    private void loadFriendsRecursively(int page, @NonNull List<ContactEntry> container) {
        ServiceManager.getUserService().getFriendsList(page, PAGE_SIZE, null, new ApiCallback<FriendsListData>() {
            @Override
            public void onSuccess(FriendsListData data) {
                List<FriendBean> items = data == null ? null : data.getItems();
                if (items != null) {
                    for (FriendBean item : items) {
                        ContactEntry entry = toContactEntry(item);
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
                renderRows(buildRows(sortedFriends(allFriends)));
            }

            @Override
            public void onError(int code, String message) {
                if (getContext() == null) {
                    return;
                }
                Toast.makeText(requireContext(), "加载好友失败：" + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Nullable
    private ContactEntry toContactEntry(@Nullable FriendBean friendBean) {
        if (friendBean == null || TextUtils.isEmpty(friendBean.getUser_id())) {
            return null;
        }
        String displayName = safeDisplayName(friendBean.getNickname(), friendBean.getUser_id());
        return new ContactEntry(
                friendBean.getUser_id(),
                displayName,
                friendBean.getAvatar(),
                resolveSection(displayName));
    }

    @NonNull
    private String safeDisplayName(@Nullable String nickname, @Nullable String userId) {
        if (!TextUtils.isEmpty(nickname)) {
            return nickname.trim();
        }
        return userId == null ? "" : userId;
    }

    @NonNull
    private List<ContactEntry> sortedFriends(@NonNull List<ContactEntry> source) {
        List<ContactEntry> sorted = new ArrayList<>(source);
        sorted.sort((left, right) -> {
            int sectionDiff = sectionOrder(left.section) - sectionOrder(right.section);
            if (sectionDiff != 0) {
                return sectionDiff;
            }
            int nameDiff = nameCollator.compare(left.displayName, right.displayName);
            if (nameDiff != 0) {
                return nameDiff;
            }
            return nameCollator.compare(left.userId, right.userId);
        });
        return sorted;
    }

    @NonNull
    private List<ContactListAdapter.RowItem> buildRows(@NonNull List<ContactEntry> sorted) {
        List<ContactListAdapter.RowItem> rows = new ArrayList<>();
        String currentSection = null;
        for (int i = 0; i < sorted.size(); i++) {
            ContactEntry item = sorted.get(i);
            if (!TextUtils.equals(currentSection, item.section)) {
                currentSection = item.section;
                rows.add(new ContactListAdapter.SectionRow(currentSection));
            }
            boolean showDivider = i < sorted.size() - 1
                    && TextUtils.equals(item.section, sorted.get(i + 1).section);
            rows.add(new ContactListAdapter.FriendRow(
                    item.userId,
                    item.displayName,
                    item.avatar,
                    item.section,
                    showDivider));
        }
        return rows;
    }

    private void renderRows(@NonNull List<ContactListAdapter.RowItem> rows) {
        currentRows.clear();
        currentRows.addAll(rows);
        adapter.submit(currentRows);

        if (adapter.findSectionPosition(activeIndexLetter) < 0) {
            String firstSection = findFirstSectionLetter();
            activeIndexLetter = firstSection == null ? "A" : firstSection;
            renderIndexHighlight();
        }
        syncIndexByFirstVisibleSection();
    }

    @Nullable
    private String findFirstSectionLetter() {
        for (ContactListAdapter.RowItem row : currentRows) {
            if (row instanceof ContactListAdapter.SectionRow) {
                return ((ContactListAdapter.SectionRow) row).section;
            }
        }
        return null;
    }

    private void initIndexBar() {
        indexBar.removeAllViews();
        indexViewMap.clear();
        for (String letter : INDEX_LETTERS) {
            TextView tv = new TextView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(11), dpToPx(16));
            if (indexBar.getChildCount() > 0) {
                lp.topMargin = dpToPx(2);
            }
            tv.setLayoutParams(lp);
            tv.setGravity(android.view.Gravity.CENTER);
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
        renderIndexHighlight();
    }

    private void renderIndexHighlight() {
        for (String letter : INDEX_LETTERS) {
            TextView tv = indexViewMap.get(letter);
            if (tv == null) {
                continue;
            }
            boolean active = TextUtils.equals(letter, activeIndexLetter);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) tv.getLayoutParams();
            lp.width = dpToPx(active ? 16 : 11);
            lp.height = dpToPx(16);
            tv.setLayoutParams(lp);
            tv.setTextColor(requireContext().getColor(active ? R.color.white : R.color.conversation_primary_text));
            tv.setBackgroundResource(active ? R.drawable.bg_create_group_index_active : android.R.color.transparent);
        }
    }

    private void scrollToSection(@NonNull String letter) {
        int position = findNearestSectionPosition(letter);
        if (position >= 0) {
            layoutManager.scrollToPositionWithOffset(position, 0);
        }
    }

    private int findNearestSectionPosition(@NonNull String letter) {
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

    private void syncIndexByFirstVisibleSection() {
        if (currentRows.isEmpty()) {
            return;
        }
        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        if (firstVisible == RecyclerView.NO_POSITION) {
            return;
        }
        for (int i = Math.min(firstVisible, currentRows.size() - 1); i >= 0; i--) {
            ContactListAdapter.RowItem row = currentRows.get(i);
            if (row instanceof ContactListAdapter.SectionRow) {
                String section = ((ContactListAdapter.SectionRow) row).section;
                if (!TextUtils.equals(activeIndexLetter, section)) {
                    activeIndexLetter = section;
                    renderIndexHighlight();
                }
                return;
            }
        }
    }

    @NonNull
    private String resolveSection(@Nullable String name) {
        if (TextUtils.isEmpty(name)) {
            return "#";
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "#";
        }
        String direct = toUpperAsciiLetter(trimmed.charAt(0));
        if (!TextUtils.isEmpty(direct)) {
            return direct;
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

    private int sectionOrder(@Nullable String section) {
        int index = INDEX_LETTERS.indexOf(section);
        return index >= 0 ? index : INDEX_LETTERS.size() - 1;
    }

    private void checkNewFriend(@NonNull View view) {
        Conversation conversation = new Conversation(Conversation.ConversationType.SYSTEM, FRIEND_APPLY);
        ConversationInfo info = JIM.getInstance().getConversationManager().getConversationInfo(conversation);
        if (info != null && info.getUnreadCount() > 0) {
            view.findViewById(R.id.new_friend_tip).setVisibility(VISIBLE);
        } else {
            view.findViewById(R.id.new_friend_tip).setVisibility(View.GONE);
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private static final class ContactEntry {
        final String userId;
        final String displayName;
        final String avatar;
        final String section;

        ContactEntry(String userId, String displayName, String avatar, String section) {
            this.userId = userId;
            this.displayName = displayName;
            this.avatar = avatar;
            this.section = section;
        }
    }
}
