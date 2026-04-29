package com.juggle.im.android.app;

import android.app.Activity;
import android.content.Context;
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
    private static final String EXTRA_PAGE_TITLE = "extra_page_title";
    private static final String EXTRA_CONTENT_PREFIX = "extra_content_prefix";
    private static final String EXTRA_CATEGORY = "extra_category";
    private static final String DEFAULT_PAGE_TITLE = "意见反馈";
    private static final String DEFAULT_CATEGORY = "个人反馈";
    private static final String REPORT_PAGE_TITLE = "举报投诉";

    private EditText inputView;
    private TextView counterView;
    private Button submitButton;

    private final List<FeedbackImageItem> images = new ArrayList<>();
    private FeedbackImageAdapter adapter;
    private boolean isSubmitting;
    private String pageTitle = DEFAULT_PAGE_TITLE;
    private String category = DEFAULT_CATEGORY;
    private String contentPrefix = "";

    /**
     * 构建通用意见反馈页面启动参数。
     *
     * @param context 页面上下文
     * @return 反馈页面 Intent（默认标题“意见反馈”）
     */
    public static Intent intentForFeedback(Context context) {
        Intent intent = new Intent(context, FeedbackActivity.class);
        intent.putExtra(EXTRA_PAGE_TITLE, DEFAULT_PAGE_TITLE);
        intent.putExtra(EXTRA_CATEGORY, DEFAULT_CATEGORY);
        return intent;
    }

    /**
     * 构建举报投诉页面启动参数。
     *
     * @param context           页面上下文
     * @param reportContentSeed 举报内容前缀（通常传会话 ID，提交时会与用户输入拼接）
     * @return 反馈页面 Intent（标题为“举报投诉”）
     */
    public static Intent intentForReport(Context context, String reportContentSeed) {
        Intent intent = new Intent(context, FeedbackActivity.class);
        intent.putExtra(EXTRA_PAGE_TITLE, REPORT_PAGE_TITLE);
        intent.putExtra(EXTRA_CATEGORY, REPORT_PAGE_TITLE);
        intent.putExtra(EXTRA_CONTENT_PREFIX, reportContentSeed == null ? "" : reportContentSeed.trim());
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);
        resolveIntentArgs();

        inputView = findViewById(R.id.et_feedback_input);
        counterView = findViewById(R.id.tv_counter);
        submitButton = findViewById(R.id.btn_submit);

        ((TextView) findViewById(R.id.tv_title)).setText(pageTitle);
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

    /**
     * 简要描述：
     * 通过 Intent 参数兼容“意见反馈”和“举报投诉”两种入口，避免复制页面逻辑。
     */
    private void resolveIntentArgs() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        String argTitle = intent.getStringExtra(EXTRA_PAGE_TITLE);
        String argCategory = intent.getStringExtra(EXTRA_CATEGORY);
        String argPrefix = intent.getStringExtra(EXTRA_CONTENT_PREFIX);

        if (!TextUtils.isEmpty(argTitle)) {
            pageTitle = argTitle.trim();
        }
        if (!TextUtils.isEmpty(argCategory)) {
            category = argCategory.trim();
        }
        if (!TextUtils.isEmpty(argPrefix)) {
            contentPrefix = argPrefix.trim();
        }
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

        String inputContent = inputView.getText() == null ? "" : inputView.getText().toString().trim();
        if (TextUtils.isEmpty(inputContent) && images.isEmpty()) {
            Toast.makeText(this, "请填写反馈内容", Toast.LENGTH_SHORT).show();
            return;
        }
        String submitContent = buildSubmitContent(inputContent);

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
        ServiceManager.getUserService().submitFeedback(category, submitContent, imageUrls, new ArrayList<>(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                setSubmitting(false);
                String successText = isReportMode() ? "已提交举报，1s后自动返回" : "反馈成功，1s后自动返回";
                Toast.makeText(FeedbackActivity.this, successText, Toast.LENGTH_SHORT).show();
                inputView.postDelayed(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        finish();
                    }
                }, 1000);
            }

            @Override
            public void onError(int code, String message) {
                setSubmitting(false);
                Toast.makeText(FeedbackActivity.this, "反馈失败：" + safeText(message), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isReportMode() {
        return TextUtils.equals(pageTitle, REPORT_PAGE_TITLE);
    }

    private String buildSubmitContent(String inputContent) {
        if (TextUtils.isEmpty(contentPrefix)) {
            return inputContent;
        }
        return contentPrefix + "|" + (inputContent == null ? "" : inputContent);
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
                holder.image.setImageResource(R.drawable.ic_feedback_submit);
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
