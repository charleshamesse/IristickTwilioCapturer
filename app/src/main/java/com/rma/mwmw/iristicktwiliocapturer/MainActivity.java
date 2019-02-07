package com.rma.mwmw.iristicktwiliocapturer;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.iristick.smartglass.core.Headset;
import com.iristick.smartglass.support.app.IristickApp;
import com.rma.mwmw.iristicktwiliocapturer.util.IristickTwilioCapturer;
import com.twilio.video.Camera2Capturer;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.VideoCapturer;
import com.twilio.video.VideoView;

import java.time.Duration;

public class MainActivity extends BaseActivity {
    private static final int CAMERA_MIC_PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "IristickTwilioCapturer";

    private static final String LOCAL_AUDIO_TRACK_NAME = "mic";
    private static final String LOCAL_VIDEO_TRACK_NAME = "camera";

    private VideoCapturer currentCapturer;
    private LocalAudioTrack localAudioTrack;
    private LocalVideoTrack localVideoTrack;

    private Boolean useGlasses;

    // UI
    private Button btnSwitchSource;
    private VideoView primaryVideoView;

    private Headset headset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start with phone camera
        useGlasses = false;

        // Get headset
        headset = IristickApp.getHeadset();

        // Set UI
        primaryVideoView = findViewById(R.id.primary_video_view);
        btnSwitchSource = findViewById(R.id.button_switch_source);
        btnSwitchSource.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(headset != null) {
                    useGlasses = !useGlasses;
                    releaseAudioAndVideoTracks();
                    createAudioAndVideoTracks();
                }
                else {
                    Toast.makeText(MainActivity.this, "Headset not connected", Toast.LENGTH_LONG).show();
                }
            }
        });

        // Make sure permissions are granted and create tracks
        if (!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraAndMicrophone();
        } else {
            createAudioAndVideoTracks();
        }
    }

    // Permissions
    private boolean checkPermissionForCameraAndMicrophone(){
        int resultCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return resultCamera == PackageManager.PERMISSION_GRANTED &&
                resultMic == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionForCameraAndMicrophone(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.RECORD_AUDIO)) {
            Toast.makeText(this,
                    "Need permissions",
                    Toast.LENGTH_LONG).show();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    CAMERA_MIC_PERMISSION_REQUEST_CODE);
        }
    }

    // Camera2Capturer Listener
    private final Camera2Capturer.Listener camera2Listener = new Camera2Capturer.Listener() {
        @Override
        public void onFirstFrameAvailable() {
            Log.i(TAG, "onFirstFrameAvailable");
        }

        @Override
        public void onCameraSwitched(String newCameraId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onError(Camera2Capturer.Exception camera2CapturerException) {
            Log.e(TAG, camera2CapturerException.getMessage());
        }
    };

    private final IristickTwilioCapturer.Listener iristickTwilioCapturerListener = new IristickTwilioCapturer.Listener() {
        @Override
        public void onFirstFrameAvailable() {
            Log.i(TAG, "onFirstFrameAvailable");
        }

        @Override
        public void onError(Exception e) {
            Log.e(TAG, e.toString());
        }
    };


    // Tracks
    private void createAudioAndVideoTracks() {
        if(useGlasses) {
            // Use our custom capturer
            currentCapturer = new IristickTwilioCapturer(this, "0", headset, iristickTwilioCapturerListener);
        }
        else {
            // Use Twilio's capturer
            currentCapturer = new Camera2Capturer(
                this,
                "0", // Hard-coded for simplicity, doesn't matter which camera it is
                camera2Listener);
        }

        // Create tracks
        localAudioTrack = LocalAudioTrack.create(this, true, LOCAL_AUDIO_TRACK_NAME);
        localVideoTrack = LocalVideoTrack.create(this, true, currentCapturer, LOCAL_VIDEO_TRACK_NAME);
        localVideoTrack.addRenderer(primaryVideoView);
    }
    private void releaseAudioAndVideoTracks() {
        localAudioTrack.release();
        localVideoTrack.release();
    }
}
