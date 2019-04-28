package chenyue.arfyp.navigation;

import android.app.Activity;
import android.content.Context;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.nfc.Tag;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.chenyue.tensorflowdetection.MultiBoxTracker;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraIntrinsics;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chenyue.arfyp.common.informationUtil.InformationManager;
import chenyue.arfyp.userviews.MainActivity;
import chenyue.arfyp.userviews.R;

public class DistanceEstimation implements SensorEventListener, Runnable {
    public final static String TAG = "estimation";
    public static float[] EulerDegrees = new float[3];
    private MultiBoxTracker tracker;
    private HashMap<String, DetailedObject> detailedObjectList;
    private Camera camera = null;
    private float[] magneticFieldValues = new float[3];
    private float[] accelerometerValues = new float[3];
    private double orientationAngle;
    private boolean requireEstimation = true;
    private Context context;
    private Activity mainActivity;
    private Handler handler;

    // not essential
    public void updateCameraParams(Camera camera) {
        if (this.camera == null)
            this.camera = camera;
    }


    private class DetailedObject {
        InformationManager informationSet;
        RectF location;
        int estimationCounts;
        double distance[];
        double averageDistance;
    }

    public DistanceEstimation(Context context, Activity activity, MultiBoxTracker tracker) {
        if (this.tracker == null)
            this.tracker = tracker;
        if (detailedObjectList == null)
            detailedObjectList = new HashMap<>(100);
        this.context = context;
        this.mainActivity = activity;
        handler = new Handler();
    }

    static int times = 0;

