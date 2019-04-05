package chenyue.arfyp.common.informationUtil;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class NetworkEnquiry extends Thread {
    private String facilityName; // the facility name that users are interested in
    private InformationManager informationManager;
    private String target;

    public NetworkEnquiry(String facilityName, String target, InformationManager informationManager) {
        this.facilityName = facilityName;
        this.informationManager = informationManager;
        this.target = target;
    }

    @Override
    public void run() {
        String enquiryAddress = "https://fypserverentry.herokuapp.com/api/" + target + "/" + this.facilityName;

        String information = "";
        String temp;
        try {
            URL url = new URL(enquiryAddress);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            InputStreamReader isr = new InputStreamReader(connection.getInputStream());
            BufferedReader br = new BufferedReader(isr);
            while ((temp = br.readLine()) != null) {
                information += temp;
            }
            br.close();
            isr.close();
            connection.disconnect();
            JSONArray returnedJSONArray = new JSONArray(information);
            if (target.equals("events"))
                synchronized (informationManager.getEventArray()) {
                    informationManager.setEventArray(returnedJSONArray);
                }
            else {
                synchronized (informationManager.getFacilityDetails()) {
                    informationManager.setFacilityDetails(returnedJSONArray);
                }
            }

        } catch (IOException | JSONException e) {
            e.printStackTrace();
            if (target.equals("events")) {
                synchronized (informationManager.getEventArray()) {
                    informationManager.setFailedMessage("information enquiry failed");
                }
            }
        }
    }
}
