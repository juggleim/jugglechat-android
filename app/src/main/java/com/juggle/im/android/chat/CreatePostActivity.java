package com.juggle.im.android.chat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.event.MomentPublishedEvent;
import com.juggle.im.android.widget.SubmitButtonState;

import org.greenrobot.eventbus.EventBus;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIM;
import com.juggle.im.JIMConst;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.utils.FileUtils;
import com.juggle.im.model.Moment;
import com.juggle.im.model.MomentMedia;
import com.juggle.im.android.utils.AvatarUtils;
import com.qiniu.android.utils.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import com.juggle.im.android.utils.LogUtils;

public class CreatePostActivity extends AbsAppActivity {
    private EditText editPostContent;
    private RecyclerView mImageRecyclerView;
    private MediaAdapter mMediaAdapter;
    private TextView tvSend;
    private SubmitButtonState sendButtonState;
    private LinkedHashMap<String, String> mImageUrls = new LinkedHashMap<>();
    private String mVideoUrl = null;
    private static final int REQUEST_CODE_PICK_IMAGES = 1001;

    public static void start(Context context) {
        Intent intent = new Intent(context, CreatePostActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);

        Toolbar toolbar = findViewById(R.id.toolbar_create_post);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        toolbar.setNavigationOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(editPostContent.getWindowToken(), 0);
            editPostContent.clearFocus();
            finish();
        });

        editPostContent = findViewById(R.id.edit_post_content);
        mImageRecyclerView = findViewById(R.id.rv_images);
        tvSend = findViewById(R.id.tv_send);

        // 发送按钮点击事件
        sendButtonState = SubmitButtonState.bind(tvSend, R.string.common_sending);
        tvSend.setOnClickListener(v -> submitPost());

        // 初始化RecyclerView
        mImageRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        mMediaAdapter = new MediaAdapter(this, mImageUrls);
        mImageRecyclerView.setAdapter(mMediaAdapter);

        // 处理从其他页面传递过来的图片URL
        Intent intent = getIntent();
        if (intent != null) {
            ArrayList<String> imageUrls = intent.getStringArrayListExtra("image_urls");
            if (imageUrls != null && !imageUrls.isEmpty()) {
                for (String path: imageUrls) {
                    mImageUrls.put(path, "");
                    String u = FileUtils.convertContentUriToFile(getApplicationContext(), path);
                    JIM.getInstance().getMessageManager().uploadImage(u, new JIMConst.IResultCallback<String>() {
                        @Override
                        public void onSuccess(String s) {
                            mImageUrls.put(path, s);
                        }

                        @Override
                        public void onError(int i) {
                            Log.e("createpost", "error: " + i);
                        }
                    });
                }
                mMediaAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_IMAGES && resultCode == RESULT_OK && data != null) {
            ArrayList<String> selectedImages = data.getStringArrayListExtra("selected_images");
            if (selectedImages != null && !selectedImages.isEmpty()) {
                // 添加新选择的图片，最多9张
                for (String imageUrl : selectedImages) {
                    if (mImageUrls.size() < 9) {
                        mImageUrls.put(imageUrl, "");
                    }
                }
                mMediaAdapter.notifyDataSetChanged();
            }
        }
    }

    private void submitPost() {
        String content = editPostContent.getText().toString().trim();
        if (TextUtils.isEmpty(content) && mImageUrls.isEmpty() && TextUtils.isEmpty(mVideoUrl)) {
            Toast.makeText(this, R.string.moments_content_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查是否有图片还在上传中
        for (String url : mImageUrls.values()) {
            if (StringUtils.isBlank(url)) {
                Toast.makeText(this, R.string.moments_image_uploading, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Build media list
        List<MomentMedia> mediaList = new ArrayList<>();
        if (!mImageUrls.isEmpty()) {
            for (String url : mImageUrls.values()) {
                if (StringUtils.isBlank(url)) continue;
                MomentMedia media = new MomentMedia();
                media.setUrl(url);
                media.setType(MomentMedia.MomentMediaType.IMAGE);
                mediaList.add(media);
            }
        }

        if (!sendButtonState.begin()) {
            return;
        }

        JIM.getInstance().getMomentManager().addMoment(content, mediaList, new JIMConst.IResultCallback<Moment>() {
            @Override
            public void onSuccess(Moment data) {
                // TIPS: 广播发布事件，已打开的动态流据此自行刷新，不依赖 startActivityForResult 的返回链路
                EventBus.getDefault().post(new MomentPublishedEvent(
                        JIM.getInstance().getCurrentUserId(),
                        data == null ? null : data.getMomentId()));
                runOnUiThread(() -> {
                    Toast.makeText(CreatePostActivity.this, R.string.moments_post_success, Toast.LENGTH_SHORT).show();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(editPostContent.getWindowToken(), 0);
                    editPostContent.clearFocus();
                    Intent it = new Intent();
                    it.putExtra("result", 0);
                    setResult(RESULT_OK, it);
                    finish();
                });
            }

            @Override
            public void onError(int errorCode) {
                runOnUiThread(() -> {
                    sendButtonState.end();
                    LogUtils.serverError("moments", "createPost", errorCode, "");
                    Toast.makeText(CreatePostActivity.this, R.string.moments_post_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // 媒体适配器内部类
    private class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.MediaViewHolder> {
        private Context mContext;
        private LinkedHashMap<String, String> mImageUrls;

        public MediaAdapter(Context context, LinkedHashMap<String, String> imageUrls) {
            this.mContext = context;
            this.mImageUrls = imageUrls;
        }

        @Override
        public MediaViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_media, parent, false);
            return new MediaViewHolder(view);
        }

        @Override
        public void onBindViewHolder(MediaViewHolder holder, int position) {
            // 如果是最后一个位置且图片数量小于9并且没有视频，则显示添加按钮
            if (position == mImageUrls.size() && mImageUrls.size() < 9 && TextUtils.isEmpty(mVideoUrl)) {
                holder.btnAdd.setVisibility(View.VISIBLE);
                holder.ivMedia.setVisibility(View.GONE);
                holder.btnDelete.setVisibility(View.GONE);

                holder.btnAdd.setOnClickListener(v -> {
                    // 跳转到相册选择图片
                    Intent intent = new Intent(CreatePostActivity.this, AlbumActivity.class);
                    startActivityForResult(intent, REQUEST_CODE_PICK_IMAGES);
                });
            } else {
                holder.btnAdd.setVisibility(View.GONE);
                holder.ivMedia.setVisibility(View.VISIBLE);
                holder.btnDelete.setVisibility(View.VISIBLE);

                // 显示图片
                final int pos = holder.getAdapterPosition();
                if (pos >= 0 && pos < mImageUrls.size()) {
                    final String imageUrl = new ArrayList<>(mImageUrls.keySet()).get(pos);
                    AvatarUtils.loadImage(holder.ivMedia, imageUrl);

                    // 删除按钮点击事件
                    holder.btnDelete.setOnClickListener(v -> {
                        mImageUrls.remove(imageUrl);
                        notifyDataSetChanged();
                    });

                    // 图片点击事件
                    holder.ivMedia.setOnClickListener(v -> {
                        // 可以添加预览功能
                    });
                }
            }
        }

        @Override
        public int getItemCount() {
            // 如果没有视频且图片数量小于9，则多显示一个添加按钮
            if (TextUtils.isEmpty(mVideoUrl) && mImageUrls.size() < 9) {
                return mImageUrls.size() + 1;
            } else {
                return mImageUrls.size();
            }
        }

        class MediaViewHolder extends RecyclerView.ViewHolder {
            ImageView ivMedia;
            ImageView btnAdd;
            ImageView btnDelete;

            MediaViewHolder(View itemView) {
                super(itemView);
                ivMedia = itemView.findViewById(R.id.iv_media);
                btnAdd = itemView.findViewById(R.id.btn_add);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
        }
    }
}
