package com.hh;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class VolumeControl extends Activity {

    private static final String TAG = "VolumeControl";
    private VolumeDialog volumeDialog;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        showVolumeDialog();
    }
    
    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }
    
    private void showVolumeDialog() {
        volumeDialog = new VolumeDialog(this);
        volumeDialog.show();
    }
    
    public void close() {
        volumeDialog.dismiss();
        finish();
    }
}