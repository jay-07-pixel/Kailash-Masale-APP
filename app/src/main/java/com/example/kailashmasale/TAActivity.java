package com.example.kailashmasale;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;
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
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class TAActivity extends AppCompatActivity {

    private static final String TAG = "TAActivity";

    /** Matches Expenditure / Disbursement: type 1 = red TA, type 2 = blue TA. */
    private static final int TA_TEXT_COLOR_DEFAULT = 0xFF424242;
    private static final int TA_TEXT_COLOR_RED = 0xFFD32F2F;
    private static final int TA_TEXT_COLOR_BLUE = 0xFF1976D2;

    private ImageButton backButton;
    private Spinner yearSpinner;
    private Spinner monthSpinner;
    private ImageButton gridButton;
    private ImageButton uploadButton;
    private LinearLayout checkInOutButton;
    private LinearLayout taDataRowsContainer;
    private TextView totalValue;
    private ListenerRegistration expenditureDocListener;

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
        
        setContentView(R.layout.activity_ta);

        initializeViews();
        setupYearSpinner();
        setupMonthSpinner();
        setupClickListeners();
        loadExpenditureFromFirestore();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.back_button);
        yearSpinner = findViewById(R.id.year_spinner);
        monthSpinner = findViewById(R.id.month_spinner);
        gridButton = findViewById(R.id.grid_button);
        uploadButton = findViewById(R.id.upload_button);
        checkInOutButton = findViewById(R.id.check_in_out_button);
        taDataRowsContainer = findViewById(R.id.ta_data_rows_container);
        totalValue = findViewById(R.id.total_value);
    }

    private void setupYearSpinner() {
        String[] years = {"2026", "2025", "2024", "2023", "2022", "2021"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
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
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int i = 0; i < years.length; i++) {
            if (years[i].equals(String.valueOf(currentYear))) {
                yearSpinner.setSelection(i);
                break;
            }
        }
        yearSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                adapter.notifyDataSetChanged();
                loadExpenditureFromFirestore();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    /** Maps spinner index to Firestore month abbreviation (Jan, Feb, Mar, ...). */
    private static final String[] MONTH_TO_ABBR = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private void setupMonthSpinner() {
        String[] months = {"Jan", "Feb", "March", "April", "May", "June",
                "July", "Aug", "Sept", "Oct", "Nov", "Dec"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
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
        monthSpinner.setSelection(Calendar.getInstance().get(Calendar.MONTH));
        monthSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                adapter.notifyDataSetChanged();
                loadExpenditureFromFirestore();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    // Load Expenditure from Firestore (collection: Expenditure, doc ID: year_month_email)
    private void loadExpenditureFromFirestore() {
        if (taDataRowsContainer == null) return;
        taDataRowsContainer.removeAllViews();

        String yearStr = (String) yearSpinner.getSelectedItem();
        int monthPos = monthSpinner.getSelectedItemPosition();
        String monthAbbr = (monthPos >= 0 && monthPos < MONTH_TO_ABBR.length) ? MONTH_TO_ABBR[monthPos] : "Jan";

        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");

        // Fetch by document ID (format: 2026_Mar_taufikali59@gmail.com)
        String docId = yearStr + "_" + monthAbbr + "_" + employeeEmail;
        Log.d(TAG, "Fetching Expenditure docId=" + docId + " email=" + (employeeEmail.isEmpty() ? "(empty)" : employeeEmail));
        if (expenditureDocListener != null) {
            expenditureDocListener.remove();
            expenditureDocListener = null;
        }
        expenditureDocListener = FirebaseFirestore.getInstance().collection("Expenditure")
                .document(docId)
                .addSnapshotListener((doc, listenErr) -> {
                    if (listenErr != null) {
                        Log.e(TAG, "Doc listener failed", listenErr);
                    }
                    List<Object[]> rowData = new ArrayList<>();
                    Set<String> skipKeys = new HashSet<>(Arrays.asList(
                            "employeeId", "employeeName", "employeeRole", "month", "monthIndex", "salary", "totals", "year", "updatedAt"));
                    if (doc != null && doc.exists()) {
                        Map<String, Object> data = doc.getData();
                        Log.d(TAG, "Doc exists, keys=" + (data != null ? data.keySet().toString() : "null"));
                        if (data != null) {
                            extractDailyRows(data, skipKeys, rowData);
                        }
                        if (rowData.isEmpty()) {
                            Object totalsObj = data != null ? data.get("totals") : null;
                            if (totalsObj instanceof Map) {
                                Map<?, ?> tm = (Map<?, ?>) totalsObj;
                                int ta = ExpenditureTaUtils.getTaFromMap(tm);
                                if (ta != 0) {
                                    int kind = ExpenditureTaUtils.resolveTaColorKind(tm);
                                    rowData.add(new Object[]{formatMonthYearForDisplay(monthAbbr, yearStr), "-", "-", ta, kind});
                                    Log.d(TAG, "Using totals TA=" + ta + " (no daily rows)");
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "Doc not found by ID, trying query fallback");
                        FirebaseFirestore.getInstance().collection("Expenditure")
                                .whereEqualTo("year", yearStr)
                                .whereEqualTo("month", monthAbbr)
                                .get()
                                .addOnSuccessListener(qSnapshot -> {
                                    if (qSnapshot != null && !qSnapshot.isEmpty()) {
                                        for (DocumentSnapshot qDoc : qSnapshot.getDocuments()) {
                                            if (employeeEmail != null && !employeeEmail.isEmpty()
                                                    && !qDoc.getId().contains(employeeEmail)) continue;
                                            Map<String, Object> qData = qDoc.getData();
                                            if (qData != null) extractDailyRows(qData, skipKeys, rowData);
                                        }
                                        if (rowData.isEmpty() && employeeEmail != null && !employeeEmail.isEmpty()) {
                                            for (DocumentSnapshot qDoc : qSnapshot.getDocuments()) {
                                                if (!qDoc.getId().contains(employeeEmail)) continue;
                                                Object to = qDoc.get("totals");
                                                if (to instanceof Map) {
                                                    Map<?, ?> tm = (Map<?, ?>) to;
                                                    int ta = ExpenditureTaUtils.getTaFromMap(tm);
                                                    if (ta != 0) {
                                                        int kind = ExpenditureTaUtils.resolveTaColorKind(tm);
                                                        rowData.add(new Object[]{formatMonthYearForDisplay(monthAbbr, yearStr), "-", "-", ta, kind});
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    processAndShowRows(rowData);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Query fallback failed", e);
                                    processAndShowRows(rowData);
                                });
                        return;
                    }
                    processAndShowRows(rowData);
                });
    }

    @Override
    protected void onDestroy() {
        if (expenditureDocListener != null) {
            expenditureDocListener.remove();
            expenditureDocListener = null;
        }
        super.onDestroy();
    }

    /** Footer total: sum of TA amounts on rows shown in blue only (kind == 2). Red rows are excluded. */
    private static int sumBlueTaOnly(List<Object[]> rowData) {
        int sum = 0;
        for (Object[] r : rowData) {
            int kind = 0;
            if (r.length > 4 && r[4] instanceof Number) {
                kind = ((Number) r[4]).intValue();
            }
            if (kind != 2) continue;
            if (r[3] instanceof Number) {
                sum += ((Number) r[3]).intValue();
            }
        }
        return sum;
    }

    private void processAndShowRows(List<Object[]> rowData) {
        Collections.sort(rowData, (a, b) -> {
            String da = (String) a[0];
            String db = (String) b[0];
            if (da == null) da = "";
            if (db == null) db = "";
            return da.compareTo(db);
        });
        final int blueTaTotal = sumBlueTaOnly(rowData);
        List<View> rows = new ArrayList<>();
        for (int i = 0; i < rowData.size(); i++) {
            Object[] r = rowData.get(i);
            int taVal = r[3] instanceof Number ? ((Number) r[3]).intValue() : 0;
            int taKind = 0;
            if (r.length > 4 && r[4] instanceof Number) {
                taKind = ((Number) r[4]).intValue();
            }
            rows.add(createDataRow((String) r[0], (String) r[1], (String) r[2], taVal, i % 2 == 0, taKind));
        }
        runOnUiThread(() -> {
            taDataRowsContainer.removeAllViews();
            for (View v : rows) taDataRowsContainer.addView(v);
            if (totalValue != null) {
                totalValue.setText(String.format(Locale.getDefault(), "₹%d", blueTaTotal));
            }
            if (rows.isEmpty()) {
                taDataRowsContainer.addView(createDataRow("No data", "-", "-", 0, true, 0));
            }
        });
    }

    /** taColorKind: 0 = default gray, 1 = red, 2 = blue (matches Expenditure / admin). */
    private LinearLayout createDataRow(String date, String distributor, String bitName, int ta, boolean lightBackground, int taColorKind) {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        row.setOrientation(LinearLayout.HORIZONTAL);
        int padding = (int) (12 * getResources().getDisplayMetrics().density);
        row.setPadding(padding, padding, padding, padding);
        row.setBackgroundResource(lightBackground ? R.drawable.table_row_light : R.drawable.table_row_dark);
        addCell(row, date, 1f);
        addCell(row, distributor, 1.2f);
        addCell(row, bitName, 1f);
        addTaAmountCell(row, String.valueOf(ta), 0.8f, taColorKind);
        return row;
    }

    private void addCell(LinearLayout row, String text, float weight) {
        TextView cell = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        cell.setLayoutParams(params);
        cell.setText(text != null ? text : "");
        cell.setGravity(android.view.Gravity.CENTER);
        cell.setTextSize(13);
        cell.setTextColor(0xFF424242);
        row.addView(cell);
    }

    /** TA column only — red / blue per Firestore row metadata. */
    private void addTaAmountCell(LinearLayout row, String text, float weight, int taColorKind) {
        TextView cell = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        cell.setLayoutParams(params);
        cell.setText(text != null ? text : "");
        cell.setGravity(android.view.Gravity.CENTER);
        cell.setTextSize(13);
        int color = 0xFF424242;
        if (taColorKind == 1) {
            color = 0xFFD32F2F;
        } else if (taColorKind == 2) {
            color = ContextCompat.getColor(this, R.color.dark_blue);
        }
        cell.setTextColor(color);
        row.addView(cell);
    }

    /** Extract daily rows from document data. Handles top-level keys, nested maps, and arrays. */
    private void extractDailyRows(Map<String, Object> data, Set<String> skipKeys,
                                  List<Object[]> rowData) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (skipKeys.contains(entry.getKey())) continue;
            Object val = entry.getValue();
            if (val instanceof Map) {
                Map<?, ?> inner = (Map<?, ?>) val;
                addDayEntryIfValid(inner, rowData);
            } else if (val instanceof List) {
                for (Object item : (List<?>) val) {
                    if (item instanceof Map) addDayEntryIfValid((Map<?, ?>) item, rowData);
                }
            }
        }
    }

    private void addDayEntryIfValid(Map<?, ?> map, List<Object[]> rowData) {
        String dateLabel = getStringFromMap(map, "dateLabel");
        if (dateLabel == null) dateLabel = getStringFromMap(map, "date");
        String distName = getStringFromMap(map, "distName");
        if (distName == null) distName = getStringFromMap(map, "distributor");
        String bitName = getStringFromMap(map, "bitName");
        if (bitName == null) bitName = getStringFromMap(map, "bit");
        if (bitName == null) bitName = getStringFromMap(map, "beatName");
        int ta = ExpenditureTaUtils.getTaFromMap(map);
        if (dateLabel != null || distName != null || bitName != null || ta > 0) {
            if (dateLabel == null) dateLabel = "-";
            else dateLabel = formatDateForDisplay(dateLabel);
            if (distName == null) distName = "-";
            if (bitName == null) bitName = "-";
            int colorKind = ExpenditureTaUtils.resolveTaColorKind(map);
            rowData.add(new Object[]{dateLabel, distName, bitName, ta, colorKind});
        }
    }

    /** Format month+year to "Mar'26" style. */
    private static String formatMonthYearForDisplay(String monthAbbr, String yearStr) {
        if (yearStr != null && yearStr.length() >= 2) {
            return monthAbbr + "'" + yearStr.substring(yearStr.length() - 2);
        }
        return monthAbbr + " " + yearStr;
    }

    /** Format date to "15 Mar'26" style. */
    private static String formatDateForDisplay(String dateLabel) {
        if (dateLabel == null || dateLabel.isEmpty()) return "-";
        try {
            SimpleDateFormat in = new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH);
            java.util.Date d = in.parse(dateLabel.trim());
            if (d != null) {
                return new SimpleDateFormat("d MMM''yy", Locale.ENGLISH).format(d);
            }
        } catch (Exception ignored) {}
        try {
            SimpleDateFormat in = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
            java.util.Date d = in.parse(dateLabel.trim());
            if (d != null) {
                return new SimpleDateFormat("d MMM''yy", Locale.ENGLISH).format(d);
            }
        } catch (Exception ignored) {}
        return dateLabel;
    }

    private static String getStringFromMap(Map<?, ?> map, String key) {
        Object o = map.get(key);
        return o != null ? String.valueOf(o).trim() : null;
    }

    private static int getIntFromMap(Map<?, ?> map, String key) {
        Object o = map.get(key);
        if (o instanceof Number) return ((Number) o).intValue();
        if (o != null) {
            try {
                return Integer.parseInt(String.valueOf(o).trim());
            } catch (NumberFormatException ignored) {}
        }
        return 0;
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
                    Intent intent = new Intent(TAActivity.this, WeeklyPlannerActivity.class);
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
                    Intent intent = new Intent(TAActivity.this, CheckInOutActivity.class);
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

        // Setup distributer spinner with custom layouts
        String[] distributers = {"Name1", "Name 2", "Name 3", "Name 4"};
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                R.layout.dialog_spinner_item,
                distributers
        ) {
            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view;
                
                // Highlight selected item with #34404F background and white text
                if (position == distributerSpinner.getSelectedItemPosition()) {
                    textView.setBackgroundResource(R.drawable.dropdown_item_selected_border);
                    textView.setTextColor(android.graphics.Color.WHITE);
                } else {
                    textView.setBackgroundResource(R.drawable.dropdown_item_border);
                    textView.setTextColor(android.graphics.Color.parseColor("#666666"));
                }
                
                // Ensure left alignment
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
        adapter.setDropDownViewResource(R.layout.dialog_spinner_dropdown_item);
        distributerSpinner.setAdapter(adapter);
        
        // Handle dropdown expansion within dialog with animation
        distributerSpinner.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    // Show dropdown expansion space with animation
                    dropdownContainer.setVisibility(View.VISIBLE);
                    dropdownContainer.removeAllViews();
                    
                    // Calculate height for 4 items
                    int itemHeight = (int) (50 * getResources().getDisplayMetrics().density);
                    android.view.ViewGroup.LayoutParams params = dropdownContainer.getLayoutParams();
                    params.height = itemHeight * 4;
                    dropdownContainer.setLayoutParams(params);
                    
                    // Animate expansion
                    Animation expandAnim = AnimationUtils.loadAnimation(TAActivity.this, R.anim.dropdown_expand);
                    dropdownContainer.startAnimation(expandAnim);
                }
                return false;
            }
        });
        
        // Hide dropdown space when item selected with animation
        distributerSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                // Force refresh spinner background to maintain border
                distributerSpinner.post(new Runnable() {
                    @Override
                    public void run() {
                        distributerSpinner.setBackgroundResource(R.drawable.dialog_spinner_background);
                        distributerSpinner.invalidate();
                        distributerSpinner.refreshDrawableState();
                    }
                });
                
                dropdownContainer.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Animate collapse
                        Animation collapseAnim = AnimationUtils.loadAnimation(TAActivity.this, R.anim.dropdown_collapse);
                        collapseAnim.setAnimationListener(new Animation.AnimationListener() {
                            @Override
                            public void onAnimationStart(Animation animation) {}

                            @Override
                            public void onAnimationEnd(Animation animation) {
                                dropdownContainer.setVisibility(View.GONE);
                                // Ensure border stays after animation
                                distributerSpinner.setBackgroundResource(R.drawable.dialog_spinner_background);
                                distributerSpinner.invalidate();
                                distributerSpinner.refreshDrawableState();
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
                Animation collapseAnim = AnimationUtils.loadAnimation(TAActivity.this, R.anim.dropdown_collapse);
                collapseAnim.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {}

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        dropdownContainer.setVisibility(View.GONE);
                        // Ensure border stays
                        distributerSpinner.setBackgroundResource(R.drawable.dialog_spinner_background);
                        distributerSpinner.invalidate();
                        distributerSpinner.refreshDrawableState();
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
                    popupWindow.setBackgroundDrawable(getResources().getDrawable(R.drawable.dialog_dropdown_background));
                    popupWindow.setHeight(android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                    
                    // Set popup animations
                    popupWindow.setAnimationStyle(R.style.SpinnerDropdownAnimation);
                    
                    // Position dropdown directly below spinner
                    popupWindow.setVerticalOffset((int) (50 * getResources().getDisplayMetrics().density));
                    popupWindow.setHorizontalOffset(0);
                    
                    popupWindow.setOnDismissListener(new android.widget.PopupWindow.OnDismissListener() {
                        @Override
                        public void onDismiss() {
                            if (dropdownContainer.getVisibility() == View.VISIBLE) {
                                Animation collapseAnim = AnimationUtils.loadAnimation(TAActivity.this, R.anim.dropdown_collapse);
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
                Toast.makeText(TAActivity.this, 
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

