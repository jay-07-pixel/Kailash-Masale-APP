package com.example.kailashmasale;

import android.app.DatePickerDialog;
import android.app.Dialog;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import android.widget.DatePicker;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WeeklyPlannerActivity extends AppCompatActivity {

    private View backButtonContainer;
    private LinearLayout addWeekButton;
    private ImageButton currentEditButton;
    private ImageButton historicalEditButton;
    private Spinner yearSpinner;
    private Spinner monthSpinner;
    private Spinner weekSpinner;
    private LinearLayout gridButton;
    private LinearLayout uploadButton;
    private LinearLayout checkInOutButton;
    private TextView currentLabel;
    private ViewGroup currentWeekTableBody;
    private ViewGroup historicalWeekTableBody;
    /** For each week option (Week 1,2,3,4,5): range start and end as time in ms (start of day 00:00, end of day 23:59:59). */
    private List<long[]> historicalWeekRanges = new ArrayList<>();

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
        
        setContentView(R.layout.activity_weekly_planner);

        initializeViews();
        setupSpinners();
        setupClickListeners();
        loadCurrentWeekFromFirestore();
    }

    private void initializeViews() {
        backButtonContainer = findViewById(R.id.back_button_container);
        addWeekButton = findViewById(R.id.add_week_button);
        currentEditButton = findViewById(R.id.current_edit_button);
        historicalEditButton = findViewById(R.id.historical_edit_button);
        yearSpinner = findViewById(R.id.year_spinner);
        monthSpinner = findViewById(R.id.month_spinner);
        weekSpinner = findViewById(R.id.week_spinner);
        gridButton = findViewById(R.id.grid_button_layout);
        uploadButton = findViewById(R.id.upload_button_layout);
        checkInOutButton = findViewById(R.id.check_in_out_button);
        currentLabel = findViewById(R.id.current_label);
        currentWeekTableBody = findViewById(R.id.current_week_table_body);
        historicalWeekTableBody = findViewById(R.id.historical_week_table_body);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCurrentWeekFromFirestore();
    }

    private void loadCurrentWeekFromFirestore() {
        if (currentLabel == null || currentWeekTableBody == null) return;
        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        if (employeeEmail.isEmpty()) return;

        FirebaseFirestore.getInstance().collection("weekly_plans")
                .whereEqualTo("employeeEmail", employeeEmail)
                .limit(50)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        runOnUiThread(() -> {
                            if (currentLabel != null) currentLabel.setText("Current :- No week submitted yet");
                        });
                        return;
                    }
                    List<DocumentSnapshot> docs = new ArrayList<>(snapshot.getDocuments());
                    Collections.sort(docs, new Comparator<DocumentSnapshot>() {
                        @Override
                        public int compare(DocumentSnapshot a, DocumentSnapshot b) {
                            Timestamp ta = a.getTimestamp("timestamp");
                            Timestamp tb = b.getTimestamp("timestamp");
                            if (ta == null && tb == null) return 0;
                            if (ta == null) return 1;
                            if (tb == null) return -1;
                            return tb.compareTo(ta);
                        }
                    });
                    DocumentSnapshot doc = docs.get(0);
                    Object daysObj = doc.get("days");
                    String weekStart = doc.getString("weekStartDate");
                    if (weekStart == null) weekStart = "";
                    final String weekLabel = "Current :- Week of " + weekStart;
                    runOnUiThread(() -> {
                        if (currentLabel != null) currentLabel.setText(weekLabel);
                    });
                    if (!(daysObj instanceof List)) return;
                    List<?> daysList = (List<?>) daysObj;
                    final int rowsToFill = Math.min(6, daysList.size());
                    runOnUiThread(() -> {
                        for (int i = 0; i < rowsToFill && (i + 1) < currentWeekTableBody.getChildCount(); i++) {
                            Object dayObj = daysList.get(i);
                            if (!(dayObj instanceof Map)) continue;
                            Map<?, ?> day = (Map<?, ?>) dayObj;
                            String dateStr = getString(day, "date");
                            String distributorStr = getStringFromKeys(day, "distributor", "distributorName", "distributor_name");
                            String bitNameStr = getString(day, "bitName");
                            String lastVisitStr = getString(day, "lastVisitDay");
                            String dateShort = formatDateShort(dateStr);
                            View row = currentWeekTableBody.getChildAt(i + 1);
                            if (row instanceof ViewGroup) {
                                ViewGroup rowGroup = (ViewGroup) row;
                                if (rowGroup.getChildCount() >= 4) {
                                    if (rowGroup.getChildAt(0) instanceof TextView) ((TextView) rowGroup.getChildAt(0)).setText(dateShort);
                                    if (rowGroup.getChildAt(1) instanceof TextView) ((TextView) rowGroup.getChildAt(1)).setText(distributorStr);
                                    if (rowGroup.getChildAt(2) instanceof TextView) ((TextView) rowGroup.getChildAt(2)).setText(bitNameStr);
                                    if (rowGroup.getChildAt(3) instanceof TextView) ((TextView) rowGroup.getChildAt(3)).setText(formatDateShort(lastVisitStr));
                                }
                            }
                        }
                    });
                })
                .addOnFailureListener(e -> runOnUiThread(() -> {
                    if (currentLabel != null) currentLabel.setText("Current :- Week, Month'25");
                }));
    }

    private String getString(Map<?, ?> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString().trim() : "";
    }

    /** Get first non-empty value from map for the given keys (e.g. distributor may be stored under different names). */
    private String getStringFromKeys(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            String s = getString(map, key);
            if (!s.isEmpty()) return s;
        }
        return "";
    }

    private String formatDateShort(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return "";
        try {
            Date d = new SimpleDateFormat("dd / MM / yyyy", Locale.getDefault()).parse(dateStr.trim());
            if (d != null) return new SimpleDateFormat("dd MMM", Locale.getDefault()).format(d);
        } catch (Exception ignored) {}
        return dateStr;
    }

    private void setupSpinners() {
        // Setup Year Spinner (table 2 filter) - 2026 as default
        String[] years = {"2026", "2025", "2024", "2023", "2022", "2021"};
        ArrayAdapter<String> yearAdapter = new ArrayAdapter<String>(
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
        yearAdapter.setDropDownViewResource(R.layout.year_spinner_dropdown_item);
        yearSpinner.setAdapter(yearAdapter);
        yearSpinner.setSelection(0); // Default to 2026

        // Setup Month Spinner
        String[] months = {"Jan", "Feb", "March", "April", "May", "June",
                "July", "Aug", "Sept", "Oct", "Nov", "Dec"};
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<String>(
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
        monthAdapter.setDropDownViewResource(R.layout.year_spinner_dropdown_item);
        monthSpinner.setAdapter(monthAdapter);
        // Default to current month (0 = Jan, 1 = Feb, ...)
        int currentMonth = Calendar.getInstance().get(Calendar.MONTH);
        monthSpinner.setSelection(currentMonth);

        // Week spinner: options depend on selected month/year; populated when month or year changes
        historicalWeekRanges.clear();
        List<String> weekLabels = new ArrayList<>();
        weekLabels.add("Select week");
        ArrayAdapter<String> weekAdapter = new ArrayAdapter<String>(

                this,
                R.layout.year_spinner_item,
                weekLabels
        ) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view;
                if (position == weekSpinner.getSelectedItemPosition()) {
                    textView.setTextColor(0xFF000000);
                } else {
                    textView.setTextColor(0xBF000000);
                }
                return view;
            }
        };
        weekAdapter.setDropDownViewResource(R.layout.year_spinner_dropdown_item);
        if (weekSpinner != null) {
            weekSpinner.setAdapter(weekAdapter);
        }

        updateWeekSpinnerForMonthYear();
        selectCurrentWeekInSpinner();
        loadHistoricalWeekFromFirestore();
        if (yearSpinner != null) {
            yearSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    updateWeekSpinnerForMonthYear();
                    loadHistoricalWeekFromFirestore();
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }
        if (monthSpinner != null) {
            monthSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    updateWeekSpinnerForMonthYear();
                    loadHistoricalWeekFromFirestore();
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }
        if (weekSpinner != null) {
            weekSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    loadHistoricalWeekFromFirestore();
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }
    }

    /** Build week options: Week 1 (1-7), Week 2 (8-14), Week 3 (15-21), Week 4 (22-28), Week 5 (29-end) if month has 29+ days. */
    private void updateWeekSpinnerForMonthYear() {
        if (yearSpinner == null || monthSpinner == null || weekSpinner == null) return;
        int yearPos = yearSpinner.getSelectedItemPosition();
        int monthPos = monthSpinner.getSelectedItemPosition();
        if (yearPos < 0 || monthPos < 0) return;
        String[] years = {"2026", "2025", "2024", "2023", "2022", "2021"};
        int year = Integer.parseInt(years[yearPos]);
        int month = monthPos;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        int lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        List<String> weekLabels = new ArrayList<>();
        historicalWeekRanges.clear();
        weekLabels.add("Select week");
        // Week 1: 1-7, Week 2: 8-14, Week 3: 15-21, Week 4: 22-28, Week 5: 29-lastDay (if lastDay >= 29)
        int[] weekEndDays = {7, 14, 21, 28, lastDay};
        int weekCount = lastDay >= 29 ? 5 : 4;
        for (int w = 0; w < weekCount; w++) {
            int startDay = w * 7 + 1;
            int endDay = weekEndDays[w];
            weekLabels.add("Week " + (w + 1));
            cal.set(Calendar.DAY_OF_MONTH, startDay);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            long rangeStartMs = cal.getTimeInMillis();
            cal.set(Calendar.DAY_OF_MONTH, endDay);
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            long rangeEndMs = cal.getTimeInMillis();
            historicalWeekRanges.add(new long[]{rangeStartMs, rangeEndMs});
        }
        ArrayAdapter<String> weekAdapter = (ArrayAdapter<String>) weekSpinner.getAdapter();
        if (weekAdapter != null) {
            weekAdapter.clear();
            weekAdapter.addAll(weekLabels);
            weekAdapter.notifyDataSetChanged();
            weekSpinner.setSelection(0);
        }
    }

    /** Set week spinner to the week that contains today (Week 1-5 by calendar dates in month). */
    private void selectCurrentWeekInSpinner() {
        if (weekSpinner == null || historicalWeekRanges.isEmpty()) return;
        Calendar today = Calendar.getInstance();
        int yearPos = yearSpinner != null ? yearSpinner.getSelectedItemPosition() : -1;
        int monthPos = monthSpinner != null ? monthSpinner.getSelectedItemPosition() : -1;
        if (yearPos < 0 || monthPos < 0) return;
        String[] years = {"2026", "2025", "2024", "2023", "2022", "2021"};
        int selectedYear = Integer.parseInt(years[yearPos]);
        int selectedMonth = monthPos;
        if (today.get(Calendar.YEAR) != selectedYear || today.get(Calendar.MONTH) != selectedMonth) return;
        int dayOfMonth = today.get(Calendar.DAY_OF_MONTH);
        int weekNum = dayOfMonth <= 7 ? 1 : dayOfMonth <= 14 ? 2 : dayOfMonth <= 21 ? 3 : dayOfMonth <= 28 ? 4 : 5;
        if (weekNum <= historicalWeekRanges.size()) {
            weekSpinner.setSelection(weekNum);
        }
    }

    private void loadHistoricalWeekFromFirestore() {
        if (historicalWeekTableBody == null || weekSpinner == null) return;
        int weekPos = weekSpinner.getSelectedItemPosition();
        if (weekPos <= 0 || weekPos > historicalWeekRanges.size()) {
            clearHistoricalTable();
            return;
        }
        long[] range = historicalWeekRanges.get(weekPos - 1);
        long rangeStartMs = range[0];
        long rangeEndMs = range[1];
        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        if (employeeEmail.isEmpty()) {
            clearHistoricalTable();
            return;
        }
        SimpleDateFormat dateFormatFull = new SimpleDateFormat("dd / MM / yyyy", Locale.getDefault());
        FirebaseFirestore.getInstance().collection("weekly_plans")
                .whereEqualTo("employeeEmail", employeeEmail)
                .limit(100)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        runOnUiThread(() -> clearHistoricalTable());
                        return;
                    }
                    DocumentSnapshot match = null;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String ws = doc.getString("weekStartDate");
                        if (ws == null || ws.trim().isEmpty()) continue;
                        try {
                            Date planMonday = dateFormatFull.parse(ws.trim().replaceAll("\\s+", " "));
                            if (planMonday == null) continue;
                            Calendar planCal = Calendar.getInstance();
                            planCal.setTime(planMonday);
                            planCal.set(Calendar.HOUR_OF_DAY, 0);
                            planCal.set(Calendar.MINUTE, 0);
                            planCal.set(Calendar.SECOND, 0);
                            long planStartMs = planCal.getTimeInMillis();
                            planCal.add(Calendar.DAY_OF_YEAR, 6);
                            planCal.set(Calendar.HOUR_OF_DAY, 23);
                            planCal.set(Calendar.MINUTE, 59);
                            planCal.set(Calendar.SECOND, 59);
                            long planEndMs = planCal.getTimeInMillis();
                            if (planStartMs <= rangeEndMs && planEndMs >= rangeStartMs) {
                                match = doc;
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                    if (match == null) {
                        runOnUiThread(() -> clearHistoricalTable());
                        return;
                    }
                    Object daysObj = match.get("days");
                    if (!(daysObj instanceof List)) {
                        runOnUiThread(() -> clearHistoricalTable());
                        return;
                    }
                    List<?> daysList = (List<?>) daysObj;
                    final int rowsToFill = Math.min(6, daysList.size());
                    runOnUiThread(() -> {
                        for (int i = 0; i < rowsToFill && (i + 1) < historicalWeekTableBody.getChildCount(); i++) {
                            Object dayObj = daysList.get(i);
                            if (!(dayObj instanceof Map)) continue;
                            Map<?, ?> day = (Map<?, ?>) dayObj;
                            String dateStr = getString(day, "date");
                            String distributorStr = getStringFromKeys(day, "distributor", "distributorName", "distributor_name");
                            String bitNameStr = getString(day, "bitName");
                            String lastVisitStr = getString(day, "lastVisitDay");
                            String dateShort = formatDateShort(dateStr);
                            View row = historicalWeekTableBody.getChildAt(i + 1);
                            if (row instanceof ViewGroup) {
                                ViewGroup rowGroup = (ViewGroup) row;
                                if (rowGroup.getChildCount() >= 4) {
                                    if (rowGroup.getChildAt(0) instanceof TextView) ((TextView) rowGroup.getChildAt(0)).setText(dateShort);
                                    if (rowGroup.getChildAt(1) instanceof TextView) ((TextView) rowGroup.getChildAt(1)).setText(distributorStr);
                                    if (rowGroup.getChildAt(2) instanceof TextView) ((TextView) rowGroup.getChildAt(2)).setText(bitNameStr);
                                    if (rowGroup.getChildAt(3) instanceof TextView) ((TextView) rowGroup.getChildAt(3)).setText(formatDateShort(lastVisitStr));
                                }
                            }
                        }
                        for (int i = rowsToFill; i + 1 < historicalWeekTableBody.getChildCount(); i++) {
                            View row = historicalWeekTableBody.getChildAt(i + 1);
                            if (row instanceof ViewGroup) {
                                ViewGroup rowGroup = (ViewGroup) row;
                                if (rowGroup.getChildCount() >= 4) {
                                    if (rowGroup.getChildAt(0) instanceof TextView) ((TextView) rowGroup.getChildAt(0)).setText("");
                                    if (rowGroup.getChildAt(1) instanceof TextView) ((TextView) rowGroup.getChildAt(1)).setText("");
                                    if (rowGroup.getChildAt(2) instanceof TextView) ((TextView) rowGroup.getChildAt(2)).setText("");
                                    if (rowGroup.getChildAt(3) instanceof TextView) ((TextView) rowGroup.getChildAt(3)).setText("");
                                }
                            }
                        }
                    });
                })
                .addOnFailureListener(e -> runOnUiThread(() -> clearHistoricalTable()));
    }

    private String normalizeWeekStartDate(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ");
    }

    private void clearHistoricalTable() {
        if (historicalWeekTableBody == null) return;
        for (int i = 1; i < historicalWeekTableBody.getChildCount(); i++) {
            View row = historicalWeekTableBody.getChildAt(i);
            if (row instanceof ViewGroup) {
                ViewGroup rowGroup = (ViewGroup) row;
                if (rowGroup.getChildCount() >= 4) {
                    if (rowGroup.getChildAt(0) instanceof TextView) ((TextView) rowGroup.getChildAt(0)).setText("");
                    if (rowGroup.getChildAt(1) instanceof TextView) ((TextView) rowGroup.getChildAt(1)).setText("");
                    if (rowGroup.getChildAt(2) instanceof TextView) ((TextView) rowGroup.getChildAt(2)).setText("");
                    if (rowGroup.getChildAt(3) instanceof TextView) ((TextView) rowGroup.getChildAt(3)).setText("");
                }
            }
        }
    }

    private void setupClickListeners() {
        backButtonContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        addWeekButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddWeekDialog();
            }
        });

        currentEditButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(WeeklyPlannerActivity.this, "Edit Current Week", Toast.LENGTH_SHORT).show();
                // TODO: Implement edit current week functionality
            }
        });

        historicalEditButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(WeeklyPlannerActivity.this, "Edit Historical Week", Toast.LENGTH_SHORT).show();
                // TODO: Implement edit historical week functionality
            }
        });

        if (gridButton != null) {
            gridButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Already on Weekly Planner page: no navigation needed
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
                    Intent intent = new Intent(WeeklyPlannerActivity.this, CheckInOutActivity.class);
                    startActivity(intent);
                }
            });
        }
    }

    private void showUploadStockDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_upload_stock);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.7f);
            dialog.getWindow().setLayout(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
            dialog.getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
        }

        final CardView dialogCard = dialog.findViewById(R.id.dialog_card);
        ImageButton closeButton = dialog.findViewById(R.id.dialog_close_button);
        Spinner distributerSpinner = dialog.findViewById(R.id.distributer_spinner);
        LinearLayout uploadButton = dialog.findViewById(R.id.upload_sheet_button);
        final LinearLayout dropdownContainer = dialog.findViewById(R.id.dropdown_container);

        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.dialog_fade_in);
        dialogCard.startAnimation(fadeIn);

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
                if (position == ((Spinner) parent).getSelectedItemPosition()) {
                    textView.setTextColor(0xFF000000);
                } else {
                    textView.setTextColor(0xBF000000);
                }
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        distributerSpinner.setAdapter(adapter);

        distributerSpinner.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    try {
                        java.lang.reflect.Field popup = android.widget.Spinner.class.getDeclaredField("mPopup");
                        popup.setAccessible(true);
                        android.widget.ListPopupWindow popupWindow = (android.widget.ListPopupWindow) popup.get(distributerSpinner);
                        popupWindow.setWidth(distributerSpinner.getWidth());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    dropdownContainer.setVisibility(View.VISIBLE);
                    dropdownContainer.removeAllViews();

                    int itemHeight = (int) (50 * getResources().getDisplayMetrics().density);
                    android.view.ViewGroup.LayoutParams params = dropdownContainer.getLayoutParams();
                    params.height = itemHeight * 4;
                    dropdownContainer.setLayoutParams(params);

                    Animation expandAnim = AnimationUtils.loadAnimation(WeeklyPlannerActivity.this, R.anim.dropdown_expand);
                    dropdownContainer.startAnimation(expandAnim);
                }
                return false;
            }
        });

        distributerSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                adapter.notifyDataSetChanged();

                dropdownContainer.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Animation collapseAnim = AnimationUtils.loadAnimation(WeeklyPlannerActivity.this, R.anim.dropdown_collapse);
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
                Animation collapseAnim = AnimationUtils.loadAnimation(WeeklyPlannerActivity.this, R.anim.dropdown_collapse);
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

        distributerSpinner.post(new Runnable() {
            @Override
            public void run() {
                try {
                    java.lang.reflect.Field popup = android.widget.Spinner.class.getDeclaredField("mPopup");
                    popup.setAccessible(true);
                    final android.widget.ListPopupWindow popupWindow = (android.widget.ListPopupWindow) popup.get(distributerSpinner);
                    popupWindow.setBackgroundDrawable(getResources().getDrawable(R.drawable.spinner_dropdown_background));
                    popupWindow.setHeight(android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                    popupWindow.setWidth(distributerSpinner.getWidth());
                    popupWindow.setAnimationStyle(R.style.SpinnerDropdownAnimation);
                    popupWindow.setVerticalOffset((int) (50 * getResources().getDisplayMetrics().density));
                    popupWindow.setHorizontalOffset(0);
                    popupWindow.setOnDismissListener(new android.widget.PopupWindow.OnDismissListener() {
                        @Override
                        public void onDismiss() {
                            if (dropdownContainer.getVisibility() == View.VISIBLE) {
                                Animation collapseAnim = AnimationUtils.loadAnimation(WeeklyPlannerActivity.this, R.anim.dropdown_collapse);
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

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String selectedDistributer = distributerSpinner.getSelectedItem().toString();
                Toast.makeText(WeeklyPlannerActivity.this,
                        "Uploading stock sheet for: " + selectedDistributer,
                        Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void showAddWeekDialog() {
        // Create dialog
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_week);
        
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
        final TextView dayM = dialog.findViewById(R.id.day_m);
        final TextView dayT = dialog.findViewById(R.id.day_t);
        final TextView dayW = dialog.findViewById(R.id.day_w);
        final TextView dayTh = dialog.findViewById(R.id.day_th);
        final TextView dayF = dialog.findViewById(R.id.day_f);
        final TextView dayS = dialog.findViewById(R.id.day_s);
        final EditText dateEditText = dialog.findViewById(R.id.date_edittext);
        final Spinner distributorSpinner = dialog.findViewById(R.id.distributor_spinner);
        final Spinner bitNameSpinner = dialog.findViewById(R.id.bit_name_spinner);
        final EditText lastVisitDayEditText = dialog.findViewById(R.id.last_visit_day_edittext);
        final EditText primaryTargetPendingEditText = dialog.findViewById(R.id.primary_target_pending_edittext);
        final EditText primaryGoalEditText = dialog.findViewById(R.id.primary_goal_edittext);
        final Button saveButton = dialog.findViewById(R.id.save_button);

        // Animate dialog entrance
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.dialog_fade_in);
        if (dialogCard != null) {
            dialogCard.startAnimation(fadeIn);
        }

        // Setup day buttons - Monday to Saturday only (no Sunday)
        final TextView[] dayButtons = {dayM, dayT, dayW, dayTh, dayF, dayS};
        final String[] dayTexts = {"M", "T", "W", "Th", "F", "S"};
        final boolean[] filledDays = new boolean[6]; // M=0 .. S=5
        final String[][] dayData = new String[6][6]; // [dayIndex][date, distributor, bitName, lastVisit, targetPending, goal]
        final int[] selectedDayIndex = new int[]{0};
        final Button submitWeekButton = dialog.findViewById(R.id.submit_week_button);
        if (submitWeekButton != null) submitWeekButton.setVisibility(View.GONE);

        // Auto-fill Mon–Sat with the nearest upcoming week (Monday to Sunday)
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd / MM / yyyy", Locale.getDefault());
        Calendar today = Calendar.getInstance();
        int dow = today.get(Calendar.DAY_OF_WEEK); // Sunday=1, Monday=2, ..., Saturday=7
        int daysToMonday = (dow == Calendar.MONDAY) ? 0 : (dow == Calendar.SUNDAY ? 1 : (9 - dow) % 7);
        Calendar weekStart = (Calendar) today.clone();
        weekStart.add(Calendar.DAY_OF_YEAR, daysToMonday);
        for (int i = 0; i < 6; i++) {
            Calendar d = (Calendar) weekStart.clone();
            d.add(Calendar.DAY_OF_YEAR, i);
            dayData[i][0] = dateFormat.format(d.getTime());
        }
        if (dateEditText != null) dateEditText.setText(dayData[0][0]);
        Calendar oneMonthBefore = (Calendar) weekStart.clone();
        oneMonthBefore.add(Calendar.MONTH, -1);
        if (lastVisitDayEditText != null) lastVisitDayEditText.setText(dateFormat.format(oneMonthBefore.getTime()));

        // Set initial state - Monday selected
        if (dayM != null) {
            dayM.setBackgroundResource(R.drawable.day_button_selected);
            dayM.setTextColor(Color.WHITE);
        }

        // Setup distributor spinner with "Select Distributor"; load assigned distributors from Firestore
        final List<String> distributorList = new ArrayList<>();
        distributorList.add("Select Distributor");
        final ArrayAdapter<String> distributorAdapter = new ArrayAdapter<String>(
                this,
                R.layout.dialog_spinner_item,
                distributorList
        ) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view;
                
                // Highlight selected item
                if (position == distributorSpinner.getSelectedItemPosition()) {
                    textView.setBackgroundResource(R.drawable.dropdown_item_selected_border);
                    textView.setTextColor(android.graphics.Color.WHITE);
                } else {
                    textView.setBackgroundResource(R.drawable.dropdown_item_border);
                    textView.setTextColor(android.graphics.Color.parseColor("#666666"));
                }
                
                textView.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
                textView.setPadding(
                    (int) (16 * getResources().getDisplayMetrics().density),
                    (int) (12 * getResources().getDisplayMetrics().density),
                    (int) (16 * getResources().getDisplayMetrics().density),
                    (int) (12 * getResources().getDisplayMetrics().density)
                );
                
                return view;
            }
        };
        distributorAdapter.setDropDownViewResource(R.layout.dialog_spinner_dropdown_item);
        if (distributorSpinner != null) {
            distributorSpinner.setAdapter(distributorAdapter);
            distributorSpinner.setSelection(0);
        }
        final List<String> assignedDistributorIdsForAddWeek = new ArrayList<>();
        final String[] pendingBitSelection = new String[1]; // when restoring a day, set bit name here so it's selected after bits load
        loadAssignedDistributorsForAddWeek(distributorAdapter, assignedDistributorIdsForAddWeek);

        // Bit spinner: options loaded when distributor is selected
        final List<String> bitList = new ArrayList<>();
        bitList.add("Select bit");
        final ArrayAdapter<String> bitAdapter = new ArrayAdapter<String>(
                this,
                R.layout.dialog_spinner_item,
                bitList
        ) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view;
                if (position == bitNameSpinner.getSelectedItemPosition()) {
                    textView.setBackgroundResource(R.drawable.dropdown_item_selected_border);
                    textView.setTextColor(android.graphics.Color.WHITE);
                } else {
                    textView.setBackgroundResource(R.drawable.dropdown_item_border);
                    textView.setTextColor(android.graphics.Color.parseColor("#666666"));
                }
                textView.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
                textView.setPadding(
                    (int) (16 * getResources().getDisplayMetrics().density),
                    (int) (12 * getResources().getDisplayMetrics().density),
                    (int) (16 * getResources().getDisplayMetrics().density),
                    (int) (12 * getResources().getDisplayMetrics().density)
                );
                return view;
            }
        };
        bitAdapter.setDropDownViewResource(R.layout.dialog_spinner_dropdown_item);
        if (bitNameSpinner != null) {
            bitNameSpinner.setAdapter(bitAdapter);
            bitNameSpinner.setSelection(0);
        }
        if (distributorSpinner != null) {
            distributorSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    distributorAdapter.notifyDataSetChanged();
                    if (position > 0 && position <= assignedDistributorIdsForAddWeek.size()) {
                        loadBitsForDistributorForAddWeek(assignedDistributorIdsForAddWeek.get(position - 1), bitAdapter, bitNameSpinner, pendingBitSelection);
                    } else {
                        bitAdapter.clear();
                        bitAdapter.add("Select bit");
                        bitAdapter.notifyDataSetChanged();
                        if (bitNameSpinner != null) bitNameSpinner.setSelection(0);
                    }
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }

        // Date and Last Visit Day: open calendar on click. When Date changes, Last Visit = Date minus 1 month.
        if (dateEditText != null) {
            dateEditText.setFocusable(false);
            dateEditText.setClickable(true);
            dateEditText.setCursorVisible(false);
            dateEditText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDatePickerForEditText(dateEditText, lastVisitDayEditText);
                }
            });
        }
        if (lastVisitDayEditText != null) {
            lastVisitDayEditText.setFocusable(false);
            lastVisitDayEditText.setClickable(true);
            lastVisitDayEditText.setCursorVisible(false);
            lastVisitDayEditText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showDatePickerForEditText(lastVisitDayEditText, null);
                }
            });
        }

        // Day button click: select day and load its saved data if any (Mon–Sat)
        View.OnClickListener dayButtonListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < dayButtons.length; i++) {
                    if (dayButtons[i] == v) {
                        selectedDayIndex[0] = i;
                        break;
                    }
                }
                for (int i = 0; i < dayButtons.length; i++) {
                    if (dayButtons[i] != null) {
                        dayButtons[i].setBackgroundResource(R.drawable.day_button_unselected);
                        dayButtons[i].setTextColor(Color.parseColor("#34404F"));
                    }
                }
                TextView clickedButton = (TextView) v;
                clickedButton.setBackgroundResource(R.drawable.day_button_selected);
                clickedButton.setTextColor(Color.WHITE);
                int idx = selectedDayIndex[0];
                SimpleDateFormat dateFormatForDay = new SimpleDateFormat("dd / MM / yyyy", Locale.getDefault());
                if (idx < 6 && dayData[idx][0] != null && !dayData[idx][0].isEmpty()) {
                    // Mon–Sat: always show the auto-filled date for this day
                    if (dateEditText != null) dateEditText.setText(dayData[idx][0]);
                    if (filledDays[idx]) {
                        // Load saved data for this day
                        if (lastVisitDayEditText != null) lastVisitDayEditText.setText(dayData[idx][3]);
                        if (primaryTargetPendingEditText != null) primaryTargetPendingEditText.setText(dayData[idx][4]);
                        if (primaryGoalEditText != null) primaryGoalEditText.setText(dayData[idx][5]);
                        if (distributorSpinner != null && dayData[idx][1] != null) {
                            for (int p = 0; p < distributorAdapter.getCount(); p++) {
                                if (dayData[idx][1].equals(distributorAdapter.getItem(p))) {
                                    distributorSpinner.setSelection(p);
                                    break;
                                }
                            }
                        }
                        if (bitNameSpinner != null && bitAdapter != null && dayData[idx][2] != null && !dayData[idx][2].isEmpty()) {
                            pendingBitSelection[0] = dayData[idx][2];
                            for (int p = 0; p < bitAdapter.getCount(); p++) {
                                if (dayData[idx][2].equals(bitAdapter.getItem(p))) {
                                    bitNameSpinner.setSelection(p);
                                    pendingBitSelection[0] = null;
                                    break;
                                }
                            }
                        }
                    } else {
                        // Day not yet filled: set Last Visit to one month before this day's date; clear other fields
                        try {
                            Date d = dateFormatForDay.parse(dayData[idx][0].trim());
                            if (d != null) {
                                Calendar cal = Calendar.getInstance();
                                cal.setTime(d);
                                cal.add(Calendar.MONTH, -1);
                                if (lastVisitDayEditText != null) lastVisitDayEditText.setText(dateFormatForDay.format(cal.getTime()));
                            } else {
                                if (lastVisitDayEditText != null) lastVisitDayEditText.setText("");
                            }
                        } catch (Exception e) {
                            if (lastVisitDayEditText != null) lastVisitDayEditText.setText("");
                        }
                        if (primaryTargetPendingEditText != null) primaryTargetPendingEditText.setText("");
                        if (primaryGoalEditText != null) primaryGoalEditText.setText("");
                        if (distributorSpinner != null) distributorSpinner.setSelection(0);
                        if (bitNameSpinner != null) bitNameSpinner.setSelection(0);
                        pendingBitSelection[0] = null;
                    }
                } else {
                    // No date for this day: clear form
                    pendingBitSelection[0] = null;
                    if (dateEditText != null) dateEditText.setText("");
                    if (lastVisitDayEditText != null) lastVisitDayEditText.setText("");
                    if (primaryTargetPendingEditText != null) primaryTargetPendingEditText.setText("");
                    if (primaryGoalEditText != null) primaryGoalEditText.setText("");
                    if (distributorSpinner != null) distributorSpinner.setSelection(0);
                    if (bitNameSpinner != null) bitNameSpinner.setSelection(0);
                }
            }
        };
        for (TextView dayButton : dayButtons) {
            if (dayButton != null) dayButton.setOnClickListener(dayButtonListener);
        }

        // Save button click: save current day (Mon–Sat only); validate all fields
        if (saveButton != null) {
            saveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int idx = selectedDayIndex[0];
                    String date = dateEditText != null ? dateEditText.getText().toString().trim() : "";
                    String distributor = distributorSpinner != null && distributorSpinner.getSelectedItem() != null ? distributorSpinner.getSelectedItem().toString() : "";
                    String bitName = "";
                    if (bitNameSpinner != null && bitNameSpinner.getSelectedItem() != null) {
                        String b = bitNameSpinner.getSelectedItem().toString().trim();
                        if (!"Select bit".equals(b)) bitName = b;
                    }
                    String lastVisitDay = lastVisitDayEditText != null ? lastVisitDayEditText.getText().toString().trim() : "";
                    String primaryTargetPending = primaryTargetPendingEditText != null ? primaryTargetPendingEditText.getText().toString().trim() : "";
                    String primaryGoal = primaryGoalEditText != null ? primaryGoalEditText.getText().toString().trim() : "";

                    if (date.isEmpty()) {
                        Toast.makeText(WeeklyPlannerActivity.this, "Please enter Date", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (distributor.equals("Select Distributor") || distributor.isEmpty()) {
                        Toast.makeText(WeeklyPlannerActivity.this, "Please select a distributor", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (bitName.isEmpty()) {
                        Toast.makeText(WeeklyPlannerActivity.this, "Please select a bit", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (lastVisitDay.isEmpty()) {
                        Toast.makeText(WeeklyPlannerActivity.this, "Please enter Last Visit Day", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (primaryTargetPending.isEmpty()) {
                        Toast.makeText(WeeklyPlannerActivity.this, "Please enter Primary Target Pending", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (primaryGoal.isEmpty()) {
                        Toast.makeText(WeeklyPlannerActivity.this, "Please enter Primary Goal", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    dayData[idx][0] = date;
                    dayData[idx][1] = distributor;
                    dayData[idx][2] = bitName;
                    dayData[idx][3] = lastVisitDay;
                    dayData[idx][4] = primaryTargetPending;
                    dayData[idx][5] = primaryGoal;
                    filledDays[idx] = true;

                    Toast.makeText(WeeklyPlannerActivity.this, dayTexts[idx] + " saved. Fill all days Mon–Sat to enable Submit.", Toast.LENGTH_SHORT).show();

                    boolean allFilled = true;
                    for (int i = 0; i < 6; i++) {
                        if (!filledDays[i]) {
                            allFilled = false;
                            break;
                        }
                    }
                    if (submitWeekButton != null) {
                        submitWeekButton.setVisibility(allFilled ? View.VISIBLE : View.GONE);
                    }
                }
            });
        }

        // Submit button: only enabled when all Mon–Sat are filled
        if (submitWeekButton != null) {
            submitWeekButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean allFilled = true;
                    for (int i = 0; i < 6; i++) {
                        if (!filledDays[i]) {
                            allFilled = false;
                            break;
                        }
                    }
                    if (!allFilled) {
                        Toast.makeText(WeeklyPlannerActivity.this, "Please fill and save all days from Monday to Saturday first.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
                    String employeeEmail = prefs.getString("logged_in_employee_email", "");
                    String employeeName = prefs.getString("logged_in_employee_name", "");
                    if (employeeEmail.isEmpty()) {
                        Toast.makeText(WeeklyPlannerActivity.this, "Not logged in", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    List<Map<String, Object>> daysList = new ArrayList<>();
                    for (int i = 0; i < 6; i++) {
                        Map<String, Object> dayMap = new HashMap<>();
                        dayMap.put("day", dayTexts[i]);
                        dayMap.put("date", dayData[i][0] != null ? dayData[i][0] : "");
                        dayMap.put("distributor", dayData[i][1] != null ? dayData[i][1] : "");
                        dayMap.put("bitName", dayData[i][2] != null ? dayData[i][2] : "");
                        dayMap.put("lastVisitDay", dayData[i][3] != null ? dayData[i][3] : "");
                        dayMap.put("primaryTargetPending", dayData[i][4] != null ? dayData[i][4] : "");
                        dayMap.put("primaryGoal", dayData[i][5] != null ? dayData[i][5] : "");
                        daysList.add(dayMap);
                    }
                    Map<String, Object> weekDoc = new HashMap<>();
                    weekDoc.put("employeeEmail", employeeEmail);
                    weekDoc.put("employeeName", employeeName);
                    weekDoc.put("days", daysList);
                    weekDoc.put("weekStartDate", dayData[0][0] != null ? dayData[0][0] : "");
                    weekDoc.put("timestamp", FieldValue.serverTimestamp());
                    if (submitWeekButton != null) submitWeekButton.setEnabled(false);
                    FirebaseFirestore.getInstance().collection("weekly_plans")
                            .add(weekDoc)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(WeeklyPlannerActivity.this, "Week submitted and saved to Firebase", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            })
                            .addOnFailureListener(e -> {
                                if (submitWeekButton != null) submitWeekButton.setEnabled(true);
                                Toast.makeText(WeeklyPlannerActivity.this, "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                }
            });
        }

        // Show dialog
        dialog.show();
    }

    private void showDatePickerForEditText(final EditText target, final EditText setLastVisitOneMonthBefore) {
        if (target == null) return;
        Calendar cal = Calendar.getInstance();
        String current = target.getText().toString().trim();
        if (!current.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd / MM / yyyy", Locale.getDefault());
                Date parsed = sdf.parse(current);
                if (parsed != null) cal.setTime(parsed);
            } catch (Exception ignored) {}
        }
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        DatePickerDialog picker = new DatePickerDialog(this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int y, int m, int dayOfMonth) {
                        Calendar selected = Calendar.getInstance();
                        selected.set(y, m, dayOfMonth);
                        target.setText(new SimpleDateFormat("dd / MM / yyyy", Locale.getDefault()).format(selected.getTime()));
                        if (setLastVisitOneMonthBefore != null) {
                            Calendar oneMonthBefore = (Calendar) selected.clone();
                            oneMonthBefore.add(Calendar.MONTH, -1);
                            setLastVisitOneMonthBefore.setText(new SimpleDateFormat("dd / MM / yyyy", Locale.getDefault()).format(oneMonthBefore.getTime()));
                        }
                    }
                },
                year, month, day);
        picker.show();
    }

    private void loadAssignedDistributorsForAddWeek(final ArrayAdapter<String> distributorAdapter, final List<String> assignedDistributorIdsForAddWeek) {
        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", null);
        if (employeeEmail == null || employeeEmail.isEmpty()) {
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
                    if (assigned == null) assigned = employeeDoc.get("distributorId");
                    if (assigned == null) assigned = employeeDoc.get("assignedDistributorIds");
                    List<String> distributorIds = new ArrayList<>();
                    if (assigned instanceof String) {
                        distributorIds.add((String) assigned);
                    } else if (assigned instanceof List) {
                        for (Object o : (List<?>) assigned) {
                            if (o instanceof String) distributorIds.add((String) o);
                        }
                    }
                    if (distributorIds.isEmpty()) return;
                    fetchDistributorNamesForAddWeek(db, distributorIds, distributorAdapter, assignedDistributorIdsForAddWeek);
                });
    }

    private void fetchDistributorNamesForAddWeek(FirebaseFirestore db, List<String> distributorIds, final ArrayAdapter<String> distributorAdapter, final List<String> outAssignedIds) {
        final int total = distributorIds.size();
        final List<String> orderedNames = new ArrayList<>(Collections.nCopies(total, ""));
        final int[] fetched = {0};
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
                                outAssignedIds.clear();
                                distributorAdapter.clear();
                                distributorAdapter.add("Select Distributor");
                                for (int j = 0; j < total; j++) {
                                    String n = orderedNames.get(j);
                                    if (n != null && !n.trim().isEmpty()) {
                                        distributorAdapter.add(n);
                                        outAssignedIds.add(distributorIds.get(j));
                                    }
                                }
                                distributorAdapter.notifyDataSetChanged();
                            });
                        }
                    })
                    .addOnFailureListener(e -> {
                        fetched[0]++;
                        if (fetched[0] == total) {
                            runOnUiThread(() -> {
                                distributorAdapter.clear();
                                distributorAdapter.add("Select Distributor");
                                distributorAdapter.notifyDataSetChanged();
                                Toast.makeText(WeeklyPlannerActivity.this, "Could not load distributors.", Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
        }
    }

    /** Load bits for the selected distributor from Firestore and set bit spinner options. */
    private void loadBitsForDistributorForAddWeek(String distributorId, final ArrayAdapter<String> bitAdapter, final Spinner bitNameSpinner, final String[] pendingBitToSelect) {
        bitAdapter.clear();
        bitAdapter.add("Select bit");
        bitAdapter.notifyDataSetChanged();
        if (distributorId == null || distributorId.isEmpty()) return;
        FirebaseFirestore.getInstance().collection("distributors").document(distributorId)
                .get()
                .addOnSuccessListener(doc -> {
                    List<String> bits = new ArrayList<>();
                    if (doc != null && doc.exists()) {
                        Object bitsObj = doc.get("bits");
                        if (bitsObj instanceof List) {
                            for (Object o : (List<?>) bitsObj) {
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
                        if (pendingBitToSelect != null && pendingBitToSelect[0] != null && bitNameSpinner != null) {
                            for (int p = 0; p < bitAdapter.getCount(); p++) {
                                if (pendingBitToSelect[0].equals(bitAdapter.getItem(p))) {
                                    bitNameSpinner.setSelection(p);
                                    break;
                                }
                            }
                            pendingBitToSelect[0] = null;
                        }
                    });
                })
                .addOnFailureListener(e -> runOnUiThread(() -> {
                    bitAdapter.clear();
                    bitAdapter.add("Select bit");
                    bitAdapter.notifyDataSetChanged();
                    if (pendingBitToSelect != null) pendingBitToSelect[0] = null;
                }));
    }
}
