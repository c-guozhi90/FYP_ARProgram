/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chenyue.arfyp.userviews;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.chenyue.tensorflowdetection.Classifier;
import com.chenyue.tensorflowdetection.Logger;
import com.chenyue.tensorflowdetection.MultiBoxTracker;
import com.chenyue.tensorflowdetection.TensorFlowObjectDetectionAPIModel;
import com.chenyue.tensorflowdetection.TensorflowUtils;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import chenyue.arfyp.common.helpers.CameraPermissionHelper;
import chenyue.arfyp.common.helpers.DisplayRotationHelper;
import chenyue.arfyp.common.helpers.FullScreenHelper;
import chenyue.arfyp.common.helpers.SnackbarHelper;
import chenyue.arfyp.common.helpers.TapHelper;
import chenyue.arfyp.common.informationUtil.InformationManager;
import chenyue.arfyp.common.rendering.BackgroundRenderer;
import chenyue.arfyp.common.rendering.PlaneRenderer;
import chenyue.arfyp.common.rendering.TextRenderer;
import chenyue.arfyp.navigation.CoordsCalculation;
import chenyue.arfyp.navigation.DistanceEstimation;
import chenyue.arfyp.navigation.Navigation;

import static com.google.ar.core.TrackingState.TRACKING;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3d model of the Android robot.
 */
public class MainActivity extends AppCompatActivity implements GLSurfaceView.Renderer, Serializable {
    private static boolean READY_FOR_NEXT_FRAME = true;
    public static boolean NAVIGATION_MODE = false;
    private static final String TAG = MainActivity.class.getSimpleName();
    public final Context context = this;

    // Views
    private Activity mainActivity;
    private GLSurfaceView glView;
    private trackingOverlay trackingView;
    private MapOverlay mapView;
    private View controlView;
    private Button search_button;
    private Button quit_navigation;
    private Button map_button;

    private boolean installRequested;

