
package com.github.pdfviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.RelativeLayout;
import com.github.pdfviewer.exception.PageRenderingException;
import com.github.pdfviewer.link.DefaultLinkHandler;
import com.github.pdfviewer.link.LinkHandler;
import com.github.pdfviewer.listener.Callbacks;
import com.github.pdfviewer.listener.OnDrawListener;
import com.github.pdfviewer.listener.OnErrorListener;
import com.github.pdfviewer.listener.OnLoadCompleteListener;
import com.github.pdfviewer.listener.OnLongPressListener;
import com.github.pdfviewer.listener.OnPageChangeListener;
import com.github.pdfviewer.listener.OnPageErrorListener;
import com.github.pdfviewer.listener.OnPageScrollListener;
import com.github.pdfviewer.listener.OnRenderListener;
import com.github.pdfviewer.listener.OnTapListener;
import com.github.pdfviewer.model.PagePart;
import com.github.pdfviewer.scroll.ScrollHandle;
import com.github.pdfviewer.source.AssetSource;
import com.github.pdfviewer.source.ByteArraySource;
import com.github.pdfviewer.source.DocumentSource;
import com.github.pdfviewer.source.FileSource;
import com.github.pdfviewer.source.InputStreamSource;
import com.github.pdfviewer.source.UriSource;
import com.github.pdfviewer.util.Constants;
import com.github.pdfviewer.util.FitPolicy;
import com.github.pdfviewer.util.MathUtils;
import com.github.pdfviewer.util.SnapEdge;
import com.github.pdfviewer.util.Util;
import com.shockwave.pdfium.PdfDocument;
import com.shockwave.pdfium.PdfiumCore;
import com.shockwave.pdfium.util.Size;
import com.shockwave.pdfium.util.SizeF;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class PDFView extends RelativeLayout {

    private static final String TAG = PDFView.class.getSimpleName();

    public static final float DEFAULT_MAX_SCALE = 3.0f;
    public static final float DEFAULT_MID_SCALE = 1.75f;
    public static final float DEFAULT_MIN_SCALE = 1.0f;

    private float minZoom = DEFAULT_MIN_SCALE;
    private float midZoom = DEFAULT_MID_SCALE;
    private float maxZoom = DEFAULT_MAX_SCALE;

    
    enum ScrollDir {
        NONE, START, END
    }

    private ScrollDir scrollDir = ScrollDir.NONE;

    
    CacheManager cacheManager;

    
    private AnimationManager animationManager;

    
    private DragPinchManager dragPinchManager;

    PdfFile pdfFile;

    
    private int currentPage;

    
    private float currentXOffset = 0;

    
    private float currentYOffset = 0;

    
    private float zoom = 1f;

    
    private boolean recycled = true;

    
    private State state = State.DEFAULT;

    
    private DecodingAsyncTask decodingAsyncTask;

    
    private HandlerThread renderingHandlerThread;
    
    RenderingHandler renderingHandler;

    private PagesLoader pagesLoader;

    Callbacks callbacks = new Callbacks();

    
    private Paint paint;

    
    private Paint debugPaint;

    
    private FitPolicy pageFitPolicy = FitPolicy.WIDTH;

    private boolean fitEachPage = false;

    private int defaultPage = 0;

    
    private boolean swipeVertical = true;

    private boolean enableSwipe = true;

    private boolean doubletapEnabled = true;

    private boolean nightMode = false;

    private boolean pageSnap = true;

    
    private PdfiumCore pdfiumCore;

    private ScrollHandle scrollHandle;

    private boolean isScrollHandleInit = false;

    ScrollHandle getScrollHandle() {
        return scrollHandle;
    }

    
    private boolean bestQuality = false;

    
    private boolean annotationRendering = false;

    
    private boolean renderDuringScale = false;

    
    private boolean enableAntialiasing = true;
    private PaintFlagsDrawFilter antialiasFilter =
            new PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    
    private int spacingPx = 0;

    
    private boolean autoSpacing = false;

    
    private boolean pageFling = true;

    
    private List<Integer> onDrawPagesNums = new ArrayList<>(10);

    
    private boolean hasSize = false;

    
    private Configurator waitingDocumentConfigurator;

    
    public PDFView(Context context, AttributeSet set) {
        super(context, set);

        renderingHandlerThread = new HandlerThread("PDF renderer");

        if (isInEditMode()) {
            return;
        }

        cacheManager = new CacheManager();
        animationManager = new AnimationManager(this);
        dragPinchManager = new DragPinchManager(this, animationManager);
        pagesLoader = new PagesLoader(this);

        paint = new Paint();
        debugPaint = new Paint();
        debugPaint.setStyle(Style.STROKE);

        pdfiumCore = new PdfiumCore(context);
        setWillNotDraw(false);
    }

    private void load(DocumentSource docSource, String password) {
        load(docSource, password, null);
    }

    private void load(DocumentSource docSource, String password, int[] userPages) {

        if (!recycled) {
            throw new IllegalStateException("Don't call load on a PDF View without recycling it first.");
        }

        recycled = false;
        // Start decoding document
        decodingAsyncTask = new DecodingAsyncTask(docSource, password, userPages, this, pdfiumCore);
        decodingAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    
    public void jumpTo(int page, boolean withAnimation) {
        if (pdfFile == null) {
            return;
        }

        page = pdfFile.determineValidPageNumberFrom(page);
        float offset = page == 0 ? 0 : -pdfFile.getPageOffset(page, zoom);
        if (swipeVertical) {
            if (withAnimation) {
                animationManager.startYAnimation(currentYOffset, offset);
            } else {
                moveTo(currentXOffset, offset);
            }
        } else {
            if (withAnimation) {
                animationManager.startXAnimation(currentXOffset, offset);
            } else {
                moveTo(offset, currentYOffset);
            }
        }
        showPage(page);
    }

    public void jumpTo(int page) {
        jumpTo(page, false);
    }

    void showPage(int pageNb) {
        if (recycled) {
            return;
        }

        // Check the page number and makes the
        // difference between UserPages and DocumentPages
        pageNb = pdfFile.determineValidPageNumberFrom(pageNb);
        currentPage = pageNb;

        loadPages();

        if (scrollHandle != null && !documentFitsView()) {
            scrollHandle.setPageNum(currentPage + 1);
        }

        callbacks.callOnPageChange(currentPage, pdfFile.getPagesCount());
    }

    
    public float getPositionOffset() {
        float offset;
        if (swipeVertical) {
            offset = -currentYOffset / (pdfFile.getDocLen(zoom) - getHeight());
        } else {
            offset = -currentXOffset / (pdfFile.getDocLen(zoom) - getWidth());
        }
        return MathUtils.limit(offset, 0, 1);
    }

    
    public void setPositionOffset(float progress, boolean moveHandle) {
        if (swipeVertical) {
            moveTo(currentXOffset, (-pdfFile.getDocLen(zoom) + getHeight()) * progress, moveHandle);
        } else {
            moveTo((-pdfFile.getDocLen(zoom) + getWidth()) * progress, currentYOffset, moveHandle);
        }
        loadPageByOffset();
    }

    public void setPositionOffset(float progress) {
        setPositionOffset(progress, true);
    }

    public void stopFling() {
        animationManager.stopFling();
    }

    public int getPageCount() {
        if (pdfFile == null) {
            return 0;
        }
        return pdfFile.getPagesCount();
    }

    public void setSwipeEnabled(boolean enableSwipe) {
        this.enableSwipe = enableSwipe;
    }

    public void setNightMode(boolean nightMode) {
        this.nightMode = nightMode;
        if (nightMode) {
            ColorMatrix colorMatrixInverted =
                    new ColorMatrix(new float[]{
                            -1, 0, 0, 0, 255,
                            0, -1, 0, 0, 255,
                            0, 0, -1, 0, 255,
                            0, 0, 0, 1, 0});

            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrixInverted);
            paint.setColorFilter(filter);
        } else {
            paint.setColorFilter(null);
        }
    }

    void enableDoubletap(boolean enableDoubletap) {
        this.doubletapEnabled = enableDoubletap;
    }

    boolean isDoubletapEnabled() {
        return doubletapEnabled;
    }

    void onPageError(PageRenderingException ex) {
        if (!callbacks.callOnPageError(ex.getPage(), ex.getCause())) {
            Log.e(TAG, "Cannot open page " + ex.getPage(), ex.getCause());
        }
    }

    public void recycle() {
        waitingDocumentConfigurator = null;

        animationManager.stopAll();
        dragPinchManager.disable();

        // Stop tasks
        if (renderingHandler != null) {
            renderingHandler.stop();
            renderingHandler.removeMessages(RenderingHandler.MSG_RENDER_TASK);
        }
        if (decodingAsyncTask != null) {
            decodingAsyncTask.cancel(true);
        }

        // Clear caches
        cacheManager.recycle();

        if (scrollHandle != null && isScrollHandleInit) {
            scrollHandle.destroyLayout();
        }

        if (pdfFile != null) {
            pdfFile.dispose();
            pdfFile = null;
        }

        renderingHandler = null;
        scrollHandle = null;
        isScrollHandleInit = false;
        currentXOffset = currentYOffset = 0;
        zoom = 1f;
        recycled = true;
        callbacks = new Callbacks();
        state = State.DEFAULT;
    }

    public boolean isRecycled() {
        return recycled;
    }

    
    @Override
    public void computeScroll() {
        super.computeScroll();
        if (isInEditMode()) {
            return;
        }
        animationManager.computeFling();
    }

    @Override
    protected void onDetachedFromWindow() {
        recycle();
        if (renderingHandlerThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                renderingHandlerThread.quitSafely();
            } else {
                renderingHandlerThread.quit();
            }
            renderingHandlerThread = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        hasSize = true;
        if (waitingDocumentConfigurator != null) {
            waitingDocumentConfigurator.load();
        }
        if (isInEditMode() || state != State.SHOWN) {
            return;
        }

        // calculates the position of the point which in the center of view relative to big strip
        float centerPointInStripXOffset = -currentXOffset + oldw * 0.5f;
        float centerPointInStripYOffset = -currentYOffset + oldh * 0.5f;

        float relativeCenterPointInStripXOffset;
        float relativeCenterPointInStripYOffset;

        if (swipeVertical){
            relativeCenterPointInStripXOffset = centerPointInStripXOffset / pdfFile.getMaxPageWidth();
            relativeCenterPointInStripYOffset = centerPointInStripYOffset / pdfFile.getDocLen(zoom);
        }else {
            relativeCenterPointInStripXOffset = centerPointInStripXOffset / pdfFile.getDocLen(zoom);
            relativeCenterPointInStripYOffset = centerPointInStripYOffset / pdfFile.getMaxPageHeight();
        }

        animationManager.stopAll();
        pdfFile.recalculatePageSizes(new Size(w, h));

        if (swipeVertical) {
            currentXOffset = -relativeCenterPointInStripXOffset * pdfFile.getMaxPageWidth() + w * 0.5f;
            currentYOffset = -relativeCenterPointInStripYOffset * pdfFile.getDocLen(zoom) + h * 0.5f ;
        }else {
            currentXOffset = -relativeCenterPointInStripXOffset * pdfFile.getDocLen(zoom) + w * 0.5f;
            currentYOffset = -relativeCenterPointInStripYOffset * pdfFile.getMaxPageHeight() + h * 0.5f;
        }
        moveTo(currentXOffset,currentYOffset);
        loadPageByOffset();
    }

    @Override
    public boolean canScrollHorizontally(int direction) {
        if (pdfFile == null) {
            return true;
        }

        if (swipeVertical) {
            if (direction < 0 && currentXOffset < 0) {
                return true;
            } else if (direction > 0 && currentXOffset + toCurrentScale(pdfFile.getMaxPageWidth()) > getWidth()) {
                return true;
            }
        } else {
            if (direction < 0 && currentXOffset < 0) {
                return true;
            } else if (direction > 0 && currentXOffset + pdfFile.getDocLen(zoom) > getWidth()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canScrollVertically(int direction) {
        if (pdfFile == null) {
            return true;
        }

        if (swipeVertical) {
            if (direction < 0 && currentYOffset < 0) {
                return true;
            } else if (direction > 0 && currentYOffset + pdfFile.getDocLen(zoom) > getHeight()) {
                return true;
            }
        } else {
            if (direction < 0 && currentYOffset < 0) {
                return true;
            } else if (direction > 0 && currentYOffset + toCurrentScale(pdfFile.getMaxPageHeight()) > getHeight()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isInEditMode()) {
            return;
        }
        // As I said in this class javadoc, we can think of this canvas as a huge
        // strip on which we draw all the images. We actually only draw the rendered
        // parts, of course, but we render them in the place they belong in this huge
        // strip.

        // That's where Canvas.translate(x, y) becomes very helpful.
        // This is the situation :
        //  _______________________________________________
        // |   			 |					 			   |
        // | the actual  |					The big strip  |
        // |	canvas	 | 								   |
        // |_____________|								   |
        // |_______________________________________________|
        //
        // If the rendered part is on the bottom right corner of the strip
        // we can draw it but we won't see it because the canvas is not big enough.

        // But if we call translate(-X, -Y) on the canvas just before drawing the object :
        //  _______________________________________________
        // |   			  					  _____________|
        // |   The big strip     			 |			   |
        // |		    					 |	the actual |
        // |								 |	canvas	   |
        // |_________________________________|_____________|
        //
        // The object will be on the canvas.
        // This technique is massively used in this method, and allows
        // abstraction of the screen position when rendering the parts.

        // Draws background

        if (enableAntialiasing) {
            canvas.setDrawFilter(antialiasFilter);
        }

        Drawable bg = getBackground();
        if (bg == null) {
            canvas.drawColor(nightMode ? Color.BLACK : Color.WHITE);
        } else {
            bg.draw(canvas);
        }

        if (recycled) {
            return;
        }

        if (state != State.SHOWN) {
            return;
        }

        // Moves the canvas before drawing any element
        float currentXOffset = this.currentXOffset;
        float currentYOffset = this.currentYOffset;
        canvas.translate(currentXOffset, currentYOffset);

        // Draws thumbnails
        for (PagePart part : cacheManager.getThumbnails()) {
            drawPart(canvas, part);

        }

        // Draws parts
        for (PagePart part : cacheManager.getPageParts()) {
            drawPart(canvas, part);
            if (callbacks.getOnDrawAll() != null
                    && !onDrawPagesNums.contains(part.getPage())) {
                onDrawPagesNums.add(part.getPage());
            }
        }

        for (Integer page : onDrawPagesNums) {
            drawWithListener(canvas, page, callbacks.getOnDrawAll());
        }
        onDrawPagesNums.clear();

        drawWithListener(canvas, currentPage, callbacks.getOnDraw());

        // Restores the canvas position
        canvas.translate(-currentXOffset, -currentYOffset);
    }

    private void drawWithListener(Canvas canvas, int page, OnDrawListener listener) {
        if (listener != null) {
            float translateX, translateY;
            if (swipeVertical) {
                translateX = 0;
                translateY = pdfFile.getPageOffset(page, zoom);
            } else {
                translateY = 0;
                translateX = pdfFile.getPageOffset(page, zoom);
            }

            canvas.translate(translateX, translateY);
            SizeF size = pdfFile.getPageSize(page);
            listener.onLayerDrawn(canvas,
                    toCurrentScale(size.getWidth()),
                    toCurrentScale(size.getHeight()),
                    page);

            canvas.translate(-translateX, -translateY);
        }
    }

    
    private void drawPart(Canvas canvas, PagePart part) {
        // Can seem strange, but avoid lot of calls
        RectF pageRelativeBounds = part.getPageRelativeBounds();
        Bitmap renderedBitmap = part.getRenderedBitmap();

        if (renderedBitmap.isRecycled()) {
            return;
        }

        // Move to the target page
        float localTranslationX = 0;
        float localTranslationY = 0;
        SizeF size = pdfFile.getPageSize(part.getPage());

        if (swipeVertical) {
            localTranslationY = pdfFile.getPageOffset(part.getPage(), zoom);
            float maxWidth = pdfFile.getMaxPageWidth();
            localTranslationX = toCurrentScale(maxWidth - size.getWidth()) / 2;
        } else {
            localTranslationX = pdfFile.getPageOffset(part.getPage(), zoom);
            float maxHeight = pdfFile.getMaxPageHeight();
            localTranslationY = toCurrentScale(maxHeight - size.getHeight()) / 2;
        }
        canvas.translate(localTranslationX, localTranslationY);

        Rect srcRect = new Rect(0, 0, renderedBitmap.getWidth(),
                renderedBitmap.getHeight());

        float offsetX = toCurrentScale(pageRelativeBounds.left * size.getWidth());
        float offsetY = toCurrentScale(pageRelativeBounds.top * size.getHeight());
        float width = toCurrentScale(pageRelativeBounds.width() * size.getWidth());
        float height = toCurrentScale(pageRelativeBounds.height() * size.getHeight());

        // If we use float values for this rectangle, there will be
        // a possible gap between page parts, especially when
        // the zoom level is high.
        RectF dstRect = new RectF((int) offsetX, (int) offsetY,
                (int) (offsetX + width),
                (int) (offsetY + height));

        // Check if bitmap is in the screen
        float translationX = currentXOffset + localTranslationX;
        float translationY = currentYOffset + localTranslationY;
        if (translationX + dstRect.left >= getWidth() || translationX + dstRect.right <= 0 ||
                translationY + dstRect.top >= getHeight() || translationY + dstRect.bottom <= 0) {
            canvas.translate(-localTranslationX, -localTranslationY);
            return;
        }

        canvas.drawBitmap(renderedBitmap, srcRect, dstRect, paint);

        if (Constants.DEBUG_MODE) {
            debugPaint.setColor(part.getPage() % 2 == 0 ? Color.RED : Color.BLUE);
            canvas.drawRect(dstRect, debugPaint);
        }

        // Restore the canvas position
        canvas.translate(-localTranslationX, -localTranslationY);

    }

    
    public void loadPages() {
        if (pdfFile == null || renderingHandler == null) {
            return;
        }

        // Cancel all current tasks
        renderingHandler.removeMessages(RenderingHandler.MSG_RENDER_TASK);
        cacheManager.makeANewSet();

        pagesLoader.loadPages();
        redraw();
    }

    
    /*void loadComplete(PdfFile pdfFile) {
        state = State.LOADED;

        this.pdfFile = pdfFile;

        if (!renderingHandlerThread.isAlive()) {
            renderingHandlerThread.start();
        }
        renderingHandler = new RenderingHandler(renderingHandlerThread.getLooper(), this);
        renderingHandler.start();

        if (scrollHandle != null) {
            scrollHandle.setupLayout(this);
            isScrollHandleInit = true;
        }

        dragPinchManager.enable();

        callbacks.callOnLoadComplete(pdfFile.getPagesCount());

        jumpTo(defaultPage, false);
    }*/

    void loadComplete(PdfFile pdfFile) {
        state = State.LOADED;
        this.pdfFile = pdfFile;
        if (renderingHandlerThread == null) {
            renderingHandlerThread = new HandlerThread("RenderingThread");
            renderingHandlerThread.start();
        }
        if (!renderingHandlerThread.isAlive()) {
            renderingHandlerThread.start();
        }
        renderingHandler = new RenderingHandler(renderingHandlerThread.getLooper(), this);
        renderingHandler.start();
        if (scrollHandle != null) {
            scrollHandle.setupLayout(this);
            isScrollHandleInit = true;
        }
        dragPinchManager.enable();
        callbacks.callOnLoadComplete(pdfFile.getPagesCount());
        jumpTo(defaultPage, false);
    }


    void loadError(Throwable t) {
        state = State.ERROR;
        // store reference, because callbacks will be cleared in recycle() method
        OnErrorListener onErrorListener = callbacks.getOnError();
        recycle();
        invalidate();
        if (onErrorListener != null) {
            onErrorListener.onError(t);
        } else {
            Log.e("PDFView", "load pdf error", t);
        }
    }

    void redraw() {
        invalidate();
    }

    
    public void onBitmapRendered(PagePart part) {
        // when it is first rendered part
        if (state == State.LOADED) {
            state = State.SHOWN;
            callbacks.callOnRender(pdfFile.getPagesCount());
        }

        if (part.isThumbnail()) {
            cacheManager.cacheThumbnail(part);
        } else {
            cacheManager.cachePart(part);
        }
        redraw();
    }

    public void moveTo(float offsetX, float offsetY) {
        moveTo(offsetX, offsetY, true);
    }

    
    public void moveTo(float offsetX, float offsetY, boolean moveHandle) {
        if (swipeVertical) {
            // Check X offset
            float scaledPageWidth = toCurrentScale(pdfFile.getMaxPageWidth());
            if (scaledPageWidth < getWidth()) {
                offsetX = getWidth() / 2 - scaledPageWidth / 2;
            } else {
                if (offsetX > 0) {
                    offsetX = 0;
                } else if (offsetX + scaledPageWidth < getWidth()) {
                    offsetX = getWidth() - scaledPageWidth;
                }
            }

            // Check Y offset
            float contentHeight = pdfFile.getDocLen(zoom);
            if (contentHeight < getHeight()) { // whole document height visible on screen
                offsetY = (getHeight() - contentHeight) / 2;
            } else {
                if (offsetY > 0) { // top visible
                    offsetY = 0;
                } else if (offsetY + contentHeight < getHeight()) { // bottom visible
                    offsetY = -contentHeight + getHeight();
                }
            }

            if (offsetY < currentYOffset) {
                scrollDir = ScrollDir.END;
            } else if (offsetY > currentYOffset) {
                scrollDir = ScrollDir.START;
            } else {
                scrollDir = ScrollDir.NONE;
            }
        } else {
            // Check Y offset
            float scaledPageHeight = toCurrentScale(pdfFile.getMaxPageHeight());
            if (scaledPageHeight < getHeight()) {
                offsetY = getHeight() / 2 - scaledPageHeight / 2;
            } else {
                if (offsetY > 0) {
                    offsetY = 0;
                } else if (offsetY + scaledPageHeight < getHeight()) {
                    offsetY = getHeight() - scaledPageHeight;
                }
            }

            // Check X offset
            float contentWidth = pdfFile.getDocLen(zoom);
            if (contentWidth < getWidth()) { // whole document width visible on screen
                offsetX = (getWidth() - contentWidth) / 2;
            } else {
                if (offsetX > 0) { // left visible
                    offsetX = 0;
                } else if (offsetX + contentWidth < getWidth()) { // right visible
                    offsetX = -contentWidth + getWidth();
                }
            }

            if (offsetX < currentXOffset) {
                scrollDir = ScrollDir.END;
            } else if (offsetX > currentXOffset) {
                scrollDir = ScrollDir.START;
            } else {
                scrollDir = ScrollDir.NONE;
            }
        }

        currentXOffset = offsetX;
        currentYOffset = offsetY;
        float positionOffset = getPositionOffset();

        if (moveHandle && scrollHandle != null && !documentFitsView()) {
            scrollHandle.setScroll(positionOffset);
        }

        callbacks.callOnPageScroll(getCurrentPage(), positionOffset);

        redraw();
    }

    void loadPageByOffset() {
        if (0 == pdfFile.getPagesCount()) {
            return;
        }

        float offset, screenCenter;
        if (swipeVertical) {
            offset = currentYOffset;
            screenCenter = ((float) getHeight()) / 2;
        } else {
            offset = currentXOffset;
            screenCenter = ((float) getWidth()) / 2;
        }

        int page = pdfFile.getPageAtOffset(-(offset - screenCenter), zoom);

        if (page >= 0 && page <= pdfFile.getPagesCount() - 1 && page != getCurrentPage()) {
            showPage(page);
        } else {
            loadPages();
        }
    }

    
    public void performPageSnap() {
        if (!pageSnap || pdfFile == null || pdfFile.getPagesCount() == 0) {
            return;
        }
        int centerPage = findFocusPage(currentXOffset, currentYOffset);
        SnapEdge edge = findSnapEdge(centerPage);
        if (edge == SnapEdge.NONE) {
            return;
        }

        float offset = snapOffsetForPage(centerPage, edge);
        if (swipeVertical) {
            animationManager.startYAnimation(currentYOffset, -offset);
        } else {
            animationManager.startXAnimation(currentXOffset, -offset);
        }
    }

    
    SnapEdge findSnapEdge(int page) {
        if (!pageSnap || page < 0) {
            return SnapEdge.NONE;
        }
        float currentOffset = swipeVertical ? currentYOffset : currentXOffset;
        float offset = -pdfFile.getPageOffset(page, zoom);
        int length = swipeVertical ? getHeight() : getWidth();
        float pageLength = pdfFile.getPageLength(page, zoom);

        if (length >= pageLength) {
            return SnapEdge.CENTER;
        } else if (currentOffset >= offset) {
            return SnapEdge.START;
        } else if (offset - pageLength > currentOffset - length) {
            return SnapEdge.END;
        } else {
            return SnapEdge.NONE;
        }
    }

    
    float snapOffsetForPage(int pageIndex, SnapEdge edge) {
        float offset = pdfFile.getPageOffset(pageIndex, zoom);

        float length = swipeVertical ? getHeight() : getWidth();
        float pageLength = pdfFile.getPageLength(pageIndex, zoom);

        if (edge == SnapEdge.CENTER) {
            offset = offset - length / 2f + pageLength / 2f;
        } else if (edge == SnapEdge.END) {
            offset = offset - length + pageLength;
        }
        return offset;
    }

    int findFocusPage(float xOffset, float yOffset) {
        float currOffset = swipeVertical ? yOffset : xOffset;
        float length = swipeVertical ? getHeight() : getWidth();
        // make sure first and last page can be found
        if (currOffset > -1) {
            return 0;
        } else if (currOffset < -pdfFile.getDocLen(zoom) + length + 1) {
            return pdfFile.getPagesCount() - 1;
        }
        // else find page in center
        float center = currOffset - length / 2f;
        return pdfFile.getPageAtOffset(-center, zoom);
    }

    
    public boolean pageFillsScreen() {
        float start = -pdfFile.getPageOffset(currentPage, zoom);
        float end = start - pdfFile.getPageLength(currentPage, zoom);
        if (isSwipeVertical()) {
            return start > currentYOffset && end < currentYOffset - getHeight();
        } else {
            return start > currentXOffset && end < currentXOffset - getWidth();
        }
    }

    
    public void moveRelativeTo(float dx, float dy) {
        moveTo(currentXOffset + dx, currentYOffset + dy);
    }

    
    public void zoomTo(float zoom) {
        this.zoom = zoom;
    }

    
    public void zoomCenteredTo(float zoom, PointF pivot) {
        float dzoom = zoom / this.zoom;
        zoomTo(zoom);
        float baseX = currentXOffset * dzoom;
        float baseY = currentYOffset * dzoom;
        baseX += (pivot.x - pivot.x * dzoom);
        baseY += (pivot.y - pivot.y * dzoom);
        moveTo(baseX, baseY);
    }

    
    public void zoomCenteredRelativeTo(float dzoom, PointF pivot) {
        zoomCenteredTo(zoom * dzoom, pivot);
    }

    
    public boolean documentFitsView() {
        float len = pdfFile.getDocLen(1);
        if (swipeVertical) {
            return len < getHeight();
        } else {
            return len < getWidth();
        }
    }

    public void fitToWidth(int page) {
        if (state != State.SHOWN) {
            Log.e(TAG, "Cannot fit, document not rendered yet");
            return;
        }
        zoomTo(getWidth() / pdfFile.getPageSize(page).getWidth());
        jumpTo(page);
    }

    public SizeF getPageSize(int pageIndex) {
        if (pdfFile == null) {
            return new SizeF(0, 0);
        }
        return pdfFile.getPageSize(pageIndex);
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public float getCurrentXOffset() {
        return currentXOffset;
    }

    public float getCurrentYOffset() {
        return currentYOffset;
    }

    public float toRealScale(float size) {
        return size / zoom;
    }

    public float toCurrentScale(float size) {
        return size * zoom;
    }

    public float getZoom() {
        return zoom;
    }

    public boolean isZooming() {
        return zoom != minZoom;
    }

    private void setDefaultPage(int defaultPage) {
        this.defaultPage = defaultPage;
    }

    public void resetZoom() {
        zoomTo(minZoom);
    }

    public void resetZoomWithAnimation() {
        zoomWithAnimation(minZoom);
    }

    public void zoomWithAnimation(float centerX, float centerY, float scale) {
        animationManager.startZoomAnimation(centerX, centerY, zoom, scale);
    }

    public void zoomWithAnimation(float scale) {
        animationManager.startZoomAnimation(getWidth() / 2, getHeight() / 2, zoom, scale);
    }

    private void setScrollHandle(ScrollHandle scrollHandle) {
        this.scrollHandle = scrollHandle;
    }

    
    public int getPageAtPositionOffset(float positionOffset) {
        return pdfFile.getPageAtOffset(pdfFile.getDocLen(zoom) * positionOffset, zoom);
    }

    public float getMinZoom() {
        return minZoom;
    }

    public void setMinZoom(float minZoom) {
        this.minZoom = minZoom;
    }

    public float getMidZoom() {
        return midZoom;
    }

    public void setMidZoom(float midZoom) {
        this.midZoom = midZoom;
    }

    public float getMaxZoom() {
        return maxZoom;
    }

    public void setMaxZoom(float maxZoom) {
        this.maxZoom = maxZoom;
    }

    public void useBestQuality(boolean bestQuality) {
        this.bestQuality = bestQuality;
    }

    public boolean isBestQuality() {
        return bestQuality;
    }

    public boolean isSwipeVertical() {
        return swipeVertical;
    }

    public boolean isSwipeEnabled() {
        return enableSwipe;
    }

    private void setSwipeVertical(boolean swipeVertical) {
        this.swipeVertical = swipeVertical;
    }

    public void enableAnnotationRendering(boolean annotationRendering) {
        this.annotationRendering = annotationRendering;
    }

    public boolean isAnnotationRendering() {
        return annotationRendering;
    }

    public void enableRenderDuringScale(boolean renderDuringScale) {
        this.renderDuringScale = renderDuringScale;
    }

    public boolean isAntialiasing() {
        return enableAntialiasing;
    }

    public void enableAntialiasing(boolean enableAntialiasing) {
        this.enableAntialiasing = enableAntialiasing;
    }

    public int getSpacingPx() {
        return spacingPx;
    }

    public boolean isAutoSpacingEnabled() {
        return autoSpacing;
    }

    public void setPageFling(boolean pageFling) {
        this.pageFling = pageFling;
    }

    public boolean isPageFlingEnabled() {
        return pageFling;
    }

    private void setSpacing(int spacingDp) {
        this.spacingPx = Util.getDP(getContext(), spacingDp);
    }

    private void setAutoSpacing(boolean autoSpacing) {
        this.autoSpacing = autoSpacing;
    }

    private void setPageFitPolicy(FitPolicy pageFitPolicy) {
        this.pageFitPolicy = pageFitPolicy;
    }

    public FitPolicy getPageFitPolicy() {
        return pageFitPolicy;
    }

    private void setFitEachPage(boolean fitEachPage) {
        this.fitEachPage = fitEachPage;
    }

    public boolean isFitEachPage() {
        return fitEachPage;
    }

    public boolean isPageSnap() {
        return pageSnap;
    }

    public void setPageSnap(boolean pageSnap) {
        this.pageSnap = pageSnap;
    }

    public boolean doRenderDuringScale() {
        return renderDuringScale;
    }

    
    public PdfDocument.Meta getDocumentMeta() {
        if (pdfFile == null) {
            return null;
        }
        return pdfFile.getMetaData();
    }

    
    public List<PdfDocument.Bookmark> getTableOfContents() {
        if (pdfFile == null) {
            return Collections.emptyList();
        }
        return pdfFile.getBookmarks();
    }

    
    public List<PdfDocument.Link> getLinks(int page) {
        if (pdfFile == null) {
            return Collections.emptyList();
        }
        return pdfFile.getPageLinks(page);
    }

    
    public Configurator fromAsset(String assetName) {
        return new Configurator(new AssetSource(assetName));
    }

    
    public Configurator fromFile(File file) {
        return new Configurator(new FileSource(file));
    }

    
    public Configurator fromUri(Uri uri) {
        return new Configurator(new UriSource(uri));
    }

    
    public Configurator fromBytes(byte[] bytes) {
        return new Configurator(new ByteArraySource(bytes));
    }

    
    public Configurator fromStream(InputStream stream) {
        return new Configurator(new InputStreamSource(stream));
    }

    
    public Configurator fromSource(DocumentSource docSource) {
        return new Configurator(docSource);
    }

    private enum State {DEFAULT, LOADED, SHOWN, ERROR}

    public class Configurator {

        private final DocumentSource documentSource;

        private int[] pageNumbers = null;

        private boolean enableSwipe = true;

        private boolean enableDoubletap = true;

        private OnDrawListener onDrawListener;

        private OnDrawListener onDrawAllListener;

        private OnLoadCompleteListener onLoadCompleteListener;

        private OnErrorListener onErrorListener;

        private OnPageChangeListener onPageChangeListener;

        private OnPageScrollListener onPageScrollListener;

        private OnRenderListener onRenderListener;

        private OnTapListener onTapListener;

        private OnLongPressListener onLongPressListener;

        private OnPageErrorListener onPageErrorListener;

        private LinkHandler linkHandler = new DefaultLinkHandler(PDFView.this);

        private int defaultPage = 0;

        private boolean swipeHorizontal = false;

        private boolean annotationRendering = false;

        private String password = null;

        private ScrollHandle scrollHandle = null;

        private boolean antialiasing = true;

        private int spacing = 0;

        private boolean autoSpacing = false;

        private FitPolicy pageFitPolicy = FitPolicy.WIDTH;

        private boolean fitEachPage = false;

        private boolean pageFling = false;

        private boolean pageSnap = false;

        private boolean nightMode = false;

        private Configurator(DocumentSource documentSource) {
            this.documentSource = documentSource;
        }

        public Configurator pages(int... pageNumbers) {
            this.pageNumbers = pageNumbers;
            return this;
        }

        public Configurator enableSwipe(boolean enableSwipe) {
            this.enableSwipe = enableSwipe;
            return this;
        }

        public Configurator enableDoubletap(boolean enableDoubletap) {
            this.enableDoubletap = enableDoubletap;
            return this;
        }

        public Configurator enableAnnotationRendering(boolean annotationRendering) {
            this.annotationRendering = annotationRendering;
            return this;
        }

        public Configurator onDraw(OnDrawListener onDrawListener) {
            this.onDrawListener = onDrawListener;
            return this;
        }

        public Configurator onDrawAll(OnDrawListener onDrawAllListener) {
            this.onDrawAllListener = onDrawAllListener;
            return this;
        }

        public Configurator onLoad(OnLoadCompleteListener onLoadCompleteListener) {
            this.onLoadCompleteListener = onLoadCompleteListener;
            return this;
        }

        public Configurator onPageScroll(OnPageScrollListener onPageScrollListener) {
            this.onPageScrollListener = onPageScrollListener;
            return this;
        }

        public Configurator onError(OnErrorListener onErrorListener) {
            this.onErrorListener = onErrorListener;
            return this;
        }

        public Configurator onPageError(OnPageErrorListener onPageErrorListener) {
            this.onPageErrorListener = onPageErrorListener;
            return this;
        }

        public Configurator onPageChange(OnPageChangeListener onPageChangeListener) {
            this.onPageChangeListener = onPageChangeListener;
            return this;
        }

        public Configurator onRender(OnRenderListener onRenderListener) {
            this.onRenderListener = onRenderListener;
            return this;
        }

        public Configurator onTap(OnTapListener onTapListener) {
            this.onTapListener = onTapListener;
            return this;
        }

        public Configurator onLongPress(OnLongPressListener onLongPressListener) {
            this.onLongPressListener = onLongPressListener;
            return this;
        }

        public Configurator linkHandler(LinkHandler linkHandler) {
            this.linkHandler = linkHandler;
            return this;
        }

        public Configurator defaultPage(int defaultPage) {
            this.defaultPage = defaultPage;
            return this;
        }

        public Configurator swipeHorizontal(boolean swipeHorizontal) {
            this.swipeHorizontal = swipeHorizontal;
            return this;
        }

        public Configurator password(String password) {
            this.password = password;
            return this;
        }

        public Configurator scrollHandle(ScrollHandle scrollHandle) {
            this.scrollHandle = scrollHandle;
            return this;
        }

        public Configurator enableAntialiasing(boolean antialiasing) {
            this.antialiasing = antialiasing;
            return this;
        }

        public Configurator spacing(int spacing) {
            this.spacing = spacing;
            return this;
        }

        public Configurator autoSpacing(boolean autoSpacing) {
            this.autoSpacing = autoSpacing;
            return this;
        }

        public Configurator pageFitPolicy(FitPolicy pageFitPolicy) {
            this.pageFitPolicy = pageFitPolicy;
            return this;
        }

        public Configurator fitEachPage(boolean fitEachPage) {
            this.fitEachPage = fitEachPage;
            return this;
        }

        public Configurator pageSnap(boolean pageSnap) {
            this.pageSnap = pageSnap;
            return this;
        }

        public Configurator pageFling(boolean pageFling) {
            this.pageFling = pageFling;
            return this;
        }

        public Configurator nightMode(boolean nightMode) {
            this.nightMode = nightMode;
            return this;
        }

        public Configurator disableLongpress() {
            PDFView.this.dragPinchManager.disableLongpress();
            return this;
        }

        public void load() {
            if (!hasSize) {
                waitingDocumentConfigurator = this;
                return;
            }
            PDFView.this.recycle();
            PDFView.this.callbacks.setOnLoadComplete(onLoadCompleteListener);
            PDFView.this.callbacks.setOnError(onErrorListener);
            PDFView.this.callbacks.setOnDraw(onDrawListener);
            PDFView.this.callbacks.setOnDrawAll(onDrawAllListener);
            PDFView.this.callbacks.setOnPageChange(onPageChangeListener);
            PDFView.this.callbacks.setOnPageScroll(onPageScrollListener);
            PDFView.this.callbacks.setOnRender(onRenderListener);
            PDFView.this.callbacks.setOnTap(onTapListener);
            PDFView.this.callbacks.setOnLongPress(onLongPressListener);
            PDFView.this.callbacks.setOnPageError(onPageErrorListener);
            PDFView.this.callbacks.setLinkHandler(linkHandler);
            PDFView.this.setSwipeEnabled(enableSwipe);
            PDFView.this.setNightMode(nightMode);
            PDFView.this.enableDoubletap(enableDoubletap);
            PDFView.this.setDefaultPage(defaultPage);
            PDFView.this.setSwipeVertical(!swipeHorizontal);
            PDFView.this.enableAnnotationRendering(annotationRendering);
            PDFView.this.setScrollHandle(scrollHandle);
            PDFView.this.enableAntialiasing(antialiasing);
            PDFView.this.setSpacing(spacing);
            PDFView.this.setAutoSpacing(autoSpacing);
            PDFView.this.setPageFitPolicy(pageFitPolicy);
            PDFView.this.setFitEachPage(fitEachPage);
            PDFView.this.setPageSnap(pageSnap);
            PDFView.this.setPageFling(pageFling);

            if (pageNumbers != null) {
                PDFView.this.load(documentSource, password, pageNumbers);
            } else {
                PDFView.this.load(documentSource, password);
            }
        }
    }
}
