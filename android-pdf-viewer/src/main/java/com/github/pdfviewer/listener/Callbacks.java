
package com.github.pdfviewer.listener;

import android.view.MotionEvent;

import com.github.pdfviewer.link.LinkHandler;
import com.github.pdfviewer.model.LinkTapEvent;

public class Callbacks {

    
    private OnLoadCompleteListener onLoadCompleteListener;

    
    private OnErrorListener onErrorListener;

    
    private OnPageErrorListener onPageErrorListener;

    
    private OnRenderListener onRenderListener;

    
    private OnPageChangeListener onPageChangeListener;

    
    private OnPageScrollListener onPageScrollListener;

    
    private OnDrawListener onDrawListener;

    private OnDrawListener onDrawAllListener;

    
    private OnTapListener onTapListener;

    
    private OnLongPressListener onLongPressListener;

    
    private LinkHandler linkHandler;

    public void setOnLoadComplete(OnLoadCompleteListener onLoadCompleteListener) {
        this.onLoadCompleteListener = onLoadCompleteListener;
    }

    public void callOnLoadComplete(int pagesCount) {
        if (onLoadCompleteListener != null) {
            onLoadCompleteListener.loadComplete(pagesCount);
        }
    }

    public void setOnError(OnErrorListener onErrorListener) {
        this.onErrorListener = onErrorListener;
    }

    public OnErrorListener getOnError() {
        return onErrorListener;
    }

    public void setOnPageError(OnPageErrorListener onPageErrorListener) {
        this.onPageErrorListener = onPageErrorListener;
    }

    public boolean callOnPageError(int page, Throwable error) {
        if (onPageErrorListener != null) {
            onPageErrorListener.onPageError(page, error);
            return true;
        }
        return false;
    }

    public void setOnRender(OnRenderListener onRenderListener) {
        this.onRenderListener = onRenderListener;
    }

    public void callOnRender(int pagesCount) {
        if (onRenderListener != null) {
            onRenderListener.onInitiallyRendered(pagesCount);
        }
    }

    public void setOnPageChange(OnPageChangeListener onPageChangeListener) {
        this.onPageChangeListener = onPageChangeListener;
    }

    public void callOnPageChange(int page, int pagesCount) {
        if (onPageChangeListener != null) {
            onPageChangeListener.onPageChanged(page, pagesCount);
        }
    }

    public void setOnPageScroll(OnPageScrollListener onPageScrollListener) {
        this.onPageScrollListener = onPageScrollListener;
    }

    public void callOnPageScroll(int currentPage, float offset) {
        if (onPageScrollListener != null) {
            onPageScrollListener.onPageScrolled(currentPage, offset);
        }
    }

    public void setOnDraw(OnDrawListener onDrawListener) {
        this.onDrawListener = onDrawListener;
    }

    public OnDrawListener getOnDraw() {
        return onDrawListener;
    }

    public void setOnDrawAll(OnDrawListener onDrawAllListener) {
        this.onDrawAllListener = onDrawAllListener;
    }

    public OnDrawListener getOnDrawAll() {
        return onDrawAllListener;
    }

    public void setOnTap(OnTapListener onTapListener) {
        this.onTapListener = onTapListener;
    }

    public boolean callOnTap(MotionEvent event) {
        return onTapListener != null && onTapListener.onTap(event);
    }

    public void setOnLongPress(OnLongPressListener onLongPressListener) {
        this.onLongPressListener = onLongPressListener;
    }

    public void callOnLongPress(MotionEvent event) {
        if (onLongPressListener != null) {
            onLongPressListener.onLongPress(event);
        }
    }

    public void setLinkHandler(LinkHandler linkHandler) {
        this.linkHandler = linkHandler;
    }

    public void callLinkHandler(LinkTapEvent event) {
        if (linkHandler != null) {
            linkHandler.handleLinkEvent(event);
        }
    }
}
