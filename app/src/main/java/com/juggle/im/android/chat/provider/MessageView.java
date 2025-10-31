package com.juggle.im.android.chat.provider;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Generic base for all message content views used by the adapter.
 * T is the UI wrapper type (UiMessage), K is the underlying SDK message content type.
 */
public abstract class MessageView<T, K> extends RecyclerView.ViewHolder {
    public MessageView(ViewGroup container, int resId) {
        super(LayoutInflater.from(container.getContext()).inflate(resId, container, false));
    }

    public MessageView(@NonNull ViewGroup itemView) {
        super(itemView);
    }

    /**
     * Bind UI wrapper to the view.
     *
     * @param message UiMessage wrapper
     * @param content SDK message content
     * @param isGroup whether the conversation is a group
     */
    public abstract void bind(T message, K content, boolean isGroup);
}
