package com.rma.mwmw.iristicktwiliocapturer.util;

import android.content.Context;
import android.graphics.Point;
import android.support.annotation.NonNull;
import android.util.Log;

import com.iristick.smartglass.core.Headset;
import com.iristick.smartglass.core.camera.CameraCharacteristics;
import com.iristick.smartglass.core.camera.CaptureRequest;
import com.twilio.video.VideoCapturer;
import com.twilio.video.VideoDimensions;
import com.twilio.video.VideoFormat;
import com.twilio.video.VideoPixelFormat;

import org.webrtc.CameraEnumerationAndroid;

import java.util.ArrayList;
import java.util.List;

public class IristickTwilioCapturer implements VideoCapturer {

    private final String TAG = "IristickTwilioCapturer";

    private final String[] cameraNames;
    private final Headset headset;

    private final Context applicationContext;
    private final Listener listener;
    private String cameraId;

    public IristickTwilioCapturer(
            @NonNull Context context,
            @NonNull String cameraId,
            @NonNull Headset headset,
            @NonNull IristickTwilioCapturer.Listener listener) {
        this.applicationContext = context.getApplicationContext();
        this.cameraId = cameraId;
        this.listener = listener;
        this.headset = headset;
        this.cameraNames = headset.getCameraIdList();
    }

    /**
     * Starts capturing frames at the specified format. Frames will be provided to the given
     * listener upon availability.
     *
     * <p><b>Note</b>: This method is not meant to be invoked directly.
     *
     * @param captureFormat the format in which to capture frames.
     * @param videoCapturerListener consumer of available frames.
     */
    @Override
    public void startCapture(
            @NonNull VideoFormat captureFormat,
            @NonNull VideoCapturer.Listener videoCapturerListener) {
        // TODO
        throw new UnsupportedOperationException();
    }

    /**
     * Stops all frames being captured.
     *
     * <p><b>Note</b>: This method is not meant to be invoked directly.
     */
    @Override
    public void stopCapture() {
        // TODO
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a list of all supported video formats. This list is based on what is specified by
     * {@link android.hardware.camera2.CameraCharacteristics}, so can vary based on a device's
     * camera capabilities.
     *
     * <p><b>Note</b>: This method can be invoked for informational purposes, but is primarily used
     * internally.
     *
     * @return all supported video formats.
     */
    @Override
    public synchronized List<VideoFormat> getSupportedFormats() {
        CameraCharacteristics.StreamConfigurationMap streamConfigurationMap = this.headset.getCameraCharacteristics(cameraNames[0])
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        Point[] sizes = streamConfigurationMap.getSizes(CaptureRequest.FORMAT_JPEG);

        List<VideoFormat> videoFormats = new ArrayList<>();
        for (Point size : sizes) {
            int frameRate = (int) Math.floor(1000000000L / streamConfigurationMap.getMinFrameDuration(size));
            VideoDimensions videoDimensions = new VideoDimensions(size.x, size.y);
            VideoFormat videoFormat = new VideoFormat(videoDimensions, frameRate, VideoPixelFormat.RGBA_8888);
            videoFormats.add(videoFormat);
        }

        return videoFormats;
    }

    /** Indicates that the capturer is not a screen cast. */
    @Override
    public boolean isScreencast() {
        return false;
    }

    public interface Listener {
        void onFirstFrameAvailable();
        void onError(@NonNull Exception iristickTwilioCapturerException);
    }
}