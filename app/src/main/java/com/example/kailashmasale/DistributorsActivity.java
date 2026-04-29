package com.example.kailashmasale;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class DistributorsActivity extends AppCompatActivity {

    private ImageButton backButton;
    private LinearLayout checkInOutButton;
    private ImageButton gridButton;
    private ImageButton uploadButton;
    private LinearLayout distributorRowsContainer;

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
        
        setContentView(R.layout.activity_distributors);

        initializeViews();
        setupClickListeners();
        loadAssignedDistributors();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAssignedDistributors();
    }

    private void initializeViews() {
        checkInOutButton = findViewById(R.id.check_in_out_button);
        gridButton = findViewById(R.id.grid_button);
        uploadButton = findViewById(R.id.upload_button);
        distributorRowsContainer = findViewById(R.id.distributor_rows_container);
    }

    private void loadAssignedDistributors() {
        if (distributorRowsContainer == null) return;
        distributorRowsContainer.removeAllViews();

        android.content.SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", null);
        if (employeeEmail == null || employeeEmail.isEmpty()) {
            addEmptyStateRow("Not logged in.");
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("employees")
                .whereEqualTo("email", employeeEmail)
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || task.getResult().isEmpty()) {
                        runOnUiThread(() -> addEmptyStateRow("Could not load your assignment."));
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
                        runOnUiThread(() -> addEmptyStateRow("No distributors assigned to you."));
                        return;
                    }
                    fetchDistributorsAndPopulate(db, distributorIds);
                });
    }

    private void fetchDistributorsAndPopulate(FirebaseFirestore db, List<String> distributorIds) {
        final List<DistributorItem> items = new ArrayList<>();
        final int[] fetched = {0};
        final int total = distributorIds.size();
        for (String id : distributorIds) {
            db.collection("distributors").document(id).get()
                    .addOnSuccessListener(doc -> {
                        String name = "";
                        String bitName = "";
                        String impNotes = "";
                        if (doc != null && doc.exists()) {
                            name = doc.getString("distributorName");
                            if (name == null || name.isEmpty()) name = doc.getString("distributor_name");
                            if (name == null || name.isEmpty()) name = doc.getString("name");
                            // Never use document ID as name — only show rows with a real name
                            if (name == null) name = "";
                            name = name.trim();
                            bitName = bitsArrayToString(doc.get("bits"));
                            if (bitName == null || bitName.isEmpty()) bitName = doc.getString("bit");
                            if (bitName == null || bitName.isEmpty()) bitName = doc.getString("bitName");
                            if (bitName == null || bitName.isEmpty()) bitName = doc.getString("bit_name");
                            if (bitName == null) bitName = "";
                            impNotes = doc.getString("importantNotes");
                            if (impNotes == null) impNotes = doc.getString("impNotes");
                            if (impNotes == null) impNotes = doc.getString("important_notes");
                            if (impNotes == null) impNotes = doc.getString("imp_notes");
                            if (impNotes == null) impNotes = doc.getString("notes");
                            if (impNotes == null) impNotes = doc.getString("note");
                            if (impNotes == null) impNotes = "";
                        }
                        synchronized (items) {
                            // Only add distributors that have a real name (no ID-only or empty rows)
                            if (name != null && !name.isEmpty()) {
                                items.add(new DistributorItem(name, bitName, impNotes));
                            }
                        }
                        fetched[0]++;
                        if (fetched[0] == total) {
                            runOnUiThread(() -> populateRows(items));
                        }
                    })
                    .addOnFailureListener(e -> {
                        fetched[0]++;
                        if (fetched[0] == total) runOnUiThread(() -> populateRows(items));
                    });
        }
    }

    /** Read Firestore 'bits' array (e.g. ["Akola"]) and return comma-separated string. */
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

    private static class DistributorItem {
        final String name;
        final String bitName;
        final String impNotes;
        DistributorItem(String name, String bitName, String impNotes) {
            this.name = name != null ? name : "";
            this.bitName = bitName != null ? bitName : "";
            this.impNotes = impNotes != null ? impNotes : "";
        }
    }

    private void populateRows(List<DistributorItem> items) {
        if (distributorRowsContainer == null) return;
        distributorRowsContainer.removeAllViews();
        // Only add rows for items with a name — no empty rows, no ID-only rows
        int rowIndex = 0;
        for (DistributorItem item : items) {
            String name = item.name != null ? item.name.trim() : "";
            if (name.isEmpty()) continue;
            boolean lightBg = (rowIndex % 2 == 0);
            distributorRowsContainer.addView(createRow(item.name, item.bitName, item.impNotes, lightBg));
            rowIndex++;
        }
        if (rowIndex == 0) {
            addEmptyStateRow(items.isEmpty() ? "No distributors to show." : "No distributors with names found.");
        }
    }

    private LinearLayout createRow(String distributorName, String bitName, String impNotes, boolean lightBackground) {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        row.setOrientation(LinearLayout.HORIZONTAL);
        int padding = (int) (12 * getResources().getDisplayMetrics().density);
        row.setPadding(padding, padding, padding, padding);
        row.setBackgroundColor(lightBackground ? 0xFFFFFFFF : 0x6334404F);
        addCell(row, 1.6f, distributorName, true);
        addVerticalDivider(row);
        addCell(row, 1.3f, bitName, false);
        addVerticalDivider(row);
        addNotesCell(row, 1f, impNotes, distributorName);
        return row;
    }

    private void addVerticalDivider(LinearLayout row) {
        View divider = new View(this);
        int w = (int) (1 * getResources().getDisplayMetrics().density);
        int m = (int) (4 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMargins(m, 0, m, 0);
        divider.setLayoutParams(params);
        divider.setBackgroundColor(0xFFD0D0D0);
        row.addView(divider);
    }

    private void addCell(LinearLayout row, float weight, String text, boolean alignStart) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        tv.setLayoutParams(params);
        tv.setText(text != null ? text : "");
        tv.setTextColor(0xFF000000);
        tv.setTextSize(13);
        tv.setGravity(alignStart ? (android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL) : android.view.Gravity.CENTER);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && getResources().getFont(R.font.inter_font_family) != null) {
                tv.setTypeface(getResources().getFont(R.font.inter_font_family));
            }
        } catch (Exception ignored) {}
        row.addView(tv);
    }

    /** IMP Notes cell: shows truncated text + dropdown arrow; click shows popup with this distributor's note from the collection. */
    private void addNotesCell(LinearLayout row, float weight, String impNotes, String distributorName) {
        String note = (impNotes != null ? impNotes : "").trim();
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        cell.setLayoutParams(cellParams);
        cell.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView tv = new TextView(this);
        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(tvParams);
        tv.setText(note.isEmpty() ? "—" : note);
        tv.setTextColor(0xFF000000);
        tv.setTextSize(13);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setMaxLines(1);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && getResources().getFont(R.font.inter_font_family) != null) {
                tv.setTypeface(getResources().getFont(R.font.inter_font_family));
            }
        } catch (Exception ignored) {}
        cell.addView(tv);

        ImageView arrow = new ImageView(this);
        int arrowSize = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(arrowSize, arrowSize);
        int margin = (int) (4 * getResources().getDisplayMetrics().density);
        arrowParams.setMargins(margin, 0, 0, 0);
        arrow.setLayoutParams(arrowParams);
        arrow.setImageResource(R.drawable.ic_dropdown_arrow);
        arrow.setScaleType(ImageView.ScaleType.FIT_CENTER);
        cell.addView(arrow);

        cell.setClickable(true);
        cell.setFocusable(true);
        TypedValue out = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true)) {
            cell.setBackgroundResource(out.resourceId);
        }
        final String fullNote = note;
        final String distName = (distributorName != null ? distributorName : "").trim();
        cell.setOnClickListener(v -> showNotesPopup(distName, fullNote));
        row.addView(cell);
    }

    private void showNotesPopup(String distributorName, String fullNote) {
        String title = distributorName.isEmpty() ? "IMP Notes" : "Note – " + distributorName;
        String message = (fullNote == null || fullNote.isEmpty()) ? "No note for this distributor." : fullNote;
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void addEmptyStateRow(String message) {
        if (distributorRowsContainer == null) return;
        distributorRowsContainer.addView(createRow(message, "—", "—", true));
    }

    private void setupClickListeners() {
        View backButtonContainer = findViewById(R.id.back_button_container);
        backButtonContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        if (checkInOutButton != null) {
            checkInOutButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(DistributorsActivity.this, CheckInOutActivity.class);
                    startActivity(intent);
                }
            });
        }

        if (gridButton != null) {
            gridButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(DistributorsActivity.this, WeeklyPlannerActivity.class);
                    startActivity(intent);
                }
            });
        }

        if (uploadButton != null) {
            uploadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(DistributorsActivity.this, "Upload", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}



