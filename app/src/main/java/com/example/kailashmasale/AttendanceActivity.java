package com.example.kailashmasale;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AttendanceActivity extends AppCompatActivity {

    private static final String DATE_FORMAT_QUERY = "dd-MM-yyyy";
    private static final String DATE_FORMAT_DISPLAY = "dd MMM yyyy";

    private ImageButton backButton;
    private TextView dateDisplay;
    private LinearLayout dateSelectorContainer;
    private LinearLayout gridButton;
    private LinearLayout uploadButton;
    private LinearLayout checkInOutButton;
    private LinearLayout teamAttendanceTableBody;

    private Calendar selectedDate = Calendar.getInstance();
    private List<String> memberIds = new ArrayList<>();
    private List<String> memberNames = new ArrayList<>();
    private List<String> memberEmails = new ArrayList<>();
    private boolean teamLoaded = false;
    private static final int DEFAULT_LOCATION_RADIUS_METERS = 500;
    private static final int LOCATION_MATCH_TOLERANCE_METERS = 75;

    private static class LocationRef {
        final String name;
        final double lat;
        final double lon;
        final int radiusMeters;

        LocationRef(String name, double lat, double lon, int radiusMeters) {
            this.name = name != null ? name.trim() : "";
            this.lat = lat;
            this.lon = lon;
            this.radiusMeters = radiusMeters > 0 ? radiusMeters : DEFAULT_LOCATION_RADIUS_METERS;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getWindow() != null) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));
            getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        }

        setContentView(R.layout.activity_attendance);

        initializeViews();
        setupClickListeners();
        updateDateDisplay();
        loadAssignedTeamMembers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (teamLoaded && memberIds != null && !memberIds.isEmpty()) {
            loadCICOForSelectedDate();
        } else {
            loadAssignedTeamMembers();
        }
    }

    private void initializeViews() {
        backButton = findViewById(R.id.back_button);
        dateDisplay = findViewById(R.id.date_display);
        dateSelectorContainer = findViewById(R.id.date_selector_container);
        gridButton = findViewById(R.id.grid_button_layout);
        uploadButton = findViewById(R.id.upload_button_layout);
        checkInOutButton = findViewById(R.id.check_in_out_button);
        teamAttendanceTableBody = findViewById(R.id.team_attendance_table_body);
    }

    private void updateDateDisplay() {
        if (dateDisplay != null) {
            dateDisplay.setText(new SimpleDateFormat(DATE_FORMAT_DISPLAY, Locale.getDefault()).format(selectedDate.getTime()));
        }
    }

    private void showDatePicker() {
        int y = selectedDate.get(Calendar.YEAR);
        int m = selectedDate.get(Calendar.MONTH);
        int d = selectedDate.get(Calendar.DAY_OF_MONTH);
        DatePickerDialog picker = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                selectedDate.set(Calendar.YEAR, year);
                selectedDate.set(Calendar.MONTH, month);
                selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                updateDateDisplay();
                loadCICOForSelectedDate();
            }
        }, y, m, d);
        picker.show();
    }

    /** Load manager's assignedTeamMemberIds from employees doc, fetch each member's name, show in table. */
    private void loadAssignedTeamMembers() {
        if (teamAttendanceTableBody == null) return;
        teamAttendanceTableBody.removeAllViews();
        teamLoaded = false;

        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String managerId = prefs.getString("logged_in_employee_id", "");
        if (managerId.isEmpty()) {
            addMessageRow("Please log in.");
            return;
        }

        FirebaseFirestore.getInstance().collection("employees").document(managerId)
                .get()
                .addOnSuccessListener(managerDoc -> {
                    if (managerDoc == null || !managerDoc.exists()) {
                        runOnUiThread(() -> addMessageRow("No manager profile found."));
                        return;
                    }
                    Object idsObj = managerDoc.get("assignedTeamMemberIds");
                    List<String> memberIds = new ArrayList<>();
                    if (idsObj instanceof List) {
                        for (Object o : (List<?>) idsObj) {
                            if (o != null) {
                                String id = o.toString().trim();
                                if (!id.isEmpty()) memberIds.add(id);
                            }
                        }
                    }
                    if (memberIds.isEmpty()) {
                        runOnUiThread(() -> addMessageRow("No team members assigned."));
                        return;
                    }
                    this.memberIds = new ArrayList<>(memberIds);
                    fetchTeamMembersBatchAndPopulate(memberIds);
                })
                .addOnFailureListener(e -> runOnUiThread(() -> addMessageRow("Failed to load team.")));
    }

    private void fetchTeamMembersBatchAndPopulate(List<String> ids) {
        // Pre-fill arrays to preserve row ordering
        final List<String> names = new ArrayList<>();
        final List<String> emails = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            names.add("(Loading...)");
            emails.add("");
        }
        memberNames = new ArrayList<>(names);
        memberEmails = new ArrayList<>(emails);
        runOnUiThread(() -> populateTeamRows(memberNames, null, null));

        // Fetch employee docs in batches (Firestore whereIn supports up to 10 values)
        List<List<String>> chunks = chunkList(ids, 10);
        final int[] pendingChunks = { chunks.size() };
        final Map<String, Integer> idToIndex = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) idToIndex.put(ids.get(i), i);

        for (List<String> chunk : chunks) {
            FirebaseFirestore.getInstance().collection("employees")
                    .whereIn(FieldPath.documentId(), chunk)
                    .get()
                    .addOnSuccessListener(snap -> {
                        if (snap != null) {
                            for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                                String id = doc.getId();
                                Integer index = idToIndex.get(id);
                                if (index == null) continue;
                                String name = doc.getString("salesPersonName");
                                if (name == null || name.isEmpty()) name = doc.getString("name");
                                if (name == null || name.isEmpty()) name = doc.getString("employeeName");
                                if (name == null || name.isEmpty()) name = "(Unknown)";
                                String email = doc.getString("email");
                                if (email == null) email = "";
                                if (index >= 0 && index < names.size()) {
                                    names.set(index, name);
                                    emails.set(index, email);
                                }
                            }
                        }
                        pendingChunks[0]--;
                        if (pendingChunks[0] == 0) {
                            memberNames = new ArrayList<>(names);
                            memberEmails = new ArrayList<>(emails);
                            teamLoaded = true;
                            runOnUiThread(() -> {
                                populateTeamRows(memberNames, null, null);
                                loadCICOForSelectedDate();
                            });
                        }
                    })
                    .addOnFailureListener(e -> {
                        pendingChunks[0]--;
                        if (pendingChunks[0] == 0) {
                            memberNames = new ArrayList<>(names);
                            memberEmails = new ArrayList<>(emails);
                            teamLoaded = true;
                            runOnUiThread(() -> {
                                populateTeamRows(memberNames, null, null);
                                loadCICOForSelectedDate();
                            });
                        }
                    });
        }
    }

    private static <T> List<List<T>> chunkList(List<T> list, int chunkSize) {
        List<List<T>> chunks = new ArrayList<>();
        if (list == null || list.isEmpty() || chunkSize <= 0) return chunks;
        int i = 0;
        while (i < list.size()) {
            int end = Math.min(i + chunkSize, list.size());
            chunks.add(new ArrayList<>(list.subList(i, end)));
            i = end;
        }
        return chunks;
    }

    /** Load check-in and check-out times for selected date for each team member, then refresh table. */
    private void loadCICOForSelectedDate() {
        if (memberEmails == null || memberEmails.isEmpty()) return;
        String dateStr = new SimpleDateFormat(DATE_FORMAT_QUERY, Locale.getDefault()).format(selectedDate.getTime());
        final List<String> ciTimes = new ArrayList<>();
        final List<String> coTimes = new ArrayList<>();
        for (int i = 0; i < memberEmails.size(); i++) {
            ciTimes.add("-");
            coTimes.add("-");
        }
        // Batch queries by date + whereIn(employeeEmail) in chunks of 10
        final Map<String, String> emailToCheckIn = new HashMap<>();
        final Map<String, String> emailToCheckOut = new HashMap<>();
        final Map<String, String> emailToCheckInLocation = new HashMap<>();
        final Map<String, String> emailToCheckOutLocation = new HashMap<>();
        final Map<String, Long> emailToCheckInTs = new HashMap<>();
        final Map<String, Long> emailToCheckOutTs = new HashMap<>();

        List<String> emailsForQuery = new ArrayList<>();
        for (String e : memberEmails) {
            if (e != null && !e.trim().isEmpty()) emailsForQuery.add(e.trim());
        }
        if (emailsForQuery.isEmpty()) {
            runOnUiThread(() -> populateTeamRows(memberNames, ciTimes, coTimes));
            return;
        }

        FirebaseFirestore.getInstance().collection("locations")
                .get()
                .addOnSuccessListener(locSnap -> {
                    final List<LocationRef> locationRefs = new ArrayList<>();
                    if (locSnap != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot locDoc : locSnap.getDocuments()) {
                            Double lat = getDoubleFromDoc(locDoc, "latitude", "lat", "assignedLatitude");
                            Double lon = getDoubleFromDoc(locDoc, "longitude", "lng", "lon", "assignedLongitude");
                            if (lat == null || lon == null) continue;
                            int radius = getRadiusFromDoc(locDoc);
                            String name = resolveLocationNameField(locDoc);
                            if (name.isEmpty()) name = locDoc.getId();
                            locationRefs.add(new LocationRef(name, lat, lon, radius));
                        }
                    }
                    runCicoQueries(dateStr, emailsForQuery, ciTimes, coTimes,
                            emailToCheckIn, emailToCheckOut, emailToCheckInLocation, emailToCheckOutLocation,
                            emailToCheckInTs, emailToCheckOutTs, locationRefs);
                })
                .addOnFailureListener(e -> runCicoQueries(dateStr, emailsForQuery, ciTimes, coTimes,
                        emailToCheckIn, emailToCheckOut, emailToCheckInLocation, emailToCheckOutLocation,
                        emailToCheckInTs, emailToCheckOutTs, new ArrayList<>()));
    }

    private void runCicoQueries(
            String dateStr,
            List<String> emailsForQuery,
            List<String> ciTimes,
            List<String> coTimes,
            Map<String, String> emailToCheckIn,
            Map<String, String> emailToCheckOut,
            Map<String, String> emailToCheckInLocation,
            Map<String, String> emailToCheckOutLocation,
            Map<String, Long> emailToCheckInTs,
            Map<String, Long> emailToCheckOutTs,
            List<LocationRef> locationRefs
    ) {
        List<List<String>> chunks = chunkList(emailsForQuery, 10);
        final int[] pendingQueries = { chunks.size() * 2 };
        Runnable done = () -> {
            if (pendingQueries[0] > 0) return;
            for (int i = 0; i < memberEmails.size(); i++) {
                String email = memberEmails.get(i);
                if (email == null) continue;
                String ci = emailToCheckIn.get(email);
                String co = emailToCheckOut.get(email);
                String ciLoc = emailToCheckInLocation.get(email);
                String coLoc = emailToCheckOutLocation.get(email);
                if (ci != null && !ci.trim().isEmpty()) ciTimes.set(i, formatTimeWithLocationForDisplay(ci, ciLoc));
                if (co != null && !co.trim().isEmpty()) coTimes.set(i, formatTimeWithLocationForDisplay(co, coLoc));
            }
            runOnUiThread(() -> populateTeamRows(memberNames, ciTimes, coTimes));
        };

        for (List<String> chunk : chunks) {
            FirebaseFirestore.getInstance().collection("check_ins")
                    .whereEqualTo("date", dateStr)
                    .whereIn("employeeEmail", chunk)
                    .get()
                    .addOnSuccessListener(snap -> {
                        if (snap != null) {
                            for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                                String email = doc.getString("employeeEmail");
                                String t = doc.getString("time");
                                Object loc = doc.get("checkInLocation");
                                if (email != null && t != null && !t.trim().isEmpty()
                                        && loc instanceof com.google.firebase.firestore.GeoPoint) {
                                    // Only treat as valid check-in when location exists.
                                    long ts = extractSortTimeMillis(doc, t, true);
                                    Long existingTs = emailToCheckInTs.get(email);
                                    // Check-in should reflect the first valid location capture of the day.
                                    if (existingTs == null || ts < existingTs) {
                                        emailToCheckInTs.put(email, ts);
                                        emailToCheckIn.put(email, t);
                                        emailToCheckInLocation.put(email, resolveLocationNameFromDoc(doc, true, locationRefs));
                                    }
                                }
                            }
                        }
                        pendingQueries[0]--;
                        done.run();
                    })
                    .addOnFailureListener(e -> { pendingQueries[0]--; done.run(); });

            FirebaseFirestore.getInstance().collection("check_outs")
                    .whereEqualTo("date", dateStr)
                    .whereIn("employeeEmail", chunk)
                    .get()
                    .addOnSuccessListener(snap -> {
                        if (snap != null) {
                            for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                                String email = doc.getString("employeeEmail");
                                String t = doc.getString("time");
                                Object loc = doc.get("checkOutLocation");
                                if (email != null && t != null && !t.trim().isEmpty()
                                        && loc instanceof com.google.firebase.firestore.GeoPoint) {
                                    long ts = extractSortTimeMillis(doc, t, false);
                                    Long existingTs = emailToCheckOutTs.get(email);
                                    // Check-out should reflect the latest valid location capture of the day.
                                    if (existingTs == null || ts > existingTs) {
                                        emailToCheckOutTs.put(email, ts);
                                        emailToCheckOut.put(email, t);
                                        emailToCheckOutLocation.put(email, resolveLocationNameFromDoc(doc, false, locationRefs));
                                    }
                                }
                            }
                        }
                        pendingQueries[0]--;
                        done.run();
                    })
                    .addOnFailureListener(e -> { pendingQueries[0]--; done.run(); });
        }
    }

    /** Gets millis for sorting attendance docs; prefers Firestore timestamp, falls back to parsed time string. */
    private long extractSortTimeMillis(com.google.firebase.firestore.DocumentSnapshot doc, String timeStr, boolean isCheckIn) {
        Object tsObj = doc.get("timestamp");
        if (tsObj instanceof Timestamp) {
            return ((Timestamp) tsObj).toDate().getTime();
        }
        if (timeStr != null && !timeStr.trim().isEmpty()) {
            try {
                Date parsed = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).parse(timeStr.trim());
                if (parsed != null) return parsed.getTime();
            } catch (Exception ignored) {}
        }
        // If timestamp/time is missing, push unknown check-ins to the end and check-outs to the start.
        return isCheckIn ? Long.MAX_VALUE : Long.MIN_VALUE;
    }

    private String formatTimeForDisplay(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) return "-";
        try {
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            Date d = in.parse(timeStr.trim());
            if (d != null) return new java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(d);
        } catch (Exception ignored) {}
        return timeStr;
    }

    private String formatTimeWithLocationForDisplay(String timeStr, String locationName) {
        String time = formatTimeForDisplay(timeStr);
        if (locationName == null || locationName.trim().isEmpty()) return time;
        return time + " (" + locationName.trim() + ")";
    }

    private String safeLocationName(String locationName) {
        if (locationName == null) return "";
        String s = locationName.trim();
        return s.equalsIgnoreCase("null") ? "" : s;
    }

    /**
     * Reads location name robustly from CI/CO docs (string/object variants and key variants).
     * Falls back to coordinates text only if no location-name field exists.
     */
    private String resolveLocationNameFromDoc(
            com.google.firebase.firestore.DocumentSnapshot doc,
            boolean isCheckIn,
            List<LocationRef> locationRefs
    ) {
        if (doc == null) return "";

        String[] preferredKeys = isCheckIn
                ? new String[]{"checkInLocationName", "checkinLocationName", "checkInlocationName"}
                : new String[]{"checkOutLocationName", "checkoutLocationName", "checkOutlocationName"};

        String[] commonKeys = new String[]{
                "locationName", "location_name", "assignedLocationName", "areaName",
                "siteName", "branchName", "displayName", "address", "location"
        };

        for (String key : preferredKeys) {
            String v = readLocationLikeValue(doc.get(key));
            if (!v.isEmpty()) return v;
        }
        for (String key : commonKeys) {
            String v = readLocationLikeValue(doc.get(key));
            if (!v.isEmpty()) return v;
        }

        Object gpObj = doc.get(isCheckIn ? "checkInLocation" : "checkOutLocation");
        if (gpObj instanceof com.google.firebase.firestore.GeoPoint) {
            com.google.firebase.firestore.GeoPoint gp = (com.google.firebase.firestore.GeoPoint) gpObj;
            String matched = findLocationNameByCoordinates(gp.getLatitude(), gp.getLongitude(), locationRefs);
            if (!matched.isEmpty()) return matched;
            return String.format(Locale.getDefault(), "%.5f, %.5f", gp.getLatitude(), gp.getLongitude());
        }
        return "";
    }

    private String readLocationLikeValue(Object value) {
        if (value == null) return "";
        if (value instanceof String) return safeLocationName((String) value);
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            String[] nestedKeys = new String[]{"name", "locationName", "label", "title", "address"};
            for (String k : nestedKeys) {
                Object nested = map.get(k);
                if (nested != null) {
                    String s = safeLocationName(String.valueOf(nested));
                    if (!s.isEmpty()) return s;
                }
            }
        }
        return safeLocationName(String.valueOf(value));
    }

    private String findLocationNameByCoordinates(double lat, double lon, List<LocationRef> locationRefs) {
        if (locationRefs == null || locationRefs.isEmpty()) return "";
        LocationRef best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LocationRef ref : locationRefs) {
            double d = distanceInMeters(lat, lon, ref.lat, ref.lon);
            if (d <= (ref.radiusMeters + LOCATION_MATCH_TOLERANCE_METERS) && d < bestDistance) {
                bestDistance = d;
                best = ref;
            }
        }
        return best != null ? safeLocationName(best.name) : "";
    }

    private static double distanceInMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private Double getDoubleFromDoc(com.google.firebase.firestore.DocumentSnapshot doc, String... keys) {
        if (doc == null) return null;
        for (String key : keys) {
            Object val = doc.get(key);
            if (val == null) continue;
            if (val instanceof Number) return ((Number) val).doubleValue();
            if (val instanceof String) {
                try {
                    return Double.parseDouble(((String) val).trim());
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private int getRadiusFromDoc(com.google.firebase.firestore.DocumentSnapshot doc) {
        if (doc == null) return DEFAULT_LOCATION_RADIUS_METERS;
        Object[] candidates = new Object[]{
                doc.get("radiusMeters"),
                doc.get("assignedRadius"),
                doc.get("radius")
        };
        for (Object c : candidates) {
            if (c instanceof Number) {
                int v = ((Number) c).intValue();
                if (v > 0) return v;
            } else if (c instanceof String) {
                try {
                    int v = Integer.parseInt(((String) c).trim());
                    if (v > 0) return v;
                } catch (Exception ignored) {}
            }
        }
        return DEFAULT_LOCATION_RADIUS_METERS;
    }

    private String resolveLocationNameField(com.google.firebase.firestore.DocumentSnapshot doc) {
        if (doc == null) return "";
        String[] keys = new String[]{
                "locationName", "name", "title", "assignedLocationName",
                "location", "areaName", "address", "location_name",
                "siteName", "branchName", "displayName"
        };
        for (String key : keys) {
            String s = readLocationLikeValue(doc.get(key));
            if (!s.isEmpty()) return s;
        }
        return "";
    }

    private void populateTeamRows(List<String> names, List<String> checkInTimes, List<String> checkOutTimes) {
        if (teamAttendanceTableBody == null || names == null) return;
        teamAttendanceTableBody.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            String ci = (checkInTimes != null && i < checkInTimes.size()) ? checkInTimes.get(i) : "-";
            String co = (checkOutTimes != null && i < checkOutTimes.size()) ? checkOutTimes.get(i) : "-";
            if (ci == null) ci = "-";
            if (co == null) co = "-";
            boolean light = (i % 2) == 0;
            String empId = (i < memberIds.size()) ? memberIds.get(i) : null;
            View row = buildAttendanceRow(name, ci, co, light, density, empId, name);
            teamAttendanceTableBody.addView(row);
            if (i < names.size() - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (1 * density)));
                divider.setBackgroundColor(0xFFD0D0D0);
                teamAttendanceTableBody.addView(divider);
            }
        }
    }

    private View buildAttendanceRow(String employeeName, String checkInTime, String checkOutTime, boolean lightRow, float density, final String employeeId, String displayName) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setPadding((int) (12 * density), (int) (12 * density), (int) (12 * density), (int) (12 * density));
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(lightRow ? R.drawable.attendance_row_light : R.drawable.attendance_row_dark);

        TextView nameTv = new TextView(this);
        nameTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        nameTv.setGravity(android.view.Gravity.CENTER);
        nameTv.setText(employeeName != null ? employeeName : "-");
        nameTv.setTextColor(0xFF424242);
        nameTv.setTextSize(13);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && getResources().getFont(R.font.inter_font_family) != null) {
                nameTv.setTypeface(getResources().getFont(R.font.inter_font_family));
            }
        } catch (Exception ignored) {}
        row.addView(nameTv);

        TextView checkInTv = new TextView(this);
        checkInTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        checkInTv.setGravity(android.view.Gravity.CENTER);
        checkInTv.setText(checkInTime != null ? checkInTime : "-");
        checkInTv.setTextColor(0xFF424242);
        checkInTv.setTextSize(13);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && getResources().getFont(R.font.inter_font_family) != null) {
                checkInTv.setTypeface(getResources().getFont(R.font.inter_font_family));
            }
        } catch (Exception ignored) {}
        row.addView(checkInTv);

        TextView checkOutTv = new TextView(this);
        checkOutTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        checkOutTv.setGravity(android.view.Gravity.CENTER);
        checkOutTv.setText(checkOutTime != null ? checkOutTime : "-");
        checkOutTv.setTextColor(0xFF424242);
        checkOutTv.setTextSize(13);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && getResources().getFont(R.font.inter_font_family) != null) {
                checkOutTv.setTypeface(getResources().getFont(R.font.inter_font_family));
            }
        } catch (Exception ignored) {}
        row.addView(checkOutTv);

        ImageView viewIcon = new ImageView(this);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(0, (int) (24 * density), 0.6f);
        viewIcon.setLayoutParams(iconLp);
        viewIcon.setImageResource(R.drawable.ic_eye_disabled);
        viewIcon.setColorFilter(0xFF424242);
        viewIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        viewIcon.setContentDescription("View");
        if (employeeId != null && !employeeId.isEmpty()) {
            viewIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AttendanceActivity.this, MonthlyPlanActivity.class);
                    intent.putExtra(MonthlyPlanActivity.EXTRA_VIEW_EMPLOYEE_ID, employeeId);
                    if (displayName != null && !displayName.isEmpty()) {
                        intent.putExtra(MonthlyPlanActivity.EXTRA_VIEW_EMPLOYEE_NAME, displayName);
                    }
                    startActivity(intent);
                }
            });
        }
        row.addView(viewIcon);

        return row;
    }

    private void addMessageRow(String message) {
        if (teamAttendanceTableBody == null) return;
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setPadding((int) (24 * getResources().getDisplayMetrics().density), (int) (24 * getResources().getDisplayMetrics().density), (int) (24 * getResources().getDisplayMetrics().density), (int) (24 * getResources().getDisplayMetrics().density));
        tv.setTextColor(0xFF424242);
        teamAttendanceTableBody.addView(tv);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        if (dateSelectorContainer != null) {
            dateSelectorContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDatePicker();
                }
            });
        }

        if (gridButton != null) {
            gridButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(AttendanceActivity.this, WeeklyPlannerActivity.class));
                }
            });
        }

        if (uploadButton != null) {
            uploadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(AttendanceActivity.this, "Upload", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (checkInOutButton != null) {
            checkInOutButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(AttendanceActivity.this, CheckInOutActivity.class));
                }
            });
        }
    }
}
