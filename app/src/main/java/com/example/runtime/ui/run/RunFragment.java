package com.example.runtime.ui.run;

import static android.content.Context.LOCATION_SERVICE;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.runtime.LoginActivity;
import com.example.runtime.R;
import com.example.runtime.databinding.FragmentRunBinding;
import com.example.runtime.firestore.FirestoreHelper;
import com.example.runtime.sharedPrefs.SharedPreferencesHelper;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import org.osmdroid.util.GeoPoint;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class RunFragment extends Fragment {

    //Button variable
    private FragmentRunBinding binding;
    private ImageButton play;
    private ImageButton pause;
    private ImageButton stop;

    //Variable used to track the behavior of the buttons
    private boolean pauseManagement = true;

    //Users data, these data will come from the user's model
    private float height_cm = 170.0f;
    private float weight_kg = 65.0f;
    private float stride_km;

    //Run data
    private float km_value = 0;
    private float calories_value = 0;
    private double actualPace_value;
    private double averagePace_value;
    private String averagePaceFormatted;

    //total run data
    private float totKm;
    private float totCalories;
    private int totSteps;

    //Run segment data
    private float km_local_value;
    private float calories_local_value;

    // Running textView
    private TextView averagePace;
    private TextView actualPace;
    private TextView calories;
    private TextView km;

    //Chronometer variable
    Chronometer chronometer;
    long timeWhenStopped;

    //Sensor variables
    private Sensor accSensor;
    private SensorManager sensorManager;
    private StepCounterListener sensorListener;


    List<LocalDateTime> globalList = new ArrayList<>();
    List<LocalDateTime> localList = new ArrayList<>();

    //Variables used to speech every km
    TextToSpeech textToSpeech;

    //Variables used for the speech
    private SpeechRecognizer speechRecognizer;
    private Intent intentRecognizer;

    //Variable used for the vibration
    private Vibrator vibrator;

    //Variable used to track every integer number of km reached by the user
    int lastIntKmValue = 0;


    String runId = "";

    LocalDateTime startRunDateTime = null;

    private ArrayList<GeoPoint> geoPoints = new ArrayList<>();
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 123;
    private static final int RECORD_AUDIO_PERMISSION_REQUEST_CODE = 456;

    private int LOCATION_REFRESH_TIME = 5000; // ms
    private int LOCATION_REFRESH_DISTANCE = 10; // meters

    private LocationManager mLocationManager;

    //implementing locationlistener to track changes in location
    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(final Location location) {
            final GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
            geoPoints.add(geoPoint);
            Toast.makeText(requireContext(), "Collected geoPoint: lat " + geoPoint.getLatitude() + " lon " + geoPoint.getLongitude(), Toast.LENGTH_SHORT).show();

        }
    };


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        RunViewModel runViewModel =
                new ViewModelProvider(this).get(RunViewModel.class);

        binding = FragmentRunBinding.inflate(inflater, container, false);
        View root = binding.getRoot();


        //Variable initialisation from the database to do all the necessary calculation
        String userUuid = SharedPreferencesHelper.getFieldStringFromSP(requireContext(), "uuid");
        String height = SharedPreferencesHelper.getFieldStringFromSP(requireContext(), "height");
        String weight = SharedPreferencesHelper.getFieldStringFromSP(requireContext(), "weight");

        if ((userUuid == null || userUuid.isEmpty()) ||
                (height == null || height.isEmpty()) ||
                (weight == null || weight.isEmpty())) {
            //navigate to login
            navigateToLogin();
        } else {
            height_cm = Float.parseFloat(height);
            weight_kg = Float.parseFloat(weight);
        }

        //stride already converted in km
        stride_km = (float) (height_cm * 0.65) / 1_000_000;


        //Button settings
        play = root.findViewById(R.id.play);
        pause = root.findViewById(R.id.pause);
        stop = root.findViewById(R.id.stop);

        //Data textView
        averagePace = root.findViewById(R.id.tv1);
        actualPace = root.findViewById(R.id.tv3);
        calories = root.findViewById(R.id.tv2);
        km = root.findViewById(R.id.tvp);

        //Sensor variable initialization
        sensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        //Chronometer variable
        chronometer = root.findViewById(R.id.time);

        //TextToSpeech variable initialisation
        textToSpeech = new TextToSpeech(getActivity(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
            } else {
                Log.e("TTS", "Initialisation failure");
            }
        });


        if (!hasAudioRecordPermissions()) {
            requestAudioRecordPermissions();
        }

        //Speech recognizer variable initialisation
        intentRecognizer = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intentRecognizer.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this.getContext());

        //Vibration variable initialisation
        vibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);

        // Check and request location permissions
        if (!hasLocationPermissions()) {
            requestLocationPermissions();
        }

        //Set button listener for all the button of the fragment
        setButtonListener(userUuid);

        return root;
    }

    //after checking if the required permissions are granted, init the locationManager and start
    //to collect data every 5 seconds. If Permissions not granted -> require them
    private void startLocationUpdates() {
        mLocationManager = (LocationManager) getActivity().getSystemService(LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            Toast.makeText(requireContext(), "start to try collecting geopoint", Toast.LENGTH_SHORT).show();
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_REFRESH_TIME,
                    /*LOCATION_REFRESH_DISTANCE*/ 0, mLocationListener);
        } else { //check if the case
            requestLocationPermissions();
        }
    }


    //stop to listen the location changes
    private void stopLocationUpdates() {
        Toast.makeText(requireContext(), "stop collecting geopoint", Toast.LENGTH_SHORT).show();
        mLocationManager.removeUpdates(mLocationListener);
    }

    //Callback interface
    public interface UpdateDataListener {
        void onUpdateData(List<LocalDateTime> last);
    }


    private void setButtonListener(String userUuid) {
        playButton(userUuid);
        pauseButton(userUuid);
        stopButton(userUuid);
    }

    private void playButton(String userUuid) {
        //setOnClickListener of the Play ImageButton
        play.setOnClickListener(v -> {
            play.setVisibility(View.GONE);
            pause.setVisibility(View.VISIBLE);
            stop.setVisibility(View.VISIBLE);
            pause.setImageResource(R.drawable.pause);
            pauseManagement = true;

            //Chronometer handling
            chronometer.setBase(SystemClock.elapsedRealtime());
            chronometer.start();

            //create run
            startRunDateTime = LocalDateTime.now();
            createRun(userUuid, startRunDateTime);

            //Sensor handling
            startResumeRun();

            //setting the speechRecognizer
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                //All those methods are the default method override from the RecognitionListener()
                @Override
                public void onReadyForSpeech(Bundle params) {
                }

                @Override
                public void onBeginningOfSpeech() {
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                }

                @Override
                public void onError(int error) {
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String result = matches.get(0);

                        if (result.equalsIgnoreCase("RUNTIME PAUSE")) {
                            pauseButton(userUuid);
                            stopSpeechRecognizer();
                        } else if (result.equalsIgnoreCase("RUNTIME STOP")) {
                            stopButton(userUuid);
                            stopSpeechRecognizer();
                        }
                    }
                }

                private void stopSpeechRecognizer() {
                    if (speechRecognizer != null) {
                        speechRecognizer.stopListening();
                        speechRecognizer.destroy();
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });

            //start to collect geoPoints
            if (hasLocationPermissions()) {
                startLocationUpdates();
            } else {
                requestLocationPermissions();
            }

        });
    }

    private void stopButton(String userUuid) {
        //setOnClickListener of the Stop ImageButton
        stop.setOnClickListener(v -> {
            //Buttons settings
            play.setVisibility(View.VISIBLE);
            pause.setVisibility(View.GONE);
            stop.setVisibility(View.GONE);
            pause.setImageResource(R.drawable.pause);

            if (sensorListener != null) {
                globalList.addAll(sensorListener.getLocalList());
            }

            //Chronometer handling
            chronometer.stop();

            //2 possibility:
            // user stop run while it is in pause (no need to create last segment),
            // user stops run while it is running (it needs to create last segment)
            if (pauseManagement) {
                LocalDateTime endRunSegmentDateTime = LocalDateTime.now();
                createRunSegment(runId, startRunDateTime, endRunSegmentDateTime, geoPoints);
            }

            pauseManagement = false;

            Toast.makeText(requireContext(), "stop collecting geopoint", Toast.LENGTH_SHORT).show();
            stopLocationUpdates();

            //add to firebase runs the summaries of the activity
            updateRun(runId);

            //reset the data
            terminateRun();

            //Sensor handling
            stopPauseRun();

            //reset chrono
            chronometer.setBase(SystemClock.elapsedRealtime());
        });
    }

    private void pauseButton(String userUuid) {
        //setOnClickListener of the Pause ImageButton
        pause.setOnClickListener(v -> {
            //go to pause
            if (pauseManagement) {
                pause.setImageResource(R.drawable.play);
                pauseManagement = false;

                globalList.addAll(sensorListener.getLocalList());

                //Chronometer handling
                timeWhenStopped = chronometer.getBase() - SystemClock.elapsedRealtime();
                chronometer.stop();

                stopLocationUpdates();

                //create run segment
                LocalDateTime endRunSegmentDateTime = LocalDateTime.now();
                createRunSegment(runId, startRunDateTime, endRunSegmentDateTime, geoPoints);

                // Reset the fragment data
                pauseRun();

                stopPauseRun();

            } else {
                pause.setImageResource(R.drawable.pause);
                pauseManagement = true;

                //Chronometer handling
                chronometer.setBase(SystemClock.elapsedRealtime() + timeWhenStopped);
                timeWhenStopped = 0;
                chronometer.start();

                startLocationUpdates();

                startRunDateTime = LocalDateTime.now();

                startResumeRun();
            }
        });
    }


    // Method used to reset the field of the class and the textview at the end of the running session
    public void terminateRun() {

        //reset the field data
        globalList.removeAll(globalList);
        localList.removeAll(localList);
        calories_value = 0;
        km_value = 0;
        averagePace_value = 0.0;
        actualPace_value = 0.0;
        averagePaceFormatted = "";

        //Reset the textView data
        calories.setText(String.valueOf(0));
        km.setText(String.valueOf(0));
        averagePace.setText("_'__''");
        actualPace.setText("_'__''");

    }

    //Method used to reset the field of the single fragment session
    public void pauseRun() {

        km_local_value = 0;
        calories_local_value = 0;
        localList.removeAll(localList);
    }

    //Method used to activate the sensorListener when the user press start or resume buttons
    public void startResumeRun() {
        if (accSensor == null) {
            Log.e("SensorError", "Accelerometer sensor not available");

        } else if (sensorListener == null) {
            sensorListener = new StepCounterListener(this::updateData);
            sensorManager.registerListener(sensorListener, accSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    //Method used to stop the stepCount listener when the user press pause or stop buttons
    public void stopPauseRun() {
        if (sensorListener != null) {
            sensorManager.unregisterListener(sensorListener);
            sensorListener = null;
        }
    }

    public void updateData(List<LocalDateTime> last) {

        //Km values updating
        km_value += (stride_km * 5);
        if (km_value >= 0.01) {
            km.setText(String.format(Locale.getDefault(), "%.2f", km_value));
        }

        //Km values for the single segment updated
        km_local_value += (stride_km * 5);


        //Calories value updating
        calories_value = km_value * weight_kg;
        if (calories_value >= 1) {
            calories.setText(String.valueOf((int) calories_value));
        }

        //Calories for the single fragment updated
        calories_local_value = km_local_value * weight_kg;


        //Add the new step to the global list
        globalList.addAll(last);
        localList.addAll(last);


        //Actual Pace calculation and update
        actualPace_value = ChronoUnit.MILLIS.between(last.get(0), last.get(4)) / 60000.0;
        if (stride_km > 0 && actualPace_value > 0) {
            double actualPace_valued = actualPace_value / (stride_km * 5);
            int actualPaceMinPart = (int) actualPace_valued;
            int actualPaceSecPart = (int) ((actualPace_valued - actualPaceMinPart) * 60);
            String actualPaceFormatted = actualPaceMinPart + "'" + actualPaceSecPart + "''";
            actualPace.setText(actualPaceFormatted);
        }

        //Average Pace calculation and update
        averagePace_value = ChronoUnit.MILLIS.between(globalList.get(0), last.get(4)) / 60000.0;
        int averagePaceMinPart = 0;
        int averagePaceSecPart = 0;
        if (km_value > 0 && averagePace_value > 0) {
            averagePace_value = averagePace_value / km_value;
            averagePaceMinPart = (int) averagePace_value;
            averagePaceSecPart = (int) ((averagePace_value - averagePaceMinPart) * 60);
            averagePaceFormatted = averagePaceMinPart + "'" + averagePaceSecPart + "''";
            averagePace.setText(averagePaceFormatted);
        }


        //Notifies the user when each kilometer is reached
        if ((int) km_value > lastIntKmValue) {

            //At every km reached, the app notify it to the user via vibration
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            }

            //via audio instaed, the app will say the total km reached and the relative average pace
            textToSpeech.speak("" + (int) km_value + "kilometers, average pace:" + averagePaceMinPart + " minutes," + averagePaceSecPart + "seconds per kilometer.",
                    TextToSpeech.QUEUE_FLUSH, null, null);
            lastIntKmValue = (int) km_value;
        }
    }


    @Override
    public void onDestroyView() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }

        super.onDestroyView();
        binding = null;
    }

    private void navigateToLogin() {
        SharedPreferencesHelper.clearSP(requireContext());
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        startActivity(intent);
        requireActivity().finish(); // Avoid navigating back
    }

