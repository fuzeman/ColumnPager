package net.dgardiner.viewpagerex_demo.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import net.dgardiner.viewpagerex.adapters.PagerFragment;
import net.dgardiner.viewpagerex_demo.R;

public class SimpleFragment extends PagerFragment {
    private String title;
    private int backgroundColour;

    public static SimpleFragment newInstance(String title, int backgroundColour) {
        SimpleFragment fragment = new SimpleFragment();

        Bundle arguments = new Bundle();
        arguments.putString("title", title);
        arguments.putInt("backgroundColour", backgroundColour);

        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        title = getArguments().getString("title");

        // Parse background colour
        backgroundColour = getArguments().getInt("backgroundColour");
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fr_simple, container, false);
        view.setBackgroundColor(backgroundColour);
        view.setTag(this);

        TextView title = (TextView) view.findViewById(R.id.title);
        title.setText(this.title);

        return view;
    }

    @Override
    public String toString() {
        return "<SimpleFragment title: \"" + title + "\">";
    }
}
