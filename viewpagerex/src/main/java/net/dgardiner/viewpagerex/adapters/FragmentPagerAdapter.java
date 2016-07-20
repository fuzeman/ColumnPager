package net.dgardiner.viewpagerex.adapters;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.ViewGroup;

public abstract class FragmentPagerAdapter implements PagerAdapter {
    private static final String TAG = "FragmentPagerAdapter";
    private static final boolean DEBUG = true;

    private final FragmentManager mFragmentManager;
    private FragmentTransaction mCurTransaction = null;

    public FragmentPagerAdapter(FragmentManager fragmentManager) {
        mFragmentManager = fragmentManager;
    }

    public abstract PagerFragment getItem(int position);

    @Override
    public abstract int getCount();

    //
    // Public methods
    //

    public long getItemId(int position) {
        return position;
    }

    public void startUpdate(ViewGroup container) {
    }

    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction();
        }

        final long itemId = getItemId(position);

        // Do we already have this fragment?
        String name = makeFragmentName(container.getId(), itemId);
        Fragment fragment = mFragmentManager.findFragmentByTag(name);

        if (fragment != null && fragment instanceof PagerFragment) {
            if (DEBUG) {
                Log.v(TAG, "Attaching item #" + itemId + ": f=" + fragment);
            }

            mCurTransaction.attach(fragment);
        } else {
            fragment = getItem(position);

            if (DEBUG) {
                Log.v(TAG, "Adding item #" + itemId + ": f=" + fragment);
            }

            mCurTransaction.add(container.getId(), fragment, makeFragmentName(container.getId(), itemId));
        }

        // TODO check what these properties are doing
        fragment.setMenuVisibility(false);
        fragment.setUserVisibleHint(false);

        return fragment;
    }

    public void destroyItem(ViewGroup container, int position, Object object) {
        if (mCurTransaction == null) {
            mCurTransaction = mFragmentManager.beginTransaction();
        }

        if (DEBUG) {
            Log.v(TAG, "Detaching item #" + getItemId(position) + ": f=" + object +
                       " v=" + ((Fragment)object).getView());
        }

        mCurTransaction.detach((Fragment)object);
    }

    public void finishUpdate(ViewGroup container) {
        if (mCurTransaction != null) {
            mCurTransaction.commitAllowingStateLoss();
            mCurTransaction = null;

            mFragmentManager.executePendingTransactions();
        }
    }

    //
    // Private methods
    //

    private static String makeFragmentName(int viewId, long id) {
        return "android:switcher:" + viewId + ":" + id;
    }
}
