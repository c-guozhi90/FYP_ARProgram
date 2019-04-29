package chenyue.arfyp.navigation;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.LinkedList;
import java.util.NoSuchElementException;

public class Navigation implements Runnable {
    private static String TAG = "navigationThread";
    public final static LinkedList<Node> path = new LinkedList<>();
    private static boolean START_NAVIGATION = false;
    public static double navigationAngle = 0;   // from -PI to PI
    public static boolean TARGET_REACHED = false;
    private final Context context;
    private double pointsDirection;
    private double distance;
    private Handler handler;

    public Navigation(Context context) {
        this.context = context;
        handler = new Handler();
    }

    public static void setPath(JSONArray jsonArray) throws JSONException {
        if (path.size() != 0) path.clear();
        for (int idx = 0; idx < jsonArray.length(); idx++) {
            Node newNode = new Node();
            JSONObject tempObject = jsonArray.getJSONObject(idx);
            newNode.nodeName = tempObject.getString("nodeName");
            newNode.floor = tempObject.getInt("floor");
            JSONArray tempArray = (JSONArray) tempObject.get("coordinates");
            newNode.coordinates[0] = (double) tempArray.get(0);
            newNode.coordinates[1] = (double) tempArray.get(1);
            path.add(newNode);
        }
    }

    public static void setStart(boolean toStart) {
        START_NAVIGATION = toStart;
    }

    public static float[] returnTargetCoordinates() {
        Node lastNode = path.getLast();
        float[] coords = {(float) lastNode.coordinates[0], (float) lastNode.coordinates[1]};
        return coords;
    }

    static class Node {
        String nodeName;
        double[] coordinates = new double[2];
        int floor;

    }

    public void calculateNavigationAngle() {
        Node aheadNode = path.getFirst();
        // point vector from current position to first path node, in building sys.
        pointsDirection = Math.atan2(aheadNode.coordinates[0] - CoordsCalculation.curPosition[0], aheadNode.coordinates[1] - CoordsCalculation.curPosition[1]);
        navigationAngle = DistanceEstimation.adjustAngle(pointsDirection - CoordsCalculation.curOrientationInBuildingSys);
    }

    public void run() {
        while (true) {
            if (!START_NAVIGATION) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }
            synchronized (path) {
                Node aheadNode = path.get(0);
                distance = DistanceEstimation.calculateDistance(CoordsCalculation.curPosition[0], CoordsCalculation.curPosition[1], aheadNode.coordinates[0], aheadNode.coordinates[1]);
                if (distance < 1) {
                    path.removeFirst();
                }
                if (path.size() == 0) {
                    TARGET_REACHED = true;
                    handler.post(() -> {
                        Toast.makeText(context, "target reached!", Toast.LENGTH_LONG).show();
                    });
                    break;
                }
                calculateNavigationAngle();
            }
            logDown();
        }
    }

    /**
     * log down the navigation related data for analysis
     * q
     */
    public void logDown() {
        String logPath = context.getExternalCacheDir().getAbsolutePath() + "/log-navigation.txt";
        Log.d(TAG, "log file here: " + logPath);
        try {
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logPath, true)));
            bw.write("current target node: " + path.getFirst().nodeName + "\n");
            bw.write("distance: " + distance + "\n");
            bw.write("navigation angle: " + navigationAngle + "\n");
            bw.write("points direction: " + pointsDirection + "\n");
            bw.flush();
            bw.close();
        } catch (IOException | NoSuchElementException e) {
            e.printStackTrace();
        }
    }

    public static boolean isTargetReached() {

        return false;
    }
}
