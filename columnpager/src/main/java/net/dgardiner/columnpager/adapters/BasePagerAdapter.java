package net.dgardiner.columnpager.adapters;

import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import net.dgardiner.columnpager.ColumnPager;

public abstract class BasePagerAdapter implements PagerAdapter {
    private final DataSetObservable mObservable = new DataSetObservable();
    private DataSetObserver mColumnPagerObserver;

    @Override
    public abstract int getCount();

    @Override
    public void startUpdate(ViewGroup container) {}

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        throw new UnsupportedOperationException(
            "Required method instantiateItem(ViewGroup, int) was not overridden"
        );
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        throw new UnsupportedOperationException(
            "Required method destroyItem(ViewGroup, int, Object) was not overridden"
        );
    }

    @Override
    public void finishUpdate(ViewGroup container) {}

    @Override
    public abstract boolean isViewFromObject(View view, Object object);

    @Override
    public Parcelable saveState() {
        return null;
    }

    @Override
    public void restoreState(Parcelable state, ClassLoader loader) {}

    @Override
    public int getItemPosition(Object object) {
        return ColumnPager.POSITION_UNCHANGED;
    }

    /**
     * This method should be called by the application if the data backing this adapter has changed
     * and associated views should update.
     */
    public void notifyDataSetChanged() {
        synchronized (this) {
            if (mColumnPagerObserver != null) {
                mColumnPagerObserver.onChanged();
            }
        }
        mObservable.notifyChanged();
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        mObservable.registerObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        mObservable.unregisterObserver(observer);
    }

    @Override
    public void setColumnPagerObserver(DataSetObserver observer) {
        synchronized (this) {
            mColumnPagerObserver = observer;
        }
    }
}
