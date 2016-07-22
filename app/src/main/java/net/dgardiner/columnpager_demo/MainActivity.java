package net.dgardiner.columnpager_demo;

import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.SeekBar;
import android.widget.TextView;
import net.dgardiner.columnpager.ColumnPager;
import net.dgardiner.columnpager_demo.adapters.SimpleAdapter;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    ColumnPager pager;

    ColumnControl columnControl;
    SeekBar columnSeekBar;
    TextView columnValue;

    WidthDimensionControl widthControl;
    SeekBar widthSeekBar;
    TextView widthValue;

    HeightDimensionControl heightControl;
    SeekBar heightSeekBar;
    TextView heightValue;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Find elements
        pager = (ColumnPager) findViewById(R.id.pager);

        columnSeekBar = (SeekBar) findViewById(R.id.columnsSeekBar);
        columnValue = (TextView) findViewById(R.id.columnsValue);

        widthSeekBar = (SeekBar) findViewById(R.id.widthSeekBar);
        widthValue = (TextView) findViewById(R.id.widthValue);

        heightSeekBar = (SeekBar) findViewById(R.id.heightSeekBar);
        heightValue = (TextView) findViewById(R.id.heightValue);

        // Configure pager
        pager.setAdapter(new SimpleAdapter(getSupportFragmentManager()));
        pager.setColumns(3);

        // Construct controls
        columnControl = new ColumnControl(pager, columnSeekBar, columnValue);
        columnSeekBar.setOnSeekBarChangeListener(columnControl);

        widthControl = new WidthDimensionControl(pager, widthSeekBar, widthValue);
        widthSeekBar.setOnSeekBarChangeListener(widthControl);

        heightControl = new HeightDimensionControl(pager, heightSeekBar, heightValue);
        heightSeekBar.setOnSeekBarChangeListener(heightControl);

        // Update column control
        columnControl.setMax(7);
        columnControl.setProgress(3);

        // Update width + height seekbars once drawn
        pager.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Update width control
                widthControl.setMax(pager.getMeasuredWidth());
                widthControl.setProgress(pager.getMeasuredWidth());

                // Update height control
                heightControl.setMax(pager.getMeasuredHeight());
                heightControl.setProgress(pager.getMeasuredHeight());

                // Remove listener
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    pager.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                } else {
                    pager.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }
            }
        });
    }

    private abstract static class Control implements SeekBar.OnSeekBarChangeListener {
        protected ColumnPager pager;

        protected SeekBar seekBar;
        protected TextView valueText;

        public Control(ColumnPager pager, SeekBar seekBar, TextView valueText) {
            this.pager = pager;

            this.seekBar = seekBar;
            this.valueText = valueText;
        }

        public abstract void onValueChanged(int value, boolean update);

        public void setMax(int value) {
            this.seekBar.setMax(value - 1);
        }

        public void setProgress(int value) {
            setProgress(value, true);
        }

        public void setProgress(int value, boolean update) {
            // Update seekbar position
            seekBar.setProgress(value - 1);
        }

        //
        // SeekBar.OnSeekBarChangeListener
        //

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            onValueChanged(progress + 1, true);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    }

    private static class ColumnControl extends Control {
        public ColumnControl(ColumnPager pager, SeekBar seekBar, TextView valueText) {
            super(pager, seekBar, valueText);
        }

        @Override
        public void onValueChanged(int value, boolean update) {
            Log.i(TAG, "Updating pager columns to " + value);

            // Update pager columns
            if(update) {
                pager.setColumns(value);
            }

            // Update value
            valueText.setText(Integer.toString(value));
        }
    }

    private abstract static class DimensionControl extends Control {
        public DimensionControl(ColumnPager pager, SeekBar seekBar, TextView value) {
            super(pager, seekBar, value);
        }

        public abstract void updateDimension(int value);

        @Override
        public void onValueChanged(int value, boolean update) {
            // Update pager dimension
            if(update) {
                updateDimension(value);
                pager.requestLayout();
            }

            // Update text view
            valueText.setText(Integer.toString(value));
        }
    }

    private static class WidthDimensionControl extends DimensionControl {
        public WidthDimensionControl(ColumnPager pager, SeekBar seekBar, TextView value) {
            super(pager, seekBar, value);
        }

        @Override
        public void updateDimension(int value) {
            Log.d(TAG, "Updating pager width to " + value);

            // Update layout width
            pager.getLayoutParams().width = value;
        }
    }

    private static class HeightDimensionControl extends DimensionControl {
        public HeightDimensionControl(ColumnPager pager, SeekBar seekBar, TextView value) {
            super(pager, seekBar, value);
        }

        @Override
        public void updateDimension(int value) {
            Log.d(TAG, "Updating pager height to " + value);

            // Update layout height
            pager.getLayoutParams().height = value;
        }
    }
}
