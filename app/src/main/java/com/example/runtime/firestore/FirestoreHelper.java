package com.example.runtime.firestore;

import android.util.Log;

import com.example.runtime.firestore.models.Run;
import com.example.runtime.firestore.models.RunSegment;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * to deserialize firebase needs the java object to have an empty constructor, and the set methods
 * need to get the correct firebase type.
 */
public class FirestoreHelper {
    private static FirebaseFirestore db = FirebaseFirestore.getInstance();

    public static Timestamp getFirebaseTimestampFromLocalDateTime(LocalDateTime date) {
        Instant instant = date.atZone(ZoneId.systemDefault()).toInstant();
        return new Timestamp(instant.toEpochMilli() / 1000, (int) ((instant.toEpochMilli() % 1000) * 1000000));
    }


    public static LocalDateTime getLocalDateTimeFromFirebaseTimestampLocalDateTime(Timestamp date) {
        Instant instant = date.toDate().toInstant();
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    public static String createUser(String username, String password) {
        // Create a new document with a generated ID
        String uuid = UUID.randomUUID().toString();
        Map<String, Object> data = new HashMap<>();
        data.put("uuid", uuid);
        data.put("username", username);
        data.put("password", password);

//        Log.d(String tag, String message): Debug log message.
//        Log.i(String tag, String message): Info log message.
//        Log.w(String tag, String message): Warning log message.
//        Log.e(String tag, String message): Error log message.
        db.collection("users")
                .add(data)
                .addOnSuccessListener(documentReference -> {
                    // Document added successfully
                    Log.d("User created", "DocumentSnapshot added with ID: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    // Handle errors
                    Log.w("User creation failed", "Error adding document", e);
                });

        return uuid;
    }

    public static void createRun(String userUuid, LocalDateTime startDateTime) {
        String runId = UUID.randomUUID().toString();
        // Create a new document with a generated ID
        Map<String, Object> data = new HashMap<>();
        data.put("runId", runId);
        data.put("userUuid", userUuid);
        data.put("startDateTime", getFirebaseTimestampFromLocalDateTime(startDateTime));

        db.collection("runs")
                .add(data)
                .addOnSuccessListener(documentReference -> {
                    // Document added successfully
                    Log.d("successRun creation", "DocumentSnapshot added with ID: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    // Handle errors
                    Log.w("failedRun creation", "Error adding document", e);
                });
    }

    public static void createRunSegment(String runId, int steps, LocalDateTime startDateTime, LocalDateTime endDateTime) {
        // Create a new document with a generated ID
        Map<String, Object> data = new HashMap<>();
        data.put("runId", runId);
        data.put("steps", steps);
        data.put("startDateTime", getFirebaseTimestampFromLocalDateTime(startDateTime));
        data.put("endDateTime", getFirebaseTimestampFromLocalDateTime(endDateTime));


        db.collection("runSegments")
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

    public static void getRunsByUuidAndStartDateTimeInRange(String userUuid, LocalDateTime startDate, LocalDateTime endDate) {
        CollectionReference runsCollection = db.collection("runs");

        Query query = runsCollection
                .whereEqualTo("userUuid", userUuid)
                .whereGreaterThanOrEqualTo("startDateTime", getFirebaseTimestampFromLocalDateTime(startDate))
                .whereLessThanOrEqualTo("startDateTime", getFirebaseTimestampFromLocalDateTime(endDate));

        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.w("RUN", "try to deserialize run");
                    List<Run> runs = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Log.w("RUN shap", "try  run");
                        Run run = document.toObject(Run.class);
                        runs.add(run);
                    }
                    runs.forEach(run -> Log.d(run.getRunId(), "at time " + run.getStartDateTime()));
                })
                .addOnFailureListener(e -> {
                    Log.w("failed to get run", "Error adding document", e);

                });

        /*try {
            // Use get() to fetch the data synchronously
            QuerySnapshot querySnapshot = Tasks.await(query.get());

            List<Run> runs = new ArrayList<>();
            for (QueryDocumentSnapshot document : querySnapshot) {
                // Convert the document to a Run object or process the data as needed
                Run run = document.toObject(Run.class);
                runs.add(run);
            }

            return runs;
        } catch (ExecutionException | InterruptedException e) {
            // Handle exceptions
            e.printStackTrace();
            return Collections.emptyList();
        }*/
    }

    public static void getRunSegmentsByRunId(String runId) {
        CollectionReference runsCollection = db.collection("runSegments");

        Query query = runsCollection.whereEqualTo("runId", runId);

        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.w("RUN SEGMENT", "try to deserialize segment");
                    List<RunSegment> segments = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        RunSegment segment = document.toObject(RunSegment.class);
                        segments.add(segment);
                    }
                    segments.forEach(run -> Log.d(run.getRunId(), "at time " + getLocalDateTimeFromFirebaseTimestampLocalDateTime(run.getStartDateTime()) ));
                })
                .addOnFailureListener(e -> {
                    Log.w("failed to get run", "Error adding document", e);

                });

    }


}