package com.juggle.im.android.chat;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;

import com.juggle.im.android.chat.ImagePreviewActivity;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.juggle.im.android.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AlbumImageAdapter extends RecyclerView.Adapter<AlbumImageAdapter.ImageViewHolder> {

    private Context mContext;
    private List<String> mImagePaths;
    private List<String> mSelectedImages;
    private OnImageSelectedListener mListener;
    private LayoutInflater mInflater;

    public AlbumImageAdapter(Context context, List<String> selectedImages) {
        this.mContext = context;
        this.mSelectedImages = selectedImages;
        this.mImagePaths = new ArrayList<>();
        this.mInflater = LayoutInflater.from(context);
    }

    public void setImages(List<String> images) {
        this.mImagePaths = images;
        notifyDataSetChanged();
    }

    public void setOnImageSelectedListener(OnImageSelectedListener listener) {
        this.mListener = listener;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.item_album_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, @SuppressLint("RecyclerView") int position) {
        String imagePath = mImagePaths.get(position);
        
        Log.d("AlbumImageAdapter", "Loading image from path: " + imagePath);
        
        // Get content URI for the image path
        Uri imageUri = getImageContentUri(imagePath);
        
        if (imageUri != null) {
            Glide.with(mContext)
                    .load(imageUri)
                    .placeholder(R.drawable.default_image)
                    .error(R.drawable.default_image)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(holder.imageView);
        } else {
            Log.e("AlbumImageAdapter", "Failed to get content URI for image: " + imagePath);
            holder.imageView.setImageResource(R.drawable.default_image);
        }
        
        holder.checkBox.setChecked(mSelectedImages.contains(imagePath));
        holder.checkBox.setOnCheckedChangeListener(null);
        
        holder.checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (!mSelectedImages.contains(imagePath)) {
                        mSelectedImages.add(imagePath);
                    }
                } else {
                    mSelectedImages.remove(imagePath);
                }
                
                if (mListener != null) {
                    mListener.onImageSelected(mSelectedImages.size());
                }
            }
        });

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                holder.checkBox.setChecked(!holder.checkBox.isChecked());
            }
        });
    }

    @Override
    public int getItemCount() {
        return mImagePaths.size();
    }


    /**
     * Gets the content:// URI from the given file path
     */
    private Uri getImageContentUri(String imagePath) {
        ContentResolver cr = mContext.getContentResolver();
        String[] projection = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA};
        String selection = MediaStore.Images.Media.DATA + "=?";
        String[] selectionArgs = {imagePath};

        Cursor cursor = null;
        try {
            cursor = cr.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID);
                if (idColumn != -1) {
                    long id = cursor.getLong(idColumn);
                    cursor.close();
                    return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                }
            }
        } catch (Exception e) {
            Log.e("AlbumImageAdapter", "Error querying content URI: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return null;
    }

    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        CheckBox checkBox;

        ImageViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.album_item_image);
            checkBox = itemView.findViewById(R.id.album_item_checkbox);
        }
    }

    public interface OnImageSelectedListener {
        void onImageSelected(int selectedCount);
    }
}