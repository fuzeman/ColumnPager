package net.dgardiner.columnpager.adapters;

import android.support.v4.app.Fragment;
import net.dgardiner.columnpager.core.PagerItem;

public class PagerFragment extends Fragment {
    private PagerItem item;

    public PagerItem getItem() {
        return item;
    }

    public void setItem(PagerItem value) {
        item = value;
    }
}
