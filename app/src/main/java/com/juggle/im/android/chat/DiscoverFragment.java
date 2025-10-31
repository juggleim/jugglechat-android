package com.juggle.im.android.chat;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.juggle.im.android.R;

/**
 * Simple "Discover" fragment. Shows a list of discovery items. The top item is "朋友圈" and opens MomentsActivity.
 */
public class DiscoverFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_discover, container, false);

        View btn = view.findViewById(R.id.item_moments);
        if (btn != null) {
            btn.setOnClickListener(v -> {
                Intent it = new Intent(requireContext(), MomentsActivity.class);
                startActivity(it);
            });
        }

        return view;
    }
}
