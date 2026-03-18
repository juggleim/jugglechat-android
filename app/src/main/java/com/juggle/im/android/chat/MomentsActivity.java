package com.juggle.im.android.chat;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.gson.Gson;
import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.model.ConfigUtils;
import com.juggle.im.android.server.beans.ImageBean;
import com.juggle.im.android.server.beans.PostBean;
import com.juggle.im.android.server.beans.PostsListData;
import com.juggle.im.android.server.beans.TopCommentBean;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.android.chat.utils.FileUtils;

import android.widget.GridLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Moments page. Collapsing cover image fills status bar area. When scrolled past cover, title bar shows.
 * Simple RecyclerView feed and a comment input anchored above keyboard.
 */
public class MomentsActivity extends AbsAppActivity {

    private AppBarLayout appBarLayout;
    private Toolbar toolbar;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View commentBar;
    private EditText editTextField;
    private static Gson gson = new Gson();
    private PostBean selectedPost = null;
    private TopCommentBean selectedTopComment = null;
    private MomentsAdapter adapter;
    private TextView tvName;
    private ImageView ivAvatar;

    // 分页相关变量
    private int currentPage = 0;
    private int pageSize = 20;
    private boolean isLoading = false;
    private boolean hasMore = true;

    // 拍照相关变量
    private static final int REQUEST_CODE_CHOOSE_PHOTO = 1001;
    private static final int REQUEST_CODE_TAKE_PHOTO = 1002;
    private static final int REQUEST_CODE_CREATE_POST = 1003;
    private Uri photoUri;
    private int currentPaddingBottom;

    protected static class CommentDetail {
        String content;
        String type; //jm:text

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moments);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        appBarLayout = findViewById(R.id.appbar);
        recyclerView = findViewById(R.id.rv_moments);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        tvName = findViewById(R.id.tv_name);
        ivAvatar = findViewById(R.id.iv_avatar);
        tvName.setText(ConfigUtils.myName);
        AvatarUtils.loadAvatar(ivAvatar, ConfigUtils.myAvatarUrl, ConfigUtils.myName);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MomentsAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        // 修改获取评论输入框相关视图的代码
        commentBar = findViewById(R.id.comment_bar);
        editTextField = findViewById(R.id.edit_comment);

        findViewById(R.id.btn_camera).setOnClickListener(v -> {
            showCameraOptions();
        });
        findViewById(R.id.btn_camera).setOnLongClickListener(l -> {
            Intent it = new Intent(MomentsActivity.this, CreatePostActivity.class);
            startActivityForResult(it, 100);
            return true;
        });

