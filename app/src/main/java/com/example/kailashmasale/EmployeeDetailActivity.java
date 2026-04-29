package com.example.kailashmasale;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class EmployeeDetailActivity extends AppCompatActivity {

    private ImageButton backButton;
    private TextView pageTitle;
    private Spinner yearSpinner;
    private TextView distributorName;
    private TextView targetGiven;
    private LinearLayout checkInOutButton;
    private ImageButton gridButton;
    private ImageButton uploadButton;
    private FloatingActionButton fabAdd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee_detail);

        initializeViews();
        setupSpinner();
        setupClickListeners();
        loadEmployeeData();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.back_button);
        pageTitle = findViewById(R.id.page_title);
        yearSpinner = findViewById(R.id.year_spinner);
        distributorName = findViewById(R.id.distributor_name);
        targetGiven = findViewById(R.id.target_given);
        checkInOutButton = findViewById(R.id.check_in_out_button);
        gridButton = findViewById(R.id.grid_button);
        uploadButton = findViewById(R.id.upload_button);
        fabAdd = findViewById(R.id.fab_add);
        if (fabAdd != null) fabAdd.setVisibility(View.GONE);
    }

    private void setupSpinner() {
        // Year spinner setup
        String[] years = {"2025", "2024", "2023", "2022", "2021"};
        ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                years
        );
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        yearSpinner.setAdapter(yearAdapter);
        yearSpinner.setSelection(0); // Set "2025" as default
    }

    private void loadEmployeeData() {
        // Get employee name from intent
        String employeeName = getIntent().getStringExtra("EMPLOYEE_NAME");
        if (employeeName != null && !employeeName.isEmpty()) {
            pageTitle.setText(employeeName);
        }

        // TODO: Load actual data from API or database
        // For now, showing placeholder values
        distributorName.setText("Distributor Name");
        targetGiven.setText("Target Given:-");
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        if (checkInOutButton != null) {
            checkInOutButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(EmployeeDetailActivity.this, CheckInOutActivity.class);
                    startActivity(intent);
                }
            });
        }

        if (gridButton != null) {
            gridButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(EmployeeDetailActivity.this, WeeklyPlannerActivity.class);
                    startActivity(intent);
                }
            });
        }

        if (uploadButton != null) {
            uploadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(EmployeeDetailActivity.this, "Upload", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (fabAdd != null) {
            fabAdd.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // TODO: Handle FAB click action
                    Toast.makeText(EmployeeDetailActivity.this, "Add", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}






