package com.juggle.im.android.chat;

import static android.view.View.GONE;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.GestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.graphics.Color;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import androidx.annotation.Nullable;
import com.juggle.im.android.component.AbsAppActivity;
import androidx.viewpager2.widget.ViewPager2;


import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;
import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.interfaces.IMessageManager;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.Message;
import com.juggle.im.model.messages.ImageMessage;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

/**
 * Full-screen image preview. Supports pinch-to-zoom (PhotoView), share (forward), and save to gallery.
 */
public class ImagePreviewActivity extends AbsAppActivity {

    public static final String EXTRA_IMAGE_URL = "image_url";
    public static final String EXTRA_IMAGE_URLS = "image_urls";
    public static final String EXTRA_IMAGE_INDEX = "image_index";
    public static final String EXTRA_IS_SELECT_MODE = "is_select_mode";

    private ViewPager2 viewPager;
    private ImageView btnForward;
    private ImageView btnSave;
    private ProgressBar progressBar;
    private TextView indicatorText;
    
    private ArrayList<String> imageUrls = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isSelectMode = false;

    // 手势检测相关
    private GestureDetector gestureDetector;
    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // enable fullscreen (hide status bar and draw behind it)
        enableFullscreen();
        // make full screen by hiding the action bar if present
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        setContentView(R.layout.activity_image_preview_multi);

        viewPager = findViewById(R.id.preview_view_pager);
        btnForward = findViewById(R.id.preview_btn_forward);
        btnSave = findViewById(R.id.preview_btn_save);
        progressBar = findViewById(R.id.preview_progress);
        indicatorText = findViewById(R.id.preview_indicator);


        // Get image URLs from intent
        if (getIntent().hasExtra(EXTRA_IMAGE_URLS)) {
            imageUrls = getIntent().getStringArrayListExtra(EXTRA_IMAGE_URLS);
            isSelectMode = getIntent().getBooleanExtra(EXTRA_IS_SELECT_MODE, false);
        } else if (getIntent().hasExtra(EXTRA_IMAGE_URL)) {
            String singleUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
            if (singleUrl != null) {
                imageUrls.add(singleUrl);
            }
        }
        if (isSelectMode) {
            btnSave.setVisibility(GONE);
            btnForward.setVisibility(GONE);
        }
        
        // Get current index
        currentIndex = getIntent().getIntExtra(EXTRA_IMAGE_INDEX, 0);
        
        if (imageUrls.isEmpty()) {
            Toast.makeText(this, R.string.image_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Set up ViewPager2 with adapter
        ImagePreviewAdapter adapter = new ImagePreviewAdapter(imageUrls);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(currentIndex, false);
        
        // Update indicator
        updateIndicator();
        
        // Listen for page changes
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentIndex = position;
                updateIndicator();
                // Load image when page is selected
                adapter.loadImage(position);
            }
        });

        // forward (share) button: share url or path as text
        btnForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent it = ForwardConversationListActivity.createIntent(ImagePreviewActivity.this, "single");
                startActivityForResult(it, ConversationActivity.REQ_FORWARD);
            }
        });

        // save button: download bitmap in background and save to gallery
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btnSave.setEnabled(false);
                progressBar.setVisibility(View.VISIBLE);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String currentUrl = imageUrls.get(currentIndex);
                            Bitmap bmp = Glide.with(ImagePreviewActivity.this)
                                    .asBitmap()
                                    .load(currentUrl)
                                    .submit(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                                    .get();
                            final boolean saved = saveBitmapToGallery(bmp);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressBar.setVisibility(GONE);
                                    btnSave.setEnabled(true);
                                    if (saved) {
                                        Toast.makeText(ImagePreviewActivity.this, R.string.saved_to_gallery, Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(ImagePreviewActivity.this, R.string.save_failed, Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                        } catch (ExecutionException | InterruptedException e) {
                            e.printStackTrace();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    progressBar.setVisibility(GONE);
                                    btnSave.setEnabled(true);
                                    Toast.makeText(ImagePreviewActivity.this, R.string.save_failed, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                }).start();
            }
        });
    }

    private void updateIndicator() {
        if (imageUrls.size() > 1) {
            indicatorText.setText((currentIndex + 1) + "/" + imageUrls.size());
            indicatorText.setVisibility(View.VISIBLE);
        } else {
            indicatorText.setVisibility(GONE);
        }
    }

    /**
     * Enable full screen mode and draw behind the status bar.
     * Uses modern WindowInsetsController on newer Android versions and
     * falls back to legacy system UI flags on older versions.
     */
    private void enableFullscreen() {
        Window window = getWindow();
        // For Android R (API 30) and above, use WindowCompat + WindowInsetsControllerCompat
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Let content draw behind system bars
            WindowCompat.setDecorFitsSystemWindows(window, false);
            // Make status bar transparent so content is visible behind it
            window.setStatusBarColor(Color.TRANSPARENT);
            // Hide the status bar
            WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(window, window.getDecorView());
            insetsController.hide(WindowInsetsCompat.Type.statusBars());
            // Allow swipe to reveal
            insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            // Older devices: use legacy system UI flags
            View decor = window.getDecorView();
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN; // hide status bar
            decor.setSystemUiVisibility(flags);
            // For very old devices, make status bar translucent as a fallback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            }
        }
    }

    private boolean saveBitmapToGallery(Bitmap bitmap) {
        if (bitmap == null) return false;
        OutputStream fos = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "juggle_im_" + System.currentTimeMillis() + ".jpg");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/JuggleIM");

                Uri uri = getContentResolver().insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
                if (uri == null) return false;
                fos = getContentResolver().openOutputStream(uri);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                return true;
            } else {
                // Older devices
                String savedUrl = MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, "juggle_im_" + System.currentTimeMillis(), "");
                return savedUrl != null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (fos != null) try {
                fos.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null) return;
        String targetConvId = data.getStringExtra(ForwardConversationListActivity.EXTRA_CONVERSATION_ID);
        boolean targetIsGroup = data.getBooleanExtra(ForwardConversationListActivity.EXTRA_IS_GROUP, false);
        Conversation targetConv = new Conversation(
                targetIsGroup ? Conversation.ConversationType.GROUP : Conversation.ConversationType.PRIVATE,
                targetConvId);
        ImageMessage image = new ImageMessage();
        image.setHeight(600);
        image.setWidth(800);
        String url = imageUrls.get(currentIndex);
        image.setUrl(url);
        sendImageMessage(image, targetConv);
    }

    private void sendImageMessage(ImageMessage image, Conversation conversation) {
        IMessageManager.ISendMessageCallback callback = new IMessageManager.ISendMessageCallback() {
            @Override
            public void onSuccess(Message message) {
                Log.i("sendImageMessage", "send message success");
            }

            @Override
            public void onError(Message message, int errorCode) {
                Log.i("sendImageMessage", "send message error: " + errorCode);
            }
        };
        Message message = JIM.getInstance().getMessageManager().sendMessage(image, conversation, callback);
        Log.i("TAG", "sendImageMessage msgId= " + message.getMessageId());
    }
    
    // 带动画效果的页面关闭
    private void finishWithAnimation() {
        finish();
        overridePendingTransition(0, 0);
    }
}