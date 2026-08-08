package com.liskovsoft.smartyoutubetv2.droid.ui.adddevice;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AddDevicePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.AddDeviceView;
import com.liskovsoft.smartyoutubetv2.droid.R;
import com.liskovsoft.smartyoutubetv2.droid.ui.base.DroidActivity;

/**
 * Touch implementation of the remote-control pairing screen: shows the pairing code
 * to enter in the YouTube app's Settings/Watch on TV section.
 */
public class AddDeviceActivity extends DroidActivity implements AddDeviceView {
    private AddDevicePresenter mAddDevicePresenter;
    private TextView mCodeText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.misc_adddevice_activity);

        mCodeText = findViewById(R.id.misc_adddevice_code_text);
        MaterialButton doneButton = findViewById(R.id.misc_adddevice_done_button);

        mAddDevicePresenter = AddDevicePresenter.instance(this);
        mAddDevicePresenter.setView(this);

        doneButton.setOnClickListener(v -> mAddDevicePresenter.onActionClicked());

        mAddDevicePresenter.onViewInitialized();
    }

    @Override
    protected void onResume() {
        super.onResume();

        mAddDevicePresenter.onViewResumed();
    }

    @Override
    protected void onPause() {
        super.onPause();

        mAddDevicePresenter.onViewPaused();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mAddDevicePresenter.onViewDestroyed();
    }

    @Override
    public void showCode(String userCode) {
        // NOTE: on error the presenter passes the error message as the code
        if (TextUtils.isEmpty(userCode)) {
            return;
        }

        mCodeText.setText(userCode);
    }

    @Override
    public void close() {
        finish();
    }
}
