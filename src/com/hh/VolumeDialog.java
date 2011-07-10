package com.hh;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class VolumeDialog extends Dialog implements OnClickListener, DialogInterface.OnDismissListener {

    private SeekBarVolumizer[] mSeekBarVolumizer;
    private static final int[] SEEKBAR_ID = new int[] {
        R.id.incoming_call_volume_seekbar,
        R.id.notification_volume_seekbar,
        R.id.media_volume_seekbar,
        R.id.alarm_volume_seekbar
    };
    private static final int[] SEEKBAR_TYPE = new int[] {
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_SYSTEM,
    };
    
    private CheckBoxRinger mcheckBoxMuter;
    private static final int[] CHECKBOX_TYPE = new int[] {
        AudioManager.RINGER_MODE_VIBRATE,
        AudioManager.RINGER_MODE_NORMAL
    };
    
    private Context mContext;
    private Button mButtonOK;
    private Button mButtonCancel;
        
    public VolumeDialog(Context context) {
        super(context,android.R.style.Theme_Dialog);
        mContext = context;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.ID_ANDROID_CONTENT);
        requestWindowFeature(Window.FEATURE_LEFT_ICON);
        
        setContentView(R.layout.preference_dialog_ringervolume);
        getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, R.drawable.ic_settings_sound);
        setTitle(R.string.sound_settings);
        
        int width = mContext.getResources().getDisplayMetrics().widthPixels;
        getWindow().setLayout(width, LayoutParams.WRAP_CONTENT);
        
        mButtonOK = (Button) findViewById(R.id.buttonOK);
        mButtonOK.setOnClickListener(this);
        mButtonCancel = (Button) findViewById(R.id.buttonCancel);
        mButtonCancel.setOnClickListener(this);
        
        mSeekBarVolumizer = new SeekBarVolumizer[SEEKBAR_ID.length];
        for (int i = 0; i < SEEKBAR_ID.length; i++) {
            SeekBar seekBar = (SeekBar) findViewById(SEEKBAR_ID[i]);
            Log.d(getClass().toString(), seekBar.toString());
            mSeekBarVolumizer[i] = new SeekBarVolumizer(mContext, seekBar,
                SEEKBAR_TYPE[i]);
        }
        CheckBox checkBox = (CheckBox) findViewById(R.id.mute_volume_checkbox);
        Log.d(getClass().toString(), checkBox.toString());
        mcheckBoxMuter = new CheckBoxRinger(mContext, checkBox);
    }

    @Override
    public void onClick(View arg0) {
        switch (arg0.getId()) {
            case R.id.buttonOK:
                closeVolumes();
                ((VolumeControl)mContext).close();
                break;
            case R.id.buttonCancel:
                revertVolume();
                closeVolumes();
                ((VolumeControl)mContext).close();
                break;
        }
    }

    @Override
    public void onBackPressed() {
        revertVolume();
        closeVolumes();
        ((VolumeControl)mContext).close();
    }
    
    private void revertVolume() {
        for (SeekBarVolumizer vol : mSeekBarVolumizer) {
            vol.revertVolume();
        }
        mcheckBoxMuter.revertMute();
    }
    
    private void closeVolumes() {
        for (SeekBarVolumizer vol : mSeekBarVolumizer) {
            vol.stop();
        }
    }
    
    protected void onSampleStarting(SeekBarVolumizer volumizer) {
        for (SeekBarVolumizer vol : mSeekBarVolumizer) {
            if (vol != null && vol != volumizer) vol.stopSample();
        }
    }
    
    public class CheckBoxRinger implements OnCheckedChangeListener, Runnable {

        private Context mContext;
        private Handler mHandler = new Handler();
    
        private AudioManager mAudioManager;
        
        private int mOriginalRingerStatus; 
    
        private boolean mLastRinger;
        private CheckBox mCheckBox;
        
        private ContentObserver mRingerObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                if (mCheckBox != null) {
                    int silentModeStreams = Settings.System.getInt(mContext.getContentResolver(),
                            Settings.System.MODE_RINGER, 0);
                    mCheckBox.setChecked((silentModeStreams == CHECKBOX_TYPE[0]) ? true : false);
                }
            }
        };
        
        public CheckBoxRinger(Context context, CheckBox checkBox) {
            mContext = context;
            mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            mCheckBox = checkBox;
            
            initCheckBox(checkBox);
        }
        
        public void revertMute() {
            mAudioManager.setRingerMode(mOriginalRingerStatus);
        }

        private void initCheckBox(CheckBox checkBox) {
            
            mOriginalRingerStatus = mAudioManager.getRingerMode();
            mCheckBox.setChecked((mOriginalRingerStatus == CHECKBOX_TYPE[0]) ? true : false);
            mCheckBox.setOnCheckedChangeListener(this);
            
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.MODE_RINGER),
                    false, mRingerObserver);
        }

        @Override
        public void run() {
            mAudioManager.setRingerMode((mLastRinger) ? 1 : 2);
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView,
                boolean isChecked) {
            postSetVolume(isChecked);
        }

        void postSetVolume(boolean isChecked) {
            mLastRinger = isChecked;
            mHandler.removeCallbacks(this);
            mHandler.post(this);
        }
    }
    
    public class SeekBarVolumizer implements OnSeekBarChangeListener, Runnable {

        private Context mContext;
        private Handler mHandler = new Handler();
    
        private AudioManager mAudioManager;
        private int mStreamType;
        private int mOriginalStreamVolume; 
        private Ringtone mRingtone;
    
        private int mLastProgress = -1;
        private SeekBar mSeekBar;
        
        private ContentObserver mVolumeObserver = new ContentObserver(mHandler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                if (mSeekBar != null) {
                    int volume = Settings.System.getInt(mContext.getContentResolver(),
                            Settings.System.VOLUME_SETTINGS[mStreamType], -1);
                    if (volume >= 0) {
                        mSeekBar.setProgress(volume);
                    }
                }
            }
        };

        public SeekBarVolumizer(Context context, SeekBar seekBar, int streamType) {
            mContext = context;
            mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            mStreamType = streamType;
            mSeekBar = seekBar;
            
            initSeekBar(seekBar);
        }

        private void initSeekBar(SeekBar seekBar) {
            seekBar.setMax(mAudioManager.getStreamMaxVolume(mStreamType));
            mOriginalStreamVolume = mAudioManager.getStreamVolume(mStreamType);
            seekBar.setProgress(mOriginalStreamVolume);
            seekBar.setOnSeekBarChangeListener(this);
            
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.VOLUME_SETTINGS[mStreamType]),
                    false, mVolumeObserver);
    
            Uri defaultUri = null;
            if (mStreamType == AudioManager.STREAM_RING) {
                defaultUri = Settings.System.DEFAULT_RINGTONE_URI;
            } else if (mStreamType == AudioManager.STREAM_NOTIFICATION) {
                defaultUri = Settings.System.DEFAULT_NOTIFICATION_URI;
            } else if (mStreamType == AudioManager.STREAM_MUSIC) {
                defaultUri = Settings.System.DEFAULT_RINGTONE_URI;
            } else {
                defaultUri = Settings.System.DEFAULT_ALARM_ALERT_URI;
            }

            mRingtone = RingtoneManager.getRingtone(mContext, defaultUri);
            if (mRingtone != null) {
                mRingtone.setStreamType(mStreamType);
            }
        }
        
        public void stop() {
            stopSample();
            mContext.getContentResolver().unregisterContentObserver(mVolumeObserver);
            mSeekBar.setOnSeekBarChangeListener(null);
        }
        
        public void revertVolume() {
            mAudioManager.setStreamVolume(mStreamType, mOriginalStreamVolume, 0);
        }
        
        public void onProgressChanged(SeekBar seekBar, int progress,
                boolean fromTouch) {
            if (!fromTouch) {
                return;
            }
    
            postSetVolume(progress);
        }

        void postSetVolume(int progress) {
            mLastProgress = progress;
            mHandler.removeCallbacks(this);
            mHandler.post(this);
        }
    
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        public void onStopTrackingTouch(SeekBar seekBar) {
            if (mRingtone != null && !mRingtone.isPlaying()) {
                sample();
            }
        }
        
        public void run() {
            mAudioManager.setStreamVolume(mStreamType, mLastProgress, 0);
        }
        
        private void sample() {
            onSampleStarting(this);
            mRingtone.play();
        }
    
        public void stopSample() {
            if (mRingtone != null) {
                mRingtone.stop();
            }
        }

        public SeekBar getSeekBar() {
            return mSeekBar;
        }
        
        public void changeVolumeBy(int amount) {
            mSeekBar.incrementProgressBy(amount);
            if (mRingtone != null && !mRingtone.isPlaying()) {
                sample();
            }
            postSetVolume(mSeekBar.getProgress());
        }

        public void onSaveInstanceState(VolumeStore volumeStore) {
            if (mLastProgress >= 0) {
                volumeStore.volume = mLastProgress;
                volumeStore.originalVolume = mOriginalStreamVolume;
            }
        }

        public void onRestoreInstanceState(VolumeStore volumeStore) {
            if (volumeStore.volume != -1) {
                mOriginalStreamVolume = volumeStore.originalVolume;
                mLastProgress = volumeStore.volume;
                postSetVolume(mLastProgress);
            }
        }
    }
    
    public static class VolumeStore {
        public int volume = -1;
        public int originalVolume = -1;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {

    }
}
