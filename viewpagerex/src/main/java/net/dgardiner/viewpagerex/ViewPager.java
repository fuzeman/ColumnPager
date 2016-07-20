package net.dgardiner.viewpagerex;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.annotation.CallSuper;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.VelocityTrackerCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.EdgeEffectCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.view.animation.Interpolator;
import android.widget.Scroller;
import net.dgardiner.viewpagerex.adapters.PagerAdapter;
import net.dgardiner.viewpagerex.adapters.PagerFragment;
import net.dgardiner.viewpagerex.core.PagerItem;

import java.util.ArrayList;
import java.util.List;

public class ViewPager extends ViewGroup {
    private static final String TAG = "ViewPager";
    private static final boolean DEBUG = true;

    private static final boolean USE_CACHE = false;

    private static final int DEFAULT_OFFSCREEN_PAGES = 1;
    private static final int MAX_SETTLE_DURATION = 600; // ms

    private static final int MIN_FLING_VELOCITY = 400; // dips

    // Properties
    private PagerAdapter mAdapter;
    private int mColumns = 1;

    private int mCurColumns = 1;
    private int mCurItem;   // Index of currently displayed page.

    private ArrayList<PagerItem> mItems;
    private final PagerItem mTempItem = new PagerItem();

    // Dragging
    private boolean mIsBeingDragged;
    private boolean mIsUnableToDrag;

    private int mDefaultGutterSize;
    private int mGutterSize;
    private int mTouchSlop;

    private boolean mScrollingCacheEnabled;

    private boolean mFirstLayout = true;
    private boolean mNeedCalculatePageOffsets = false;
    private boolean mPopulatePending = true;

    private boolean mCalledSuper;
    private int mDecorChildCount;

    private int mOffscreenPageLimit = DEFAULT_OFFSCREEN_PAGES;

    private boolean mFakeDragging;
    private long mFakeDragBeginTime;

    private EdgeEffectCompat mLeftEdge;
    private EdgeEffectCompat mRightEdge;

    private int mPageMargin;
    private Drawable mMarginDrawable;
    private int mTopPageBounds;
    private int mBottomPageBounds;

    // Scrolling
    private Scroller mScroller;
    private boolean mIsScrollStarted;

    /**
     * Position of the last motion event.
     */
    private float mLastMotionX;
    private float mLastMotionY;
    private float mInitialMotionX;
    private float mInitialMotionY;

    /**
     * ID of the active pointer. This is used to retain consistency during
     * drags/flings if multiple pointers are used.
     */
    private int mActivePointerId = INVALID_POINTER;

    /**
     * Sentinel value for no current active pointer.
     * Used by {@link #mActivePointerId}.
     */
    private static final int INVALID_POINTER = -1;

    /**
     * Determines speed during touch scrolling
     */
    private VelocityTracker mVelocityTracker;
    private int mMinimumVelocity;
    private int mMaximumVelocity;
    private int mFlingDistance;
    private int mCloseEnough;

    // If the pager is at least this close to its final position, complete the scroll
    // on touch down and let the user interact with the content inside instead of
    // "catching" the flinging pager.
    private static final int CLOSE_ENOUGH = 2; // dp

    // Offsets of the first and last items, if known.
    // Set during population, used to determine if we are at the beginning
    // or end of the pager data set during touch scrolling.
    private float mFirstOffset = -Float.MAX_VALUE;
    private float mLastOffset = Float.MAX_VALUE;

    private int mChildWidthMeasureSpec;
    private int mChildHeightMeasureSpec;
    private boolean mInLayout;

    /**
     * Indicates that the pager is in an idle, settled state. The current page
     * is fully in view and no animation is in progress.
     */
    public static final int SCROLL_STATE_IDLE = 0;

    /**
     * Indicates that the pager is currently being dragged by the user.
     */
    public static final int SCROLL_STATE_DRAGGING = 1;

    /**
     * Indicates that the pager is in the process of settling to a final position.
     */
    public static final int SCROLL_STATE_SETTLING = 2;

    private final Runnable mEndScrollRunnable = new Runnable() {
        public void run() {
            setScrollState(SCROLL_STATE_IDLE);
            populate();
        }
    };

    private int mScrollState = SCROLL_STATE_IDLE;

    private List<OnPageChangeListener> mOnPageChangeListeners;
    private OnPageChangeListener mOnPageChangeListener;
    private OnPageChangeListener mInternalPageChangeListener;
    private OnAdapterChangeListener mAdapterChangeListener;
    private PageTransformer mPageTransformer;

