package com.example.demo.friends;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demo.bean.FriendBean;
import com.example.demo.bean.HttpResult;
import com.example.demo.bean.ListResult;
import com.example.demo.common.adapter.CommonAdapter;
import com.example.demo.common.adapter.MultiItemTypeAdapter;
import com.example.demo.common.widgets.TitleBar;
import com.example.demo.databinding.FragmentFriendsGroupsBinding;
import com.example.demo.friends.add.AddFriendListActivity;
import com.example.demo.http.CustomCallback;
import com.example.demo.http.ServiceManager;
import com.jet.im.kit.SendbirdUIKit;
import com.jet.im.kit.activities.ChannelActivity;
import com.juggle.im.model.Conversation;

/**
 * Fragment displaying the member list in the channel.
 */
public class FriendListFragment extends Fragment {
    private FragmentFriendsGroupsBinding binding;
    private CommonAdapter<FriendBean> adapter=new FriendAdapter();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFriendsGroupsBinding.inflate(inflater, container, false);
        adapter.setOnItemClickListener(new MultiItemTypeAdapter.OnItemClickListener<FriendBean>() {
            @Override
            public void onItemClick(View view, RecyclerView.ViewHolder holder, FriendBean friendBean, int position) {
                startActivity(ChannelActivity.newIntent(requireContext(), Conversation.ConversationType.PRIVATE.getValue(), friendBean.getUser_id()));
            }

            @Override
            public boolean onItemLongClick(View view, RecyclerView.ViewHolder holder, FriendBean friendBean, int position) {
                return false;
            }
        });
        binding.rvList.setAdapter(adapter);
        binding.rvList.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.tvTitle.setTitle("friend");
        binding.tvTitle.setOnRightIconClickListener(new TitleBar.OnRightIconClickListener() {
            @Override
            public void onRightIconClick(View v) {
                startActivity(AddFriendListActivity.newIntent(getContext()));
            }
        });
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    protected void refresh() {
        ServiceManager.friendsService().getFriendList(SendbirdUIKit.userId, "0", 200).enqueue(new CustomCallback<HttpResult<ListResult<FriendBean>>, ListResult<FriendBean>>() {
            @Override
            public void onSuccess(ListResult<FriendBean> listResult) {
                if (listResult.getItems() != null && !listResult.getItems().isEmpty()) {
                    adapter.setData(listResult.getItems());
                }

            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }
}
