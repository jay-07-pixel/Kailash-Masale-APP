package com.example.kailashmasale;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkingDaysActivity extends AppCompatActivity {

    private static final String[] MONTHS = {"Jan", "Feb", "March", "April", "May", "June",
            "July", "Aug", "Sept", "Oct", "Nov", "Dec"};
    /** Short month names for monthlyData doc id (e.g. 2026_Mar) */
    private static final String[] MONTH_SHORT = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    /** Years for spinner: current year first, then previous years (used as filter) */
    private String[] yearOptions;

    private ImageButton backButton;
    private Spinner yearSpinner;
    private Spinner monthSpinner;
    private View gridButton;
    private View uploadButton;
    private View checkInOutButton;
    private LinearLayout emptyRowsContainer;

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
        
        setContentView(R.layout.activity_working_days);

        initializeViews();
        setupYearSpinner();
        setupMonthSpinner();
        setupClickListeners();
        loadWorkingDaysFromCheckIns();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.back_button);
        yearSpinner = findViewById(R.id.year_spinner);
        monthSpinner = findViewById(R.id.month_spinner);
        gridButton = findViewById(R.id.grid_button);
        uploadButton = findViewById(R.id.upload_button);
        checkInOutButton = findViewById(R.id.check_in_out_button);
        emptyRowsContainer = findViewById(R.id.empty_rows_container);
    }

    private void setupYearSpinner() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        yearOptions = new String[5];
        for (int i = 0; i < yearOptions.length; i++) {
            yearOptions[i] = String.valueOf(currentYear - i);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                R.layout.year_spinner_item,
                yearOptions
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
        yearSpinner.setSelection(0); // default: current year
    }

    private void setupMonthSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                R.layout.year_spinner_item,
                MONTHS
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
        monthSpinner.setSelection(Calendar.getInstance().get(Calendar.MONTH)); // default: current month

        yearSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                loadWorkingDaysFromCheckIns();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        monthSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                loadWorkingDaysFromCheckIns();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    /** Load data in parallel (distributors + monthlyData + check_ins) then merge and show — no sequential wait, smooth open. */
    private void loadWorkingDaysFromCheckIns() {
        if (emptyRowsContainer == null) return;
        emptyRowsContainer.removeAllViews();
        addWorkingDaysMessageRow("Loading...");

        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", null);
        String employeeId = prefs.getString("logged_in_employee_id", null);
        if (employeeEmail == null || employeeEmail.isEmpty()) {
            emptyRowsContainer.removeAllViews();
            addWorkingDaysMessageRow("Please log in.");
            return;
        }

        int yearPos = yearSpinner != null ? yearSpinner.getSelectedItemPosition() : 0;
        int monthPos = monthSpinner != null ? monthSpinner.getSelectedItemPosition() : 0;
        if (yearOptions == null || yearPos < 0 || yearPos >= yearOptions.length || monthPos < 0 || monthPos >= MONTHS.length) {
            emptyRowsContainer.removeAllViews();
            addWorkingDaysMessageRow("Select year and month.");
            return;
        }
        final int selectedYear = Integer.parseInt(yearOptions[yearPos]);
        final int selectedMonth = monthPos + 1; // 1-based
        final String monthAbbr = (monthPos >= 0 && monthPos < MONTH_SHORT.length) ? MONTH_SHORT[monthPos] : String.valueOf(selectedMonth);
        final String monthlyDataDocId = (employeeId != null ? employeeId : "") + "_" + selectedYear + "_" + monthAbbr;

        // Holders for parallel results (set from Firestore callbacks)
        final List<String>[] assignedDistributorIdsHolder = new List[] { new ArrayList<>() };
        final Map<String, String>[] nameToIdHolder = new Map[] { new HashMap<>() };
        final Map<String, String>[] daysAssignedHolder = new Map[] { new HashMap<>() };
        final Map<String, TreeSet<String>>[] distributorToDatesHolder = new Map[] { new HashMap<>() };
        final AtomicInteger pending = new AtomicInteger(4);
        final boolean[] hadFailure = new boolean[] { false };

        Runnable tryMerge = () -> {
            if (pending.decrementAndGet() != 0) return;
            runOnUiThread(() -> {
                if (hadFailure[0]) {
                    emptyRowsContainer.removeAllViews();
                    addWorkingDaysMessageRow("Failed to load data.");
                    return;
                }
                // Keep only distributors assigned to this employee
                List<String> assignedIds = assignedDistributorIdsHolder[0];
                Map<String, String> filtered = new HashMap<>();
                for (Map.Entry<String, String> e : nameToIdHolder[0].entrySet()) {
                    if (assignedIds.contains(e.getValue())) filtered.put(e.getKey(), e.getValue());
                }
                nameToIdHolder[0].clear();
                nameToIdHolder[0].putAll(filtered);
                buildDaysAssignedFromMonthlyData(nameToIdHolder[0], daysAssignedHolder[0]);
                List<String> assignedDistributorNames = new ArrayList<>(nameToIdHolder[0].keySet());
                populateWorkingDaysRows(assignedDistributorNames, distributorToDatesHolder[0], daysAssignedHolder[0]);
            });
        };

        // 1) Employee's assigned distributor IDs
        FirebaseFirestore.getInstance().collection("employees")
                .whereEqualTo("email", employeeEmail)
                .limit(1)
                .get()
                .addOnSuccessListener((QuerySnapshot empSnapshot) -> {
                    if (empSnapshot != null && !empSnapshot.isEmpty()) {
                        DocumentSnapshot empDoc = empSnapshot.getDocuments().get(0);
                        Object assigned = empDoc.get("assignedDistributorId");
                        if (assigned == null) assigned = empDoc.get("distributorId");
                        if (assigned == null) assigned = empDoc.get("assignedDistributorIds");
                        List<String> ids = new ArrayList<>();
                        if (assigned instanceof String) ids.add((String) assigned);
                        else if (assigned instanceof List) {
                            for (Object o : (List<?>) assigned) {
                                if (o instanceof String) ids.add((String) o);
                            }
                        }
                        assignedDistributorIdsHolder[0] = ids;
                    }
                    tryMerge.run();
                })
                .addOnFailureListener(e -> { tryMerge.run(); });

        // 2) All distributors (we filter by assigned IDs in tryMerge)
        FirebaseFirestore.getInstance().collection("distributors").get()
                .addOnSuccessListener(distSnapshot -> {
                    if (distSnapshot != null) {
                        for (DocumentSnapshot doc : distSnapshot.getDocuments()) {
                            String name = doc.getString("distributorName");
                            if (name == null || name.isEmpty()) name = doc.getString("distributor_name");
                            if (name != null && !name.isEmpty())
                                nameToIdHolder[0].put(name.trim(), doc.getId());
                        }
                    }
                    tryMerge.run();
                })
                .addOnFailureListener(e -> { hadFailure[0] = true; tryMerge.run(); });

        // 3) monthlyData — in parallel
        FirebaseFirestore.getInstance().collection("monthlyData").document(monthlyDataDocId).get()
                .addOnSuccessListener(monthDoc -> {
                    if (monthDoc != null && monthDoc.exists()) {
                        try {
                            Object dd = monthDoc.get("distributorDetails");
                            if (dd instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> distributorDetails = (Map<String, Object>) dd;
                                for (Map.Entry<String, Object> e : distributorDetails.entrySet()) {
                                    Object details = e.getValue();
                                    if (details instanceof Map) {
                                        Object wd = ((Map<?, ?>) details).get("workingDays");
                                        String wdStr = (wd != null) ? wd.toString() : "000";
                                        daysAssignedHolder[0].put(e.getKey(), wdStr); // key = distributorId for now
                                    }
                                }
                            }
                        } catch (Exception ignored) { }
                    }
                    tryMerge.run();
                })
                .addOnFailureListener(e -> tryMerge.run());

        // 4) check_ins — in parallel
        FirebaseFirestore.getInstance().collection("check_ins")
                .whereEqualTo("employeeEmail", employeeEmail)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    SimpleDateFormat parseFmt = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                    Calendar cal = Calendar.getInstance();
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            String dateStr = doc.getString("date");
                            String distributor = doc.getString("distributor");
                            if (distributor == null) distributor = "";
                            if (dateStr == null || dateStr.isEmpty()) continue;
                            try {
                                cal.setTime(parseFmt.parse(dateStr));
                                if (cal.get(Calendar.YEAR) == selectedYear && cal.get(Calendar.MONTH) + 1 == selectedMonth) {
                                    TreeSet<String> dates = distributorToDatesHolder[0].get(distributor);
                                    if (dates == null) {
                                        dates = new TreeSet<>();
                                        distributorToDatesHolder[0].put(distributor, dates);
                                    }
                                    dates.add(dateStr);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    tryMerge.run();
                })
                .addOnFailureListener(e -> { hadFailure[0] = true; tryMerge.run(); });
    }

    /** Resolve monthlyData distributorId -> workingDays into distributor name -> daysAssigned using nameToId. */
    private void buildDaysAssignedFromMonthlyData(Map<String, String> nameToId, Map<String, String> daysAssignedByDistId) {
        if (daysAssignedByDistId == null || nameToId == null) return;
        Map<String, String> byName = new HashMap<>();
        for (Map.Entry<String, String> nameId : nameToId.entrySet()) {
            String wd = daysAssignedByDistId.get(nameId.getValue());
            byName.put(nameId.getKey(), (wd != null && !wd.isEmpty() && !wd.equals("000")) ? wd : null);
        }
        daysAssignedByDistId.clear();
        daysAssignedByDistId.putAll(byName);
    }

    /** Show all distributors; use "-" for dates, days assigned, or days worked when not set. */
    private void populateWorkingDaysRows(List<String> allDistributorNames, Map<String, TreeSet<String>> distributorToDates, Map<String, String> distributorNameToDaysAssigned) {
        if (emptyRowsContainer == null) return;
        emptyRowsContainer.removeAllViews();
        if (allDistributorNames == null || allDistributorNames.isEmpty()) {
            addWorkingDaysMessageRow("No distributors assigned to you.");
            return;
        }
        SimpleDateFormat parseFmt = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        SimpleDateFormat displayFmt = new SimpleDateFormat("dd MMM", Locale.getDefault());
        List<String> distributors = new ArrayList<>(allDistributorNames);
        Collections.sort(distributors);
        for (int i = 0; i < distributors.size(); i++) {
            String dist = distributors.get(i);
            String daysAssignedStr = (distributorNameToDaysAssigned != null && distributorNameToDaysAssigned.containsKey(dist))
                    ? distributorNameToDaysAssigned.get(dist) : null;
            if (daysAssignedStr == null || daysAssignedStr.isEmpty() || daysAssignedStr.equals("000")) {
                daysAssignedStr = "-";
            }
            TreeSet<String> dateSet = distributorToDates != null ? distributorToDates.get(dist) : null;
            List<String> dateList = dateSet != null ? new ArrayList<>(dateSet) : new ArrayList<>();
            Collections.sort(dateList);
            StringBuilder datesDisplay = new StringBuilder();
            if (dateList.isEmpty()) {
                datesDisplay.append("-");
            } else {
                for (int j = 0; j < dateList.size(); j++) {
                    try {
                        datesDisplay.append(displayFmt.format(parseFmt.parse(dateList.get(j))));
                        if (j < dateList.size() - 1) datesDisplay.append("\n");
                    } catch (Exception e) {
                        datesDisplay.append(dateList.get(j));
                        if (j < dateList.size() - 1) datesDisplay.append("\n");
                    }
                }
            }
            String daysWorkedStr = dateList.isEmpty() ? "-" : String.valueOf(dateList.size());
            boolean lightBg = (i % 2 == 0);
            LinearLayout row = createDataRow(dist, datesDisplay.toString(), daysAssignedStr, daysWorkedStr, lightBg);
            emptyRowsContainer.addView(row);
        }
    }

    private LinearLayout createDataRow(String distributorName, String datesText, String daysAssigned, String daysWorked, boolean lightBackground) {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        row.setOrientation(LinearLayout.HORIZONTAL);
        int padding = (int) (12 * getResources().getDisplayMetrics().density);
        row.setPadding(padding, padding, padding, padding);
        row.setBackgroundResource(lightBackground ? R.drawable.table_row_light : R.drawable.table_row_dark);
        addCell(row, 2f, distributorName);
        addCell(row, 0.8f, datesText);
        addCell(row, 0.5f, daysAssigned);
        addCell(row, 0.5f, daysWorked);
        return row;
    }

    private void addCell(LinearLayout row, float weight, String text) {
        TextView cell = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        cell.setLayoutParams(params);
        cell.setText(text != null ? text : "");
        cell.setGravity(android.view.Gravity.CENTER);
        cell.setTextSize(13);
        cell.setTextColor(0xFF424242);
        cell.setTypeface(getResources().getFont(R.font.inter_font_family));
        row.addView(cell);
    }

    private void addWorkingDaysMessageRow(String message) {
        if (emptyRowsContainer == null) return;
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setPadding((int)(24 * getResources().getDisplayMetrics().density), (int)(20 * getResources().getDisplayMetrics().density), 24, 20);
        tv.setTextColor(0xFF424242);
        tv.setTextSize(14);
        emptyRowsContainer.addView(tv);
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
        
        // Also set on the button itself as fallback
        if (backButton != null) {
            backButton.setOnClickListener(new View.OnClickListener() {
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
                    Intent intent = new Intent(WorkingDaysActivity.this, WeeklyPlannerActivity.class);
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
                    Intent intent = new Intent(WorkingDaysActivity.this, CheckInOutActivity.class);
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
                    Animation expandAnim = AnimationUtils.loadAnimation(WorkingDaysActivity.this, R.anim.dropdown_expand);
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
                        Animation collapseAnim = AnimationUtils.loadAnimation(WorkingDaysActivity.this, R.anim.dropdown_collapse);
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
                }, 100);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                Animation collapseAnim = AnimationUtils.loadAnimation(WorkingDaysActivity.this, R.anim.dropdown_collapse);
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
                                Animation collapseAnim = AnimationUtils.loadAnimation(WorkingDaysActivity.this, R.anim.dropdown_collapse);
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
                Toast.makeText(WorkingDaysActivity.this, 
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