        // hide keyboard and comment when tapping content
        CoordinatorLayout root = findViewById(R.id.root_coordinator);
        root.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                hideCommentInput();
            }
            return false;
        });
        recyclerView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN ||
                    event.getAction() == MotionEvent.ACTION_UP) {
                hideCommentInput();
            }
            return false;
        });

        // show/hide toolbar title based on collapse
        appBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
            boolean shown = false;

            @Override
            public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                int total = appBarLayout.getTotalScrollRange();
                if (Math.abs(verticalOffset) >= total - 10) {
                    // Collapsed
                    if (!shown) {
                        shown = true;
                    }
                } else {
                    // Expanded
                    if (shown) {
                        toolbar.setTitle("");
                        shown = false;
                    }
                }
            }
        });

        // 设置下拉刷新监听器
        swipeRefreshLayout.setOnRefreshListener(() -> {
            refreshMoments();
        });

        // 设置上拉加载更多
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                // 判断是否需要加载更多
                if (!isLoading && hasMore && (visibleItemCount + firstVisibleItemPosition) >= totalItemCount && firstVisibleItemPosition >= 0) {
                    loadMoreMoments();
                }
            }
        });
        currentPaddingBottom = recyclerView.getPaddingBottom();


        adapter.setListener(new Listener() {
            @Override
            public void onComment(int position, PostBean post, TopCommentBean topCommentBean) {
                if (topCommentBean != null && JIM.getInstance().getCurrentUserId().equals(topCommentBean.getUser_info().getUserId())) {
                    BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(MomentsActivity.this);
                    View sheetView = LayoutInflater.from(MomentsActivity.this).inflate(R.layout.dialog_delete_comment, null);
                    bottomSheetDialog.setContentView(sheetView);
                    sheetView.findViewById(R.id.btn_delete).setOnClickListener(v -> {
                        bottomSheetDialog.dismiss();
                        List<String> commentIds = new ArrayList<>();
                        commentIds.add(post.getPost_id()); // Assuming post contains comment_id
                        ServiceManager.getMomentService().deleteComment(commentIds, new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void data) {
                                refreshPostItem(post);
                            }

                            @Override
                            public void onError(int code, String message) {
                                runOnUiThread(() -> {
                                    // Handle error
                                    Toast.makeText(MomentsActivity.this, "Failed to delete comment: " + message, Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    });

                    sheetView.findViewById(R.id.btn_cancel).setOnClickListener(v -> bottomSheetDialog.dismiss());

                    bottomSheetDialog.show();
                } else {
                    showPostComment(position, post, topCommentBean);
                }
            }

            @Override
            public void onClickImage(int position, PostBean post, String imageUrl) {
                Intent it = new Intent(MomentsActivity.this, ImagePreviewActivity.class);
                ArrayList<String> urls = new ArrayList<>();
                int startIndex = 0;
                List<ImageBean> images = post.getContent().getImages();
                for (int i = 0; i < images.size(); i++) {
                    ImageBean imageBean = images.get(i);
                    urls.add(imageBean.getUrl());
                    if (imageBean.getUrl().equals(imageUrl)) {
                        startIndex = i;
                    }
                }
                it.putStringArrayListExtra(ImagePreviewActivity.EXTRA_IMAGE_URLS, urls);
                it.putExtra(ImagePreviewActivity.EXTRA_IMAGE_INDEX, startIndex);
                startActivity(it);
            }

            @Override
            public void onDeletePost(int position, PostBean post) {
                ServiceManager.getMomentService().deletePost(Arrays.asList(post.getPost_id()), new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        adapter.notifyItemRemoved(position);
                    }

                    @Override
                    public void onError(int code, String message) {
                        Toast.makeText(MomentsActivity.this, "Failed to delete post: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        findViewById(R.id.btn_send_comment).setOnClickListener(v -> {
            if (selectedPost != null) {
                String commentText = editTextField.getText().toString().trim();
                if (!TextUtils.isEmpty(commentText)) {
                    Map<String, String> comment = new HashMap<>();
                    comment.put("content", commentText);
                    comment.put("type", "jg:text");
                    ServiceManager.getMomentService().addComment(
                            selectedPost.getPost_id(), // postId
                            selectedTopComment != null ? selectedTopComment.getParent_comment_id() : null, // parentCommentId (null for top-level comment)
                            selectedTopComment != null ? selectedTopComment.getUser_info().getUserId() : null, // parentUserId (null for top-level comment)
                            gson.toJson(comment), // text
                            new ApiCallback<Void>() {
                                @Override
                                public void onSuccess(Void data) {
                                    refreshPostItem(selectedPost);
                                    runOnUiThread(() -> {
                                        hideCommentInput();
                                        editTextField.setText(""); // Clear input field
                                    });
                                }

                                @Override
                                public void onError(int code, String message) {
                                    runOnUiThread(() -> {
                                        Log.e("MomentsActivity", "Failed to add comment: " + message);
                                    });
                                }
                            }
                    );
                }
            }
        });

        // 初始加载数据
        swipeRefreshLayout.setRefreshing(true);
        loadMoments();
    }

    private void showCameraOptions() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.dialog_camera_options, null);
        bottomSheetDialog.setContentView(sheetView);

        sheetView.findViewById(R.id.btn_take_photo).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            takePhoto();
        });

        sheetView.findViewById(R.id.btn_choose_from_album).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            chooseFromAlbum();
        });

        sheetView.findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void takePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "请授予相机权限", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            photoUri = FileUtils.createTmpImageFile(this);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            startActivityForResult(takePictureIntent, REQUEST_CODE_TAKE_PHOTO);
        }
    }

    private void chooseFromAlbum() {
        Intent intent = new Intent(this, AlbumActivity.class);
        startActivityForResult(intent, REQUEST_CODE_CHOOSE_PHOTO);
    }

    private void refreshMoments() {
        currentPage = 0;
        hasMore = true;
        loadMoments();
    }

    private void loadMoreMoments() {
        if (!hasMore) return;
        isLoading = true;
        loadMoments();
    }

    private void refreshPostItem(PostBean postBean) {
        ServiceManager.getMomentService().getPost(postBean.getPost_id(), new ApiCallback<PostBean>() {
            @Override
            public void onSuccess(PostBean data) {
                runOnUiThread(() -> {
                    int pos = adapter.getPositionById(data.getPost_id());
                    if (pos >= 0) {
                        adapter.items.set(pos, data);
                        adapter.notifyItemChanged(pos);
                    }
                });

            }

            @Override
            public void onError(int code, String message) {

            }
        });
    }

    private void likePost(int position, PostBean post) {
        ServiceManager.getMomentService().addReaction(post.getPost_id(), "key", "v", new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                refreshPostItem(post);
            }

            @Override
            public void onError(int code, String message) {
                Log.e("MomentsActivity", "Failed to add reaction: " + message);
            }
        });
    }

    private void showPostComment(int position, PostBean post, TopCommentBean topCommentBean) {
        if (commentBar.getVisibility() == GONE) {
            selectedPost = post;
            selectedTopComment = topCommentBean;
            showCommentInput(position);
        }
        if (topCommentBean != null) {
            String hint = topCommentBean.getUser_info().getNickname();
            editTextField.setHint("回复 " + hint + ": ");
        }
    }

    private void showCommentInput(int position) {
        commentBar.setVisibility(VISIBLE);
        editTextField.requestFocus();

        // 监听布局变化以处理键盘弹出后的滚动定位
        View rootView = findViewById(android.R.id.content);
        View.OnLayoutChangeListener layoutChangeListener = new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                // 移除监听器避免重复调用
                v.removeOnLayoutChangeListener(this);

                // 获取布局管理器
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) return;

                // 折叠AppBarLayout确保可见性
                appBarLayout.setExpanded(false, true);

                // 获取目标视图
                View targetView = layoutManager.findViewByPosition(position);
                if (targetView == null) {
                    // 如果目标视图不可见，先滚动到目标位置
                    recyclerView.smoothScrollToPosition(position);
                    // 添加延时处理，确保滚动完成后再进行精确调整
                    recyclerView.postDelayed(() -> {
                        View newTargetView = layoutManager.findViewByPosition(position);
                        if (newTargetView != null) {
                            adjustScrollPosition(newTargetView, layoutManager, position);
                        }
                    }, 300);
                    return;
                }

                // 调整滚动位置
                adjustScrollPosition(targetView, layoutManager, position);
            }

            private void adjustScrollPosition(View targetView, LinearLayoutManager layoutManager, int position) {
                // 计算键盘高度
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                int rootViewHeight = rootView.getHeight();
                int keyboardHeight = screenHeight - rootViewHeight;

                // 计算目标视图在屏幕中的位置
                int[] location = new int[2];
                targetView.getLocationInWindow(location);
                int targetTop = location[1];
                int targetBottom = targetTop + targetView.getHeight();

                // 计算需要滚动的距离
                int scrollDistance = 0;

                if (keyboardHeight > 0) {
                    // 键盘可见，计算目标视图与键盘顶部的距离
                    int visibleAreaBottom = screenHeight - keyboardHeight;
                    // 考虑EditText的高度，确保输入框不被遮挡
                    int editTextHeight = editTextField.getHeight();
                    int safeAreaBottom = visibleAreaBottom - editTextHeight - dpToPx(MomentsActivity.this, 10);

                    if (targetBottom > safeAreaBottom) {
                        // 目标视图被键盘遮挡，需要向上滚动
                        scrollDistance = targetBottom - safeAreaBottom;
                    }
                } else {
                    // 键盘高度无法确定时使用默认策略
                    int editTextHeight = editTextField.getHeight();
                    // 检查是否在底部
                    int totalItemCount = layoutManager.getItemCount();
                    int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
                    boolean isAtBottom = (totalItemCount > 0) && (lastVisiblePosition >= totalItemCount - 1);

                    // 在底部时增加滚动距离确保可见
                    int extraScroll = isAtBottom ? editTextHeight * 3 : editTextHeight * 2;
                    scrollDistance = extraScroll;
                }

                // 执行滚动 TODO 执行无效，已经在最底部
                if (scrollDistance > 0) {
                    recyclerView.smoothScrollBy(0, scrollDistance);
                }
            }
        };

        // 添加布局变化监听器

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            // 添加小延迟确保视图完全布局后再显示键盘
            editTextField.postDelayed(() -> {
                rootView.addOnLayoutChangeListener(layoutChangeListener);
                imm.showSoftInput(editTextField, InputMethodManager.SHOW_IMPLICIT);
            }, 100);
        }
    }

    private void hideCommentInput() {
        if (commentBar.getVisibility() == VISIBLE) {
            editTextField.clearFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(editTextField.getWindowToken(), 0);
            commentBar.setVisibility(View.GONE);
        }
    }

    private void loadMoments() {
        // fetch posts from server and populate adapter
        ServiceManager.getMomentService().getPosts(null, pageSize, currentPage * pageSize, new ApiCallback<PostsListData>() {
            @Override
            public void onSuccess(PostsListData data) {
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    isLoading = false;

                    if (data != null && data.getItems() != null) {
                        if (currentPage == 0) {
                            // 下拉刷新，替换所有数据
                            adapter.setItems(data.getItems());
                        } else {
                            // 上拉加载更多，追加数据
                            adapter.addItems(data.getItems());
                        }

                        // 更新分页参数
                        if (data.getItems().size() < pageSize) {
                            hasMore = false; // 没有更多数据了
                        } else {
                            currentPage++;
                        }
                    } else if (currentPage == 0) {
                        // 第一页就没有数据，清空列表
                        adapter.setItems(new ArrayList<>());
                        hasMore = false;
                    }
                });
            }

            @Override
            public void onError(int code, String message) {
                Log.e("MomentsActivity", "Failed to loadMoments: " + message);
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    isLoading = false;
                    Toast.makeText(MomentsActivity.this, "加载失败: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    interface Listener {
        void onComment(int position, PostBean post, TopCommentBean topCommentBean);

        void onClickImage(int position, PostBean post, String imageUrl);

        void onDeletePost(int position, PostBean post);
    }

    class MomentsAdapter extends RecyclerView.Adapter<MomentsAdapter.VH> {
        private final List<PostBean> items;
        private Listener listener;

        MomentsAdapter(List<PostBean> items) {
            this.items = items;
        }

        void setListener(Listener l) {
            this.listener = l;
        }

        int getPositionById(String postId) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getPost_id().equals(postId))
                    return i;
            }
            return -1;
        }

        void setItems(List<PostBean> newItems) {
            items.clear();
            if (newItems != null) items.addAll(newItems);
            notifyDataSetChanged();
        }

        void addItems(List<PostBean> newItems) {
            if (newItems != null) {
                int startPosition = items.size();
                items.addAll(newItems);
                notifyItemRangeInserted(startPosition, newItems.size());
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_moment_post, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            PostBean post = items.get(position);
            String text = (post.getContent() != null && post.getContent().getText() != null) ? post.getContent().getText() : "";
            // name
            if (post.getUser_info() != null) {
                holder.tvName.setText(post.getUser_info().getNickname());
                if (post.getUser_info().getUserId().equals(JIM.getInstance().getCurrentUserId())) {
                    holder.vDelete.setVisibility(VISIBLE);
                    holder.vDelete.setOnClickListener(v -> {
                        if (listener != null) listener.onDeletePost(position, post);
                    });
                }
            } else {
                holder.tvName.setText("匿名");
            }

            AvatarUtils.loadAvatar(holder.ivAvatar, post.getUser_info().getAvatar(), post.getUser_info().getNickname());

            // content text
            if (TextUtils.isEmpty(text)) {
                holder.tvContent.setVisibility(View.GONE);
            } else {
                holder.tvContent.setVisibility(VISIBLE);
                holder.tvContent.setText(text);
            }

            // media (images)
            holder.mediaContainer.removeAllViews();
            if (post.getContent() != null && post.getContent().getImages() != null && !post.getContent().getImages().isEmpty()) {
                holder.mediaContainer.setVisibility(VISIBLE);
                int imageSize = post.getContent().getImages().size();

                // 根据图片数量确定行列数
                int rows, cols;
                if (imageSize < 4) {
                    // 少于4张，单行展示
                    rows = 1;
                    cols = imageSize;
                } else if (imageSize == 4) {
                    // 4张图片，2行2列展示
                    rows = 2;
                    cols = 2;
                } else {
                    // 多于4张，3列展示
                    cols = 3;
                    rows = (imageSize + 2) / 3; // 向上取整
                }

                // 设置GridLayout的行列数
                GridLayout gridLayout = (GridLayout) holder.mediaContainer;
                gridLayout.setRowCount(rows);
                gridLayout.setColumnCount(cols);

                // 添加图片视图
                int idx = 0;
                for (com.juggle.im.android.server.beans.ImageBean img : post.getContent().getImages()) {
                    ImageView iv = new ImageView(holder.itemView.getContext());

                    // 计算图片尺寸
                    int dp;
                    if (imageSize < 4) {
                        // 少于4张，每张图片宽度为容器宽度的1/3
                        dp = (int) (80 * holder.itemView.getResources().getDisplayMetrics().density);
                    } else if (imageSize == 4) {
                        // 4张图片，每张图片更大一些
                        dp = (int) (120 * holder.itemView.getResources().getDisplayMetrics().density);
                    } else {
                        // 多于4张，每张图片小一些以适应3列
                        dp = (int) (90 * holder.itemView.getResources().getDisplayMetrics().density);
                    }

                    GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                    lp.width = dp;
                    lp.height = dp;
                    lp.setMargins(4, 4, 4, 4);
                    iv.setLayoutParams(lp);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setBackgroundColor(0xFFCCCCCC);
                    AvatarUtils.loadImage(iv, img.getUrl());

                    gridLayout.addView(iv);
                    idx++;
                    final int positionCopy = position;
                    final String imageUrl = img.getUrl();
                    iv.setOnClickListener(l -> {
                        if (listener != null) listener.onClickImage(positionCopy, post, imageUrl);
                    });
                }
            } else {
                holder.mediaContainer.setVisibility(View.GONE);
            }

            // time
            if (post.getCreated_time() > 0) {
                long currentTime = System.currentTimeMillis();
                long timeDifference = currentTime - post.getCreated_time();

                long minutes = timeDifference / (1000 * 60);
                long hours = timeDifference / (1000 * 60 * 60);
                long days = timeDifference / (1000 * 60 * 60 * 24);

                String timeText;
                if (minutes < 1) {
                    timeText = "刚刚";
                } else if (minutes < 60) {
                    timeText = minutes + "分钟前";
                } else if (hours < 24) {
                    timeText = hours + "小时前";
                } else if (days < 2) {
                    timeText = "昨天";
                } else {
                    timeText = days + "天前";
                }

                holder.tvTime.setText(timeText);
            } else {
                holder.tvTime.setText("");
            }

            // likes (reactions) - flatten user nicknames
            boolean hasLikes = false;
            if (post.getReactions() != null && !post.getReactions().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (java.util.Map.Entry<String, java.util.List<com.juggle.im.android.server.beans.ReactionItem>> entry : post.getReactions().entrySet()) {
                    for (com.juggle.im.android.server.beans.ReactionItem it : entry.getValue()) {
                        if (it.getUser_info() != null) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(it.getUser_info().getNickname());
                        }
                    }
                }
                if (sb.length() > 0) {
                    hasLikes = true;
                    holder.tvLikes.setVisibility(VISIBLE);
                    // color the names using Spannable
                    android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder(sb.toString());
                    // naive: color full string; for more precise color-per-name we'd parse and apply spans per name
                    ssb.setSpan(new android.text.style.ForegroundColorSpan(0xFF576B95), 0, ssb.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    holder.tvLikes.setText(ssb);
                }
                holder.likesContainer.setVisibility(VISIBLE);
            } else {
                holder.likesContainer.setVisibility(View.GONE);
            }
            holder.dividerLikes.setVisibility(hasLikes ? VISIBLE : View.GONE);

            // comments
            holder.commentsContainer.removeAllViews();
            boolean hasComments = false;
            if (post.getTop_comments() != null && !post.getTop_comments().isEmpty()) {
                hasComments = true;
                for (com.juggle.im.android.server.beans.TopCommentBean c : post.getTop_comments()) {
                    TextView tv = new TextView(holder.itemView.getContext());
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.setMargins(0, 5, 0, 0);
                    tv.setLayoutParams(params);
                    tv.setTextSize(13f);
                    // build name + content with name colored
                    String name = c.getUser_info() != null ? c.getUser_info().getNickname() : "";
                    String content = c.getText() != null ? c.getText() : "";
                    CommentDetail commentDetail = gson.fromJson(content, CommentDetail.class);
                    String full = name + ": " + commentDetail.getContent();
                    android.text.SpannableStringBuilder ss = new android.text.SpannableStringBuilder(full);
                    if (!name.isEmpty()) {
                        ss.setSpan(new android.text.style.ForegroundColorSpan(0xFF576B95), 0, name.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    ss.setSpan(new android.text.style.ForegroundColorSpan(0xFF666666), name.length(), full.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    tv.setText(ss);
                    holder.commentsContainer.addView(tv);
                    tv.setOnClickListener(v -> {
                        if (listener != null) listener.onComment(position, post, c);
                    });
                }
            }

            holder.blockLikesComments.setVisibility(hasLikes || hasComments ? VISIBLE : View.GONE);

            // btnMore click listener for popup menu
            holder.btnMore.setOnClickListener(v -> {
                View popupView = LayoutInflater.from(holder.itemView.getContext()).inflate(R.layout.popup_menu, null);
                PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);

                // Set click listeners for popup menu items
                popupView.findViewById(R.id.btn_like).setOnClickListener(view -> {
                    popupWindow.dismiss();
                    likePost(position, post);
                });

                popupView.findViewById(R.id.btn_comment).setOnClickListener(view -> {
                    popupWindow.dismiss();
                    // Handle comment action
                    showPostComment(position, post, null);
                });

                // 获取 PopupWindow 宽度
                popupWindow.getContentView().measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                int popupWidth = popupWindow.getContentView().getMeasuredWidth();
                int popupHeight = popupWindow.getContentView().getMeasuredHeight();
                int[] location = new int[2];
                holder.btnMore.getLocationOnScreen(location);
                int xPos = location[0] - popupWidth;
                int yPos = location[1] - 10;
                popupWindow.showAtLocation(holder.btnMore, Gravity.NO_GRAVITY, xPos, yPos);
                // Set animation style for popup
                popupWindow.getContentView().setTranslationX(xPos);  // 从右侧开始
                popupWindow.getContentView().animate().translationX(0f).setDuration(300).start();  // 滑动到目标位置
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            TextView tvName;
            ImageView vDelete;
            ImageView btnMore;
            TextView tvContent;
            GridLayout mediaContainer;
            TextView tvTime;
            ImageView btnComment;
            View blockLikesComments;
            TextView tvLikes;
            ViewGroup likesContainer;
            View dividerLikes;
            LinearLayout commentsContainer;

            VH(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_avatar);
                tvName = itemView.findViewById(R.id.tv_name);
                tvContent = itemView.findViewById(R.id.tv_content);
                mediaContainer = itemView.findViewById(R.id.media_container);
                tvTime = itemView.findViewById(R.id.tv_time);
                btnMore = itemView.findViewById(R.id.btn_more);
                blockLikesComments = itemView.findViewById(R.id.block_likes_comments);
                tvLikes = itemView.findViewById(R.id.tv_likes);
                dividerLikes = itemView.findViewById(R.id.divider_likes);
                commentsContainer = itemView.findViewById(R.id.comments_container);
                likesContainer = itemView.findViewById(R.id.likes_container);
                vDelete = itemView.findViewById(R.id.delete_moment);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) return;

        switch (requestCode) {
            case REQUEST_CODE_CHOOSE_PHOTO:
                if (data != null) {
                    ArrayList<String> selectedImages = data.getStringArrayListExtra("selected_images");
                    if (selectedImages != null && !selectedImages.isEmpty()) {
                        Intent intent = new Intent(this, CreatePostActivity.class);
                        intent.putStringArrayListExtra("image_urls", selectedImages);
                        startActivityForResult(intent, REQUEST_CODE_CREATE_POST);
                    }
                }
                break;

            case REQUEST_CODE_TAKE_PHOTO:
                if (photoUri != null) {
                    ArrayList<String> imageUrls = new ArrayList<>();
                    imageUrls.add(photoUri.toString());
                    Intent intent = new Intent(this, CreatePostActivity.class);
                    intent.putStringArrayListExtra("image_urls", imageUrls);
                    startActivityForResult(intent, REQUEST_CODE_CREATE_POST);
                }
                break;

            case REQUEST_CODE_CREATE_POST:
                refreshMoments(); // 重新加载数据
                break;

            default:
                if (data != null) {
                    refreshMoments(); // 重新加载数据
                }
                break;
        }
    }
}