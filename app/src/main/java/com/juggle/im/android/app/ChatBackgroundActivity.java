package com.juggle.im.android.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import com.juggle.im.android.component.AbsAppActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.android.R;
import com.juggle.im.android.widget.JuggleCheckBox;

public class ChatBackgroundActivity extends AbsAppActivity {
    private int selectedIndex;
    private BackgroundAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_background);

        selectedIndex = AppSettingsStore.getChatBackgroundIndex(this);

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        findViewById(R.id.tv_save).setOnClickListener(v -> {
            AppSettingsStore.setChatBackgroundIndex(this, selectedIndex);
            setResult(RESULT_OK);
            finish();
        });

        RecyclerView recyclerView = findViewById(R.id.rv_backgrounds);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new BackgroundAdapter(AppSettingsStore.getChatBackgrounds());
        recyclerView.setAdapter(adapter);
    }

    private final class BackgroundAdapter extends RecyclerView.Adapter<BackgroundAdapter.Holder> {
        private final int[] backgrounds;

        private BackgroundAdapter(int[] backgrounds) {
            this.backgrounds = backgrounds;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_background_option, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.preview.setImageResource(backgrounds[position]);
            holder.checkBox.setVisibility(selectedIndex == position ? View.VISIBLE : View.GONE);
            holder.checkBox.setChecked(selectedIndex == position);
            holder.itemView.setOnClickListener(v -> {
                int old = selectedIndex;
                selectedIndex = holder.getBindingAdapterPosition();
                if (old >= 0) {
                    notifyItemChanged(old);
                }
                notifyItemChanged(selectedIndex);
            });
        }

        @Override
        public int getItemCount() {
            return backgrounds.length;
        }

        private final class Holder extends RecyclerView.ViewHolder {
            private final ImageView preview;
            private final JuggleCheckBox checkBox;

            private Holder(@NonNull View itemView) {
                super(itemView);
                preview = itemView.findViewById(R.id.iv_background);
                checkBox = itemView.findViewById(R.id.checkbox);
            }
        }
    }
}
