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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

public class PerformanceActivity extends AppCompatActivity {

    private static final String[] MONTH_NAMES = {"January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"};
    /** Month abbreviations as stored in performance docs (e.g. "Jan", "Mar") */
    private static final String[] MONTH_ABBR = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private ImageButton backButton;
    private Spinner yearSpinner;
    private ImageButton fab;
    private ImageButton gridButton;
    private ImageButton uploadButton;
    private LinearLayout checkInOutButton;
    private LinearLayout performanceRowsContainer;

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
        
        setContentView(R.layout.activity_performance);

        initializeViews();
        setupSpinners();
        setupClickListeners();
        loadPerformanceData();
    }

    private void initializeViews() {
        yearSpinner = findViewById(R.id.year_spinner);
        fab = findViewById(R.id.fab);
        if (fab != null) fab.setVisibility(View.GONE);
        gridButton = findViewById(R.id.grid_button);
        uploadButton = findViewById(R.id.upload_button);
        checkInOutButton = findViewById(R.id.check_in_out_button);
        performanceRowsContainer = findViewById(R.id.performance_rows_container);
    }

    private void setupSpinners() {
        // Setup Year Spinner - 2026 as default
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
                    textView.setTextColor(0xFF000000);
                } else {
                    textView.setTextColor(0xBF000000);
                }
                return view;
            }
        };
        yearAdapter.setDropDownViewResource(R.layout.year_spinner_dropdown_item);
        yearSpinner.setAdapter(yearAdapter);
        yearSpinner.setSelection(0);
        yearSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                loadPerformanceData();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    /** Load performance collection for selected year; totalAchieved month-wise. Color: 100% green, <100% red, >100% blue. */
    private void loadPerformanceData() {
        if (performanceRowsContainer == null) return;
        performanceRowsContainer.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("Loading...");
        loading.setTextSize(16);
        loading.setTextColor(0xFF616161);
        int pad = (int) (32 * getResources().getDisplayMetrics().density);
        loading.setPadding(pad, pad, pad, pad);
        loading.setGravity(android.view.Gravity.CENTER);
        performanceRowsContainer.addView(loading);

        String employeeId = getSharedPreferences("user_prefs", Context.MODE_PRIVATE).getString("logged_in_employee_id", null);
        if (employeeId == null) employeeId = "";
        Object yearObj = yearSpinner != null && yearSpinner.getSelectedItem() != null ? yearSpinner.getSelectedItem() : "2026";
        String year = yearObj.toString();

        // Performance collection: one doc per month per employee, doc id like 2026_Mar_employeeId
        // Fields: month ("Mar"), year ("2026"), employeeId, totalAchieved (e.g. 11.6)
        FirebaseFirestore.getInstance().collection("performance")
                .whereEqualTo("employeeId", employeeId)
                .whereEqualTo("year", year)
                .get()
                .addOnSuccessListener(snap -> {
                    Map<Integer, Double> monthToPercent = new HashMap<>();
                    if (snap != null) {
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            String monthStr = doc.getString("month");
                            Object achieved = doc.get("totalAchieved");
                            if (monthStr == null) monthStr = "";
                            else monthStr = monthStr.trim();
                            double val = toDouble(achieved);
                            int monthIndex = monthAbbrToIndex(monthStr);
                            if (monthIndex >= 1 && monthIndex <= 12) {
                                monthToPercent.put(monthIndex, val);
                            }
                        }
                    }
                    runOnUiThread(() -> populatePerformanceRows(monthToPercent));
                })
                .addOnFailureListener(e -> runOnUiThread(() -> populatePerformanceRows(new HashMap<>())));
    }

    /** Map month abbr (Jan, Feb, Mar, ...) to 1-12; case-insensitive. */
    private static int monthAbbrToIndex(String abbr) {
        if (abbr == null || abbr.isEmpty()) return -1;
        String a = abbr.length() >= 3 ? abbr.substring(0, 3) : abbr;
        for (int i = 0; i < MONTH_ABBR.length; i++) {
            if (MONTH_ABBR[i].equalsIgnoreCase(a)) return i + 1;
        }
        return -1;
    }

    private static double toDouble(Object o) {
        if (o == null) return Double.NaN;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(o.toString().trim().replace("%", ""));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private void populatePerformanceRows(Map<Integer, Double> monthToPercent) {
        if (performanceRowsContainer == null) return;
        performanceRowsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        boolean alt = false;
        for (int month = 1; month <= 12; month++) {
            View row = inflater.inflate(R.layout.item_performance_row, performanceRowsContainer, false);
            TextView monthTv = row.findViewById(R.id.row_month);
            TextView percentTv = row.findViewById(R.id.row_percent);
            monthTv.setText(MONTH_NAMES[month - 1]);
            if (alt) row.setBackgroundColor(0x6634404F);
            alt = !alt;

            Double d = monthToPercent != null ? monthToPercent.get(month) : null;
            double percent = (d != null && !Double.isNaN(d)) ? d : Double.NaN;
            String percentStr;
            if (Double.isNaN(percent)) {
                percentStr = "-";
                percentTv.setBackgroundResource(R.drawable.performance_badge_red);
            } else {
                percentStr = (percent == (long) percent) ? String.valueOf((long) percent) : String.format("%.1f", percent);
                percentStr = percentStr + "%";
                if (percent < 100.0) {
                    percentTv.setBackgroundResource(R.drawable.performance_badge_red);
                } else if (percent > 100.0) {
                    percentTv.setBackgroundResource(R.drawable.performance_badge_blue);
                } else {
                    percentTv.setBackgroundResource(R.drawable.performance_badge_green);
                }
            }
            percentTv.setText(percentStr);
            performanceRowsContainer.addView(row);
        }
    }

    private void setupClickListeners() {
        View backButtonContainer = findViewById(R.id.back_button_container);
        backButtonContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(PerformanceActivity.this, AddOrdersActivity.class);
                startActivity(intent);
            }
        });

        if (gridButton != null) {
            gridButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(PerformanceActivity.this, WeeklyPlannerActivity.class);
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
                    Intent intent = new Intent(PerformanceActivity.this, CheckInOutActivity.class);
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
                    Animation expandAnim = AnimationUtils.loadAnimation(PerformanceActivity.this, R.anim.dropdown_expand);
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
                        Animation collapseAnim = AnimationUtils.loadAnimation(PerformanceActivity.this, R.anim.dropdown_collapse);
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
                Animation collapseAnim = AnimationUtils.loadAnimation(PerformanceActivity.this, R.anim.dropdown_collapse);
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
                                Animation collapseAnim = AnimationUtils.loadAnimation(PerformanceActivity.this, R.anim.dropdown_collapse);
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
                Toast.makeText(PerformanceActivity.this, 
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


