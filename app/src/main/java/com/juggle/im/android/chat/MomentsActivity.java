package com.juggle.im.android.chat;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
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
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.juggle.im.JIM;
import com.juggle.im.JIMConst;
import com.juggle.im.android.R;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.model.ConfigUtils;
import com.juggle.im.interfaces.IMessageManager;
import com.juggle.im.model.GetMomentOption;
import com.juggle.im.model.Moment;
import com.juggle.im.model.MomentComment;
import com.juggle.im.model.MomentMedia;
import com.juggle.im.model.MomentReaction;
import com.juggle.im.model.UserInfo;
import com.juggle.im.android.utils.PermissionComponent;
import com.juggle.im.android.event.MomentPublishedEvent;
import com.juggle.im.android.widget.AppConfirmDialog;
import com.juggle.im.android.widget.LoadingOverlay;
import com.juggle.im.android.widget.SubmitButtonState;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.android.chat.utils.FileUtils;

import android.widget.GridLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.juggle.im.android.utils.LogUtils;

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
    private Moment selectedMoment = null;
    private MomentComment selectedComment = null;
    private MomentsAdapter adapter;
    private TextView tvName;
    private ImageView ivAvatar;
    private SubmitButtonState commentSubmitState;
    /** 正在请求点赞的动态 ID，避免连点重复发请求 */
    private final Set<String> likingMomentIds = new HashSet<>();

    // 分页相关变量
    private int currentPage = 0;
    private int pageSize = 20;
    private boolean isLoading = false;
    private boolean hasMore = true;

    private static final String TAG = "MomentsActivity";
    private static final String FEATURE_MOMENTS = "moments";

    /** 点赞在 SDK 里的 reaction key */
    private static final String REACTION_KEY_LIKE = "like";

    /** 标题开始渐显的折叠进度阈值 */
    private static final float TITLE_FADE_IN_START = 0.7f;

    // 拍照相关变量
    private static final int REQUEST_CODE_CHOOSE_PHOTO = 1001;
    private static final int REQUEST_CODE_TAKE_PHOTO = 1002;
    private static final int REQUEST_CODE_CREATE_POST = 1003;
    private Uri photoUri;
    private int currentPaddingBottom;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moments);
        EventBus.getDefault().register(this);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            // 标题由居中的 tv_toolbar_title 承担，关闭 ActionBar 自带的左对齐标题
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        appBarLayout = findViewById(R.id.appbar);
        setupCollapsedTitle();
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
            startActivityForResult(it, REQUEST_CODE_CREATE_POST);
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
            public void onComment(int position, Moment moment, MomentComment comment) {
                if (comment != null && JIM.getInstance().getCurrentUserId().equals(comment.getUserInfo().getUserId())) {
                    BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(MomentsActivity.this);
                    View sheetView = LayoutInflater.from(MomentsActivity.this).inflate(R.layout.dialog_delete_comment, null);
                    bottomSheetDialog.setContentView(sheetView);
                    sheetView.findViewById(R.id.btn_delete).setOnClickListener(v -> {
                        bottomSheetDialog.dismiss();
                        LoadingOverlay overlay = LoadingOverlay.show(MomentsActivity.this);
                        JIM.getInstance().getMomentManager().removeComment(moment.getMomentId(), comment.getCommentId(), new IMessageManager.ISimpleCallback() {
                            @Override
                            public void onSuccess() {
                                runOnUiThread(() -> LoadingOverlay.dismiss(overlay));
                                refreshMomentItem(moment);
                            }

                            @Override
                            public void onError(int errorCode) {
                                runOnUiThread(() -> {
                                    LoadingOverlay.dismiss(overlay);
                                    Toast.makeText(MomentsActivity.this,
                                            R.string.moments_comment_delete_failed,
                                            Toast.LENGTH_SHORT).show();
                                    LogUtils.serverError(FEATURE_MOMENTS, "removeComment", errorCode, "");
                                });
                            }
                        });
                    });

                    sheetView.findViewById(R.id.btn_cancel).setOnClickListener(v -> bottomSheetDialog.dismiss());

                    bottomSheetDialog.show();
                } else {
                    showPostComment(position, moment, comment);
                }
            }

            @Override
            public void onClickImage(int position, Moment moment, String imageUrl) {
                Intent it = new Intent(MomentsActivity.this, ImagePreviewActivity.class);
                ArrayList<String> urls = new ArrayList<>();
                int startIndex = 0;
                List<MomentMedia> mediaList = moment.getMediaList();
                if (mediaList != null) {
                    for (int i = 0; i < mediaList.size(); i++) {
                        MomentMedia media = mediaList.get(i);
                        urls.add(media.getUrl());
                        if (media.getUrl().equals(imageUrl)) {
                            startIndex = i;
                        }
                    }
                }
                it.putStringArrayListExtra(ImagePreviewActivity.EXTRA_IMAGE_URLS, urls);
                it.putExtra(ImagePreviewActivity.EXTRA_IMAGE_INDEX, startIndex);
                startActivity(it);
            }

            @Override
            public void onDeletePost(int position, Moment moment) {
                // 与 iOS 一致：删除动态不可撤销，先确认再执行；确认按钮自带加载态承载这次请求
                AppConfirmDialog.builder(MomentsActivity.this)
                        .setMessage(getString(R.string.moments_delete_confirm_message))
                        .setNegativeText(getString(R.string.txt_cancel))
                        .setPositiveText(getString(R.string.create_group_confirm))
                        .setOnPositiveAsyncClick(action ->
                                JIM.getInstance().getMomentManager().removeMoment(moment.getMomentId(),
                                        new IMessageManager.ISimpleCallback() {
                                            @Override
                                            public void onSuccess() {
                                                runOnUiThread(() -> {
                                                    action.succeed();
                                                    adapter.items.remove(position);
                                                    adapter.notifyItemRemoved(position);
                                                });
                                            }

                                            @Override
                                            public void onError(int errorCode) {
                                                runOnUiThread(() -> {
                                                    action.fail();
                                                    Toast.makeText(MomentsActivity.this,
                                                            R.string.moments_delete_failed,
                                                            Toast.LENGTH_SHORT).show();
                                                });
                                                LogUtils.serverError(FEATURE_MOMENTS, "removeMoment", errorCode, "");
                                            }
                                        }))
                        .show();
            }
        });

        TextView sendCommentView = findViewById(R.id.btn_send_comment);
        commentSubmitState = SubmitButtonState.bind(sendCommentView, R.string.common_sending);
        sendCommentView.setOnClickListener(v -> {
            if (selectedMoment == null) {
                return;
            }
            String commentText = editTextField.getText().toString().trim();
            if (TextUtils.isEmpty(commentText)) {
                return;
            }
            // TIPS: 评论是会产生脏数据的写操作，必须先占住提交态再发请求，否则弱网下会重复评论
            if (!commentSubmitState.begin()) {
                return;
            }
            Moment commentTarget = selectedMoment;
            JIM.getInstance().getMomentManager().addComment(
                    commentTarget.getMomentId(),
                    selectedComment != null ? selectedComment.getCommentId() : null,
                    commentText,
                    new JIMConst.IResultCallback<MomentComment>() {
                        @Override
                        public void onSuccess(MomentComment data) {
                            refreshMomentItem(commentTarget);
                            runOnUiThread(() -> {
                                commentSubmitState.end();
                                hideCommentInput();
                                editTextField.setText("");
                            });
                        }

                        @Override
                        public void onError(int errorCode) {
                            runOnUiThread(() -> {
                                commentSubmitState.end();
                                Toast.makeText(MomentsActivity.this,
                                        R.string.moments_comment_send_failed,
                                        Toast.LENGTH_SHORT).show();
                                LogUtils.serverError("moments", "addComment", errorCode, "");
                            });
                        }
                    }
            );
        });

        // 初始加载数据
        swipeRefreshLayout.setRefreshing(true);
        loadMoments();
    }

    /**
     * 折叠标题渐显。
     * TIPS: CollapsingToolbarLayout 自带标题的折叠位置会被导航按钮挤偏，无法真正居中，
     * 因此关闭其 titleEnabled，改由 Toolbar 内居中的 TextView 承担，按折叠进度控制透明度。
     */
    private void setupCollapsedTitle() {
        TextView titleView = findViewById(R.id.tv_toolbar_title);
        if (appBarLayout == null || titleView == null) {
            return;
        }
        appBarLayout.addOnOffsetChangedListener((AppBarLayout.OnOffsetChangedListener) (bar, verticalOffset) -> {
            int scrollRange = bar.getTotalScrollRange();
            if (scrollRange <= 0) {
                titleView.setAlpha(0f);
                return;
            }
            float collapsedRatio = Math.min(1f, Math.abs(verticalOffset) / (float) scrollRange);
            // 接近完全折叠时才渐显，避免与封面上的昵称同时出现
            float alpha = (collapsedRatio - TITLE_FADE_IN_START) / (1f - TITLE_FADE_IN_START);
            titleView.setAlpha(Math.max(0f, Math.min(1f, alpha)));
        });
    }

    /**
     * 收到朋友圈发布成功事件后刷新动态流。
     *
     * @param event 发布事件
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMomentPublished(MomentPublishedEvent event) {
        LogUtils.i(TAG, null, FEATURE_MOMENTS, "publish", "received",
                "momentId=" + (event == null ? "" : event.getMomentId()));
        refreshMoments();
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    private void showCameraOptions() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this, R.style.TransparentBottomSheetDialog);
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

        // 设置导航栏背景色为白色
        if (bottomSheetDialog.getWindow() != null) {
            bottomSheetDialog.getWindow().setNavigationBarColor(android.graphics.Color.WHITE);
        }
    }

    private void takePhoto() {
        if (!PermissionComponent.hasAllPermissions(this, Manifest.permission.CAMERA)) {
            Toast.makeText(this, R.string.moments_camera_permission_denied, Toast.LENGTH_SHORT).show();
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

    private void refreshMomentItem(Moment moment) {
        JIM.getInstance().getMomentManager().getMoment(moment.getMomentId(), new JIMConst.IResultCallback<Moment>() {
            @Override
            public void onSuccess(Moment data) {
                runOnUiThread(() -> {
                    int pos = adapter.getPositionById(data.getMomentId());
                    if (pos >= 0) {
                        adapter.items.set(pos, data);
                        adapter.notifyItemChanged(pos);
                    }
                });
            }

            @Override
            public void onError(int errorCode) {
                // Handle error
            }
        });
    }

    /**
     * 点赞 / 取消点赞。
     * TIPS: 点赞是高频轻量操作，转圈反而打断手感，因此走乐观更新——先本地增删自己，
     * 请求成功再用服务端数据校正，失败则回滚并提示；与 iOS 的 toggleLike 行为对齐。
     *
     * @param position 列表位置
     * @param moment   目标动态
     */
    private void likePost(int position, Moment moment) {
        String momentId = moment.getMomentId();
        if (momentId == null || !likingMomentIds.add(momentId)) {
            return;
        }
        boolean liked = isLikedByCurrentUser(moment);
        boolean localApplied = applyLocalLike(moment, !liked);
        if (localApplied) {
            adapter.notifyItemChanged(position);
        }
        IMessageManager.ISimpleCallback callback = new IMessageManager.ISimpleCallback() {
            @Override
            public void onSuccess() {
                // TIPS: SDK 回调不保证在主线程，点赞标记与列表数据都只在主线程增删，避免并发改动
                runOnUiThread(() -> likingMomentIds.remove(momentId));
                refreshMomentItem(moment);
            }

            @Override
            public void onError(int errorCode) {
                runOnUiThread(() -> {
                    likingMomentIds.remove(momentId);
                    if (localApplied) {
                        applyLocalLike(moment, liked);
                        adapter.notifyItemChanged(position);
                    }
                    Toast.makeText(MomentsActivity.this,
                            R.string.moments_like_failed,
                            Toast.LENGTH_SHORT).show();
                });
                LogUtils.serverError(FEATURE_MOMENTS, liked ? "removeReaction" : "addReaction", errorCode, "");
            }
        };
        if (liked) {
            JIM.getInstance().getMomentManager().removeReaction(momentId, REACTION_KEY_LIKE, callback);
        } else {
            JIM.getInstance().getMomentManager().addReaction(momentId, REACTION_KEY_LIKE, callback);
        }
    }

    /**
     * 判断当前用户是否已点赞该动态。
     *
     * @param moment 目标动态
     * @return true 表示已点赞
     */
    private boolean isLikedByCurrentUser(@NonNull Moment moment) {
        String currentUserId = JIM.getInstance().getCurrentUserId();
        if (TextUtils.isEmpty(currentUserId) || moment.getReactionList() == null) {
            return false;
        }
        for (MomentReaction reaction : moment.getReactionList()) {
            if (!REACTION_KEY_LIKE.equals(reaction.getKey()) || reaction.getUserList() == null) {
                continue;
            }
            for (UserInfo user : reaction.getUserList()) {
                if (user != null && currentUserId.equals(user.getUserId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 在本地动态数据里增删当前用户的点赞。
     *
     * @param moment 目标动态
     * @param liked  true 表示插入点赞，false 表示回滚
     * @return 是否真的改动了数据（已点过赞时插入会返回 false）
     */
    private boolean applyLocalLike(@NonNull Moment moment, boolean liked) {
        String currentUserId = JIM.getInstance().getCurrentUserId();
        if (TextUtils.isEmpty(currentUserId)) {
            return false;
        }
        List<MomentReaction> reactionList = moment.getReactionList();
        if (reactionList == null) {
            reactionList = new ArrayList<>();
            moment.setReactionList(reactionList);
        }
        MomentReaction likeReaction = null;
        for (MomentReaction reaction : reactionList) {
            if (REACTION_KEY_LIKE.equals(reaction.getKey())) {
                likeReaction = reaction;
                break;
            }
        }
        if (likeReaction == null) {
            if (!liked) {
                return false;
            }
            likeReaction = new MomentReaction();
            likeReaction.setKey(REACTION_KEY_LIKE);
            likeReaction.setUserList(new ArrayList<>());
            reactionList.add(likeReaction);
        }
        List<UserInfo> userList = likeReaction.getUserList();
        if (userList == null) {
            userList = new ArrayList<>();
            likeReaction.setUserList(userList);
        }
        int existingIndex = -1;
        for (int i = 0; i < userList.size(); i++) {
            UserInfo user = userList.get(i);
            if (user != null && currentUserId.equals(user.getUserId())) {
                existingIndex = i;
                break;
            }
        }
        if (liked) {
            if (existingIndex >= 0) {
                return false;
            }
            UserInfo self = new UserInfo();
            self.setUserId(currentUserId);
            self.setUserName(ConfigUtils.myName);
            self.setPortrait(ConfigUtils.myAvatarUrl);
            userList.add(self);
            return true;
        }
        if (existingIndex < 0) {
            return false;
        }
        userList.remove(existingIndex);
        if (userList.isEmpty()) {
            reactionList.remove(likeReaction);
        }
        return true;
    }

    private void showPostComment(int position, Moment moment, MomentComment comment) {
        if (commentBar.getVisibility() == GONE) {
            selectedMoment = moment;
            selectedComment = comment;
            showCommentInput(position);
        }
        if (comment != null && comment.getUserInfo() != null) {
            String hint = comment.getUserInfo().getUserName();
            editTextField.setHint(getString(R.string.moments_reply_hint, hint));
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
        // fetch moments from SDK
        GetMomentOption option = new GetMomentOption();
        option.setCount(pageSize);
        option.setStartTime(currentPage == 0 ? 0 : adapter.items.isEmpty() ? 0 : adapter.items.get(adapter.items.size() - 1).getCreateTime());
        option.setDirection(JIMConst.PullDirection.OLDER);

        JIM.getInstance().getMomentManager().getMomentList(option, new JIMConst.IResultListCallback<Moment>() {
            @Override
            public void onSuccess(List<Moment> data, boolean isFinish) {
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    isLoading = false;

                    if (data != null && !data.isEmpty()) {
                        if (currentPage == 0) {
                            // 下拉刷新，替换所有数据
                            adapter.setItems(data);
                        } else {
                            // 上拉加载更多，追加数据
                            adapter.addItems(data);
                        }

                        // 更新分页参数
                        if (isFinish || data.size() < pageSize) {
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
            public void onError(int errorCode) {
                Log.e("MomentsActivity", "Failed to loadMoments: " + errorCode);
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    isLoading = false;
                    LogUtils.serverError("moments", "loadMoments", errorCode, "");
                    Toast.makeText(MomentsActivity.this, R.string.moments_load_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    interface Listener {
        void onComment(int position, Moment moment, MomentComment comment);

        void onClickImage(int position, Moment moment, String imageUrl);

        void onDeletePost(int position, Moment moment);
    }

    class MomentsAdapter extends RecyclerView.Adapter<MomentsAdapter.VH> {
        private final List<Moment> items;
        private Listener listener;

        MomentsAdapter(List<Moment> items) {
            this.items = items;
        }

        void setListener(Listener l) {
            this.listener = l;
        }

        int getPositionById(String momentId) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).getMomentId().equals(momentId))
                    return i;
            }
            return -1;
        }

        void setItems(List<Moment> newItems) {
            items.clear();
            if (newItems != null) items.addAll(newItems);
            notifyDataSetChanged();
        }

        void addItems(List<Moment> newItems) {
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
            Moment moment = items.get(position);
            String text = moment.getContent() != null ? moment.getContent() : "";
            // name
            if (moment.getUserInfo() != null) {
                holder.tvName.setText(moment.getUserInfo().getUserName());
                if (moment.getUserInfo().getUserId().equals(JIM.getInstance().getCurrentUserId())) {
                    holder.vDelete.setVisibility(VISIBLE);
                    holder.vDelete.setOnClickListener(v -> {
                        if (listener != null) listener.onDeletePost(position, moment);
                    });
                } else {
                    holder.vDelete.setVisibility(View.GONE);
                }
            } else {
                holder.tvName.setText(R.string.moments_anonymous);
                holder.vDelete.setVisibility(View.GONE);
            }

            AvatarUtils.loadAvatar(holder.ivAvatar, moment.getUserInfo() != null ? moment.getUserInfo().getPortrait() : null, moment.getUserInfo() != null ? moment.getUserInfo().getUserName() : null);

            // content text
            if (TextUtils.isEmpty(text)) {
                holder.tvContent.setVisibility(View.GONE);
            } else {
                holder.tvContent.setVisibility(VISIBLE);
                holder.tvContent.setText(text);
            }

            // media (images)
            holder.mediaContainer.removeAllViews();
            if (moment.getMediaList() != null && !moment.getMediaList().isEmpty()) {
                holder.mediaContainer.setVisibility(VISIBLE);
                int imageSize = moment.getMediaList().size();

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
                for (MomentMedia media : moment.getMediaList()) {
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
                    AvatarUtils.loadImage(iv, media.getUrl());

                    gridLayout.addView(iv);
                    final int positionCopy = position;
                    final String imageUrl = media.getUrl();
                    iv.setOnClickListener(l -> {
                        if (listener != null) listener.onClickImage(positionCopy, moment, imageUrl);
                    });
                }
            } else {
                holder.mediaContainer.setVisibility(View.GONE);
            }

            // time
            if (moment.getCreateTime() > 0) {
                long currentTime = System.currentTimeMillis();
                long timeDifference = currentTime - moment.getCreateTime();

                long minutes = timeDifference / (1000 * 60);
                long hours = timeDifference / (1000 * 60 * 60);
                long days = timeDifference / (1000 * 60 * 60 * 24);

                String timeText;
                if (minutes < 1) {
                    timeText = getString(R.string.time_just_now);
                } else if (minutes < 60) {
                    timeText = getString(R.string.time_minutes_ago, minutes);
                } else if (hours < 24) {
                    timeText = getString(R.string.time_hours_ago, hours);
                } else if (days < 2) {
                    timeText = getString(R.string.time_yesterday);
                } else {
                    timeText = getString(R.string.time_days_ago, days);
                }

                holder.tvTime.setText(timeText);
            } else {
                holder.tvTime.setText("");
            }

            // likes (reactions) - flatten user nicknames
            boolean hasLikes = false;
            if (moment.getReactionList() != null && !moment.getReactionList().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (MomentReaction reaction : moment.getReactionList()) {
                    if (reaction.getUserList() != null) {
                        for (UserInfo user : reaction.getUserList()) {
                            if (user != null) {
                                if (sb.length() > 0) sb.append(", ");
                                sb.append(user.getUserName());
                            }
                        }
                    }
                }
                if (sb.length() > 0) {
                    hasLikes = true;
                    holder.tvLikes.setVisibility(VISIBLE);
                    // color the names using Spannable
                    android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder(sb.toString());
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
            if (moment.getCommentList() != null && !moment.getCommentList().isEmpty()) {
                hasComments = true;
                for (MomentComment c : moment.getCommentList()) {
                    TextView tv = new TextView(holder.itemView.getContext());
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.setMargins(0, 5, 0, 0);
                    tv.setLayoutParams(params);
                    tv.setTextSize(13f);
                    // build name + content with name colored
                    String name = c.getUserInfo() != null ? c.getUserInfo().getUserName() : "";
                    String content = c.getContent() != null ? c.getContent() : "";
                    String full = name + ": " + content;
                    android.text.SpannableStringBuilder ss = new android.text.SpannableStringBuilder(full);
                    if (!name.isEmpty()) {
                        ss.setSpan(new android.text.style.ForegroundColorSpan(0xFF576B95), 0, name.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    ss.setSpan(new android.text.style.ForegroundColorSpan(0xFF666666), name.length(), full.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    tv.setText(ss);
                    holder.commentsContainer.addView(tv);
                    tv.setOnClickListener(v -> {
                        if (listener != null) listener.onComment(position, moment, c);
                    });
                }
            }

            holder.blockLikesComments.setVisibility(hasLikes || hasComments ? VISIBLE : View.GONE);

            // btnMore click listener for popup menu
            holder.btnMore.setOnClickListener(v -> {
                View popupView = LayoutInflater.from(holder.itemView.getContext()).inflate(R.layout.popup_menu, null);
                PopupWindow popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);

                // Set click listeners for popup menu items
                // 与 iOS 一致：按钮文案跟随当前点赞状态在"赞/已赞"之间切换
                TextView likeView = popupView.findViewById(R.id.btn_like);
                likeView.setText(isLikedByCurrentUser(moment)
                        ? R.string.moments_unlike
                        : R.string.moments_like);
                likeView.setOnClickListener(view -> {
                    popupWindow.dismiss();
                    likePost(position, moment);
                });

                popupView.findViewById(R.id.btn_comment).setOnClickListener(view -> {
                    popupWindow.dismiss();
                    // Handle comment action
                    showPostComment(position, moment, null);
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
                // 发布结果由 MomentPublishedEvent 统一驱动刷新，这里不再重复拉取
                break;

            default:
                if (data != null) {
                    refreshMoments(); // 重新加载数据
                }
                break;
        }
    }
}
