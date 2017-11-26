package com.nbdeg.unityplanner;

import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.appinvite.AppInviteInvitation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.classroom.model.Course;
import com.google.api.services.classroom.model.CourseWork;
import com.google.api.services.classroom.model.ListCourseWorkResponse;
import com.google.api.services.classroom.model.ListCoursesResponse;
import com.google.api.services.classroom.model.ListStudentSubmissionsResponse;
import com.google.api.services.classroom.model.StudentSubmission;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserInfo;
import com.nbdeg.unityplanner.data.Assignment;
import com.nbdeg.unityplanner.data.Time;
import com.nbdeg.unityplanner.showcase.ShowcaseActivity;
import com.nbdeg.unityplanner.utils.AlarmReceiver;
import com.nbdeg.unityplanner.utils.Database;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class Dashboard extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private LinearLayout syncLayout;

    private GoogleAccountCredential mCredential;

    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;
    static final int REQUEST_INVITE = 4001;

    private static final String[] SCOPES = LauncherLogin.SCOPES;

    @Override
    protected void onStart() {
        super.onStart();

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(Dashboard.this, LauncherLogin.class));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_dashboard);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.dashboard_fab);
        syncLayout = (LinearLayout) findViewById(R.id.dashboard_sync);
        setSupportActionBar(toolbar);

        Database.refreshDatabase(getApplicationContext());

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);


        // Adding Dashboard Fragment as primary fragment
        DashboardFragment dashFrag = new DashboardFragment();

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.dashboard_fragments, dashFrag);
        transaction.addToBackStack(null);

        transaction.commit();

        // Adding Name and E-Mail to Nav
        View hView =  navigationView.getHeaderView(0);
        final ImageView userPhoto = (ImageView)hView.findViewById(R.id.nav_header_photo);
        final TextView userName = (TextView)hView.findViewById(R.id.nav_header_user);
        final TextView userEmail = (TextView)hView.findViewById(R.id.nav_header_email);

        if (Database.getUser().getDisplayName() != null) {
            userName.setText(Database.getUser().getDisplayName());
        }
        if (Database.getUser().getEmail() != null) {
            userEmail.setText(Database.getUser().getEmail());
        }
        if (Database.getUser().getPhotoUrl() != null) {
            Picasso.with(this).load(Database.getUser().getPhotoUrl())
                    .resize(150, 150)
                    .into(userPhoto, new Callback() {
                        @Override
                        public void onSuccess() {
                            Bitmap imageBitmap = ((BitmapDrawable) userPhoto.getDrawable()).getBitmap();
                            RoundedBitmapDrawable imageDrawable = RoundedBitmapDrawableFactory.create(getResources(), imageBitmap);
                            imageDrawable.setCircular(true);
                            imageDrawable.setCornerRadius(Math.max(imageBitmap.getWidth(), imageBitmap.getHeight()) / 2.0f);
                            userPhoto.setImageDrawable(imageDrawable);
                        }

                        @Override
                        public void onError() {
                            userPhoto.setImageResource(R.mipmap.ic_launcher_round);
                        }
                    });
        }

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.support.v4.app.Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.dashboard_fragments);
                if (currentFragment instanceof DashboardFragment || currentFragment instanceof AssignmentList) {
                    startActivity(new Intent(Dashboard.this, CreateAssignment.class));
                } else if (currentFragment instanceof CourseList){
                    startActivity(new Intent(Dashboard.this, CreateCourse.class));
                }
            }
        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("firstTime", true)) {
            onFirstStart(prefs);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        DashboardFragment dashFrag = new DashboardFragment();

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.dashboard_fragments, dashFrag);
        transaction.addToBackStack(null);

        transaction.commit();
    }


    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.dashboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case R.id.action_logout:
                AuthUI.getInstance()
                        .signOut(this);
                startActivity(new Intent(Dashboard.this, LauncherLogin.class));

                break;
            case R.id.action_old_assignments:
                startActivity(new Intent(Dashboard.this, DoneAssignmentList.class));

                break;
            case R.id.action_showcase:
                Intent i = new Intent(Dashboard.this, ShowcaseActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);

                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        switch (id) {
            case R.id.nav_home:
                DashboardFragment dashFrag = new DashboardFragment();
                getSupportFragmentManager().beginTransaction().replace(R.id.dashboard_fragments, dashFrag).addToBackStack(null).commit();

                break;
            case R.id.nav_assignment:
                AssignmentList assignmentFrag = new AssignmentList();
                getSupportFragmentManager().beginTransaction().replace(R.id.dashboard_fragments, assignmentFrag).addToBackStack(null).commit();

                break;
            case R.id.nav_courses:
                CourseList courseFrag = new CourseList();
                getSupportFragmentManager().beginTransaction().replace(R.id.dashboard_fragments, courseFrag).addToBackStack(null).commit();

                break;
            case R.id.nav_sync:
                // Initialize credentials and service object.
                for (UserInfo info : Database.getUser().getProviderData()) {
                    if (info.getProviderId().equals("google.com")) {
                        if (EasyPermissions.hasPermissions(this, android.Manifest.permission.GET_ACCOUNTS)) {
                            signInGoogleCredential();
                            return super.onOptionsItemSelected(item);
                        } else {
                            EasyPermissions.requestPermissions(
                                    this,
                                    "This app needs to access your Google account (via Contacts).",
                                    REQUEST_PERMISSION_GET_ACCOUNTS,
                                    android.Manifest.permission.GET_ACCOUNTS);
                            return super.onOptionsItemSelected(item);
                        }
                    }
                }

                Toast.makeText(this, "Please log in with a Google Education account.", Toast.LENGTH_SHORT).show();

                break;
            case R.id.nav_share:
                Intent intent = new AppInviteInvitation.IntentBuilder(getString(R.string.app_name))
                        .setMessage(getString(R.string.invitation_message))
                        .setCallToActionText(getString(R.string.invitation_cta))
                        .build();
                startActivityForResult(intent, REQUEST_INVITE);

                break;
            case R.id.nav_settings:
                startActivity(new Intent(Dashboard.this, Settings.class));

                break;
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }


    /**
     * Run when dashboard is first launched
     * User WILL be logged in
     *
     * @param prefs Shared preferences containing notification preferences and firstTime boolean
     */
    private void onFirstStart(SharedPreferences prefs) {

        // Notifications
        Intent alarmIntent = new Intent(getApplicationContext(), AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, alarmIntent, 0);

        AlarmManager manager = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);

        Calendar calendar = Calendar.getInstance();
        Calendar hourCal = Calendar.getInstance();
        hourCal.setTimeInMillis(prefs.getLong("notification_time", 90000000));

        calendar.set(Calendar.HOUR_OF_DAY, hourCal.get(Calendar.HOUR_OF_DAY));
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        manager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pendingIntent);

        // Launching showcase tutorial
        Intent i = new Intent(Dashboard.this, ShowcaseActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);

        // Not first time anymore
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("firstTime", false);
        editor.apply();
    }

    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void signInGoogleCredential() {
        mCredential = GoogleAccountCredential.usingOAuth2(
                this, Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
        mCredential.setSelectedAccountName(Database.getUser().getEmail());

        getResultsFromApi();
    }

    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        if (! isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            Toast.makeText(this, "Something went wrong. Please try again.", Toast.LENGTH_LONG).show();
        } else if (! isDeviceOnline()) {
            Toast.makeText(this, "No network connection available.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(Dashboard.this, "Sync Started", Toast.LENGTH_SHORT).show();
            new MakeRequestTask(mCredential).execute();
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    Toast.makeText(this, "This app requires Google Play Services. Please install " +
                            "Google Play Services on your device and relaunch this app.", Toast.LENGTH_SHORT).show();
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }


    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     * @param requestCode The request code passed in
     *     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                Dashboard.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    private int getMatColor(String typeColor) {
        int returnColor = Color.BLACK;
        int arrayId = getResources().getIdentifier("mdcolor_" + typeColor, "array", getApplicationContext().getPackageName());

        if (arrayId != 0)
        {
            TypedArray colors = getResources().obtainTypedArray(arrayId);
            int index = (int) (Math.random() * colors.length());
            returnColor = colors.getColor(index, Color.BLACK);
            colors.recycle();
        }
        return returnColor;
    }

    /**
     * An asynchronous task that handles the Classroom API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, Void> {
        private com.google.api.services.classroom.Classroom mService = null;
        private Exception mLastError = null;

        MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.classroom.Classroom.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Unity Planner")
                    .build();
        }

        /**
         * Background task to call Classroom API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected Void doInBackground(Void... params) {
            try {
                getDataFromApi();
            } catch (Exception e) {
                e.printStackTrace();
                mLastError = e;
                cancel(true);
            }
            return null;
        }

        /**
         * Fetch a list of the names of the first 10 courses the user has access to.
         * @return List course names, or a simple error message if no courses are
         *         found.
         * @throws IOException
         */
        private void getDataFromApi() throws IOException {
            ListCoursesResponse courseResponse = mService.courses().list()
                    .setPageSize(10)
                    .execute();

            ArrayList<com.nbdeg.unityplanner.data.Course> classroomCourses = new ArrayList<>();
            ArrayList<String> courseIDs = new ArrayList<>();
            ArrayList<String> courseWorkIDs = new ArrayList<>();

            for (Assignment assignment : Database.getAssignments()) {
                if (assignment.getClassroomCourseWork() != null) {
                    courseWorkIDs.add(assignment.getClassroomCourseWork().getId());
                }
            }
            for (com.nbdeg.unityplanner.data.Course course : Database.getCourses()) {
                if (course.getClassroomCourse() != null) {
                    classroomCourses.add(course);
                    courseIDs.add(course.getClassroomCourse().getId());
                }
            }

            for (Course course : courseResponse.getCourses()) {
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault());
                long startDate = System.currentTimeMillis();
                try {
                    startDate = f.parse(course.getCreationTime()).getTime();
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                if (course.getCourseState().equals("ACTIVE")) {
                    com.nbdeg.unityplanner.data.Course dbCourse = new com.nbdeg.unityplanner.data.Course(
                            course.getName(),
                            mService.userProfiles().get(course.getOwnerId()).execute().getName().getFullName(),
                            new Time(startDate),
                            course.getRoom(),
                            course.getDescription(),
                            course,
                            getMatColor("200"));
                    if (Database.getChangedCourseNames().containsKey(dbCourse.getName())) {
                        dbCourse.setName(Database.getChangedCourseNames().get(course.getName()));
                    }

                    if (!courseIDs.contains(course.getId())) {
                        // Add class to database
                        Database.createCourse(dbCourse);
                        FirebaseAnalytics.getInstance(getApplicationContext()).logEvent("course_created", null);
                        Log.i("Classroom", "Course Created: " + dbCourse.getName());
                    } else {
                        for (com.nbdeg.unityplanner.data.Course mCourse : classroomCourses) {
                            if (mCourse.getClassroomCourse().getId().equals(course.getId())) {
                                dbCourse = mCourse;
                            }
                        }
                    }

                    // Add assignments to database
                    ListCourseWorkResponse courseworkResponse = mService.courses().courseWork().list(course.getId()).execute();
                    if (courseworkResponse.getCourseWork() != null) {
                        for (CourseWork courseWork : courseworkResponse.getCourseWork()) {
                            if (!courseWorkIDs.contains(courseWork.getId())) {
                                ListStudentSubmissionsResponse studentSubmissionResponse = mService.courses().courseWork().studentSubmissions().list(course.getId(), courseWork.getId()).execute();
                                for (StudentSubmission submission : studentSubmissionResponse.getStudentSubmissions()) {
                                    Calendar cal = Calendar.getInstance();
                                    if (courseWork.getDueDate() != null) {
                                        cal.set(courseWork.getDueDate().getYear(), courseWork.getDueDate().getMonth()-1, courseWork.getDueDate().getDay()-1);
                                    } else {
                                        cal.setTimeInMillis(System.currentTimeMillis());
                                    }
                                    if (submission.getState().equalsIgnoreCase("RETURNED") || submission.getState().equalsIgnoreCase("TURNED_IN")) {
                                        Database.createAssignment(new Assignment(
                                                courseWork.getTitle(),
                                                cal.getTimeInMillis(),
                                                dbCourse,
                                                100,
                                                courseWork.getDescription(),
                                                courseWork), getApplicationContext());
                                        FirebaseAnalytics.getInstance(getApplicationContext()).logEvent("assignment_created", null);
                                        Log.i("Classroom", "Assignment Created: " + courseWork.getTitle());
                                    } else {
                                        Database.createAssignment(new Assignment(
                                                courseWork.getTitle(),
                                                cal.getTimeInMillis(),
                                                dbCourse,
                                                0,
                                                courseWork.getDescription(),
                                                courseWork), getApplicationContext());
                                        FirebaseAnalytics.getInstance(getApplicationContext()).logEvent("assignment_created", null);
                                        Log.i("Classroom", "Assignment Created: " + courseWork.getTitle());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }


        @Override
        protected void onPreExecute() {
            // Start Spinner
            syncLayout.setVisibility(View.VISIBLE);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            // Hide spinner
            syncLayout.setVisibility(View.INVISIBLE);
            Toast.makeText(Dashboard.this, "Sync finished", Toast.LENGTH_SHORT).show();
            Database.refreshDatabase(getApplicationContext());
        }

        @Override
        protected void onCancelled() {
            // Hide spinner
            syncLayout.setVisibility(View.INVISIBLE);
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            REQUEST_AUTHORIZATION);
                } else {
                    Toast.makeText(Dashboard.this, "The following error occurred: " + mLastError.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(Dashboard.this, "Request cancelled.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
