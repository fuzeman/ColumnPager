package net.dgardiner.columnpager.adapters;

import android.view.ViewGroup;

public interface PagerAdapter {
    int getCount();

    void startUpdate(ViewGroup container);
    Object instantiateItem(ViewGroup container, int position);
    void destroyItem(ViewGroup container, int position, Object object);
    void finishUpdate(ViewGroup container);
}
