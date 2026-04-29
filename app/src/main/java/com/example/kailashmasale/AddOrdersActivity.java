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
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Filter;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class AddOrdersActivity extends AppCompatActivity {

    private ImageButton backButton;
    private Spinner distributorSpinner;
    private EditText orderDateInput;
    private ArrayAdapter<String> distributorAdapter;
    private static final int MAX_ORDERS_PER_DISTRIBUTOR_DATE = 20;
    /** Local draft rows (saved in UI only, not in Firestore) until user taps Submit. */
    private final List<Map<String, String>> localDraftRows = new ArrayList<>();
    private String localDraftDistributor = "";
    private String localDraftDate = "";
    private Button[] addOrderItemButtons = new Button[MAX_ORDERS_PER_DISTRIBUTOR_DATE];
    private LinearLayout checkInOutButton;
    private ImageButton gridButton;
    private ImageButton uploadButton;
    private LinearLayout orderItemsContainer;
    private Button submitButton;
    private View orderLimitReachedContainer;
    private LinearLayout orderPlacedList;
    private TextView ordersLabel;
    private View submittedOrdersContainer;
    private LinearLayout submittedOrdersList;
    /** When > 0, we are editing this submitted order (sub-orders in editingOrderDocs). */
    private long editingOrderNumber = -1;
    private List<DocumentSnapshot> editingOrderDocs = null;
    private ListenerRegistration ordersLiveListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set window background and status bar to white
        if (getWindow() != null) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));
            getWindow().getDecorView().setBackgroundColor(ContextCompat.getColor(this, android.R.color.white));
            // Force light status bar icons
            getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        }
        
        setContentView(R.layout.activity_add_orders);
        
        // Ensure header accounts for status bar
        View header = findViewById(R.id.header);
        if (header != null) {
            ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, systemBars.top, 0, 0);
                return insets;
            });
        }

        initializeViews();
        setupSpinner();
        setupClickListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshOrdersStateForDistributorAndDate();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (ordersLiveListener != null) {
            ordersLiveListener.remove();
            ordersLiveListener = null;
        }
    }

    private void initializeViews() {
        distributorSpinner = findViewById(R.id.distributor_spinner);
        orderDateInput = findViewById(R.id.order_date_input);
        addOrderItemButtons[0] = findViewById(R.id.add_order_item_1);
        addOrderItemButtons[1] = findViewById(R.id.add_order_item_2);
        addOrderItemButtons[2] = findViewById(R.id.add_order_item_3);
        addOrderItemButtons[3] = findViewById(R.id.add_order_item_4);
        orderItemsContainer = findViewById(R.id.order_items_container);
        // Add rows 5-20 programmatically
        float density = getResources().getDisplayMetrics().density;
        for (int i = 4; i < MAX_ORDERS_PER_DISTRIBUTOR_DATE; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            ((LinearLayout.LayoutParams) row.getLayoutParams()).bottomMargin = (int) (16 * density);
            TextView numLabel = new TextView(this);
            numLabel.setText((i + 1) + ".");
            numLabel.setTextColor(0xFF34404F);
            numLabel.setTextSize(16);
            numLabel.setTypeface(null, android.graphics.Typeface.NORMAL);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelLp.setMarginEnd((int) (12 * density));
            numLabel.setLayoutParams(labelLp);
            row.addView(numLabel);
            androidx.appcompat.widget.AppCompatButton plusBtn = new androidx.appcompat.widget.AppCompatButton(this);
            plusBtn.setText("+");
            plusBtn.setTextColor(Color.WHITE);
            plusBtn.setTextSize(20);
            plusBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            plusBtn.setBackgroundResource(R.drawable.order_item_plus_button);
            plusBtn.setBackgroundTintList(null);
            plusBtn.setVisibility(View.GONE);
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams((int) (54 * density), (int) (23 * density));
            row.addView(plusBtn, btnLp);
            addOrderItemButtons[i] = plusBtn;
            if (orderItemsContainer != null) orderItemsContainer.addView(row);
        }
        checkInOutButton = findViewById(R.id.check_in_out_button);
        gridButton = findViewById(R.id.grid_button);
        uploadButton = findViewById(R.id.upload_button);
        submitButton = findViewById(R.id.submit_button);
        orderLimitReachedContainer = findViewById(R.id.order_limit_reached_container);
        orderPlacedList = findViewById(R.id.order_placed_list);
        ordersLabel = findViewById(R.id.orders_label);
        submittedOrdersContainer = findViewById(R.id.submitted_orders_container);
        submittedOrdersList = findViewById(R.id.submitted_orders_list);
    }

    private void setupSpinner() {
        List<String> distributorList = new ArrayList<>();
        distributorList.add("Select distributor");
        distributorAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                distributorList
        ) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view;
                if (position == distributorSpinner.getSelectedItemPosition()) {
                    textView.setTextColor(0xFF000000);
                } else {
                    textView.setTextColor(0xBF000000);
                }
                return view;
            }
        };
        distributorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        distributorSpinner.setAdapter(distributorAdapter);
        
        distributorSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                distributorAdapter.notifyDataSetChanged();
                refreshOrdersStateForDistributorAndDate();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        loadAssignedDistributors();
    }

    private void loadAssignedDistributors() {
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
                    fetchDistributorNames(db, distributorIds);
                });
    }

    private void fetchDistributorNames(FirebaseFirestore db, List<String> distributorIds) {
        final List<String> resultNames = new ArrayList<>();
        final int[] fetched = {0};
        final int total = distributorIds.size();
        final boolean[] hadError = {false};
        for (String id : distributorIds) {
            db.collection("distributors").document(id).get()
                    .addOnSuccessListener(docSnapshot -> {
                        if (docSnapshot != null && docSnapshot.exists()) {
                            String name = docSnapshot.getString("distributorName");
                            if (name == null || name.isEmpty()) name = docSnapshot.getString("distributor_name");
                            if (name == null || name.isEmpty()) name = docSnapshot.getString("name");
                            if (name == null || name.isEmpty()) name = docSnapshot.getId();
                            synchronized (resultNames) { resultNames.add(name); }
                        }
                        synchronized (resultNames) {
                            fetched[0]++;
                            if (fetched[0] == total) {
                                runOnUiThread(() -> {
                                    distributorAdapter.clear();
                                    distributorAdapter.add("Select distributor");
                                    distributorAdapter.addAll(resultNames);
                                    distributorAdapter.notifyDataSetChanged();
                                });
                            }
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (!hadError[0]) {
                            hadError[0] = true;
                            runOnUiThread(() -> Toast.makeText(AddOrdersActivity.this,
                                    "Could not load distributors. Check Firestore rules.", Toast.LENGTH_LONG).show());
                        }
                        synchronized (resultNames) {
                            fetched[0]++;
                            if (fetched[0] == total) {
                                runOnUiThread(() -> {
                                    distributorAdapter.clear();
                                    distributorAdapter.add("Select distributor");
                                    distributorAdapter.addAll(resultNames);
                                    distributorAdapter.notifyDataSetChanged();
                                });
                            }
                        }
                    });
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

        // Default order date to today and open date picker on click
        if (orderDateInput != null) {
            orderDateInput.setText(new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date()));
            orderDateInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    showOrderDatePicker();
                }
            });
        }

        for (int i = 0; i < MAX_ORDERS_PER_DISTRIBUTOR_DATE; i++) {
            final int slot = i + 1;
            final Button btn = addOrderItemButtons[i];
            if (btn != null) {
                btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                        showAddOrderItemDialog(slot, btn, null, null, null);
                }
            });
            }
        }
        
        if (submitButton != null) {
            submitButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (editingOrderNumber >= 0) {
                        handleUpdateOrder();
                    } else {
                        submitOrdersToFirebase();
                    }
                }
            });
        }

        if (checkInOutButton != null) {
            checkInOutButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AddOrdersActivity.this, CheckInOutActivity.class);
                    startActivity(intent);
                }
            });
        }

        if (gridButton != null) {
            gridButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(AddOrdersActivity.this, WeeklyPlannerActivity.class);
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
    }

    private void submitOrdersToFirebase() {
        String distributor = distributorSpinner.getSelectedItem() != null ? distributorSpinner.getSelectedItem().toString() : "";
        if (distributor.isEmpty() || "Select distributor".equals(distributor)) {
            Toast.makeText(this, "Please select a distributor", Toast.LENGTH_SHORT).show();
            return;
        }
        String dateStr = orderDateInput != null ? orderDateInput.getText().toString().trim() : "";
        if (dateStr.isEmpty()) {
            dateStr = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
        }
        final String dateStrFinal = dateStr;
        // If context changed, clear temporary local rows.
        if (!distributor.equals(localDraftDistributor) || !dateStr.equals(localDraftDate)) {
            localDraftRows.clear();
            localDraftDistributor = distributor;
            localDraftDate = dateStr;
        }
        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        if (employeeEmail.isEmpty()) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        // Submit only what user saved locally in this screen (not temporary Firestore drafts).
        List<Map<String, String>> rowsToSubmit = getOrderRowsFromUI(MAX_ORDERS_PER_DISTRIBUTOR_DATE);
        List<Map<String, String>> filteredRows = new ArrayList<>();
        for (Map<String, String> row : rowsToSubmit) {
            String sku = row.get("sku");
            if (sku != null && !sku.trim().isEmpty() && !"+".equals(sku.trim())) {
                filteredRows.add(row);
            }
        }
        if (filteredRows.isEmpty()) {
            Toast.makeText(this, "No sub-orders to submit. Save at least one sub-order first.", Toast.LENGTH_LONG).show();
            return;
        }

        FirebaseFirestore.getInstance().collection("orders")
                .whereEqualTo("employeeEmail", employeeEmail)
                .whereEqualTo("distributor", distributor)
                .whereEqualTo("date", dateStr)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> allDocs = (snapshot != null && !snapshot.isEmpty()) ? snapshot.getDocuments() : new ArrayList<>();
                    long maxOrderNumber = 0;
                    for (DocumentSnapshot d : allDocs) {
                        Object on = d.get("orderNumber");
                        if (on != null && on instanceof Number) {
                            long n = ((Number) on).longValue();
                            if (n > maxOrderNumber) maxOrderNumber = n;
                        }
                    }
                    final long nextOrderNumber = maxOrderNumber + 1;
                    WriteBatch batch = FirebaseFirestore.getInstance().batch();
                    for (int i = 0; i < filteredRows.size(); i++) {
                        Map<String, String> row = filteredRows.get(i);
                        Map<String, Object> orderItem = new HashMap<>();
                        orderItem.put("distributor", distributor);
                        orderItem.put("employeeEmail", employeeEmail);
                        orderItem.put("employeeName", prefs.getString("logged_in_employee_name", ""));
                        orderItem.put("date", dateStrFinal);
                        orderItem.put("subOrderIndex", i + 1);
                        orderItem.put("orderNumber", nextOrderNumber);
                        orderItem.put("submitted", true);
                        orderItem.put("submittedAt", FieldValue.serverTimestamp());
                        orderItem.put("sku", row.get("sku") != null ? row.get("sku") : "");
                        orderItem.put("totalKg", row.get("totalKg") != null ? row.get("totalKg") : "");
                        orderItem.put("scheme", row.get("scheme") != null ? row.get("scheme") : "");
                        orderItem.put("timestamp", FieldValue.serverTimestamp());
                        batch.set(FirebaseFirestore.getInstance().collection("orders").document(), orderItem);
                    }
                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                localDraftRows.clear();
                                localDraftDistributor = "";
                                localDraftDate = "";
                                Toast.makeText(this, "Order " + nextOrderNumber + " submitted successfully (" + filteredRows.size() + " sub-orders)", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Submit failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Could not load orders: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void refreshOrdersStateForDistributorAndDate() {
        editingOrderNumber = -1;
        editingOrderDocs = null;
        updateSubmitButtonForEditMode();
        if (ordersLiveListener != null) {
            ordersLiveListener.remove();
            ordersLiveListener = null;
        }

        String distributor = distributorSpinner.getSelectedItem() != null ? distributorSpinner.getSelectedItem().toString() : "";
        if (distributor.isEmpty() || "Select distributor".equals(distributor)) {
            showSubmittedOrdersSection(new TreeMap<>());
            showAddOrdersUI(0, new ArrayList<>());
            return;
        }
        String dateStr = orderDateInput != null ? orderDateInput.getText().toString().trim() : "";
        if (dateStr.isEmpty()) {
            dateStr = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
        }
        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        if (employeeEmail.isEmpty()) {
            showSubmittedOrdersSection(new TreeMap<>());
            showAddOrdersUI(0, new ArrayList<>());
            return;
        }

        ordersLiveListener = FirebaseFirestore.getInstance().collection("orders")
                .whereEqualTo("employeeEmail", employeeEmail)
                .whereEqualTo("distributor", distributor)
                .whereEqualTo("date", dateStr)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        runOnUiThread(() -> {
                            showSubmittedOrdersSection(new TreeMap<>());
                            showAddOrdersUI(localDraftRows.size(), new ArrayList<>());
                        });
                        return;
                    }
                    List<DocumentSnapshot> allDocs = (snapshot != null && !snapshot.isEmpty()) ? snapshot.getDocuments() : new ArrayList<>();
                    Map<Long, List<DocumentSnapshot>> submittedByOrderNumber = new TreeMap<>();
                    for (DocumentSnapshot d : allDocs) {
                        Object sub = d.get("submitted");
                        if (Boolean.TRUE.equals(sub)) {
                            Object on = d.get("orderNumber");
                            long orderNum = (on != null && on instanceof Number) ? ((Number) on).longValue() : 0;
                            if (orderNum > 0) {
                                if (!submittedByOrderNumber.containsKey(orderNum))
                                    submittedByOrderNumber.put(orderNum, new ArrayList<>());
                                submittedByOrderNumber.get(orderNum).add(d);
                            }
                        }
                    }
                    for (List<DocumentSnapshot> list : submittedByOrderNumber.values()) {
                        Collections.sort(list, new Comparator<DocumentSnapshot>() {
            @Override
                            public int compare(DocumentSnapshot a, DocumentSnapshot b) {
                                return Long.compare(getSubOrderSortIndex(a), getSubOrderSortIndex(b));
                            }
                        });
                    }
                    final Map<Long, List<DocumentSnapshot>> submittedMap = submittedByOrderNumber;
                    runOnUiThread(() -> {
                        showSubmittedOrdersSection(submittedMap);
                        showAddOrdersUI(localDraftRows.size(), new ArrayList<>());
                    });
                });
    }

    private static long getSubOrderSortIndex(DocumentSnapshot doc) {
        Object sub = doc.get("subOrderIndex");
        if (sub != null && sub instanceof Number) return ((Number) sub).longValue();
        Object ord = doc.get("orderNumber");
        if (ord != null && ord instanceof Number) return ((Number) ord).longValue();
        return 0;
    }

    /** Get order status set by admin (e.g. placed, declined). Checks orderStatus, status, adminStatus. */
    private static String getOrderStatusFromDoc(DocumentSnapshot doc) {
        if (doc == null) return null;
        String s = doc.getString("orderStatus");
        if (s != null && !s.isEmpty()) return s.trim();
        s = doc.getString("status");
        if (s != null && !s.isEmpty()) return s.trim();
        s = doc.getString("adminStatus");
        if (s != null && !s.isEmpty()) return s.trim();
        return null;
    }

    /** Get decline reason set by admin. Checks declineReason, decline_reason, reason, adminRemarks. */
    private static String getDeclineReasonFromDoc(DocumentSnapshot doc) {
        if (doc == null) return null;
        String s = doc.getString("declineReason");
        if (s != null && !s.isEmpty()) return s.trim();
        s = doc.getString("decline_reason");
        if (s != null && !s.isEmpty()) return s.trim();
        s = doc.getString("reason");
        if (s != null && !s.isEmpty()) return s.trim();
        s = doc.getString("adminRemarks");
        if (s != null && !s.isEmpty()) return s.trim();
        s = doc.getString("admin_remarks");
        if (s != null && !s.isEmpty()) return s.trim();
        return null;
    }

    private static String formatOrderStatusForDisplay(String status) {
        if (status == null || status.isEmpty()) return "";
        String lower = status.toLowerCase();
        if (lower.equals("placed")) return "Placed";
        if (lower.equals("declined")) return "Declined";
        if (lower.equals("pending")) return "Pending";
        return status.substring(0, 1).toUpperCase() + (status.length() > 1 ? status.substring(1).toLowerCase() : "");
    }

    /** Load submitted order into form for editing; set distributor, date, and rows. */
    private void startEditingOrder(long orderNum, List<DocumentSnapshot> subOrders) {
        if (subOrders == null || subOrders.isEmpty()) return;
        DocumentSnapshot first = subOrders.get(0);
        String distributor = first.getString("distributor");
        String dateStr = first.getString("date");
        if (distributor == null) distributor = "";
        if (dateStr == null) dateStr = "";
        if (orderDateInput != null) orderDateInput.setText(dateStr);
        for (int i = 0; i < distributorAdapter.getCount(); i++) {
            String item = distributorAdapter.getItem(i);
            if (item != null && item.equals(distributor)) {
                distributorSpinner.setSelection(i);
                break;
            }
        }
        editingOrderNumber = orderNum;
        editingOrderDocs = new ArrayList<>(subOrders);
        showAddOrdersUI(subOrders.size(), subOrders);
        if (ordersLabel != null) ordersLabel.setVisibility(View.VISIBLE);
        if (orderItemsContainer != null) orderItemsContainer.setVisibility(View.VISIBLE);
        if (orderLimitReachedContainer != null) orderLimitReachedContainer.setVisibility(View.GONE);
        if (submitButton != null) {
            submitButton.setVisibility(View.VISIBLE);
            submitButton.setText("Update order");
        }
    }

    private void updateSubmitButtonForEditMode() {
        if (submitButton != null) {
            submitButton.setText(editingOrderNumber >= 0 ? "Update order" : "Submit");
        }
    }

    /** Read current order row values from UI (sku, totalKg, scheme) for up to maxRows rows. */
    private List<Map<String, String>> getOrderRowsFromUI(int maxRows) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (orderItemsContainer == null) return rows;
        for (int i = 0; i < orderItemsContainer.getChildCount() && rows.size() < maxRows; i++) {
            View child = orderItemsContainer.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) child;
            if (row.getChildCount() < 4) continue;
            View v1 = row.getChildAt(1);
            View v2 = row.getChildAt(2);
            View v3 = row.getChildAt(3);
            CharSequence skuCs = v1 instanceof TextView ? ((TextView) v1).getText() : (v1 instanceof Button ? ((Button) v1).getText() : "");
            CharSequence kgCs = v2 instanceof TextView ? ((TextView) v2).getText() : (v2 instanceof Button ? ((Button) v2).getText() : "");
            CharSequence schemeCs = v3 instanceof TextView ? ((TextView) v3).getText() : (v3 instanceof Button ? ((Button) v3).getText() : "");
            String sku = skuCs != null ? skuCs.toString().trim() : "";
            String kg = kgCs != null ? kgCs.toString().trim() : "";
            String scheme = schemeCs != null ? schemeCs.toString().trim() : "";
            Map<String, String> map = new HashMap<>();
            map.put("sku", sku);
            map.put("totalKg", kg);
            map.put("scheme", scheme);
            rows.add(map);
        }
        return rows;
    }

    private void handleUpdateOrder() {
        if (editingOrderDocs == null || editingOrderDocs.isEmpty()) {
            editingOrderNumber = -1;
            editingOrderDocs = null;
            updateSubmitButtonForEditMode();
            refreshOrdersStateForDistributorAndDate();
            return;
        }
        List<Map<String, String>> currentRows = getOrderRowsFromUI(editingOrderDocs.size());
        if (currentRows.size() < editingOrderDocs.size()) {
            Toast.makeText(this, "Please fill all order rows.", Toast.LENGTH_SHORT).show();
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();
        for (int i = 0; i < editingOrderDocs.size(); i++) {
            DocumentSnapshot doc = editingOrderDocs.get(i);
            Map<String, String> row = currentRows.get(i);
            batch.update(doc.getReference(),
                    "sku", row.get("sku") != null ? row.get("sku") : "",
                    "totalKg", row.get("totalKg") != null ? row.get("totalKg") : "",
                    "scheme", row.get("scheme") != null ? row.get("scheme") : "",
                    "timestamp", FieldValue.serverTimestamp());
        }
        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Order updated successfully.", Toast.LENGTH_SHORT).show();
                    editingOrderNumber = -1;
                    editingOrderDocs = null;
                    updateSubmitButtonForEditMode();
                    refreshOrdersStateForDistributorAndDate();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void showSubmittedOrdersSection(Map<Long, List<DocumentSnapshot>> submittedByOrderNumber) {
        if (submittedOrdersContainer == null || submittedOrdersList == null) return;
        if (submittedByOrderNumber == null || submittedByOrderNumber.isEmpty()) {
            submittedOrdersContainer.setVisibility(View.GONE);
                    return;
                }
        submittedOrdersContainer.setVisibility(View.VISIBLE);
        submittedOrdersList.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (Map.Entry<Long, List<DocumentSnapshot>> entry : submittedByOrderNumber.entrySet()) {
            long orderNum = entry.getKey();
            List<DocumentSnapshot> subOrders = entry.getValue();
            if (subOrders == null || subOrders.isEmpty()) continue;

            String statusStr = null;
            String declineReason = null;
            for (DocumentSnapshot doc : subOrders) {
                if (statusStr == null || statusStr.isEmpty()) statusStr = getOrderStatusFromDoc(doc);
                if (declineReason == null || declineReason.isEmpty()) declineReason = getDeclineReasonFromDoc(doc);
                if ((statusStr != null && !statusStr.isEmpty()) && (declineReason != null || !"declined".equals(statusStr.toLowerCase()))) break;
            }
            String statusDisplay = formatOrderStatusForDisplay(statusStr);
            boolean isPlaced = statusDisplay.equalsIgnoreCase("Placed");
            boolean isDeclined = statusDisplay.equalsIgnoreCase("Declined");

            LinearLayout orderHeaderRow = new LinearLayout(this);
            orderHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
            orderHeaderRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            orderHeaderRow.setPadding(0, (int) (8 * density), 0, (int) (2 * density));

            TextView orderHeader = new TextView(this);
            orderHeader.setText("Order " + orderNum + " (" + subOrders.size() + " sub-orders)");
            orderHeader.setTextColor(0xFF34404F);
            orderHeader.setTextSize(16);
            orderHeader.setTypeface(null, android.graphics.Typeface.BOLD);
            orderHeader.setPadding(0, 0, (int) (8 * density), 0);
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && getResources().getFont(R.font.inter_font_family) != null) {
                    orderHeader.setTypeface(getResources().getFont(R.font.inter_font_family));
                }
            } catch (Exception ignored) {}
            orderHeaderRow.addView(orderHeader);

            if (!statusDisplay.isEmpty()) {
                TextView statusBadge = new TextView(this);
                statusBadge.setText(statusDisplay);
                statusBadge.setTextColor(0xFFFFFFFF);
                statusBadge.setTextSize(13);
                statusBadge.setTypeface(null, android.graphics.Typeface.BOLD);
                int padH = (int) (10 * density);
                int padV = (int) (4 * density);
                statusBadge.setPadding(padH, padV, padH, padV);
                if (isPlaced) {
                    statusBadge.setBackgroundColor(0xFF4CAF50);
                } else if (isDeclined) {
                    statusBadge.setBackgroundColor(0xFFE53935);
                } else {
                    statusBadge.setBackgroundColor(0xFF757575);
                }
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && getResources().getFont(R.font.inter_font_family) != null) {
                        statusBadge.setTypeface(getResources().getFont(R.font.inter_font_family));
                    }
                } catch (Exception ignored) {}
                orderHeaderRow.addView(statusBadge);
            }
            TextView editButton = new TextView(this);
            editButton.setText("Edit");
            editButton.setTextColor(0xFF1976D2);
            editButton.setTextSize(14);
            editButton.setPadding((int) (12 * density), (int) (4 * density), (int) (12 * density), (int) (4 * density));
            editButton.setClickable(true);
            editButton.setFocusable(true);
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && getResources().getFont(R.font.inter_font_family) != null) {
                    editButton.setTypeface(getResources().getFont(R.font.inter_font_family));
                }
            } catch (Exception ignored) {}
            final long orderNumFinal = orderNum;
            final List<DocumentSnapshot> subOrdersFinal = new ArrayList<>(subOrders);
            editButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startEditingOrder(orderNumFinal, subOrdersFinal);
                }
            });
            LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            editLp.setMarginStart((int) (8 * density));
            orderHeaderRow.addView(editButton, editLp);
            submittedOrdersList.addView(orderHeaderRow);

            if (isDeclined && declineReason != null && !declineReason.isEmpty()) {
                TextView reasonRow = new TextView(this);
                reasonRow.setText("Decline reason: " + declineReason);
                reasonRow.setTextColor(0xFFB71C1C);
                reasonRow.setTextSize(13);
                reasonRow.setPadding((int) (12 * density), (int) (2 * density), 0, (int) (6 * density));
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && getResources().getFont(R.font.inter_font_family) != null) {
                        reasonRow.setTypeface(getResources().getFont(R.font.inter_font_family));
                    }
                } catch (Exception ignored) {}
                submittedOrdersList.addView(reasonRow);
            }

            for (DocumentSnapshot doc : subOrders) {
                String sku = doc.getString("sku");
                String totalKg = doc.getString("totalKg");
                String scheme = doc.getString("scheme");
                if (sku == null) sku = "";
                if (totalKg == null) totalKg = "";
                if (scheme == null) scheme = "";
                long sortIdx = getSubOrderSortIndex(doc);
                String line = (sortIdx > 0 ? sortIdx + ". " : "") + sku + ", " + totalKg + " kg" + (scheme.isEmpty() ? "" : ", " + scheme);
                TextView row = new TextView(this);
                row.setText(line);
                row.setTextColor(0xFF34404F);
                row.setTextSize(14);
                row.setPadding((int) (12 * density), (int) (2 * density), 0, (int) (4 * density));
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && getResources().getFont(R.font.inter_font_family) != null) {
                        row.setTypeface(getResources().getFont(R.font.inter_font_family));
                    }
                } catch (Exception ignored) {}
                submittedOrdersList.addView(row);
            }
        }
    }

    private void showLimitReachedUI(List<DocumentSnapshot> documents) {
        if (orderLimitReachedContainer != null) orderLimitReachedContainer.setVisibility(View.VISIBLE);
        if (ordersLabel != null) ordersLabel.setVisibility(View.GONE);
        if (orderItemsContainer != null) orderItemsContainer.setVisibility(View.GONE);
        if (submitButton != null) submitButton.setVisibility(View.VISIBLE);

        if (orderPlacedList != null) {
            orderPlacedList.removeAllViews();
            List<DocumentSnapshot> sorted = new ArrayList<>(documents);
            Collections.sort(sorted, new Comparator<DocumentSnapshot>() {
                @Override
                public int compare(DocumentSnapshot a, DocumentSnapshot b) {
                    return Long.compare(getSubOrderSortIndex(a), getSubOrderSortIndex(b));
                }
            });
            float density = getResources().getDisplayMetrics().density;
            for (DocumentSnapshot doc : sorted) {
                String sku = doc.getString("sku");
                String totalKg = doc.getString("totalKg");
                String scheme = doc.getString("scheme");
                long sortIdx = getSubOrderSortIndex(doc);
                String orderNumStr = sortIdx > 0 ? String.valueOf(sortIdx) : "?";
                if (sku == null) sku = "";
                if (totalKg == null) totalKg = "";
                if (scheme == null) scheme = "";
                String line = "Sub-order " + orderNumStr + ": " + sku + ", " + totalKg + " kg, Scheme: " + scheme;
                TextView row = new TextView(this);
                row.setText(line);
                row.setTextColor(0xFF34404F);
                row.setTextSize(14);
                row.setPadding(0, (int) (6 * density), 0, (int) (6 * density));
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && getResources().getFont(R.font.inter_font_family) != null) {
                        row.setTypeface(getResources().getFont(R.font.inter_font_family));
                    }
                } catch (Exception ignored) {}
                orderPlacedList.addView(row);
            }
        }
    }

    private void showAddOrdersUI(int existingOrderCount, List<DocumentSnapshot> documents) {
        if (orderLimitReachedContainer != null) orderLimitReachedContainer.setVisibility(View.GONE);
        if (ordersLabel != null) ordersLabel.setVisibility(View.VISIBLE);
        if (orderItemsContainer != null) orderItemsContainer.setVisibility(View.VISIBLE);

        List<DocumentSnapshot> sorted = new ArrayList<>();
        if (documents != null && !documents.isEmpty()) {
            sorted.addAll(documents);
            Collections.sort(sorted, new Comparator<DocumentSnapshot>() {
                @Override
                public int compare(DocumentSnapshot a, DocumentSnapshot b) {
                    return Long.compare(getSubOrderSortIndex(a), getSubOrderSortIndex(b));
                }
            });
        }

            float density = getResources().getDisplayMetrics().density;
        int rowCount = orderItemsContainer != null ? orderItemsContainer.getChildCount() : 0;
        for (int i = 0; i < MAX_ORDERS_PER_DISTRIBUTOR_DATE && i < rowCount; i++) {
            View child = orderItemsContainer.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) child;
            while (row.getChildCount() > 1) row.removeViewAt(1);

            if ((i + 1) <= existingOrderCount && i < sorted.size()) {
                DocumentSnapshot doc = sorted.get(i);
                String sku = doc.getString("sku");
                String kg = doc.getString("totalKg");
                String scheme = doc.getString("scheme");
                if (sku == null) sku = "";
                if (kg == null) kg = "";
                if (scheme == null) scheme = "";
                addChipsToRow(row, sku, kg, scheme, density);
                final int rowIndex = i + 1;
                final String skuFinal = sku;
                final String kgFinal = kg;
                final String schemeFinal = scheme;
                row.setClickable(true);
                row.setFocusable(true);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showAddOrderItemDialog(rowIndex, row, skuFinal, kgFinal, schemeFinal);
                    }
                });
            } else {
                if (i < addOrderItemButtons.length && addOrderItemButtons[i] != null) {
                    ViewGroup parent = (ViewGroup) addOrderItemButtons[i].getParent();
                    if (parent != null) parent.removeView(addOrderItemButtons[i]);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int)(54 * density), (int)(23 * density));
                    row.addView(addOrderItemButtons[i], lp);
                    addOrderItemButtons[i].setVisibility((i + 1) == existingOrderCount + 1 ? View.VISIBLE : View.GONE);
                }
            }
        }

        if (submitButton != null) submitButton.setVisibility(existingOrderCount >= 1 ? View.VISIBLE : View.GONE);
    }

    private void addChipsToRow(LinearLayout parentLayout, String sku, String kg, String scheme, float density) {
            int paddingHorizontal = (int)(12 * density);
            int marginEnd = (int)(8 * density);
            int chipHeight = (int)(32 * density);
            
            TextView skuChip = new TextView(this);
            skuChip.setBackgroundResource(R.drawable.order_item_chip_background);
            skuChip.setText(sku);
            skuChip.setTextColor(Color.parseColor("#34404F"));
            skuChip.setTextSize(14);
            skuChip.setPadding(paddingHorizontal, 0, paddingHorizontal, 0);
            skuChip.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams skuParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, chipHeight);
            skuParams.setMargins(0, 0, marginEnd, 0);
            skuChip.setLayoutParams(skuParams);
            parentLayout.addView(skuChip);
            
            if (!kg.isEmpty()) {
                TextView kgChip = new TextView(this);
                kgChip.setBackgroundResource(R.drawable.order_item_chip_background);
                kgChip.setText(kg);
                kgChip.setTextColor(Color.parseColor("#34404F"));
                kgChip.setTextSize(14);
                kgChip.setPadding(paddingHorizontal, 0, paddingHorizontal, 0);
                kgChip.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams kgParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, chipHeight);
                kgParams.setMargins(0, 0, marginEnd, 0);
                kgChip.setLayoutParams(kgParams);
                parentLayout.addView(kgChip);
            }
            if (!scheme.isEmpty()) {
                TextView schemeChip = new TextView(this);
                schemeChip.setBackgroundResource(R.drawable.order_item_chip_background);
                schemeChip.setText(scheme);
                schemeChip.setTextColor(Color.parseColor("#34404F"));
                schemeChip.setTextSize(14);
                schemeChip.setPadding(paddingHorizontal, 0, paddingHorizontal, 0);
                schemeChip.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams schemeParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, chipHeight);
                schemeChip.setLayoutParams(schemeParams);
                parentLayout.addView(schemeChip);
            }
    }

    private void showAddOrderItemDialog(final int orderItemNumber, final View replaceView) {
        showAddOrderItemDialog(orderItemNumber, replaceView, null, null, null);
    }

    private void showAddOrderItemDialog(final int orderItemNumber, final View replaceView,
                                        final String existingSku, final String existingKg, final String existingScheme) {
        // Create dialog
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_order_item);
        
        // Make dialog background transparent for rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.7f); // Dim background
        }

        // Initialize dialog views
        final EditText skuSearchInput = dialog.findViewById(R.id.sku_search_input);
        final ListView skuList = dialog.findViewById(R.id.sku_list);
        final TextView skuSelectedText = dialog.findViewById(R.id.sku_selected_text);
        final EditText kgEditText = dialog.findViewById(R.id.kg_edittext);
        final EditText schemeEditText = dialog.findViewById(R.id.scheme_edittext);
        Button saveButton = dialog.findViewById(R.id.save_button);

        // Pre-fill when editing an existing row
        if (existingSku != null) {
            kgEditText.setText(existingKg != null ? existingKg : "");
            schemeEditText.setText(existingScheme != null ? existingScheme : "");
            skuSelectedText.setText("Selected: " + existingSku);
            skuSelectedText.setVisibility(View.VISIBLE);
        }

        // Source list from Firestore (never cleared by adapter); adapter has its own display list
        final List<String> skuOptionsList = new ArrayList<>();
        final List<String> skuDisplayList = new ArrayList<>();
        final ArrayAdapter<String> skuListAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                skuDisplayList
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view;
                textView.setTextColor(Color.parseColor("#34404F"));
                textView.setTextSize(14);
                int padding = (int) (12 * getResources().getDisplayMetrics().density);
                textView.setPadding(padding, padding, padding, padding);
                return view;
            }
        };
        skuList.setAdapter(skuListAdapter);

        final String[] selectedSkuHolder = new String[1];
        if (existingSku != null) selectedSkuHolder[0] = existingSku;

        Runnable applySkuFilter = new Runnable() {
            @Override
            public void run() {
                String prefix = skuSearchInput.getText() != null ? skuSearchInput.getText().toString().trim().toLowerCase(Locale.getDefault()) : "";
                skuDisplayList.clear();
                if (prefix.isEmpty()) {
                    skuList.setVisibility(View.GONE);
                    skuListAdapter.notifyDataSetChanged();
                    return;
                }
                for (String sku : skuOptionsList) {
                    if (sku.toLowerCase(Locale.getDefault()).startsWith(prefix)) {
                        skuDisplayList.add(sku);
                    }
                }
                if (skuDisplayList.isEmpty()) {
                    skuDisplayList.add("No matching SKUs");
                }
                skuListAdapter.notifyDataSetChanged();
                skuList.setVisibility(View.VISIBLE);
                skuList.requestLayout();
            }
        };

        skuSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applySkuFilter.run();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        skuList.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String sku = skuListAdapter.getItem(position);
                if (sku != null && !sku.startsWith("Loading") && !sku.startsWith("No SKUs") && !sku.startsWith("No matching") && !sku.startsWith("Failed")) {
                    selectedSkuHolder[0] = sku;
                    skuSelectedText.setText("Selected: " + sku);
                    skuSelectedText.setVisibility(View.VISIBLE);
                    skuList.setVisibility(View.GONE);
                    kgEditText.requestFocus();
                }
            }
        });

        // Load SKUs from Firestore in background; list stays hidden until user types
        FirebaseFirestore.getInstance().collection("skus")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    skuOptionsList.clear();
                    if (querySnapshot != null && !querySnapshot.isEmpty()) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            String name = doc.getString("name");
                            if (name != null && !name.trim().isEmpty()) {
                                skuOptionsList.add(name.trim());
                            } else {
                                skuOptionsList.add(doc.getId());
                            }
                        }
                        if (!skuOptionsList.isEmpty()) {
                            Collections.sort(skuOptionsList, new Comparator<String>() {
                                private String prefix(String s) {
                                    if (s == null || s.isEmpty()) return "";
                                    String t = s.trim();
                                    int i = t.indexOf(' ');
                                    return i > 0 ? t.substring(0, i) : t;
                                }
                                @Override
                                public int compare(String a, String b) {
                                    int c = prefix(a).compareToIgnoreCase(prefix(b));
                                    if (c != 0) return c;
                                    return a.compareToIgnoreCase(b);
                                }
                            });
                        }
                    }
                    runOnUiThread(applySkuFilter);
                })
                .addOnFailureListener(e -> runOnUiThread(() ->
                        Toast.makeText(AddOrdersActivity.this, "Could not load SKUs: " + (e.getMessage() != null ? e.getMessage() : ""), Toast.LENGTH_SHORT).show()));

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String selectedSKU = selectedSkuHolder[0] != null ? selectedSkuHolder[0] : "";
                String kg = kgEditText.getText().toString().trim();
                String scheme = schemeEditText.getText().toString().trim();

                if (selectedSKU.isEmpty()) {
                    Toast.makeText(AddOrdersActivity.this, "Please select a SKU from the list", Toast.LENGTH_SHORT).show();
                    return;
                }

                String distributor = distributorSpinner.getSelectedItem() != null ? distributorSpinner.getSelectedItem().toString() : "";
                if (distributor.isEmpty() || "Select distributor".equals(distributor)) {
                    Toast.makeText(AddOrdersActivity.this, "Please select a distributor", Toast.LENGTH_SHORT).show();
                    return;
                }

                String dateStr = orderDateInput != null ? orderDateInput.getText().toString().trim() : "";
                if (dateStr.isEmpty()) {
                    dateStr = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
                }

                saveButton.setEnabled(false);

                if (editingOrderNumber >= 0 && editingOrderDocs != null && orderItemNumber >= 1 && orderItemNumber <= editingOrderDocs.size()) {
                    DocumentSnapshot docToUpdate = editingOrderDocs.get(orderItemNumber - 1);
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("sku", selectedSKU);
                    updates.put("totalKg", kg);
                    updates.put("scheme", scheme);
                    updates.put("timestamp", FieldValue.serverTimestamp());
                    docToUpdate.getReference().update(updates)
                            .addOnSuccessListener(aVoid -> {
                                saveButton.setEnabled(true);
                                updateOrderItemWithChips(orderItemNumber, selectedSKU, kg, scheme, replaceView);
                                Toast.makeText(AddOrdersActivity.this, "Sub-order updated", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            })
                            .addOnFailureListener(e -> {
                                saveButton.setEnabled(true);
                                Toast.makeText(AddOrdersActivity.this, "Failed to update: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                    return;
                }

                int count = localDraftRows.size();
                if (orderItemNumber > count + 1) {
                    saveButton.setEnabled(true);
                    Toast.makeText(AddOrdersActivity.this, "Please fill previous row first.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Map<String, String> localRow = new HashMap<>();
                localRow.put("sku", selectedSKU);
                localRow.put("totalKg", kg);
                localRow.put("scheme", scheme);
                if (orderItemNumber - 1 < localDraftRows.size()) {
                    localDraftRows.set(orderItemNumber - 1, localRow);
                } else {
                    localDraftRows.add(localRow);
                }
                localDraftDistributor = distributor;
                localDraftDate = dateStr;
                saveButton.setEnabled(true);
                updateOrderItemWithChips(orderItemNumber, selectedSKU, kg, scheme, replaceView);
                if (orderItemNumber >= 1 && orderItemNumber < MAX_ORDERS_PER_DISTRIBUTOR_DATE && addOrderItemButtons[orderItemNumber] != null) {
                    addOrderItemButtons[orderItemNumber].setVisibility(View.VISIBLE);
                }
                if (submitButton != null) submitButton.setVisibility(View.VISIBLE);
                Toast.makeText(AddOrdersActivity.this, "Sub-order saved locally", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
        
        // Show dialog
        dialog.show();
    }

    private void showOrderDatePicker() {
        if (orderDateInput == null) return;
        Calendar cal = Calendar.getInstance();
        String current = orderDateInput.getText().toString().trim();
        if (!current.isEmpty()) {
            try {
                Date parsed = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(current);
                if (parsed != null) {
                    cal.setTime(parsed);
                }
            } catch (Exception ignored) {}
        }
        long selection = cal.getTimeInMillis();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date")
                .setTheme(R.style.KailashMaterialDatePickerTheme)
                .setSelection(selection)
                .build();
        picker.addOnPositiveButtonClickListener(millis -> {
            if (millis == null) return;
            Calendar selected = Calendar.getInstance();
            selected.setTimeInMillis(millis);
            orderDateInput.setText(new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(selected.getTime()));
            refreshOrdersStateForDistributorAndDate();
        });
        picker.show(getSupportFragmentManager(), "order_date_picker");
    }
    
    private void updateOrderItemWithChips(int orderNumber, String sku, String kg, String scheme, View replaceView) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout rowLayout = null;
        if (replaceView instanceof LinearLayout && replaceView.getParent() == orderItemsContainer) {
            rowLayout = (LinearLayout) replaceView;
        } else {
            android.view.ViewParent parent = replaceView != null ? replaceView.getParent() : null;
            if (parent instanceof LinearLayout) rowLayout = (LinearLayout) parent;
        }
        if (rowLayout != null) {
            while (rowLayout.getChildCount() > 1) rowLayout.removeViewAt(1);
            addChipsToRow(rowLayout, sku, kg, scheme, density);
            final int rowIndex = orderNumber;
            final String skuFinal = sku;
            final String kgFinal = kg;
            final String schemeFinal = scheme;
            final LinearLayout rowFinal = rowLayout;
            rowLayout.setClickable(true);
            rowLayout.setFocusable(true);
            rowLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAddOrderItemDialog(rowIndex, rowFinal, skuFinal, kgFinal, schemeFinal);
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
            // Set fixed dialog size: 317dp × 379dp
            float density = getResources().getDisplayMetrics().density;
            int widthPx = (int) (317 * density);
            int heightPx = (int) (379 * density);
            dialog.getWindow().setLayout(widthPx, heightPx);
            
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
                    Animation expandAnim = AnimationUtils.loadAnimation(AddOrdersActivity.this, R.anim.dropdown_expand);
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
                        Animation collapseAnim = AnimationUtils.loadAnimation(AddOrdersActivity.this, R.anim.dropdown_collapse);
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
                Animation collapseAnim = AnimationUtils.loadAnimation(AddOrdersActivity.this, R.anim.dropdown_collapse);
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
                                Animation collapseAnim = AnimationUtils.loadAnimation(AddOrdersActivity.this, R.anim.dropdown_collapse);
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
                Toast.makeText(AddOrdersActivity.this, 
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