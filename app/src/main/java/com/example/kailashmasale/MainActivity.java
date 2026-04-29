package com.example.kailashmasale;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "user_prefs";
    private static final String KEY_REMEMBER_ME = "remember_me";
    private static final String KEY_SAVED_DOMAIN = "saved_domain";
    private static final String KEY_SAVED_USER_ID = "saved_user_id";
    private static final String KEY_SAVED_PASSWORD = "saved_password";

    private Spinner domainSpinner;
    private EditText userIdEditText;
    private EditText passwordEditText;
    private ImageButton passwordToggle;
    private Button loginButton;
    private CheckBox rememberMeRadio;
    private ScrollView scrollView;
    private View blurOverlay;
    private boolean isPasswordVisible = false;
    private int initialHeight = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initializeViews();
        setupDomainSpinner();
        loadSavedCredentialsAndAutoLogin();
        setupPasswordToggle();
        setupLoginButton();
        setupAutoScroll();
        setupPasswordTextWatcher();
        setupPasswordClick();
        setupKeyboardListener();
        setupSimpleScroll();
        setupPasswordDoneAction();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ensure blur overlay is hidden when activity resumes
        if (blurOverlay != null) {
            blurOverlay.setVisibility(View.GONE);
            blurOverlay.setAlpha(0f);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Hide blur overlay when activity is paused
        if (blurOverlay != null) {
            blurOverlay.setVisibility(View.GONE);
            blurOverlay.setAlpha(0f);
        }
    }

    private void initializeViews() {
        domainSpinner = findViewById(R.id.domain_spinner);
        userIdEditText = findViewById(R.id.user_id_edittext);
        passwordEditText = findViewById(R.id.password_edittext);
        passwordToggle = findViewById(R.id.password_toggle);
        loginButton = findViewById(R.id.login_button);
        rememberMeRadio = findViewById(R.id.remember_me_radio);
        scrollView = findViewById(R.id.scroll_view);
        blurOverlay = findViewById(R.id.blur_overlay);
    }

    private void setupDomainSpinner() {
        // Create custom adapter with placeholder color support
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(
                this,
                R.layout.spinner_selected_item,
                getResources().getStringArray(R.array.domain_options)
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = (TextView) view;
                String item = getItem(position).toString();
                if (item.equals("---------------")) {
                    // Set placeholder color with 50% opacity (#8034404F)
                    textView.setTextColor(getResources().getColor(R.color.placeholder_color, null));
                } else {
                    // Set normal color
                    textView.setTextColor(getResources().getColor(R.color.text_primary, null));
                }
                return view;
            }
            
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = (TextView) view;
                String item = getItem(position).toString();
                if (item.equals("---------------")) {
                    // Set placeholder color with 50% opacity for dropdown
                    textView.setTextColor(getResources().getColor(R.color.placeholder_color, null));
                } else {
                    // Set normal color
                    textView.setTextColor(getResources().getColor(R.color.text_primary, null));
                }
                return view;
            }
        };
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        domainSpinner.setAdapter(adapter);
        
        // Set default selection to position 0 (placeholder)
        domainSpinner.setSelection(0);
        
        // Set dropdown positioning to appear between domain and password boxes
        domainSpinner.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    // Calculate position to show dropdown between domain and password
                    int[] location = new int[2];
                    domainSpinner.getLocationOnScreen(location);
                    
                    // Position dropdown to start right below domain box
                    domainSpinner.setDropDownVerticalOffset(domainSpinner.getHeight());
                    
                    // Show blur overlay with fade-in animation
                    showBlurOverlay();
                }
                return false;
            }
        });
        
        // Hide blur overlay when item is selected
        domainSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                // Hide blur overlay with fade-out animation
                hideBlurOverlay();
                // Scroll to show the spinner when an item is selected
                scrollView.smoothScrollTo(0, domainSpinner.getTop() - 100);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // Hide blur overlay when nothing is selected
                hideBlurOverlay();
            }
        });
        
        // Hide blur overlay when clicked
        blurOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideBlurOverlay();
                domainSpinner.performClick(); // Close the dropdown
            }
        });
    }

    private void loadSavedCredentialsAndAutoLogin() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_REMEMBER_ME, false)) return;

        String domain = prefs.getString(KEY_SAVED_DOMAIN, "");
        String userId = prefs.getString(KEY_SAVED_USER_ID, "");
        String password = prefs.getString(KEY_SAVED_PASSWORD, "");
        if (domain.isEmpty() || userId.isEmpty() || password.isEmpty()) return;

        String[] options = getResources().getStringArray(R.array.domain_options);
        for (int i = 0; i < options.length; i++) {
            if (domain.equals(options[i])) {
                domainSpinner.setSelection(i);
                break;
            }
        }
        userIdEditText.setText(userId);
        passwordEditText.setText(password);
        rememberMeRadio.setChecked(true);

        userIdEditText.post(new Runnable() {
            @Override
            public void run() {
                performLogin();
            }
        });
    }

    private void saveCredentialsIfRememberMe(String domain, String userId, String password) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (rememberMeRadio.isChecked()) {
            editor.putBoolean(KEY_REMEMBER_ME, true)
                    .putString(KEY_SAVED_DOMAIN, domain)
                    .putString(KEY_SAVED_USER_ID, userId)
                    .putString(KEY_SAVED_PASSWORD, password)
                    .apply();
        } else {
            editor.remove(KEY_REMEMBER_ME).remove(KEY_SAVED_DOMAIN)
                    .remove(KEY_SAVED_USER_ID).remove(KEY_SAVED_PASSWORD).apply();
        }
    }

    private void setupPasswordToggle() {
        passwordToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isPasswordVisible) {
                    // Hide password
                    passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                    passwordToggle.setImageResource(R.drawable.ic_visibility_off);
                    isPasswordVisible = false;
                } else {
                    // Show password
                    passwordEditText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                    passwordToggle.setImageResource(R.drawable.ic_visibility);
                    isPasswordVisible = true;
                }
            }
        });
    }

    private void setupLoginButton() {
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Hide blur overlay if it's visible
                hideBlurOverlay();
                performLogin();
            }
        });
    }

    private void performLogin() {
        // Check if spinner has a selected item
        if (domainSpinner.getSelectedItem() == null) {
            Toast.makeText(this, "Please select a domain", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String selectedDomain = domainSpinner.getSelectedItem().toString();
        String emailOrUserId = userIdEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Validation
        if (selectedDomain.equals("---------------") || selectedDomain.isEmpty()) {
            Toast.makeText(this, "Please select a domain", Toast.LENGTH_SHORT).show();
            return;
        }

        if (emailOrUserId.isEmpty()) {
            userIdEditText.setError("Please enter your email / User ID");
            userIdEditText.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            passwordEditText.setError("Please enter your password");
            passwordEditText.requestFocus();
            return;
        }

        // Hide blur overlay before navigation
        hideBlurOverlay();

        // Employee: validate against Firestore (employees collection: email + defaultPassword)
        if (selectedDomain.equalsIgnoreCase("Employee")) {
            loginAsEmployee(emailOrUserId, password);
            return;
        }

        // Manager: credentials + Firestore assignedTeamMemberIds must have at least one member
        if (selectedDomain.equalsIgnoreCase("Manager")) {
            loginAsManager(emailOrUserId, password);
            return;
        }

        boolean rememberMe = rememberMeRadio.isChecked();
        String message = "Login attempt:\nDomain: " + selectedDomain + "\nUser ID: " + emailOrUserId + "\nRemember Me: " + (rememberMe ? "Yes" : "No");
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void loginAsEmployee(String email, String password) {
        loginButton.setEnabled(false);
        loginButton.setText("Signing in...");

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("employees")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    loginButton.setEnabled(true);
                    loginButton.setText(R.string.login_button);

                    if (!task.isSuccessful()) {
                        Toast.makeText(MainActivity.this, "Login failed. Check your connection.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    QuerySnapshot snapshot = task.getResult();
                    if (snapshot == null || snapshot.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Invalid email or password", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    DocumentSnapshot employeeDoc = snapshot.getDocuments().get(0);
                    String defaultPassword = employeeDoc.getString("defaultPassword");
                    if (defaultPassword == null || !defaultPassword.equals(password)) {
                        Toast.makeText(MainActivity.this, "Invalid email or password", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String employeeName = employeeDoc.getString("salesPersonName");
                    if (employeeName == null || employeeName.isEmpty()) {
                        employeeName = email;
                    }

                    String employeeId = employeeDoc.getId();

                    saveCredentialsIfRememberMe("Employee", email, password);

                    // Save logged-in employee (email, name, and Firestore doc ID for tasks)
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit()
                            .putString("logged_in_employee_email", email)
                            .putString("logged_in_employee_name", employeeName)
                            .putString("logged_in_employee_id", employeeId != null ? employeeId : "")
                            .apply();

                    Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
                    intent.putExtra("EMPLOYEE_NAME", employeeName);
                    intent.putExtra("IS_MANAGER", false);
                    startActivity(intent);
                    finish();
                });
    }

    /**
     * Manager login: same email/password as employees doc, plus {@code assignedTeamMemberIds}
     * must list at least one team member (Firestore employees collection).
     */
    private void loginAsManager(String email, String password) {
        loginButton.setEnabled(false);
        loginButton.setText("Signing in...");

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("employees")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    loginButton.setEnabled(true);
                    loginButton.setText(R.string.login_button);

                    if (!task.isSuccessful()) {
                        Toast.makeText(MainActivity.this, "Login failed. Check your connection.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    QuerySnapshot snapshot = task.getResult();
                    if (snapshot == null || snapshot.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Invalid email or password", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    DocumentSnapshot employeeDoc = snapshot.getDocuments().get(0);
                    String defaultPassword = employeeDoc.getString("defaultPassword");
                    if (defaultPassword == null || !defaultPassword.equals(password)) {
                        Toast.makeText(MainActivity.this, "Invalid email or password", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!hasAssignedTeamMembers(employeeDoc)) {
                        Toast.makeText(MainActivity.this,
                                "Manager login is only allowed when you have team members assigned. Ask your admin to add team members to your profile.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    String employeeName = employeeDoc.getString("salesPersonName");
                    if (employeeName == null || employeeName.isEmpty()) {
                        employeeName = email;
                    }
                    String employeeId = employeeDoc.getId();

                    saveCredentialsIfRememberMe("Manager", email, password);

                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit()
                            .putString("logged_in_employee_email", email)
                            .putString("logged_in_employee_name", employeeName)
                            .putString("logged_in_employee_id", employeeId != null ? employeeId : "")
                            .apply();

                    Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
                    intent.putExtra("EMPLOYEE_NAME", employeeName);
                    intent.putExtra("IS_MANAGER", true);
                    startActivity(intent);
                    finish();
                });
    }

    /**
     * True if employees/{doc} has non-empty {@code assignedTeamMemberIds} (array or map of ids).
     */
    private static boolean hasAssignedTeamMembers(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return false;
        Object idsObj = doc.get("assignedTeamMemberIds");
        if (idsObj == null) return false;
        if (idsObj instanceof List) {
            List<?> list = (List<?>) idsObj;
            for (Object o : list) {
                if (o != null && !String.valueOf(o).trim().isEmpty()) {
                    return true;
                }
            }
            return false;
        }
        if (idsObj instanceof Map) {
            for (Object v : ((Map<?, ?>) idsObj).values()) {
                if (v != null && !String.valueOf(v).trim().isEmpty()) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    private void setupAutoScroll() {
        // Auto-scroll when User ID field is focused
        userIdEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    // Delay the scroll to ensure the keyboard is shown
                    userIdEditText.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            scrollView.smoothScrollTo(0, userIdEditText.getTop() - 200);
                        }
                    }, 300);
                }
            }
        });

        // Auto-scroll when Password field is focused
        passwordEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                Log.d(TAG, "Password field focus changed: " + hasFocus);
                if (hasFocus) {
                    // Force scroll immediately
                    scrollView.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(TAG, "Scrolling to top");
                            scrollView.scrollTo(0, 0);
                        }
                    });
                    
                    // Then scroll to password field position
                    passwordEditText.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            int scrollY = passwordEditText.getTop() - 400;
                            Log.d(TAG, "Scrolling to password field, scrollY: " + scrollY);
                            scrollView.scrollTo(0, Math.max(0, scrollY));
                        }
                    }, 100);
                }
            }
        });
    }

    private void setupPasswordTextWatcher() {
        passwordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Do nothing
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Ensure the password field remains visible while typing
                if (passwordEditText.hasFocus()) {
                    passwordEditText.post(new Runnable() {
                        @Override
                        public void run() {
                            // Keep password field visible above keyboard
                            int scrollY = passwordEditText.getTop() - 500;
                            scrollView.scrollTo(0, Math.max(0, scrollY));
                        }
                    });
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Do nothing
            }
        });
    }

    private void setupPasswordClick() {
        passwordEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Force scroll when password field is clicked
                passwordEditText.post(new Runnable() {
                    @Override
                    public void run() {
                        int scrollY = passwordEditText.getTop() - 500;
                        scrollView.scrollTo(0, Math.max(0, scrollY));
                    }
                });
            }
        });
    }

    private void setupKeyboardListener() {
        final View rootView = findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int currentHeight = rootView.getHeight();
                
                if (initialHeight == 0) {
                    initialHeight = currentHeight;
                }
                
                // If height decreased significantly, keyboard is shown
                if (currentHeight < initialHeight - 200) {
                    if (passwordEditText.hasFocus()) {
                        // Keyboard is shown and password field has focus, scroll up
                        passwordEditText.post(new Runnable() {
                            @Override
                            public void run() {
                                int scrollY = passwordEditText.getTop() - 300;
                                scrollView.scrollTo(0, Math.max(0, scrollY));
                            }
                        });
                    }
                }
            }
        });
    }


    private void setupSimpleScroll() {
        // Simple approach: scroll when password field is touched
        passwordEditText.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    Log.d(TAG, "Password field touched - scrolling");
                    // Scroll to show password field
                    scrollView.post(new Runnable() {
                        @Override
                        public void run() {
                            int scrollY = passwordEditText.getTop() - 300;
                            Log.d(TAG, "Scrolling to: " + scrollY);
                            scrollView.scrollTo(0, Math.max(0, scrollY));
                        }
                    });
                }
                return false; // Let the touch event continue
            }
        });
    }

    private void setupPasswordDoneAction() {
        passwordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || 
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && 
                     event.getAction() == KeyEvent.ACTION_DOWN)) {
                    // Hide keyboard
                    hideKeyboard();
                    // Clear focus from password field
                    passwordEditText.clearFocus();
                    // Optionally perform login
                    // performLogin();
                    return true;
                }
                return false;
            }
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    private void showBlurOverlay() {
        if (blurOverlay != null && blurOverlay.getVisibility() != View.VISIBLE) {
            blurOverlay.setVisibility(View.VISIBLE);
            blurOverlay.setAlpha(0f);
            blurOverlay.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .setListener(null);
        }
    }

    private void hideBlurOverlay() {
        if (blurOverlay != null && blurOverlay.getVisibility() == View.VISIBLE) {
            blurOverlay.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            blurOverlay.setVisibility(View.GONE);
                        }
                    });
        }
    }
}