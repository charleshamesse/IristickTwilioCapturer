package com.rma.mwmw.iristicktwiliocapturer.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;

import com.iristick.smartglass.core.Headset;
import com.iristick.smartglass.core.camera.CameraCharacteristics;
import com.iristick.smartglass.core.camera.CameraDevice;
import com.iristick.smartglass.core.camera.CaptureFailure;
import com.iristick.smartglass.core.camera.CaptureListener;
import com.iristick.smartglass.core.camera.CaptureRequest;
import com.iristick.smartglass.core.camera.CaptureResult;
import com.iristick.smartglass.core.camera.CaptureSession;
import com.twilio.video.VideoCapturer;
import com.twilio.video.VideoDimensions;
import com.twilio.video.VideoFormat;
import com.twilio.video.VideoPixelFormat;
import com.twilio.video.VideoFrame;

import org.webrtc.EglBase;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSink;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    private Handler cameraThreadHandler;// = new Handler(Looper.getMainLooper());
    private HandlerThread cameraThread;
    private VideoCapturer.Listener videoCapturerListener;

    private ImageReader imageReader;


    public IristickTwilioCapturer(
            @NonNull Context context,
            @NonNull String cameraId,
            @NonNull Headset headset,
            @NonNull IristickTwilioCapturer.Listener listener,
            @NonNull EglBase.Context sharedContext) {
        this.applicationContext = context.getApplicationContext();
        this.cameraId = cameraId;
        this.listener = listener;
        this.headset = headset;
        this.cameraNames = headset.getCameraIdList();


        surfaceHelper = SurfaceTextureHelper.create("SurfaceTextureHelper", sharedContext);
        cameraThreadHandler = surfaceHelper.getHandler();
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

        if(surfaceHelper == null) {
            Log.i(TAG, "waiting for surface helper");
        }
        else {
            synchronized (stateLock) {
                this.videoCapturerListener = videoCapturerListener;
                if (sessionOpening || captureSession != null) {
                    Log.w(TAG, "Capture already started");
                    return;
                }

                openCamera(true);
            }
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
        // TODO: Refactor using VideoFormat
        width = sizes[sizes.length - 1].x;
        height = sizes[sizes.length - 1].y;
        frameRate = (int) Math.floor(1000000000L / streamConfigurationMap.getMinFrameDuration(sizes[sizes.length - 1]));


        imageReader = ImageReader.newInstance(width, height,
                ImageFormat.JPEG, 2);
        imageReader.setOnImageAvailableListener(imageReaderListener, cameraThreadHandler);
        return videoFormats;
    }

    /** Indicates that the capturer is not a screen cast. */
    @Override
    public boolean isScreencast() {
        return false;
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
                surfaceTexture = surfaceHelper.getSurfaceTexture();
                surfaceTexture.setDefaultBufferSize(width, height);
                surface = new Surface(surfaceTexture);

                // Create the capture session
                captureSession = null;
                outputs = new ArrayList<>();
                //outputs.add(surface);
                outputs.add(imageReader.getSurface());
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

    private final ImageReader.OnImageAvailableListener imageReaderListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.d(TAG, "onImageAvailable");
            try (final Image image = reader.acquireLatestImage()) {
                if (image == null) {
                    Log.i(TAG, "No image available in callback");
                    return;
                }
                Log.i(TAG, "image available in callback" + image.getFormat());

                ByteBuffer _buffer = image.getPlanes()[0].getBuffer();
                byte[] _bytes = new byte[_buffer.capacity()];
                _buffer.get(_bytes);
                Bitmap viewBitmap = BitmapFactory.decodeByteArray(_bytes, 0, _bytes.length, null);

                // Extract the frame from the bitmap
                int bytes = viewBitmap.getByteCount();
                ByteBuffer buffer = ByteBuffer.allocate(bytes);
                viewBitmap.copyPixelsToBuffer(buffer);

                byte[] array = buffer.array();
                final long captureTimeNs =
                        TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());

                // Create video frame
                VideoDimensions dimensions = new VideoDimensions(width, height);
                VideoFrame videoFrame = new VideoFrame(array,
                        dimensions, VideoFrame.RotationAngle.ROTATION_0, captureTimeNs);

                videoCapturerListener.onFrameCaptured(videoFrame);
                //image.getPlanes()


                /*
                File file = new File(dir, PICTURE_FILENAME.format(new Date()));
                try (OutputStream os = new FileOutputStream(file)) {
                    Channels.newChannel(os).write(image.getPlanes()[0].getBuffer());
                } catch (IOException e) {
                    Log.e(TAG, "Failed to write capture to " + file.getPath(), e);
                    Toast.makeText(mContext, R.string.call_toast_picture_fail, Toast.LENGTH_SHORT).show();
                    return;
                }
                MediaScannerConnection.scanFile(mContext, new String[] { file.toString() }, null, null);
                Toast.makeText(mContext, R.string.call_toast_picture_taken, Toast.LENGTH_SHORT).show();
                */
            }
        }
    };

    private SurfaceTexture.OnFrameAvailableListener onFrameAvailableListener = new SurfaceTexture.OnFrameAvailableListener() {
        @Override
        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
            Log.i(TAG, "frame available");
        }
    };

    private final CaptureSession.Listener captureSessionListener = new CaptureSession.Listener() {
        @Override
        public void onConfigured(CaptureSession session) {
            // setCapture();
            checkIsOnCameraThread();
            synchronized (stateLock) {
                Log.i(TAG, "Capture session configured");
                captureSession = session;
                // TODO: set sink
                imageReader.setOnImageAvailableListener(imageReaderListener, cameraThreadHandler);
                //surfaceTexture.setOnFrameAvailableListener(onFrameAvailableListener);
                observerAdapter.onCapturerStarted(true);
                sessionOpening = false;
                firstFrameObserved = false;
                stateLock.notifyAll();

                applyParametersInternal();
            }
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

    private void applyParametersInternal() {
        checkIsOnCameraThread();
        synchronized (stateLock) {
            Log.i(TAG, "applyParametersInternal");
            if (sessionOpening || sessionStopping || captureSession == null)
                return;

            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(imageReader.getSurface());
            builder.set(CaptureRequest.SENSOR_FRAME_DURATION, 1000000000L / frameRate);
            setupCaptureRequest(builder);
            captureSession.setRepeatingRequest(builder.build(), null, null);

        }
    }

    private void setupCaptureRequest(CaptureRequest.Builder builder) {
        Log.i(TAG, "setupCaptureRequest");

        builder.set(CaptureRequest.SCALER_ZOOM, (float)(1 << Math.max(0, 2 - 1)));
        builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        builder.set(CaptureRequest.LASER_MODE, CaptureRequest.LASER_MODE_OFF);
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
    }


    private final org.webrtc.VideoCapturer.CapturerObserver observerAdapter =
            new org.webrtc.VideoCapturer.CapturerObserver() {
                @Override
                public void onCapturerStarted(boolean success) {
                    videoCapturerListener.onCapturerStarted(success);
                }

                @Override
                public void onCapturerStopped() {
                }

                @Override
                public void onFrameCaptured(org.webrtc.VideoFrame videoFrame) {
                    Log.i(TAG, "onFrameCaptured");
                    /*
                    org.webrtc.VideoFrame.Buffer buffer = videoFrame.getBuffer();
                    VideoDimensions dimensions =
                            new VideoDimensions(buffer.getWidth(), buffer.getHeight());
                    VideoFrame.RotationAngle orientation =
                            VideoFrame.RotationAngle.fromInt(videoFrame.getRotation());

                    videoCapturerListener.onFrameCaptured(
                            new VideoFrame(videoFrame, dimensions, orientation));
                            */
                }
            };

    public interface Listener {
        void onFirstFrameAvailable();
        void onError(@NonNull Exception exception);
    }

}