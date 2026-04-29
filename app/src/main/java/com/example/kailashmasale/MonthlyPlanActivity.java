package com.example.kailashmasale;

import android.app.Dialog;
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
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.FirebaseFirestore;

public class MonthlyPlanActivity extends AppCompatActivity {

    /** When launched from Team Attendance "View", show this employee's plan (read-only). */
    public static final String EXTRA_VIEW_EMPLOYEE_ID = "view_employee_id";
    public static final String EXTRA_VIEW_EMPLOYEE_NAME = "view_employee_name";

    private static final String[] MONTHS = {"Jan", "Feb", "March", "April", "May", "June",
            "July", "Aug", "Sept", "Oct", "Nov", "Dec"};
    /** Short month names for monthlyData doc id (e.g. 2026_Mar) */
    private static final String[] MONTH_SHORT = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private String[] yearOptions;
    /** When set, we show this employee's plan (from Team Attendance View); edit/FAB hidden. */
    private String viewEmployeeId;
    private String viewEmployeeName;

    private View backButtonContainer;
    private LinearLayout addNewMonthButton;
    private Spinner yearSpinner;
    private Spinner monthSpinner;
    private LinearLayout monthlyPlanRowsContainer;
    private TextView totalWdView;
    private TextView totalLmaView;
    private TextView totalTargetView;
    private TextView totalIncentiveView;
    private TextView lmaHeaderTextView;
    private TextView incentiveHeaderTextView;
    private TextView planStatusText;
    private ImageButton editPlanIcon;
    private ImageButton fab;
    private ImageButton gridButton;
    private ImageButton uploadButton;
    private LinearLayout checkInOutButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // White status bar to match white header (Productivity-style)
        if (getWindow() != null) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));
            getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        }
        
        setContentView(R.layout.activity_monthly_plan);

        Intent intent = getIntent();
        if (intent != null) {
            viewEmployeeId = intent.getStringExtra(EXTRA_VIEW_EMPLOYEE_ID);
            viewEmployeeName = intent.getStringExtra(EXTRA_VIEW_EMPLOYEE_NAME);
            if (viewEmployeeId != null) viewEmployeeId = viewEmployeeId.trim();
            if (viewEmployeeId != null && viewEmployeeId.isEmpty()) viewEmployeeId = null;
        }

        initializeViews();
        setupSpinners();
        setupClickListeners();
        if (viewEmployeeId != null) {
            if (editPlanIcon != null) editPlanIcon.setVisibility(View.GONE);
            if (fab != null) fab.setVisibility(View.GONE);
            if (addNewMonthButton != null) addNewMonthButton.setVisibility(View.GONE);
            if (lmaHeaderTextView != null) {
                lmaHeaderTextView.setText("Days\nWkd");
                lmaHeaderTextView.setGravity(android.view.Gravity.CENTER);
                lmaHeaderTextView.setSingleLine(false);
                lmaHeaderTextView.setMaxLines(2);
            }
            if (incentiveHeaderTextView != null) {
                incentiveHeaderTextView.setText("TG\nAcvd");
                incentiveHeaderTextView.setGravity(android.view.Gravity.CENTER);
                incentiveHeaderTextView.setSingleLine(false);
                incentiveHeaderTextView.setMaxLines(2);
            }
        }
        loadPlanFromFirebase();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (monthlyPlanRowsContainer != null) {
            loadPlanFromFirebase();
        }
    }

    private void initializeViews() {
        backButtonContainer = findViewById(R.id.back_button_container);
        addNewMonthButton = findViewById(R.id.add_new_month_button);
        yearSpinner = findViewById(R.id.year_spinner);
        monthSpinner = findViewById(R.id.month_spinner);
        monthlyPlanRowsContainer = findViewById(R.id.monthly_plan_rows_container);
        totalWdView = findViewById(R.id.total_wd);
        totalLmaView = findViewById(R.id.total_lma);
        totalTargetView = findViewById(R.id.total_target);
        totalIncentiveView = findViewById(R.id.total_incentive);
        lmaHeaderTextView = findViewById(R.id.lma_header_text);
        incentiveHeaderTextView = findViewById(R.id.incentive_header_text);
        planStatusText = findViewById(R.id.plan_status_text);
        editPlanIcon = findViewById(R.id.edit_plan_icon);
        fab = null;
        gridButton = findViewById(R.id.grid_button);
        uploadButton = findViewById(R.id.upload_button);
        checkInOutButton = findViewById(R.id.check_in_out_button);
    }

    private void setupSpinners() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        yearOptions = new String[5];
        for (int i = 0; i < yearOptions.length; i++) {
            yearOptions[i] = String.valueOf(currentYear - i);
        }
        ArrayAdapter<String> yearAdapter = new ArrayAdapter<String>(
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
        yearAdapter.setDropDownViewResource(R.layout.year_spinner_dropdown_item);
        yearSpinner.setAdapter(yearAdapter);
        yearSpinner.setSelection(0);

        ArrayAdapter<String> monthAdapter = new ArrayAdapter<String>(
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
        monthAdapter.setDropDownViewResource(R.layout.year_spinner_dropdown_item);
        monthSpinner.setAdapter(monthAdapter);
        monthSpinner.setSelection(Calendar.getInstance().get(Calendar.MONTH));
    }

    private void setupClickListeners() {
        backButtonContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        if (addNewMonthButton != null) {
            addNewMonthButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAddMonthDialog();
                }
            });
        }

        if (editPlanIcon != null) {
            editPlanIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MonthlyPlanActivity.this, MonthlyPlanEditActivity.class);
                    intent.putExtra(MonthlyPlanEditActivity.EXTRA_YEAR, yearOptions != null && yearSpinner.getSelectedItemPosition() >= 0 && yearSpinner.getSelectedItemPosition() < yearOptions.length ? yearOptions[yearSpinner.getSelectedItemPosition()] : String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));
                    intent.putExtra(MonthlyPlanEditActivity.EXTRA_MONTH_INDEX, monthSpinner.getSelectedItemPosition());
                    startActivity(intent);
                }
            });
        }
        if (yearSpinner != null) {
            yearSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    loadPlanFromFirebase();
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }
        if (monthSpinner != null) {
            monthSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    loadPlanFromFirebase();
                }
                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }

        if (gridButton != null) {
            gridButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MonthlyPlanActivity.this, WeeklyPlannerActivity.class);
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
                    Intent intent = new Intent(MonthlyPlanActivity.this, CheckInOutActivity.class);
                    startActivity(intent);
                }
            });
        }
    }

    private void showAddMonthDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_month);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.6f);
        }

        Spinner dialogYear = dialog.findViewById(R.id.dialog_year_spinner);
        Spinner dialogMonth = dialog.findViewById(R.id.dialog_month_spinner);
        TextView cancelBtn = dialog.findViewById(R.id.dialog_cancel);
        TextView confirmBtn = dialog.findViewById(R.id.dialog_confirm);

        String[] dialogYears = yearOptions != null ? yearOptions : new String[]{String.valueOf(Calendar.getInstance().get(Calendar.YEAR))};
        ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(this, R.layout.year_spinner_item, dialogYears);
        yearAdapter.setDropDownViewResource(R.layout.year_spinner_dropdown_item);
        dialogYear.setAdapter(yearAdapter);
        dialogYear.setSelection(0);

        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(this, R.layout.year_spinner_item, MONTHS);
        monthAdapter.setDropDownViewResource(R.layout.year_spinner_dropdown_item);
        dialogMonth.setAdapter(monthAdapter);
        dialogMonth.setSelection(Calendar.getInstance().get(Calendar.MONTH));

        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        confirmBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int selectedYearPos = dialogYear.getSelectedItemPosition();
                int selectedMonthPos = dialogMonth.getSelectedItemPosition();
                dialog.dismiss();
                // Open the same Edit page as when user taps Edit icon, with selected year/month
                Intent intent = new Intent(MonthlyPlanActivity.this, MonthlyPlanEditActivity.class);
                intent.putExtra(MonthlyPlanEditActivity.EXTRA_YEAR, (yearOptions != null && selectedYearPos >= 0 && selectedYearPos < yearOptions.length) ? yearOptions[selectedYearPos] : String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));
                intent.putExtra(MonthlyPlanEditActivity.EXTRA_MONTH_INDEX, selectedMonthPos);
                startActivity(intent);
            }
        });

        dialog.show();
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
                    Animation expandAnim = AnimationUtils.loadAnimation(MonthlyPlanActivity.this, R.anim.dropdown_expand);
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
                        Animation collapseAnim = AnimationUtils.loadAnimation(MonthlyPlanActivity.this, R.anim.dropdown_collapse);
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
                Animation collapseAnim = AnimationUtils.loadAnimation(MonthlyPlanActivity.this, R.anim.dropdown_collapse);
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
                                Animation collapseAnim = AnimationUtils.loadAnimation(MonthlyPlanActivity.this, R.anim.dropdown_collapse);
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
                Toast.makeText(MonthlyPlanActivity.this, 
                    "Uploading stock sheet for: " + selectedDistributer, 
                    Toast.LENGTH_SHORT).show();
                // TODO: Implement file picker and upload logic
                dialog.dismiss();
            }
        });

        // Show dialog
        dialog.show();
    }

    private void loadPlanFromFirebase() {
        if (monthlyPlanRowsContainer == null) return;
        monthlyPlanRowsContainer.removeAllViews();

        final String employeeIdToLoad;
        if (viewEmployeeId != null && !viewEmployeeId.isEmpty()) {
            employeeIdToLoad = viewEmployeeId;
        } else {
            android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            String employeeEmail = prefs.getString("logged_in_employee_email", null);
            String employeeId = prefs.getString("logged_in_employee_id", null);
            if (employeeEmail == null || employeeEmail.isEmpty()) {
                if (planStatusText != null) planStatusText.setText("—");
                addPlanMessageRow("Please log in.");
                return;
            }
            final int yearPos = yearSpinner != null ? yearSpinner.getSelectedItemPosition() : 0;
            final int monthPos = monthSpinner != null ? monthSpinner.getSelectedItemPosition() : 0;
            if (yearOptions == null || yearPos < 0 || yearPos >= yearOptions.length || monthPos < 0 || monthPos >= MONTHS.length) {
                if (planStatusText != null) planStatusText.setText("—");
                addPlanMessageRow("Select year and month.");
                return;
            }
            final String year = yearOptions[yearPos];
            final int monthNum = monthPos + 1;
            if (employeeId == null || employeeId.isEmpty()) {
                FirebaseFirestore.getInstance().collection("employees")
                        .whereEqualTo("email", employeeEmail)
                        .limit(1)
                        .get()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                                String eid = task.getResult().getDocuments().get(0).getId();
                                loadMonthlyDataDoc(eid, year, monthNum);
                            } else {
                                runOnUiThread(() -> addPlanMessageRow("Could not load your data."));
                            }
                        });
                return;
            }
            employeeIdToLoad = employeeId;
        }

        final int yearPos = yearSpinner != null ? yearSpinner.getSelectedItemPosition() : 0;
        final int monthPos = monthSpinner != null ? monthSpinner.getSelectedItemPosition() : 0;
        if (yearOptions == null || yearPos < 0 || yearPos >= yearOptions.length || monthPos < 0 || monthPos >= MONTHS.length) {
            if (planStatusText != null) planStatusText.setText("—");
            addPlanMessageRow("Select year and month.");
            return;
        }
        final String year = yearOptions[yearPos];
        final int monthNum = monthPos + 1;
        loadMonthlyDataDoc(employeeIdToLoad, year, monthNum);
    }

    private void loadMonthlyDataDoc(String employeeId, String year, int monthNum) {
        String monthlyDataDocId = employeeId + "_" + year + "_" + (monthNum >= 1 && monthNum <= 12 ? MONTH_SHORT[monthNum - 1] : "Jan");
        String monthlyDataDocIdForData = employeeId + "_" + year + "_" + monthNum;

        // Fetch monthlyData first (for approval status + distributor details)
        FirebaseFirestore.getInstance().collection("monthlyData").document(monthlyDataDocId)
                .get()
                .addOnSuccessListener(monthDoc -> {
                    updatePlanApprovalStatus(monthDoc);
                    Map<String, String> wdFromMonthlyData = new HashMap<>();
                    Map<String, String> incFromMonthlyData = new HashMap<>();
                    if (monthDoc != null && monthDoc.exists()) {
                        Object dd = monthDoc.get("distributorDetails");
                        if (dd instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> distributorDetails = (Map<String, Object>) dd;
                            for (Map.Entry<String, Object> e : distributorDetails.entrySet()) {
                                String distId = e.getKey();
                                Object details = e.getValue();
                                if (details instanceof Map) {
                                    Map<?, ?> d = (Map<?, ?>) details;
                                    Object wd = d.get("workingDays");
                                    Object inc = d.get("incentive");
                                    wdFromMonthlyData.put(distId, wd != null ? wd.toString() : "");
                                    incFromMonthlyData.put(distId, inc != null ? inc.toString() : "");
                                }
                            }
                        }
                    }
                    final Map<String, String> finalWd = wdFromMonthlyData;
                    final Map<String, String> finalInc = incFromMonthlyData;
                    FirebaseFirestore.getInstance().collection("monthly_data").document(monthlyDataDocIdForData)
                            .get()
                            .addOnSuccessListener(doc -> {
                                if (doc != null && doc.exists()) {
                                    List<Map<String, Object>> rows = (List<Map<String, Object>>) doc.get("rows");
                                    if (rows != null && !rows.isEmpty()) {
                                        runOnUiThread(() -> populatePlanRows(rows, finalWd, finalInc));
                                    } else {
                                        runOnUiThread(() -> addPlanMessageRow("No plan data for this month."));
                                    }
                                } else {
                                    runOnUiThread(() -> addPlanMessageRow("No plan submitted for " + MONTHS[monthNum - 1] + " " + year + "."));
                                }
                            })
                            .addOnFailureListener(e -> runOnUiThread(() -> addPlanMessageRow("Failed to load plan.")));
                })
                .addOnFailureListener(e -> {
                    runOnUiThread(() -> {
                        if (planStatusText != null) planStatusText.setText("Pending approval");
                    });
                    FirebaseFirestore.getInstance().collection("monthly_data").document(monthlyDataDocIdForData)
                            .get()
                            .addOnSuccessListener(doc -> {
                                if (doc != null && doc.exists()) {
                                    List<Map<String, Object>> rows = (List<Map<String, Object>>) doc.get("rows");
                                    if (rows != null && !rows.isEmpty()) {
                                        runOnUiThread(() -> populatePlanRows(rows, new HashMap<>(), new HashMap<>()));
                                    } else {
                                        runOnUiThread(() -> addPlanMessageRow("No plan data for this month."));
                                    }
                                } else {
                                    runOnUiThread(() -> addPlanMessageRow("No plan submitted for " + MONTHS[monthNum - 1] + " " + year + "."));
                                }
                            })
                            .addOnFailureListener(e2 -> runOnUiThread(() -> addPlanMessageRow("Failed to load plan.")));
                });
    }

    private void updatePlanApprovalStatus(com.google.firebase.firestore.DocumentSnapshot monthDoc) {
        if (planStatusText == null) return;
        runOnUiThread(() -> {
            if (monthDoc == null || !monthDoc.exists()) {
                planStatusText.setText("Pending approval");
                planStatusText.setTextColor(0xFF555555);
                return;
            }
            Object approvedAt = monthDoc.get("approvedAt");
            if (approvedAt != null && approvedAt instanceof com.google.firebase.Timestamp) {
                planStatusText.setText("Approved");
                planStatusText.setTextColor(0xFF2E7D32);
            } else {
                planStatusText.setText("Pending approval");
                planStatusText.setTextColor(0xFF555555);
            }
        });
    }

    private void populatePlanRows(List<Map<String, Object>> rows,
                                  Map<String, String> workingDaysFromMonthlyData,
                                  Map<String, String> incentiveFromMonthlyData) {
        if (monthlyPlanRowsContainer == null) return;
        monthlyPlanRowsContainer.removeAllViews();
        if (workingDaysFromMonthlyData == null) workingDaysFromMonthlyData = new HashMap<>();
        if (incentiveFromMonthlyData == null) incentiveFromMonthlyData = new HashMap<>();
        LayoutInflater inflater = LayoutInflater.from(this);
        double sumWd = 0, sumLma = 0, sumTarget = 0, sumIncentive = 0;
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            View rowView = inflater.inflate(R.layout.item_monthly_plan_view_row, monthlyPlanRowsContainer, false);
            if (i % 2 == 1) {
                rowView.setBackgroundColor(0xFFFFFFFF);
            } else {
                rowView.setBackgroundResource(R.drawable.monthly_plan_row_grey);
            }
            TextView dn = rowView.findViewById(R.id.row_dn);
            TextView wd = rowView.findViewById(R.id.row_wd);
            TextView lma = rowView.findViewById(R.id.row_lma);
            TextView target = rowView.findViewById(R.id.row_target);
            TextView incentive = rowView.findViewById(R.id.row_incentive);
            dn.setText(getStringFrom(row, "distributorName", "distributor_name", "name"));
            String distId = row.get("distributorId") != null ? row.get("distributorId").toString() : null;
            String wdVal = (distId != null && workingDaysFromMonthlyData.containsKey(distId)) ? workingDaysFromMonthlyData.get(distId) : null;
            String incVal = (distId != null && incentiveFromMonthlyData.containsKey(distId)) ? incentiveFromMonthlyData.get(distId) : null;
            String wdStr = wdVal != null && !wdVal.isEmpty() ? wdVal : "0";
            String lmaStr = getStringFrom(row, "lmaKg", "lma", "LMA", "0");
            String targetStr = getStringFrom(row, "targetKg", "target", "");
            String incStr = incVal != null && !incVal.isEmpty() ? incVal : "0";
            wd.setText(wdStr);
            lma.setText(lmaStr.isEmpty() ? "0" : lmaStr);
            target.setText(targetStr);
            incentive.setText(incStr);
            sumWd += parseDouble(wdStr, 0);
            sumLma += parseDouble(lmaStr.isEmpty() ? "0" : lmaStr, 0);
            sumTarget += parseDouble(targetStr.isEmpty() ? "0" : targetStr, 0);
            sumIncentive += parseDouble(incStr, 0);
            monthlyPlanRowsContainer.addView(rowView);
        }
        // Update Total row: no rupee symbol, just column totals
        if (totalWdView != null) totalWdView.setText(formatTotal(sumWd));
        if (totalLmaView != null) totalLmaView.setText(formatTotal(sumLma));
        if (totalTargetView != null) totalTargetView.setText(formatTotal(sumTarget));
        if (totalIncentiveView != null) totalIncentiveView.setText(formatTotal(sumIncentive));
    }

    private static double parseDouble(String s, double def) {
        if (s == null || s.trim().isEmpty()) return def;
        try {
            return Double.parseDouble(s.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String formatTotal(double value) {
        if (value == (long) value) return String.valueOf((long) value);
        return String.format(Locale.getDefault(), "%.2f", value);
    }

    private static String getStringFrom(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object v = map.get(key);
            if (v != null) return v.toString().trim();
        }
        return "";
    }

    private void addPlanMessageRow(String message) {
        if (monthlyPlanRowsContainer == null) return;
        resetTotalRow();
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setPadding(32, 24, 32, 24);
        tv.setTextColor(0xFF424242);
        monthlyPlanRowsContainer.addView(tv);
    }

    private void resetTotalRow() {
        if (totalWdView != null) totalWdView.setText("0");
        if (totalLmaView != null) totalLmaView.setText("0");
        if (totalTargetView != null) totalTargetView.setText("0");
        if (totalIncentiveView != null) totalIncentiveView.setText("0");
    }
}

