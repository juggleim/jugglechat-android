package com.juggle.im.android.chat;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.model.UiConversation;
import com.juggle.im.model.Conversation;

import java.util.List;

/**
 * Conversation list as a Fragment so it can be hosted inside MainActivity.
 */
public class ConversationListFragment extends Fragment implements ConversationListAdapter.OnConversationClickListener {
    private RecyclerView conversationListView;
    private ConversationListAdapter conversationListAdapter;
    private PopupWindow popupWindow;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_conversation_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        conversationListView = view.findViewById(R.id.rv_conversation_list);
        conversationListAdapter = new ConversationListAdapter();
        conversationListAdapter.setOnConversationClickListener(this);
        conversationListView.setLayoutManager(new LinearLayoutManager(requireContext()));
        conversationListView.setAdapter(conversationListAdapter);
    }

    public void upsertConversations(List<UiConversation> dataSet) {
        conversationListAdapter.upsertConversations(dataSet);
    }

    @Override
    public void onConversationClick(UiConversation uiConversation) {
        JIM.getInstance().getConversationManager().clearUnreadCount(uiConversation.getConversationInfo().getConversation(), null);
        Intent intent = ConversationActivity.intentFor(this.getActivity(),
                uiConversation.getConversationInfo().getConversation().getConversationId(),
                uiConversation.getName(),
                uiConversation.getConversationInfo().getConversation().getConversationType().equals(Conversation.ConversationType.GROUP),
                uiConversation.isTop(),
                uiConversation.isMuted());
        startActivity(intent);
    }

    @Override
    public void onConversationLongClick(UiConversation uiConversation) {
        showPopupMenu(uiConversation);
    }

    private void showPopupMenu(UiConversation uiConversation) {
        // Inflate the popup menu layout
        View menuView = LayoutInflater.from(requireContext()).inflate(R.layout.layout_conversation_popup_menu, null);

        // Create the popup window
        popupWindow = new PopupWindow(menuView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(10);
        
        // 设置PopupWindow消失监听器，用于清除选中状态
        popupWindow.setOnDismissListener(() -> {
            conversationListAdapter.clearSelectedPosition();
        });

        // Get references to menu items
        TextView deleteItem = menuView.findViewById(R.id.menu_delete);
        TextView topItem = menuView.findViewById(R.id.menu_top);
        TextView muteItem = menuView.findViewById(R.id.menu_mute);

        // Set dynamic text based on conversation state
        topItem.setText(uiConversation.isTop() ? "取消置顶" : "置顶");
        muteItem.setText(uiConversation.isMuted() ? "取消免打扰" : "免打扰");

        // Set click listeners
        deleteItem.setOnClickListener(v -> {
            deleteConversation(uiConversation);
            popupWindow.dismiss();
        });

        topItem.setOnClickListener(v -> {
            toggleTopConversation(uiConversation);
            popupWindow.dismiss();
        });

        muteItem.setOnClickListener(v -> {
            toggleMuteConversation(uiConversation);
            popupWindow.dismiss();
        });

        RecyclerView.ViewHolder viewHolder = conversationListView.findViewHolderForAdapterPosition(
                conversationListAdapter.getPosition(uiConversation)
        );
        if (viewHolder != null) {
            View anchorView = viewHolder.itemView;

            // 设置选中状态
            conversationListAdapter.setSelectedPosition(conversationListAdapter.getPosition(uiConversation));

            int[] location = new int[2];
            anchorView.getLocationOnScreen(location); // 获取 item 在屏幕中的绝对坐标

            int anchorX = location[0];
            int anchorY = location[1];

            // 获取 item 高度和屏幕宽度
            int itemHeight = anchorView.getHeight();
            int screenWidth = anchorView.getResources().getDisplayMetrics().widthPixels;

            // 获取 PopupWindow 的宽高（需提前测量）
            popupWindow.getContentView().measure(
                    View.MeasureSpec.UNSPECIFIED,
                    View.MeasureSpec.UNSPECIFIED
            );
            int popupWidth = popupWindow.getContentView().getMeasuredWidth();
            int popupHeight = popupWindow.getContentView().getMeasuredHeight();

            // 计算横向位置：居中到屏幕中间
            int x = (screenWidth - popupWidth) / 2;

            // 计算纵向位置：贴在 item 上方
            int y = anchorY - popupHeight;

            // 注意：Gravity.NO_GRAVITY 才能让 x、y 生效
            popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y);
        }
    }

    private void deleteConversation(UiConversation uiConversation) {
        JIM.getInstance().getConversationManager().deleteConversationInfo(
                uiConversation.getConversationInfo().getConversation(),
                null);
        conversationListAdapter.removeConversation(uiConversation);
    }

    private void toggleTopConversation(UiConversation uiConversation) {
        boolean newTopStatus = !uiConversation.isTop();
        JIM.getInstance().getConversationManager().setTop(
                uiConversation.getConversationInfo().getConversation(),
                newTopStatus,
                null);
        uiConversation.getConversationInfo().setTop(newTopStatus);
        int idx = conversationListAdapter.getPosition(uiConversation);
        conversationListAdapter.notifyItemChanged(idx);
    }

    private void toggleMuteConversation(UiConversation uiConversation) {
        boolean newMuteStatus = !uiConversation.isMuted();
        JIM.getInstance().getConversationManager().setMute(
                uiConversation.getConversationInfo().getConversation(),
                newMuteStatus,
                null);
        int idx = conversationListAdapter.getPosition(uiConversation);
        conversationListAdapter.notifyItemChanged(idx);
    }
}