package com.example.kailashmasale;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DAActivity extends AppCompatActivity {

    private static final String TAG = "DAActivity";

    /** Spinner index → Firestore month in doc id (same as TA screen). */
    private static final String[] MONTH_TO_ABBR = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private ImageButton backButton;
    private Spinner yearSpinner;
    private Spinner monthSpinner;
    private LinearLayout gridButton;
    private LinearLayout uploadButton;
    private LinearLayout checkInOutButton;
    private LinearLayout daDataRowsContainer;
    private TextView totalDaValue;
    private TextView totalNhValue;
    private ListenerRegistration expenditureDocListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getWindow() != null) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));
            getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        }

        setContentView(R.layout.activity_da);

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
        gridButton = findViewById(R.id.grid_button_layout);
        uploadButton = findViewById(R.id.upload_button_layout);
        checkInOutButton = findViewById(R.id.check_in_out_button);
        daDataRowsContainer = findViewById(R.id.da_data_rows_container);
        totalDaValue = findViewById(R.id.total_da_value);
        totalNhValue = findViewById(R.id.total_nh_value);
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
                    textView.setTextColor(0xFF000000);
                } else {
                    textView.setTextColor(0xBF000000);
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
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void setupMonthSpinner() {
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sept", "Oct", "Nov", "Dec"};
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
                    textView.setTextColor(0xFF000000);
                } else {
                    textView.setTextColor(0xBF000000);
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
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void loadExpenditureFromFirestore() {
        if (daDataRowsContainer == null) return;
        daDataRowsContainer.removeAllViews();

        String yearStr = (String) yearSpinner.getSelectedItem();
        int monthPos = monthSpinner.getSelectedItemPosition();
        String monthAbbr = (monthPos >= 0 && monthPos < MONTH_TO_ABBR.length) ? MONTH_TO_ABBR[monthPos] : "Jan";

        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");

        String docId = yearStr + "_" + monthAbbr + "_" + employeeEmail;
        Log.d(TAG, "Expenditure docId=" + docId);

        if (expenditureDocListener != null) {
            expenditureDocListener.remove();
            expenditureDocListener = null;
        }

        expenditureDocListener = FirebaseFirestore.getInstance().collection("Expenditure")
                .document(docId)
                .addSnapshotListener((doc, listenErr) -> {
                    if (listenErr != null) {
                        Log.e(TAG, "Expenditure listener failed", listenErr);
                    }
                    List<Object[]> rowData = new ArrayList<>();
                    Set<String> skipKeys = new HashSet<>(Arrays.asList(
                            "employeeId", "employeeName", "employeeRole", "month", "monthIndex", "salary", "totals", "year", "updatedAt"));
                    if (doc != null && doc.exists()) {
                        Map<String, Object> data = doc.getData();
                        if (data != null) {
                            extractDailyDaRows(data, skipKeys, rowData);
                        }
                        if (rowData.isEmpty() && data != null) {
                            Object totalsObj = data.get("totals");
                            if (totalsObj instanceof Map) {
                                Map<?, ?> tm = (Map<?, ?>) totalsObj;
                                int da = ExpenditureTaUtils.getDaFromMap(tm);
                                int nh = ExpenditureTaUtils.getNhFromMap(tm);
                                if (da != 0 || nh != 0) {
                                    int daK = ExpenditureTaUtils.resolveDaColorKind(tm);
                                    int nhK = ExpenditureTaUtils.resolveNhColorKind(tm);
                                    rowData.add(new Object[]{
                                            formatMonthYearForDisplay(monthAbbr, yearStr),
                                            "-",
                                            da,
                                            nh,
                                            daK,
                                            nhK
                                    });
                                }
                            }
                        }
                    } else {
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
                                            if (qData != null) extractDailyDaRows(qData, skipKeys, rowData);
                                        }
                                        if (rowData.isEmpty() && employeeEmail != null && !employeeEmail.isEmpty()) {
                                            for (DocumentSnapshot qDoc : qSnapshot.getDocuments()) {
                                                if (!qDoc.getId().contains(employeeEmail)) continue;
                                                Object to = qDoc.get("totals");
                                                if (to instanceof Map) {
                                                    Map<?, ?> tm = (Map<?, ?>) to;
                                                    int da = ExpenditureTaUtils.getDaFromMap(tm);
                                                    int nh = ExpenditureTaUtils.getNhFromMap(tm);
                                                    if (da != 0 || nh != 0) {
                                                        rowData.add(new Object[]{
                                                                formatMonthYearForDisplay(monthAbbr, yearStr),
                                                                "-",
                                                                da,
                                                                nh,
                                                                ExpenditureTaUtils.resolveDaColorKind(tm),
                                                                ExpenditureTaUtils.resolveNhColorKind(tm)
                                                        });
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

    /** Row: date, distributor, da, nh, daColorKind, nhColorKind */
    private void extractDailyDaRows(Map<String, Object> data, Set<String> skipKeys, List<Object[]> rowData) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (skipKeys.contains(entry.getKey())) continue;
            Object val = entry.getValue();
            if (val instanceof Map) {
                addDaDayEntryIfValid((Map<?, ?>) val, rowData);
            } else if (val instanceof List) {
                for (Object item : (List<?>) val) {
                    if (item instanceof Map) addDaDayEntryIfValid((Map<?, ?>) item, rowData);
                }
            }
        }
    }

    private void addDaDayEntryIfValid(Map<?, ?> map, List<Object[]> rowData) {
        String dateLabel = getStringFromMap(map, "dateLabel");
        if (dateLabel == null) dateLabel = getStringFromMap(map, "date");
        String distName = getStringFromMap(map, "distName");
        if (distName == null) distName = getStringFromMap(map, "distributor");
        int da = ExpenditureTaUtils.getDaFromMap(map);
        int nh = ExpenditureTaUtils.getNhFromMap(map);
        if (dateLabel != null || distName != null || da != 0 || nh != 0) {
            if (dateLabel == null) dateLabel = "-";
            else dateLabel = formatDateForDisplay(dateLabel);
            if (distName == null) distName = "-";
            int daK = ExpenditureTaUtils.resolveDaColorKind(map);
            int nhK = ExpenditureTaUtils.resolveNhColorKind(map);
            rowData.add(new Object[]{dateLabel, distName, da, nh, daK, nhK});
        }
    }

    private static int sumBlueDaOnly(List<Object[]> rowData) {
        int sum = 0;
        for (Object[] r : rowData) {
            if (r.length < 6) continue;
            int kind = ((Number) r[4]).intValue();
            if (kind == 2) sum += ((Number) r[2]).intValue();
        }
        return sum;
    }

    private static int sumBlueNhOnly(List<Object[]> rowData) {
        int sum = 0;
        for (Object[] r : rowData) {
            if (r.length < 6) continue;
            int kind = ((Number) r[5]).intValue();
            if (kind == 2) sum += ((Number) r[3]).intValue();
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
        final int totalDaBlue = sumBlueDaOnly(rowData);
        final int totalNhBlue = sumBlueNhOnly(rowData);
        List<View> rows = new ArrayList<>();
        for (int i = 0; i < rowData.size(); i++) {
            Object[] r = rowData.get(i);
            int da = r[2] instanceof Number ? ((Number) r[2]).intValue() : 0;
            int nh = r[3] instanceof Number ? ((Number) r[3]).intValue() : 0;
            int daK = r.length > 4 && r[4] instanceof Number ? ((Number) r[4]).intValue() : 0;
            int nhK = r.length > 5 && r[5] instanceof Number ? ((Number) r[5]).intValue() : 0;
            rows.add(createDataRow((String) r[0], (String) r[1], da, nh, daK, nhK, i % 2 == 0));
        }
        runOnUiThread(() -> {
            daDataRowsContainer.removeAllViews();
            for (View v : rows) daDataRowsContainer.addView(v);
            if (totalDaValue != null) {
                totalDaValue.setText(String.format(Locale.getDefault(), "₹%d", totalDaBlue));
            }
            if (totalNhValue != null) {
                totalNhValue.setText(String.format(Locale.getDefault(), "₹%d", totalNhBlue));
            }
            if (rows.isEmpty()) {
                daDataRowsContainer.addView(createDataRow("No data", "-", 0, 0, 0, 0, true));
            }
        });
    }

    private LinearLayout createDataRow(String date, String distributor, int da, int nh,
                                         int daColorKind, int nhColorKind, boolean lightBackground) {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        row.setOrientation(LinearLayout.HORIZONTAL);
        int padding = (int) (12 * getResources().getDisplayMetrics().density);
        row.setPadding(padding, padding, padding, padding);
        row.setBackgroundResource(lightBackground ? R.drawable.table_row_light : R.drawable.table_row_dark);
        addPlainCell(row, date, 1f);
        addPlainCell(row, distributor, 1.2f);
        addAmountCell(row, String.valueOf(da), 1f, daColorKind);
        addAmountCell(row, String.valueOf(nh), 0.8f, nhColorKind);
        return row;
    }

    private void addPlainCell(LinearLayout row, String text, float weight) {
        TextView cell = new TextView(this);
        cell.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight));
        cell.setText(text != null ? text : "");
        cell.setGravity(android.view.Gravity.CENTER);
        cell.setTextSize(13);
        cell.setTextColor(0xFF424242);
        row.addView(cell);
    }

    private void addAmountCell(LinearLayout row, String text, float weight, int colorKind) {
        TextView cell = new TextView(this);
        cell.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight));
        cell.setText(text != null ? text : "");
        cell.setGravity(android.view.Gravity.CENTER);
        cell.setTextSize(13);
        int color = 0xFF424242;
        if (colorKind == 1) {
            color = 0xFFD32F2F;
        } else if (colorKind == 2) {
            color = ContextCompat.getColor(this, R.color.dark_blue);
        }
        cell.setTextColor(color);
        row.addView(cell);
    }

    private static String formatMonthYearForDisplay(String monthAbbr, String yearStr) {
        if (yearStr != null && yearStr.length() >= 2) {
            return monthAbbr + "'" + yearStr.substring(yearStr.length() - 2);
        }
        return monthAbbr + " " + yearStr;
    }

    private static String formatDateForDisplay(String dateLabel) {
        if (dateLabel == null || dateLabel.isEmpty()) return "-";
        try {
            SimpleDateFormat in = new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH);
            java.util.Date d = in.parse(dateLabel.trim());
            if (d != null) {
                return new SimpleDateFormat("d MMM''yy", Locale.ENGLISH).format(d);
            }
        } catch (Exception ignored) { /* ignore */ }
        try {
            SimpleDateFormat in = new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH);
            java.util.Date d = in.parse(dateLabel.trim());
            if (d != null) {
                return new SimpleDateFormat("d MMM''yy", Locale.ENGLISH).format(d);
            }
        } catch (Exception ignored) { /* ignore */ }
        return dateLabel;
    }

    private static String getStringFromMap(Map<?, ?> map, String key) {
        Object o = map.get(key);
        return o != null ? String.valueOf(o).trim() : null;
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
                    Intent intent = new Intent(DAActivity.this, WeeklyPlannerActivity.class);
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
                    Intent intent = new Intent(DAActivity.this, CheckInOutActivity.class);
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
                    Animation expandAnim = AnimationUtils.loadAnimation(DAActivity.this, R.anim.dropdown_expand);
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
                        Animation collapseAnim = AnimationUtils.loadAnimation(DAActivity.this, R.anim.dropdown_collapse);
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
                Animation collapseAnim = AnimationUtils.loadAnimation(DAActivity.this, R.anim.dropdown_collapse);
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
                                Animation collapseAnim = AnimationUtils.loadAnimation(DAActivity.this, R.anim.dropdown_collapse);
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
                Toast.makeText(DAActivity.this, 
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






