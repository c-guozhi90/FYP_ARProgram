package chenyue.arfyp.common.informationUtil;

import org.json.JSONArray;
import org.json.JSONException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class NetworkEnquiry extends Thread  {
    private String facilityName; // the facility name that users are interested in
    private InformationManager informationSetter;
    private int lines; // lines of text

    public NetworkEnquiry(String facilityName, InformationManager informationSetter) {
        this.facilityName = facilityName;
        this.informationSetter = informationSetter;
    }

    @Override
    public void run() {
        String target = "https://fypserverentry.herokuapp.com/api/events/" + this.facilityName;
        String information = null;
        String temp;
        try {
            URL url = new URL(target);
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
            informationSetter.setInformationArray(returnedJSONArray);

        } catch (IOException | JSONException e) {
            e.printStackTrace();
            informationSetter.setInformationArray("information enquiry failed");
        }
    }
}
