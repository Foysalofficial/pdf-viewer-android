package com.github.pdfviewer;

import android.graphics.RectF;
import com.github.pdfviewer.model.PagePart;
import com.github.pdfviewer.util.Constants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import org.jetbrains.annotations.Nullable;

class CacheManager {

    private final PriorityQueue<PagePart> passiveCache;

    private final PriorityQueue<PagePart> activeCache;

    private final List<PagePart> thumbnails;

    private final Object passiveActiveLock = new Object();

    private final PagePartComparator orderComparator = new PagePartComparator();

    public CacheManager() {
        activeCache = new PriorityQueue<>(Constants.Cache.CACHE_SIZE, orderComparator);
        passiveCache = new PriorityQueue<>(Constants.Cache.CACHE_SIZE, orderComparator);
        thumbnails = new ArrayList<>();
    }

    public void cachePart(PagePart part) {
        synchronized (passiveActiveLock) {
            // If cache too big, remove and recycle
            makeAFreeSpace();

            // Then add part
            activeCache.offer(part);
        }
    }

    public void makeANewSet() {
        synchronized (passiveActiveLock) {
            passiveCache.addAll(activeCache);
            activeCache.clear();
        }
    }

    private void makeAFreeSpace() {
        synchronized (passiveActiveLock) {
            while ((activeCache.size() + passiveCache.size()) >= Constants.Cache.CACHE_SIZE &&
                    !passiveCache.isEmpty()) {
                PagePart part = passiveCache.poll();
                part.getRenderedBitmap().recycle();
            }

            while ((activeCache.size() + passiveCache.size()) >= Constants.Cache.CACHE_SIZE &&
                    !activeCache.isEmpty()) {
                activeCache.poll().getRenderedBitmap().recycle();
            }
        }
    }

    public void cacheThumbnail(PagePart part) {
        synchronized (thumbnails) {
            // If cache too big, remove and recycle
            while (thumbnails.size() >= Constants.Cache.THUMBNAILS_CACHE_SIZE) {
                thumbnails.remove(0).getRenderedBitmap().recycle();
            }

            // Then add thumbnail
            addWithoutDuplicates(thumbnails, part);
        }

    }

    public boolean upPartIfContained(int page, RectF pageRelativeBounds, int toOrder) {
        PagePart fakePart = new PagePart(page, null, pageRelativeBounds, false, 0);

        PagePart found;
        synchronized (passiveActiveLock) {
            if ((found = find(passiveCache, fakePart)) != null) {
                passiveCache.remove(found);
                found.setCacheOrder(toOrder);
                activeCache.offer(found);
                return true;
            }

            return find(activeCache, fakePart) != null;
        }
    }

    
    public boolean containsThumbnail(int page, RectF pageRelativeBounds) {
        PagePart fakePart = new PagePart(page, null, pageRelativeBounds, true, 0);
        synchronized (thumbnails) {
            for (PagePart part : thumbnails) {
                if (part.equals(fakePart)) {
                    return true;
                }
            }
            return false;
        }
    }

    
    private void addWithoutDuplicates(Collection<PagePart> collection, PagePart newPart) {
        for (PagePart part : collection) {
            if (part.equals(newPart)) {
                newPart.getRenderedBitmap().recycle();
                return;
            }
        }
        collection.add(newPart);
    }

    @Nullable
    private static PagePart find(PriorityQueue<PagePart> vector, PagePart fakePart) {
        for (PagePart part : vector) {
            if (part.equals(fakePart)) {
                return part;
            }
        }
        return null;
    }

    public List<PagePart> getPageParts() {
        synchronized (passiveActiveLock) {
            List<PagePart> parts = new ArrayList<>(passiveCache);
            parts.addAll(activeCache);
            return parts;
        }
    }

    public List<PagePart> getThumbnails() {
        synchronized (thumbnails) {
            return thumbnails;
        }
    }

    public void recycle() {
        synchronized (passiveActiveLock) {
            for (PagePart part : passiveCache) {
                part.getRenderedBitmap().recycle();
            }
            passiveCache.clear();
            for (PagePart part : activeCache) {
                part.getRenderedBitmap().recycle();
            }
            activeCache.clear();
        }
        synchronized (thumbnails) {
            for (PagePart part : thumbnails) {
                part.getRenderedBitmap().recycle();
            }
            thumbnails.clear();
        }
    }

    class PagePartComparator implements Comparator<PagePart> {
        @Override
        public int compare(PagePart part1, PagePart part2) {
            if (part1.getCacheOrder() == part2.getCacheOrder()) {
                return 0;
            }
            return part1.getCacheOrder() > part2.getCacheOrder() ? 1 : -1;
        }
    }

}
