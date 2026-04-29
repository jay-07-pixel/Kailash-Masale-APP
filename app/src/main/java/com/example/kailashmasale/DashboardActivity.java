package com.example.kailashmasale;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DashboardActivity extends AppCompatActivity {

    /** SharedPreferences keys for team attendance (manager view). */
    public static final String PREFS_TEAM_ATTENDANCE = "team_attendance";
    public static final String KEY_TEAM_ATTENDANCE_PRESENT = "present";
    public static final String KEY_TEAM_ATTENDANCE_TOTAL = "total";

    private ImageButton menuButton;
    private TextView employeeName;
    private TextView workingDaysText;
    private TextView daValue;
    private TextView taValue;
    private TextView incentiveValue;
    private LinearLayout checkInOutButton;
    private DrawerLayout drawerLayout;
    private ImageButton fabAdd;
    private LinearLayout dashboardTasksContainer;
    private TextView productivityPercentText;
    private CircularProgressView productivityProgressBar;
    private CircularProgressView targetAchievedProgressBar;
    private TextView targetAchievedPercentText;
    /** Tasks loaded from Firestore for the logged-in user (used for card and dialog). */
    private final List<String> pendingTasksList = new ArrayList<>();
    /** Live Firestore listener for assigned pending tasks. */
    private ListenerRegistration pendingTasksListener;
    /** Single Expenditure doc listener updates both DA and TA dashboard cards. */
    private ListenerRegistration expenditureCardsListener;
    private ListenerRegistration productivityListener;
    private ListenerRegistration targetAchievedListener;
    private ListenerRegistration workingDaysAssignedListener;
    private ListenerRegistration workingDaysWorkedListener;
    private ListenerRegistration teamAttendanceListener;
    private final Set<String> managerTeamEmails = new HashSet<>();
    private int liveDaysAssigned = 0;
    private int liveDaysWorked = 0;

    private Dialog currentUploadStockDialog;
    private Uri currentStockImageUri;
    private File currentStockPhotoFile;
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private boolean pendingTakePhoto = false;

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
        
        setContentView(R.layout.activity_dashboard);

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                        currentStockImageUri = result.getData().getData();
                        currentStockPhotoFile = null;
                        updateStockImagePreview();
                    }
                });
        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    if (success && currentStockPhotoFile != null) {
                        currentStockImageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", currentStockPhotoFile);
                        updateStockImagePreview();
                    }
                });
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted && pendingTakePhoto) {
                        pendingTakePhoto = false;
                        launchCamera();
                    } else if (!granted) {
                        Toast.makeText(this, "Camera permission is needed to take a photo", Toast.LENGTH_SHORT).show();
                    }
                });

        initializeViews();
        setupClickListeners();
        setupNavigationDrawer();
        loadEmployeeData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startLiveDashboardListeners();
    }

    @Override
    protected void onStop() {
        super.onStop();
        clearLiveDashboardListeners();
    }

    private void clearLiveDashboardListeners() {
        if (pendingTasksListener != null) {
            pendingTasksListener.remove();
            pendingTasksListener = null;
        }
        if (expenditureCardsListener != null) {
            expenditureCardsListener.remove();
            expenditureCardsListener = null;
        }
        if (productivityListener != null) {
            productivityListener.remove();
            productivityListener = null;
        }
        if (targetAchievedListener != null) {
            targetAchievedListener.remove();
            targetAchievedListener = null;
        }
        if (workingDaysAssignedListener != null) {
            workingDaysAssignedListener.remove();
            workingDaysAssignedListener = null;
        }
        if (workingDaysWorkedListener != null) {
            workingDaysWorkedListener.remove();
            workingDaysWorkedListener = null;
        }
        if (teamAttendanceListener != null) {
            teamAttendanceListener.remove();
            teamAttendanceListener = null;
        }
    }

    private void initializeViews() {
        menuButton = findViewById(R.id.menu_button);
        employeeName = findViewById(R.id.employee_name);
        workingDaysText = findViewById(R.id.working_days_text);
        daValue = findViewById(R.id.da_value);
        taValue = findViewById(R.id.ta_value);
        incentiveValue = findViewById(R.id.incentive_value);
        checkInOutButton = findViewById(R.id.check_in_out_button);
        drawerLayout = findViewById(R.id.drawer_layout);
        fabAdd = findViewById(R.id.fab_add);
        dashboardTasksContainer = findViewById(R.id.dashboard_tasks_container);
        productivityPercentText = findViewById(R.id.productivity_percent_text);
        productivityProgressBar = findViewById(R.id.productivity_progress_bar);
        targetAchievedProgressBar = findViewById(R.id.target_achieved_progress_bar);
        targetAchievedPercentText = findViewById(R.id.target_achieved_percent_text);
    }

    private void setupClickListeners() {
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (drawerLayout != null) {
                    drawerLayout.openDrawer(android.view.Gravity.START);
                }
            }
        });

        View notificationButton = findViewById(R.id.notification_button);
        if (notificationButton != null) {
            notificationButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showNotificationsDialog();
                }
            });
        }

        checkInOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, CheckInOutActivity.class);
                startActivity(intent);
            }
        });

        findViewById(R.id.pending_tasks_card).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPendingTasksDialog();
            }
        });

        findViewById(R.id.target_achieved_card).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, TargetAchievedActivity.class);
                startActivity(intent);
            }
        });

        findViewById(R.id.productivity_card).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, ProductivityActivity.class);
                startActivity(intent);
            }
        });

        findViewById(R.id.working_days_card).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, WorkingDaysActivity.class);
                startActivity(intent);
            }
        });

        findViewById(R.id.da_card).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, DAActivity.class);
                startActivity(intent);
            }
        });

        findViewById(R.id.ta_card).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, TAActivity.class);
                startActivity(intent);
            }
        });

        findViewById(R.id.incentive_card).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(DashboardActivity.this, "Incentive Details", Toast.LENGTH_SHORT).show();
                // TODO: Open incentive details
            }
        });

        findViewById(R.id.team_attendance_card).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, AttendanceActivity.class);
                startActivity(intent);
            }
        });

        findViewById(R.id.grid_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, WeeklyPlannerActivity.class);
                startActivity(intent);
            }
        });

        findViewById(R.id.upload_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showUploadStockDialog();
            }
        });

        fabAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, AddOrdersActivity.class);
                startActivity(intent);
            }
        });
    }

    private void loadEmployeeData() {
        // Get employee name from intent or shared preferences
        SharedPreferences userPrefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String name = getIntent().getStringExtra("EMPLOYEE_NAME");
        if (name == null || name.isEmpty()) {
            name = userPrefs.getString("logged_in_employee_name", "");
        }
        if (name != null && !name.isEmpty()) {
            employeeName.setText(name);
        } else {
            employeeName.setText("Employee");
        }

        startLiveDashboardListeners();

        // My Team Attendance: visible only for manager; show attendance that has been added
        boolean isManager = getIntent().getBooleanExtra("IS_MANAGER", false);
        View teamAttendanceCard = findViewById(R.id.team_attendance_card);
        if (teamAttendanceCard != null) {
            teamAttendanceCard.setVisibility(isManager ? View.VISIBLE : View.GONE);
        }
        TextView teamAttendanceText = findViewById(R.id.team_attendance_text);
        if (teamAttendanceText != null && isManager) {
            teamAttendanceText.setText("0/0");
        }

        // DA / TA: live from Expenditure in onResume (startLiveExpenditureCardsListener).
        incentiveValue.setText("5555");
    }

    private void startLiveDashboardListeners() {
        loadPendingTasksFromFirestore();
        startLiveExpenditureCardsListener();
        startLiveProductivityListener();
        startLiveTargetAchievedListener();
        startLiveWorkingDaysListeners();
        startLiveTeamAttendanceListener();
    }

    private boolean isCurrentUserManager() {
        return getIntent().getBooleanExtra("IS_MANAGER", false);
    }

    private void setTeamAttendanceText(int present, int total) {
        TextView teamAttendanceText = findViewById(R.id.team_attendance_text);
        if (teamAttendanceText != null) {
            teamAttendanceText.setText(Math.max(0, present) + "/" + Math.max(0, total));
        }
    }

    /** Live manager card count: present team members today / total assigned team members. */
    private void startLiveTeamAttendanceListener() {
        if (!isCurrentUserManager()) return;
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String managerId = prefs.getString("logged_in_employee_id", "");
        if (managerId.isEmpty()) {
            setTeamAttendanceText(0, 0);
            return;
        }
        FirebaseFirestore.getInstance().collection("employees").document(managerId)
                .get()
                .addOnSuccessListener(managerDoc -> {
                    if (managerDoc == null || !managerDoc.exists()) {
                        setTeamAttendanceText(0, 0);
                        return;
                    }
                    Object idsObj = managerDoc.get("assignedTeamMemberIds");
                    List<String> memberIds = new ArrayList<>();
                    if (idsObj instanceof List) {
                        for (Object o : (List<?>) idsObj) {
                            if (o != null) {
                                String id = o.toString().trim();
                                if (!id.isEmpty()) memberIds.add(id);
                            }
                        }
                    } else if (idsObj instanceof Map) {
                        for (Object v : ((Map<?, ?>) idsObj).values()) {
                            if (v != null) {
                                String id = v.toString().trim();
                                if (!id.isEmpty()) memberIds.add(id);
                            }
                        }
                    }
                    if (memberIds.isEmpty()) {
                        managerTeamEmails.clear();
                        setTeamAttendanceText(0, 0);
                        if (teamAttendanceListener != null) {
                            teamAttendanceListener.remove();
                            teamAttendanceListener = null;
                        }
                        return;
                    }
                    final int total = memberIds.size();
                    setTeamAttendanceText(0, total);
                    fetchTeamEmailsAndAttachAttendanceListener(memberIds, total);
                })
                .addOnFailureListener(e -> setTeamAttendanceText(0, 0));
    }

    private void fetchTeamEmailsAndAttachAttendanceListener(List<String> memberIds, int totalMembers) {
        List<List<String>> chunks = chunkList(memberIds, 10);
        final int[] pending = {chunks.size()};
        final Set<String> loadedEmails = new HashSet<>();

        for (List<String> chunk : chunks) {
            FirebaseFirestore.getInstance().collection("employees")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get()
                    .addOnSuccessListener(snap -> {
                        if (snap != null) {
                            for (DocumentSnapshot doc : snap.getDocuments()) {
                                String email = doc.getString("email");
                                if (email != null && !email.trim().isEmpty()) {
                                    loadedEmails.add(email.trim());
                                }
                            }
                        }
                        pending[0]--;
                        if (pending[0] == 0) {
                            attachAttendanceDateListener(loadedEmails, totalMembers);
                        }
                    })
                    .addOnFailureListener(e -> {
                        pending[0]--;
                        if (pending[0] == 0) {
                            attachAttendanceDateListener(loadedEmails, totalMembers);
                        }
                    });
        }
    }

    private void attachAttendanceDateListener(Set<String> teamEmails, int totalMembers) {
        managerTeamEmails.clear();
        managerTeamEmails.addAll(teamEmails);
        if (teamAttendanceListener != null) {
            teamAttendanceListener.remove();
            teamAttendanceListener = null;
        }
        if (managerTeamEmails.isEmpty()) {
            setTeamAttendanceText(0, totalMembers);
            return;
        }
        String today = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
        teamAttendanceListener = FirebaseFirestore.getInstance().collection("check_ins")
                .whereEqualTo("date", today)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null) {
                        setTeamAttendanceText(0, totalMembers);
                        return;
                    }
                    Set<String> presentEmails = new HashSet<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String email = doc.getString("employeeEmail");
                        Object loc = doc.get("checkInLocation");
                        if (email != null && managerTeamEmails.contains(email) && loc instanceof com.google.firebase.firestore.GeoPoint) {
                            presentEmails.add(email);
                        }
                    }
                    setTeamAttendanceText(presentEmails.size(), totalMembers);
                });
    }

    private static <T> List<List<T>> chunkList(List<T> list, int chunkSize) {
        List<List<T>> chunks = new ArrayList<>();
        if (list == null || list.isEmpty() || chunkSize <= 0) return chunks;
        int i = 0;
        while (i < list.size()) {
            int end = Math.min(i + chunkSize, list.size());
            chunks.add(new ArrayList<>(list.subList(i, end)));
            i = end;
        }
        return chunks;
    }

    /** Current month totals from Expenditure (same doc as TA / DA screens): sum of display amounts. */
    private void startLiveExpenditureCardsListener() {
        if (daValue == null && taValue == null) return;
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        if (employeeEmail.isEmpty()) {
            runOnUiThread(() -> {
                if (daValue != null) daValue.setText("0");
                if (taValue != null) taValue.setText("0");
            });
            return;
        }
        Calendar cal = Calendar.getInstance(Locale.getDefault());
        String yearStr = String.valueOf(cal.get(Calendar.YEAR));
        String monthAbbr = MONTH_ABBR[cal.get(Calendar.MONTH)];
        String docId = yearStr + "_" + monthAbbr + "_" + employeeEmail;
        if (expenditureCardsListener != null) expenditureCardsListener.remove();
        expenditureCardsListener = FirebaseFirestore.getInstance().collection("Expenditure")
                .document(docId)
                .addSnapshotListener((doc, e) -> {
                    int totalDa = 0;
                    int totalTa = 0;
                    if (e == null && doc != null && doc.exists()) {
                        Map<String, Object> data = doc.getData();
                        if (data != null) {
                            totalDa = ExpenditureTaUtils.sumAllDaFromExpenditureData(data);
                            totalTa = ExpenditureTaUtils.sumAllTaFromExpenditureData(data);
                        }
                    }
                    final int daFinal = totalDa;
                    final int taFinal = totalTa;
                    runOnUiThread(() -> {
                        if (daValue != null) daValue.setText(String.valueOf(daFinal));
                        if (taValue != null) taValue.setText(String.valueOf(taFinal));
                    });
                });
    }

    private void startLiveProductivityListener() {
        if (productivityPercentText == null) return;
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        if (employeeEmail.isEmpty()) {
            if (productivityPercentText != null) productivityPercentText.setText("0%");
            if (productivityProgressBar != null) productivityProgressBar.setProgress(0);
            return;
        }
        Calendar cal = Calendar.getInstance(Locale.getDefault());
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        if (productivityListener != null) productivityListener.remove();
        productivityListener = FirebaseFirestore.getInstance().collection("check_outs")
                .whereEqualTo("employeeEmail", employeeEmail)
                .addSnapshotListener((snapshot, e) -> {
                    int totalTC = 0;
                    int totalPC = 0;
                    if (e == null && snapshot != null) {
                        SimpleDateFormat parseFmt = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                        Calendar temp = Calendar.getInstance(Locale.getDefault());
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            String dateStr = doc.getString("date");
                            if (dateStr == null) continue;
                            try {
                                Date d = parseFmt.parse(dateStr);
                                if (d == null) continue;
                                temp.setTime(d);
                                if (temp.get(Calendar.YEAR) == year && temp.get(Calendar.MONTH) == month) {
                                    totalTC += parseInt(doc.get("totalCalls"), 0);
                                    totalPC += parseInt(doc.get("productiveCalls"), 0);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    double percent = (totalTC > 0) ? (100.0 * totalPC / totalTC) : 0;
                    int progress = (int) Math.round(Math.min(100, Math.max(0, percent)));
                    String text = (totalTC > 0) ? String.format(Locale.getDefault(), "%.0f%%", percent) : "0%";
                    runOnUiThread(() -> {
                        if (productivityPercentText != null) productivityPercentText.setText(text);
                        if (productivityProgressBar != null) productivityProgressBar.setProgress(progress);
                    });
                });
    }

    private void startLiveTargetAchievedListener() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeId = prefs.getString("logged_in_employee_id", "");
        if (employeeId.isEmpty()) return;
        Calendar cal = Calendar.getInstance(Locale.getDefault());
        String yearStr = String.valueOf(cal.get(Calendar.YEAR));
        String monthAbbr = MONTH_ABBR[cal.get(Calendar.MONTH)];
        String docId = yearStr + "_" + monthAbbr + "_" + employeeId;
        if (targetAchievedListener != null) targetAchievedListener.remove();
        targetAchievedListener = FirebaseFirestore.getInstance().collection("performance").document(docId)
                .addSnapshotListener((doc, e) -> {
                    double achieved = 0;
                    double target = 1;
                    if (e == null && doc != null && doc.exists()) {
                        Object a = doc.get("achieved");
                        Object t = doc.get("target");
                        if (a instanceof Number) achieved = ((Number) a).doubleValue();
                        if (t instanceof Number) target = ((Number) t).doubleValue();
                    }
                    double percent = (target > 0) ? (100.0 * achieved / target) : 0;
                    int progress = (int) Math.round(Math.min(100, Math.max(0, percent)));
                    String text = (target > 0) ? String.format(Locale.getDefault(), "%.1f%%", percent) : "0%";
                    runOnUiThread(() -> {
                        if (targetAchievedPercentText != null) targetAchievedPercentText.setText(text);
                        if (targetAchievedProgressBar != null) targetAchievedProgressBar.setProgress(progress);
                    });
                });
    }

    private void startLiveWorkingDaysListeners() {
        if (workingDaysText == null) return;
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeId = prefs.getString("logged_in_employee_id", "");
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        if (employeeId.isEmpty() || employeeEmail.isEmpty()) {
            workingDaysText.setText("00/00");
            return;
        }
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        String monthAbbr = (month >= 0 && month < MONTH_ABBR.length) ? MONTH_ABBR[month] : String.valueOf(month + 1);
        String monthlyDataDocId = employeeId + "_" + year + "_" + monthAbbr;

        if (workingDaysAssignedListener != null) workingDaysAssignedListener.remove();
        if (workingDaysWorkedListener != null) workingDaysWorkedListener.remove();

        workingDaysAssignedListener = FirebaseFirestore.getInstance().collection("monthlyData").document(monthlyDataDocId)
                .addSnapshotListener((monthDoc, e) -> {
                    int assigned = 0;
                    if (e == null && monthDoc != null && monthDoc.exists()) {
                        try {
                            Object dd = monthDoc.get("distributorDetails");
                            if (dd instanceof Map) {
                                Map<?, ?> distributorDetails = (Map<?, ?>) dd;
                                for (Object details : distributorDetails.values()) {
                                    if (details instanceof Map) {
                                        Object wd = ((Map<?, ?>) details).get("workingDays");
                                        if (wd != null) {
                                            String s = wd.toString().trim();
                                            if (!s.isEmpty() && !s.equals("000")) {
                                                assigned += Integer.parseInt(s);
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    liveDaysAssigned = assigned;
                    runOnUiThread(() -> {
                        if (workingDaysText != null) workingDaysText.setText(liveDaysWorked + "/" + liveDaysAssigned);
                    });
                });

        workingDaysWorkedListener = FirebaseFirestore.getInstance().collection("check_ins")
                .whereEqualTo("employeeEmail", employeeEmail)
                .addSnapshotListener((snapshot, e) -> {
                    int worked = 0;
                    if (e == null && snapshot != null) {
                        Set<String> uniqueDates = new HashSet<>();
                        SimpleDateFormat parseFmt = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                        Calendar temp = Calendar.getInstance();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            String dateStr = doc.getString("date");
                            if (dateStr == null || dateStr.isEmpty()) continue;
                            try {
                                Date d = parseFmt.parse(dateStr);
                                if (d != null) {
                                    temp.setTime(d);
                                    if (temp.get(Calendar.YEAR) == year && temp.get(Calendar.MONTH) == month) {
                                        uniqueDates.add(dateStr);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        worked = uniqueDates.size();
                    }
                    liveDaysWorked = worked;
                    runOnUiThread(() -> {
                        if (workingDaysText != null) workingDaysText.setText(liveDaysWorked + "/" + liveDaysAssigned);
                    });
                });
    }

    /** Working Days card: show days worked / days assigned for current month (from check_ins + monthlyData). */
    private static final String[] MONTH_ABBR = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private void loadWorkingDaysForDashboard() {
        if (workingDaysText == null) return;
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeId = prefs.getString("logged_in_employee_id", "");
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        if (employeeId.isEmpty() || employeeEmail.isEmpty()) {
            workingDaysText.setText("00/00");
            return;
        }
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        String monthAbbr = (month >= 0 && month < MONTH_ABBR.length) ? MONTH_ABBR[month] : String.valueOf(month + 1);
        String monthlyDataDocId = employeeId + "_" + year + "_" + monthAbbr;
        final int[] daysAssignedSum = {0};
        final int[] daysWorkedCount = {0};
        final int[] pending = {2};
        Runnable updateUi = () -> {
            if (pending[0] > 0) return;
            runOnUiThread(() -> {
                if (workingDaysText != null) {
                    workingDaysText.setText(daysWorkedCount[0] + "/" + daysAssignedSum[0]);
                }
            });
        };

        FirebaseFirestore.getInstance().collection("monthlyData").document(monthlyDataDocId).get()
                .addOnSuccessListener(monthDoc -> {
                    if (monthDoc != null && monthDoc.exists()) {
                        try {
                            Object dd = monthDoc.get("distributorDetails");
                            if (dd instanceof Map) {
                                Map<?, ?> distributorDetails = (Map<?, ?>) dd;
                                for (Object details : distributorDetails.values()) {
                                    if (details instanceof Map) {
                                        Object wd = ((Map<?, ?>) details).get("workingDays");
                                        if (wd != null) {
                                            String s = wd.toString().trim();
                                            if (!s.isEmpty() && !s.equals("000")) {
                                                try {
                                                    daysAssignedSum[0] += Integer.parseInt(s);
                                                } catch (NumberFormatException ignored) {}
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    pending[0]--;
                    updateUi.run();
                })
                .addOnFailureListener(e -> { pending[0]--; updateUi.run(); });

        SimpleDateFormat parseFmt = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
        FirebaseFirestore.getInstance().collection("check_ins")
                .whereEqualTo("employeeEmail", employeeEmail)
                .get()
                .addOnSuccessListener(snapshot -> {
                    Set<String> uniqueDates = new HashSet<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            String dateStr = doc.getString("date");
                            if (dateStr == null || dateStr.isEmpty()) continue;
                            try {
                                Date d = parseFmt.parse(dateStr);
                                if (d != null) {
                                    cal.setTime(d);
                                    if (cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month) {
                                        uniqueDates.add(dateStr);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    daysWorkedCount[0] = uniqueDates.size();
                    pending[0]--;
                    updateUi.run();
                })
                .addOnFailureListener(e -> { pending[0]--; updateUi.run(); });
    }

    /**
     * Loads tasks assigned to the logged-in user from Firestore "tasks" collection
     * and fills the Pending Tasks card and list for the dialog.
     */
    private void loadPendingTasksFromFirestore() {
        SharedPreferences userPrefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeId = userPrefs.getString("logged_in_employee_id", "");
        if (pendingTasksListener != null) {
            pendingTasksListener.remove();
            pendingTasksListener = null;
        }
        if (employeeId.isEmpty()) {
            pendingTasksList.clear();
            refreshDashboardTasksUi();
            return;
        }

        pendingTasksListener = FirebaseFirestore.getInstance().collection("tasks")
                .whereEqualTo("employeeId", employeeId)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        pendingTasksList.clear();
                        refreshDashboardTasksUi();
                        return;
                    }
                    pendingTasksList.clear();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            String title = doc.getString("title");
                            String description = doc.getString("description");
                            String text = (title != null && !title.trim().isEmpty())
                                    ? title.trim()
                                    : (description != null && !description.trim().isEmpty())
                                            ? description.trim()
                                            : "Task";
                            pendingTasksList.add(text);
                        }
                    }
                    refreshDashboardTasksUi();
                });
    }

    /**
     * Load productivity for current month from check_outs (same data as Productivity page),
     * compute average TC/PC % and show in the Productivity card.
     */
    private void loadProductivityAverage() {
        if (productivityPercentText == null) return;
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        if (employeeEmail.isEmpty()) {
            if (productivityPercentText != null) productivityPercentText.setText("0%");
            if (productivityProgressBar != null) productivityProgressBar.setProgress(0);
            return;
        }
        Calendar cal = Calendar.getInstance(Locale.getDefault());
        String yearStr = String.valueOf(cal.get(Calendar.YEAR));
        int month1Based = cal.get(Calendar.MONTH) + 1;
        cal.set(Calendar.DAY_OF_MONTH, 1);
        String firstDay = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.getTime());
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        String lastDay = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(cal.getTime());

        FirebaseFirestore.getInstance().collection("check_outs")
                .whereEqualTo("employeeEmail", employeeEmail)
                .get()
                .addOnSuccessListener(snapshot -> {
                    int totalTC = 0;
                    int totalPC = 0;
                    if (snapshot != null && !snapshot.isEmpty()) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            String dateStr = doc.getString("date");
                            if (dateStr == null) continue;
                            if (dateStr.compareTo(firstDay) < 0 || dateStr.compareTo(lastDay) > 0) continue;
                            totalTC += parseInt(doc.get("totalCalls"), 0);
                            totalPC += parseInt(doc.get("productiveCalls"), 0);
                        }
                    }
                    double percent = (totalTC > 0) ? (100.0 * totalPC / totalTC) : 0;
                    int progress = (int) Math.round(Math.min(100, Math.max(0, percent)));
                    String text = (totalTC > 0) ? String.format(Locale.getDefault(), "%.0f%%", percent) : "0%";
                    runOnUiThread(() -> {
                        if (productivityPercentText != null) productivityPercentText.setText(text);
                        if (productivityProgressBar != null) productivityProgressBar.setProgress(progress);
                    });
                })
                .addOnFailureListener(e -> runOnUiThread(() -> {
                    if (productivityPercentText != null) productivityPercentText.setText("0%");
                    if (productivityProgressBar != null) productivityProgressBar.setProgress(0);
                }));
    }

    /**
     * Load total achieved from performance collection for current month and show in Target Achieved card
     * (same progress bar style as Productivity). Doc id: year_month_employeeId (e.g. 2026_Mar_YCb...).
     */
    private void loadTargetAchievedFromPerformance() {
        if (targetAchievedProgressBar == null && targetAchievedPercentText == null) return;
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeId = prefs.getString("logged_in_employee_id", "");
        if (employeeId.isEmpty()) {
            runOnUiThread(() -> {
                if (targetAchievedPercentText != null) targetAchievedPercentText.setText("0%");
                if (targetAchievedProgressBar != null) targetAchievedProgressBar.setProgress(0);
            });
            return;
        }
        Calendar cal = Calendar.getInstance(Locale.getDefault());
        String yearStr = String.valueOf(cal.get(Calendar.YEAR));
        String monthAbbr = MONTH_ABBR[cal.get(Calendar.MONTH)];
        String docId = yearStr + "_" + monthAbbr + "_" + employeeId;

        FirebaseFirestore.getInstance().collection("performance").document(docId)
                .get()
                .addOnSuccessListener(doc -> {
                    double achieved = 0;
                    double target = 1;
                    if (doc != null && doc.exists()) {
                        Object a = doc.get("achieved");
                        Object t = doc.get("target");
                        if (a instanceof Number) achieved = ((Number) a).doubleValue();
                        if (t instanceof Number) target = ((Number) t).doubleValue();
                    }
                    double percent = (target > 0) ? (100.0 * achieved / target) : 0;
                    int progress = (int) Math.round(Math.min(100, Math.max(0, percent)));
                    String text = (target > 0) ? String.format(Locale.getDefault(), "%.1f%%", percent) : "0%";
                    runOnUiThread(() -> {
                        if (targetAchievedPercentText != null) targetAchievedPercentText.setText(text);
                        if (targetAchievedProgressBar != null) targetAchievedProgressBar.setProgress(progress);
                    });
                })
                .addOnFailureListener(e -> runOnUiThread(() -> {
                    if (targetAchievedPercentText != null) targetAchievedPercentText.setText("0%");
                    if (targetAchievedProgressBar != null) targetAchievedProgressBar.setProgress(0);
                }));
    }

    private static int parseInt(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (Exception ex) {
            return def;
        }
    }

    private void refreshDashboardTasksUi() {
        if (dashboardTasksContainer == null) return;
        dashboardTasksContainer.removeAllViews();
        for (int i = 0; i < pendingTasksList.size(); i++) {
            View row = getLayoutInflater().inflate(R.layout.task_row_dashboard, dashboardTasksContainer, false);
            TextView number = row.findViewById(R.id.task_row_number);
            TextView description = row.findViewById(R.id.task_row_description);
            number.setText((i + 1) + ".");
            description.setText(pendingTasksList.get(i));
            dashboardTasksContainer.addView(row);
        }
    }

    /**
     * Call this when team attendance is added (e.g. from Monthly Attendance).
     * Updates stored present/total so manager dashboard shows the added attendance.
     */
    public static void saveTeamAttendanceAdded(Context context, int present, int total) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_TEAM_ATTENDANCE, Context.MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_TEAM_ATTENDANCE_PRESENT, present)
                .putInt(KEY_TEAM_ATTENDANCE_TOTAL, total)
                .apply();
    }

    /**
     * Increment team attendance by one (one more marked present out of one more total).
     * Call when user adds an attendance entry.
     */
    public static void incrementTeamAttendance(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_TEAM_ATTENDANCE, Context.MODE_PRIVATE);
        int present = prefs.getInt(KEY_TEAM_ATTENDANCE_PRESENT, 0);
        int total = prefs.getInt(KEY_TEAM_ATTENDANCE_TOTAL, 0);
        saveTeamAttendanceAdded(context, present + 1, total + 1);
    }

    private void setupNavigationDrawer() {
        // Close drawer button
        ImageButton closeButton = findViewById(R.id.drawer_close_button);
        if (closeButton != null) {
            closeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    drawerLayout.closeDrawer(android.view.Gravity.START);
                }
            });
        }

        // Edit profile button
        ImageButton editProfileButton = findViewById(R.id.edit_profile_button);
        if (editProfileButton != null) {
            editProfileButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(DashboardActivity.this, ProfileActivity.class);
                    String userName = getIntent().getStringExtra("EMPLOYEE_NAME");
                    if (userName != null) {
                        intent.putExtra("EMPLOYEE_NAME", userName);
                    }
                    startActivity(intent);
                    drawerLayout.closeDrawer(android.view.Gravity.START);
                }
            });
        }

        // Monthly Plan
        findViewById(R.id.menu_monthly_plan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawerLayout.closeDrawer(android.view.Gravity.START);
                Intent intent = new Intent(DashboardActivity.this, MonthlyPlanActivity.class);
                startActivity(intent);
            }
        });

        // Performance
        findViewById(R.id.menu_performance).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, PerformanceActivity.class);
                startActivity(intent);
                drawerLayout.closeDrawer(android.view.Gravity.START);
            }
        });

        // Distributer
        findViewById(R.id.menu_distributer).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, DistributorsActivity.class);
                startActivity(intent);
                drawerLayout.closeDrawer(android.view.Gravity.START);
            }
        });

        // Leave
        View leaveMenu = findViewById(R.id.menu_leave);
        if (leaveMenu != null) {
            leaveMenu.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(DashboardActivity.this, LeaveActivity.class);
                    startActivity(intent);
                    drawerLayout.closeDrawer(android.view.Gravity.START);
                }
            });
        }

        // Log out: clear session and turn off Remember me so next time login shows unchecked unless user checks it again
        findViewById(R.id.menu_logout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(DashboardActivity.this, "Logging out...", Toast.LENGTH_SHORT).show();
                drawerLayout.closeDrawer(android.view.Gravity.START);
                SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
                prefs.edit()
                        .putBoolean("remember_me", false)
                        .remove("saved_domain")
                        .remove("saved_user_id")
                        .remove("saved_password")
                        .remove("logged_in_employee_email")
                        .remove("logged_in_employee_name")
                        .remove("logged_in_employee_id")
                        .apply();
                Intent intent = new Intent(DashboardActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });

        // Set user info in drawer
        TextView drawerUserName = findViewById(R.id.drawer_user_name);
        TextView drawerUserRole = findViewById(R.id.drawer_user_role);
        
        String userName = getIntent().getStringExtra("EMPLOYEE_NAME");
        if (userName != null && !userName.isEmpty() && drawerUserName != null) {
            drawerUserName.setText(userName);
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

        currentStockImageUri = null;
        currentStockPhotoFile = null;
        currentUploadStockDialog = dialog;
        dialog.setOnDismissListener(d -> currentUploadStockDialog = null);

        ImageView imagePreview = dialog.findViewById(R.id.stock_image_preview);
        Button submitButton = dialog.findViewById(R.id.stock_submit_button);
        if (imagePreview != null) imagePreview.setVisibility(View.GONE);
        if (submitButton != null) submitButton.setVisibility(View.GONE);

        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                new ArrayList<String>()
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
        adapter.add("Select distributor");
        distributerSpinner.setAdapter(adapter);
        loadAssignedDistributorsForUpload(adapter, distributerSpinner);

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
                    Animation expandAnim = AnimationUtils.loadAnimation(DashboardActivity.this, R.anim.dropdown_expand);
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
                        Animation collapseAnim = AnimationUtils.loadAnimation(DashboardActivity.this, R.anim.dropdown_collapse);
                        collapseAnim.setAnimationListener(new Animation.AnimationListener() {
                            @Override
                            public void onAnimationStart(Animation animation) {}

                            @Override
                            public void onAnimationEnd(Animation animation) {
                                dropdownContainer.setVisibility(View.GONE);
                                // Border is handled by container
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
                Animation collapseAnim = AnimationUtils.loadAnimation(DashboardActivity.this, R.anim.dropdown_collapse);
                collapseAnim.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {}

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        dropdownContainer.setVisibility(View.GONE);
                        // Border is handled by container
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
                                Animation collapseAnim = AnimationUtils.loadAnimation(DashboardActivity.this, R.anim.dropdown_collapse);
                                collapseAnim.setAnimationListener(new Animation.AnimationListener() {
                                    @Override
                                    public void onAnimationStart(Animation animation) {}

                                    @Override
                                    public void onAnimationEnd(Animation animation) {
                                        dropdownContainer.setVisibility(View.GONE);
                                        // Border is handled by container
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

        uploadButton.setOnClickListener(v -> showImageSourcePicker(dialog, distributerSpinner));
        submitButton.setOnClickListener(v -> submitStockSheet(dialog, distributerSpinner));

        dialog.show();
    }

    private void showImageSourcePicker(Dialog dialog, Spinner distributerSpinner) {
        String distributor = distributerSpinner.getSelectedItem().toString();
        if (distributor.isEmpty() || "Select distributor".equals(distributor)) {
            Toast.makeText(this, "Please select a distributor", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Upload Stock Sheet")
                .setItems(new CharSequence[]{"Choose from Gallery", "Take Photo"}, (d, which) -> {
                    if (which == 0) {
                        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                        pick.setType("image/*");
                        pickImageLauncher.launch(Intent.createChooser(pick, "Select image"));
                    } else {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            pendingTakePhoto = true;
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                        } else {
                            launchCamera();
                        }
                    }
                })
                .show();
    }

    private void launchCamera() {
        try {
            File photoFile = File.createTempFile("stock_", ".jpg", getCacheDir());
            currentStockPhotoFile = photoFile;
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            takePictureLauncher.launch(uri);
        } catch (Exception e) {
            Toast.makeText(this, "Could not create photo file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateStockImagePreview() {
        if (currentUploadStockDialog == null) return;
        runOnUiThread(() -> {
            if (currentUploadStockDialog == null) return;
            ImageView preview = currentUploadStockDialog.findViewById(R.id.stock_image_preview);
            Button submitBtn = currentUploadStockDialog.findViewById(R.id.stock_submit_button);
            if (preview != null && currentStockImageUri != null) {
                preview.setImageURI(currentStockImageUri);
                preview.setVisibility(View.VISIBLE);
            }
            if (submitBtn != null) submitBtn.setVisibility(View.VISIBLE);
        });
    }

    private void submitStockSheet(Dialog dialog, Spinner distributerSpinner) {
        String distributor = distributerSpinner.getSelectedItem().toString();
        if (distributor.isEmpty() || "Select distributor".equals(distributor)) {
            Toast.makeText(this, "Please select a distributor", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentStockImageUri == null) {
            Toast.makeText(this, "Please select or capture an image first", Toast.LENGTH_SHORT).show();
            return;
        }

        Button submitBtn = dialog.findViewById(R.id.stock_submit_button);
        if (submitBtn != null) {
            submitBtn.setText("Uploading...");
            submitBtn.setEnabled(false);
        }
        Toast.makeText(this, "Uploading stock sheet...", Toast.LENGTH_SHORT).show();

        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        String employeeName = prefs.getString("logged_in_employee_name", "");
        String dateStr = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(new Date());
        String timestamp = String.valueOf(System.currentTimeMillis());

        StorageReference ref = FirebaseStorage.getInstance().getReference()
                .child("stock_sheets")
                .child(employeeEmail.replace(".", "_"))
                .child(dateStr + "_" + timestamp + ".jpg");

        UploadTask task = ref.putFile(currentStockImageUri);
        task.addOnSuccessListener(snapshot -> ref.getDownloadUrl().addOnSuccessListener(uri -> {
            Map<String, Object> doc = new HashMap<>();
            doc.put("distributor", distributor);
            doc.put("employeeEmail", employeeEmail);
            doc.put("employeeName", employeeName);
            doc.put("date", dateStr);
            doc.put("downloadUrl", uri.toString());
            doc.put("timestamp", com.google.firebase.firestore.FieldValue.serverTimestamp());
            FirebaseFirestore.getInstance().collection("stock_sheets").add(doc)
                    .addOnSuccessListener(a -> {
                        Toast.makeText(this, "Stock sheet uploaded successfully", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e -> {
                        if (submitBtn != null) {
                            submitBtn.setText("Submit");
                            submitBtn.setEnabled(true);
                        }
                        Toast.makeText(this, "Failed to save: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
        })).addOnFailureListener(e -> {
            if (submitBtn != null) {
                submitBtn.setText("Submit");
                submitBtn.setEnabled(true);
            }
            Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void loadAssignedDistributorsForUpload(ArrayAdapter<String> adapter, Spinner distributerSpinner) {
        SharedPreferences prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE);
        String employeeEmail = prefs.getString("logged_in_employee_email", "");
        if (employeeEmail.isEmpty()) return;

        FirebaseFirestore.getInstance().collection("employees")
                .whereEqualTo("email", employeeEmail)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) return;
                    Object assigned = snapshot.getDocuments().get(0).get("assignedDistributorId");
                    if (assigned == null) assigned = snapshot.getDocuments().get(0).get("assignedDistributorIds");
                    List<String> ids = new ArrayList<>();
                    if (assigned instanceof String) ids.add((String) assigned);
                    else if (assigned instanceof List) {
                        for (Object o : (List<?>) assigned)
                            if (o instanceof String) ids.add((String) o);
                    }
                    if (ids.isEmpty()) return;
                    List<String> names = new ArrayList<>();
                    final int[] fetched = {0};
                    int total = ids.size();
                    for (String id : ids) {
                        FirebaseFirestore.getInstance().collection("distributors").document(id).get()
                                .addOnSuccessListener(doc -> {
                                    if (doc != null && doc.exists()) {
                                        String name = doc.getString("distributorName");
                                        if (name == null) name = doc.getString("distributor_name");
                                        if (name == null) name = doc.getString("name");
                                        if (name == null) name = doc.getId();
                                        synchronized (names) { names.add(name); }
                                    }
                                    synchronized (names) {
                                        fetched[0]++;
                                        if (fetched[0] == total) {
                                            runOnUiThread(() -> {
                                                adapter.clear();
                                                adapter.add("Select distributor");
                                                adapter.addAll(names);
                                                adapter.notifyDataSetChanged();
                                            });
                                        }
                                    }
                                });
                    }
                });
    }

    private void showNotificationsDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_notifications);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.7f);
        }
        dialog.setCancelable(true);
        dialog.show();
    }

    private void showPendingTasksDialog() {
        // Create dialog
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_pending_tasks);
        
        // Set dialog window size to 368dp x 557dp
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.7f); // Dim background
            
            // Convert dp to pixels
            float density = getResources().getDisplayMetrics().density;
            int widthPixels = (int) (368 * density);
            int heightPixels = (int) (557 * density);
            
            WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.width = widthPixels;
            params.height = heightPixels;
            dialog.getWindow().setAttributes(params);
        }

        // Initialize dialog views - use tasks loaded from Firestore for this user
        LinearLayout tasksContainer = dialog.findViewById(R.id.tasks_container);
        tasksContainer.removeAllViews();

        for (int i = 0; i < pendingTasksList.size(); i++) {
            View taskItem = getLayoutInflater().inflate(R.layout.task_item, tasksContainer, false);
            TextView taskNumber = taskItem.findViewById(R.id.task_number);
            TextView taskDescription = taskItem.findViewById(R.id.task_description);
            taskNumber.setText((i + 1) + ".");
            taskDescription.setText(pendingTasksList.get(i));
            tasksContainer.addView(taskItem);
        }

        // Show dialog
        dialog.show();
    }
}










