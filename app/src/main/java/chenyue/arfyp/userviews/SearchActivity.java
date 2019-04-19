package chenyue.arfyp.userviews;

import android.app.Activity;
import android.os.Bundle;
import android.util.JsonReader;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.util.Iterator;
import java.util.function.Consumer;

import chenyue.arfyp.navigation.CoordsCalculation;

public class SearchActivity extends Activity implements View.OnClickListener {
    private ListView searchListView;
    private EditText searchInput;
    private Button searchButton;
    private Button cancelButton;
    private Button goThereButton;
    private ArrayAdapter<String> searchResultsItems;
    private boolean EnquiryFinished = false;
    private Thread estimatorThread;
    private Thread coordsThread;
    private Thread drawMapThread;
    private Button quitNavigationBtn;
    private Button searchBtnInMain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        searchListView = findViewById(R.id.search_list_view);
        searchInput = findViewById(R.id.search_input);
        searchButton = findViewById(R.id.search_result_button);
        goThereButton = findViewById(R.id.go_there_button);
        cancelButton = cancelButton.findViewById(R.id.cancel_button);
        searchButton.setOnClickListener(this);
        cancelButton.setOnClickListener(this);
        goThereButton.setOnClickListener(this);
        String[] initList = {"no result"};
        searchResultsItems = new ArrayAdapter<>(this, R.layout.activity_search, initList);
        searchListView.setAdapter(searchResultsItems);
        searchListView.setOnClickListener(this);
        estimatorThread = (Thread) savedInstanceState.get("estimator_thread");
        coordsThread = (Thread) savedInstanceState.get("coords_tracker_thread");
        drawMapThread = (Thread) savedInstanceState.get("draw_map_thread");
        quitNavigationBtn = (Button) savedInstanceState.get("quit_navigation_button");
        searchBtnInMain = (Button) savedInstanceState.get("search_button");
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.search_result_button:
                searchFacilityOnline(String.valueOf(searchInput.getText()));
                showStatusText();
                break;
            case R.id.cancel_button:
                this.finish();
                break;
            case R.id.go_there_button:
                startNavigation(searchListView.getSelectedItemPosition());
                finish();
                break;
            case R.id.search_list_view:
                goThereButton.setVisibility(View.VISIBLE);
            default:
                break;
        }
    }

    private void searchFacilityOnline(String input) {
        new Thread(() -> {
            String enquiryAddress = "https://fypserverentry.herokuapp.com/api/search/" + input;

            String result = "";
            String temp;
            try {
                URL url = new URL(enquiryAddress);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();
                InputStreamReader isr = new InputStreamReader(connection.getInputStream());
                BufferedReader br = new BufferedReader(isr);
                while ((temp = br.readLine()) != null) {
                    result += temp;
                }
                br.close();
                isr.close();
                connection.disconnect();
                JSONArray returnedJSONArray = new JSONArray(result);
                for (int idx = 0; idx < returnedJSONArray.length(); idx++) {
                    JSONObject details = returnedJSONArray.getJSONObject(idx);
                    Iterator<String> keys = details.keys();
                    synchronized (searchResultsItems) {
                        searchResultsItems.clear();
                        keys.forEachRemaining(new Consumer<String>() {
                            @Override
                            public void accept(String key) {
                                try {
                                    if (key.equals("facility_name")) {
                                        String property = details.getString(key);
                                        searchResultsItems.add(property);
                                    }
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                        searchResultsItems.notifyDataSetChanged();
                        EnquiryFinished = true;
                    }

                }

            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void showStatusText() {
        new Thread(() -> {
            String statusText = "connecting network";
            int count = 0;
            while (!EnquiryFinished) {
                String outputText = statusText;
                for (int num = 0; num < count; num++) {
                    outputText += ".";
                }
                count = (count + 1) % 4;
                try {
                    synchronized (searchResultsItems) {
                        if (EnquiryFinished)
                            break; // make sure the network enquiry is not finished
                        searchResultsItems.clear();
                        searchResultsItems.add(outputText);
                        searchResultsItems.notifyDataSetChanged();
                    }
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void searchNavigationPath(double[] start, int floor, String target) {
        try {
            Socket newEnquiry = new Socket("localhost", 80);    // set up a website
            DataOutputStream dos = new DataOutputStream(newEnquiry.getOutputStream());
            DataInputStream dis = new DataInputStream(newEnquiry.getInputStream());
            dos.writeInt(0); // operation code
            dos.writeDouble(start[0]);
            dos.writeDouble(start[1]);
            dos.writeInt(floor);
            dos.writeUTF(target);
            String results = dis.readUTF();
            JSONArray jsonArray = new JSONArray(results);
            JSONObject jsonObject = jsonArray.getJSONObject(0);
            double[] coordinates = (double[]) jsonObject.get("coordinates");  // may cause a bug
            dis.close();
            dos.close();
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    private void startNavigation(int selectedTarget) {
        MainActivity.NAVIGATION_MODE = true;
        estimatorThread.start();
        coordsThread.start();
        drawMapThread.start();
        new Thread(() -> {
            String target = searchResultsItems.getItem(selectedTarget);
            while (true) {
                if (CoordsCalculation.readyForTracking) {
                    //searchNavigationPath(CoordsCalculation.initCoordinates, CoordsCalculation.floor, target);
                    break;
                } else {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

        }).start();
        quitNavigationBtn.setVisibility(View.VISIBLE);
        searchBtnInMain.setVisibility(View.INVISIBLE);
    }
}
