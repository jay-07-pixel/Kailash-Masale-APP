package com.example.kailashmasale;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LeaveActivity extends AppCompatActivity {

    private static final int SUNDAY = Calendar.SUNDAY;

    private ImageButton backButton;
    private EditText subjectInput;
    private EditText nameInput;
    private EditText leaveFromDateInput;
    private EditText leaveToDateInput;
    private EditText leaveReasonInput;
    private ImageButton fromDateButton;
    private ImageButton toDateButton;
    private TextView applicationDateText;
    private Button submitButton;

    private View leaveCard;
    private View sundayWorkCard;
    private TextView choiceLeave;
    private TextView choiceWorkSunday;
    private EditText sundaySubjectInput;
    private EditText sundayNameInput;
    private EditText sundayDateInput;
    private ImageButton sundayDateButton;
    private EditText sundayReasonInput;

    private boolean isSundayWorkMode;
    private LinearLayout myApplicationsContainer;

    private final Calendar calendar = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // White status bar
        if (getWindow() != null) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, android.R.color.white));
            getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            );
        }

        setContentView(R.layout.activity_leave);

        backButton = findViewById(R.id.back_button);
        subjectInput = findViewById(R.id.subject_input);
        nameInput = findViewById(R.id.name_input);
        leaveFromDateInput = findViewById(R.id.leave_from_date);
        leaveToDateInput = findViewById(R.id.leave_to_date);
        leaveReasonInput = findViewById(R.id.leave_reason_input);
        fromDateButton = findViewById(R.id.from_date_button);
        toDateButton = findViewById(R.id.to_date_button);
        applicationDateText = findViewById(R.id.application_date_text);
        submitButton = findViewById(R.id.submit_button);

        leaveCard = findViewById(R.id.leave_card);
        sundayWorkCard = findViewById(R.id.sunday_work_card);
        choiceLeave = findViewById(R.id.choice_leave);
        choiceWorkSunday = findViewById(R.id.choice_work_sunday);
        sundaySubjectInput = findViewById(R.id.sunday_subject_input);
        sundayNameInput = findViewById(R.id.sunday_name_input);
        sundayDateInput = findViewById(R.id.sunday_date_input);
        sundayDateButton = findViewById(R.id.sunday_date_button);
        sundayReasonInput = findViewById(R.id.sunday_reason_input);
        myApplicationsContainer = findViewById(R.id.my_applications_container);

        isSundayWorkMode = false;

        // Auto-fill employee name from logged-in user
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeName = prefs.getString("logged_in_employee_name", "");
        if (nameInput != null && employeeName != null && !employeeName.isEmpty()) {
            nameInput.setText(employeeName);
        }
        if (sundayNameInput != null && employeeName != null && !employeeName.isEmpty()) {
            sundayNameInput.setText(employeeName);
        }
        if (sundaySubjectInput != null) {
            sundaySubjectInput.setText("Work on Sunday");
        }

        applyLeaveButtonColor34404F();

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // Set application date to today's date
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String today = sdf.format(calendar.getTime());
        applicationDateText.setText("Application date :- " + today);

        loadMyApplications();

        View.OnClickListener fromListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePicker(leaveFromDateInput);
            }
        };
        View.OnClickListener toListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDatePicker(leaveToDateInput);
            }
        };

        leaveFromDateInput.setOnClickListener(fromListener);
        fromDateButton.setOnClickListener(fromListener);

        leaveToDateInput.setOnClickListener(toListener);
        toDateButton.setOnClickListener(toListener);

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isSundayWorkMode) {
                    submitSundayWorkApplication();
                } else {
                    submitLeaveApplication();
                }
            }
        });

        if (choiceLeave != null) {
            choiceLeave.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchToLeaveMode();
                }
            });
        }
        if (choiceWorkSunday != null) {
            choiceWorkSunday.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switchToSundayWorkMode();
                }
            });
        }
        if (sundayDateInput != null) {
            sundayDateInput.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showSundayDatePicker();
                }
            });
        }
        if (sundayDateButton != null) {
            sundayDateButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showSundayDatePicker();
                }
            });
        }
    }

    private void switchToLeaveMode() {
        isSundayWorkMode = false;
        if (leaveCard != null) leaveCard.setVisibility(View.VISIBLE);
        if (sundayWorkCard != null) sundayWorkCard.setVisibility(View.GONE);
        setChoiceStyle(false);
        loadMyApplications();
    }

    private void switchToSundayWorkMode() {
        isSundayWorkMode = true;
        if (leaveCard != null) leaveCard.setVisibility(View.GONE);
        if (sundayWorkCard != null) sundayWorkCard.setVisibility(View.VISIBLE);
        setChoiceStyle(true);
        loadMyApplications();
    }

    private static final int LEAVE_BUTTON_COLOR = 0xFF34404F;

    private void applyLeaveButtonColor34404F() {
        if (submitButton != null) {
            if (submitButton instanceof androidx.appcompat.widget.AppCompatButton) {
                ((androidx.appcompat.widget.AppCompatButton) submitButton).setSupportBackgroundTintList(null);
            }
            submitButton.setBackground(getLeaveButtonDrawable());
        }
        if (choiceLeave != null && !isSundayWorkMode) choiceLeave.setBackground(getLeaveButtonDrawable());
        if (choiceWorkSunday != null && isSundayWorkMode) choiceWorkSunday.setBackground(getLeaveButtonDrawable());
    }

    private android.graphics.drawable.Drawable getLeaveButtonDrawable() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(LEAVE_BUTTON_COLOR);
        d.setCornerRadius(8 * getResources().getDisplayMetrics().density);
        return d;
    }

    private void setChoiceStyle(boolean sundaySelected) {
        if (choiceLeave != null) {
            choiceLeave.setBackground(sundaySelected ? getLeaveTabUnselectedDrawable() : getLeaveButtonDrawable());
            choiceLeave.setTextColor(sundaySelected ? Color.parseColor("#424242") : Color.WHITE);
        }
        if (choiceWorkSunday != null) {
            choiceWorkSunday.setBackground(sundaySelected ? getLeaveButtonDrawable() : getLeaveTabUnselectedDrawable());
            choiceWorkSunday.setTextColor(sundaySelected ? Color.WHITE : Color.parseColor("#424242"));
        }
    }

    private android.graphics.drawable.Drawable getLeaveTabUnselectedDrawable() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(0xFFE0E0E0);
        d.setCornerRadius(8 * getResources().getDisplayMetrics().density);
        return d;
    }

    private void showSundayDatePicker() {
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year1, month1, dayOfMonth) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(year1, month1, dayOfMonth);
                    if (selected.get(Calendar.DAY_OF_WEEK) != SUNDAY) {
                        Toast.makeText(LeaveActivity.this, "Please select a Sunday", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    if (sundayDateInput != null) {
                        sundayDateInput.setText(sdf.format(selected.getTime()));
                    }
                },
                year, month, day
        );
        dialog.show();
    }

    private void submitSundayWorkApplication() {
        String subject = sundaySubjectInput != null ? sundaySubjectInput.getText().toString().trim() : "";
        String name = sundayNameInput != null ? sundayNameInput.getText().toString().trim() : "";
        String sundayDate = sundayDateInput != null ? sundayDateInput.getText().toString().trim() : "";
        String reason = sundayReasonInput != null ? sundayReasonInput.getText().toString().trim() : "";
        String applicationDateStr = applicationDateText != null ? applicationDateText.getText().toString() : "";

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sundayDate.isEmpty()) {
            Toast.makeText(this, "Please select a Sunday", Toast.LENGTH_SHORT).show();
            return;
        }
        // Validate that selected date is actually a Sunday
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            java.util.Date d = sdf.parse(sundayDate);
            if (d != null) {
                Calendar c = Calendar.getInstance();
                c.setTime(d);
                if (c.get(Calendar.DAY_OF_WEEK) != SUNDAY) {
                    Toast.makeText(this, "Please select a Sunday", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Invalid date", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        String employeeId = prefs.getString("logged_in_employee_id", "");

        submitButton.setEnabled(false);
        submitButton.setAlpha(0.6f);

        Map<String, Object> doc = new HashMap<>();
        doc.put("type", "sunday_work");
        doc.put("subject", subject.isEmpty() ? "Work on Sunday" : subject);
        doc.put("name", name);
        doc.put("workOnSundayDate", sundayDate);
        doc.put("leaveFromDate", sundayDate);
        doc.put("leaveToDate", sundayDate);
        doc.put("reason", reason);
        doc.put("applicationDate", applicationDateStr);
        doc.put("employeeEmail", employeeEmail);
        doc.put("employeeId", employeeId);
        doc.put("timestamp", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance().collection("leave_applications")
                .add(doc)
                .addOnSuccessListener(aVoid -> {
                    submitButton.setEnabled(true);
                    submitButton.setAlpha(1f);
                    Toast.makeText(LeaveActivity.this, "Sunday work request submitted successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    submitButton.setEnabled(true);
                    submitButton.setAlpha(1f);
                    Toast.makeText(LeaveActivity.this, "Failed to submit: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void submitLeaveApplication() {
        String subject = subjectInput != null ? subjectInput.getText().toString().trim() : "";
        String name = nameInput != null ? nameInput.getText().toString().trim() : "";
        String fromDate = leaveFromDateInput != null ? leaveFromDateInput.getText().toString().trim() : "";
        String toDate = leaveToDateInput != null ? leaveToDateInput.getText().toString().trim() : "";
        String reason = leaveReasonInput != null ? leaveReasonInput.getText().toString().trim() : "";
        String applicationDateStr = applicationDateText != null ? applicationDateText.getText().toString() : "";

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
            return;
        }
        if (fromDate.isEmpty()) {
            Toast.makeText(this, "Please select leave from date", Toast.LENGTH_SHORT).show();
            return;
        }
        if (toDate.isEmpty()) {
            Toast.makeText(this, "Please select leave to date", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        String employeeId = prefs.getString("logged_in_employee_id", "");

        submitButton.setEnabled(false);
        submitButton.setAlpha(0.6f);

        Map<String, Object> leave = new HashMap<>();
        leave.put("subject", subject);
        leave.put("name", name);
        leave.put("leaveFromDate", fromDate);
        leave.put("leaveToDate", toDate);
        leave.put("reason", reason);
        leave.put("applicationDate", applicationDateStr);
        leave.put("employeeEmail", employeeEmail);
        leave.put("employeeId", employeeId);
        leave.put("timestamp", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance().collection("leave_applications")
                .add(leave)
                .addOnSuccessListener(aVoid -> {
                    submitButton.setEnabled(true);
                    submitButton.setAlpha(1f);
                    Toast.makeText(LeaveActivity.this, "Leave application submitted successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    submitButton.setEnabled(true);
                    submitButton.setAlpha(1f);
                    Toast.makeText(LeaveActivity.this, "Failed to submit: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void loadMyApplications() {
        if (myApplicationsContainer == null) return;
        myApplicationsContainer.removeAllViews();
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        if (employeeEmail == null || employeeEmail.isEmpty()) return;

        FirebaseFirestore.getInstance().collection("leave_applications")
                .whereEqualTo("employeeEmail", employeeEmail)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<QueryDocumentSnapshot> docs = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) docs.add(doc);
                    docs.sort((a, b) -> {
                        Object ta = a.get("timestamp");
                        Object tb = b.get("timestamp");
                        if (ta == null && tb == null) return 0;
                        if (ta == null) return 1;
                        if (tb == null) return -1;
                        if (ta instanceof com.google.firebase.Timestamp && tb instanceof com.google.firebase.Timestamp) {
                            return Long.compare(((com.google.firebase.Timestamp) tb).getSeconds(), ((com.google.firebase.Timestamp) ta).getSeconds());
                        }
                        return 0;
                    });
                    final boolean showSundayOnly = isSundayWorkMode;
                    runOnUiThread(() -> {
                        myApplicationsContainer.removeAllViews();
                        for (QueryDocumentSnapshot doc : docs) {
                            boolean isSundayWork = "sunday_work".equals(doc.getString("type"));
                            if (showSundayOnly != isSundayWork) continue;
                            View item = LayoutInflater.from(LeaveActivity.this).inflate(R.layout.item_leave_application_status, myApplicationsContainer, false);
                            TextView typeTv = item.findViewById(R.id.item_app_type);
                            TextView statusTv = item.findViewById(R.id.item_app_status);
                            TextView datesTv = item.findViewById(R.id.item_app_dates);
                            String type = isSundayWork ? "Work on Sunday" : "Leave";
                            typeTv.setText(type);
                            String from = doc.getString("leaveFromDate");
                            String to = doc.getString("leaveToDate");
                            String workDate = doc.getString("workOnSundayDate");
                            if (from != null && !from.isEmpty() || to != null && !to.isEmpty()) {
                                datesTv.setText((from != null ? from : "—") + (to != null && !to.equals(from) ? " to " + to : ""));
                            } else if (workDate != null && !workDate.isEmpty()) {
                                datesTv.setText(workDate);
                            } else {
                                datesTv.setText("—");
                            }
                            String status = "Pending";
                            Object statusObj = doc.get("status");
                            Object approvedObj = doc.get("approved");
                            if (statusObj != null && statusObj.toString().trim().length() > 0) {
                                String raw = statusObj.toString().trim();
                                status = raw.length() > 1
                                        ? raw.substring(0, 1).toUpperCase(Locale.getDefault()) + raw.substring(1).toLowerCase(Locale.getDefault())
                                        : raw.toUpperCase(Locale.getDefault());
                            } else if (approvedObj instanceof Boolean) {
                                status = (Boolean) approvedObj ? "Approved" : "Rejected";
                            }
                            statusTv.setText(status);
                            if ("Approved".equalsIgnoreCase(status)) {
                                statusTv.setBackgroundColor(0xFF4CAF50);
                                statusTv.setTextColor(0xFFFFFFFF);
                            } else if ("Rejected".equalsIgnoreCase(status)) {
                                statusTv.setBackgroundColor(0xFFE53935);
                                statusTv.setTextColor(0xFFFFFFFF);
                            } else {
                                statusTv.setBackgroundColor(0xFFE0E0E0);
                                statusTv.setTextColor(0xFF424242);
                            }
                            myApplicationsContainer.addView(item);
                        }
                    });
                })
                .addOnFailureListener(e -> { });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (myApplicationsContainer != null) loadMyApplications();
    }

    private void showDatePicker(final EditText target) {
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year1, month1, dayOfMonth) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(year1, month1, dayOfMonth);
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    target.setText(sdf.format(selected.getTime()));
                },
                year, month, day
        );
        dialog.show();
    }
}

