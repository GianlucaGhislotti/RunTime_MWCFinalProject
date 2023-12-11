package com.example.runtime.ui.activityDetails;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.anychart.AnyChart;
import com.anychart.AnyChartView;
import com.anychart.chart.common.dataentry.DataEntry;
import com.anychart.chart.common.dataentry.ValueDataEntry;
import com.anychart.charts.Cartesian;
import com.anychart.core.cartesian.series.Column;
import com.anychart.enums.Anchor;
import com.anychart.enums.HoverMode;
import com.anychart.enums.Position;
import com.anychart.enums.TooltipPositionMode;
import com.example.runtime.R;
import com.example.runtime.firestore.FirestoreHelper;
import com.example.runtime.firestore.models.RunSegment;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import firebase.com.protolitewrapper.BuildConfig;

public class ActivityDetail extends AppCompatActivity {

    private MapView mapView;

    private AnyChartView anyChartView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        // Retrieve the runId from the Intent
        String runId = getIntent().getStringExtra("runId");

        //TextView textView = findViewById(R.id.detailTitle);

        List<RunSegment> runSegments = new ArrayList<>();
        LinearLayout infoContainer = findViewById(R.id.infoContainer);

        // Set the user agent for osmdroid
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);


        //testBackend
        getItemsFromBackend(runId, runSegments, infoContainer);
        updateUI(runSegments, infoContainer);

        //map settings
        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);

        //chart settings
        anyChartView = findViewById(R.id.barChart);
    }

    private void getItemsFromBackend(String runId, List<RunSegment> runSegments, LinearLayout infoContainer) {
        CollectionReference runsCollection = FirestoreHelper.getDb().collection("runSegments");

        Query query = runsCollection.whereEqualTo("runId", runId);

        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.w("RUN SEGMENT", "try to deserialize segment");
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        RunSegment segment = document.toObject(RunSegment.class);
                        runSegments.add(segment);
                    }
                    //runSegments.forEach(segment -> Log.d(segment.getRunId(), "at time " + FirestoreHelper.getLocalDateTimeFromFirebaseTimestampLocalDateTime(segment.getStartDateTime()) ));

                    updateUI(runSegments, infoContainer);

                })
                .addOnFailureListener(e -> {
                    Log.w("failed to get run", "Error adding document", e);
                });
    }

    private void updateUI(List<RunSegment> runSegments, LinearLayout infoContainer) {
        if (runSegments.isEmpty()) {
            return;
        }

        //todo: handling the geopoint and the mapview
        //runSegments.sort(Comparator.comparing(RunSegment::getStartDateTime));

        /*runSegments.get(0).getGeoPoints().forEach(item -> Log.e("geopoint", item.getLatitude() + " " + item.getLongitude()));
        ArrayList<GeoPoint> totalpaths = new ArrayList<>();
        for(RunSegment segment : runSegments){
            totalpaths.addAll(segment.getGeoPoints());
        }
        if (totalpaths.size() > 0) {
            // Set the map center to the first GeoPoint in the list
            IMapController mapController = mapView.getController();
            mapController.setZoom(14.0); // adjust the zoom level as needed
            mapController.setCenter(totalpaths.get(0));

            // Add a Polyline to connect the GeoPoints
            Polyline line = new Polyline();
            line.setPoints(totalpaths);
            line.setColor(Color.RED);
            mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
            mapView.getOverlayManager().add(line);
        }*/

        //init mapView if data are available
        initMapView(runSegments);

        if(runSegments.size() > 1){
            //init chart
            createColumnChart(runSegments);
        } else {
            anyChartView.setActivated(false);
        }


        //property prep

        LocalDateTime startRun = FirestoreHelper.getLocalDateTimeFromFirebaseTimestamp(runSegments.get(0).getStartDateTime());
        LocalDateTime endRun = FirestoreHelper.getLocalDateTimeFromFirebaseTimestamp(runSegments.get(runSegments.size() - 1).getEndDateTime());

        Duration totalDuration = Duration.between(startRun, endRun);
        String totalDurationString = null;
        /*if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            totalDurationString = String.format("%02d:%02d:%02d", totalDuration.toHours(), totalDuration.toMinutesPart(), totalDuration.toSecondsPart());
        }*/

        int totalSteps = runSegments.stream().mapToInt(RunSegment::getSteps).sum();
        //double totalDistanceKM = totalSteps * 0.8 / 1000;
        double totalDistanceKM = runSegments.stream().mapToDouble(item -> item.getKm()).sum();
        double calories = runSegments.stream().mapToDouble(item -> item.getCalories()).sum();

        double averagePace = runSegments.stream().mapToDouble(item -> item.getAveragePace()).sum() / runSegments.size();
        Log.d("avgpace", String.valueOf(runSegments.get(0).getAveragePace()));
        //double averagePace = itemList.get(0).getAveragePace();
        createInfoItem("startRun", FirestoreHelper.formatDateTimeWithSecondos(startRun), infoContainer);
        createInfoItem("endRun", FirestoreHelper.formatDateTimeWithSecondos(endRun), infoContainer);
        //createInfoItem("totalDurationInMinutes", String.valueOf(totalDuration.toMinutes()), infoContainer);

        createInfoItem("Nr.steps", String.valueOf(totalSteps), infoContainer);
        createInfoItem("average_pace", String.valueOf(averagePace), infoContainer);
        createInfoItem("totalDistanceKM", String.valueOf(totalDistanceKM), infoContainer);
        //createInfoItem("calories", String.valueOf(calories), infoContainer);

        //createInfoItem("totalDurationInSeconds", String.valueOf(totalDuration.getSeconds()), infoContainer);


    }

    private void initMapView(List<RunSegment> runSegments){
        ArrayList<GeoPoint> totalpaths = new ArrayList<>();
        for(RunSegment segment : runSegments){
            totalpaths.addAll(segment.getGeoPoints());
        }
        if (totalpaths.size() > 0) {
            // Set the map center to the first GeoPoint in the list
            IMapController mapController = mapView.getController();
            mapController.setZoom(14.0); // adjust the zoom level as needed
            mapController.setCenter(totalpaths.get(0));

            // Add a Polyline to connect the GeoPoints
            Polyline line = new Polyline();
            line.setPoints(totalpaths);
            line.setColor(Color.RED);
            mapView.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
            mapView.getOverlayManager().add(line);
        }
    }


    public /*Cartesian*/void  createColumnChart(List<RunSegment> runSegments){
        Map<Integer, Integer> graph_map = new TreeMap<>();

        for(RunSegment segment : runSegments){
            graph_map.put(runSegments.indexOf(segment) + 1, segment.getSteps() );
        }



        //***** Create column chart using AnyChart library *********/
        // Create and get the cartesian coordinate system for column chart
        Cartesian cartesian = AnyChart.column();

        //  Create data entries for x and y axis of the graph
        List<DataEntry> data = new ArrayList<>();

        for (Map.Entry<Integer,Integer> entry : graph_map.entrySet())
            data.add(new ValueDataEntry(entry.getKey(), entry.getValue()));

        //  Add the data to column chart and get the columns
        Column column = cartesian.column(data);

        //***** Modify the UI of the chart *********/
        // Change the color of column chart and its border
        column.fill("#1EB980");
        column.stroke("#1EB980");


        // Modifying properties of tooltip
        column.tooltip()
                .titleFormat("segment nr.: {%X}")
                .format("{%Value} Steps")
                .anchor(Anchor.RIGHT_BOTTOM);

        // Modify column chart tooltip properties
        column.tooltip()
                .position(Position.RIGHT_TOP)
                .offsetX(0d)
                .offsetY(5);

        // Modifying properties of cartesian
        cartesian.tooltip().positionMode(TooltipPositionMode.POINT);
        cartesian.interactivity().hoverMode(HoverMode.BY_X);
        cartesian.yScale().minimum(0);


        //  Modify the UI of the cartesian
        cartesian.yAxis(0).title("#Steps");
        cartesian.xAxis(0).title("Per segment");
        cartesian.background().fill("#00000000");
        cartesian.animation(true);

        //Cartesian cartesian = createColumnChart();
        anyChartView.setBackgroundColor("#00000000");
        anyChartView.setChart(cartesian);

        //return cartesian;
    }

    private void createInfoItem(String titleText, String propertyText, LinearLayout layout) {
        LinearLayout infoLayout = (LinearLayout) LayoutInflater.from(this).inflate(R.layout.info_run_detail, layout, false);
        // Customize the card content based on item data
        TextView title = infoLayout.findViewById(R.id.itemTitle);
        TextView property = infoLayout.findViewById(R.id.itemProperty);
        //cardText.setText(item.getRunId());
        title.setText(titleText);
        property.setText(propertyText);

        layout.addView(infoLayout);
    }
}
