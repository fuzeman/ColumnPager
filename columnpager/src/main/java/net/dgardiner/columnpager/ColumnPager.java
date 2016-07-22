package net.dgardiner.columnpager;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.CallSuper;
import android.support.annotation.DrawableRes;
import android.support.v4.os.ParcelableCompat;
import android.support.v4.os.ParcelableCompatCreatorCallbacks;
import android.support.v4.view.*;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.support.v4.widget.EdgeEffectCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.Interpolator;
import android.widget.Scroller;
import net.dgardiner.columnpager.adapters.PagerAdapter;
import net.dgardiner.columnpager.adapters.PagerFragment;
import net.dgardiner.columnpager.core.PagerItem;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Layout manager that displays a specified number of pages on the screen, and allows the user to flip
 * left and right through extra pages. You supply an implementation of a
 * {@link net.dgardiner.columnpager.adapters.PagerAdapter} to generate the pages that the view shows.
 *
 * <p><b>Note:</b> This view is a heavily modified version of {@link android.support.v4.view.ViewPager},
 * with many breaking changes to provide the best support for displaying multiple pages. We recommend
 * using {@link android.support.v4.view.ViewPager} if you only need to display one page on the screen.</p>
 *
 * <p>Note this class is currently under early design and
 * development.  The API will likely change in later updates of
 * the compatibility library, requiring changes to the source code
 * of apps when they are compiled against the newer version.</p>
 *
 * <p>ColumnPager is most often used in conjunction with {@link android.app.Fragment},
 * which is a convenient way to supply and manage the lifecycle of each page.
 * There are standard adapters implemented for using fragments with the ColumnPager,
 * which cover the most common use cases.  These are
 * {@link net.dgardiner.columnpager.adapters.FragmentPagerAdapter} and
 * {@link net.dgardiner.columnpager.adapters.FragmentStatePagerAdapter}; each of these
 * classes have simple code showing how to build a full user interface
 * with them.
 */
public class ColumnPager extends ViewGroup {
    // region Static variables
    private static final String TAG = "ColumnPager";
    private static final boolean DEBUG = true;

    private static final boolean USE_CACHE = false;

    private static final int DEFAULT_OFFSCREEN_PAGES = 1;
    private static final int MAX_SETTLE_DURATION = 600; // ms
    private static final int MIN_DISTANCE_FOR_FLING = 25; // dips

    private static final int DEFAULT_GUTTER_SIZE = 16; // dips

    private static final int MIN_FLING_VELOCITY = 400; // dips

    private static final int[] LAYOUT_ATTRS = new int[] {
        android.R.attr.layout_gravity
    };

    private static final ViewPositionComparator sPositionComparator = new ViewPositionComparator();

    private static final Comparator<PagerItem> COMPARATOR = new Comparator<PagerItem>(){
        @Override
        public int compare(PagerItem lhs, PagerItem rhs) {
            return lhs.getPosition() - rhs.getPosition();
        }
    };

