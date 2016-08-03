package net.dgardiner.columnpager_demo.adapters;

import android.graphics.Color;
import android.support.v4.app.FragmentManager;
import net.dgardiner.columnpager.adapters.FragmentPagerAdapter;
import net.dgardiner.columnpager.adapters.PagerFragment;
import net.dgardiner.columnpager_demo.fragments.SimpleFragment;

import java.util.Random;

public class SimpleAdapter extends FragmentPagerAdapter {
    private Random rnd = new Random(2350982395735035723L);

    public SimpleAdapter(FragmentManager fragmentManager) {
        super(fragmentManager);
    }

    @Override
    public PagerFragment getItem(int position) {
        return SimpleFragment.newInstance(Integer.toString(position), Color.argb(
            255, rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)
        ));
    }

    @Override
    public int getCount() {
        return 24;
    }
}
