package com.rma.mwmw.iristicktwiliocapturer.util;

import android.content.Context;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import com.iristick.smartglass.core.Headset;
import com.iristick.smartglass.core.camera.CameraCharacteristics;
import com.iristick.smartglass.core.camera.CameraDevice;
import com.iristick.smartglass.core.camera.CaptureRequest;
import com.iristick.smartglass.core.camera.CaptureSession;
import com.twilio.video.VideoCapturer;
import com.twilio.video.VideoDimensions;
import com.twilio.video.VideoFormat;
import com.twilio.video.VideoPixelFormat;

import org.webrtc.SurfaceTextureHelper;

import java.util.ArrayList;
import java.util.List;

public class IristickTwilioCapturer implements VideoCapturer {

    private final String TAG = "IristickTwilioCapturer";

    private final String[] cameraNames;
    private final Headset headset;

    private final Context applicationContext;
    private final Listener listener;
    private String cameraId;


    private final Object stateLock = new Object();

    private boolean sessionOpening;
    private boolean sessionStopping;
    private boolean firstFrameObserved;
    private int failureCount;
    private int cameraIdx = 0;
    private int width;
    private int height;
    private int frameRate;

    private CameraDevice cameraDevice;
    private Surface surface;
    private CaptureSession captureSession;
    private SurfaceTextureHelper surfaceHelper;
    private SurfaceTexture surfaceTexture;
    // TODO
    // Get handler from some surface texture helper
    private Handler cameraThreadHandler = new Handler(Looper.getMainLooper());

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

        synchronized (stateLock) {
            if (sessionOpening || captureSession != null) {
                Log.w(TAG, "Capture already started");
                return;
            }

            openCamera(true);
        }
    }

    private void openCamera(boolean resetFailures) {
        Log.i(TAG, "openCamera");

        synchronized (stateLock) {
            if (resetFailures)
                failureCount = 0;

            closeCamera();
            sessionOpening = true;
            Log.i(TAG, "sessionOpening");
            cameraThreadHandler.post(() -> {
                synchronized (stateLock) {
                    final String name = cameraNames[0];
                    Log.i(TAG, "camera thread handler" + name);
                    try {
                        headset.openCamera(name, cameraListener, cameraThreadHandler);
                    } catch (IllegalArgumentException e) {
                        Log.i(TAG, "Error openCamera headset");
                    }
                }
            });
        }
    }

    private void closeCamera() {
        synchronized (stateLock) {
            Log.i(TAG, "closeCamera");
            final CameraDevice _cameraDevice = cameraDevice;
            final Surface _surface = surface;
            cameraThreadHandler.post(() -> {
                try {
                    if (_cameraDevice != null)
                        _cameraDevice.close();
                } catch (IllegalStateException e) {
                    // ignore
                }
                if (_surface != null)
                    _surface.release();
            });
            captureSession = null;
            surface = null;
            cameraDevice = null;
        }
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
            int currentFrameRate = (int) Math.floor(1000000000L / streamConfigurationMap.getMinFrameDuration(size));
            VideoDimensions videoDimensions = new VideoDimensions(size.x, size.y);
            VideoFormat videoFormat = new VideoFormat(videoDimensions, currentFrameRate, VideoPixelFormat.RGBA_8888);
            videoFormats.add(videoFormat);
        }

        // Set up capture format
        // TODO
        // Refactor using VideoFormat
        width = sizes[sizes.length - 1].x;
        height = sizes[sizes.length - 1].y;
        frameRate = (int) Math.floor(1000000000L / streamConfigurationMap.getMinFrameDuration(sizes[sizes.length - 1]));

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

    private void checkIsOnCameraThread() {
        if(Thread.currentThread() != cameraThreadHandler.getLooper().getThread()) {
            Log.e(TAG, "Check is on camera thread failed.");
            throw new RuntimeException("Not on camera thread.");
        }
    }

    private final CameraDevice.Listener cameraListener = new CameraDevice.Listener() {
        @Override
        public void onOpened(CameraDevice device) {
            checkIsOnCameraThread();
            synchronized (stateLock) {
                cameraDevice = device;

                List<Surface> outputs = new ArrayList<>();

                Log.i(TAG, "Attempting create capture session");


                // Set the desired camera resolution
                surfaceTexture.setDefaultBufferSize(width, height);

                // Create the capture session
                captureSession = null;
                outputs = new ArrayList<>();
                outputs.add(surface);
                // TODO
                // Currently here
                /*
                https://github.com/wizzeye/wizzeye/blob/master/app/src/main/java/app/wizzeye/app/service/IristickCapturer.java
                https://github.com/twilio/video-quickstart-android/blob/master/exampleCustomVideoCapturer/src/main/java/com/twilio/video/examples/customcapturer/ViewCapturer.java
                package com.twilio.video.Camera2Capturer
                package com.iristick.smartglass.examples.camera;
                 */
                cameraDevice.createCaptureSession(outputs, captureSessionListener, cameraThreadHandler);
            }
        }

        @Override
        public void onClosed(CameraDevice device) {}

        @Override
        public void onDisconnected(CameraDevice device) {
            checkIsOnCameraThread();
            synchronized (stateLock) {
                if (cameraDevice == device || cameraDevice == null)
                    Log.i(TAG, "Disconnected");
                else
                    Log.w(TAG, "onDisconnected from another CameraDevice");
            }
        }

        @Override
        public void onError(CameraDevice device, int error) {
            checkIsOnCameraThread();
            synchronized (stateLock) {
                if (cameraDevice == device || cameraDevice == null)
                    Log.i(TAG, "Camera device error");
                else
                    Log.w(TAG, "onError from another CameraDevice");
            }
        }
    };

    private final CaptureSession.Listener captureSessionListener = new CaptureSession.Listener() {
        @Override
        public void onConfigured(CaptureSession session) {
            captureSession = session;
            Log.i(TAG, "Capture session configured");
            // setCapture();
        }

        @Override
        public void onConfigureFailed(CaptureSession session) {
            Log.i(TAG, "Capture session config error");
        }

        @Override
        public void onClosed(CaptureSession session) {
            if (captureSession == session)
                captureSession = null;
        }

        @Override
        public void onActive(CaptureSession session) {
        }

        @Override
        public void onCaptureQueueEmpty(CaptureSession session) {
        }

        @Override
        public void onReady(CaptureSession session) {
        }
    };
}