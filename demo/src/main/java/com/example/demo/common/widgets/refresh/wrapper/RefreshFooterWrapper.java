package com.example.demo.common.widgets.refresh.wrapper;

import android.annotation.SuppressLint;
import android.view.View;

import com.example.demo.common.widgets.refresh.api.RefreshFooter;
import com.example.demo.common.widgets.refresh.simple.SimpleComponent;


/** 刷新底部包装 Created by scwang on 2017/5/26. */
@SuppressLint("ViewConstructor")
public class RefreshFooterWrapper extends SimpleComponent implements RefreshFooter {

    public RefreshFooterWrapper(View wrapper) {
        super(wrapper);
    }
}
