package com.juggle.im.android.chat;

import android.app.Activity;
import android.content.Intent;
import android.icu.text.Transliterator;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.app.BlockUsersActivity;
import com.juggle.im.android.app.ContactDetailActivity;
import com.juggle.im.android.app.FriendApplicationsActivity;
import com.juggle.im.android.app.MyGroupsActivity;
import com.juggle.im.android.chat.widget.IndexBar;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import com.juggle.im.android.utils.LogUtils;
import com.juggle.im.android.i18n.LanguageManager;

public class FriendsFragment extends Fragment {
    private static final String FRIEND_APPLY = "friend_apply";
    private static final int REQ_CONTACT_DETAIL = 3001;
    private static final int PAGE_SIZE = 50;
    private static final Transliterator HAN_TO_LATIN = Transliterator.getInstance(
            "Han-Latin; NFD; [:Nonspacing Mark:] Remove; NFC");
    private static final List<String> INDEX_LETTERS = Collections.unmodifiableList(Arrays.asList(
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#"));

    private final List<ContactEntry> allFriends = new ArrayList<>();
    private final List<ContactListAdapter.RowItem> currentRows = new ArrayList<>();
    private final Collator nameCollator = Collator.getInstance(LanguageManager.currentLocale());

    private RecyclerView recyclerView;
    private IndexBar indexBar;
    private LinearLayoutManager layoutManager;
    private ContactListAdapter adapter;
    private String activeIndexLetter;
    private boolean hasNewFriendUnread;

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

        refreshNewFriendBadge();
        setupRecyclerView();
        initIndexBar();
        loadFriendsRecursively(1, new ArrayList<>());
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshNewFriendBadge();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CONTACT_DETAIL || resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        String removedUserId = data.getStringExtra(ContactDetailActivity.RESULT_REMOVED_USER_ID);
        if (TextUtils.isEmpty(removedUserId)) {
            return;
        }
        removeFriendLocally(removedUserId);
    }

    private void setupRecyclerView() {
        layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);
        adapter = new ContactListAdapter(this::openFriendDetail, this::onActionEntryClick);
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                syncIndexByFirstVisibleSection();
            }
        });
    }

    private void initIndexBar() {
        indexBar.setOnIndexSelectedListener(letter -> {
            activeIndexLetter = letter;
            indexBar.setActiveLetter(letter);
            scrollToSection(letter);
        });
    }

    /**
     * 功能入口点击分发。
     *
     * @param actionType 功能入口类型，见 ContactListAdapter.ACTION_* 常量
     */
    private void onActionEntryClick(int actionType) {
        if (getContext() == null) {
            return;
        }
        if (actionType == ContactListAdapter.ACTION_GROUPS) {
            startActivity(new Intent(requireContext(), MyGroupsActivity.class));
            return;
        }
        if (actionType == ContactListAdapter.ACTION_NEW_FRIENDS) {
            startActivity(new Intent(requireContext(), FriendApplicationsActivity.class));
            return;
        }
        if (actionType == ContactListAdapter.ACTION_BLACKLIST) {
            startActivity(new Intent(requireContext(), BlockUsersActivity.class));
        }
    }

    private void openFriendDetail(@NonNull ContactListAdapter.FriendRow row) {
        Intent intent = new Intent(requireContext(), ContactDetailActivity.class);
        intent.putExtra(ContactDetailActivity.EXTRA_USER_ID, row.userId);
        startActivityForResult(intent, REQ_CONTACT_DETAIL);
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
                LogUtils.serverError("contact", "loadFriends", code, message);
                Toast.makeText(requireContext(), R.string.friends_load_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 本地移除已删除联系人，并刷新列表。
     *
     * @param userId 已删除的联系人 ID
     */
    private void removeFriendLocally(@NonNull String userId) {
        boolean removed = allFriends.removeIf(item -> TextUtils.equals(item.userId, userId));
        if (!removed) {
            return;
        }
        renderRows(buildRows(sortedFriends(allFriends)));
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

        // 关键逻辑：三个功能入口也作为 RecyclerView 行渲染，保证它们与好友列表一起滚动。
        rows.addAll(buildActionRows());

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

    @NonNull
    private List<ContactListAdapter.RowItem> buildActionRows() {
        List<ContactListAdapter.RowItem> rows = new ArrayList<>(3);
        rows.add(new ContactListAdapter.ActionRow(
                ContactListAdapter.ACTION_GROUPS,
                R.drawable.icon_group,
                getString(R.string.contact_groups),
                true,
                false));
        rows.add(new ContactListAdapter.ActionRow(
                ContactListAdapter.ACTION_NEW_FRIENDS,
                R.drawable.icon_new_friend,
                getString(R.string.contact_new_friends),
                true,
                hasNewFriendUnread));
        rows.add(new ContactListAdapter.ActionRow(
                ContactListAdapter.ACTION_BLACKLIST,
                R.drawable.icon_black_user,
                getString(R.string.contact_blacklist),
                false,
                false));
        return rows;
    }

    private void renderRows(@NonNull List<ContactListAdapter.RowItem> rows) {
        currentRows.clear();
        currentRows.addAll(rows);
        adapter.submit(currentRows);

        List<String> letters = collectSectionLetters(currentRows);
        indexBar.setLetters(letters);
        indexBar.setVisibility(letters.isEmpty() ? View.GONE : View.VISIBLE);

        if (letters.isEmpty()) {
            activeIndexLetter = null;
            indexBar.setActiveLetter(null);
            return;
        }

        if (TextUtils.isEmpty(activeIndexLetter) || !letters.contains(activeIndexLetter)) {
            activeIndexLetter = letters.get(0);
        }
        indexBar.setActiveLetter(activeIndexLetter);
        syncIndexByFirstVisibleSection();
    }

    @NonNull
    private List<String> collectSectionLetters(@NonNull List<ContactListAdapter.RowItem> rows) {
        LinkedHashSet<String> letters = new LinkedHashSet<>();
        for (ContactListAdapter.RowItem row : rows) {
            if (row instanceof ContactListAdapter.SectionRow) {
                letters.add(((ContactListAdapter.SectionRow) row).section);
            }
        }
        return new ArrayList<>(letters);
    }

    private void scrollToSection(@Nullable String letter) {
        if (TextUtils.isEmpty(letter)) {
            return;
        }
        int position = adapter.findSectionPosition(letter);
        if (position >= 0) {
            layoutManager.scrollToPositionWithOffset(position, 0);
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
            ContactListAdapter.RowItem row = currentRows.get(i);
            if (row instanceof ContactListAdapter.SectionRow) {
                String section = ((ContactListAdapter.SectionRow) row).section;
                if (!TextUtils.equals(activeIndexLetter, section)) {
                    activeIndexLetter = section;
                    indexBar.setActiveLetter(section);
                }
                return;
            }
        }
    }

    private void refreshNewFriendBadge() {
        Conversation conversation = new Conversation(Conversation.ConversationType.SYSTEM, FRIEND_APPLY);
        ConversationInfo info = JIM.getInstance().getConversationManager().getConversationInfo(conversation);
        hasNewFriendUnread = info != null && info.getUnreadCount() > 0;
        if (adapter != null) {
            adapter.updateActionTip(ContactListAdapter.ACTION_NEW_FRIENDS, hasNewFriendUnread);
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