    private Session session;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private TapHelper tapHelper;

    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final TextRenderer textRenderer = new TextRenderer();

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] anchorMatrix = new float[16];
    private static final float[] DEFAULT_COLOR = new float[]{0f, 0f, 0f, 0f};
    private boolean useSingleImage = false;
    private boolean shouldConfigureSession = false;
    private Collection<AugmentedImage> updatedAugmentedImage;
    private Map<Integer, Pair<AugmentedImage, InformationManager>> augmentedImageMap = new HashMap<>();

    // Tensorflow related
    private static final String MODEL_PATH = "file:///android_asset/tensorflow_models/frozen_inference_graph.pb";
    private static final String LABELS_PATH = "file:///android_asset/tensorflow_models/fyp_labels_list.txt";
    private Classifier detector;
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;
    private static final int TF_INPUT_SIZE = 300;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private MultiBoxTracker tracker;
    private Runnable detection = null;
    private CameraCharacteristics cameraCharacteristics = null;

    // navigation related
    private DistanceEstimation estimator;
    public Thread estimatorThread;
    private CoordsCalculation coordsTracker;
    public Thread coordsTrackerThread;
    public Thread drawMapThread;
    public Navigation navigator;
    public Thread navigationThread;
    private SensorManager sensorManager;
    private Sensor gSensor;
    private Sensor aSensor;
    public static int glScreenHeight;
    public static int glScreenWidth;

    public static boolean requireDistanceEstimation = true; // distance estimator will check it before task

    // Anchors created from taps used for object placing with a given color.
    private static class ColoredAnchor {
        public final Anchor anchor;
        public final float[] color;

        public ColoredAnchor(Anchor a, float[] color4f) {
            this.anchor = a;
            this.color = color4f;
        }
    }

    private final ArrayList<ColoredAnchor> anchors = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainActivity = this;
        glView = findViewById(R.id.surface_view);
        mapView = findViewById(R.id.map_view);


        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

        // Set up tap listener.
        tapHelper = new TapHelper(/*context=*/ this);
        glView.setOnTouchListener(tapHelper);

        // Set up renderer.
        glView.setPreserveEGLContextOnPause(true);
        glView.setEGLContextClientVersion(2);
        glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        glView.setRenderer(this);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        glView.setWillNotDraw(false);

        // Set up tracking related stuff
        tracker = new MultiBoxTracker(this);
        estimator = new DistanceEstimation(this, this, tracker);
        coordsTracker = new CoordsCalculation(this);
        navigator = new Navigation(this);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        gSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        aSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        trackingView = findViewById(R.id.tracking_view);

        // set up navigation related stuff

        //sensorManager.registerListener(estimator, aSensor, SensorManager.SENSOR_DELAY_GAME);
        //sensorManager.registerListener(estimator, gSensor, SensorManager.SENSOR_DELAY_GAME);
        search_button = findViewById(R.id.search_button);
        quit_navigation = findViewById(R.id.quit_navigation);
        map_button = findViewById(R.id.map_button);


        search_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // start an activity for searching facilities
                Intent intent = new Intent(context, SearchActivity.class);
                estimatorThread = new Thread(estimator);
                coordsTrackerThread = new Thread(coordsTracker);
                drawMapThread = new Thread(mapView);
                startActivityForResult(intent, 333);
            }
        });
        quit_navigation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                quitNavigation();
            }
        });
        map_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mapView.setVisibility(View.VISIBLE);
                mapView.setClickable(true);
            }
        });
        installRequested = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(estimator, aSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(estimator, gSensor, SensorManager.SENSOR_DELAY_NORMAL);
        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                // Create the session.
                session = new Session(/* context= */ this);

            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

            if (message != null) {
                messageSnackbarHelper.showError(this, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
            shouldConfigureSession = true;

        }
        if (shouldConfigureSession) {
            configureSession();
            shouldConfigureSession = false;
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            // In some cases (such as another camera app launching) the camera may be given to
            // a different app instead. Handle this properly by showing a message and recreate the
            // session at the next iteration.
            messageSnackbarHelper.showError(this, "Camera not available. Please restart the app.");
            session = null;
            return;
        }
        estimator.setRequireEstimation(true);
        glView.onResume();
        displayRotationHelper.onResume();
        if (coordsTrackerThread != null && coordsTrackerThread.isAlive()) {

        }

    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            glView.onPause();
            session.pause();
        }
        sensorManager.unregisterListener(estimator);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(/*context=*/ this);
            textRenderer.createOnTread(/*context*/ this);
            // set up tensorflow detector and tracker
            detector = TensorFlowObjectDetectionAPIModel.create(this.getAssets(), MODEL_PATH, LABELS_PATH, TF_INPUT_SIZE);
            trackingView.addDrawCallback(new trackingOverlay.DrawCallback() {
                @Override
                public void draw(final Canvas canvas) {
                    tracker.draw(canvas);
                    // don't draw debug view
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        //GLES20.glViewport(0, 0, width, height);
        //Log.d(TAG, "width: " + width + "height: " + height);
        glScreenWidth = width;
        glScreenHeight = height;
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (session == null) {
            return;
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session);

        try {
            session.setCameraTextureName(backgroundRenderer.getTextureId());

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            Frame frame = session.update();
            Camera camera = frame.getCamera();
            CameraConfig cameraConfig = session.getCameraConfig();
            String cameraId = cameraConfig.getCameraId();
            final CameraManager manager = (CameraManager) mainActivity.getSystemService(Context.CAMERA_SERVICE);
            cameraCharacteristics = manager.getCameraCharacteristics(cameraId);

            // Handle one tap per frame.
            //handleTap(frame, camera);

            // Draw background.
            backgroundRenderer.draw(frame);

            // If not tracking, don't draw 3d objects.
            if (camera.getTrackingState() == TrackingState.PAUSED) {
                return;
            }

            // Get projection matrix.
            float[] projmtx = new float[16];
            camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

            // Get camera matrix and draw.
            float[] viewmtx = new float[16];
            camera.getViewMatrix(viewmtx, 0);

            // Compute lighting from average intensity of the image.
            // The first three components are color scaling factors.
            // The last one is the average pixel intensity in gamma space.
            final float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

            // Check if we detected at least one plane. If so, hide the loading message.
            if (messageSnackbarHelper.isShowing()) {
                for (Plane plane : session.getAllTrackables(Plane.class)) {
                    if (plane.getTrackingState() == TRACKING) {
                        messageSnackbarHelper.hide(this);
                        break;
                    }
                }
            }

            /* augmented image start */
            updatedAugmentedImage = frame.getUpdatedTrackables(AugmentedImage.class);
            for (AugmentedImage augmentedImage : updatedAugmentedImage) {
                switch (augmentedImage.getTrackingState()) {
                    case PAUSED:
                        // When an image is in PAUSED state, but the camera is not PAUSED, it has been detected,
                        // but not yet tracked.
                        String text = String.format("Detected Image %d", augmentedImage.getIndex());
                        messageSnackbarHelper.showMessage(this, text);
                        break;

                    case TRACKING:
                        // Create a new anchor for newly found images.
                        if (!augmentedImageMap.containsKey(augmentedImage.getIndex())) {
                            String facility_name = augmentedImage.getName();
                            facility_name = facility_name.substring(0, facility_name.indexOf('.'));
                            InformationManager info = new InformationManager(facility_name, "events");
                            augmentedImageMap.put(
                                    augmentedImage.getIndex(), Pair.create(augmentedImage, info));
                        }
                        break;

                    case STOPPED:
                        augmentedImageMap.remove(augmentedImage.getIndex());
                        break;

                    default:
                        break;
                }

            }

            for (Pair<AugmentedImage, InformationManager> pair : augmentedImageMap.values()) {
                AugmentedImage augmentedImage = pair.first;
                InformationManager informationManager = pair.second;
                if (augmentedImage.getTrackingState() == TRACKING) {
                    augmentedImage.getCenterPose().toMatrix(anchorMatrix, 0);
                    textRenderer.updateModelMatrix(anchorMatrix, 1.0f);
                    textRenderer.draw(viewmtx, projmtx, informationManager);
                }
            }
            /* augmented image end */

            /* tensorflow detection start */

            //Here should be asymmetric detection.
            if (READY_FOR_NEXT_FRAME == true) {
                Image image = frame.acquireCameraImage();
                detection = tensorflowThread(image);
                new Thread(detection).start();
            }

            //HERE STILL NEEDS A CHARACTER RECOGNITION PROCESS

            /* tensorflow detection end */

            /* distance estimation start*/
            estimator.updateCameraParams(camera);
            /* distance estimation end*/

        } catch (NullPointerException npe) {
            Log.e(TAG, "something wrong!");
        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    static int count = 9;

    // we have to define tensorflowThread in MainAcitivity because some variables like tracker can only be accessed within this class.
    private Runnable tensorflowThread(Image image) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                READY_FOR_NEXT_FRAME = false;
                if (cameraCharacteristics == null) return;
                int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) - TensorflowUtils.getScreenOrientation(mainActivity);
                Logger logger = new Logger(TAG);
                int cropedHeight = 480, cropedWidth = 640, startPoint = 0;
                if (sensorOrientation == 90) {
                    cropedHeight = (int) (1080f / 2138f * 640f);
                    startPoint = (480 - cropedHeight) / 2;
                }

                Size size = new Size(cropedWidth, cropedHeight);

                int[] rgbBytes = new int[image.getWidth() * image.getHeight()];
                TensorflowUtils.convert(image.getPlanes(), new Size(image.getWidth(), image.getHeight()), rgbBytes);

                frameToCropTransform = TensorflowUtils.getTransformationMatrix(
                        size.getWidth(), size.getHeight(), TF_INPUT_SIZE, TF_INPUT_SIZE, sensorOrientation, false);
                cropToFrameTransform = new Matrix();
                frameToCropTransform.invert(cropToFrameTransform);

                // the bug can be fixed if the width is changed to 360, use another createbitmap method to do this
                Bitmap originBitmap = Bitmap.createBitmap(
                        rgbBytes, image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
                Bitmap croppedOrigin = Bitmap.createBitmap(originBitmap, 0, startPoint, size.getWidth(), size.getHeight());
                if (count < 10)
                    TensorflowUtils.saveBitmap(context, croppedOrigin, "originPreview.png");

                Bitmap croppedFrame = Bitmap.createBitmap(
                        croppedOrigin, 0, 0, croppedOrigin.getWidth(), croppedOrigin.getHeight(), frameToCropTransform, false);

                Canvas canvas = new Canvas(croppedFrame);
                Paint paint = new Paint();
                paint.setColor(Color.BLUE);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.0f);
                if (count < 10)
                    TensorflowUtils.saveBitmap(context, croppedFrame, "croppedPreview.png");
                final List<Classifier.Recognition> mappedRecognitions = new LinkedList<>(); // record those qualified results
                List<Classifier.Recognition> objectList = detector.recognizeImage(croppedFrame);
                for (Classifier.Recognition recognizedObject : objectList) {
                    final RectF location = recognizedObject.getLocation();
                    if (recognizedObject.getConfidence() >= MINIMUM_CONFIDENCE_TF_OD_API && location != null) {
                        Log.d(TAG, "title: " + recognizedObject.getTitle() + " with confidence " + recognizedObject.getConfidence());
                        //logger.d("location before transform: x %f, y %f", location.left, location.top);
                        canvas.drawRect(location, paint);
                        cropToFrameTransform.mapRect(location);
                        recognizedObject.setLocation(location);
                        //logger.d("location after transform: x %f, y %f", location.left, location.right);
                        mappedRecognitions.add(recognizedObject); // the recognized frame with predefined size from ARCore
                    }
                }
                if (count < 10)
                    TensorflowUtils.saveBitmap(context, croppedFrame, "afterDraw.png");

                //add detected objects into result set, set proper parameters for tracker
                tracker.setFrameWidth(cropedWidth);
                tracker.setFrameHeight(cropedHeight);
                tracker.setSensorOrientation(sensorOrientation);
                tracker.trackResults(mappedRecognitions);   // we can notify the distance estimator after updating the trackedResults
                logger.d("size of collection %d", mappedRecognitions.size());
                // draw it
                trackingView.refreshView();
                originBitmap.recycle();
                croppedFrame.recycle();
                croppedOrigin.recycle();
                image.close();
                READY_FOR_NEXT_FRAME = true;
                count++;
            }
        };
        return runnable;
    }

    private void configureSession() {
        Config config = new Config(session);
        if (!setupAugmentedImageDatabase(config)) {
            messageSnackbarHelper.showError(this, "Could not setup augmented image database");
        }
        config.setFocusMode(Config.FocusMode.AUTO);
        session.configure(config);
    }

    private boolean setupAugmentedImageDatabase(Config config) {
        AugmentedImageDatabase augmentedImageDatabase;

        // There are two ways to configure an AugmentedImageDatabase:
        // 1. Add Bitmap to DB directly
        // 2. Load a pre-built AugmentedImageDatabase
        // Option 2) has
        // * shorter setup time
        // * doesn't require images to be packaged in apk.
        if (useSingleImage) {
            Bitmap augmentedImageBitmap = loadAugmentedImageBitmap();
            if (augmentedImageBitmap == null) {
                return false;
            }

            augmentedImageDatabase = new AugmentedImageDatabase(session);
            augmentedImageDatabase.addImage("image_name", augmentedImageBitmap);
            // If the physical size of the image is known, you can instead use:
            //     augmentedImageDatabase.addImage("image_name", augmentedImageBitmap, widthInMeters);
            // This will improve the initial detection speed. ARCore will still actively estimate the
            // physical size of the image as it is viewed from multiple viewpoints.
        } else {
            // This is an alternative way to initialize an AugmentedImageDatabase instance,
            // load a pre-existing augmented image database.
            try (InputStream is = getAssets().open("marker_database.imgdb")) {
                augmentedImageDatabase = AugmentedImageDatabase.deserialize(session, is);
            } catch (IOException e) {
                Log.e(TAG, "IO exception loading augmented image database.", e);
                return false;
            }
        }

        config.setAugmentedImageDatabase(augmentedImageDatabase);
        return true;
    }

    private Bitmap loadAugmentedImageBitmap() {
        try (InputStream is = getAssets().open("default.jpg")) {
            return BitmapFactory.decodeStream(is);
        } catch (IOException e) {
            Log.e(TAG, "IO exception loading augmented image bitmap.", e);
        }
        return null;
    }


    // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
    private void handleTap(Frame frame, Camera camera) {
        MotionEvent tap = tapHelper.poll();
        if (tap != null && camera.getTrackingState() == TRACKING) {
            for (HitResult hit : frame.hitTest(tap)) {
                // Check if any plane was hit, and if it was hit inside the plane polygon
                Trackable trackable = hit.getTrackable();
                // Creates an anchor if a plane or an oriented point was hit.
                if ((trackable instanceof AugmentedImage
                        && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                        && (PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0))
                        || (trackable instanceof Point
                        && ((Point) trackable).getOrientationMode()
                        == OrientationMode.ESTIMATED_SURFACE_NORMAL)) {
                    // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
                    // Cap the number of objects created. This avoids overloading both the
                    // rendering system and ARCore.
                    if (anchors.size() >= 20) {
                        anchors.get(0).anchor.detach();
                        anchors.remove(0);
                    }

                    // Assign a color to the object for rendering based on the trackable type
                    // this anchor attached to. For AR_TRACKABLE_POINT, it's blue color, and
                    // for AR_TRACKABLE_PLANE, it's green color.
                    float[] objColor;
                    if (trackable instanceof Point) {
                        objColor = new float[]{66.0f, 133.0f, 244.0f, 255.0f};
                    } else if (trackable instanceof Plane) {
                        objColor = new float[]{139.0f, 195.0f, 74.0f, 255.0f};
                    } else {
                        objColor = DEFAULT_COLOR;
                    }

                    // Adding an Anchor tells ARCore that it should track this position in
                    // space. This anchor is created on the Plane to place the 3D model
                    // in the correct position relative both to the world and to the plane.
                    anchors.add(new ColoredAnchor(hit.createAnchor(), objColor));
                    break;
                }
            }
        }
    }

    private void quitNavigation() {
        NAVIGATION_MODE = false;
        estimatorThread.interrupt();
        coordsTrackerThread.interrupt();
        drawMapThread.interrupt();
        quit_navigation.setVisibility(View.INVISIBLE);
        map_button.setVisibility(View.INVISIBLE);
        search_button.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == SearchActivity.START_FOR_NAVIGATION) {
            if (coordsTrackerThread != null) coordsTrackerThread.start();
            if (estimatorThread != null) estimatorThread.start();
            if (drawMapThread != null) drawMapThread.start();
            quit_navigation.setVisibility(View.VISIBLE);
            search_button.setVisibility(View.INVISIBLE);
            // map button visibility is controlled by estimator
        }
    }

}
