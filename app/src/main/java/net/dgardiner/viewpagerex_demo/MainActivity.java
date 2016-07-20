package net.dgardiner.viewpagerex_demo;

import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.SeekBar;
import net.dgardiner.viewpagerex.ViewPager;
import net.dgardiner.viewpagerex_demo.adapters.SimpleAdapter;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    boolean dimensionSeekBarsInitialized = false;

    ViewPager pager;

    SeekBar seekColumns;
    SeekBar seekWidth;
    SeekBar seekHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pager = (ViewPager) findViewById(R.id.pager);
        pager.setAdapter(new SimpleAdapter(getSupportFragmentManager()));
        pager.setColumns(3);

        seekColumns = (SeekBar) findViewById(R.id.seekColumns);
        seekWidth = (SeekBar) findViewById(R.id.seekWidth);
        seekHeight = (SeekBar) findViewById(R.id.seekHeight);

        // Bind to seekbar events
        seekColumns.setOnSeekBarChangeListener(new OnColumnSeekBarChangeListener(pager));
        seekWidth.setOnSeekBarChangeListener(new OnWidthSeekBarChangeListener(pager));
        seekHeight.setOnSeekBarChangeListener(new OnHeightSeekBarChangeListener(pager));

        // Update column seekbar
        seekColumns.setMax(8);
        seekColumns.setProgress(3);

        // Update width + height seekbars once drawn
        pager.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Update width seekbar
                seekWidth.setMax(pager.getMeasuredWidth());
                seekWidth.setProgress(pager.getMeasuredWidth());

                // Update height seekbar
                seekHeight.setMax(pager.getMeasuredHeight());
                seekHeight.setProgress(pager.getMeasuredHeight());

                // Remove listener
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    pager.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                } else {
                    pager.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }
            }
        });
    }

    private static class OnSeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {
        protected ViewPager pager;

        public OnSeekBarChangeListener(ViewPager pager) {
            this.pager = pager;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    }

    private static class OnColumnSeekBarChangeListener extends OnSeekBarChangeListener {
        public OnColumnSeekBarChangeListener(ViewPager pager) {
            super(pager);
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            progress += 1;

            Log.i(TAG, "Updating pager columns to " + progress);
            pager.setColumns(progress);
        }
    }

    private static class OnWidthSeekBarChangeListener extends OnSeekBarChangeListener {
        public OnWidthSeekBarChangeListener(ViewPager pager) {
            super(pager);
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

        }
    }

    private static class OnHeightSeekBarChangeListener extends OnSeekBarChangeListener {
        public OnHeightSeekBarChangeListener(ViewPager pager) {
            super(pager);
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

        }
    }
}
