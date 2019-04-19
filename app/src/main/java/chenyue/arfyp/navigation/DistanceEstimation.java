package chenyue.arfyp.navigation;

import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Pair;

import com.chenyue.tensorflowdetection.MultiBoxTracker;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraIntrinsics;

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
    private double orientationAngle;
    private boolean requiredEstimation = true;

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
        double realHeight, objectFacing;
        //double objectFacing;
        synchronized (object.informationSet.getFacilityDetails()) {
            realHeight = Double.parseDouble(object.informationSet.getFacilityDetails().get("realHeight"));
            objectFacing = Double.parseDouble((object.informationSet.getFacilityDetails().get("objectFacing")));
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
        double nearestOD = (nearestOG / Math.sin(angle_oag) - realHeight * Math.sin(EulerDegrees[1] + 90)) * Math.sin(angle_oag + EulerDegrees[1]);
        //double orientedDifference = Math.abs(-objectFacing - EulerDegrees[0]);
        // calculate the OD in convenient way(lose accuracy). if the camera dose not facing the object, the error will increase.
        double calibratedOD = nearestOD / Math.sin(Math.atan(focalLength[0] / Math.abs(location.centerX() - O[0])));
        double BJ = nearestOD * 0.5 * location.width() / focalLength[0];
        orientationAngle = adjustAngle(DistanceEstimation.EulerDegrees[0] + Math.atan(Math.abs(O[0] - location.centerX()) / focalLength[0]));
        double angle_JKB = Math.abs(adjustAngle(objectFacing + 0.5 * Math.PI) - orientationAngle);
        double angle_kBJ = Math.abs(adjustAngle(DistanceEstimation.EulerDegrees[0] + 0.5 * Math.PI) - adjustAngle(objectFacing + 0.5 * Math.PI));
        double JK = BJ * Math.sin(angle_kBJ) / Math.sin(angle_JKB);
        return calibratedOD + JK;
    }

    public void run() {
        // Because the network inquiry is time consuming, it must start a new sub-thread for every detected object.
        // But here, a new sub-thread will be started automatically within the constructor of informationManager.
        List<Pair<RectF, String>> qualifiedTrackedObjects = null;
        while (requiredEstimation) {  // here, may set a boolean variable
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
            // 2. for every new detected object, start an information inquiry, and add into a hashMap
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
                    if (!qualifiedTrackedObjects.contains(key)) {
                        detailedObjectList.remove(key); // remove those objects that are out of tracking
                        continue;
                    }
                    do {
                        synchronized (detailedObjectList.get(key).informationSet.getFacilityDetails()) {
                            try {
                                if (detailedObjectList.get(key).informationSet.getFacilityDetails() == null)
                                    detailedObjectList.get(key).informationSet.getFacilityDetails().wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    while (detailedObjectList.get(key).informationSet.getFacilityDetails() == null);
                    inquiryCount--;
                }
            }
            // 4. start estimating distance. estimate five times and then calculate its average value
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
            double[] sumCoords = new double[2];
            if (detailedObjectList.size() == 0) continue;
            for (String key : detailedObjectList.keySet()) {
                if (!MainActivity.NAVIGATION_MODE) break;
                DetailedObject detailedObject = detailedObjectList.get(key);
                if (detailedObject == null && detailedObject.averageDistance >= -1000d && detailedObject.averageDistance <= -1000)
                    continue;
                double[] objectCoords = new double[2];
                objectCoords[0] = Double.parseDouble(detailedObject.informationSet.getFacilityDetails().get("coordE"));
                objectCoords[1] = Double.parseDouble(detailedObject.informationSet.getFacilityDetails().get("coordN"));
                sumCoords[0] = sumCoords[0] + objectCoords[0] + detailedObject.averageDistance * Math.sin(adjustAngle(Math.PI - orientationAngle));
                sumCoords[1] = sumCoords[1] + objectCoords[1] + detailedObject.averageDistance * Math.cos(adjustAngle(Math.PI - orientationAngle));
                objectNum++;
                CoordsCalculation.floor = Integer.parseInt(detailedObject.informationSet.getFacilityDetails().get("floor"));

                // when facing north, the angle is 0, when facing south, the angle will be 180, east is 90, west is -90
            }
            // after calculation for initCoordinates, it can start the navigation(by setting the readyForTracking with true)
            // Rotation about the y axis. that is because the initial coordinates system in ARCore for camera is special.
            // Its Y axis is pointing up, X axis is pointing to right, and Z axis is pointing to forward
            CoordsCalculation.setRotationOffset(DistanceEstimation.EulerDegrees[0]);
            CoordsCalculation.initCoordinates[0] = sumCoords[0] / objectNum;
            CoordsCalculation.initCoordinates[1] = sumCoords[1] / objectNum;
            CoordsCalculation.readyForTracking = true;
            requiredEstimation = false;
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

    public static double adjustAngle(double angle) {
        if (angle > Math.PI)
            return angle - Math.PI;
        else if (angle < -Math.PI)
            return angle + Math.PI;
        else return angle;
    }
}



