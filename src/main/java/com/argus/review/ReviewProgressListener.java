package com.argus.review;

/**
 * 审查进度回调, 供异步任务向前端暴露"正在审查第几个文件"。
 */
public interface ReviewProgressListener {

    ReviewProgressListener NOOP = new ReviewProgressListener() {
    };

    default void onStart(int totalFiles) {
    }

    default void onFileDone(String path, int done, int total) {
    }
}
