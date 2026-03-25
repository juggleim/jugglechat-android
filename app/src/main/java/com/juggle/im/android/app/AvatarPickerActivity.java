package com.juggle.im.android.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import com.juggle.im.android.component.AbsAppActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.juggle.im.android.R;
import com.juggle.im.android.widget.JuggleCheckBox;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AvatarPickerActivity extends AbsAppActivity {
    public static final String EXTRA_SELECTED_PATH = "extra_selected_path";

    private final List<String> imagePaths = new ArrayList<>();
    private String selectedPath;
    private Button previewButton;
    private Button confirmButton;
    private AvatarImageAdapter adapter;

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) {
                    loadImages();
                } else {
                    Toast.makeText(this, "未授予相册权限", Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    private final ActivityResultLauncher<Intent> previewLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                    return;
                }
                String path = result.getData().getStringExtra(AvatarPreviewActivity.EXTRA_IMAGE_PATH);
                if (TextUtils.isEmpty(path)) {
                    return;
                }
                Intent intent = new Intent();
                intent.putExtra(EXTRA_SELECTED_PATH, path);
                setResult(RESULT_OK, intent);
                finish();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar_picker);

        previewButton = findViewById(R.id.btn_preview);
        confirmButton = findViewById(R.id.btn_confirm);

        RecyclerView recyclerView = findViewById(R.id.rv_avatar_images);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new AvatarImageAdapter();
        recyclerView.setAdapter(adapter);

        findViewById(R.id.iv_close).setOnClickListener(v -> finish());
        previewButton.setOnClickListener(v -> openPreview());
        confirmButton.setOnClickListener(v -> confirmSelection());

        updateButtons();

        if (hasImagePermission()) {
            loadImages();
        } else {
            permissionLauncher.launch(requiredPermission());
        }
    }

    private void openPreview() {
        if (TextUtils.isEmpty(selectedPath)) {
            return;
        }
        Intent intent = new Intent(this, AvatarPreviewActivity.class);
        intent.putExtra(AvatarPreviewActivity.EXTRA_IMAGE_PATH, selectedPath);
        previewLauncher.launch(intent);
    }

    private void confirmSelection() {
        if (TextUtils.isEmpty(selectedPath)) {
            Toast.makeText(this, "请选择头像", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent();
        intent.putExtra(EXTRA_SELECTED_PATH, selectedPath);
        setResult(RESULT_OK, intent);
        finish();
    }

    private void loadImages() {
        new Thread(() -> {
            List<String> result = new ArrayList<>();
            Cursor cursor = null;
            try {
                String[] projection = {
                        MediaStore.Images.Media.DATA,
                        MediaStore.Images.Media.DATE_ADDED
                };
                cursor = getContentResolver().query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        null,
                        null,
                        MediaStore.Images.Media.DATE_ADDED + " DESC");
                if (cursor != null) {
                    int pathColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                    int count = 0;
                    while (cursor.moveToNext() && count < 200) {
                        if (pathColumn == -1) {
                            continue;
                        }
                        String path = cursor.getString(pathColumn);
                        if (TextUtils.isEmpty(path)) {
                            continue;
                        }
                        File file = new File(path);
                        if (!file.exists()) {
                            continue;
                        }
                        result.add(path);
                        count++;
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            runOnUiThread(() -> {
                imagePaths.clear();
                imagePaths.addAll(result);
                adapter.notifyDataSetChanged();
                if (imagePaths.isEmpty()) {
                    Toast.makeText(this, "未找到可用图片", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void updateButtons() {
        boolean hasSelection = !TextUtils.isEmpty(selectedPath);
        previewButton.setEnabled(hasSelection);
        confirmButton.setEnabled(hasSelection);
        previewButton.setText(hasSelection ? "预览（1）" : "预览");
    }

    private boolean hasImagePermission() {
        return ContextCompat.checkSelfPermission(this, requiredPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    private String requiredPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return Manifest.permission.READ_MEDIA_IMAGES;
        }
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private final class AvatarImageAdapter extends RecyclerView.Adapter<AvatarImageAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_avatar_picker_image, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            String path = imagePaths.get(position);
            Glide.with(AvatarPickerActivity.this)
                    .load(new File(path))
                    .placeholder(R.drawable.default_image)
                    .error(R.drawable.default_image)
                    .centerCrop()
                    .into(holder.image);
            boolean selected = path.equals(selectedPath);
            holder.mask.setVisibility(selected ? View.VISIBLE : View.GONE);
            holder.checkBox.setVisibility(selected ? View.VISIBLE : View.GONE);
            holder.checkBox.setChecked(selected);
            holder.itemView.setOnClickListener(v -> {
                selectedPath = path;
                notifyDataSetChanged();
                updateButtons();
            });
        }

        @Override
        public int getItemCount() {
            return imagePaths.size();
        }

        private final class Holder extends RecyclerView.ViewHolder {
            private final ImageView image;
            private final View mask;
            private final JuggleCheckBox checkBox;

            private Holder(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.iv_image);
                mask = itemView.findViewById(R.id.view_mask);
                checkBox = itemView.findViewById(R.id.checkbox);
            }
        }
    }
}
