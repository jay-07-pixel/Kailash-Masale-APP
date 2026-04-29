package com.example.kailashmasale;

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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ProductivityActivity extends AppCompatActivity {

    private ImageButton backButton;
    private Spinner yearSpinner;
    private Spinner monthSpinner;
    private ImageButton gridButton;
    private ImageButton uploadButton;
    private LinearLayout checkInOutButton;
    private LinearLayout emptyRowsContainer;

    private List<String> assignedDistributorNames = new ArrayList<>();

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
        
        setContentView(R.layout.activity_productivity);

        initializeViews();
        setupYearSpinner();
        setupMonthSpinner();
        setupClickListeners();
        loadProductivityData();
        loadAssignedDistributorsAndData();
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
        String[] years = new String[5];
        for (int i = 0; i < 5; i++) {
            years[i] = String.valueOf(currentYear - i);
        }
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
        yearSpinner.setSelection(0); // default: current year
        yearSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                loadProductivityData();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

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
        monthSpinner.setSelection(Calendar.getInstance().get(Calendar.MONTH)); // default: current month
        monthSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                loadProductivityData();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void loadAssignedDistributorsAndData() {
        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", null);
        if (employeeEmail == null || employeeEmail.isEmpty()) {
            assignedDistributorNames.clear();
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("employees")
                .whereEqualTo("email", employeeEmail)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) {
                        assignedDistributorNames.clear();
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
                    if (distributorIds.isEmpty()) {
                        assignedDistributorNames.clear();
                        return;
                    }
                    fetchDistributorNames(db, distributorIds);
                });
    }

    private void fetchDistributorNames(FirebaseFirestore db, List<String> distributorIds) {
        assignedDistributorNames.clear();
        final int[] fetched = {0};
        final int total = distributorIds.size();
        for (String id : distributorIds) {
            db.collection("distributors").document(id).get()
                    .addOnSuccessListener(docSnapshot -> {
                        if (docSnapshot != null && docSnapshot.exists()) {
                            String name = docSnapshot.getString("distributorName");
                            if (name == null || name.isEmpty()) name = docSnapshot.getString("distributor_name");
                            if (name == null || name.isEmpty()) name = docSnapshot.getString("name");
                            if (name == null || name.isEmpty()) name = docSnapshot.getId();
                            assignedDistributorNames.add(name);
                        }
                        fetched[0]++;
                        if (fetched[0] == total) {
                            // Distributor list ready for upload dialog etc.; table already loads in parallel
                        }
                    })
                    .addOnFailureListener(e -> {
                        fetched[0]++;
                    });
        }
    }

    private void loadProductivityData() {
        if (emptyRowsContainer == null) return;
        emptyRowsContainer.removeAllViews();
        addProductivityLoadingRow();

        int monthPos = monthSpinner != null ? monthSpinner.getSelectedItemPosition() : Calendar.getInstance().get(Calendar.MONTH);
        if (monthPos < 0 || monthPos > 11) monthPos = Calendar.getInstance().get(Calendar.MONTH);
        String yearStr = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
        if (yearSpinner != null && yearSpinner.getSelectedItem() != null) {
            yearStr = yearSpinner.getSelectedItem().toString().trim();
        }
        int month1Based = monthPos + 1;

        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");

        final int selectedYearInt = Integer.parseInt(yearStr);
        final int selectedMonth1Based = month1Based;
        SimpleDateFormat displayFmt = new SimpleDateFormat("dd MMM", Locale.getDefault());
        SimpleDateFormat parseFmt = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());

        java.util.function.Predicate<String> isInSelectedMonth = dateStr -> {
            if (dateStr == null || dateStr.isEmpty()) return false;
            try {
                Calendar docCal = Calendar.getInstance(Locale.getDefault());
                docCal.setTime(parseFmt.parse(dateStr));
                return docCal.get(Calendar.YEAR) == selectedYearInt && docCal.get(Calendar.MONTH) + 1 == selectedMonth1Based;
            } catch (Exception e) { return false; }
        };

        final Map<String, int[]> checkOutByDateDist = new HashMap<>();
        final List<String> dateList = new ArrayList<>();
        final List<String> distList = new ArrayList<>();
        final AtomicInteger pending = new AtomicInteger(2);
        final boolean[] hadFailure = new boolean[] { false };

        Runnable tryMerge = () -> {
            if (pending.decrementAndGet() != 0) return;
            runOnUiThread(() -> {
                emptyRowsContainer.removeAllViews();
                if (hadFailure[0]) {
                    emptyRowsContainer.addView(createDataRow("—", "Failed to load data", 0, 0, "—", true));
                    return;
                }
                Map<String, String> dateDistOrder = new TreeMap<>();
                for (int i = 0; i < dateList.size(); i++) {
                    dateDistOrder.put(dateList.get(i) + "_" + distList.get(i) + "_" + i, dateList.get(i) + "|" + distList.get(i));
                }
                if (dateDistOrder.isEmpty()) {
                    emptyRowsContainer.addView(createDataRow("—", "No visits for this month", 0, 0, "—", true));
                    return;
                }
                int idx = 0;
                for (String combined : dateDistOrder.values()) {
                    String[] parts = combined.split("\\|", 2);
                    String dateStr = parts[0];
                    String dist = parts.length > 1 ? parts[1] : "";
                    String dateDisplay;
                    try {
                        dateDisplay = displayFmt.format(parseFmt.parse(dateStr));
                    } catch (Exception e) {
                        dateDisplay = dateStr;
                    }
                    String key = dateStr + "_" + dist;
                    int[] arr = checkOutByDateDist.get(key);
                    int tc = arr != null ? arr[0] : 0;
                    int pc = arr != null ? arr[1] : 0;
                    String percentStr = (tc > 0) ? String.format(Locale.getDefault(), "%.1f%%", 100.0 * pc / tc) : "—";
                    emptyRowsContainer.addView(createDataRow(dateDisplay, dist, tc, pc, percentStr, idx % 2 == 0));
                    idx++;
                }
            });
        };

        // 1) check_outs — in parallel
        FirebaseFirestore.getInstance().collection("check_outs")
                .whereEqualTo("employeeEmail", employeeEmail)
                .get()
                .addOnSuccessListener(checkOutSnapshot -> {
                    if (checkOutSnapshot != null) {
                        for (DocumentSnapshot doc : checkOutSnapshot.getDocuments()) {
                            String dateStr = doc.getString("date");
                            if (!isInSelectedMonth.test(dateStr)) continue;
                            String dist = doc.getString("distributor");
                            if (dist == null) dist = "";
                            String key = dateStr + "_" + dist;
                            int tc = parseInt(doc.get("totalCalls"), 0);
                            int pc = parseInt(doc.get("productiveCalls"), 0);
                            if (!checkOutByDateDist.containsKey(key)) {
                                checkOutByDateDist.put(key, new int[]{0, 0});
                            }
                            int[] arr = checkOutByDateDist.get(key);
                            arr[0] += tc;
                            arr[1] += pc;
                        }
                    }
                    tryMerge.run();
                })
                .addOnFailureListener(e -> { hadFailure[0] = true; tryMerge.run(); });

        // 2) check_ins — in parallel
        FirebaseFirestore.getInstance().collection("check_ins")
                .whereEqualTo("employeeEmail", employeeEmail)
                .get()
                .addOnSuccessListener(checkInSnapshot -> {
                    if (checkInSnapshot != null) {
                        for (DocumentSnapshot doc : checkInSnapshot.getDocuments()) {
                            String dateStr = doc.getString("date");
                            if (!isInSelectedMonth.test(dateStr)) continue;
                            String dist = doc.getString("distributor");
                            if (dist == null) dist = "";
                            dateList.add(dateStr);
                            distList.add(dist);
                        }
                    }
                    tryMerge.run();
                })
                .addOnFailureListener(e -> { hadFailure[0] = true; tryMerge.run(); });
    }

    private void addProductivityLoadingRow() {
        if (emptyRowsContainer == null) return;
        TextView tv = new TextView(this);
        tv.setText("Loading...");
        tv.setPadding((int) (24 * getResources().getDisplayMetrics().density), (int) (20 * getResources().getDisplayMetrics().density), 24, 20);
        tv.setTextColor(0xFF424242);
        tv.setTextSize(14);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && getResources().getFont(R.font.inter_font_family) != null) {
                tv.setTypeface(getResources().getFont(R.font.inter_font_family));
            }
        } catch (Exception ignored) {}
        emptyRowsContainer.addView(tv);
    }

    private static int parseInt(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception e) {
            return def;
        }
    }

    /** Row: Date | Distributor | TC | PC | TC/PC (%) */
    private LinearLayout createDataRow(String dateDisplay, String distributorName, int totalCalls, int productiveCalls, String percentStr, boolean lightBackground) {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        row.setOrientation(LinearLayout.HORIZONTAL);
        int padding = (int) (12 * getResources().getDisplayMetrics().density);
        row.setPadding(padding, padding, padding, padding);
        row.setBackgroundColor(lightBackground ? 0xFFFFFFFF : 0xFFF0F0F0);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && getResources().getFont(R.font.inter_font_family) != null) {
                addCellText(row, 0.8f, dateDisplay != null ? dateDisplay : "—");
                TextView t1 = addCellText(row, 1.5f, distributorName != null ? distributorName : "");
                t1.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
                t1.setTextAlignment(android.view.View.TEXT_ALIGNMENT_TEXT_START);
                addCellText(row, 0.5f, String.valueOf(totalCalls));
                addCellText(row, 0.5f, String.valueOf(productiveCalls));
                addCellText(row, 0.8f, percentStr != null ? percentStr : "—");
        } else {
                addCellText(row, 0.8f, dateDisplay != null ? dateDisplay : "—");
                addCellText(row, 1.5f, distributorName != null ? distributorName : "");
                addCellText(row, 0.5f, String.valueOf(totalCalls));
                addCellText(row, 0.5f, String.valueOf(productiveCalls));
                addCellText(row, 0.8f, percentStr != null ? percentStr : "—");
            }
        } catch (Exception e) {
            addCellText(row, 0.8f, dateDisplay != null ? dateDisplay : "—");
            addCellText(row, 1.5f, distributorName != null ? distributorName : "");
            addCellText(row, 0.5f, String.valueOf(totalCalls));
            addCellText(row, 0.5f, String.valueOf(productiveCalls));
            addCellText(row, 0.8f, percentStr != null ? percentStr : "—");
        }
        return row;
    }

    private TextView addCellText(LinearLayout row, float weight, String text) {
        TextView cell = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        cell.setLayoutParams(params);
        cell.setText(text != null ? text : "");
        cell.setTextColor(0xFF424242);
        cell.setTextSize(13);
        cell.setGravity(android.view.Gravity.CENTER);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && getResources().getFont(R.font.inter_font_family) != null) {
                cell.setTypeface(getResources().getFont(R.font.inter_font_family));
            }
        } catch (Exception ignored) {}
        row.addView(cell);
        return cell;
    }

    private void addDivider(LinearLayout row) {
        View divider = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                (int) (1 * getResources().getDisplayMetrics().density),
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        divider.setLayoutParams(params);
        divider.setBackgroundColor(0xFFD0D0D0); // #D0D0D0
        row.addView(divider);
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
                    Intent intent = new Intent(ProductivityActivity.this, WeeklyPlannerActivity.class);
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
                    Intent intent = new Intent(ProductivityActivity.this, CheckInOutActivity.class);
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
                    Animation expandAnim = AnimationUtils.loadAnimation(ProductivityActivity.this, R.anim.dropdown_expand);
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
                        Animation collapseAnim = AnimationUtils.loadAnimation(ProductivityActivity.this, R.anim.dropdown_collapse);
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
                Animation collapseAnim = AnimationUtils.loadAnimation(ProductivityActivity.this, R.anim.dropdown_collapse);
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
                                Animation collapseAnim = AnimationUtils.loadAnimation(ProductivityActivity.this, R.anim.dropdown_collapse);
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
                Toast.makeText(ProductivityActivity.this, 
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

