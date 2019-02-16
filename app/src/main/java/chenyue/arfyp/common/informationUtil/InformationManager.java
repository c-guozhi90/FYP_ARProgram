package chenyue.arfyp.common.informationUtil;

import org.json.JSONArray;

import java.util.ArrayList;

public class InformationManager {
    private String facilityName;
    private ArrayList<String> informationArray = new ArrayList<>();
    private boolean expended = false;

    public InformationManager(String facilityName) {
        this.informationArray.add(0, "connecting network...");
        this.facilityName = facilityName;
    }

    public void setInformationArray(JSONArray informationArray) {
        this.informationArray.clear();
        for (int idx = 0; idx < informationArray.length(); idx++) {
            this.informationArray.add(idx, informationArray.toString());
        }
    }

    public void setInformationArray(String message) {
        informationArray.clear();
        informationArray.add(0, message);
    }

    public ArrayList<String> getInformationArray() {
        return informationArray;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }

    public boolean isExpended() {
        return expended;
    }

    public void setExpended(boolean expended) {
        this.expended = expended;
    }
}
