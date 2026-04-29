package com.example.kailashmasale;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TargetAchievedActivity extends AppCompatActivity {

    private static final String[] MONTH_LABELS = {"Jan", "Feb", "March", "April", "May", "June",
            "July", "Aug", "Sept", "Oct", "Nov", "Dec"};
    /** Short month names for monthlyData doc id (e.g. 2026_Mar) */
    private static final String[] MONTH_SHORT = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private ImageButton backButton;
    private Spinner yearSpinner;
    private Spinner monthSpinner;
    private ImageButton gridButton;
    private ImageButton uploadButton;
    private LinearLayout checkInOutButton;
    private View fabAdd;
    private LinearLayout targetTablesContainer;

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
        
        setContentView(R.layout.activity_target_achieved);

        initializeViews();
        setupYearSpinner();
        setupMonthSpinner();
        setupClickListeners();
        loadTargetAchievedData();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.back_button);
        yearSpinner = findViewById(R.id.year_spinner);
        monthSpinner = findViewById(R.id.month_spinner);
        gridButton = findViewById(R.id.grid_button);
        uploadButton = findViewById(R.id.upload_button);
        checkInOutButton = findViewById(R.id.check_in_out_button);
        fabAdd = findViewById(R.id.fab_add);
        if (fabAdd != null) fabAdd.setVisibility(View.GONE);
        targetTablesContainer = findViewById(R.id.target_tables_container);
    }

    private void setupYearSpinner() {
        String[] years = {"2026", "2025", "2024", "2023", "2022", "2021"};
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                R.layout.year_spinner_item,
                years
        ) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view;
                if (position == yearSpinner.getSelectedItemPosition()) {
                    textView.setTextColor(0xFF000000); // 100% opacity for selected
                } else {
                    textView.setTextColor(0xBF000000); // 75% opacity for non-selected
                }
                return view;
            }
        };
        adapter.setDropDownViewResource(R.layout.year_spinner_dropdown_item);
        yearSpinner.setAdapter(adapter);
        yearSpinner.setSelection(0); // default 2026
        yearSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                adapter.notifyDataSetChanged();
                loadTargetAchievedData();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void setupMonthSpinner() {
        String[] months = {"Jan", "Feb", "March", "April", "May", "June", 
                          "July", "Aug", "Sept", "Oct", "Nov", "Dec"};
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                R.layout.year_spinner_item,
                months
        ) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view;
                if (position == monthSpinner.getSelectedItemPosition()) {
                    textView.setTextColor(0xFF000000); // 100% opacity for selected
                } else {
                    textView.setTextColor(0xBF000000); // 75% opacity for non-selected
                }
                return view;
            }
        };
        adapter.setDropDownViewResource(R.layout.year_spinner_dropdown_item);
        monthSpinner.setAdapter(adapter);
        monthSpinner.setSelection(java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)); // current month
        monthSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                adapter.notifyDataSetChanged();
                loadTargetAchievedData();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    /** Load assigned distributors and monthlyData; show one table per distributor with Working Days and Target Achieved (Primary Kg week-wise). */
    private void loadTargetAchievedData() {
        if (targetTablesContainer == null) return;
        targetTablesContainer.removeAllViews();
        showLoadingView();

        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", null);
        String employeeId = prefs.getString("logged_in_employee_id", null);
        if (employeeEmail == null || employeeEmail.isEmpty()) {
            targetTablesContainer.removeAllViews();
            addMessageRow("Please log in.");
            return;
        }

        int monthPos = monthSpinner != null ? monthSpinner.getSelectedItemPosition() : 0;
        Object yearObj = (yearSpinner != null && yearSpinner.getSelectedItem() != null) ? yearSpinner.getSelectedItem() : "2026";
        final String selectedYear = yearObj.toString();
        if (monthPos < 0 || monthPos >= MONTH_LABELS.length) {
            targetTablesContainer.removeAllViews();
            addMessageRow("Select year and month.");
            return;
        }
        final int month1Based = monthPos + 1;
        final String monthAbbr = (monthPos >= 0 && monthPos < MONTH_SHORT.length) ? MONTH_SHORT[monthPos] : String.valueOf(month1Based);
        final String monthLabel = MONTH_LABELS[monthPos];
        final String monthlyDataDocId = (employeeId != null ? employeeId : "") + "_" + selectedYear + "_" + monthAbbr;

        // 1) Get assigned distributor IDs from employees
        FirebaseFirestore.getInstance().collection("employees")
                .whereEqualTo("email", employeeEmail)
                .limit(1)
                .get()
                .addOnSuccessListener((QuerySnapshot empSnapshot) -> {
                    List<String> assignedIds = new ArrayList<>();
                    if (empSnapshot != null && !empSnapshot.isEmpty()) {
                        DocumentSnapshot empDoc = empSnapshot.getDocuments().get(0);
                        Object assigned = empDoc.get("assignedDistributorId");
                        if (assigned == null) assigned = empDoc.get("distributorId");
                        if (assigned == null) assigned = empDoc.get("assignedDistributorIds");
                        if (assigned instanceof String) assignedIds.add((String) assigned);
                        else if (assigned instanceof List) {
                            for (Object o : (List<?>) assigned) {
                                if (o instanceof String) assignedIds.add((String) o);
                            }
                        }
                    }
                    if (assignedIds.isEmpty()) {
                        runOnUiThread(() -> {
                            targetTablesContainer.removeAllViews();
                            addMessageRow("No distributors assigned to you.");
                        });
                        return;
                    }
                    fetchMonthlyDataAndDistributors(assignedIds, selectedYear, monthLabel, monthlyDataDocId, monthAbbr, employeeId, month1Based);
                })
                .addOnFailureListener(e -> runOnUiThread(() -> addMessageRow("Failed to load data.")));
    }

    private void fetchMonthlyDataAndDistributors(List<String> assignedIds, String selectedYear, String monthLabel,
                                                  String monthlyDataDocId, String monthAbbr, String employeeId, int month1Based) {
        final Map<String, Map<String, Object>> monthlyByDist = new HashMap<>();
        final Map<String, String> distIdToName = new HashMap<>();
        final Map<String, String> targetKgByDistId = new HashMap<>();
        // admin collection: doc id = 2026_Mar, structure: employees[].distRows[].distName, weeks.1.workingDays, etc.
        final Map<String, Map<Integer, String>> adminWorkingDaysByDistName = new HashMap<>();
        final int totalFetches = 3 + (assignedIds.isEmpty() ? 0 : 1); // monthlyData + admin + monthly_data (targetkg) + batched distributor fetch
        final int[] done = {0};
        Runnable tryPopulate = () -> {
            if (done[0] < totalFetches) return;
            runOnUiThread(() -> populateDistributorTables(assignedIds, distIdToName, monthlyByDist, adminWorkingDaysByDistName, targetKgByDistId, monthLabel));
        };

        // Load admin document for this month: admin/2026_Mar -> employees[].distRows[] with distName, weeks.1.workingDays, ...
        String adminDocId = selectedYear + "_" + monthAbbr;
        FirebaseFirestore.getInstance().collection("admin").document(adminDocId)
                .get()
                .addOnSuccessListener(adminDoc -> {
                    if (adminDoc != null && adminDoc.exists()) {
                        Object employeesObj = adminDoc.get("employees");
                        if (employeesObj instanceof List) {
                            List<?> employees = (List<?>) employeesObj;
                            for (Object emp : employees) {
                                if (!(emp instanceof Map)) continue;
                                @SuppressWarnings("unchecked")
                                Map<String, Object> empMap = (Map<String, Object>) emp;
                                Object distRowsObj = empMap.get("distRows");
                                if (!(distRowsObj instanceof List)) continue;
                                List<?> distRows = (List<?>) distRowsObj;
                                for (Object row : distRows) {
                                    if (!(row instanceof Map)) continue;
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> rowMap = (Map<String, Object>) row;
                                    String distName = getStringFrom(rowMap, "distName", "distKey", "distributorName");
                                    if (distName == null || distName.isEmpty()) continue;
                                    distName = distName.trim();
                                    Object weeksObj = rowMap.get("weeks");
                                    if (!(weeksObj instanceof Map)) continue;
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> weeks = (Map<String, Object>) weeksObj;
                                    Map<Integer, String> weekToDays = new HashMap<>();
                                    for (int w = 1; w <= 4; w++) {
                                        String key = String.valueOf(w);
                                        Object weekData = weeks.get(key);
                                        String wdStr = "-";
                                        if (weekData instanceof Map) {
                                            Object wd = ((Map<?, ?>) weekData).get("workingDays");
                                            if (wd == null) wd = ((Map<?, ?>) weekData).get("working_days");
                                            if (wd != null) wdStr = wd.toString().trim();
                                            if (wdStr.isEmpty()) wdStr = "-";
                                        }
                                        weekToDays.put(w, wdStr);
                                    }
                                    adminWorkingDaysByDistName.put(distName, weekToDays);
                                }
                            }
                        }
                    }
                    done[0]++;
                    tryPopulate.run();
                })
                .addOnFailureListener(e -> { done[0]++; tryPopulate.run(); });

        // Load monthlyData for selected year/month
        FirebaseFirestore.getInstance().collection("monthlyData").document(monthlyDataDocId).get()
                .addOnSuccessListener(monthDoc -> {
                    if (monthDoc != null && monthDoc.exists()) {
                        Object dd = monthDoc.get("distributorDetails");
                        if (dd instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> distributorDetails = (Map<String, Object>) dd;
                            for (Map.Entry<String, Object> e : distributorDetails.entrySet()) {
                                if (e.getValue() instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> detail = (Map<String, Object>) e.getValue();
                                    monthlyByDist.put(e.getKey(), detail);
                                }
                            }
                        }
                    }
                    done[0]++;
                    tryPopulate.run();
                })
                .addOnFailureListener(e -> { done[0]++; tryPopulate.run(); });

        // Load monthly_data for targetkg (doc id = employeeId_year_monthNum; rows[].distributorId, rows[].targetkg)
        String monthlyDataDocIdNumeric = (employeeId != null ? employeeId : "") + "_" + selectedYear + "_" + month1Based;
        FirebaseFirestore.getInstance().collection("monthly_data").document(monthlyDataDocIdNumeric).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        Object rowsObj = doc.get("rows");
                        if (rowsObj instanceof List) {
                            for (Object rowObj : (List<?>) rowsObj) {
                                if (!(rowObj instanceof Map)) continue;
                                @SuppressWarnings("unchecked")
                                Map<String, Object> row = (Map<String, Object>) rowObj;
                                String did = row.get("distributorId") != null ? row.get("distributorId").toString().trim() : null;
                                if (did == null || did.isEmpty()) continue;
                                String tg = getStringFrom(row, "targetkg", "targetKg", "target");
                                if (!tg.isEmpty()) targetKgByDistId.put(did, tg);
                            }
                        }
                    }
                    done[0]++;
                    tryPopulate.run();
                })
                .addOnFailureListener(e -> { done[0]++; tryPopulate.run(); });

        // Load distributor names in batch (whereIn by document ID) to reduce round-trips
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        final int batchSize = 10; // Firestore whereIn limit
        final int[] batchesDone = {0};
        final int totalBatches = (assignedIds.size() + batchSize - 1) / batchSize;
        if (assignedIds.isEmpty()) {
            done[0]++;
            tryPopulate.run();
        } else {
            for (int i = 0; i < assignedIds.size(); i += batchSize) {
                int end = Math.min(i + batchSize, assignedIds.size());
                List<String> batchIds = assignedIds.subList(i, end);
                firestore.collection("distributors")
                        .whereIn(FieldPath.documentId(), batchIds)
                        .get()
                        .addOnSuccessListener(snap -> {
                            if (snap != null) {
                                for (DocumentSnapshot doc : snap.getDocuments()) {
                                    String id = doc.getId();
                                    String name = "";
                                    if (doc.exists()) {
                                        name = doc.getString("distributorName");
                                        if (name == null || name.isEmpty()) name = doc.getString("distributor_name");
                                        if (name == null || name.isEmpty()) name = doc.getString("name");
                                        if (name == null) name = "";
                                    }
                                    distIdToName.put(id, name);
                                }
                            }
                            batchesDone[0]++;
                            if (batchesDone[0] == totalBatches) {
                                done[0]++;
                                tryPopulate.run();
                            }
                        })
                        .addOnFailureListener(e -> {
                            for (String id : batchIds) distIdToName.put(id, "");
                            batchesDone[0]++;
                            if (batchesDone[0] == totalBatches) {
                                done[0]++;
                                tryPopulate.run();
                            }
                        });
            }
        }
    }

    /** Show a single lightweight Loading view until tables are ready; avoids lag from drawing many placeholders. */
    private void showLoadingView() {
        if (targetTablesContainer == null) return;
        TextView loading = new TextView(this);
        loading.setText("Loading...");
        loading.setTextSize(16);
        loading.setTextColor(0xFF616161);
        loading.setPadding((int) (48 * getResources().getDisplayMetrics().density),
                (int) (80 * getResources().getDisplayMetrics().density),
                (int) (48 * getResources().getDisplayMetrics().density),
                (int) (48 * getResources().getDisplayMetrics().density));
        loading.setGravity(android.view.Gravity.CENTER);
        targetTablesContainer.addView(loading);
    }

    private void populateDistributorTables(List<String> assignedIds, Map<String, String> distIdToName,
                                           Map<String, Map<String, Object>> monthlyByDist,
                                           Map<String, Map<Integer, String>> adminWorkingDaysByDistName,
                                           Map<String, String> targetKgByDistId,
                                           String monthLabel) {
        if (targetTablesContainer == null) return;
        targetTablesContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String distId : assignedIds) {
            // Use only name, never distributor ID (distName from distributors / monthlyData; admin also has distName)
            String distName = distIdToName.get(distId);
            if (distName == null) distName = "";
            Map<String, Object> detail = monthlyByDist.get(distId);
            // Fallback: name may be stored in monthlyData distributor detail
            if (distName.isEmpty() && detail != null) {
                distName = getStringFrom(detail, "distributorName", "distributor_name", "name", "distName");
                if (distName == null) distName = "";
            }
            distName = distName != null ? distName.trim() : "";
            // Skip tables that would show no name (don't show cards for "-" or empty)
            if (distName.isEmpty()) continue;

            View card = inflater.inflate(R.layout.item_target_achieved_table, targetTablesContainer, false);
            TextView nameTv = card.findViewById(R.id.distributor_name_label);
            nameTv.setText("Distributor: " + distName);

            String targetKg = targetKgByDistId != null ? targetKgByDistId.get(distId) : null;
            if (targetKg == null && detail != null) targetKg = getStringFrom(detail, "targetkg", "targetKg", "target");
            setText(card, R.id.target_given_value, (targetKg != null && !targetKg.isEmpty()) ? targetKg : "-");

            TextView headerMonth = card.findViewById(R.id.table_header_month);
            headerMonth.setText(monthLabel);

            // Working days: from admin collection (week 1-4 per distName), fallback to monthlyData
            Map<Integer, String> adminDays = findAdminWorkingDaysForDistName(adminWorkingDaysByDistName, distName);
            String workDays1 = adminDays != null ? adminDays.get(1) : null;
            String workDays2 = adminDays != null ? adminDays.get(2) : null;
            String workDays3 = adminDays != null ? adminDays.get(3) : null;
            String workDays4 = adminDays != null ? adminDays.get(4) : null;
            if (workDays1 == null) workDays1 = getStringFrom(detail, "workDaysWeek1", "week1WorkingDays");
            if (workDays2 == null) workDays2 = getStringFrom(detail, "workDaysWeek2", "week2WorkingDays");
            if (workDays3 == null) workDays3 = getStringFrom(detail, "workDaysWeek3", "week3WorkingDays");
            if (workDays4 == null) workDays4 = getStringFrom(detail, "workDaysWeek4", "week4WorkingDays");
            // Week-wise primary from monthlyData: primary1to7, primary8to15, primary16to22, primary23to31
            String w1 = getStringFrom(detail, "primary1to7", "primaryKgWeek1", "week1PrimaryKg", "week1");
            String w2 = getStringFrom(detail, "primary8to15", "primaryKgWeek2", "week2PrimaryKg", "week2");
            String w3 = getStringFrom(detail, "primary16to22", "primaryKgWeek3", "week3PrimaryKg", "week3");
            String w4 = getStringFrom(detail, "primary23to31", "primaryKgWeek4", "week4PrimaryKg", "week4");

            setText(card, R.id.week1_working_days, workDays1 != null && !workDays1.isEmpty() ? workDays1 : "-");
            setText(card, R.id.week2_working_days, workDays2 != null && !workDays2.isEmpty() ? workDays2 : "-");
            setText(card, R.id.week3_working_days, workDays3 != null && !workDays3.isEmpty() ? workDays3 : "-");
            setText(card, R.id.week4_working_days, workDays4 != null && !workDays4.isEmpty() ? workDays4 : "-");
            setText(card, R.id.week1_target_achieved, (w1 != null && !w1.isEmpty()) ? w1 : "0");
            setText(card, R.id.week2_target_achieved, (w2 != null && !w2.isEmpty()) ? w2 : "0");
            setText(card, R.id.week3_target_achieved, (w3 != null && !w3.isEmpty()) ? w3 : "0");
            setText(card, R.id.week4_target_achieved, (w4 != null && !w4.isEmpty()) ? w4 : "0");
            // Total for Days Worked = sum of Week 1–4 values in the table (not a separate field)
            int totalDaysWorked = parseNum(workDays1) + parseNum(workDays2) + parseNum(workDays3) + parseNum(workDays4);
            setText(card, R.id.total_working_days, totalDaysWorked > 0 ? String.valueOf(totalDaysWorked) : "-");
            int totalKg = parseNum(w1) + parseNum(w2) + parseNum(w3) + parseNum(w4);
            setText(card, R.id.total_target_achieved, String.valueOf(totalKg));
            String shortfallStr = getStringFrom(detail, "shortfall", "shortfall_kg");
            setText(card, R.id.shortfall_value, (shortfallStr != null && !shortfallStr.isEmpty()) ? shortfallStr : "-");

            if (targetTablesContainer.getChildCount() > 0) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) card.getLayoutParams();
                if (lp == null) lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = (int) (24 * getResources().getDisplayMetrics().density);
                card.setLayoutParams(lp);
            }
            targetTablesContainer.addView(card);
        }
    }

    /** Find admin working-days map for a distributor by distName (exact or case-insensitive match). */
    private static Map<Integer, String> findAdminWorkingDaysForDistName(
            Map<String, Map<Integer, String>> adminByDistName, String distName) {
        if (adminByDistName == null || distName == null) return null;
        String key = distName.trim();
        if (adminByDistName.containsKey(key)) return adminByDistName.get(key);
        for (Map.Entry<String, Map<Integer, String>> e : adminByDistName.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        return null;
    }

    private static String getStringFrom(Map<String, Object> map, String... keys) {
        if (map == null) return "";
        for (String key : keys) {
            Object v = map.get(key);
            if (v != null) {
                String s = v.toString().trim();
                if (!s.isEmpty()) return s;
            }
        }
        return "";
    }

    private static int parseNum(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return (int) Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void setText(View parent, int id, String text) {
        View v = parent.findViewById(id);
        if (v instanceof TextView) ((TextView) v).setText(text);
    }

    private void addMessageRow(String message) {
        if (targetTablesContainer == null) return;
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setPadding(32, 24, 32, 24);
        tv.setTextColor(0xFF424242);
        targetTablesContainer.addView(tv);
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

        if (gridButton != null) {
            gridButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(TargetAchievedActivity.this, WeeklyPlannerActivity.class);
                    startActivity(intent);
                }
            });
        }

        if (uploadButton != null) {
            uploadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showUploadStockDialog();
                }
            });
        }

        if (checkInOutButton != null) {
            checkInOutButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(TargetAchievedActivity.this, CheckInOutActivity.class);
                    startActivity(intent);
                }
            });
        }

        if (fabAdd != null) {
            fabAdd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(TargetAchievedActivity.this, AddOrdersActivity.class);
                    startActivity(intent);
                }
            });
        }
    }

    private void showUploadStockDialog() {
        // Create dialog
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_upload_stock);
        
        // Make dialog background transparent for rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.7f); // Dim background
            // Allow dialog to expand for dropdown
            dialog.getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
            
            // Set dialog animations
            dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
        }

        // Initialize dialog views
        final CardView dialogCard = dialog.findViewById(R.id.dialog_card);
        ImageButton closeButton = dialog.findViewById(R.id.dialog_close_button);
        Spinner distributerSpinner = dialog.findViewById(R.id.distributer_spinner);
        LinearLayout uploadButton = dialog.findViewById(R.id.upload_sheet_button);
        final LinearLayout dropdownContainer = dialog.findViewById(R.id.dropdown_container);
        
        // Animate dialog entrance
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.dialog_fade_in);
        dialogCard.startAnimation(fadeIn);

        // Setup distributer spinner with same layout as add orders page
        String[] distributers = {"Name1", "Name 2", "Name 3", "Name 4"};
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                distributers
        ) {
            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view;
                if (position == distributerSpinner.getSelectedItemPosition()) {
                    textView.setTextColor(0xFF000000); // 100% opacity for selected
                } else {
                    textView.setTextColor(0xBF000000); // 75% opacity for non-selected
                }
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        distributerSpinner.setAdapter(adapter);
        
        // Handle dropdown expansion within dialog with animation
        distributerSpinner.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    // Set dropdown width to match spinner width before opening (like add orders page)
                    try {
                        java.lang.reflect.Field popup = android.widget.Spinner.class.getDeclaredField("mPopup");
                        popup.setAccessible(true);
                        android.widget.ListPopupWindow popupWindow = (android.widget.ListPopupWindow) popup.get(distributerSpinner);
                        popupWindow.setWidth(distributerSpinner.getWidth());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    
                    // Show dropdown expansion space with animation
                    dropdownContainer.setVisibility(View.VISIBLE);
                    dropdownContainer.removeAllViews();
                    
                    // Calculate height for 4 items
                    int itemHeight = (int) (50 * getResources().getDisplayMetrics().density);
                    android.view.ViewGroup.LayoutParams params = dropdownContainer.getLayoutParams();
                    params.height = itemHeight * 4;
                    dropdownContainer.setLayoutParams(params);
                    
                    // Animate expansion
                    Animation expandAnim = AnimationUtils.loadAnimation(TargetAchievedActivity.this, R.anim.dropdown_expand);
                    dropdownContainer.startAnimation(expandAnim);
                }
                return false;
            }
        });
        
        // Hide dropdown space when item selected with animation
        distributerSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                // Refresh dropdown to update opacity (like year/month spinners)
                adapter.notifyDataSetChanged();
                
                dropdownContainer.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Animate collapse
                        Animation collapseAnim = AnimationUtils.loadAnimation(TargetAchievedActivity.this, R.anim.dropdown_collapse);
                        collapseAnim.setAnimationListener(new Animation.AnimationListener() {
                            @Override
                            public void onAnimationStart(Animation animation) {}

                            @Override
                            public void onAnimationEnd(Animation animation) {
                                dropdownContainer.setVisibility(View.GONE);
                                // Border is handled by container
                            }

                            @Override
                            public void onAnimationRepeat(Animation animation) {}
                        });
                        dropdownContainer.startAnimation(collapseAnim);
                    }
                }, 100);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                Animation collapseAnim = AnimationUtils.loadAnimation(TargetAchievedActivity.this, R.anim.dropdown_collapse);
                collapseAnim.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {}

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        dropdownContainer.setVisibility(View.GONE);
                        // Border is handled by container
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {}
                });
                dropdownContainer.startAnimation(collapseAnim);
            }
        });
        
        // Set dropdown to appear within dialog bounds with animations
        distributerSpinner.post(new Runnable() {
            @Override
            public void run() {
                try {
                    java.lang.reflect.Field popup = android.widget.Spinner.class.getDeclaredField("mPopup");
                    popup.setAccessible(true);
                    final android.widget.ListPopupWindow popupWindow = (android.widget.ListPopupWindow) popup.get(distributerSpinner);
                    popupWindow.setBackgroundDrawable(getResources().getDrawable(R.drawable.spinner_dropdown_background));
                    popupWindow.setHeight(android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                    
                    // Let dropdown width match spinner width naturally (like add orders page)
                    popupWindow.setWidth(distributerSpinner.getWidth());
                    
                    // Set popup animations
                    popupWindow.setAnimationStyle(R.style.SpinnerDropdownAnimation);
                    
                    // Position dropdown directly below spinner
                    popupWindow.setVerticalOffset((int) (50 * getResources().getDisplayMetrics().density));
                    popupWindow.setHorizontalOffset(0);
                    
                    popupWindow.setOnDismissListener(new android.widget.PopupWindow.OnDismissListener() {
                        @Override
                        public void onDismiss() {
                            if (dropdownContainer.getVisibility() == View.VISIBLE) {
                                Animation collapseAnim = AnimationUtils.loadAnimation(TargetAchievedActivity.this, R.anim.dropdown_collapse);
                                collapseAnim.setAnimationListener(new Animation.AnimationListener() {
                                    @Override
                                    public void onAnimationStart(Animation animation) {}

                                    @Override
                                    public void onAnimationEnd(Animation animation) {
                                        dropdownContainer.setVisibility(View.GONE);
                                    }

                                    @Override
                                    public void onAnimationRepeat(Animation animation) {}
                                });
                                dropdownContainer.startAnimation(collapseAnim);
                            }
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        // Close button click
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        // Upload button click
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String selectedDistributer = distributerSpinner.getSelectedItem().toString();
                Toast.makeText(TargetAchievedActivity.this, 
                    "Uploading stock sheet for: " + selectedDistributer, 
                    Toast.LENGTH_SHORT).show();
                // TODO: Implement file picker and upload logic
                dialog.dismiss();
            }
        });

        // Show dialog
        dialog.show();
    }
}

