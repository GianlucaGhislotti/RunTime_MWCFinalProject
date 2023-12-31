package com.example.runtime.ui.activityDetails;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import firebase.com.protolitewrapper.BuildConfig;

public class ActivityDetail extends AppCompatActivity {

    private MapView mapView;

    private AnyChartView anyChartView;

    //Button used to handle the activity sharing
    private ImageButton shareButton;

    private LinearLayout buttonContainer;
    private TextView stepsText;
    private TextView caloriesText;
    private TextView kmText;
    private TextView paceText;

    private TextView durationText;

    private Button prevChartButton;
    private Button nextChartButton;

    private final ArrayList<String> chartType = new ArrayList<>(Arrays.asList("Steps", "Kms", "Calories"));
    private int indexList = 1;

    private Cartesian cartesian;

    private Column column;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        // Retrieve the runId from the Intent
        String runId = getIntent().getStringExtra("runId");

        List<RunSegment> runSegments = new ArrayList<>();

        // Set the user agent for osmdroid
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

        stepsText = findViewById(R.id.textSteps);
        kmText = findViewById(R.id.textKm);
        paceText = findViewById(R.id.paceText);
        caloriesText = findViewById(R.id.textCalories);
        durationText = findViewById(R.id.durationText);

        prevChartButton = findViewById(R.id.prevChart);
        nextChartButton = findViewById(R.id.nextChart);

        buttonContainer = findViewById(R.id.buttonContainer);

        shareButton = findViewById(R.id.shareButton);
        shareButton.setOnClickListener(v -> sharePdf());

        //try to get data from backend
        getRunSegmentsFromBackend(runId, runSegments);