    private static final Interpolator sInterpolator = new Interpolator() {
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    /**
     * Sentinel value for no current active pointer.
     * Used by {@link #mActivePointerId}.
     */
    private static final int INVALID_POINTER = -1;

    // If the pager is at least this close to its final position, complete the scroll
    // on touch down and let the user interact with the content inside instead of
    // "catching" the flinging pager.
    private static final int CLOSE_ENOUGH = 2; // dp

    private static final int DRAW_ORDER_DEFAULT = 0;
    private static final int DRAW_ORDER_FORWARD = 1;
    private static final int DRAW_ORDER_REVERSE = 2;

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

    public static final int POSITION_UNCHANGED = -1;
    public static final int POSITION_NONE = -2;

    // endregion

    // region Class variables

    /**
     * Used to track what the expected number of items in the adapter should be.
     * If the app changes this when we don't expect it, we'll throw a big obnoxious exception.
     */
    private int mExpectedAdapterCount;

    private final ArrayList<PagerItem> mItems = new ArrayList<PagerItem>();
    private final PagerItem mTempItem = new PagerItem();

    private final Rect mTempRect = new Rect();

    private PagerAdapter mAdapter;
    private int mCurItem;   // Index of currently displayed page.
    private int mRestoredCurItem = -1;
    private Parcelable mRestoredAdapterState = null;
    private ClassLoader mRestoredClassLoader = null;

    private int mColumns = 1;
    private int mCurColumns = 1;

    private Scroller mScroller;
    private boolean mIsScrollStarted;

    private PagerObserver mObserver;

    private int mPageMargin;
    private Drawable mMarginDrawable;
    private int mTopPageBounds;
    private int mBottomPageBounds;

    // Offsets of the first and last items, if known.
    // Set during population, used to determine if we are at the beginning
    // or end of the pager data set during touch scrolling.
    private float mFirstOffset = -Float.MAX_VALUE;
    private float mLastOffset = Float.MAX_VALUE;

    private int mChildWidthMeasureSpec;
    private int mChildHeightMeasureSpec;
    private boolean mInLayout;

    private boolean mScrollingCacheEnabled;

    private boolean mPopulatePending;
    private int mOffscreenPageLimit = DEFAULT_OFFSCREEN_PAGES;

    private boolean mIsBeingDragged;
    private boolean mIsUnableToDrag;
    private int mDefaultGutterSize;
    private int mGutterSize;
    private int mTouchSlop;
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
     * Determines speed during touch scrolling
     */
    private VelocityTracker mVelocityTracker;
    private int mMinimumVelocity;
    private int mMaximumVelocity;
    private int mFlingDistance;
    private int mCloseEnough;

    private boolean mFakeDragging;
    private long mFakeDragBeginTime;

    private EdgeEffectCompat mLeftEdge;
    private EdgeEffectCompat mRightEdge;

    private boolean mFirstLayout = true;
    private boolean mNeedCalculatePageOffsets = false;
    private boolean mCalledSuper;
    private int mDecorChildCount;

    private List<OnPageChangeListener> mOnPageChangeListeners;
    private OnPageChangeListener mOnPageChangeListener;
    private OnPageChangeListener mInternalPageChangeListener;
    private OnAdapterChangeListener mAdapterChangeListener;
    private PageTransformer mPageTransformer;
    private Method mSetChildrenDrawingOrderEnabled;

    private int mDrawingOrder;
    private ArrayList<View> mDrawingOrderedChildren;

    private final Runnable mEndScrollRunnable = new Runnable() {
        public void run() {
            setScrollState(SCROLL_STATE_IDLE);
            populate();
        }
    };

    private int mScrollState = SCROLL_STATE_IDLE;

    // endregion

    // region Constructor

    public ColumnPager(Context context) {
        super(context);
        initialize();
    }

    public ColumnPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public ColumnPager(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        final Context context = getContext();
        final ViewConfiguration configuration = ViewConfiguration.get(context);

        // Setup view
        setWillNotDraw(false);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setFocusable(true);

        // Construct scroller
        mScroller = new Scroller(context, sInterpolator);

        // Construct edge effects
        mLeftEdge = new EdgeEffectCompat(context);
        mRightEdge = new EdgeEffectCompat(context);

        // Retrieve touch "slop"
        mTouchSlop = ViewConfigurationCompat.getScaledPagingTouchSlop(configuration);

        // Set minimum + maximum "fling" velocities
        final float density = context.getResources().getDisplayMetrics().density;

        mMinimumVelocity = (int) (MIN_FLING_VELOCITY * density);
        mMaximumVelocity = configuration.getScaledMaximumFlingVelocity();

        // Set scaled attributes
        mFlingDistance = (int) (MIN_DISTANCE_FOR_FLING * density);
        mCloseEnough = (int) (CLOSE_ENOUGH * density);
        mDefaultGutterSize = (int) (DEFAULT_GUTTER_SIZE * density);

        // Setup view accessibility
        ViewCompat.setAccessibilityDelegate(this, new ColumnPagerAccessibilityDelegate());

        if (ViewCompat.getImportantForAccessibility(this) == ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            ViewCompat.setImportantForAccessibility(
                this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES
            );
        }

        // Setup window insets handler
        ViewCompat.setOnApplyWindowInsetsListener(this,
                new android.support.v4.view.OnApplyWindowInsetsListener() {
                    private final Rect mTempRect = new Rect();

                    @Override
                    public WindowInsetsCompat onApplyWindowInsets(final View v,
                            final WindowInsetsCompat originalInsets) {

                        // First let the ColumnPager itself try and consume them...
                        final WindowInsetsCompat applied = ViewCompat.onApplyWindowInsets(v, originalInsets);

                        if (applied.isConsumed()) {
                            // If the ColumnPager consumed all insets, return now
                            return applied;
                        }

                        // Now we'll manually dispatch the insets to our children. Since ColumnPager
                        // children are always full-height, we do not want to use the standard
                        // ViewGroup dispatchApplyWindowInsets since if child 0 consumes them,
                        // the rest of the children will not receive any insets. To workaround this
                        // we manually dispatch the applied insets, not allowing children to
                        // consume them from each other. We do however keep track of any insets
                        // which are consumed, returning the union of our children's consumption
                        final Rect res = mTempRect;
                        res.left = applied.getSystemWindowInsetLeft();
                        res.top = applied.getSystemWindowInsetTop();
                        res.right = applied.getSystemWindowInsetRight();
                        res.bottom = applied.getSystemWindowInsetBottom();

                        for (int i = 0, count = getChildCount(); i < count; i++) {
                            final WindowInsetsCompat childInsets = ViewCompat.dispatchApplyWindowInsets(
                                getChildAt(i), applied
                            );

                            // Now keep track of any consumed by tracking each dimension's min value
                            res.left = Math.min(childInsets.getSystemWindowInsetLeft(), res.left);
                            res.top = Math.min(childInsets.getSystemWindowInsetTop(), res.top);
                            res.right = Math.min(childInsets.getSystemWindowInsetRight(), res.right);
                            res.bottom = Math.min(childInsets.getSystemWindowInsetBottom(), res.bottom);
                        }

                        // Now return a new WindowInsets, using the consumed window insets
                        return applied.replaceSystemWindowInsets(
                            res.left, res.top,
                            res.right, res.bottom
                        );
                    }
                });
    }

    // endregion

    // region Properties

    // region Adapter

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

    // endregion

    // region Columns

    public int getColumns() {
        return mColumns;
    }

    public void setColumns(int value) {
        mColumns = value;

        if(mFirstLayout) {
            return;
        }

        // Reset children
        for(int i = 0; i < getChildCount(); ++i) {
            LayoutParams lp = (LayoutParams) getChildAt(i).getLayoutParams();
            lp.needsMeasure = true;
            lp.width = 0;
        }

        // Populate layout
        mPopulatePending = false;
        populate();

        // Refresh view
        requestLayout();
    }

    // endregion

    // region CurrentItem

    public int getCurrentItem() {
        return mCurItem;
    }

    /**
     * Set the currently selected page. If the ColumnPager has already been through its first
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

    // endregion

    // region OffscreenPageLimit

    /**
     * Returns the number of pages that will be retained to either side of the
     * current page in the view hierarchy in an idle state. Defaults to 1.
     *
     * @return How many pages will be kept offscreen on either side
     * @see #setOffscreenPageLimit(int)
     */
    public int getOffscreenPageLimit() {
        return mOffscreenPageLimit;
    }

    /**
     * Set the number of pages that should be retained to either side of the
     * current page in the view hierarchy in an idle state. Pages beyond this
     * limit will be recreated from the adapter when needed.
     *
     * <p>This is offered as an optimization. If you know in advance the number
     * of pages you will need to support or have lazy-loading mechanisms in place
     * on your pages, tweaking this setting can have benefits in perceived smoothness
     * of paging animations and interaction. If you have a small number of pages (3-4)
     * that you can keep active all at once, less time will be spent in layout for
     * newly created view subtrees as the user pages back and forth.</p>
     *
     * <p>You should keep this limit low, especially if your pages have complex layouts.
     * This setting defaults to 1.</p>
     *
     * @param limit How many pages will be kept offscreen in an idle state.
     */
    public void setOffscreenPageLimit(int limit) {
        if (limit < DEFAULT_OFFSCREEN_PAGES) {
            Log.w(TAG, "Requested offscreen page limit " + limit + " too small; defaulting to " +
                    DEFAULT_OFFSCREEN_PAGES);
            limit = DEFAULT_OFFSCREEN_PAGES;
        }
        if (limit != mOffscreenPageLimit) {
            mOffscreenPageLimit = limit;
            populate();
        }
    }

    // endregion

    // region PageMargin

    /**
     * Set the margin between pages.
     *
     * @param marginPixels Distance between adjacent pages in pixels
     * @see #getPageMargin()
     * @see #setPageMarginDrawable(Drawable)
     * @see #setPageMarginDrawable(int)
     */
    public void setPageMargin(int marginPixels) {
        final int oldMargin = mPageMargin;
        mPageMargin = marginPixels;

        final int width = getWidth();
        recomputeScrollPosition(width, width, marginPixels, oldMargin);

        requestLayout();
    }

    /**
     * Return the margin between pages.
     *
     * @return The size of the margin in pixels
     */
    public int getPageMargin() {
        return mPageMargin;
    }

    // endregion

    // region PageMarginDrawable

    /**
     * Set a drawable that will be used to fill the margin between pages.
     *
     * @param d Drawable to display between pages
     */
    public void setPageMarginDrawable(Drawable d) {
        mMarginDrawable = d;
        if (d != null) refreshDrawableState();
        setWillNotDraw(d == null);
        invalidate();
    }

    /**
     * Set a drawable that will be used to fill the margin between pages.
     *
     * @param resId Resource ID of a drawable to display between pages
     */
    public void setPageMarginDrawable(@DrawableRes int resId) {
        setPageMarginDrawable(getContext().getResources().getDrawable(resId));
    }

    // endregion

    // region PageTransformer

    /**
     * Set a {@link PageTransformer} that will be called for each attached page whenever
     * the scroll position is changed. This allows the application to apply custom property
     * transformations to each page, overriding the default sliding look and feel.
     *
     * <p><em>Note:</em> Prior to Android 3.0 the property animation APIs did not exist.
     * As a result, setting a PageTransformer prior to Android 3.0 (API 11) will have no effect.</p>
     *
     * @param reverseDrawingOrder true if the supplied PageTransformer requires page views
     *                            to be drawn from last to first instead of first to last.
     * @param transformer PageTransformer that will modify each page's animation properties
     */
    public void setPageTransformer(boolean reverseDrawingOrder, PageTransformer transformer) {
        if (Build.VERSION.SDK_INT >= 11) {
            final boolean hasTransformer = transformer != null;
            final boolean needsPopulate = hasTransformer != (mPageTransformer != null);

            mPageTransformer = transformer;
            setChildrenDrawingOrderEnabledCompat(hasTransformer);

            if (hasTransformer) {
                mDrawingOrder = reverseDrawingOrder ? DRAW_ORDER_REVERSE : DRAW_ORDER_FORWARD;
            } else {
                mDrawingOrder = DRAW_ORDER_DEFAULT;
            }

            if (needsPopulate) populate();
        }
    }

    // endregion

    // region ScrollState

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

    // endregion

    // endregion

    // region Public methods

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

    // region Key Events

    /**
     * You can call this function yourself to have the scroll view perform
     * scrolling from a key event, just as if the event had been dispatched to
     * it by the view hierarchy.
     *
     * @param event The key event to execute.
     * @return Return true if the event was handled, else false.
     */
    public boolean executeKeyEvent(KeyEvent event) {
        boolean handled = false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    handled = arrowScroll(FOCUS_LEFT);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    handled = arrowScroll(FOCUS_RIGHT);
                    break;
                case KeyEvent.KEYCODE_TAB:
                    if (Build.VERSION.SDK_INT >= 11) {
                        // The focus finder had a bug handling FOCUS_FORWARD and FOCUS_BACKWARD
                        // before Android 3.0. Ignore the tab key on those devices.
                        if (KeyEventCompat.hasNoModifiers(event)) {
                            handled = arrowScroll(FOCUS_FORWARD);
                        } else if (KeyEventCompat.hasModifiers(event, KeyEvent.META_SHIFT_ON)) {
                            handled = arrowScroll(FOCUS_BACKWARD);
                        }
                    }
                    break;
            }
        }
        return handled;
    }

    public boolean arrowScroll(int direction) {
        View currentFocused = findFocus();
        if (currentFocused == this) {
            currentFocused = null;
        } else if (currentFocused != null) {
            boolean isChild = false;
            for (ViewParent parent = currentFocused.getParent(); parent instanceof ViewGroup;
                 parent = parent.getParent()) {
                if (parent == this) {
                    isChild = true;
                    break;
                }
            }
            if (!isChild) {
                // This would cause the focus search down below to fail in fun ways.
                final StringBuilder sb = new StringBuilder();
                sb.append(currentFocused.getClass().getSimpleName());
                for (ViewParent parent = currentFocused.getParent(); parent instanceof ViewGroup;
                     parent = parent.getParent()) {
                    sb.append(" => ").append(parent.getClass().getSimpleName());
                }
                Log.e(TAG, "arrowScroll tried to find focus based on non-child " +
                        "current focused view " + sb.toString());
                currentFocused = null;
            }
        }

        boolean handled = false;

        View nextFocused = FocusFinder.getInstance().findNextFocus(this, currentFocused,
                direction);
        if (nextFocused != null && nextFocused != currentFocused) {
            if (direction == View.FOCUS_LEFT) {
                // If there is nothing to the left, or this is causing us to
                // jump to the right, then what we really want to do is page left.
                final int nextLeft = getChildRectInPagerCoordinates(mTempRect, nextFocused).left;
                final int currLeft = getChildRectInPagerCoordinates(mTempRect, currentFocused).left;
                if (currentFocused != null && nextLeft >= currLeft) {
                    handled = pageLeft();
                } else {
                    handled = nextFocused.requestFocus();
                }
            } else if (direction == View.FOCUS_RIGHT) {
                // If there is nothing to the right, or this is causing us to
                // jump to the left, then what we really want to do is page right.
                final int nextLeft = getChildRectInPagerCoordinates(mTempRect, nextFocused).left;
                final int currLeft = getChildRectInPagerCoordinates(mTempRect, currentFocused).left;
                if (currentFocused != null && nextLeft <= currLeft) {
                    handled = pageRight();
                } else {
                    handled = nextFocused.requestFocus();
                }
            }
        } else if (direction == FOCUS_LEFT || direction == FOCUS_BACKWARD) {
            // Trying to move left and nothing there; try to page.
            handled = pageLeft();
        } else if (direction == FOCUS_RIGHT || direction == FOCUS_FORWARD) {
            // Trying to move right and nothing there; try to page.
            handled = pageRight();
        }
        if (handled) {
            playSoundEffect(SoundEffectConstants.getContantForFocusDirection(direction));
        }
        return handled;
    }

    // endregion

    // region Event Listeners

    /**
     * Add a listener that will be invoked whenever the page changes or is incrementally
     * scrolled. See {@link OnPageChangeListener}.
     *
     * <p>Components that add a listener should take care to remove it when finished.
     * Other components that take ownership of a view may call {@link #clearOnPageChangeListeners()}
     * to remove all attached listeners.</p>
     *
     * @param listener listener to add
     */
    public void addOnPageChangeListener(OnPageChangeListener listener) {
        if (mOnPageChangeListeners == null) {
            mOnPageChangeListeners = new ArrayList<>();
        }
        mOnPageChangeListeners.add(listener);
    }

    /**
     * Remove a listener that was previously added via
     * {@link #addOnPageChangeListener(OnPageChangeListener)}.
     *
     * @param listener listener to remove
     */
    public void removeOnPageChangeListener(OnPageChangeListener listener) {
        if (mOnPageChangeListeners != null) {
            mOnPageChangeListeners.remove(listener);
        }
    }

    /**
     * Remove all listeners that are notified of any changes in scroll state or position.
     */
    public void clearOnPageChangeListeners() {
        if (mOnPageChangeListeners != null) {
            mOnPageChangeListeners.clear();
        }
    }

    // endregion

    // endregion

    // region Private methods

    private int getClientWidth() {
        return getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
    }

    private int getPageWidth() {
        return (int)(
            ((float) getClientWidth()) / getColumns()
        );
    }

    private Rect getChildRectInPagerCoordinates(Rect outRect, View child) {
        if (outRect == null) {
            outRect = new Rect();
        }

        if (child == null) {
            outRect.set(0, 0, 0, 0);
            return outRect;
        }

        outRect.left = child.getLeft();
        outRect.right = child.getRight();
        outRect.top = child.getTop();
        outRect.bottom = child.getBottom();

        ViewParent parent = child.getParent();

        while (parent instanceof ViewGroup && parent != this) {
            final ViewGroup group = (ViewGroup) parent;
            outRect.left += group.getLeft();
            outRect.right += group.getRight();
            outRect.top += group.getTop();
            outRect.bottom += group.getBottom();

            parent = group.getParent();
        }

        return outRect;
    }

    private boolean pageLeft() {
        if (mCurItem > 0) {
            setCurrentItem(mCurItem-1, true);
            return true;
        }
        return false;
    }

    private boolean pageRight() {
        if (mAdapter != null && mCurItem < (mAdapter.getCount()-1)) {
            setCurrentItem(mCurItem+1, true);
            return true;
        }
        return false;
    }

    private void setChildrenDrawingOrderEnabledCompat(boolean enable) {
        if (Build.VERSION.SDK_INT >= 7) {
            if (mSetChildrenDrawingOrderEnabled == null) {
                try {
                    mSetChildrenDrawingOrderEnabled = ViewGroup.class.getDeclaredMethod(
                        "setChildrenDrawingOrderEnabled",
                        new Class[] { Boolean.TYPE }
                    );
                } catch (NoSuchMethodException e) {
                    Log.e(TAG, "Can't find setChildrenDrawingOrderEnabled", e);
                }
            }
            try {
                mSetChildrenDrawingOrderEnabled.invoke(this, enable);
            } catch (Exception e) {
                Log.e(TAG, "Error changing children drawing order", e);
            }
        }
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

    // region PagerItem

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

    private PagerItem infoForAnyChild(View child) {
        ViewParent parent;

        while ((parent=child.getParent()) != this) {
            if (parent == null || !(parent instanceof View)) {
                return null;
            }

            child = (View)parent;
        }

        return infoForChild(child);
    }

    private PagerItem infoForChild(View child) {
        for (int i = 0; i < mItems.size(); i++) {
            PagerItem item = mItems.get(i);

            if (mAdapter.isViewFromObject(child, item.getObject())) {
                return item;
            }
        }
        return null;
    }

    private PagerItem infoForPosition(int position) {
        for (PagerItem item : mItems) {
            if (item.getIndex() == position) {
                return item;
            }
        }

        return null;
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

    // endregion

    private void dataSetChanged() {
        // This method only gets called if our observer is attached, so mAdapter is non-null.
        final int adapterCount = mAdapter.getCount();
        mExpectedAdapterCount = adapterCount;

        boolean needPopulate = mItems.size() < mOffscreenPageLimit * 2 + 1 && mItems.size() < adapterCount;
        int newCurrItem = mCurItem;

        boolean isUpdating = false;
        for (int i = 0; i < mItems.size(); i++) {
            final PagerItem item = mItems.get(i);
            final int newPos = mAdapter.getItemPosition(item.getObject());

            if (newPos == android.support.v4.view.PagerAdapter.POSITION_UNCHANGED) {
                continue;
            }

            if (newPos == android.support.v4.view.PagerAdapter.POSITION_NONE) {
                mItems.remove(i);
                i--;

                if (!isUpdating) {
                    mAdapter.startUpdate(this);
                    isUpdating = true;
                }

                mAdapter.destroyItem(this, item.getPosition(), item.getObject());
                needPopulate = true;

                if (mCurItem == item.getPosition()) {
                    // Keep the current item in the valid range
                    newCurrItem = Math.max(0, Math.min(mCurItem, adapterCount - 1));
                    needPopulate = true;
                }
                continue;
            }

            if (item.getPosition() != newPos) {
                if (item.getPosition() == mCurItem) {
                    // Our current item changed position. Follow it.
                    newCurrItem = newPos;
                }

                item.setPosition(newPos);
                needPopulate = true;
            }
        }

        if (isUpdating) {
            mAdapter.finishUpdate(this);
        }

        Collections.sort(mItems, COMPARATOR);

        if (needPopulate) {
            // Reset our known page widths; populate will recompute them.
            final int childCount = getChildCount();

            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                if (!lp.isDecor) {
                    lp.width = 0;
                }
            }

            setCurrentItemInternal(newCurrItem, false, true);
            requestLayout();
        }
    }

    private void populate() {
        populate(mCurItem);
    }

    private void populate(int newCurrentItem) {
        if(DEBUG) {
            Log.d(TAG, "populate(" + newCurrentItem + ")");
        }

        PagerItem oldCurInfo = null;

        if (mCurItem != newCurrentItem) {
            oldCurInfo = infoForPosition(mCurItem);
            mCurItem = newCurrentItem;

            Log.d(TAG, " - Current item updated to " + newCurrentItem);
        }

        if(mAdapter == null) {
            if(DEBUG) {
                Log.d(TAG, " - No adapter available");
            }
            return;
        }

        if(mPopulatePending) {
            if (DEBUG) {
                Log.d(TAG, " - Populate is pending, skipping for now...");
            }
            return;
        }

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

        // Check width measurement of current pages and drawing sort order.
        // Update LayoutParams as needed.
        final int childCount = getChildCount();

        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            lp.childIndex = i;

            if (!lp.isDecor && lp.width == 0) {
                // 0 means requery the adapter for this, it doesn't have a valid width.
                final PagerItem item = infoForChild(child);

                if (item != null) {
                    lp.width = item.getWidth();
                    lp.position = item.getPosition();

                    Log.d(TAG, " - Updated layout params for child #" + i + ": " + lp);
                }
            }
        }

        sortChildDrawingOrder();

        if (hasFocus()) {
            View currentFocused = findFocus();
            PagerItem item = currentFocused != null ? infoForAnyChild(currentFocused) : null;

            if (item == null || item.getPosition() != mCurItem) {
                for (int i=0; i<getChildCount(); i++) {
                    View child = getChildAt(i);
                    item = infoForChild(child);

                    if (item != null && item.getPosition() == mCurItem) {
                        if (child.requestFocus(View.FOCUS_FORWARD)) {
                            break;
                        }
                    }
                }
            }
        }
    }

    private void sortChildDrawingOrder() {
        if (mDrawingOrder != DRAW_ORDER_DEFAULT) {
            if (mDrawingOrderedChildren == null) {
                mDrawingOrderedChildren = new ArrayList<View>();
            } else {
                mDrawingOrderedChildren.clear();
            }

            final int childCount = getChildCount();

            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                mDrawingOrderedChildren.add(child);
            }

            Collections.sort(mDrawingOrderedChildren, sPositionComparator);
        }
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
                final LayoutParams lp = (LayoutParams) child.getLayoutParams();
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

    // region dispatch

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

    // endregion

    // region smoothScrollTo

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

    // endregion

    // endregion

    // region ViewGroup

    /**
     * We only want the current page that is being shown to be focusable.
     */
    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        final int focusableCount = views.size();

        final int descendantFocusability = getDescendantFocusability();

        if (descendantFocusability != FOCUS_BLOCK_DESCENDANTS) {
            for (int i = 0; i < getChildCount(); i++) {
                final View child = getChildAt(i);

                if (child.getVisibility() == VISIBLE) {
                    PagerItem item = infoForChild(child);

                    if (item != null && item.getPosition() == mCurItem) {
                        child.addFocusables(views, direction, focusableMode);
                    }
                }
            }
        }

        // we add ourselves (if focusable) in all cases except for when we are
        // FOCUS_AFTER_DESCENDANTS and there are some descendants focusable.  this is
        // to avoid the focus search finding layouts when a more precise search
        // among the focusable children would be more interesting.
        if (descendantFocusability != FOCUS_AFTER_DESCENDANTS ||
                // No focusable descendants
                (focusableCount == views.size())) {

            // Note that we can't call the superclass here, because it will
            // add all views in.  So we need to do the same thing View does.
            if (!isFocusable()) {
                return;
            }

            if ((focusableMode & FOCUSABLES_TOUCH_MODE) == FOCUSABLES_TOUCH_MODE &&
                    isInTouchMode() && !isFocusableInTouchMode()) {
                return;
            }

            if (views != null) {
                views.add(this);
            }
        }
    }

    /**
     * We only want the current page that is being shown to be touchable.
     */
    @Override
    public void addTouchables(ArrayList<View> views) {
        // Note that we don't call super.addTouchables(), which means that
        // we don't call View.addTouchables().  This is okay because a ColumnPager
        // is itself not touchable.
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == VISIBLE) {
                PagerItem item = infoForChild(child);

                if (item != null && item.getPosition() == mCurItem) {
                    child.addTouchables(views);
                }
            }
        }
    }

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
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Let the focused view and/or our descendants get the key first
        return super.dispatchKeyEvent(event) || executeKeyEvent(event);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // Dispatch scroll events from this ColumnPager.
        if (event.getEventType() == AccessibilityEventCompat.TYPE_VIEW_SCROLLED) {
            return super.dispatchPopulateAccessibilityEvent(event);
        }

        // Dispatch all other accessibility events from the current page.
        final int childCount = getChildCount();

        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == VISIBLE) {
                final PagerItem item = infoForChild(child);

                if (item != null && item.getPosition() == mCurItem &&
                        child.dispatchPopulateAccessibilityEvent(event)) {
                    return true;
                }
            }
        }

        return false;
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
    public void removeView(View view) {
        if (mInLayout) {
            removeViewInLayout(view);
        } else {
            super.removeView(view);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        Log.d(TAG, "onLayout(" + changed + ", " + l + ", " + t + ", " + r + ", " + b + ")");

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

                if (dx != 0 && canScroll(this, false, (int) dx, (int) x, (int) y)) {
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
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        SavedState ss = new SavedState(superState);
        ss.position = mCurItem;

        if (mAdapter != null) {
            ss.adapterState = mAdapter.saveState();
        }

        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState)state;
        super.onRestoreInstanceState(ss.getSuperState());

        if (mAdapter != null) {
            mAdapter.restoreState(ss.adapterState, ss.loader);
            setCurrentItemInternal(ss.position, false, true);
        } else {
            mRestoredCurItem = ss.position;
            mRestoredAdapterState = ss.adapterState;
            mRestoredClassLoader = ss.loader;
        }
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

    // endregion

    // region Classes

    class ColumnPagerAccessibilityDelegate extends AccessibilityDelegateCompat {
        @Override
        public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(host, event);
            event.setClassName(ColumnPager.class.getName());

            final AccessibilityRecordCompat recordCompat = AccessibilityEventCompat.asRecord(event);
            recordCompat.setScrollable(canScroll());

            if (event.getEventType() == AccessibilityEventCompat.TYPE_VIEW_SCROLLED && mAdapter != null) {
                recordCompat.setItemCount(mAdapter.getCount());
                recordCompat.setFromIndex(mCurItem);
                recordCompat.setToIndex(mCurItem);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName(ColumnPager.class.getName());
            info.setScrollable(canScroll());

            if (canScrollHorizontally(1)) {
                info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
            }

            if (canScrollHorizontally(-1)) {
                info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
            }
        }

        @Override
        public boolean performAccessibilityAction(View host, int action, Bundle args) {
            if (super.performAccessibilityAction(host, action, args)) {
                return true;
            }

            switch (action) {
                case AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD: {
                    if (canScrollHorizontally(1)) {
                        setCurrentItem(mCurItem + 1);
                        return true;
                    }
                } return false;
                case AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD: {
                    if (canScrollHorizontally(-1)) {
                        setCurrentItem(mCurItem - 1);
                        return true;
                    }
                } return false;
            }
            return false;
        }

        private boolean canScroll() {
            return (mAdapter != null) && (mAdapter.getCount() > 1);
        }
    }

    /**
     * Layout parameters that should be supplied for views added to a
     * ColumnPager.
     */
    public static class LayoutParams extends ViewGroup.LayoutParams {
        /**
         * true if this view is a decoration on the pager itself and not
         * a view supplied by the adapter.
         */
        public boolean isDecor;

        /**
         * Gravity setting for use on decor views only:
         * Where to position the view page within the overall ColumnPager
         * container; constants are defined in {@link android.view.Gravity}.
         */
        public int gravity;

        /**
         * Width (in pixels)
         */
        int width = 0;

        /**
         * true if this view was added during layout and needs to be measured
         * before being positioned.
         */
        boolean needsMeasure;

        /**
         * Adapter position this view is for if !isDecor
         */
        int position;

        /**
         * Current child index within the ColumnPager that this view occupies
         */
        int childIndex;

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
            return "<ColumnPager.LayoutParams position: " + position + ", width: " + width + ", needsMeasure: " + needsMeasure + ">";
        }
    }

    private class PagerObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            dataSetChanged();
        }
        @Override
        public void onInvalidated() {
            dataSetChanged();
        }
    }

    /**
     * This is the persistent state that is saved by ColumnPager.  Only needed
     * if you are creating a sublass of ColumnPager that must save its own
     * state, in which case it should implement a subclass of this which
     * contains that state.
     */
    public static class SavedState extends BaseSavedState {
        int position;
        Parcelable adapterState;
        ClassLoader loader;

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(position);
            out.writeParcelable(adapterState, flags);
        }

        @Override
        public String toString() {
            return "ColumnPager.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " position=" + position + "}";
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = ParcelableCompat.newCreator(new ParcelableCompatCreatorCallbacks<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in, loader);
            }
            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        });

        SavedState(Parcel in, ClassLoader loader) {
            super(in);
            if (loader == null) {
                loader = getClass().getClassLoader();
            }
            position = in.readInt();
            adapterState = in.readParcelable(loader);
            this.loader = loader;
        }
    }

    static class ViewPositionComparator implements Comparator<View> {
        @Override
        public int compare(View lhs, View rhs) {
            final LayoutParams llp = (LayoutParams) lhs.getLayoutParams();
            final LayoutParams rlp = (LayoutParams) rhs.getLayoutParams();

            if (llp.isDecor != rlp.isDecor) {
                return llp.isDecor ? 1 : -1;
            }

            return llp.position - rlp.position;
        }
    }

    /**
     * Simple implementation of the {@link ColumnPager.OnPageChangeListener} interface with stub
     * implementations of each method. Extend this if you do not intend to override
     * every method of {@link ColumnPager.OnPageChangeListener}.
     */
    public static class SimpleOnPageChangeListener implements ColumnPager.OnPageChangeListener {
        @Override
        public void onPageScrolled(int position, int positionOffset) {
            // This space for rent
        }

        @Override
        public void onPageSelected(int position) {
            // This space for rent
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            // This space for rent
        }
    }

    // endregion

    // region Interfaces

    /**
     * Used internally to tag special types of child views that should be added as
     * pager decorations by default.
     */
    interface Decor {}

    /**
     * A PageTransformer is invoked whenever a visible/attached page is scrolled.
     * This offers an opportunity for the application to apply a custom transformation
     * to the page views using animation properties.
     *
     * <p>As property animation is only supported as of Android 3.0 and forward,
     * setting a PageTransformer on a ColumnPager on earlier platform versions will
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
        public void onAdapterChanged(PagerAdapter oldAdapter, PagerAdapter newAdapter);
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
        public void onPageScrolled(int position, int positionOffset);

        /**
         * This method will be invoked when a new page becomes selected. Animation is not
         * necessarily complete.
         *
         * @param position Position index of the new selected page.
         */
        public void onPageSelected(int position);

        /**
         * Called when the scroll state changes. Useful for discovering when the user
         * begins dragging, when the pager is automatically settling to the current page,
         * or when it is fully stopped/idle.
         *
         * @param state The new scroll state.
         * @see ColumnPager#SCROLL_STATE_IDLE
         * @see ColumnPager#SCROLL_STATE_DRAGGING
         * @see ColumnPager#SCROLL_STATE_SETTLING
         */
        public void onPageScrollStateChanged(int state);
    }

    // endregion
}
