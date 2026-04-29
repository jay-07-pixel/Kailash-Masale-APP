package com.example.kailashmasale;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

public class ProfileActivity extends AppCompatActivity {

    private ImageButton backButton;
    private ImageView profilePicture;
    private TextView profileName;
    private TextView profileRole;
    private EditText nameEditText;
    private EditText emailEditText;
    private EditText phoneEditText;
    private EditText designationEditText;
    private EditText headquartersEditText;
    private ImageButton gridButton;
    private ImageButton uploadButton;
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
        
        setContentView(R.layout.activity_profile);

        initializeViews();
        loadProfileData();
        setupClickListeners();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.back_button);
        profilePicture = findViewById(R.id.profile_picture);
        profileName = findViewById(R.id.profile_name);
        profileRole = findViewById(R.id.profile_role);
        nameEditText = findViewById(R.id.name_edit_text);
        emailEditText = findViewById(R.id.email_edit_text);
        phoneEditText = findViewById(R.id.phone_edit_text);
        designationEditText = findViewById(R.id.designation_edit_text);
        headquartersEditText = findViewById(R.id.headquarters_edit_text);
        gridButton = findViewById(R.id.grid_button);
        uploadButton = findViewById(R.id.upload_button);
        checkInOutButton = findViewById(R.id.check_in_out_button);
    }

    private void loadProfileData() {
        // Get employee name from intent or shared preferences
        String name = getIntent().getStringExtra("EMPLOYEE_NAME");
        if (name != null && !name.isEmpty()) {
            profileName.setText(name);
            nameEditText.setText(name);
        } else {
            profileName.setText("Shlok Thakkral");
            nameEditText.setText("Shlok Thakkral");
        }

        // Set role
        profileRole.setText("Employee");

        // TODO: Load actual profile data from API or database
        // For now, leaving fields empty as shown in the image
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

        profilePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(ProfileActivity.this, "Change Profile Picture", Toast.LENGTH_SHORT).show();
                // TODO: Open image picker
            }
        });

        if (gridButton != null) {
            gridButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(ProfileActivity.this, WeeklyPlannerActivity.class);
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
                    Intent intent = new Intent(ProfileActivity.this, CheckInOutActivity.class);
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
                    Animation expandAnim = AnimationUtils.loadAnimation(ProfileActivity.this, R.anim.dropdown_expand);
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
                        Animation collapseAnim = AnimationUtils.loadAnimation(ProfileActivity.this, R.anim.dropdown_collapse);
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
                Animation collapseAnim = AnimationUtils.loadAnimation(ProfileActivity.this, R.anim.dropdown_collapse);
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
                                Animation collapseAnim = AnimationUtils.loadAnimation(ProfileActivity.this, R.anim.dropdown_collapse);
                                collapseAnim.setAnimationListener(new Animation.AnimationListener() {
                                    @Override
                                    public void onAnimationStart(Animation animation) {}

                                    @Override
                                    public void onAnimationEnd(Animation animation) {
                                        dropdownContainer.setVisibility(View.GONE);
                                        // Ensure spinner keeps its border after selection
                                        distributerSpinner.setBackgroundResource(R.drawable.dialog_spinner_background);
                                        distributerSpinner.invalidate();
                                        distributerSpinner.refreshDrawableState();
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

        // Close button
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        // Upload button
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String selectedDistributer = distributerSpinner.getSelectedItem().toString();
                Toast.makeText(ProfileActivity.this, "Uploading stock for " + selectedDistributer, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                // TODO: Implement actual upload logic
            }
        });

        // Show dialog
        dialog.show();
    }
}

