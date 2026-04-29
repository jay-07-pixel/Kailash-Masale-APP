package com.example.kailashmasale;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MonthlyAttendanceActivity extends AppCompatActivity {

    private ImageButton backButton;
    private Spinner yearSpinner;
    private Spinner monthSpinner;
    private ImageButton fab;
    private LinearLayout gridButton;
    private LinearLayout uploadButton;
    private LinearLayout checkInOutButton;

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
        
        setContentView(R.layout.activity_monthly_attendance);

        initializeViews();
        setupSpinners();
        setupClickListeners();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.back_button);
        yearSpinner = findViewById(R.id.year_spinner);
        monthSpinner = findViewById(R.id.month_spinner);
        fab = findViewById(R.id.fab);
        gridButton = findViewById(R.id.grid_button_layout);
        uploadButton = findViewById(R.id.upload_button_layout);
        checkInOutButton = findViewById(R.id.check_in_out_button);
    }

    private void setupSpinners() {
        // Setup Year Spinner
        String[] years = {"2025", "2024", "2023", "2022", "2021"};
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
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Update team attendance count so manager dashboard shows "My Team Attendance" that has been added
                DashboardActivity.incrementTeamAttendance(MonthlyAttendanceActivity.this);
                Toast.makeText(MonthlyAttendanceActivity.this, "Add Attendance Entry", Toast.LENGTH_SHORT).show();
                // TODO: Implement add attendance entry functionality (e.g. dialog then save)
            }
        });

        if (gridButton != null) {
            gridButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MonthlyAttendanceActivity.this, WeeklyPlannerActivity.class);
                    startActivity(intent);
                }
            });
        }

        if (uploadButton != null) {
            uploadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(MonthlyAttendanceActivity.this, "Upload Attendance", Toast.LENGTH_SHORT).show();
                    // TODO: Implement upload attendance functionality
                }
            });
        }

        if (checkInOutButton != null) {
            checkInOutButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(MonthlyAttendanceActivity.this, CheckInOutActivity.class);
                    startActivity(intent);
                }
            });
        }
    }
}