////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////

    public void createRun(String userUuid, LocalDateTime startDateTime) {
        //this field will be updated for each new run.
        this.runId = UUID.randomUUID().toString();
        // Create a new document with a generated ID
        Map<String, Object> data = new HashMap<>();
        data.put("runId", runId);
        data.put("userUuid", userUuid);
        data.put("startDateTime", FirestoreHelper.getFirebaseTimestampFromLocalDateTime(startDateTime));

        FirestoreHelper.getDb().collection("runs")
                .add(data)
                .addOnSuccessListener(documentReference -> {
                    // Document added successfully
                    Log.d("successRun creation", "DocumentSnapshot added with ID: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    //if run instance fails to be created, reset all the condition or navigate elsewhere
                    Log.w("failedRun creation", "Error adding document", e);
                });
    }

    //method called on "stop button" is clicked, it adds the overall data regarding the session
    //to the model run
    public void updateRun(String runId) {
        if (runId == null) {
            Log.e("Firestore Update", "Invalid runId");
            return;
        }

        long elapsedMillis = SystemClock.elapsedRealtime() - chronometer.getBase();

        Map<String, Object> data = new HashMap<>();

        data.put("totalSteps", totSteps);
        data.put("totalCalories", totCalories);
        data.put("totalKm", totKm);
        data.put("totalTimeMs", elapsedMillis);


        // Create a query to find the document with the specified runId property
        Query query = FirestoreHelper.getDb().collection("runs").whereEqualTo("runId", runId);

        // Execute the query
        query.get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            // Update the first document found with the specified runId
                            QueryDocumentSnapshot documentSnapshot = (QueryDocumentSnapshot) querySnapshot.getDocuments().get(0);
                            DocumentReference runRef = documentSnapshot.getReference();
                            Log.e("snapshot id", runRef.toString());

                            // Update the document with the new data
                            runRef.update(data)
                                    .addOnSuccessListener(aVoid -> {
                                        // Update successful
                                        Log.d("Firestore Update", "Document updated successfully!");
                                    })
                                    .addOnFailureListener(e -> {
                                        // Handle the failure
                                        Log.e("Firestore Update", "Error updating document: " + e.getMessage());
                                    });
                        } else {
                            // Handle the case where no document with the specified runId is found
                            Log.e("Firestore Update", "No document found with runId: " + runId);
                        }
                    } else {
                        // Handle the failure to execute the query
                        Log.e("Firestore Update", "Error executing query: " + task.getException().getMessage());
                    }
                });

    }


    public void createRunSegment(String runId, LocalDateTime startDateTime, LocalDateTime endDateTime, ArrayList<GeoPoint> geoPoints) {

        //update total values
        updateTotValues();

        // Create a new document with a generated ID
        Map<String, Object> data = new HashMap<>();
        data.put("runId", runId);
        data.put("steps", localList.size());
        data.put("calories", calories_local_value);
        data.put("km", km_local_value);
        data.put("averagePace", averagePace_value);
        data.put("startDateTime", FirestoreHelper.getFirebaseTimestampFromLocalDateTime(startDateTime));
        data.put("endDateTime", FirestoreHelper.getFirebaseTimestampFromLocalDateTime(endDateTime));

        List<Map<String, Double>> geoPointsMaps = new ArrayList<>();
        for (GeoPoint geoPoint : geoPoints) {
            Map<String, Double> geoPointMap = new HashMap<>();
            geoPointMap.put("latitude", geoPoint.getLatitude());
            geoPointMap.put("longitude", geoPoint.getLongitude());
            geoPointsMaps.add(geoPointMap);
        }

        data.put("geoPoints", geoPointsMaps);


        FirestoreHelper.getDb().collection("runSegments")
                .add(data)
                .addOnSuccessListener(documentReference -> {
                    // Document added successfully
                    Log.d("runSegments creation", "DocumentSnapshot added with ID: " + documentReference.getId());

                })
                .addOnFailureListener(e -> {
                    // Handle errors
                    //Log.w(TAG, "Error adding document", e);
                    Log.w("failed runSegments creation", "Error adding document", e);
                });
    }

    private void updateTotValues() {
        totSteps += localList.size();
        totCalories += calories_local_value;
        totKm += km_local_value;
    }

    private boolean hasAudioRecordPermissions() {
        // Check if the app has the necessary location permissions
        return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestAudioRecordPermissions() {
        // Request location permissions
        ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_REQUEST_CODE);
    }

    private boolean hasLocationPermissions() {
        // Check if the app has the necessary location permissions
        return ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermissions() {
        // Request location permissions
        ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            // Check if the permission was granted
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start location updates
                //startLocationUpdates();
            } else {
                // Permission denied, handle accordingly (e.g., show a message to the user)
                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

}


////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////

class StepCounterListener implements SensorEventListener {

    List<Integer> accSeries = new ArrayList<>();
    private double accMag = 0;
    private int lastAddedIndex = 1;
    int stepThreshold = 7;
    List<LocalDateTime> localList = new ArrayList<>();

    //RunFragment object
    private RunFragment.UpdateDataListener updateDataListener;

    //Listener constructor
    public StepCounterListener(RunFragment.UpdateDataListener listener) {
        this.updateDataListener = listener;
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        // Check the type of the sensor, this is helpful in case of multiple sensors
        if (sensorEvent.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {

            //Get the raw acc. sensor data
            float x = sensorEvent.values[0];
            float y = sensorEvent.values[1];
            float z = sensorEvent.values[2];

            // Computing the magnitude for the acceleration
            accMag = Math.sqrt(x * x + y * y + z * z);

            //Storing the magnitude for the acceleration in accSeries
            accSeries.add((int) accMag);

            peakDetection();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    private void peakDetection() {

        int windowSize = 10;
        /* Peak detection algorithm derived from: A Step Counter Service for Java-Enabled Devices Using a Built-In Accelerometer Mladenov et al.
         */
        int currentSize = accSeries.size(); // get the length of the series
        if (currentSize - lastAddedIndex < windowSize) { // if the segment is smaller than the processing window size skip it
            return;
        }

        List<Integer> valuesInWindow = accSeries.subList(lastAddedIndex, currentSize);
        lastAddedIndex = currentSize;

        for (int i = 1; i < valuesInWindow.size() - 1; i++) {
            int forwardSlope = valuesInWindow.get(i + 1) - valuesInWindow.get(i);
            int downwardSlope = valuesInWindow.get(i) - valuesInWindow.get(i - 1);

            if (forwardSlope < 0 && downwardSlope > 0 && valuesInWindow.get(i) > stepThreshold) {
                Log.d("ACC STEPS: ", String.valueOf(LocalDateTime.now()));
                countSteps();

            }
        }
    }

    private void countSteps() {
        localList.add(LocalDateTime.now());

        //Every 5 steps, we update the data
        if (localList.size() % 5 == 0) {
            updateDataListener.onUpdateData(new ArrayList<>(localList.subList(localList.size() - 5, localList.size())));
        }

    }

    //Get method of the localList of steps
    public List<LocalDateTime> getLocalList() {
        return localList;
    }

}


