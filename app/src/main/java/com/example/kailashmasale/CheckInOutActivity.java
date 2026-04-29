package com.example.kailashmasale;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CheckInOutActivity extends AppCompatActivity {

    private static final int DEFAULT_RADIUS_METERS = 500;
    private static final long LOCATION_TIMEOUT_MS = 15_000;

    private final Handler locationHandler = new Handler(Looper.getMainLooper());
    private Runnable locationTimeoutRunnable;
    private LocationListener pendingLocationListener;
    private LocationManager pendingLocationManager;
    private boolean locationResultShown = false;

    private LinearLayout checkInTab;
    private LinearLayout checkOutTab;
    private TextView checkInText;
    private TextView checkOutText;
    private ViewFlipper contentFlipper;
    private boolean isCheckInSelected = true;
    
    // Check In fields
    private Spinner distributorSpinner;
    private Spinner bitSpinner;
    private EditText primaryTargetEditText;
    private EditText workingWithEditText;
    private LinearLayout submitButton;
    private LinearLayout addLocationButton;
    private ArrayAdapter<String> distributorAdapter;
    private ArrayAdapter<String> bitAdapter;
    /** Ordered list of distributor IDs matching distributor spinner (index 0 = first name after "Select distributor"). */
    private java.util.List<String> assignedDistributorIds = new java.util.ArrayList<>();
    
    // Check Out fields
    private TextView checkoutDistributorText;
    private TextView checkoutBitText;
    private EditText totalCallsInput;
    private EditText productiveCallsInput;
    private EditText achievedSecondaryInput;
    private EditText achievedPrimaryInput;
    private EditText additionalNotesInput;
    private CheckBox nightHoultCheckbox;
    private LinearLayout checkoutButton;
    private LinearLayout endLocationButton;

    private ActivityResultLauncher<String> locationPermissionLauncher;
    private boolean pendingLocationCheckIsAddLocation = true;
    /** True only after Check In button was clicked and user was within assigned location. */
    private boolean checkInLocationVerified = false;
    /** True after we saved a location-only check-in so we don't double-save. */
    private boolean locationOnlyCheckInSaved = false;
    /** Lat/long captured when Check In succeeded (within range), stored with the check-in document. */
    private double checkInLatitude = 0;
    private double checkInLongitude = 0;
    private String checkInLocationName = "";
    /** Exact epoch millis when check-in location was captured. */
    private long checkInLocationCapturedAtMillis = 0L;
    /** Lat/long captured when Check Out button clicked and within range, stored with the check-out document. */
    private double checkOutLatitude = 0;
    private double checkOutLongitude = 0;
    private String checkOutLocationName = "";
    /** Exact epoch millis when check-out location was captured. */
    private long checkOutLocationCapturedAtMillis = 0L;
    private boolean checkOutLocationCaptured = false;
    /** True after we saved a location-only check-out so we don't double-save. */
    private boolean locationOnlyCheckOutSaved = false;
    /** True only after Check Out button was clicked and user was within assigned location. */
    private boolean checkOutLocationVerified = false;
    /** True after user submitted check-in (full form); show submitted details in same UI until checkout. */
    private boolean checkInSubmittedToday = false;
    /** True after user submitted checkout form; show submitted details until checkout location is submitted. */
    private boolean checkOutSubmittedToday = false;
    /** When true, we are applying today's saved check-in from Firestore to the form (distributor/bit set after adapters load). */
    private boolean pendingApplyTodayCheckIn = false;
    private String savedCheckInDistributor;
    private String savedCheckInBit;
    private String savedCheckInPrimaryTarget;
    private String savedCheckInWorkingWith;
    private ListenerRegistration todayCheckInListener;
    private ListenerRegistration todayCheckOutListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set status bar to white to remove purple/violet strip
        if (getWindow() != null) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));
            getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        }
        
        setContentView(R.layout.activity_check_in_out);

        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        fetchAssignedLocationAndCheckCurrent(pendingLocationCheckIsAddLocation);
                    } else {
                        showLocationResultDialog("Permission needed", "Location permission is needed to check your assigned area.");
                    }
                });

        initializeViews();
        setupToggleButtons();
        setupSpinners();
        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTodayCheckInIfAny();
        if (!isCheckInSelected) {
            loadTodayCheckOutIfAny();
        }
    }

    @Override
    protected void onDestroy() {
        if (todayCheckInListener != null) {
            todayCheckInListener.remove();
            todayCheckInListener = null;
        }
        if (todayCheckOutListener != null) {
            todayCheckOutListener.remove();
            todayCheckOutListener = null;
        }
        cancelLocationRequest();
        super.onDestroy();
    }

    private void initializeViews() {
        checkInTab = findViewById(R.id.check_in_tab);
        checkOutTab = findViewById(R.id.check_out_tab);
        checkInText = findViewById(R.id.check_in_text);
        checkOutText = findViewById(R.id.check_out_text);
        contentFlipper = findViewById(R.id.content_flipper);
        
        // Check In fields
        distributorSpinner = findViewById(R.id.distributor_spinner);
        bitSpinner = findViewById(R.id.bit_spinner);
        primaryTargetEditText = findViewById(R.id.primary_target_input);
        workingWithEditText = findViewById(R.id.working_with_edittext);
        submitButton = findViewById(R.id.submit_button);
        addLocationButton = findViewById(R.id.add_location_button);
        
        // Check Out fields
        checkoutDistributorText = findViewById(R.id.checkout_distributor_text);
        checkoutBitText = findViewById(R.id.checkout_bit_text);
        totalCallsInput = findViewById(R.id.total_calls_input);
        productiveCallsInput = findViewById(R.id.productive_calls_input);
        achievedSecondaryInput = findViewById(R.id.achieved_secondary_input);
        achievedPrimaryInput = findViewById(R.id.achieved_primary_input);
        additionalNotesInput = findViewById(R.id.additional_notes_input);
        nightHoultCheckbox = findViewById(R.id.night_hoult_checkbox);
        checkoutButton = findViewById(R.id.checkout_button);
        endLocationButton = findViewById(R.id.end_location_button);
    }

    private void setupToggleButtons() {
        checkInTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectCheckIn();
            }
        });
        checkOutTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectCheckOut();
            }
        });
        selectCheckIn();
    }

    private void selectCheckIn() {
        isCheckInSelected = true;
        checkInTab.setBackgroundResource(R.drawable.toggle_segment_left_selected);
        checkOutTab.setBackgroundResource(R.drawable.toggle_segment_right_unselected);
        checkInText.setTextColor(android.graphics.Color.WHITE);
        checkOutText.setTextColor(android.graphics.Color.BLACK);
        contentFlipper.setDisplayedChild(0);
        if (!checkInSubmittedToday) {
            checkInLocationVerified = false;
            checkInLatitude = 0;
            checkInLongitude = 0;
            checkInLocationName = "";
            checkInLocationCapturedAtMillis = 0L;
            locationOnlyCheckInSaved = false;
            setSubmitButtonEnabled(true);
            setAddLocationButtonEnabled(true);
        } else {
            showCheckInSubmittedState();
        }
    }

    private void selectCheckOut() {
        isCheckInSelected = false;
        checkInTab.setBackgroundResource(R.drawable.toggle_segment_left_unselected);
        checkOutTab.setBackgroundResource(R.drawable.toggle_segment_right_selected);
        checkInText.setTextColor(android.graphics.Color.BLACK);
        checkOutText.setTextColor(android.graphics.Color.WHITE);
        contentFlipper.setDisplayedChild(1);
        checkOutLocationCaptured = false;
        checkOutLocationVerified = false;
        checkOutLatitude = 0;
        checkOutLongitude = 0;
        checkOutLocationName = "";
        checkOutLocationCapturedAtMillis = 0L;
        locationOnlyCheckOutSaved = false;
        setCheckoutSubmitButtonEnabled(true);
        setEndLocationButtonEnabled(true);
        loadTodaysCheckInForCheckout();
        loadTodayCheckOutIfAny();
    }

    private void loadTodaysCheckInForCheckout() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", null);
        if (employeeEmail == null || employeeEmail.isEmpty()) {
            if (checkoutDistributorText != null) checkoutDistributorText.setText("");
            if (checkoutBitText != null) checkoutBitText.setText("");
            return;
        }
        String today = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
        FirebaseFirestore.getInstance().collection("check_ins")
                .whereEqualTo("employeeEmail", employeeEmail)
                .whereEqualTo("date", today)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        if (checkoutDistributorText != null) checkoutDistributorText.setText("");
                        if (checkoutBitText != null) checkoutBitText.setText("");
                        return;
                    }
                    // Pick the latest by timestamp (server may not return sorted)
                    DocumentSnapshot latest = null;
                    long latestTime = 0;
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Object ts = doc.get("timestamp");
                        long t = 0;
                        if (ts instanceof Timestamp) t = ((Timestamp) ts).getSeconds();
                        if (t >= latestTime) {
                            latestTime = t;
                            latest = doc;
                        }
                    }
                    if (latest != null) {
                        String distributor = latest.getString("distributor");
                        String bit = latest.getString("bit");
                        if (checkoutDistributorText != null) checkoutDistributorText.setText(distributor != null ? distributor : "");
                        if (checkoutBitText != null) checkoutBitText.setText(bit != null ? bit : "");
                    } else {
                        if (checkoutDistributorText != null) checkoutDistributorText.setText("");
                        if (checkoutBitText != null) checkoutBitText.setText("");
                    }
                })
                .addOnFailureListener(e -> {
                    if (checkoutDistributorText != null) checkoutDistributorText.setText("");
                    if (checkoutBitText != null) checkoutBitText.setText("");
                });
    }

    private void setupSpinners() {
        // Use mutable list so adapter.clear() / add() work when we load from Firestore
        java.util.List<String> distributorList = new java.util.ArrayList<>();
        distributorList.add("Select distributor");
        distributorAdapter = new ArrayAdapter<String>(
                this,
                R.layout.spinner_selected_item,
                distributorList
        ) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view;
                if (position == distributorSpinner.getSelectedItemPosition()) {
                    textView.setTextColor(0xFF34404F);
                } else {
                    textView.setTextColor(0xBF34404F);
                }
                return view;
            }
        };
        distributorAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        distributorSpinner.setAdapter(distributorAdapter);
        
        // Bit spinner: options loaded when distributor is selected (from distributors collection)
        java.util.List<String> bitPlaceholder = new java.util.ArrayList<>();
        bitPlaceholder.add("Select bit");
        bitAdapter = new ArrayAdapter<String>(this, R.layout.spinner_selected_item, bitPlaceholder) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) view;
                tv.setTextColor(position == bitSpinner.getSelectedItemPosition() ? 0xFF34404F : 0xBF34404F);
                return view;
            }
        };
        bitAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        bitSpinner.setAdapter(bitAdapter);
        
        loadAssignedDistributors();

        distributorSpinner.setDropDownVerticalOffset(distributorSpinner.getHeight());
        distributorSpinner.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    distributorSpinner.setDropDownVerticalOffset(distributorSpinner.getHeight());
                }
                return false;
            }
        });
        distributorSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                distributorAdapter.notifyDataSetChanged();
                if (position > 0 && position <= assignedDistributorIds.size()) {
                    Runnable afterLoaded = pendingApplyTodayCheckIn ? () -> runOnUiThread(() -> {
                        applySavedCheckInBit();
                        showCheckInSubmittedState();
                        pendingApplyTodayCheckIn = false;
                    }) : null;
                    loadBitsForDistributor(assignedDistributorIds.get(position - 1), afterLoaded);
                } else {
                    bitAdapter.clear();
                    bitAdapter.add("Select bit");
                    bitAdapter.notifyDataSetChanged();
                    if (pendingApplyTodayCheckIn) {
                        showCheckInSubmittedState();
                        pendingApplyTodayCheckIn = false;
                    }
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    /** Load bits for the selected distributor from Firestore and set bit spinner options. Optional runnable after bits are loaded (e.g. when applying saved check-in). */
    private void loadBitsForDistributor(String distributorId, Runnable afterBitsLoaded) {
        bitAdapter.clear();
        bitAdapter.add("Select bit");
        bitAdapter.notifyDataSetChanged();
        if (distributorId == null || distributorId.isEmpty()) {
            if (afterBitsLoaded != null) afterBitsLoaded.run();
            return;
        }
        FirebaseFirestore.getInstance().collection("distributors").document(distributorId)
                .get()
                .addOnSuccessListener(doc -> {
                    java.util.List<String> bits = new java.util.ArrayList<>();
                    if (doc != null && doc.exists()) {
                        Object bitsObj = doc.get("bits");
                        if (bitsObj instanceof java.util.List) {
                            for (Object o : (java.util.List<?>) bitsObj) {
                                if (o != null) {
                                    String s = o.toString().trim();
                                    if (!s.isEmpty()) bits.add(s);
                                }
                            }
                        }
                        if (bits.isEmpty()) {
                            String single = doc.getString("bit");
                            if (single == null || single.isEmpty()) single = doc.getString("bitName");
                            if (single == null || single.isEmpty()) single = doc.getString("bit_name");
                            if (single != null && !single.isEmpty()) bits.add(single.trim());
                        }
                    }
                    runOnUiThread(() -> {
                        bitAdapter.clear();
                        bitAdapter.add("Select bit");
                        if (!bits.isEmpty()) bitAdapter.addAll(bits);
                        bitAdapter.notifyDataSetChanged();
                        if (afterBitsLoaded != null) afterBitsLoaded.run();
                    });
                })
                .addOnFailureListener(e -> runOnUiThread(() -> {
                    bitAdapter.clear();
                    bitAdapter.add("Select bit");
                    bitAdapter.notifyDataSetChanged();
                    if (afterBitsLoaded != null) afterBitsLoaded.run();
                }));
    }

    private void loadBitsForDistributor(String distributorId) {
        loadBitsForDistributor(distributorId, null);
    }

    /** Set bit spinner to saved value and show check-in recorded state. Called when applying today's check-in. */
    private void applySavedCheckInBit() {
        if (savedCheckInBit == null || bitAdapter == null || bitSpinner == null) return;
        for (int i = 1; i < bitAdapter.getCount(); i++) {
            if (savedCheckInBit.equals(bitAdapter.getItem(i))) {
                bitSpinner.setSelection(i);
                break;
            }
        }
    }

    private void loadAssignedDistributors() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", null);
        if (employeeEmail == null || employeeEmail.isEmpty()) {
            // Manager or not logged in: keep "Select distributor" only
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("employees")
                .whereEqualTo("email", employeeEmail)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) {
                        return;
                    }
                    DocumentSnapshot employeeDoc = task.getResult().getDocuments().get(0);
                    Object assigned = employeeDoc.get("assignedDistributorId");
                    if (assigned == null) {
                        assigned = employeeDoc.get("distributorId");
                    }
                    if (assigned == null) {
                        assigned = employeeDoc.get("assignedDistributorIds");
                    }

                    java.util.List<String> distributorIds = new java.util.ArrayList<>();
                    if (assigned instanceof String) {
                        distributorIds.add((String) assigned);
                    } else if (assigned instanceof java.util.List) {
                        for (Object o : (java.util.List<?>) assigned) {
                            if (o instanceof String) distributorIds.add((String) o);
                        }
                    }
                    if (distributorIds.isEmpty()) {
                        return;
                    }
                    assignedDistributorIds = new java.util.ArrayList<>(distributorIds);
                    fetchDistributorNames(db, distributorIds);
                });
    }

    private void fetchDistributorNames(FirebaseFirestore db, java.util.List<String> distributorIds) {
        final int total = distributorIds.size();
        final java.util.List<String> orderedNames = new java.util.ArrayList<>(java.util.Collections.nCopies(total, ""));
        final int[] fetched = {0};
        final boolean[] hadError = {false};
        for (int i = 0; i < total; i++) {
            final int index = i;
            String id = distributorIds.get(i);
            db.collection("distributors").document(id).get()
                    .addOnSuccessListener(docSnapshot -> {
                        if (docSnapshot != null && docSnapshot.exists()) {
                            String name = docSnapshot.getString("distributorName");
                            if (name == null || name.isEmpty()) name = docSnapshot.getString("distributor_name");
                            if (name == null || name.isEmpty()) name = docSnapshot.getString("name");
                            if (name == null || name.isEmpty()) name = docSnapshot.getId();
                            orderedNames.set(index, name != null ? name : "");
                        }
                        fetched[0]++;
                        if (fetched[0] == total) {
                            runOnUiThread(() -> {
                                java.util.List<String> namesOnly = new java.util.ArrayList<>();
                                java.util.List<String> idsOnly = new java.util.ArrayList<>();
                                for (int j = 0; j < total; j++) {
                                    String n = orderedNames.get(j);
                                    if (n != null && !n.trim().isEmpty()) {
                                        namesOnly.add(n);
                                        idsOnly.add(distributorIds.get(j));
                                    }
                                }
                                assignedDistributorIds = idsOnly;
                                distributorAdapter.clear();
                                distributorAdapter.add("Select distributor");
                                distributorAdapter.addAll(namesOnly);
                                distributorAdapter.notifyDataSetChanged();
                                bitAdapter.clear();
                                bitAdapter.add("Select bit");
                                bitAdapter.notifyDataSetChanged();
                                applySavedCheckInDistributorAndBitIfPending();
                            });
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (!hadError[0]) {
                            hadError[0] = true;
                            runOnUiThread(() -> Toast.makeText(CheckInOutActivity.this,
                                    "Could not load distributors. Check Firestore rules.", Toast.LENGTH_LONG).show());
                        }
                        fetched[0]++;
                        if (fetched[0] == total) {
                            runOnUiThread(() -> {
                                java.util.List<String> namesOnly = new java.util.ArrayList<>();
                                java.util.List<String> idsOnly = new java.util.ArrayList<>();
                                for (int j = 0; j < total; j++) {
                                    String n = orderedNames.get(j);
                                    if (n != null && !n.trim().isEmpty()) {
                                        namesOnly.add(n);
                                        idsOnly.add(distributorIds.get(j));
                                    }
                                }
                                assignedDistributorIds = idsOnly;
                                distributorAdapter.clear();
                                distributorAdapter.add("Select distributor");
                                distributorAdapter.addAll(namesOnly);
                                distributorAdapter.notifyDataSetChanged();
                                applySavedCheckInDistributorAndBitIfPending();
                            });
                        }
                    });
        }
    }

    /** If we have today's check-in to apply and distributor list is ready, set distributor (and bit will follow via listener). */
    private void applySavedCheckInDistributorAndBitIfPending() {
        if (!pendingApplyTodayCheckIn || savedCheckInDistributor == null || distributorAdapter == null || assignedDistributorIds == null) return;
        int pos = -1;
        for (int i = 1; i < distributorAdapter.getCount(); i++) {
            if (savedCheckInDistributor.equals(distributorAdapter.getItem(i))) {
                pos = i;
                break;
            }
        }
        if (pos >= 0 && pos <= assignedDistributorIds.size()) {
            distributorSpinner.setSelection(pos);
        } else {
            showCheckInSubmittedState();
            pendingApplyTodayCheckIn = false;
        }
    }

    /** Load today's check-in from Firestore and pre-fill the form (same UI, read-only) so user sees what they submitted. */
    private void loadTodayCheckInIfAny() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", null);
        if (employeeEmail == null || employeeEmail.isEmpty()) return;
        if (todayCheckInListener != null) {
            todayCheckInListener.remove();
            todayCheckInListener = null;
        }

        String today = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
        todayCheckInListener = FirebaseFirestore.getInstance().collection("check_ins")
                .whereEqualTo("employeeEmail", employeeEmail)
                .whereEqualTo("date", today)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) return;
                    if (snap == null || snap.isEmpty()) return;
                    DocumentSnapshot latest = null;
                    long latestTs = 0;
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Object ts = doc.get("timestamp");
                        long t = (ts instanceof Timestamp) ? ((Timestamp) ts).getSeconds() : 0;
                        if (t >= latestTs) {
                            latestTs = t;
                            latest = doc;
                        }
                    }
                    if (latest == null) return;

                    String dist = latest.getString("distributor");
                    String bit = latest.getString("bit");
                    String primaryTarget = latest.getString("primaryTarget");
                    String workingWith = latest.getString("workingWith");
                    if (dist == null) dist = "";
                    if (bit == null) bit = "";
                    if (primaryTarget == null) primaryTarget = "";
                    if (workingWith == null) workingWith = "";

                    savedCheckInDistributor = dist.trim().isEmpty() ? null : dist.trim();
                    savedCheckInBit = bit.trim().isEmpty() ? null : bit.trim();
                    savedCheckInPrimaryTarget = primaryTarget;
                    savedCheckInWorkingWith = workingWith;

                    Object loc = latest.get("checkInLocation");
                    if (loc instanceof GeoPoint) {
                        GeoPoint gp = (GeoPoint) loc;
                        checkInLocationVerified = true;
                        checkInLatitude = gp.getLatitude();
                        checkInLongitude = gp.getLongitude();
                        String savedLocName = latest.getString("checkInLocationName");
                        checkInLocationName = savedLocName != null ? savedLocName : "";
                        Object ts = latest.get("timestamp");
                        if (ts instanceof Timestamp) {
                            checkInLocationCapturedAtMillis = ((Timestamp) ts).toDate().getTime();
                        }
                    }

                    checkInSubmittedToday = true;
                    String primaryNumberForForm = primaryTarget;
                    if (primaryTarget != null && primaryTarget.contains(" / ")) {
                        primaryNumberForForm = primaryTarget.substring(0, primaryTarget.indexOf(" / ")).trim();
                    }
                    final String primaryNumberFinal = primaryNumberForForm;
                    runOnUiThread(() -> {
                        if (primaryTargetEditText != null && primaryNumberFinal != null) {
                            primaryTargetEditText.setText(primaryNumberFinal);
                        }
                        if (workingWithEditText != null && savedCheckInWorkingWith != null) {
                            workingWithEditText.setText(savedCheckInWorkingWith);
                        }
                        if (savedCheckInDistributor != null) {
                            pendingApplyTodayCheckIn = true;
                            if (distributorAdapter != null && distributorAdapter.getCount() > 1) {
                                applySavedCheckInDistributorAndBitIfPending();
                            }
                        }
                        showCheckInSubmittedState();
                    });
                });
    }

    private void setupClickListeners() {
        // Set click listener on the container for easier clicking
        View backButtonContainer = findViewById(R.id.back_button_container);
        if (backButtonContainer != null) {
            backButtonContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!validateCheckInFields()) {
                    showLocationResultDialog("Incomplete", "Please fill required check-in details (distributor, primary target) before submitting.");
                    return;
                }
                handleSubmit();
            }
        });

        addLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchAssignedLocationAndCheckCurrent(true);
            }
        });
        
        checkoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleCheckout();
            }
        });
        
        endLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchAssignedLocationAndCheckCurrent(false);
            }
        });
    }

    /** One assigned location: lat, lon, radius in meters. */
    private static class AssignedLocation {
        final double lat;
        final double lon;
        final int radiusMeters;
        final String locationName;
        AssignedLocation(double lat, double lon, int radiusMeters, String locationName) {
            this.lat = lat;
            this.lon = lon;
            this.radiusMeters = radiusMeters;
            this.locationName = locationName != null ? locationName.trim() : "";
        }
    }

    /**
     * Fetches all of the employee's assigned locations (lat, long, radius) from Firestore,
     * gets current location, and allows check-in/out if within radius of ANY assigned location.
     */
    private void fetchAssignedLocationAndCheckCurrent(boolean isAddLocation) {
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeId = prefs.getString("logged_in_employee_id", "");
        if (employeeId.isEmpty()) {
            showLocationResultDialog("Error", "Unable to identify employee. Please log in again.");
            return;
        }

        FirebaseFirestore.getInstance().collection("employees").document(employeeId)
                .get()
                .addOnSuccessListener(empSnapshot -> {
                    if (empSnapshot == null || !empSnapshot.exists()) {
                        showLocationResultDialog("Location check", "No assigned location found for your account.");
                        return;
                    }
                    // Employee may have assignedLocationIds (array of location doc IDs) - support multiple
                    List<String> locationIds = new ArrayList<>();
                    Object idsObj = empSnapshot.get("assignedLocationIds");
                    if (idsObj instanceof List) {
                        List<?> list = (List<?>) idsObj;
                        if (list != null) {
                            for (Object o : list) {
                                if (o != null) {
                                    String id = o.toString().trim();
                                    if (!id.isEmpty()) locationIds.add(id);
                                }
                            }
                        }
                    }
                    if (!locationIds.isEmpty()) {
                        fetchAllLocationsAndCheck(locationIds, isAddLocation);
                        return;
                    }
                    // Fallback: direct lat/long on employee document (single location)
                    Double assignedLat = getDoubleFromSnapshot(empSnapshot, "assignedLatitude", "latitude");
                    Double assignedLon = getDoubleFromSnapshot(empSnapshot, "assignedLongitude", "longitude");
                    if (assignedLat == null || assignedLon == null) {
                        showLocationResultDialog("Location check", "No assigned location set for your account.");
                        return;
                    }
                    int radiusMeters = DEFAULT_RADIUS_METERS;
                    Object r1 = empSnapshot.get("assignedRadius");
                    Object r2 = empSnapshot.get("radiusMeters");
                    Number radius = r1 instanceof Number ? (Number) r1 : (r2 instanceof Number ? (Number) r2 : null);
                    if (radius != null) {
                        radiusMeters = radius.intValue();
                        if (radiusMeters <= 0) radiusMeters = DEFAULT_RADIUS_METERS;
                    }
                    List<AssignedLocation> single = new ArrayList<>();
                    single.add(new AssignedLocation(assignedLat, assignedLon, radiusMeters, getLocationNameFromSnapshot(empSnapshot)));
                    runLocationCheckWithMultipleLocations(single, isAddLocation);
                })
                .addOnFailureListener(e -> showLocationResultDialog("Error", "Could not load assigned location: " + e.getMessage()));
    }

    /** Fetches all location docs by ID, builds list of AssignedLocation, then runs location check against any. */
    private void fetchAllLocationsAndCheck(List<String> locationIds, boolean isAddLocation) {
        List<AssignedLocation> assigned = new ArrayList<>();
        final int[] pending = { locationIds.size() };
        for (String locId : locationIds) {
            FirebaseFirestore.getInstance().collection("locations").document(locId)
                    .get()
                    .addOnSuccessListener(locSnapshot -> {
                        if (locSnapshot != null && locSnapshot.exists()) {
                            Double lat = getDoubleFromSnapshot(locSnapshot, "latitude", "lat", "assignedLatitude");
                            Double lon = getDoubleFromSnapshot(locSnapshot, "longitude", "lng", "lon", "assignedLongitude");
                            if (lat != null && lon != null) {
                                int radiusMeters = DEFAULT_RADIUS_METERS;
                                Object r1 = locSnapshot.get("radiusMeters");
                                Object r2 = locSnapshot.get("assignedRadius");
                                Object r3 = locSnapshot.get("radius");
                                Number radius = r1 instanceof Number ? (Number) r1 : (r2 instanceof Number ? (Number) r2 : (r3 instanceof Number ? (Number) r3 : null));
                                if (radius != null) {
                                    radiusMeters = radius.intValue();
                                    if (radiusMeters <= 0) radiusMeters = DEFAULT_RADIUS_METERS;
                                }
                                synchronized (assigned) {
                                    assigned.add(new AssignedLocation(lat, lon, radiusMeters, getLocationNameFromSnapshot(locSnapshot)));
                                }
                            }
                        }
                        synchronized (assigned) {
                            pending[0]--;
                            if (pending[0] <= 0) {
                                runOnUiThread(() -> {
                                    if (assigned.isEmpty()) {
                                        showLocationResultDialog("Location check", "Assigned location data not found or invalid.");
                                    } else {
                                        runLocationCheckWithMultipleLocations(assigned, isAddLocation);
                                    }
                                });
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        synchronized (assigned) {
                            pending[0]--;
                            if (pending[0] <= 0) {
                                runOnUiThread(() -> {
                                    if (assigned.isEmpty()) {
                                        showLocationResultDialog("Error", "Could not load locations: " + (e.getMessage() != null ? e.getMessage() : ""));
                                    } else {
                                        runLocationCheckWithMultipleLocations(assigned, isAddLocation);
                                    }
                                });
                            }
                        }
                    });
        }
    }

    /** Gets current location and checks if within radius of ANY assigned location. */
    private void runLocationCheckWithMultipleLocations(List<AssignedLocation> locations, boolean isAddLocation) {
        if (locations == null || locations.isEmpty()) {
            showLocationResultDialog("Location check", "No assigned locations to check.");
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            pendingLocationCheckIsAddLocation = isAddLocation;
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            showLocationResultDialog("Error", "Location service unavailable.");
            return;
        }
        Location lastLocation = null;
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (lastLocation == null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
        } catch (SecurityException ignored) { }
        if (lastLocation != null) {
            showDistanceMessageForMultipleLocations(
                    lastLocation.getLatitude(), lastLocation.getLongitude(), locations, isAddLocation);
            return;
        }
        Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show();
        locationResultShown = false;
        final List<AssignedLocation> locationsFinal = new ArrayList<>(locations);
        pendingLocationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (location == null) return;
                synchronized (CheckInOutActivity.this) {
                    if (locationResultShown) return;
                    locationResultShown = true;
                }
                cancelLocationRequest();
                runOnUiThread(() -> showDistanceMessageForMultipleLocations(
                        location.getLatitude(), location.getLongitude(), locationsFinal, isAddLocation));
            }
            @Override
            public void onProviderDisabled(String provider) {}
            @Override
            public void onProviderEnabled(String provider) {}
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}
        };
        pendingLocationManager = locationManager;
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 0, 0, pendingLocationListener, Looper.getMainLooper());
            }
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, 0, 0, pendingLocationListener, Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            showLocationResultDialog("Permission needed", "Location permission is needed.");
            return;
        }
        locationTimeoutRunnable = () -> {
            synchronized (CheckInOutActivity.this) {
                if (locationResultShown) return;
                locationResultShown = true;
            }
            cancelLocationRequest();
            runOnUiThread(() -> showLocationResultDialog("Location unavailable",
                    "Could not get your location. Please ensure location is enabled and try again."));
        };
        locationHandler.postDelayed(locationTimeoutRunnable, LOCATION_TIMEOUT_MS);
    }

    private void showDistanceMessageForMultipleLocations(double currentLat, double currentLon,
                                                          List<AssignedLocation> locations, boolean isAddLocation) {
        double minDistanceMeters = Double.MAX_VALUE;
        AssignedLocation withinLocation = null;
        for (AssignedLocation loc : locations) {
            double d = distanceInMeters(currentLat, currentLon, loc.lat, loc.lon);
            if (d <= loc.radiusMeters) {
                withinLocation = loc;
                break;
            }
            if (d < minDistanceMeters) minDistanceMeters = d;
        }
        String action = isAddLocation ? "Check In" : "Check Out";
        String title = "Location check";
        String message;
        if (withinLocation != null) {
            if (isAddLocation) {
                checkInLocationVerified = true;
                checkInLatitude = currentLat;
                checkInLongitude = currentLon;
                checkInLocationName = withinLocation.locationName;
                checkInLocationCapturedAtMillis = System.currentTimeMillis();
                setSubmitButtonEnabled(true);
                if (!locationOnlyCheckInSaved) {
                    saveCheckInLocationOnly(currentLat, currentLon, checkInLocationCapturedAtMillis, checkInLocationName);
                    locationOnlyCheckInSaved = true;
                }
                if (checkInSubmittedToday) {
                    setAddLocationButtonEnabled(false);
                }
                message = "Location and map link saved. You may optionally fill the form and tap Submit to add details.";
            } else {
                checkOutLocationVerified = true;
                checkOutLatitude = currentLat;
                checkOutLongitude = currentLon;
                checkOutLocationName = withinLocation.locationName;
                checkOutLocationCapturedAtMillis = System.currentTimeMillis();
                checkOutLocationCaptured = true;
                setCheckoutSubmitButtonEnabled(true);
                if (!locationOnlyCheckOutSaved) {
                    saveCheckOutLocationOnly(currentLat, currentLon, checkOutLocationCapturedAtMillis, checkOutLocationName);
                    locationOnlyCheckOutSaved = true;
                }
                if (checkOutSubmittedToday) {
                    setEndLocationButtonEnabled(false);
                }
                message = "Check-out (location) saved. You may optionally fill the form and submit to add details.";
            }
        } else {
            int dist = (int) Math.round(minDistanceMeters);
            if (!isAddLocation) {
                // Still save check-out location when out of range so we have a record of where they checked out
                checkOutLatitude = currentLat;
                checkOutLongitude = currentLon;
                checkOutLocationName = "";
                checkOutLocationCapturedAtMillis = System.currentTimeMillis();
                checkOutLocationCaptured = true;
                setCheckoutSubmitButtonEnabled(true);
                if (!locationOnlyCheckOutSaved) {
                    saveCheckOutLocationOnly(currentLat, currentLon, checkOutLocationCapturedAtMillis, checkOutLocationName);
                    locationOnlyCheckOutSaved = true;
                }
                message = "Check-out (location) saved. You were " + dist + " m from nearest assigned location.";
            } else {
                message = "You are " + dist + " meters away from your nearest assigned location. Cannot submit.";
            }
        }
        showLocationResultDialog(title, message);
    }

    private void runLocationCheck(double assignedLat, double assignedLon, int radiusMeters, boolean isAddLocation) {
        List<AssignedLocation> single = new ArrayList<>();
        single.add(new AssignedLocation(assignedLat, assignedLon, radiusMeters, ""));
        runLocationCheckWithMultipleLocations(single, isAddLocation);
    }

    private Double getDoubleFromSnapshot(DocumentSnapshot doc, String... keys) {
        for (String key : keys) {
            Object val = doc.get(key);
            if (val == null) continue;
            if (val instanceof Number) return ((Number) val).doubleValue();
            if (val instanceof String) {
                try {
                    return Double.parseDouble((String) val);
                } catch (NumberFormatException ignored) { }
            }
        }
        return null;
    }

    private String getLocationNameFromSnapshot(DocumentSnapshot doc) {
        if (doc == null) return "";
        String[] keys = new String[]{
                "locationName", "name", "title", "assignedLocationName",
                "location", "areaName", "address", "location_name",
                "siteName", "branchName", "displayName"
        };
        for (String key : keys) {
            Object v = doc.get(key);
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty()) return s;
            }
        }
        return "";
    }

    private void cancelLocationRequest() {
        locationHandler.removeCallbacks(locationTimeoutRunnable);
        if (pendingLocationManager != null && pendingLocationListener != null) {
            try {
                pendingLocationManager.removeUpdates(pendingLocationListener);
            } catch (SecurityException ignored) { }
            pendingLocationManager = null;
            pendingLocationListener = null;
        }
    }

    private void showDistanceMessage(double currentLat, double currentLon, double assignedLat, double assignedLon, int radiusMeters, boolean isAddLocation) {
        double distanceMeters = distanceInMeters(currentLat, currentLon, assignedLat, assignedLon);
        int dist = (int) Math.round(distanceMeters);
        String action = isAddLocation ? "Check In" : "Check Out";
        String title = "Location check";
        String message;
        if (distanceMeters <= radiusMeters) {
            if (isAddLocation) {
                checkInLocationVerified = true;
                checkInLatitude = currentLat;
                checkInLongitude = currentLon;
                checkInLocationName = "";
                checkInLocationCapturedAtMillis = System.currentTimeMillis();
                setSubmitButtonEnabled(true);
                if (!locationOnlyCheckInSaved) {
                    saveCheckInLocationOnly(currentLat, currentLon, checkInLocationCapturedAtMillis, checkInLocationName);
                    locationOnlyCheckInSaved = true;
                }
                if (checkInSubmittedToday) {
                    setAddLocationButtonEnabled(false);
                }
                message = "Location and map link saved. You may optionally fill the form and tap Submit to add details.";
            } else {
                checkOutLocationVerified = true;
                checkOutLatitude = currentLat;
                checkOutLongitude = currentLon;
                checkOutLocationName = "";
                checkOutLocationCapturedAtMillis = System.currentTimeMillis();
                checkOutLocationCaptured = true;
                setCheckoutSubmitButtonEnabled(true);
                if (!locationOnlyCheckOutSaved) {
                    saveCheckOutLocationOnly(currentLat, currentLon, checkOutLocationCapturedAtMillis, checkOutLocationName);
                    locationOnlyCheckOutSaved = true;
                }
                if (checkOutSubmittedToday) {
                    setEndLocationButtonEnabled(false);
                }
                message = "Check-out (location) saved. You may optionally fill the form and submit to add details.";
            }
        } else {
            if (!isAddLocation) {
                // Still save check-out location when out of range
                checkOutLatitude = currentLat;
                checkOutLongitude = currentLon;
                checkOutLocationName = "";
                checkOutLocationCapturedAtMillis = System.currentTimeMillis();
                checkOutLocationCaptured = true;
                setCheckoutSubmitButtonEnabled(true);
                if (!locationOnlyCheckOutSaved) {
                    saveCheckOutLocationOnly(currentLat, currentLon, checkOutLocationCapturedAtMillis, checkOutLocationName);
                    locationOnlyCheckOutSaved = true;
                }
                message = "Check-out (location) saved. You were " + dist + " m from assigned location.";
            } else {
                message = "You are " + dist + " meters away from your assigned Location. Cannot submit.";
            }
        }
        showLocationResultDialog(title, message);
    }

    private String getSelectedBit() {
        if (bitSpinner == null || bitSpinner.getSelectedItem() == null) return "";
        String s = bitSpinner.getSelectedItem().toString().trim();
        return "Select bit".equals(s) ? "" : s;
    }

    private boolean validateCheckInFields() {
        String distributor = distributorSpinner.getSelectedItem().toString();
        String primaryTarget = primaryTargetEditText.getText().toString().trim();
        if (distributor.isEmpty() || "Select distributor".equals(distributor)) return false;
        if (primaryTarget.isEmpty()) return false;
        return true;
    }

    private void setSubmitButtonEnabled(boolean enabled) {
        submitButton.setEnabled(enabled);
        submitButton.setAlpha(enabled ? 1f : 0.6f);
    }

    private void setCheckoutSubmitButtonEnabled(boolean enabled) {
        checkoutButton.setEnabled(enabled);
        checkoutButton.setAlpha(enabled ? 1f : 0.6f);
    }

    private boolean validateCheckOutFields() {
        String totalCalls = totalCallsInput.getText().toString().trim();
        String productiveCalls = productiveCallsInput.getText().toString().trim();
        if (totalCalls.isEmpty()) return false;
        if (productiveCalls.isEmpty()) return false;
        return true;
    }

    /** Saves check-in location. If user already submitted check-in form today (same distributor/bit), updates that same document with location. */
    private void saveCheckInLocationOnly(double lat, double lon, long capturedAtMillis, String locationName) {
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        String employeeName = prefs.getString("logged_in_employee_name", "");
        Date capturedAt = new Date(capturedAtMillis > 0 ? capturedAtMillis : System.currentTimeMillis());
        String dateString = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(capturedAt);
        String timeString = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(capturedAt);
        Timestamp capturedTimestamp = new Timestamp(capturedAt);

        FirebaseFirestore.getInstance().collection("check_ins")
                .whereEqualTo("employeeEmail", employeeEmail)
                .whereEqualTo("date", dateString)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap != null && !snap.isEmpty()) {
                        DocumentSnapshot existing = snap.getDocuments().get(0);
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            Object ts = doc.get("timestamp");
                            long t = (ts instanceof Timestamp) ? ((Timestamp) ts).getSeconds() : 0;
                            Object ets = existing.get("timestamp");
                            long et = (ets instanceof Timestamp) ? ((Timestamp) ets).getSeconds() : 0;
                            if (t >= et) existing = doc;
                        }
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("checkInLocation", new GeoPoint(lat, lon));
                        updates.put("checkInMapsLink", buildGoogleMapsLink(lat, lon));
                        updates.put("checkInLocationName", locationName != null ? locationName : "");
                        updates.put("time", timeString);
                        updates.put("timestamp", capturedTimestamp);
                        existing.getReference().update(updates)
                                .addOnSuccessListener(aVoid ->
                                        Toast.makeText(CheckInOutActivity.this, "Check-in location saved (same record).", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(CheckInOutActivity.this, "Failed to save location: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        return;
                    }
                    String distributor = distributorSpinner != null && distributorSpinner.getSelectedItem() != null
                            ? distributorSpinner.getSelectedItem().toString() : "";
                    if ("Select distributor".equals(distributor)) distributor = "";
                    String bit = getSelectedBit();
                    String primaryTargetInput = primaryTargetEditText != null ? primaryTargetEditText.getText().toString().trim() : "";
                    String primaryTarget = primaryTargetInput.isEmpty() ? "" : primaryTargetInput + " / 200kg";
                    String workingWith = workingWithEditText != null ? workingWithEditText.getText().toString().trim() : "";
                    Map<String, Object> checkIn = new HashMap<>();
                    checkIn.put("distributor", distributor != null ? distributor : "");
                    checkIn.put("bit", bit != null ? bit : "");
                    checkIn.put("primaryTarget", primaryTarget != null ? primaryTarget : "");
                    checkIn.put("workingWith", workingWith != null ? workingWith : "");
                    checkIn.put("checkInLocation", new GeoPoint(lat, lon));
                    checkIn.put("checkInMapsLink", buildGoogleMapsLink(lat, lon));
                    checkIn.put("checkInLocationName", locationName != null ? locationName : "");
                    checkIn.put("employeeEmail", employeeEmail);
                    checkIn.put("employeeName", employeeName);
                    checkIn.put("date", dateString);
                    checkIn.put("time", timeString);
                    checkIn.put("timestamp", capturedTimestamp);
                    FirebaseFirestore.getInstance().collection("check_ins")
                            .add(checkIn)
                            .addOnSuccessListener(aVoid ->
                                    Toast.makeText(CheckInOutActivity.this, "Check-in (location) saved.", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(CheckInOutActivity.this, "Failed to save check-in: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(CheckInOutActivity.this, "Failed to save check-in: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    /** Saves check-out location. If user already submitted checkout details today, updates that same document with location (so distributor/bit stay). */
    private void saveCheckOutLocationOnly(double lat, double lon, long capturedAtMillis, String locationName) {
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        String employeeName = prefs.getString("logged_in_employee_name", "");
        Date capturedAt = new Date(capturedAtMillis > 0 ? capturedAtMillis : System.currentTimeMillis());
        String dateString = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(capturedAt);
        String timeString = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(capturedAt);
        Timestamp capturedTimestamp = new Timestamp(capturedAt);

        FirebaseFirestore.getInstance().collection("check_outs")
                .whereEqualTo("employeeEmail", employeeEmail)
                .whereEqualTo("date", dateString)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap != null && !snap.isEmpty()) {
                        DocumentSnapshot existing = snap.getDocuments().get(0);
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            Object ts = doc.get("timestamp");
                            long t = (ts instanceof Timestamp) ? ((Timestamp) ts).getSeconds() : 0;
                            Object ets = existing.get("timestamp");
                            long et = (ets instanceof Timestamp) ? ((Timestamp) ets).getSeconds() : 0;
                            if (t >= et) existing = doc;
                        }
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("checkOutLocation", new GeoPoint(lat, lon));
                        updates.put("checkOutMapsLink", buildGoogleMapsLink(lat, lon));
                        updates.put("checkOutLocationName", locationName != null ? locationName : "");
                        updates.put("time", timeString);
                        updates.put("timestamp", capturedTimestamp);
                        existing.getReference().update(updates)
                                .addOnSuccessListener(aVoid ->
                                        Toast.makeText(CheckInOutActivity.this, "Check-out location saved (same record).", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e ->
                                        Toast.makeText(CheckInOutActivity.this, "Failed to save location: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        return;
                    }
                    String distributor = checkoutDistributorText != null ? checkoutDistributorText.getText().toString().trim() : "";
                    String bit = checkoutBitText != null ? checkoutBitText.getText().toString().trim() : "";
                    String totalCalls = totalCallsInput != null ? totalCallsInput.getText().toString().trim() : "";
                    String productiveCalls = productiveCallsInput != null ? productiveCallsInput.getText().toString().trim() : "";
                    String achievedSecondary = achievedSecondaryInput != null ? achievedSecondaryInput.getText().toString().trim() : "";
                    String achievedPrimary = achievedPrimaryInput != null ? achievedPrimaryInput.getText().toString().trim() : "";
                    String additionalNotes = additionalNotesInput != null ? additionalNotesInput.getText().toString().trim() : "";
                    boolean isNightHoult = nightHoultCheckbox != null && nightHoultCheckbox.isChecked();
                    Map<String, Object> checkOut = new HashMap<>();
                    checkOut.put("distributor", distributor != null ? distributor : "");
                    checkOut.put("bit", bit != null ? bit : "");
                    checkOut.put("checkOutLocation", new GeoPoint(lat, lon));
                    checkOut.put("checkOutMapsLink", buildGoogleMapsLink(lat, lon));
                    checkOut.put("checkOutLocationName", locationName != null ? locationName : "");
                    checkOut.put("totalCalls", totalCalls != null ? totalCalls : "");
                    checkOut.put("productiveCalls", productiveCalls != null ? productiveCalls : "");
                    checkOut.put("achievedSecondary", achievedSecondary != null ? achievedSecondary : "");
                    checkOut.put("achievedPrimary", achievedPrimary != null ? achievedPrimary : "");
                    checkOut.put("additionalNotes", additionalNotes != null ? additionalNotes : "");
                    checkOut.put("nightHoult", isNightHoult);
                    checkOut.put("employeeEmail", employeeEmail);
                    checkOut.put("employeeName", employeeName);
                    checkOut.put("date", dateString);
                    checkOut.put("time", timeString);
                    checkOut.put("timestamp", capturedTimestamp);
                    FirebaseFirestore.getInstance().collection("check_outs")
                            .add(checkOut)
                            .addOnSuccessListener(aVoid ->
                                    Toast.makeText(CheckInOutActivity.this, "Check-out (location) saved.", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(CheckInOutActivity.this, "Failed to save check-out: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(CheckInOutActivity.this, "Failed to save check-out: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showLocationResultDialog(String title, String message) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_location_result);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.6f);
        }
        TextView titleView = dialog.findViewById(R.id.location_dialog_title);
        TextView messageView = dialog.findViewById(R.id.location_dialog_message);
        Button okButton = dialog.findViewById(R.id.location_dialog_ok);
        if (titleView != null) titleView.setText(title);
        if (messageView != null) messageView.setText(message);
        if (okButton != null) {
            okButton.setOnClickListener(v -> dialog.dismiss());
        }
        dialog.setCancelable(true);
        dialog.show();
    }

    /** Builds Google Maps link for the exact lat/long (place where user checked in/out). */
    private static String buildGoogleMapsLink(double lat, double lon) {
        return "https://www.google.com/maps?q=" + lat + "," + lon;
    }

    /** Haversine formula: distance in meters between two lat/lon points. */
    private static double distanceInMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void handleSubmit() {
        String distributor = distributorSpinner.getSelectedItem().toString();
        String bit = getSelectedBit();
        String primaryTargetInput = primaryTargetEditText.getText().toString().trim();
        String primaryTarget = primaryTargetInput.isEmpty() ? " / 200kg" : primaryTargetInput + " / 200kg";
        String workingWith = workingWithEditText.getText().toString().trim();

        if (distributor.isEmpty() || "Select distributor".equals(distributor)) {
            Toast.makeText(this, "Please select a distributor", Toast.LENGTH_SHORT).show();
            return;
        }

        setSubmitButtonEnabled(false);

        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        String employeeName = prefs.getString("logged_in_employee_name", "");

        String dateString = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
        String timeString = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        if (checkInLocationCapturedAtMillis > 0L) {
            Date capturedAt = new Date(checkInLocationCapturedAtMillis);
            dateString = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(capturedAt);
            timeString = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(capturedAt);
        }

        Map<String, Object> checkIn = new HashMap<>();
        checkIn.put("distributor", distributor);
        checkIn.put("bit", bit);
        checkIn.put("primaryTarget", primaryTarget);
        if (checkInLocationVerified) {
            checkIn.put("checkInLocation", new GeoPoint(checkInLatitude, checkInLongitude));
            checkIn.put("checkInMapsLink", buildGoogleMapsLink(checkInLatitude, checkInLongitude));
            checkIn.put("checkInLocationName", checkInLocationName != null ? checkInLocationName : "");
        }
        checkIn.put("workingWith", workingWith);
        checkIn.put("employeeEmail", employeeEmail);
        checkIn.put("employeeName", employeeName);
        checkIn.put("date", dateString);
        checkIn.put("time", timeString);
        if (checkInLocationCapturedAtMillis > 0L) {
            checkIn.put("timestamp", new Timestamp(new Date(checkInLocationCapturedAtMillis)));
        } else {
            checkIn.put("timestamp", FieldValue.serverTimestamp());
        }

        FirebaseFirestore.getInstance().collection("check_ins")
                .whereEqualTo("employeeEmail", employeeEmail)
                .whereEqualTo("date", dateString)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap != null && !snap.isEmpty()) {
                        DocumentSnapshot existing = snap.getDocuments().get(0);
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            Object ts = doc.get("timestamp");
                            long t = (ts instanceof Timestamp) ? ((Timestamp) ts).getSeconds() : 0;
                            Object ets = existing.get("timestamp");
                            long et = (ets instanceof Timestamp) ? ((Timestamp) ets).getSeconds() : 0;
                            if (t >= et) existing = doc;
                        }
                        existing.getReference().update(checkIn)
                                .addOnSuccessListener(aVoid -> {
                                    setSubmitButtonEnabled(true);
                                    checkInSubmittedToday = true;
                                    showCheckInSubmittedState();
                                    Toast.makeText(CheckInOutActivity.this, "Check In saved successfully", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> {
                                    setSubmitButtonEnabled(true);
                                    Toast.makeText(CheckInOutActivity.this, "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                        return;
                    }
                    FirebaseFirestore.getInstance().collection("check_ins")
                            .add(checkIn)
                            .addOnSuccessListener(aVoid -> {
                                setSubmitButtonEnabled(true);
                                checkInSubmittedToday = true;
                                showCheckInSubmittedState();
                                Toast.makeText(CheckInOutActivity.this, "Check In saved successfully", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                setSubmitButtonEnabled(true);
                                Toast.makeText(CheckInOutActivity.this, "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setSubmitButtonEnabled(true);
                    Toast.makeText(CheckInOutActivity.this, "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    /** Makes check-in form read-only and shows "Check-in recorded" so user sees what they submitted until checkout. */
    private void showCheckInSubmittedState() {
        if (distributorSpinner != null) {
            distributorSpinner.setEnabled(false);
            distributorSpinner.setClickable(false);
        }
        if (bitSpinner != null) {
            bitSpinner.setEnabled(false);
            bitSpinner.setClickable(false);
        }
        if (primaryTargetEditText != null) {
            primaryTargetEditText.setEnabled(false);
            primaryTargetEditText.setFocusable(false);
        }
        if (workingWithEditText != null) {
            workingWithEditText.setEnabled(false);
            workingWithEditText.setFocusable(false);
        }
        if (submitButton != null) {
            submitButton.setClickable(false);
            submitButton.setEnabled(false);
            submitButton.setAlpha(0.6f);
            if (submitButton instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) submitButton;
                if (group.getChildCount() > 0) {
                    View child = group.getChildAt(0);
                    if (child instanceof TextView) {
                        ((TextView) child).setText("Check-in recorded");
                    }
                }
            }
        }
        if (addLocationButton != null && checkInLocationVerified && checkInSubmittedToday) {
            setAddLocationButtonEnabled(false);
        }
    }

    private void setAddLocationButtonEnabled(boolean enabled) {
        if (addLocationButton == null) return;
        addLocationButton.setEnabled(enabled);
        addLocationButton.setClickable(enabled);
        addLocationButton.setAlpha(enabled ? 1f : 0.6f);
        addLocationButton.setBackgroundResource(enabled ? R.drawable.check_in_button_background : R.drawable.check_in_button_disabled);
    }

    private void setEndLocationButtonEnabled(boolean enabled) {
        if (endLocationButton == null) return;
        endLocationButton.setEnabled(enabled);
        endLocationButton.setClickable(enabled);
        endLocationButton.setAlpha(enabled ? 1f : 0.6f);
        endLocationButton.setBackgroundResource(enabled ? R.drawable.check_in_button_background : R.drawable.check_in_button_disabled);
    }

    /** Makes checkout form read-only and shows submitted state until checkout location is submitted. */
    private void showCheckOutSubmittedState() {
        if (totalCallsInput != null) {
            totalCallsInput.setEnabled(false);
            totalCallsInput.setFocusable(false);
        }
        if (productiveCallsInput != null) {
            productiveCallsInput.setEnabled(false);
            productiveCallsInput.setFocusable(false);
        }
        if (achievedSecondaryInput != null) {
            achievedSecondaryInput.setEnabled(false);
            achievedSecondaryInput.setFocusable(false);
        }
        if (achievedPrimaryInput != null) {
            achievedPrimaryInput.setEnabled(false);
            achievedPrimaryInput.setFocusable(false);
        }
        if (additionalNotesInput != null) {
            additionalNotesInput.setEnabled(false);
            additionalNotesInput.setFocusable(false);
        }
        if (nightHoultCheckbox != null) {
            nightHoultCheckbox.setEnabled(false);
            nightHoultCheckbox.setClickable(false);
        }
        if (checkoutButton != null) {
            checkoutButton.setClickable(false);
            checkoutButton.setEnabled(false);
            checkoutButton.setAlpha(0.6f);
            checkoutButton.setBackgroundResource(R.drawable.check_in_button_disabled);
            if (checkoutButton instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) checkoutButton;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    if (child instanceof TextView) {
                        ((TextView) child).setText("Check-out recorded");
                        break;
                    }
                }
            }
        }
        if (endLocationButton != null && checkOutSubmittedToday && checkOutLocationVerified) {
            setEndLocationButtonEnabled(false);
        }
    }

    private void loadTodayCheckOutIfAny() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        if (employeeEmail.isEmpty()) return;
        if (todayCheckOutListener != null) {
            todayCheckOutListener.remove();
            todayCheckOutListener = null;
        }
        String today = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
        todayCheckOutListener = FirebaseFirestore.getInstance().collection("check_outs")
                .whereEqualTo("employeeEmail", employeeEmail)
                .whereEqualTo("date", today)
                .addSnapshotListener((snap, err) -> {
                    if (err != null) return;
                    if (snap == null || snap.isEmpty()) return;
                    DocumentSnapshot latest = null;
                    long latestTime = 0;
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Object ts = doc.get("timestamp");
                        long t = (ts instanceof Timestamp) ? ((Timestamp) ts).getSeconds() : 0;
                        if (t >= latestTime) {
                            latestTime = t;
                            latest = doc;
                        }
                    }
                    if (latest == null) return;
                    final DocumentSnapshot latestDoc = latest;
                    runOnUiThread(() -> {
                        String totalCalls = latestDoc.getString("totalCalls");
                        String productiveCalls = latestDoc.getString("productiveCalls");
                        String achievedSec = latestDoc.getString("achievedSecondary");
                        String achievedPri = latestDoc.getString("achievedPrimary");
                        String notes = latestDoc.getString("additionalNotes");
                        Object nh = latestDoc.get("nightHoult");
                        boolean nightHoult = nh instanceof Boolean && (Boolean) nh;
                        if (totalCallsInput != null && totalCalls != null) totalCallsInput.setText(totalCalls);
                        if (productiveCallsInput != null && productiveCalls != null) productiveCallsInput.setText(productiveCalls);
                        if (achievedSecondaryInput != null) achievedSecondaryInput.setText(achievedSec != null ? achievedSec : "");
                        if (achievedPrimaryInput != null) achievedPrimaryInput.setText(achievedPri != null ? achievedPri : "");
                        if (additionalNotesInput != null) additionalNotesInput.setText(notes != null ? notes : "");
                        if (nightHoultCheckbox != null) nightHoultCheckbox.setChecked(nightHoult);
                        Object loc = latestDoc.get("checkOutLocation");
                        if (loc instanceof GeoPoint) {
                            checkOutLocationVerified = true;
                            checkOutLatitude = ((GeoPoint) loc).getLatitude();
                            checkOutLongitude = ((GeoPoint) loc).getLongitude();
                            locationOnlyCheckOutSaved = true;
                            Object ts = latestDoc.get("timestamp");
                            if (ts instanceof Timestamp) {
                                checkOutLocationCapturedAtMillis = ((Timestamp) ts).toDate().getTime();
                                checkOutLocationCaptured = true;
                            }
                        }
                        String savedLocName = latestDoc.getString("checkOutLocationName");
                        checkOutLocationName = savedLocName != null ? savedLocName : "";
                        checkOutSubmittedToday = true;
                        showCheckOutSubmittedState();
                    });
                });
    }

    private void handleCheckout() {
        String totalCalls = totalCallsInput.getText().toString().trim();
        String productiveCalls = productiveCallsInput.getText().toString().trim();
        String achievedSecondary = achievedSecondaryInput.getText().toString().trim();
        String achievedPrimary = achievedPrimaryInput.getText().toString().trim();
        String additionalNotes = additionalNotesInput.getText().toString().trim();
        boolean isNightHoult = nightHoultCheckbox != null && nightHoultCheckbox.isChecked();

        if (totalCalls.isEmpty()) {
            Toast.makeText(this, "Please enter total calls", Toast.LENGTH_SHORT).show();
            return;
        }

        if (productiveCalls.isEmpty()) {
            Toast.makeText(this, "Please enter productive calls", Toast.LENGTH_SHORT).show();
            return;
        }

        setCheckoutSubmitButtonEnabled(false);

        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        String employeeName = prefs.getString("logged_in_employee_name", "");
        String distributor = checkoutDistributorText != null ? checkoutDistributorText.getText().toString().trim() : "";
        String bit = checkoutBitText != null ? checkoutBitText.getText().toString().trim() : "";

        String dateString = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
        String timeString = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        if (checkOutLocationCapturedAtMillis > 0L) {
            Date capturedAt = new Date(checkOutLocationCapturedAtMillis);
            dateString = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(capturedAt);
            timeString = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(capturedAt);
        }

        Map<String, Object> checkOut = new HashMap<>();
        checkOut.put("distributor", distributor);
        checkOut.put("bit", bit);
        if (checkOutLocationCaptured) {
            checkOut.put("checkOutLocation", new GeoPoint(checkOutLatitude, checkOutLongitude));
            checkOut.put("checkOutMapsLink", buildGoogleMapsLink(checkOutLatitude, checkOutLongitude));
            checkOut.put("checkOutLocationName", checkOutLocationName != null ? checkOutLocationName : "");
        }
        checkOut.put("totalCalls", totalCalls);
        checkOut.put("productiveCalls", productiveCalls);
        checkOut.put("achievedSecondary", achievedSecondary);
        checkOut.put("achievedPrimary", achievedPrimary);
        checkOut.put("additionalNotes", additionalNotes);
        checkOut.put("nightHoult", isNightHoult);
        checkOut.put("employeeEmail", employeeEmail);
        checkOut.put("employeeName", employeeName);
        checkOut.put("date", dateString);
        checkOut.put("time", timeString);
        if (checkOutLocationCapturedAtMillis > 0L) {
            checkOut.put("timestamp", new Timestamp(new Date(checkOutLocationCapturedAtMillis)));
        } else {
            checkOut.put("timestamp", FieldValue.serverTimestamp());
        }

        FirebaseFirestore.getInstance().collection("check_outs")
                .whereEqualTo("employeeEmail", employeeEmail)
                .whereEqualTo("date", dateString)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap != null && !snap.isEmpty()) {
                        DocumentSnapshot existing = snap.getDocuments().get(0);
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            Object ts = doc.get("timestamp");
                            long t = (ts instanceof Timestamp) ? ((Timestamp) ts).getSeconds() : 0;
                            Object ets = existing.get("timestamp");
                            long et = (ets instanceof Timestamp) ? ((Timestamp) ets).getSeconds() : 0;
                            if (t >= et) existing = doc;
                        }
                        existing.getReference().update(checkOut)
                                .addOnSuccessListener(aVoid -> {
                                    checkOutSubmittedToday = true;
                                    showCheckOutSubmittedState();
                                    Toast.makeText(CheckInOutActivity.this, "Check Out saved successfully", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> {
                                    setCheckoutSubmitButtonEnabled(true);
                                    Toast.makeText(CheckInOutActivity.this, "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                        return;
                    }
                    FirebaseFirestore.getInstance().collection("check_outs")
                            .add(checkOut)
                            .addOnSuccessListener(aVoid -> {
                                checkOutSubmittedToday = true;
                                showCheckOutSubmittedState();
                                Toast.makeText(CheckInOutActivity.this, "Check Out saved successfully", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                setCheckoutSubmitButtonEnabled(true);
                                Toast.makeText(CheckInOutActivity.this, "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    setCheckoutSubmitButtonEnabled(true);
                    Toast.makeText(CheckInOutActivity.this, "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}

