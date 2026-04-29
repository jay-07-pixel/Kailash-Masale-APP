package com.example.kailashmasale;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MonthlyPlanEditActivity extends AppCompatActivity {

    public static final String EXTRA_YEAR = "year";
    public static final String EXTRA_MONTH_INDEX = "month_index";

    private static final String[] MONTHS = {"Jan", "Feb", "March", "April", "May", "June",
            "July", "Aug", "Sept", "Oct", "Nov", "Dec"};
    /** Short month names for monthlyData doc id (e.g. 2026_Mar) */
    private static final String[] MONTH_SHORT = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private String[] yearOptions;
    private ImageButton backButton;
    private Spinner yearSpinner;
    private Spinner monthSpinner;
    private LinearLayout rowsContainer;
    private ArrayAdapter<String> targetAdapter;
    private List<String> targetOptionsList;
    private List<PlanDistributorItem> currentPlanItems;
    private String employeeId;
    private String selectedYear;
    private int selectedMonthIndex;
    private View submitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getWindow() != null) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));
            getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        }

        setContentView(R.layout.activity_monthly_plan_edit);

        backButton = findViewById(R.id.back_button);
        yearSpinner = findViewById(R.id.year_spinner);
        monthSpinner = findViewById(R.id.month_spinner);
        rowsContainer = findViewById(R.id.monthly_plan_rows_container);
        submitButton = findViewById(R.id.submit_button);

        setupSpinners();

        selectedYear = getIntent().getStringExtra(EXTRA_YEAR);
        int monthIndex = getIntent().getIntExtra(EXTRA_MONTH_INDEX, -1);
        if (selectedYear != null && yearOptions != null) {
            for (int i = 0; i < yearOptions.length; i++) {
                if (yearOptions[i].equals(selectedYear)) {
                    yearSpinner.setSelection(i);
                    break;
                }
            }
        }
        if (monthIndex >= 0 && monthIndex < MONTHS.length) {
            selectedMonthIndex = monthIndex;
            monthSpinner.setSelection(monthIndex);
        } else {
            selectedMonthIndex = monthSpinner.getSelectedItemPosition();
        }
        selectedYear = yearOptions != null && yearSpinner.getSelectedItemPosition() >= 0 && yearSpinner.getSelectedItemPosition() < yearOptions.length
                ? yearOptions[yearSpinner.getSelectedItemPosition()] : String.valueOf(Calendar.getInstance().get(Calendar.YEAR));

        yearSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (yearOptions != null && position >= 0 && position < yearOptions.length) selectedYear = yearOptions[position];
                loadData();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        monthSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedMonthIndex = position;
                loadData();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        backButton.setOnClickListener(v -> finish());
        if (submitButton != null) {
            submitButton.setOnClickListener(v -> savePlanToFirebase());
        }

        buildTargetOptions();
        loadData();
    }

    private void setupSpinners() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        yearOptions = new String[5];
        for (int i = 0; i < yearOptions.length; i++) {
            yearOptions[i] = String.valueOf(currentYear - i);
        }
        ArrayAdapter<String> yearAdapter = new ArrayAdapter<String>(this, R.layout.year_spinner_item, yearOptions) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) view;
                tv.setTextColor(position == yearSpinner.getSelectedItemPosition() ? 0xFF000000 : 0xBF000000);
                return view;
            }
        };
        yearAdapter.setDropDownViewResource(R.layout.year_spinner_dropdown_item);
        yearSpinner.setAdapter(yearAdapter);

        ArrayAdapter<String> monthAdapter = new ArrayAdapter<String>(this, R.layout.year_spinner_item, MONTHS) {
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) view;
                tv.setTextColor(position == monthSpinner.getSelectedItemPosition() ? 0xFF000000 : 0xBF000000);
                return view;
            }
        };
        monthAdapter.setDropDownViewResource(R.layout.year_spinner_dropdown_item);
        monthSpinner.setAdapter(monthAdapter);
    }

    private void buildTargetOptions() {
        targetOptionsList = new ArrayList<>();
        targetOptionsList.add("");
        for (int value = 50; value <= 2500; value += 25) {
            targetOptionsList.add(String.valueOf(value));
        }
        targetAdapter = new ArrayAdapter<>(this, R.layout.target_spinner_item, targetOptionsList);
        targetAdapter.setDropDownViewResource(R.layout.target_spinner_dropdown_item);
    }

    private void loadData() {
        if (rowsContainer == null) return;
        rowsContainer.removeAllViews();

        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", null);
        employeeId = prefs.getString("logged_in_employee_id", null);
        if (employeeEmail == null || employeeEmail.isEmpty()) {
            addMessageRow("Please log in.");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("employees")
                .whereEqualTo("email", employeeEmail)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) {
                        runOnUiThread(() -> addMessageRow("Could not load your assignment."));
                        return;
                    }
                    DocumentSnapshot empDoc = task.getResult().getDocuments().get(0);
                    if (employeeId == null || employeeId.isEmpty()) employeeId = empDoc.getId();
                    Object assigned = empDoc.get("assignedDistributorId");
                    if (assigned == null) assigned = empDoc.get("distributorId");
                    if (assigned == null) assigned = empDoc.get("assignedDistributorIds");
                    List<String> distributorIds = new ArrayList<>();
                    if (assigned instanceof String) distributorIds.add((String) assigned);
                    else if (assigned instanceof List) {
                        for (Object o : (List<?>) assigned) {
                            if (o instanceof String) distributorIds.add((String) o);
                        }
                    }
                    if (distributorIds.isEmpty()) {
                        runOnUiThread(() -> addMessageRow("No distributors assigned to you."));
                        return;
                    }
                    loadMonthlyDataThenDistributors(db, distributorIds);
                });
    }

    private void loadMonthlyDataThenDistributors(FirebaseFirestore db, List<String> distributorIds) {
        String docId = (employeeId != null ? employeeId : "") + "_" + selectedYear + "_" + (selectedMonthIndex + 1);
        db.collection("monthly_data").document(docId).get()
                .addOnSuccessListener(monthDoc -> {
                    Map<String, MonthlyRowData> monthlyByDist = new HashMap<>();
                    if (monthDoc != null && monthDoc.exists()) {
                        List<Map<String, Object>> rows = (List<Map<String, Object>>) monthDoc.get("rows");
                        if (rows != null) {
                            for (Map<String, Object> row : rows) {
                                String did = row.get("distributorId") != null ? row.get("distributorId").toString() : null;
                                if (did == null) continue;
                                MonthlyRowData d = new MonthlyRowData();
                                d.workingDays = "";
                                d.lmaKg = getStringFrom(row, "lmaKg", "lma", "LMA");
                                d.targetKg = getStringFrom(row, "targetKg", "target");
                                d.incentive = "";
                                monthlyByDist.put(did, d);
                            }
                        }
                    }
                    String monthlyDataDocId = (employeeId != null ? employeeId : "") + "_" + selectedYear + "_"
                            + (selectedMonthIndex >= 0 && selectedMonthIndex < MONTH_SHORT.length ? MONTH_SHORT[selectedMonthIndex] : "Jan");
                    db.collection("monthlyData").document(monthlyDataDocId).get()
                            .addOnSuccessListener(monthDataDoc -> {
                                if (monthDataDoc != null && monthDataDoc.exists()) {
                                    Object dd = monthDataDoc.get("distributorDetails");
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
                                                MonthlyRowData rowData = monthlyByDist.get(distId);
                                                if (rowData == null) rowData = new MonthlyRowData();
                                                if (wd != null) rowData.workingDays = wd.toString();
                                                if (inc != null) rowData.incentive = inc.toString();
                                                monthlyByDist.put(distId, rowData);
                                            }
                                        }
                                    }
                                }
                                fetchDistributorsAndPopulate(db, distributorIds, monthlyByDist);
                            })
                            .addOnFailureListener(e -> fetchDistributorsAndPopulate(db, distributorIds, monthlyByDist));
                })
                .addOnFailureListener(e -> fetchDistributorsAndPopulate(db, distributorIds, new HashMap<>()));
    }

    private static String getStringFrom(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object v = map.get(key);
            if (v != null) return v.toString();
        }
        return "";
    }

    private void fetchDistributorsAndPopulate(FirebaseFirestore db, List<String> distributorIds,
                                              Map<String, MonthlyRowData> monthlyByDist) {
        final Map<String, PlanDistributorItem> byId = new LinkedHashMap<>();
        final int[] fetched = {0};
        final int total = distributorIds.size();
        for (String id : distributorIds) {
            db.collection("distributors").document(id).get()
                    .addOnSuccessListener(doc -> {
                        String name = "";
                        String bitName = "";
                        if (doc != null && doc.exists()) {
                            name = doc.getString("distributorName");
                            if (name == null || name.isEmpty()) name = doc.getString("distributor_name");
                            if (name == null || name.isEmpty()) name = doc.getString("name");
                            if (name == null) name = "";
                            bitName = bitsArrayToString(doc.get("bits"));
                            if (bitName == null || bitName.isEmpty()) bitName = doc.getString("bit");
                            if (bitName == null || bitName.isEmpty()) bitName = doc.getString("bitName");
                            if (bitName == null || bitName.isEmpty()) bitName = doc.getString("bit_name");
                            if (bitName == null) bitName = "";
                        }
                        MonthlyRowData rowData = monthlyByDist.get(id);
                        byId.put(id, new PlanDistributorItem(id, name, bitName, rowData));
                        fetched[0]++;
                        if (fetched[0] == total) runOnUiThread(() -> buildOrderedItemsAndPopulate(distributorIds, byId));
                    })
                    .addOnFailureListener(e -> {
                        byId.put(id, new PlanDistributorItem(id, "", "", monthlyByDist.get(id)));
                        fetched[0]++;
                        if (fetched[0] == total) runOnUiThread(() -> buildOrderedItemsAndPopulate(distributorIds, byId));
                    });
        }
    }

    private void buildOrderedItemsAndPopulate(List<String> distributorIds, Map<String, PlanDistributorItem> byId) {
        List<PlanDistributorItem> items = new ArrayList<>();
        for (String id : distributorIds) {
            PlanDistributorItem item = byId.get(id);
            if (item != null && item.name != null && !item.name.trim().isEmpty()) items.add(item);
        }
        populateTableRows(items);
    }

    private static String bitsArrayToString(Object bits) {
        if (bits == null) return null;
        if (bits instanceof List) {
            List<?> list = (List<?>) bits;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(", ");
                Object item = list.get(i);
                if (item != null) sb.append(item.toString().trim());
            }
            return sb.toString();
        }
        return bits.toString();
    }

    private void populateTableRows(List<PlanDistributorItem> items) {
        if (rowsContainer == null) return;
        rowsContainer.removeAllViews();
        currentPlanItems = items;
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < items.size(); i++) {
            PlanDistributorItem item = items.get(i);
            View row = inflater.inflate(R.layout.item_monthly_plan_edit_row, rowsContainer, false);
            if (i % 2 == 1) {
                row.setBackgroundColor(0xFFFFFFFF);
            } else {
                row.setBackgroundResource(R.drawable.monthly_plan_row_grey);
            }
            TextView dn = row.findViewById(R.id.row_dn);
            TextView wd = row.findViewById(R.id.row_wd);
            TextView lma = row.findViewById(R.id.row_lma);
            View targetCell = row.findViewById(R.id.row_target_cell);
            TextView targetValue = row.findViewById(R.id.row_target_value);
            TextView incentive = row.findViewById(R.id.row_incentive);

            dn.setText(item.name != null ? item.name : "");
            String wdVal = item.monthly != null && item.monthly.workingDays != null && !item.monthly.workingDays.isEmpty() ? item.monthly.workingDays : "0";
            String lmaVal = item.monthly != null && item.monthly.lmaKg != null && !item.monthly.lmaKg.isEmpty() ? item.monthly.lmaKg : "0";
            String incVal = item.monthly != null && item.monthly.incentive != null && !item.monthly.incentive.isEmpty() ? item.monthly.incentive : "0";
            wd.setText(wdVal);
            lma.setText(lmaVal);
            incentive.setText(incVal);

            String initialTarget = (item.monthly != null && item.monthly.targetKg != null && !item.monthly.targetKg.isEmpty())
                    ? item.monthly.targetKg : "";
            targetValue.setText(initialTarget);

            targetCell.setOnClickListener(v -> showTargetDropdown(v, targetValue));
            rowsContainer.addView(row);
        }
    }

    /** Shows dropdown for Target Kg when user taps the target cell. Only the value is shown until then. */
    private void showTargetDropdown(View anchor, TextView targetValue) {
        if (targetOptionsList == null) return;
        ListPopupWindow popup = new ListPopupWindow(this);
        popup.setAdapter(new ArrayAdapter<>(this, R.layout.target_spinner_dropdown_item, targetOptionsList));
        popup.setAnchorView(anchor);
        popup.setWidth((int) (88 * getResources().getDisplayMetrics().density));
        popup.setModal(true);
        popup.setOnItemClickListener((parent, view, position, id) -> {
            String selected = targetOptionsList.get(position);
            targetValue.setText(selected != null ? selected : "");
            popup.dismiss();
        });
        popup.show();
    }

    private void addMessageRow(String message) {
        if (rowsContainer == null) return;
        currentPlanItems = null;
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setPadding(32, 24, 32, 24);
        tv.setTextColor(0xFF424242);
        rowsContainer.addView(tv);
    }

    private void savePlanToFirebase() {
        if (currentPlanItems == null || currentPlanItems.isEmpty()) {
            Toast.makeText(this, "No plan data to save.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (rowsContainer == null || employeeId == null || employeeId.isEmpty()) {
            Toast.makeText(this, "Cannot save: missing employee or data.", Toast.LENGTH_SHORT).show();
            return;
        }
        int childCount = rowsContainer.getChildCount();
        if (childCount != currentPlanItems.size()) {
            Toast.makeText(this, "Data mismatch. Please reload and try again.", Toast.LENGTH_SHORT).show();
            return;
        }
        int saveYearPos = yearSpinner != null ? yearSpinner.getSelectedItemPosition() : 0;
        int saveMonthPos = monthSpinner != null ? monthSpinner.getSelectedItemPosition() : 0;
        String yearToSave = (yearOptions != null && saveYearPos >= 0 && saveYearPos < yearOptions.length)
                ? yearOptions[saveYearPos] : (selectedYear != null ? selectedYear : String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));
        int monthNumToSave = saveMonthPos >= 0 && saveMonthPos < MONTHS.length ? saveMonthPos + 1 : (selectedMonthIndex + 1);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < childCount; i++) {
            View rowView = rowsContainer.getChildAt(i);
            PlanDistributorItem item = currentPlanItems.get(i);
            TextView wdTv = rowView.findViewById(R.id.row_wd);
            TextView lmaTv = rowView.findViewById(R.id.row_lma);
            TextView targetTv = rowView.findViewById(R.id.row_target_value);
            TextView incTv = rowView.findViewById(R.id.row_incentive);
            String wd = wdTv != null && wdTv.getText() != null ? wdTv.getText().toString().trim() : "0";
            String lma = lmaTv != null && lmaTv.getText() != null ? lmaTv.getText().toString().trim() : "0";
            String targetKg = targetTv != null && targetTv.getText() != null ? targetTv.getText().toString().trim() : "";
            String incentive = incTv != null && incTv.getText() != null ? incTv.getText().toString().trim() : "0";
            Map<String, Object> row = new HashMap<>();
            row.put("distributorId", item.distributorId);
            row.put("distributorName", item.name);
            row.put("bits", item.bitName);
            row.put("workingDays", wd);
            row.put("lmaKg", lma);
            row.put("targetKg", targetKg);
            row.put("incentive", incentive);
            rows.add(row);
        }
        String docId = employeeId + "_" + yearToSave + "_" + monthNumToSave;
        Map<String, Object> data = new HashMap<>();
        data.put("year", yearToSave);
        data.put("month", monthNumToSave);
        data.put("employeeId", employeeId);
        data.put("rows", rows);
        FirebaseFirestore.getInstance().collection("monthly_data").document(docId)
                .set(data)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(MonthlyPlanEditActivity.this, "Plan saved.", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(MonthlyPlanEditActivity.this, "Failed to save: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"), Toast.LENGTH_LONG).show();
                });
    }

    private static class MonthlyRowData {
        String workingDays;
        String lmaKg;
        String targetKg;
        String incentive;
    }

    private static class PlanDistributorItem {
        final String distributorId;
        final String name;
        final String bitName;
        final MonthlyRowData monthly;
        PlanDistributorItem(String distributorId, String name, String bitName, MonthlyRowData monthly) {
            this.distributorId = distributorId;
            this.name = name != null ? name : "";
            this.bitName = bitName != null ? bitName : "";
            this.monthly = monthly;
        }
    }
}
