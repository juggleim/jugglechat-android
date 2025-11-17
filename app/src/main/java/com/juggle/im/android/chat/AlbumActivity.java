package com.juggle.im.android.chat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.android.R;

import java.util.ArrayList;
import java.util.List;

public class AlbumActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;
    private RecyclerView mImageGrid;
    private ImageView mCancelButton;
    private Button mPreviewButton;
    private Button mSendButton;
    private AlbumImageAdapter mAdapter;
    private List<String> mSelectedImages = new ArrayList<>();
    private static final String TAG = "AlbumActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album);
        
        enableFullscreen();
        // make full screen by hiding the action bar if present
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        initViews();
        setupRecyclerView();
        setupEventListeners();
        
        // Check for permissions before loading images
        if (checkPermissions()) {
            loadImages();
        } else {
            requestPermissions();
        }
    }
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
        window.setNavigationBarColor(getColor(R.color.black));

    }

    private void initViews() {
        mImageGrid = findViewById(R.id.album_image_grid);
        mCancelButton = findViewById(R.id.album_cancel_button);
        mPreviewButton = findViewById(R.id.album_preview_button);
        mSendButton = findViewById(R.id.album_send_button);
    }

    private void setupRecyclerView() {
        mImageGrid.setLayoutManager(new GridLayoutManager(this, 4));
        mAdapter = new AlbumImageAdapter(this, mSelectedImages);
        mImageGrid.setAdapter(mAdapter);
    }

    private void setupEventListeners() {
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mPreviewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mSelectedImages.isEmpty()) {
                    Intent intent = new Intent(AlbumActivity.this, ImagePreviewActivity.class);
                    intent.putStringArrayListExtra(ImagePreviewActivity.EXTRA_IMAGE_URLS, new ArrayList<>(mSelectedImages));
                    intent.putExtra(ImagePreviewActivity.EXTRA_IS_SELECT_MODE, true);
                    startActivity(intent);
                }
            }
        });

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mSelectedImages.isEmpty()) {
                    Intent resultIntent = new Intent();
                    resultIntent.putStringArrayListExtra("selected_images", new ArrayList<>(mSelectedImages));
                    setResult(Activity.RESULT_OK, resultIntent);
                    finish();
                }
            }
        });

        mAdapter.setOnImageSelectedListener(new AlbumImageAdapter.OnImageSelectedListener() {
            @Override
            public void onImageSelected(int selectedCount) {
                mPreviewButton.setEnabled(selectedCount > 0);
                mSendButton.setEnabled(selectedCount > 0);
                if (selectedCount > 0) {
                    mSendButton.setText(getString(R.string.send) + "(" + selectedCount + ")");
                } else {
                    mSendButton.setText(R.string.send);
                }
            }
        });
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 (API 33) and above
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            // Below Android 13
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13 (API 33) and above
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 
                    PERMISSION_REQUEST_CODE);
        } else {
            // Below Android 13
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadImages();
            } else {
                Toast.makeText(this, "Permission denied. Cannot load images.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void loadImages() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<String> images = new ArrayList<>();
                
                // Query for images using MediaStore
                Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                String[] projection = {
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED
                };
                
                // Sort by date added in descending order (newest first)
                String orderBy = MediaStore.Images.Media.DATE_ADDED + " DESC";
                
                Cursor cursor = null;
                try {
                    cursor = getContentResolver().query(
                            uri,
                            projection,
                            null,  // No selection
                            null,  // No selection args
                            orderBy
                    );
                    
                    if (cursor != null && cursor.moveToFirst()) {
                        int count = 0;
                        do {
                            int dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                            if (dataColumn != -1) {
                                String imagePath = cursor.getString(dataColumn);
                                if (imagePath != null && !imagePath.isEmpty()) {
                                    // For Android 10 and above, we need to check file access differently
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        // On Android 10+, just add the path, Glide will load via content URI
                                        images.add(imagePath);
                                        Log.d(TAG, "Found image (Android 10+): " + imagePath);
                                    } else {
                                        // On older versions, check if file exists
                                        java.io.File file = new java.io.File(imagePath);
                                        if (file.exists()) {
                                            images.add(imagePath);
                                            Log.d(TAG, "Found image (Older Android): " + imagePath);
                                        } else {
                                            Log.w(TAG, "Image file does not exist: " + imagePath);
                                        }
                                    }
                                }
                            }
                            count++;
                        } while (cursor.moveToNext() && count < 100); // Limit to 100 images for performance
                        
                        Log.d(TAG, "Total images found: " + images.size());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error loading images", e);
                    e.printStackTrace();
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                
                // Update UI on main thread
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.setImages(images);
                        if (images.isEmpty()) {
                            Toast.makeText(AlbumActivity.this, "No images found", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        }).start();
    }
}