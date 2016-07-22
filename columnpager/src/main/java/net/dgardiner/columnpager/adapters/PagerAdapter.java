package net.dgardiner.columnpager.adapters;

import android.database.DataSetObserver;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;

public interface PagerAdapter {
    /**
     * Return the number of views available.
     */
    int getCount();

    /**
     * Called when a change in the shown pages is going to start being made.
     * @param container The containing View which is displaying this adapter's
     * page views.
     */
    void startUpdate(ViewGroup container);

    /**
     * Create the page for the given position.  The adapter is responsible
     * for adding the view to the container given here, although it only
     * must ensure this is done by the time it returns from
     * {@link #finishUpdate(ViewGroup)}.
     *
     * @param container The containing View in which the page will be shown.
     * @param position The page position to be instantiated.
     * @return Returns an Object representing the new page.  This does not
     * need to be a View, but can be some other container of the page.
     */
    Object instantiateItem(ViewGroup container, int position);

    /**
     * Remove a page for the given position.  The adapter is responsible
     * for removing the view from its container, although it only must ensure
     * this is done by the time it returns from {@link #finishUpdate(ViewGroup)}.
     *
     * @param container The containing View from which the page will be removed.
     * @param position The page position to be removed.
     * @param object The same object that was returned by
     * {@link #instantiateItem(ViewGroup, int)}.
     */
    void destroyItem(ViewGroup container, int position, Object object);

    /**
     * Called when the a change in the shown pages has been completed.  At this
     * point you must ensure that all of the pages have actually been added or
     * removed from the container as appropriate.
     * @param container The containing View which is displaying this adapter's
     * page views.
     */
    void finishUpdate(ViewGroup container);

    /**
     * Determines whether a page View is associated with a specific key object
     * as returned by {@link #instantiateItem(ViewGroup, int)}. This method is
     * required for a PagerAdapter to function properly.
     *
     * @param view Page View to check for association with <code>object</code>
     * @param object Object to check for association with <code>view</code>
     * @return true if <code>view</code> is associated with the key object <code>object</code>
     */
    boolean isViewFromObject(View view, Object object);

    /**
     * Save any instance state associated with this adapter and its pages that should be
     * restored if the current UI state needs to be reconstructed.
     *
     * @return Saved state for this adapter
     */
    public Parcelable saveState();

    /**
     * Restore any instance state associated with this adapter and its pages
     * that was previously saved by {@link #saveState()}.
     *
     * @param state State previously saved by a call to {@link #saveState()}
     * @param loader A ClassLoader that should be used to instantiate any restored objects
     */
    public void restoreState(Parcelable state, ClassLoader loader);

    /**
     * Called when the host view is attempting to determine if an item's position
     * has changed. Returns {@link #ColumnPager.POSITION_UNCHANGED} if the position of the given
     * item has not changed or {@link #ColumnPager.POSITION_NONE} if the item is no longer present
     * in the adapter.
     *
     * <p>The default implementation assumes that items will never
     * change position and always returns {@link #ColumnPager.POSITION_UNCHANGED}.
     *
     * @param object Object representing an item, previously returned by a call to
     *               {@link #instantiateItem(ViewGroup, int)}.
     * @return object's new position index from [0, {@link #getCount()}),
     *         {@link #ColumnPager.POSITION_UNCHANGED} if the object's position has not changed,
     *         or {@link #ColumnPager.POSITION_NONE} if the item is no longer present.
     */
    int getItemPosition(Object object);

    /**
     * Register an observer to receive callbacks related to the adapter's data changing.
     *
     * @param observer The {@link android.database.DataSetObserver} which will receive callbacks.
     */
    public void registerDataSetObserver(DataSetObserver observer);

    /**
     * Unregister an observer from callbacks related to the adapter's data changing.
     *
     * @param observer The {@link android.database.DataSetObserver} which will be unregistered.
     */
    public void unregisterDataSetObserver(DataSetObserver observer);

    void setColumnPagerObserver(DataSetObserver observer);
}
