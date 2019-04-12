package chenyue.arfyp.navigation;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Pair;

import com.chenyue.tensorflowdetection.MultiBoxTracker;
import com.chenyue.tensorflowdetection.TensorflowUtils;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Pose;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import chenyue.arfyp.common.informationUtil.InformationManager;
import chenyue.arfyp.helloar.MainActivity;

public class DistanceEstimation extends Thread implements SensorEventListener {
    private static float[] EulerDegrees = new float[3];
    private double cameraCoordinates[];    // this is for arcore coordinates of camera. Not the coordinates in floor plan.
    private double currentPosition[];   // first for x, second for y, third for z. But z is the number of floor
    private double cameraIntrinsics[];
    private MultiBoxTracker tracker;
    private HashMap<String, DetailedObject> detailedObjectList;
    private Camera camera = null;
    private CameraIntrinsics intrinsics;
    private float[] magneticFieldValues;
    private float[] accelerometerValues;

    // not essential
    public void updateCameraParams(Camera camera) {
        if (this.camera == null)
            this.camera = camera;
        intrinsics = camera.getImageIntrinsics();
    }


    private class DetailedObject {
        InformationManager informationSet;
        RectF location;
        int estimationCounts;
        double distance[];
        double averageDistance;
    }

    public DistanceEstimation(MultiBoxTracker tracker) {
        if (this.tracker == null)
            this.tracker = tracker;
        if (detailedObjectList == null)
            detailedObjectList = new HashMap<>(100);

    }

    // in this function, we use the default size of ARCore Image(640*480) to estimate the distance
    public double estimateDistance(DetailedObject object) {
        double realHeight;
        //double objectFacing;
        synchronized (object.informationSet.getFacilityDetails()) {
            realHeight = Double.parseDouble(object.informationSet.getFacilityDetails().get("realHeight"));
            //objectFacing = Double.parseDouble((object.informationSet.getFacilityDetails().get("objectFacing")));
        }

        RectF location = object.location;
        int frameSize[] = intrinsics.getImageDimensions(); // width, height order
        float O[] = intrinsics.getPrincipalPoint();

        // fx is approximately equal to fy. fy is bigger.
        // the fx=F*sx, where sx is the num of pixels per millimeter in width of an image. so it is easy to conclude that the focal length
        // is measured by millimeter
        float focalLength[] = intrinsics.getFocalLength();
        double nearestOG = realHeight * focalLength[1] / location.height();
        double angle_oag = Math.atan(focalLength[1] / location.height() / 2);
        double nearestOD = (nearestOG / Math.sin(angle_oag) - realHeight * Math.sin(EulerDegrees[1])) * Math.sin(angle_oag + EulerDegrees[1]);
        //double orientedDifference = Math.abs(-objectFacing - EulerDegrees[0]);
        // calculate the OD in convenient way(lose accuracy). if the camera dose not facing the object, the error will increase.
        double calibratedOD = nearestOD / Math.sin(Math.atan(focalLength[0]/Math.abs(location.centerX()-O[0])));

        // for further work, some concepts must understand. 1. Pose(ARCore) 2. fireBase
        return calibratedOD;
    }

    public void run() {
        // Because the network inquiry is time consuming, it must start a new sub-thread for every detected object.
        // But here, a new sub-thread will be started automatically within the constructor of informationManager.
        List<Pair<RectF, String>> qualifiedTrackedObjects = null;
        while (true) {  // here, may set a boolean variable
            // 1. get qualified detected object(not near the edge) from tracker in sync manner
            synchronized (tracker.getTrackedObjects()) {
                while (qualifiedTrackedObjects == null || qualifiedTrackedObjects.size() == 0) {
                    qualifiedTrackedObjects = tracker.getItemInTrackedObjects();
                    if (qualifiedTrackedObjects == null || qualifiedTrackedObjects.size() == 0)
                        try {
                            tracker.getTrackedObjects().wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                }
            }
            int inquiryCount = 0;
            // 2. for every new detected object, start an information inquiry, and added into a hashMap
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
                }
            }
            // 3. wait until all information are retrieved. decrease inquiryCount by one when one thread is finished.
            // remove useless objects if any.
            while (inquiryCount > 0) {
                for (String key : detailedObjectList.keySet()) {
                    String realHeight;
                    do {
                        synchronized (detailedObjectList.get(key).informationSet.getFacilityDetails()) {
                            realHeight = detailedObjectList.get(key).informationSet.getFacilityDetails().get("realHeight");
                            try {
                                detailedObjectList.get(key).informationSet.getFacilityDetails().wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    } while (realHeight == null);
                    inquiryCount--;
                    if (!qualifiedTrackedObjects.contains(key)) {
                        detailedObjectList.remove(key); // remove those objects that are out of tracking
                    }
                }
            }
            // 4. start estimating distance. estimate five times and then calculate its average value
            for (String key : detailedObjectList.keySet()) {
                DetailedObject detailedObject = detailedObjectList.get(key);
                if (detailedObject.estimationCounts >= 5) {
                    Arrays.sort(detailedObject.distance);
                    detailedObject.averageDistance = (detailedObject.distance[0] + detailedObject.distance[1] + detailedObject.distance[2]) / 3;
                    continue;
                }
                detailedObject.distance[detailedObject.estimationCounts] = estimateDistance(detailedObject);
                detailedObject.estimationCounts++;
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
        float RMtx[] = new float[9];
        SensorManager.getRotationMatrix(RMtx, null, accelerometerValues, magneticFieldValues);
        SensorManager.getOrientation(RMtx, DistanceEstimation.EulerDegrees);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}



