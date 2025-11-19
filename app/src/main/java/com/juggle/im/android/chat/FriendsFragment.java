package com.juggle.im.android.chat;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.android.R;
import com.juggle.im.android.chat.component.UserListAdapter;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.server.beans.FriendsListData;
import com.juggle.im.android.server.beans.FriendBean;

import java.util.ArrayList;
import java.util.List;

public class FriendsFragment extends Fragment {

    private RecyclerView recyclerView;
    private UserListAdapter adapter;
    private SelectionListener selectionListener;
    private String selectionMode = UserListAdapter.LIST_MODE_NORMAL;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friends_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.rv_friends_list);
        adapter = new UserListAdapter();
        adapter.setMode(selectionMode);
        adapter.setSelectionChangedListener((item, selected) -> {
            if (selectionListener != null) selectionListener.onMemberSelected(item, selected);
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        loadFriends();
    }

    public void setSelectionMode(String mode) {
        this.selectionMode = mode;
    }

    public void setSelectionListener(SelectionListener l) { this.selectionListener = l; }

    public void uncheckUser(String userId) { if (adapter != null) adapter.uncheckUser(userId); }

    public interface SelectionListener { void onMemberSelected(UserListAdapter.UserInfoObj member, boolean selected); }

    private void loadFriends() {
        ServiceManager.getUserService().getFriendsList(1, 50, null, new ApiCallback<FriendsListData>() {
            @Override
            public void onSuccess(FriendsListData data) {
                List<FriendBean> items = data != null ? data.getItems() : null;
                List<UserListAdapter.UserInfoObj> memberList = new ArrayList<>();
                for (FriendBean member : items) {
                    boolean disabled = false;
                    UserListAdapter.UserInfoObj userInfoObj = new UserListAdapter.UserInfoObj(disabled);
                    userInfoObj.setUserId(member.getUser_id());
                    userInfoObj.setName(member.getNickname());
                    userInfoObj.setAvatar(member.getAvatar());
                    memberList.add(userInfoObj);
                }
                adapter.setItems(memberList);
            }

            @Override
            public void onError(int code, String message) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), "加载好友失败: " + message, Toast.LENGTH_SHORT).show());
                }
            }
        });
    }
}