    private static final Interpolator sInterpolator = new Interpolator() {
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    private static final int[] LAYOUT_ATTRS = new int[] {
        android.R.attr.layout_gravity
    };

    //
    // Constructor
    //

    public ViewPager(Context context) {
        super(context);
        initialize();
    }

    public ViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public ViewPager(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        final Context context = getContext();

        // Setup view
        setWillNotDraw(false);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setFocusable(true);

        // Construct item list
        mItems = new ArrayList<>();

        // Construct scroller
        mScroller = new Scroller(context, sInterpolator);

        // Construct edge effects
        mLeftEdge = new EdgeEffectCompat(context);
        mRightEdge = new EdgeEffectCompat(context);

        // Set minimum + maximum "fling" velocities
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        final float density = context.getResources().getDisplayMetrics().density;

        mMinimumVelocity = (int) (MIN_FLING_VELOCITY * density);
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();
    }

    //
    // Properties
    //

    public PagerAdapter getAdapter() {
        return mAdapter;
    }

    public void setAdapter(PagerAdapter adapter) {
        mAdapter = adapter;

        if(mAdapter != null) {
            mPopulatePending = false;

            final boolean wasFirstLayout = mFirstLayout;
            mFirstLayout = true;

            if (!wasFirstLayout) {
                populate();
            } else {
                requestLayout();
            }
        }
    }

    public int getColumns() {
        return mColumns;
    }

    public void setColumns(int value) {
        setColumns(value, true);
    }

    public void setColumns(int value, boolean smoothScroll) {
        mColumns = value;

        if(!mFirstLayout) {
            mPopulatePending = true;
            requestLayout();
        }
    }

    private void setScrollState(int newState) {
        if (mScrollState == newState) {
            return;
        }

        mScrollState = newState;

        if (mPageTransformer != null) {
            // PageTransformers can do complex things that benefit from hardware layers.
            enableLayers(newState != SCROLL_STATE_IDLE);
        }

        dispatchOnScrollStateChanged(newState);
    }

    //
    // Public methods
    //

    public PagerItem infoForPosition(int position) {
        for (PagerItem item : mItems) {
            if (item.getIndex() == position) {
                return item;
            }
        }

        return null;
    }

    /**
     * Set the currently selected page. If the ViewPager has already been through its first
     * layout with its current adapter there will be a smooth animated transition between
     * the current item and the specified item.
     *
     * @param item Item index to select
     */
    public void setCurrentItem(int item) {
        mPopulatePending = false;
        setCurrentItemInternal(item, !mFirstLayout, false);
    }

    /**
     * Set the currently selected page.
     *
     * @param item Item index to select
     * @param smoothScroll True to smoothly scroll to the new item, false to transition immediately
     */
    public void setCurrentItem(int item, boolean smoothScroll) {
        mPopulatePending = false;
        setCurrentItemInternal(item, smoothScroll, false);
    }

    public int getCurrentItem() {
        return mCurItem;
    }

    /**
     * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
     *
     * @param x the number of pixels to scroll by on the X axis
     * @param y the number of pixels to scroll by on the Y axis
     */
    public void smoothScrollTo(int x, int y) {
        smoothScrollTo(x, y, 0);
    }

    /**
     * Like {@link View#scrollBy}, but scroll smoothly instead of immediately.
     *
     * @param x the number of pixels to scroll by on the X axis
     * @param y the number of pixels to scroll by on the Y axis
     * @param velocity the velocity associated with a fling, if applicable. (0 otherwise)
     */
    public void smoothScrollTo(int x, int y, int velocity) {
        Log.d(TAG, "smoothScrollTo(" + x + ", " + y + ", " + velocity + ")");

        if (getChildCount() == 0) {
            Log.d(TAG, " - Nothing to do");

            // Nothing to do.
            setScrollingCacheEnabled(false);
            return;
        }

        int sx;
        boolean wasScrolling = (mScroller != null) && !mScroller.isFinished();

        if (wasScrolling) {
            // We're in the middle of a previously initiated scrolling. Check to see
            // whether that scrolling has actually started (if we always call getStartX
            // we can get a stale value from the scroller if it hadn't yet had its first
            // computeScrollOffset call) to decide what is the current scrolling position.
            sx = mIsScrollStarted ? mScroller.getCurrX() : mScroller.getStartX();
            // And abort the current scrolling.
            mScroller.abortAnimation();
            setScrollingCacheEnabled(false);
        } else {
            sx = getScrollX();
        }

        int sy = getScrollY();
        int dx = x - sx;
        int dy = y - sy;

        if (dx == 0 && dy == 0) {
            completeScroll(false);
            populate();
            setScrollState(SCROLL_STATE_IDLE);
            return;
        }

        setScrollingCacheEnabled(true);
        setScrollState(SCROLL_STATE_SETTLING);

        final int width = getClientWidth();
        final int halfWidth = width / 2;
        final float distanceRatio = Math.min(1f, 1.0f * Math.abs(dx) / width);
        final float distance = halfWidth + halfWidth * distanceInfluenceForSnapDuration(distanceRatio);

        int duration;
        velocity = Math.abs(velocity);

        if (velocity > 0) {
            duration = 4 * Math.round(1000 * Math.abs(distance / velocity));
        } else {
            final float pageDelta = (float) Math.abs(dx) / (getPageWidth() + mPageMargin);
            duration = (int) ((pageDelta + 1) * 100);
        }

        duration = Math.min(duration, MAX_SETTLE_DURATION);

        // Reset the "scroll started" flag. It will be flipped to true in all places
        // where we call computeScrollOffset().
        Log.d(TAG, " - Scrolling from (" + sx + ", " + sy + ") to (" + dx + ", " + dy + ") in " + duration + "ms");

        mIsScrollStarted = false;
        mScroller.startScroll(sx, sy, dx, dy, duration);
        ViewCompat.postInvalidateOnAnimation(this);
    }

    //
    // Private methods
    //

    private int getClientWidth() {
        return getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
    }

    private int getPageWidth() {
        return (int)(
            ((float) getClientWidth()) / getColumns()
        );
    }

    private void completeScroll(boolean postEvents) {
        boolean needPopulate = mScrollState == SCROLL_STATE_SETTLING;

        if (needPopulate) {
            // Done with scroll, no longer want to cache view drawing.
            setScrollingCacheEnabled(false);
            boolean wasScrolling = !mScroller.isFinished();

            if (wasScrolling) {
                mScroller.abortAnimation();
                int oldX = getScrollX();
                int oldY = getScrollY();
                int x = mScroller.getCurrX();
                int y = mScroller.getCurrY();

                if (oldX != x || oldY != y) {
                    scrollTo(x, y);
                    if (x != oldX) {
                        pageScrolled(x);
                    }
                }
            }
        }

        mPopulatePending = false;

        for (PagerItem item : mItems) {
            if (item.isScrolling()) {
                needPopulate = true;
                item.setScrolling(false);
            }
        }

        if (needPopulate) {
            if (postEvents) {
                ViewCompat.postOnAnimation(this, mEndScrollRunnable);
            } else {
                mEndScrollRunnable.run();
            }
        }
    }

    private PagerItem createPagerItem(int position, int index) {
        PagerItem item = new PagerItem(position, index);

        // Update attributes
        item.setWidth((int)(((float) getClientWidth()) / 3));

        // Instantiate item fragment
        Object object = mAdapter.instantiateItem(this, position);

        if(object instanceof PagerFragment) {
            ((PagerFragment) object).setItem(item);
        } else {
            Log.w(TAG, " - Unsupported object: " + object);
        }

        item.setObject(object);

        // Add item to `mItems`
        if (index < 0 || index >= mItems.size()) {
            mItems.add(item);
        } else {
            mItems.add(index, item);
        }

        Log.d(TAG, " - Created item: " + item);
        return item;
    }

    // We want the duration of the page snap animation to be influenced by the distance that
    // the screen has to travel, however, we don't want this duration to be effected in a
    // purely linear fashion. Instead, we use this method to moderate the effect that the distance
    // of travel has on the overall snap duration.
    private float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * Math.PI / 2.0f;
        return (float) Math.sin(f);
    }

