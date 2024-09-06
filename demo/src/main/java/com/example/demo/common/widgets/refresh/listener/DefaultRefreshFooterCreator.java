package com.example.demo.common.widgets.refresh.listener;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.demo.common.widgets.refresh.api.RefreshFooter;
import com.example.demo.common.widgets.refresh.api.RefreshLayout;


/** 默认Footer创建器 Created by scwang on 2018/1/26. */
public interface DefaultRefreshFooterCreator {
    @NonNull
    RefreshFooter createRefreshFooter(@NonNull Context context, @NonNull RefreshLayout layout);
}
