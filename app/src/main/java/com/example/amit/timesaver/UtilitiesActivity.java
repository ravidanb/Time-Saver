package com.example.amit.timesaver;


import android.accounts.AccountManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.icu.util.Calendar;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.codetroopers.betterpickers.radialtimepicker.RadialTimePickerDialogFragment;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.List;


import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;


public class UtilitiesActivity extends BaseActivity implements EasyPermissions.PermissionCallbacks, RadialTimePickerDialogFragment.OnTimeSetListener {


    private static final String TAG = "time.saver.app";
    GoogleAccountCredential mCredential;
    private Button syncWithCalendarButton;
    private TextView setNotificationTime;
    private Switch notificationSwitch;

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = {CalendarScopes.CALENDAR};

    private static final String FRAG_TAG_TIME_PICKER = "Notification Time";

    private ArrayList<Semester> semesters;
    private ArrayList<Course> courses;
    private ArrayList<CourseInstance> courseInstances;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_utilities);

        buildDrawer();
        DrawerLayout.LayoutParams dlp = (DrawerLayout.LayoutParams) findViewById(R.id.utilities_main).getLayoutParams();
        dlp.setMargins(50, 50, 50, 50);

        semesters = Dashboard.getInstance().getSemesters();


        syncWithCalendarButton = findViewById(R.id.utilities_calendar_sync_button);
        setNotificationTime = findViewById(R.id.utilities_notification_time_set);
        notificationSwitch = findViewById(R.id.utilities_notification_switch);

        syncWithCalendarButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                syncWithCalendarButton.setEnabled(false);
                getResultsFromApi();
                syncWithCalendarButton.setEnabled(true);
            }
        });

        setNotificationTime(getNotificationHour(), getNotificationMinute());
        setNotificationTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RadialTimePickerDialogFragment rtpd = new RadialTimePickerDialogFragment()
                        .setOnTimeSetListener(UtilitiesActivity.this);
                rtpd.show(getSupportFragmentManager(), FRAG_TAG_TIME_PICKER);
            }
        });

        notificationSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });

        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());

    }


    @Override
    public void onTimeSet(RadialTimePickerDialogFragment dialog, int hourOfDay, int minute) {
        setNotificationTime(hourOfDay, minute);
    }

    private void setNotificationTime(int hour, int minute) {
        String hourString = hour < 10 ? "0" + hour : "" + hour;
        String minuteString = minute < 10 ? "0" + minute : "" + minute;

        setNotificationTime.setText(hourString + ":" + minuteString);

        setNotificationHour(hour);
        setNotificationMinute(minute);
    }

    private void getResultsFromApi() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (!isDeviceOnline()) {
            Toast.makeText(getApplicationContext(), "No network connection available.", Toast.LENGTH_LONG).show();
        } else {

            new MakeRequestTask(mCredential).execute();
        }
    }

    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, android.Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    android.Manifest.permission.GET_ACCOUNTS);
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    Toast.makeText(getApplicationContext(),
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.", Toast.LENGTH_LONG).show();
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;

        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }

    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                UtilitiesActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }


    /**
     * An asynchronous task that handles the Google Calendar API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {

        private com.google.api.services.calendar.Calendar mService = null;
        private Exception mLastError = null;

        MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.calendar.Calendar.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Google Calendar API Android Quickstart")
                    .build();
        }


        @Override
        protected List<String> doInBackground(Void... params) {
            try {
                syncToCalendar();
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        private List<String> getDataFromApi() throws IOException {
            // List the next 10 events from the primary calendar.
            DateTime now = new DateTime(System.currentTimeMillis());
            List<String> eventStrings = new ArrayList<>();

            Events events = mService.events().list("primary")
                    .setMaxResults(10)
                    .setTimeMin(now)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();
            List<Event> items = events.getItems();

            for (Event event : items) {
                DateTime start = event.getStart().getDateTime();
                if (start == null) {
                    // All-day events don't have start times, so just use
                    // the start date.
                    start = event.getStart().getDate();
                }
                eventStrings.add(
                        String.format("%s (%s)", event.getSummary(), start));
            }
            return eventStrings;
        }

        @Override
        protected void onPreExecute() {
            syncWithCalendarButton.setEnabled(false);
            syncWithCalendarButton.setClickable(false);
        }

        @Override
        protected void onPostExecute(List<String> output) {

            if (output == null || output.size() == 0) {
                Toast.makeText(getApplicationContext(), "No results returned.", Toast.LENGTH_LONG).show();
            } else {
                output.add(0, "Data retrieved using the Google Calendar API:");
                //mOutputText.setText(TextUtils.join("\n", output));
            }
            syncWithCalendarButton.setClickable(true);
            syncWithCalendarButton.setEnabled(true);
        }

        @Override
        protected void onCancelled() {
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            UtilitiesActivity.REQUEST_AUTHORIZATION);
                } else {
                    Toast.makeText(getApplicationContext(), "The following error occurred:\n"
                            + mLastError.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(getApplicationContext(), "Request cancelled.", Toast.LENGTH_SHORT).show();
            }
        }

        private void syncToCalendar() throws IOException {
            Calendar date1 = Calendar.getInstance();
            Calendar date2 = Calendar.getInstance();
            ArrayList<Course> added = new ArrayList<>();
            for (int i = 0; i < semesters.size(); i++) {
                courses = semesters.get(i).getArrayListCourses();
                date1.clear();
                date1.set(semesters.get(i).getStartDate().getYear(), semesters.get(i).getStartDate().getMonth() + 1, semesters.get(i).getStartDate().getDay());
                date2.clear();
                date2.set(semesters.get(i).getEndDate().getYear(), semesters.get(i).getEndDate().getMonth() + 1, semesters.get(i).getEndDate().getDay());
                long diff = date2.getTimeInMillis() - date1.getTimeInMillis();
                long dayCountForSemester = (long) diff / (24 * 60 * 60 * 1000);
                for (int j = 0; j < courses.size(); j++) {
                    courseInstances = courses.get(j).getArrayListCourseInstances();
                    for (int k = 0; k < courseInstances.size(); k++) {
                        String startSemester = createStringFormatForDate(semesters.get(i).getStartDate(),0);
                        String startCourse = createStringFormatForDate(courseInstances.get(k).getDay(),
                                courseInstances.get(k).getStartHour());
                        String endCourse = createStringFormatForDate(courseInstances.get(k).getDay(),
                                courseInstances.get(k).getEndHour());
                        if(!added.contains(courseInstances.get(k).getCourse())) {
                            addEvent(courseInstances.get(k), dayCountForSemester, startCourse, endCourse);
                            added.add(courseInstances.get(k).getCourse());
                        }
                    }
                }
            }

        }

        private String createStringFormatForDate(MyDate date, int hourNMinute) {
            String toReturn = "";
            String month;
            String day;
            String startHour;
            String startMinute;
            int hour = hourNMinute / 100;
            int minute = hourNMinute % 100;

            toReturn += date.getYear();
            month = date.getMonth() + 1 < 10 ? "0" + (date.getMonth() + 1) : "" + (date.getMonth() + 1);
            toReturn += "-" + month + "-";

            day = date.getDay() + 1 < 10 ? "0" + (date.getDay() - 1) : "" + (date.getDay() - 1);
            toReturn += day +"T";

            startHour =  hour < 10 ? "0" + hour : "" + hour;
            toReturn += startHour + ":";

            startMinute = minute < 10 ? "0" + minute : "" + minute;
            toReturn += startMinute + ":00-22:00";

            return toReturn;

        }

        private void addEvent(CourseInstance courseInstance,long dayCountForSemester, String startDate, String endDate) throws IOException {

            Event event = new Event()
                    .setSummary(courseInstance.getCourse().getName())
                    .setDescription(" with " + courseInstance.getProfessorName());

            DateTime startDateTime = new DateTime(startDate);
            EventDateTime start = new EventDateTime()
                    .setDateTime(startDateTime)
                    .setTimeZone("Asia/Jerusalem");
            event.setStart(start);

            DateTime endDateTime = new DateTime(endDate);
            EventDateTime end = new EventDateTime()
                    .setDateTime(endDateTime)
                    .setTimeZone("Asia/Jerusalem");
            event.setEnd(end);

            String count = String.valueOf(dayCountForSemester/7);
            String[] recurrence = new String[] {"RRULE:FREQ=WEEKLY;COUNT=" + count};
            event.setRecurrence(Arrays.asList(recurrence));


            Event.Reminders reminders = new Event.Reminders()
                    .setUseDefault(true);
            event.setReminders(reminders);


            String calendarId = "primary";

            try {
                mService.events().insert(calendarId, event).execute();
                Toast.makeText(getApplicationContext(), "Calendar Events created successfully!", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Log.d(TAG, "addEvent: ",e.fillInStackTrace());
            }
        }

    }
}