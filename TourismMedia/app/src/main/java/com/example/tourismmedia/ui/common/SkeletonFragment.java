package com.example.tourismmedia.ui.common;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.example.tourismmedia.R;

/** Shared presentation for top-level destinations while product features are being defined. */
public abstract class SkeletonFragment extends Fragment {
    @StringRes private final int titleRes;
    @StringRes private final int descriptionRes;

    protected SkeletonFragment(@StringRes int titleRes, @StringRes int descriptionRes) {
        super(R.layout.fragment_skeleton);
        this.titleRes = titleRes;
        this.descriptionRes = descriptionRes;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((TextView) view.findViewById(R.id.skeleton_title)).setText(titleRes);
        ((TextView) view.findViewById(R.id.skeleton_description)).setText(descriptionRes);
    }
}