        //map settings
        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);

        //chart settings
        initChartView();

        nextChartButton.setOnClickListener(e -> {
            if (indexList == 2) {
                indexList = 0;
            } else {
                indexList++;
            }

            if (runSegments.size() > 1) {
                updateChart(runSegments);
            }
        });

        prevChartButton.setOnClickListener(e -> {
            if (indexList == 0) {
                indexList = 2;
            } else {
                indexList--;
            }

            if (runSegments.size() > 1) {
                updateChart(runSegments);
            }
        });

    }

    //if success, update ui
    private void getRunSegmentsFromBackend(String runId, List<RunSegment> runSegments) {
        CollectionReference runsCollection = FirestoreHelper.getDb().collection("runSegments");
        Query query = runsCollection.whereEqualTo("runId", runId);
        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        RunSegment segment = document.toObject(RunSegment.class);
                        runSegments.add(segment);
                    }
                    updateUI(runSegments);
                })
                .addOnFailureListener(e -> {
                    Log.w("failed to get run", "Error adding document", e);
                });
    }

    //pass the right property to the main ui elements
    private void updateUI(List<RunSegment> runSegments) {
        if (runSegments.isEmpty()) {
            return;
        }

        //init mapView if data are available
        initMapView(runSegments);

        //makes sense to display the chart only if user completed different segments
        if (runSegments.size() > 1) {
            //init chart
            createColumnChart(runSegments);
            anyChartView.setChart(cartesian);
        }


        //property preparation
        LocalDateTime startRun = FirestoreHelper.getLocalDateTimeFromFirebaseTimestamp(runSegments.get(0).getStartDateTime());
        LocalDateTime endRun = FirestoreHelper.getLocalDateTimeFromFirebaseTimestamp(runSegments.get(runSegments.size() - 1).getEndDateTime());
        long totalMinutes = ChronoUnit.MINUTES.between(startRun, endRun);
        long totalSeconds = ChronoUnit.SECONDS.between(startRun, endRun) - totalMinutes * 60;
        String timeFormatted = String.format("%d:%02d", totalMinutes, totalSeconds);

        DecimalFormat df = new DecimalFormat("#.##");

        int totalSteps = runSegments.stream().mapToInt(RunSegment::getSteps).sum();
        double totalDistanceKM = runSegments.stream().mapToDouble(item -> item.getKm()).sum();
        double calories = runSegments.stream().mapToDouble(item -> item.getCalories()).sum();
        double averagePace = runSegments.stream().mapToDouble(item -> item.getAveragePace()).sum() / runSegments.size();

        stepsText.setText(String.valueOf(totalSteps) + " steps");
        caloriesText.setText(df.format(calories) + " cal");
        paceText.setText(df.format(averagePace) + " min/Km");
        kmText.setText(df.format(totalDistanceKM) + " km");
        durationText.setText(timeFormatted);

        createPdf(String.valueOf(totalSteps) + "total steps",
                df.format(calories) + " calories",
                df.format(averagePace) + " min/Km",
                df.format(totalDistanceKM) + " km",
                timeFormatted + " min");

    }

    private void initMapView(List<RunSegment> runSegments) {
        ArrayList<GeoPoint> totalpaths = new ArrayList<>();
        for (RunSegment segment : runSegments) {
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

    private void initChartView() {
        anyChartView = findViewById(R.id.barChart);
        anyChartView.setBackgroundColor("#00000000");
    }

    private void createColumnChart(List<RunSegment> runSegments) {
        Map<Integer, Number> graph_map = new TreeMap<>();

        for (RunSegment segment : runSegments) {
            if (indexList == 0) {
                graph_map.put(runSegments.indexOf(segment) + 1, segment.getSteps());
            } else if (indexList == 1) {
                graph_map.put(runSegments.indexOf(segment) + 1, segment.getKm());
            } else {
                graph_map.put(runSegments.indexOf(segment) + 1, segment.getCalories());
            }
        }

        //init of cartesian
        cartesian = AnyChart.column();
        List<DataEntry> data = new ArrayList<>();

        for (Map.Entry<Integer, Number> entry : graph_map.entrySet())
            data.add(new ValueDataEntry(entry.getKey(), entry.getValue()));

        //init of column series
        column = cartesian.column(data);

        column.fill("#1EB980");
        column.stroke("#1EB980");

        column.tooltip()
                .titleFormat("segment nr.: {%X}")
                .format("{%Value}")
                .anchor(Anchor.RIGHT_BOTTOM);

        column.tooltip()
                .position(Position.RIGHT_TOP)
                .offsetX(0d)
                .offsetY(5);

        cartesian.tooltip().positionMode(TooltipPositionMode.POINT);
        cartesian.interactivity().hoverMode(HoverMode.BY_X);
        cartesian.yScale().minimum(0);

        cartesian.yAxis(0).title("#" + chartType.get(indexList));
        cartesian.xAxis(0).title("Per segment");
        cartesian.background().fill("#00000000");
        cartesian.animation(true);

    }

    //require chart to use new data
    private void updateChart(List<RunSegment> runSegments) {
        if (runSegments.size() > 1) {
            //anyChartView.clear();

            Map<Integer, Number> graph_map = new TreeMap<>();

            for (RunSegment segment : runSegments) {
                if (indexList == 0) {
                    graph_map.put(runSegments.indexOf(segment) + 1, segment.getSteps());
                } else if (indexList == 1) {
                    graph_map.put(runSegments.indexOf(segment) + 1, segment.getKm());
                } else {
                    graph_map.put(runSegments.indexOf(segment) + 1, segment.getCalories());
                }
            }

            List<DataEntry> newData = new ArrayList<>();

            for (Map.Entry<Integer, Number> entry : graph_map.entrySet())
                newData.add(new ValueDataEntry(entry.getKey(), entry.getValue()));

            column.data(newData);
            cartesian.yAxis(0).title("#" + chartType.get(indexList));
            cartesian.xAxis(0).title("Per segment");
        } else {
            anyChartView.setVisibility(View.GONE);
            buttonContainer.setVisibility(View.GONE);
        }
    }

    //Method called to create the pdf file immediately after the recap of the data of all
    //the running fragment
    private void createPdf(String steps, String calories, String averagePace,
                           String totalKm, String totMin) {

        PdfDocument document = new PdfDocument();

        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(300, 600, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);

        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();
        int x = 10, y = 25;

        canvas.drawText("Steps: " + steps, x, y, paint);
        y += 15;
        canvas.drawText("Calories: " + calories, x, y, paint);
        y += 15;
        canvas.drawText("Average Pace: " + averagePace, x, y, paint);
        y += 15;
        canvas.drawText("Total distance: " + totalKm, x, y, paint);
        y += 15;
        canvas.drawText("Duration: " + totMin, x, y, paint);

        document.finishPage(page);

        // Write the document in a file
        String filePath = getExternalFilesDir(null).getAbsolutePath() + "/mypdf.pdf";
        File file = new File(filePath);
        try {
            document.writeTo(new FileOutputStream(file));
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Document closed
        document.close();
    }

    //Method used to share the pdf created via implicit intent
    private void sharePdf() {
        File file = new File(getExternalFilesDir(null), "mypdf.pdf");

        if (!file.exists()) {
            Toast.makeText(this, "PDF not found.", Toast.LENGTH_SHORT).show();
            return;
        }
        //Create the URI of the file
        Uri uri = FileProvider.getUriForFile(this, "com.example.runtime.provider", file);

        //Creating the intent
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share PDF via:"));
    }
}