    // in this function, we use the default size of ARCore Image(640*480) to estimate the distance
    public double estimateDistance(DetailedObject object) {
        double realHeight, objectFacing;
        //double objectFacing;
        realHeight = Double.parseDouble(object.informationSet.getFacilityDetails().get("realHeight"));
        objectFacing = Math.toRadians(Double.parseDouble((object.informationSet.getFacilityDetails().get("facing"))));

        RectF location = object.location;
        CameraIntrinsics intrinsics = camera.getImageIntrinsics();
        //int frameSize[] = intrinsics.getImageDimensions(); // width, height order
        float O[] = intrinsics.getPrincipalPoint();

        // fx is approximately equal to fy. fy is bigger.
        // the fx=F*sx, where sx is the num of pixels per millimeter in width of an image. so it is easy to conclude that the focal length
        // is measured by millimeter
        float focalLength[] = intrinsics.getFocalLength();
        double nearestOG = realHeight * focalLength[1] / location.height();
        double angle_oag = Math.atan(focalLength[1] / location.height() / 2);
        double nearestOD = (nearestOG / Math.sin(angle_oag) - realHeight * Math.sin(EulerDegrees[1] + 0.5 * Math.PI) / Math.sin(angle_oag)) * Math.sin(angle_oag + EulerDegrees[1] + 0.5 * Math.PI);
        //double orientedDifference = Math.abs(-objectFacing - EulerDegrees[0]);
        // calculate the OD in convenient way(lose accuracy). if the camera dose not facing the object, the error will increase.
        double calibratedOD = nearestOD / Math.sin(Math.atan(focalLength[0] / Math.abs(location.centerX() - O[0])));
        double BJ = nearestOD * 0.5 * location.width() / focalLength[0];
        //orientationAngle = adjustAngle(DistanceEstimation.EulerDegrees[0] + Math.atan2(location.centerX() - O[0], focalLength[0]));
        orientationAngle = adjustAngle(objectFacing - Math.PI);
        double angle_JKB = Math.abs(adjustAngle(objectFacing + 0.5 * Math.PI) - orientationAngle);
        double angle_kBJ = Math.abs(adjustAngle(DistanceEstimation.EulerDegrees[0] + 0.5 * Math.PI) - adjustAngle(objectFacing + 0.5 * Math.PI));
        double JK = BJ * Math.sin(angle_kBJ) / Math.sin(angle_JKB);
        /*log in file start*/
        try {
            String logPath = context.getExternalCacheDir().getAbsolutePath() + "/log.txt";
            Log.d(TAG, "file here: " + logPath);
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logPath, true)));
            bw.write("times " + ++times + "\n");
            bw.write("theta: " + Math.toDegrees(EulerDegrees[1] + 0.5 * Math.PI));
            bw.write("sin theta: " + Math.sin(EulerDegrees[1] + 0.5 * Math.PI));
            bw.write("focal length " + focalLength[1] + "\n");
            bw.write("location height " + location.height() + "\n");
            bw.write("orientation angle " + Math.toDegrees(orientationAngle) + "\n");
            bw.write("angle_oag " + Math.toDegrees(angle_oag) + "\n");
            bw.write("angle_JKB " + Math.toDegrees(angle_JKB) + "\n");
            bw.write("angle_KBJ " + Math.toDegrees(angle_kBJ) + "\n");
            bw.write("nearest OG " + nearestOG + "\n");
            bw.write("nearest OD " + nearestOD + "\n");
            bw.write("calibrated OD " + calibratedOD + "\n");
            bw.write("distance " + (calibratedOD + JK) + "\n");
            bw.flush();
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        /*log in file end*/
        return calibratedOD;
    }

    public void run() {
        // Because the network inquiry is time consuming, it must start a new sub-thread for every detected object.
        // But here, a new sub-thread will be started automatically within the constructor of informationManager.
        Log.d(TAG, "estimation started");
        List<Pair<RectF, String>> qualifiedTrackedObjects;
        while (requireEstimation) {  // here, may set a boolean variable
            // 1. get qualified detected object(not near the edge) from tracker in sync manner
            synchronized (tracker.getTrackedObjects()) {
                do {
                    qualifiedTrackedObjects = tracker.getItemInTrackedObjects();
                    if (qualifiedTrackedObjects == null || qualifiedTrackedObjects.size() == 0)
                        try {
                            tracker.getTrackedObjects().wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                } while (qualifiedTrackedObjects == null || qualifiedTrackedObjects.size() == 0);
            }
            Log.d(TAG, "start enquiry");

            int inquiryCount = 0;
            // 2. for every new detected object, start an information inquiry, and add into a hashMap.
            // if such object already exists in hashMap, update the its location if any.
            for (Pair<RectF, String> object : qualifiedTrackedObjects) {
                String objectName = object.second;
                if (!detailedObjectList.containsKey(objectName)) {
                    InformationManager newInformationSet = new InformationManager(objectName, "details");   // maybe all needed data could be packed into app package
                    DetailedObject newDetails = new DetailedObject();
                    newDetails.informationSet = newInformationSet;
                    newDetails.location = object.first;
                    newDetails.distance = new double[5];
                    newDetails.averageDistance = -1000;
                    newDetails.estimationCounts = 0;
                    detailedObjectList.put(objectName, newDetails);
                    inquiryCount++;
                } else {
                    // update the location in screen if any
                    detailedObjectList.get(objectName).location = object.first;
                }
            }
            // 3. wait until all information are retrieved. decrease inquiryCount by one when one thread is finished.
            // remove useless objects if any.
            Log.d(TAG, "wait for enquiry");

            while (inquiryCount > 0) {
                for (String key : detailedObjectList.keySet()) {
                    do {
                        synchronized (detailedObjectList.get(key).informationSet.getFacilityDetails()) {
                            try {
                                Map facilityDetails = detailedObjectList.get(key).informationSet.getFacilityDetails();
                                if (facilityDetails.get("realHeight") == null || facilityDetails.get("facing") == null)
                                    detailedObjectList.get(key).informationSet.getFacilityDetails().wait();
                                else
                                    break;
                            } catch (InterruptedException e) {

                                e.printStackTrace();
                            }
                        }
                    }
                    while (true);
                    inquiryCount--;
                }
            }
            // 4. start estimating distance. estimate five times and then calculate its average value
            Log.d(TAG, "estimate distance");

            for (String key : detailedObjectList.keySet()) {
                DetailedObject detailedObject = detailedObjectList.get(key);
                if (detailedObject == null) continue;
                if (detailedObject.estimationCounts >= 5) {
                    Arrays.sort(detailedObject.distance);
                    detailedObject.averageDistance = (detailedObject.distance[0] + detailedObject.distance[1] + detailedObject.distance[2]) / 3;
                    continue;
                }
                detailedObject.distance[detailedObject.estimationCounts] = estimateDistance(detailedObject);
                detailedObject.estimationCounts++;
            }
            // 5. calculate the initial average coordinates of user and start navigation.
            // place the calculation here to simplify multithreading cooperation
            int objectNum = 0;
            double[] sumCoords = {0, 0};
            if (detailedObjectList.size() == 0) continue;
            for (String key : detailedObjectList.keySet()) {
                if (!MainActivity.NAVIGATION_MODE) break;
                DetailedObject detailedObject = detailedObjectList.get(key);
                if (detailedObject == null || detailedObject.estimationCounts < 5)
                    continue;

                double[] objectCoords = new double[2];
                //objectCoords = (double[])detailedObject.informationSet.getFacilityDetails().get("coordinates"));
                objectCoords[0] = Double.parseDouble(detailedObject.informationSet.getFacilityDetails().get("coordsE"));
                objectCoords[1] = Double.parseDouble(detailedObject.informationSet.getFacilityDetails().get("coordsN"));
                double objectFacing = Double.parseDouble(detailedObject.informationSet.getFacilityDetails().get("facing"));
                sumCoords[0] = sumCoords[0] + objectCoords[0] + detailedObject.averageDistance * Math.sin(objectFacing);
                sumCoords[1] = sumCoords[1] + objectCoords[1] + detailedObject.averageDistance * Math.cos(objectFacing);
                objectNum++;
                CoordsCalculation.floor = Integer.parseInt(detailedObject.informationSet.getFacilityDetails().get("floor"));

                // when facing north, the angle is 0, when facing south, the angle will be 180, east is 90, west is -90
            }
            // after calculation for initCoordinates, it can start the navigation(by setting the readyForTracking with true)
            // Rotation about the y axis. that is because the initial coordinates system in ARCore for camera is special.
            // Its Y axis is pointing up, X axis is pointing to right, and Z axis is pointing to forward
            if (!CoordsCalculation.readyForTracking && objectNum > 0) {
                CoordsCalculation.prepareTracking(sumCoords[0] / objectNum, sumCoords[1] / objectNum, camera, orientationAngle);
                handler.post(() -> {
                            Toast.makeText(context, "your position information is available", Toast.LENGTH_SHORT).show();
                            Button mapButton = mainActivity.findViewById(R.id.map_button);
                            mapButton.setVisibility(View.VISIBLE);
                        }
                );
                requireEstimation = false;
                Log.d(TAG, "estimation end");
            }
            try {
                Thread.sleep(50); // cannot calculate too fast
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            magneticFieldValues = event.values;
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            accelerometerValues = event.values;
        calculateRotation();
    }

    private void calculateRotation() {
        float[] RMtx = new float[9];
        SensorManager.getRotationMatrix(RMtx, null, accelerometerValues, magneticFieldValues);
        SensorManager.getOrientation(RMtx, EulerDegrees);
        Log.d(TAG, "degree " + EulerDegrees[1]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public static double adjustAngle(double angle) {
        // adjust the angle so that it ranges from -PI to PI
        if (angle > Math.PI)
            return -2 * Math.PI + angle;
        else if (angle < -Math.PI)
            return angle + 2 * Math.PI;
        else return angle;
    }

    public void setRequireEstimation(boolean operation) {
        requireEstimation = operation;
    }
}



