package com.juggle.im.android.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.juggle.im.JIM;
import com.juggle.im.JIMConst;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.AlbumActivity;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 意见反馈页面
 */
public class FeedbackActivity extends AbsAppActivity {
    private static final int REQ_PICK_IMAGES = 2101;
    private static final int MAX_IMAGE_COUNT = 8;

    private EditText inputView;
    private TextView counterView;
    private Button submitButton;

    private final List<FeedbackImageItem> images = new ArrayList<>();
    private FeedbackImageAdapter adapter;
    private boolean isSubmitting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        inputView = findViewById(R.id.et_feedback_input);
        counterView = findViewById(R.id.tv_counter);
        submitButton = findViewById(R.id.btn_submit);

        ((TextView) findViewById(R.id.tv_title)).setText("意见反馈");
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.rv_feedback_images);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 4));
        adapter = new FeedbackImageAdapter();
        recyclerView.setAdapter(adapter);

        inputView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                counterView.setText(s.length() + "/300");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        submitButton.setOnClickListener(v -> submitFeedback());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_IMAGES || resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        ArrayList<String> selected = data.getStringArrayListExtra("selected_images");
        if (selected == null || selected.isEmpty()) {
            return;
        }
        for (String path : selected) {
            if (images.size() >= MAX_IMAGE_COUNT) {
                break;
            }
            if (containsPath(path)) {
                continue;
            }
            FeedbackImageItem item = new FeedbackImageItem(path);
            images.add(item);
            adapter.notifyDataSetChanged();
            uploadImage(item);
        }
    }

    private boolean containsPath(String path) {
        for (FeedbackImageItem item : images) {
            if (TextUtils.equals(item.localPath, path)) {
                return true;
            }
        }
        return false;
    }

    private void pickImages() {
        if (images.size() >= MAX_IMAGE_COUNT) {
            Toast.makeText(this, "最多添加 8 张图片", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivityForResult(new Intent(this, AlbumActivity.class), REQ_PICK_IMAGES);
    }

    private void uploadImage(FeedbackImageItem item) {
        item.uploading = true;
        adapter.notifyDataSetChanged();
        JIM.getInstance().getMessageManager().uploadImage(item.localPath, new JIMConst.IResultCallback<String>() {
            @Override
            public void onSuccess(String url) {
                runOnUiThread(() -> {
                    if (!images.contains(item)) {
                        return;
                    }
                    item.uploading = false;
                    item.remoteUrl = url == null ? "" : url;
                    adapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onError(int code) {
                runOnUiThread(() -> {
                    images.remove(item);
                    adapter.notifyDataSetChanged();
                    Toast.makeText(FeedbackActivity.this, "图片上传失败", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void submitFeedback() {
        if (isSubmitting) {
            return;
        }

        String content = inputView.getText() == null ? "" : inputView.getText().toString().trim();
        if (TextUtils.isEmpty(content) && images.isEmpty()) {
            Toast.makeText(this, "请填写反馈内容", Toast.LENGTH_SHORT).show();
            return;
        }

        for (FeedbackImageItem item : images) {
            if (item.uploading) {
                Toast.makeText(this, "图片上传中，请稍候", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        List<String> imageUrls = new ArrayList<>();
        for (FeedbackImageItem item : images) {
            if (!TextUtils.isEmpty(item.remoteUrl)) {
                imageUrls.add(item.remoteUrl);
            }
        }

        setSubmitting(true);
        ServiceManager.getUserService().submitFeedback("个人反馈", content, imageUrls, new ArrayList<>(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                setSubmitting(false);
                Toast.makeText(FeedbackActivity.this, "反馈成功", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(int code, String message) {
                setSubmitting(false);
                Toast.makeText(FeedbackActivity.this, "反馈失败：" + safeText(message), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setSubmitting(boolean submitting) {
        isSubmitting = submitting;
        submitButton.setEnabled(!submitting);
        submitButton.setText(submitting ? "提交中..." : "提交");
    }

    private String safeText(String value) {
        if (value == null) {
            return "未知错误";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "未知错误" : trimmed;
    }

    private final class FeedbackImageAdapter extends RecyclerView.Adapter<FeedbackImageAdapter.Holder> {
        private static final int TYPE_IMAGE = 1;
        private static final int TYPE_ADD = 2;

        @Override
        public int getItemViewType(int position) {
            if (position < images.size()) {
                return TYPE_IMAGE;
            }
            return TYPE_ADD;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_feedback_image, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            if (getItemViewType(position) == TYPE_ADD) {
                holder.progress.setVisibility(View.GONE);
                holder.remove.setVisibility(View.GONE);
                holder.image.setScaleType(ImageView.ScaleType.CENTER);
                holder.image.setImageResource(R.drawable.ic_add);
                holder.itemView.setOnClickListener(v -> pickImages());
                return;
            }

            FeedbackImageItem item = images.get(position);
            holder.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Glide.with(FeedbackActivity.this)
                    .load(new File(item.localPath))
                    .placeholder(R.drawable.default_image)
                    .error(R.drawable.default_image)
                    .into(holder.image);

            holder.progress.setVisibility(item.uploading ? View.VISIBLE : View.GONE);
            holder.remove.setVisibility(View.VISIBLE);
            holder.remove.setOnClickListener(v -> {
                int index = holder.getBindingAdapterPosition();
                if (index == RecyclerView.NO_POSITION || index >= images.size()) {
                    return;
                }
                images.remove(index);
                notifyDataSetChanged();
            });
            holder.itemView.setOnClickListener(null);
        }

        @Override
        public int getItemCount() {
            if (images.size() >= MAX_IMAGE_COUNT) {
                return images.size();
            }
            return images.size() + 1;
        }

        private final class Holder extends RecyclerView.ViewHolder {
            private final ImageView image;
            private final ImageView remove;
            private final ProgressBar progress;

            private Holder(@NonNull View itemView) {
                super(itemView);
                image = itemView.findViewById(R.id.iv_item_image);
                remove = itemView.findViewById(R.id.iv_remove);
                progress = itemView.findViewById(R.id.progress_upload);
            }
        }
    }

    private static final class FeedbackImageItem {
        private final String localPath;
        private String remoteUrl;
        private boolean uploading;

        private FeedbackImageItem(String localPath) {
            this.localPath = localPath;
            this.remoteUrl = "";
        }
    }
}