    private void enableLayers(boolean enable) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final int layerType = enable ?
                    ViewCompat.LAYER_TYPE_HARDWARE : ViewCompat.LAYER_TYPE_NONE;
            ViewCompat.setLayerType(getChildAt(i), layerType, null);
        }
    }

    private boolean isGutterDrag(float x, float dx) {
        return (x < mGutterSize && dx > 0) || (x > getWidth() - mGutterSize && dx < 0);
    }

    private PagerItem infoForCurrentScrollPosition() {
        int lastPos = -1;
        int lastOffset = 0;
        int lastWidth = 0;

        boolean first = true;
        PagerItem lastItem = null;

        for (int i = 0; i < mItems.size(); i++) {
            PagerItem ii = mItems.get(i);
            int offset;

            if (!first && ii.getPosition() != lastPos + 1) {
                // Create a synthetic item for a missing page.
                ii = mTempItem;
                ii.setOffset(lastOffset + lastWidth + mPageMargin);
                ii.setPosition(lastPos + 1);
                ii.setWidth(getPageWidth());
                i--;
            }

            offset = ii.getOffset();

            final float leftBound = offset;
            final float rightBound = offset + ii.getWidth() + mPageMargin;

            if (first || getScrollX() >= leftBound) {
                if (getScrollX() < rightBound || i == mItems.size() - 1) {
                    return ii;
                }
            } else {
                return lastItem;
            }

            first = false;
            lastPos = ii.getPosition();
            lastOffset = offset;
            lastWidth = ii.getWidth();
            lastItem = ii;
        }

        return lastItem;
    }

    private void populate() {
        populate(mCurItem);
    }

    private void populate(int newCurrentItem) {
        PagerItem oldCurInfo = null;

        if (mCurItem != newCurrentItem) {
            oldCurInfo = infoForPosition(mCurItem);
            mCurItem = newCurrentItem;

            Log.d(TAG, " - Current item updated to " + newCurrentItem);
        }

        if(mAdapter == null) {
            return;
        }

        if(mPopulatePending) {
            if (DEBUG) {
                Log.i(TAG, "populate is pending, skipping for now...");
            }
            return;
        }

        Log.d(TAG, "populate(" + newCurrentItem + ")");
        mAdapter.startUpdate(this);

        final int N = mAdapter.getCount();

        // Locate the currently focused item or add it if needed.
        int curIndex = -1;
        PagerItem curItem = null;

        for (curIndex = 0; curIndex < mItems.size(); curIndex++) {
            final PagerItem item = mItems.get(curIndex);

            if (item.getPosition() >= mCurItem) {
                if (item.getPosition() == mCurItem) {
                    curItem = item;
                }
                break;
            }
        }

        if (curItem == null && N > 0) {
            curItem = createPagerItem(mCurItem, curIndex);
        }

        Log.d(TAG, " - curIndex: " + curIndex);
        Log.d(TAG, " - curItem: " + curItem);

        // Create a PagerItem for each item in the adapter
        // TODO limit items to: OFFSCREEN_ITEMS (left) + OFFSCREEN_ITEMS (right) + VISIBLE_ITEMS
        if (curItem != null) {
            int itemIndex = curIndex - 1;
            PagerItem item = itemIndex >= 0 ? mItems.get(itemIndex) : null;

            // Create items before `mCurItem`
            for (int pos = mCurItem - 1; pos >= 0; pos--) {
                if (item != null && pos == item.getPosition()) {
                    itemIndex--;
                    item = itemIndex >= 0 ? mItems.get(itemIndex) : null;
                } else {
                    item = createPagerItem(pos, itemIndex + 1);
                    curIndex++;

                    item = itemIndex >= 0 ? mItems.get(itemIndex) : null;
                }
            }

            // Create items after `mCurItem`
            itemIndex = curIndex + 1;
            item = itemIndex < mItems.size() ? mItems.get(itemIndex) : null;

            for (int pos = mCurItem + 1; pos < N; pos++) {
                if (item != null && pos == item.getPosition()) {
                    itemIndex++;
                    item = itemIndex < mItems.size() ? mItems.get(itemIndex) : null;
                } else {
                    item = createPagerItem(pos, itemIndex);
                    itemIndex++;

                    item = itemIndex < mItems.size() ? mItems.get(itemIndex) : null;
                }
            }

            // Re-calculate page offsets
            calculatePageOffsets(curItem, curIndex, oldCurInfo);
        }

        mAdapter.finishUpdate(this);
    }

    private void calculatePageOffsets(PagerItem curItem, int curIndex, PagerItem oldCurInfo) {
        Log.d(TAG, "calculatePageOffsets(" + curItem + ", " + curIndex + ", " + oldCurInfo + ")");

        final int N = mAdapter.getCount();

        // Fix up offsets for later layout.
        if (oldCurInfo != null) {
            final int oldCurPosition = oldCurInfo.getPosition();

            // Base offsets off of oldCurInfo.
            if (oldCurPosition < curItem.getPosition()) {
                int itemIndex = 0;
                PagerItem item = null;
                int offset = oldCurInfo.getOffset() + mPageMargin;

                for (int pos = oldCurPosition + 1; pos <= curItem.getPosition() && itemIndex < mItems.size(); pos++) {
                    item = mItems.get(itemIndex);

                    while (pos > item.getPosition() && itemIndex < mItems.size() - 1) {
                        itemIndex++;
                        item = mItems.get(itemIndex);
                    }

                    while (pos < item.getPosition()) {
                        // We don't have an item populated for this,
                        // ask the adapter for an offset.
                        offset += getPageWidth() + mPageMargin;
                        pos++;
                    }

                    item.setWidth(getPageWidth());

                    item.setOffset(offset);
                    offset += item.getWidth() + mPageMargin;

                    Log.d(TAG, " - Updated item: " + item);
                }
            } else if (oldCurPosition > curItem.getPosition()) {
                int itemIndex = mItems.size() - 1;
                PagerItem item = null;
                int offset = oldCurInfo.getOffset();

                for (int pos = oldCurPosition - 1; pos >= curItem.getPosition() && itemIndex >= 0; pos--) {
                    item = mItems.get(itemIndex);

                    while (pos < item.getPosition() && itemIndex > 0) {
                        itemIndex--;
                        item = mItems.get(itemIndex);
                    }

                    while (pos > item.getPosition()) {
                        // We don't have an item populated for this,
                        // ask the adapter for an offset.
                        offset -= getPageWidth() + mPageMargin;
                        pos--;
                    }

                    item.setWidth(getPageWidth());

                    offset -= item.getWidth() + mPageMargin;
                    item.setOffset(offset);

                    Log.d(TAG, " - Updated item: " + item);
                }
            }
        }

        // Update current item
        curItem.setOffset(curItem.getIndex() * getPageWidth());
        curItem.setWidth(getPageWidth());

        Log.d(TAG, " - Updated item: " + curItem);

        // Base all offsets off of curItem.
        final int itemCount = mItems.size();
        int offset = curItem.getOffset();
        int pos = curItem.getPosition() - 1;

        // Update first + last offsets
        mFirstOffset = 0;
        mLastOffset = (mAdapter.getCount() - getColumns()) * getPageWidth();

        // Previous pages
        for (int i = curIndex - 1; i >= 0; i--, pos--) {
            final PagerItem item = mItems.get(i);

            while (pos > item.getPosition()) {
                offset -= getPageWidth() + mPageMargin;
            }

            item.setWidth(getPageWidth());

            offset -= item.getWidth() + mPageMargin;
            item.setOffset(offset);

            Log.d(TAG, " - Updated item: " + item);

//            if (item.getPosition() == 0) {
//                mFirstOffset = offset;
//            }
        }

        offset = curItem.getOffset() + curItem.getWidth() + mPageMargin;
        pos = curItem.getPosition() + 1;

        // Next pages
        for (int i = curIndex + 1; i < itemCount; i++, pos++) {
            final PagerItem item = mItems.get(i);

            while (pos < item.getPosition()) {
                offset += getPageWidth() + mPageMargin;
            }

//            if (item.getPosition() == N - 1) {
//                mLastOffset = offset + item.getWidth() - (getPageWidth() * getColumns());
//            }

            item.setWidth(getPageWidth());

            item.setOffset(offset);
            offset += item.getWidth() + mPageMargin;

            Log.d(TAG, " - Updated item: " + item);
        }

        Log.d(TAG, " - mFirstOffset: " + mFirstOffset);
        Log.d(TAG, " - mLastOffset: " + mLastOffset);

        mNeedCalculatePageOffsets = false;
    }

    private boolean resetTouch() {
        boolean needsInvalidate;
        mActivePointerId = INVALID_POINTER;
        endDrag();
        needsInvalidate = mLeftEdge.onRelease() | mRightEdge.onRelease();
        return needsInvalidate;
    }

    private void requestParentDisallowInterceptTouchEvent(boolean disallowIntercept) {
        final ViewParent parent = getParent();

        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    private boolean performDrag(float x) {
        boolean needsInvalidate = false;

        final float deltaX = mLastMotionX - x;
        mLastMotionX = x;

        float oldScrollX = getScrollX();
        float scrollX = oldScrollX + deltaX;
        final int width = getClientWidth();

        float leftBound = mFirstOffset;
        float rightBound = mLastOffset;
        boolean leftAbsolute = true;
        boolean rightAbsolute = true;

        final PagerItem firstItem = mItems.get(0);
        final PagerItem lastItem = mItems.get(mItems.size() - 1);

        if (firstItem.getIndex() != 0) {
            leftAbsolute = false;
            leftBound = firstItem.getOffset();
        }

        if (lastItem.getIndex() != mAdapter.getCount() - 1) {
            rightAbsolute = false;
            rightBound = lastItem.getOffset() - (getPageWidth() * (getColumns() - 1));
        }

        if (scrollX < leftBound) {
            if (leftAbsolute) {
                float over = leftBound - scrollX;
                needsInvalidate = mLeftEdge.onPull(Math.abs(over) / width);
            }
            scrollX = leftBound;
        } else if (scrollX > rightBound) {
            if (rightAbsolute) {
                float over = scrollX - rightBound;
                needsInvalidate = mRightEdge.onPull(Math.abs(over) / width);
            }
            scrollX = rightBound;
        }

        // Don't lose the rounded component
        mLastMotionX += scrollX - (int) scrollX;
        scrollTo((int) scrollX, getScrollY());
        pageScrolled((int) scrollX);

        return needsInvalidate;
    }

    private void endDrag() {
        mIsBeingDragged = false;
        mIsUnableToDrag = false;

        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private void setCurrentItemInternal(int item, boolean smoothScroll, boolean always) {
        setCurrentItemInternal(item, smoothScroll, always, 0);
    }

    private void setCurrentItemInternal(int item, boolean smoothScroll, boolean always, int velocity) {
        if (mAdapter == null || mAdapter.getCount() <= 0) {
            setScrollingCacheEnabled(false);
            return;
        }

        if (!always && mCurItem == item && mItems.size() != 0) {
            setScrollingCacheEnabled(false);
            return;
        }

        if (item < 0) {
            item = 0;
        } else if (item >= mAdapter.getCount()) {
            item = mAdapter.getCount() - 1;
        }

        final int pageLimit = mOffscreenPageLimit;

        if (item > (mCurItem + pageLimit) || item < (mCurItem - pageLimit)) {
            // We are doing a jump by more than one page.  To avoid
            // glitches, we want to keep all current pages in the view
            // until the scroll ends.
            for (PagerItem mItem : mItems) {
                mItem.setScrolling(true);
            }
        }

        final boolean dispatchSelected = mCurItem != item;

        if (mFirstLayout) {
            // We don't have any idea how big we are yet and shouldn't have any pages either.
            // Just set things up and let the pending layout handle things.
            mCurItem = item;

            if (dispatchSelected) {
                dispatchOnPageSelected(item);
            }
            requestLayout();
        } else {
            populate(item);
            scrollToItem(item, smoothScroll, velocity, dispatchSelected);
        }
    }

    private void scrollToItem(int position, boolean smoothScroll) {
        scrollToItem(position, smoothScroll, 0, mCurItem != position);
    }

    private void scrollToItem(int position, boolean smoothScroll, int velocity, boolean dispatchSelected) {
        Log.d(TAG, "scrollToItem(" + position + ", " + smoothScroll + ", " + velocity + ", " + dispatchSelected + ")");

        // Calculate X destination
        final PagerItem curInfo = infoForPosition(position);
        int destX = 0;

        Log.d(TAG, " - mFirstOffset: " + mFirstOffset);
        Log.d(TAG, " - mLastOffset: " +mLastOffset);

        if (curInfo != null) {
            Log.d(TAG, " - curInfo.getOffset(): " + curInfo.getOffset());

            destX = (int) (Math.max(mFirstOffset, Math.min(curInfo.getOffset(), mLastOffset)));
        }

        // Scroll to position
        if (smoothScroll) {
            smoothScrollTo(destX, 0, velocity);

            if (dispatchSelected) {
                dispatchOnPageSelected(position);
            }
        } else {
            if (dispatchSelected) {
                dispatchOnPageSelected(position);
            }

            completeScroll(false);
            scrollTo(destX, 0);
            pageScrolled(destX);
        }
    }

    private void setScrollingCacheEnabled(boolean enabled) {
        if (mScrollingCacheEnabled != enabled) {
            mScrollingCacheEnabled = enabled;

            if (USE_CACHE) {
                final int size = getChildCount();
                for (int i = 0; i < size; ++i) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() != GONE) {
                        child.setDrawingCacheEnabled(enabled);
                    }
                }
            }
        }
    }

    private void recomputeScrollPosition(int width, int oldWidth, int margin, int oldMargin) {
        Log.d(TAG, "recomputeScrollPosition(" + width + ", " + oldWidth + ", " + margin + ", " + oldMargin + ")");

        if (oldWidth > 0 && !mItems.isEmpty()) {
            if (!mScroller.isFinished()) {
                mScroller.setFinalX(getCurrentItem() * getClientWidth());
            } else {
                final int widthWithMargin = width - getPaddingLeft() - getPaddingRight() + margin;
                final int oldWidthWithMargin = oldWidth - getPaddingLeft() - getPaddingRight() + oldMargin;
                final int xpos = getScrollX();

                scrollTo(
                    (int) ((float) xpos / oldWidthWithMargin * widthWithMargin),
                    getScrollY()
                );
            }
        } else {
            final PagerItem item = infoForPosition(mCurItem);
            final int scrollOffset = item != null ? (int) Math.min(item.getOffset(), mLastOffset) : 0;

            if (scrollOffset != getScrollX()) {
                completeScroll(false);
                scrollTo(scrollOffset, getScrollY());
            }
        }
    }

    public boolean canScrollHorizontally(int direction) {
        if (mAdapter == null) {
            return false;
        }

        final int width = getClientWidth();
        final int scrollX = getScrollX();

        if (direction < 0) {
            return (scrollX > (int) (width * mFirstOffset));
        } else if (direction > 0) {
            return (scrollX < (int) (width * mLastOffset));
        } else {
            return false;
        }
    }

    /**
     * Tests scrollability within child views of v given a delta of dx.
     *
     * @param v View to test for horizontal scrollability
     * @param checkV Whether the view v passed should itself be checked for scrollability (true),
     *               or just its children (false).
     * @param dx Delta scrolled in pixels
     * @param x X coordinate of the active touch point
     * @param y Y coordinate of the active touch point
     * @return true if child views of v can be scrolled by delta of dx.
     */
    protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
        if (v instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) v;
            final int scrollX = v.getScrollX();
            final int scrollY = v.getScrollY();
            final int count = group.getChildCount();

            // Count backwards - let topmost views consume scroll distance first.
            for (int i = count - 1; i >= 0; i--) {
                // TODO: Add versioned support here for transformed views.
                // This will not work for transformed views in Honeycomb+
                final View child = group.getChildAt(i);

                if (x + scrollX >= child.getLeft() && x + scrollX < child.getRight() &&
                        y + scrollY >= child.getTop() && y + scrollY < child.getBottom() &&
                        canScroll(child, true, dx, x + scrollX - child.getLeft(),
                                y + scrollY - child.getTop())) {
                    return true;
                }
            }
        }

        return checkV && ViewCompat.canScrollHorizontally(v, -dx);
    }

    private int determineTargetPage(int currentPage, float pageOffset, int velocity, int deltaX) {
        Log.d(TAG, "determineTargetPage(" + currentPage + ", " + pageOffset + ", " + velocity + ", " + deltaX + ")");

        int targetPage;

        if (Math.abs(deltaX) > mFlingDistance && Math.abs(velocity) > mMinimumVelocity) {
            targetPage = velocity > 0 ? currentPage : currentPage + 1;
        } else {
            final float truncator = currentPage >= mCurItem ? 0.4f : 0.6f;
            targetPage = (int) (currentPage + pageOffset + truncator);
        }

        if (mItems.size() > 0) {
            final PagerItem firstItem = mItems.get(0);
            final PagerItem lastItem = mItems.get(mItems.size() - 1);

            // Only let the user target pages we have items for
            targetPage = Math.max(firstItem.getPosition(), Math.min(targetPage, lastItem.getPosition()));
        }

        Log.d(TAG, " - targetPage: " + targetPage);
        return targetPage;
    }

    private boolean pageScrolled(int xpos) {
        if (mItems.size() == 0) {
            mCalledSuper = false;
            onPageScrolled(0, 0);

            if (!mCalledSuper) {
                throw new IllegalStateException("onPageScrolled did not call superclass implementation");
            }

            return false;
        }

        final PagerItem item = infoForCurrentScrollPosition();

        mCalledSuper = false;
        onPageScrolled(item.getPosition(), xpos - item.getOffset());

        if (!mCalledSuper) {
            throw new IllegalStateException("onPageScrolled did not call superclass implementation");
        }

        return true;
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = MotionEventCompat.getActionIndex(ev);
        final int pointerId = MotionEventCompat.getPointerId(ev, pointerIndex);

        if (pointerId == mActivePointerId) {
            // This was our active pointer going up. Choose a new
            // active pointer and adjust accordingly.
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mLastMotionX = MotionEventCompat.getX(ev, newPointerIndex);
            mActivePointerId = MotionEventCompat.getPointerId(ev, newPointerIndex);

            if (mVelocityTracker != null) {
                mVelocityTracker.clear();
            }
        }
    }

    /**
     * This method will be invoked when the current page is scrolled, either as part
     * of a programmatically initiated smooth scroll or a user initiated touch scroll.
     * If you override this method you must call through to the superclass implementation
     * (e.g. super.onPageScrolled(position, offset, offsetPixels)) before onPageScrolled
     * returns.
     *
     * @param position Position index of the first page currently being displayed.
     *                 Page position+1 will be visible if positionOffset is nonzero.
     * @param offset Value in pixels indicating the offset from position.
     */
    @CallSuper
    protected void onPageScrolled(int position, int offset) {
        // Offset any decor views if needed - keep them on-screen at all times.
        if (mDecorChildCount > 0) {
            final int scrollX = getScrollX();
            int paddingLeft = getPaddingLeft();
            int paddingRight = getPaddingRight();
            final int width = getWidth();
            final int childCount = getChildCount();

            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                final android.support.v4.view.ViewPager.LayoutParams lp = (android.support.v4.view.ViewPager.LayoutParams) child.getLayoutParams();
                if (!lp.isDecor) continue;

                final int hgrav = lp.gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                int childLeft = 0;

                switch (hgrav) {
                    default:
                        childLeft = paddingLeft;
                        break;
                    case Gravity.LEFT:
                        childLeft = paddingLeft;
                        paddingLeft += child.getWidth();
                        break;
                    case Gravity.CENTER_HORIZONTAL:
                        childLeft = Math.max((width - child.getMeasuredWidth()) / 2,
                                paddingLeft);
                        break;
                    case Gravity.RIGHT:
                        childLeft = width - paddingRight - child.getMeasuredWidth();
                        paddingRight += child.getMeasuredWidth();
                        break;
                }

                childLeft += scrollX;

                final int childOffset = childLeft - child.getLeft();

                if (childOffset != 0) {
                    child.offsetLeftAndRight(childOffset);
                }
            }
        }

        dispatchOnPageScrolled(position, offset);

        if (mPageTransformer != null) {
            final int scrollX = getScrollX();
            final int childCount = getChildCount();

            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);

                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                if (lp.isDecor) {
                    continue;
                }

                final float transformPos = (float) (child.getLeft() - scrollX) / getClientWidth();
                mPageTransformer.transformPage(child, transformPos);
            }
        }

        mCalledSuper = true;
    }

    private void dispatchOnPageScrolled(int position, int offset) {
        if (mOnPageChangeListener != null) {
            mOnPageChangeListener.onPageScrolled(position, offset);
        }

        if (mOnPageChangeListeners != null) {
            for (OnPageChangeListener listener : mOnPageChangeListeners) {
                if (listener != null) {
                    listener.onPageScrolled(position, offset);
                }
            }
        }

        if (mInternalPageChangeListener != null) {
            mInternalPageChangeListener.onPageScrolled(position, offset);
        }
    }

    private void dispatchOnPageSelected(int position) {
        if (mOnPageChangeListener != null) {
            mOnPageChangeListener.onPageSelected(position);
        }

        if (mOnPageChangeListeners != null) {
            for (OnPageChangeListener listener : mOnPageChangeListeners) {
                if (listener != null) {
                    listener.onPageSelected(position);
                }
            }
        }

        if (mInternalPageChangeListener != null) {
            mInternalPageChangeListener.onPageSelected(position);
        }
    }

    private void dispatchOnScrollStateChanged(int state) {
        if (mOnPageChangeListener != null) {
            mOnPageChangeListener.onPageScrollStateChanged(state);
        }

        if (mOnPageChangeListeners != null) {
            for (OnPageChangeListener listener : mOnPageChangeListeners) {
                if (listener != null) {
                    listener.onPageScrollStateChanged(state);
                }
            }
        }

        if (mInternalPageChangeListener != null) {
            mInternalPageChangeListener.onPageScrollStateChanged(state);
        }
    }

    //
    // ViewGroup
    //

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (!checkLayoutParams(params)) {
            params = generateLayoutParams(params);
        }

        final LayoutParams lp = (LayoutParams) params;
        lp.needsMeasure = true;

        super.addView(child, index, params);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams && super.checkLayoutParams(p);
    }

    @Override
    public void computeScroll() {
        mIsScrollStarted = true;

        if (!mScroller.isFinished() && mScroller.computeScrollOffset()) {
            int oldX = getScrollX();
            int oldY = getScrollY();
            int x = mScroller.getCurrX();
            int y = mScroller.getCurrY();

            if (oldX != x || oldY != y) {
                scrollTo(x, y);

                if (!pageScrolled(x)) {
                    mScroller.abortAnimation();
                    scrollTo(0, y);
                }
            }

            // Keep on drawing until the animation has finished.
            ViewCompat.postInvalidateOnAnimation(this);
            return;
        }

        // Done with scroll, clean up state.
        completeScroll(true);
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        boolean needsInvalidate = false;
        final int overScrollMode = ViewCompat.getOverScrollMode(this);

        if (overScrollMode == ViewCompat.OVER_SCROLL_ALWAYS || (
            overScrollMode == ViewCompat.OVER_SCROLL_IF_CONTENT_SCROLLS &&
            mAdapter != null && mAdapter.getCount() > 1
        )) {
            if (!mLeftEdge.isFinished()) {
                final int restoreCount = canvas.save();
                final int width = getWidth();
                final int height = getHeight() - getPaddingTop() - getPaddingBottom();

                canvas.rotate(270);
                canvas.translate(-height + getPaddingTop(), mFirstOffset);
                mLeftEdge.setSize(height, width);
                needsInvalidate |= mLeftEdge.draw(canvas);
                canvas.restoreToCount(restoreCount);
            }

            if (!mRightEdge.isFinished()) {
                final int restoreCount = canvas.save();
                final int width = getWidth();
                final int height = getHeight() - getPaddingTop() - getPaddingBottom();

                canvas.rotate(90);
                canvas.translate(-getPaddingTop(), -(mLastOffset + (getPageWidth() * getColumns())));
                mRightEdge.setSize(height, width);
                needsInvalidate |= mRightEdge.draw(canvas);
                canvas.restoreToCount(restoreCount);
            }
        } else {
            mLeftEdge.finish();
            mRightEdge.finish();
        }

        if (needsInvalidate) {
            // Keep animating
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return generateDefaultLayoutParams();
    }

    @Override
    protected void onDraw(Canvas canvas) {
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        Log.d(TAG, "onLayout(" + changed + ", " + l + ", " + t + ", " + r + ", " + b + ")");

        int width = r - l;
        int height = b - t;

        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int paddingRight = getPaddingRight();
        int paddingBottom = getPaddingBottom();

        if(mPopulatePending) {
            mPopulatePending = false;
            populate();
        }

        // Update children
        for(int i = 0; i < getChildCount(); ++i) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            // Retrieve pager fragment
            final PagerFragment fragment;

            if(child.getTag() != null && child.getTag() instanceof PagerFragment) {
                fragment = (PagerFragment) child.getTag();
            } else {
                Log.w(TAG, " - Unsupported child tag: " + child.getTag());
                continue;
            }

            // Retrieve pager item
            final PagerItem item;

            if(fragment.getItem() != null) {
                item = fragment.getItem();
            } else {
                Log.w(TAG, " - Unsupported fragment item: " + fragment.getItem());
                continue;
            }

            int childLeft = paddingLeft + item.getOffset();
            int childTop = paddingTop;

            if(lp.needsMeasure) {
                final int widthSpec = MeasureSpec.makeMeasureSpec(
                    item.getWidth(),
                    MeasureSpec.EXACTLY
                );

                final int heightSpec = MeasureSpec.makeMeasureSpec(
                    height - paddingTop - paddingBottom,
                    MeasureSpec.EXACTLY
                );

                if (DEBUG) {
                    Log.v(TAG, " - Measuring #" + i + " " + child);
                }

                child.measure(widthSpec, heightSpec);
                lp.needsMeasure = false;
            }

            if (DEBUG) {
                Log.v(TAG, " - Positioning #" + i + " (" + item.getIndex() + ") " + child + " f=" + item.getObject()
                        + " - (" + childLeft + "," + childTop + ") "
                        + child.getMeasuredWidth() + "x" + child.getMeasuredHeight());
            }

            child.layout(
                childLeft, childTop,
                childLeft + child.getMeasuredWidth(),
                childTop + child.getMeasuredHeight()
            );
        }

        // Update scroll position
        if (mFirstLayout || mColumns != mCurColumns) {
            Log.d(TAG, " - Scrolling to current item (" + mCurItem + ")");

            scrollToItem(mCurItem, false, 0, false);
        }

        mCurColumns = mColumns;
        mFirstLayout = false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Log.d(TAG, "onMeasure(" + widthMeasureSpec + ", " + heightMeasureSpec + ")");

        // For simple implementation, our internal size is always 0.
        // We depend on the container to specify the layout size of
        // our view.  We can't really know what it is since we will be
        // adding and removing different arbitrary views and do not
        // want the layout to change as this happens.
        setMeasuredDimension(
            getDefaultSize(0, widthMeasureSpec),
            getDefaultSize(0, heightMeasureSpec)
        );

        // Children are just made to fill our space.
        int childWidthSize = getPageWidth();
        int childHeightSize = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();

        for(int i = 0; i < getChildCount(); ++i) {
            final View child = getChildAt(i);
            final PagerItem item = mItems.get(i);

            if(item.getWidth() == 0) {
                item.setWidth((int) (((float) getClientWidth()) / 3));
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            if(lp == null) {
                continue;
            }

            final int hgrav = lp.gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
            final int vgrav = lp.gravity & Gravity.VERTICAL_GRAVITY_MASK;

            int widthMode = MeasureSpec.AT_MOST;
            int heightMode = MeasureSpec.AT_MOST;

            boolean consumeVertical = vgrav == Gravity.TOP || vgrav == Gravity.BOTTOM;
            boolean consumeHorizontal = hgrav == Gravity.LEFT || hgrav == Gravity.RIGHT;

            if (consumeVertical) {
                widthMode = MeasureSpec.EXACTLY;
            } else if (consumeHorizontal) {
                heightMode = MeasureSpec.EXACTLY;
            }

            int widthSize = childWidthSize;
            int heightSize = childHeightSize;

            if (lp.width != LayoutParams.WRAP_CONTENT) {
                widthMode = MeasureSpec.EXACTLY;

                if (lp.width != LayoutParams.FILL_PARENT) {
                    widthSize = lp.width;
                }
            }

            if (lp.height != LayoutParams.WRAP_CONTENT) {
                heightMode = MeasureSpec.EXACTLY;

                if (lp.height != LayoutParams.FILL_PARENT) {
                    heightSize = lp.height;
                }
            }

            final int widthSpec = MeasureSpec.makeMeasureSpec(widthSize, widthMode);
            final int heightSpec = MeasureSpec.makeMeasureSpec(heightSize, heightMode);

            if (DEBUG) {
                Log.v(TAG, " - Measuring #" + i + " " + child);
            }

            child.measure(widthSpec, heightSpec);
            lp.needsMeasure = false;
        }

        // Make sure we have created all fragments that we need to have shown.
        mInLayout = true;
        populate();
        mInLayout = false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        /*
         * This method JUST determines whether we want to intercept the motion.
         * If we return true, onMotionEvent will be called and we do the actual
         * scrolling there.
         */

        final int action = ev.getAction() & MotionEventCompat.ACTION_MASK;

        // Always take care of the touch gesture being complete.
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            // Release the drag.
            if (DEBUG) Log.v(TAG, "Intercept done!");
            resetTouch();
            return false;
        }

        // Nothing more to do here if we have decided whether or not we
        // are dragging.
        if (action != MotionEvent.ACTION_DOWN) {
            if (mIsBeingDragged) {
                if (DEBUG) Log.v(TAG, "Intercept returning true!");
                return true;
            }
            if (mIsUnableToDrag) {
                if (DEBUG) Log.v(TAG, "Intercept returning false!");
                return false;
            }
        }

        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                /*
                 * mIsBeingDragged == false, otherwise the shortcut would have caught it. Check
                 * whether the user has moved far enough from his original down touch.
                 */

                /*
                * Locally do absolute value. mLastMotionY is set to the y value
                * of the down event.
                */
                final int activePointerId = mActivePointerId;
                if (activePointerId == INVALID_POINTER) {
                    // If we don't have a valid id, the touch down wasn't on content.
                    break;
                }

                final int pointerIndex = MotionEventCompat.findPointerIndex(ev, activePointerId);
                final float x = MotionEventCompat.getX(ev, pointerIndex);
                final float dx = x - mLastMotionX;
                final float xDiff = Math.abs(dx);
                final float y = MotionEventCompat.getY(ev, pointerIndex);
                final float yDiff = Math.abs(y - mInitialMotionY);
                if (DEBUG) Log.v(TAG, "Moved x to " + x + "," + y + " diff=" + xDiff + "," + yDiff);

                if (dx != 0 && !isGutterDrag(mLastMotionX, dx) && canScroll(this, false, (int) dx, (int) x, (int) y)) {
                    // Nested view has scrollable area under this point. Let it be handled there.
                    mLastMotionX = x;
                    mLastMotionY = y;
                    mIsUnableToDrag = true;
                    return false;
                }
                if (xDiff > mTouchSlop && xDiff * 0.5f > yDiff) {
                    if (DEBUG) Log.v(TAG, "Starting drag!");
                    mIsBeingDragged = true;
                    requestParentDisallowInterceptTouchEvent(true);
                    setScrollState(SCROLL_STATE_DRAGGING);
                    mLastMotionX = dx > 0 ? mInitialMotionX + mTouchSlop :
                            mInitialMotionX - mTouchSlop;
                    mLastMotionY = y;
                    setScrollingCacheEnabled(true);
                } else if (yDiff > mTouchSlop) {
                    // The finger has moved enough in the vertical
                    // direction to be counted as a drag...  abort
                    // any attempt to drag horizontally, to work correctly
                    // with children that have scrolling containers.
                    if (DEBUG) Log.v(TAG, "Starting unable to drag!");
                    mIsUnableToDrag = true;
                }
                if (mIsBeingDragged) {
                    // Scroll to follow the motion event
                    if (performDrag(x)) {
                        ViewCompat.postInvalidateOnAnimation(this);
                    }
                }
                break;
            }

            case MotionEvent.ACTION_DOWN: {
                /*
                 * Remember location of down touch.
                 * ACTION_DOWN always refers to pointer index 0.
                 */
                mLastMotionX = mInitialMotionX = ev.getX();
                mLastMotionY = mInitialMotionY = ev.getY();
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                mIsUnableToDrag = false;

                mIsScrollStarted = true;
                mScroller.computeScrollOffset();
                if (mScrollState == SCROLL_STATE_SETTLING &&
                        Math.abs(mScroller.getFinalX() - mScroller.getCurrX()) > mCloseEnough) {
                    // Let the user 'catch' the pager as it animates.
                    mScroller.abortAnimation();
                    mPopulatePending = false;
                    populate();
                    mIsBeingDragged = true;
                    requestParentDisallowInterceptTouchEvent(true);
                    setScrollState(SCROLL_STATE_DRAGGING);
                } else {
                    completeScroll(false);
                    mIsBeingDragged = false;
                }

                if (DEBUG) Log.v(TAG, "Down at " + mLastMotionX + "," + mLastMotionY
                        + " mIsBeingDragged=" + mIsBeingDragged
                        + "mIsUnableToDrag=" + mIsUnableToDrag);
                break;
            }

            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;
        }

        if (mVelocityTracker == null) {
            Log.d(TAG, " - Obtaining velocity tracker instance");
            mVelocityTracker = VelocityTracker.obtain();
        }

        Log.d(TAG, " - Calculating velocity for: " + ev);
        mVelocityTracker.addMovement(ev);

        /*
         * The only time we want to intercept motion events is if we are in the
         * drag mode.
         */
        return mIsBeingDragged;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Make sure scroll position is set correctly.
        if (w != oldw) {
            recomputeScrollPosition(w, oldw, mPageMargin, mPageMargin);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mFakeDragging) {
            // A fake drag is in progress already, ignore this real one
            // but still eat the touch events.
            // (It is likely that the user is multi-touching the screen.)
            return true;
        }

        if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getEdgeFlags() != 0) {
            // Don't handle edge touches immediately -- they may actually belong to one of our
            // descendants.
            return false;
        }

        if (mAdapter == null || mAdapter.getCount() == 0) {
            // Nothing to present or scroll; nothing to touch.
            return false;
        }

        if (mVelocityTracker == null) {
            Log.d(TAG, " - Obtaining velocity tracker instance");
            mVelocityTracker = VelocityTracker.obtain();
        }

        Log.d(TAG, " - Calculating velocity for: " + ev);
        mVelocityTracker.addMovement(ev);

        final int action = ev.getAction();
        boolean needsInvalidate = false;

        switch (action & MotionEventCompat.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN: {
                mScroller.abortAnimation();
                mPopulatePending = false;
                populate();

                // Remember where the motion event started
                mLastMotionX = mInitialMotionX = ev.getX();
                mLastMotionY = mInitialMotionY = ev.getY();
                mActivePointerId = MotionEventCompat.getPointerId(ev, 0);
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (!mIsBeingDragged) {
                    final int pointerIndex = MotionEventCompat.findPointerIndex(ev, mActivePointerId);
                    if (pointerIndex == -1) {
                        // A child has consumed some touch events and put us into an inconsistent state.
                        needsInvalidate = resetTouch();
                        break;
                    }
                    final float x = MotionEventCompat.getX(ev, pointerIndex);
                    final float xDiff = Math.abs(x - mLastMotionX);
                    final float y = MotionEventCompat.getY(ev, pointerIndex);
                    final float yDiff = Math.abs(y - mLastMotionY);
                    if (DEBUG) Log.v(TAG, "Moved x to " + x + "," + y + " diff=" + xDiff + "," + yDiff);
                    if (xDiff > mTouchSlop && xDiff > yDiff) {
                        if (DEBUG) Log.v(TAG, "Starting drag!");
                        mIsBeingDragged = true;
                        requestParentDisallowInterceptTouchEvent(true);
                        mLastMotionX = x - mInitialMotionX > 0 ? mInitialMotionX + mTouchSlop :
                                mInitialMotionX - mTouchSlop;
                        mLastMotionY = y;
                        setScrollState(SCROLL_STATE_DRAGGING);
                        setScrollingCacheEnabled(true);

                        // Disallow Parent Intercept, just in case
                        ViewParent parent = getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                    }
                }
                // Not else! Note that mIsBeingDragged can be set above.
                if (mIsBeingDragged) {
                    // Scroll to follow the motion event
                    final int activePointerIndex = MotionEventCompat.findPointerIndex(
                            ev, mActivePointerId);
                    final float x = MotionEventCompat.getX(ev, activePointerIndex);
                    needsInvalidate |= performDrag(x);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mIsBeingDragged) {
                    mPopulatePending = true;

                    // Calculate velocity
                    Log.d(TAG, " - Calculating velocity for pointer " + mActivePointerId);
                    mVelocityTracker.computeCurrentVelocity(1000, mMaximumVelocity);

                    float initialVelocity = VelocityTrackerCompat.getXVelocity(mVelocityTracker, mActivePointerId);
                    Log.d(TAG, " - initialVelocity: " + initialVelocity);

                    // Retrieve current item
                    final PagerItem item = infoForCurrentScrollPosition();

                    // Retrieve pointer X position
                    final float x = MotionEventCompat.getX(ev, MotionEventCompat.findPointerIndex(ev, mActivePointerId));

                    // Determine target page
                    int nextPage = determineTargetPage(
                        item.getPosition(),
                        ((float)(getScrollX() - item.getOffset())) / getPageWidth(),
                        (int) initialVelocity,
                        (int) (x - mInitialMotionX)
                    );

                    // Update current item
                    setCurrentItemInternal(
                        nextPage, true, true,
                        (int) initialVelocity
                    );

                    // Reset touch state
                    needsInvalidate = resetTouch();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mIsBeingDragged) {
                    scrollToItem(mCurItem, true, 0, false);
                    needsInvalidate = resetTouch();
                }
                break;
            case MotionEventCompat.ACTION_POINTER_DOWN: {
                final int index = MotionEventCompat.getActionIndex(ev);

                mLastMotionX = MotionEventCompat.getX(ev, index);
                mActivePointerId = MotionEventCompat.getPointerId(ev, index);
                break;
            }
            case MotionEventCompat.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);

                mLastMotionX = MotionEventCompat.getX(ev, MotionEventCompat.findPointerIndex(ev, mActivePointerId));
                break;
        }

        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }

        return true;
    }

    public static class LayoutParams extends ViewGroup.LayoutParams {
        public int gravity;

        public boolean isDecor;
        public boolean needsMeasure;

        public LayoutParams() {
            super(MATCH_PARENT, MATCH_PARENT);
        }

        public LayoutParams(Context context, AttributeSet attrs) {
            super(context, attrs);

            final TypedArray a = context.obtainStyledAttributes(attrs, LAYOUT_ATTRS);
            gravity = a.getInteger(0, Gravity.TOP);
            a.recycle();
        }

        @Override
        public String toString() {
            return "<LayoutParams gravity: " + gravity + ", needsMeasure: " + needsMeasure + ">";
        }
    }

    /**
     * Callback interface for responding to changing state of the selected page.
     */
    public interface OnPageChangeListener {

        /**
         * This method will be invoked when the current page is scrolled, either as part
         * of a programmatically initiated smooth scroll or a user initiated touch scroll.
         *
         * @param position Position index of the first page currently being displayed.
         *                 Page position+1 will be visible if positionOffset is nonzero.
         * @param positionOffset Value in pixels indicating the offset from position.
         */
        void onPageScrolled(int position, float positionOffset);

        /**
         * This method will be invoked when a new page becomes selected. Animation is not
         * necessarily complete.
         *
         * @param position Position index of the new selected page.
         */
        void onPageSelected(int position);

        /**
         * Called when the scroll state changes. Useful for discovering when the user
         * begins dragging, when the pager is automatically settling to the current page,
         * or when it is fully stopped/idle.
         *
         * @param state The new scroll state.
         * @see android.support.v4.view.ViewPager#SCROLL_STATE_IDLE
         * @see android.support.v4.view.ViewPager#SCROLL_STATE_DRAGGING
         * @see android.support.v4.view.ViewPager#SCROLL_STATE_SETTLING
         */
        void onPageScrollStateChanged(int state);
    }

    /**
     * A PageTransformer is invoked whenever a visible/attached page is scrolled.
     * This offers an opportunity for the application to apply a custom transformation
     * to the page views using animation properties.
     *
     * <p>As property animation is only supported as of Android 3.0 and forward,
     * setting a PageTransformer on a ViewPager on earlier platform versions will
     * be ignored.</p>
     */
    public interface PageTransformer {
        /**
         * Apply a property transformation to the given page.
         *
         * @param page Apply the transformation to this page
         * @param position Position of page relative to the current front-and-center
         *                 position of the pager. 0 is front and center. 1 is one full
         *                 page position to the right, and -1 is one page position to the left.
         */
        public void transformPage(View page, float position);
    }

    /**
     * Used internally to monitor when adapters are switched.
     */
    interface OnAdapterChangeListener {
        void onAdapterChanged(PagerAdapter oldAdapter, PagerAdapter newAdapter);
    }
}
