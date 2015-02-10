package com.example.isaac.nileswestlitcenter;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.model.people.Person;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    public void onDisconnected(){}
    /* Request code used to invoke sign in user interactions. */

    public static final String EXTRA_MESSAGE = "message";
    public static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    /**
     * Substitute you own sender ID here. This is the project number you got
     * from the API Console, as described in "Getting Started."
     */
    String SENDER_ID = "492971078631";

    /**
     * Tag used on log messages.
     */
    static final String TAG = "GCMDemo";
    private static Context appContext;
    GoogleCloudMessaging gcm;
    AtomicInteger msgId = new AtomicInteger();
    SharedPreferences prefs;
    Context context;

    String regid;
    private static LinearLayout studentList;
    private static final int RC_SIGN_IN = 0;
    private String email;
    private String RAILS_APP_URL = "http://nileswest.herokuapp.com";
    private String RAILS_SECRET_KEY = "DEVISING";
    /* Client used to interact with Google APIs. */
    private GoogleApiClient mGoogleApiClient;
    /* A flag indicating that a PendingIntent is in progress and prevents
     * us from starting further intents.
     */
    private boolean mIntentInProgress;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        appContext = getApplicationContext();
        int SDK_INT = android.os.Build.VERSION.SDK_INT;
        if (SDK_INT > 8) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);
            //fixes AndroidBlockGuardPolicy for HTTP request
            //              ¯\_(ツ)_/¯

        }

        studentList = (LinearLayout)findViewById(R.id.studentList);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Plus.API)
                .addScope(Plus.SCOPE_PLUS_LOGIN)
                .build();


        TextView t = (TextView)findViewById(R.id.TextView01);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);


                emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[] {"hi@isaacmoldofsky.com"});

                emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Lit Center Android App");

                emailIntent.setType("plain/text");
                emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, "Hey Isaac.\n\nJust wanted to say, " +
                        "I think you've put together a really great product here. " +
                        "Keep up the good work!\n\n" +
                        "With warm regards,\n\n" +
                        email.replace("@nths219.org","")+
                        "\n(if you have non-autogenerated feedback, feel free to erase everything here)");

                startActivity(emailIntent);
                }
        });
        TextView q = (TextView)findViewById(R.id.quit);
        q.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logout(v);
            }
        });

    }


    private void sendRegistrationIdToBackend(String regid) {
        // Your implementation here.
        List<NameValuePair> l = new ArrayList<NameValuePair>();
        l.add(new BasicNameValuePair("email",email));
        l.add(new BasicNameValuePair("token",regid));
        l.add(new BasicNameValuePair("platform","Android"));
        postRequest(RAILS_APP_URL + "/register", l);
    }



    private void storeRegistrationId(Context contex, String regId) {
        final SharedPreferences prefs = getGCMPreferences(contex);
        int appVersion = getAppVersion(contex);
        Log.i(TAG, "Saving regId on app version " + appVersion);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PROPERTY_REG_ID, regId);
        editor.putInt(PROPERTY_APP_VERSION, appVersion);
        editor.commit();
    }



    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Extract data included in the Intent
            String subject = intent.getStringExtra("subject");
            String name = intent.getStringExtra("name");
            Boolean deleting = intent.getBooleanExtra("deleting",true);
            if(deleting){
                deleteStudentFromList(subject,name);
            }
            else{
                addStudentToList(subject,name);
            }

        }
    };
    @Override
    protected void onPause() {
        // Unregister since the activity is not visible
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        super.onPause();
    }
    @Override
    protected void onResume() {
        super.onResume();
        if(email!=null) {
            checkPlayServices();
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter("studentList"));
        ArrayList<String[]> stored = GcmIntentService.retrieveAndClearStoredMessages();
        for(int i = 0; i< stored.size(); i++){
            if(stored.get(i)[0]=="true"){
                //delete
                deleteStudentFromList(stored.get(i)[1],stored.get(i)[2]);
            }
            else{
                //add
                addStudentToList(stored.get(i)[1], stored.get(i)[2]);
            }
        }
    }


    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Log.i(TAG, "This device is not supported.");
                finish();
            }
            return false;
        }
        return true;
    }
    public void logout(View v){

        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();

            List<NameValuePair> headers = new ArrayList<NameValuePair>();
            headers.add(new BasicNameValuePair("email",email));
            headers.add(new BasicNameValuePair("secret_key",RAILS_SECRET_KEY));
            headers.add(new BasicNameValuePair("status","0"));
            postRequest(RAILS_APP_URL + "/change_status", headers);


        }
        finish();

    }
    private void registerInBackground() {
        Log.d("whereami","registerInBackground");
        new AsyncTask() {
            @Override
            protected String doInBackground(Object[] params) {
                Log.d("whereami","doInBackground");
                String msg = "";
                try {
                    Log.d("whereami","doInBackground try clause");
                    if (gcm == null) {
                        gcm = GoogleCloudMessaging.getInstance(context);
                    }
                    Log.d("whereami","doInBackground before regid set");
                    regid = gcm.register(SENDER_ID);

                    Log.d("whereami","doInBackground after regid set");
                    msg = "Device registered, registration ID=" + regid;
                    Log.d("device registered, regid=",regid);

                    Log.d("whereami","doInBackground after regid log");
                    if(regid!=null) {

                    }
                    else
                    {
                        Toast.makeText(getApplicationContext(), "regid null",
                                Toast.LENGTH_LONG).show();
                    }
                    // You should send the registration ID to your server over HTTP,
                    // so it can use GCM/HTTP or CCS to send messages to your app.
                    // The request to your server should be authenticated if your app
                    // is using accounts.
                    sendRegistrationIdToBackend(regid);

                    // For this demo: we don't need to send it because the device
                    // will send upstream messages to a server that echo back the
                    // message using the 'from' address in the message.

                    // Persist the regID - no need to register again.
                    storeRegistrationId(context, regid);
                } catch (IOException ex) {
                    msg = "Error :" + ex.getMessage();
                    Log.d("async error",ex.getLocalizedMessage());
                    // If there is an error, don't just keep trying to register.
                    // Require the user to click a button again, or perform
                    // exponential back-off.
                }
                return msg;
            }

            protected void onPostExecute(String msg) {
                Log.d("onPostExecute",msg);
            }

        }.execute(null, null, null);

    }




    private static int getAppVersion(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // should never happen
            throw new RuntimeException("Could not get package name: " + e);
        }
    }



    private String getRegistrationId(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId.isEmpty()) {
            Log.i(TAG, "Registration not found.");
            return "";
        }
        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = getAppVersion(context);
        if (registeredVersion != currentVersion) {
            Log.i(TAG, "App version changed.");
            return "";
        }
        return registrationId;
    }
    /**
     * @return Application's {@code SharedPreferences}.
     */
    private SharedPreferences getGCMPreferences(Context context) {
        // This sample app persists the registration ID in shared preferences, but
        // how you store the regID in your app is up to you.
        return getSharedPreferences(MainActivity.class.getSimpleName(),
                Context.MODE_PRIVATE);
    }









    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    protected void onStop() {
        super.onStop();

        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();

        }
    }




public void onConnectionFailed(ConnectionResult result) {
        if (!mIntentInProgress && result.hasResolution()) {
        try {
        mIntentInProgress = true;
        startIntentSenderForResult(result.getResolution().getIntentSender(),
                RC_SIGN_IN, null, 0, 0, 0);
        } catch (IntentSender.SendIntentException e) {
        // The intent was canceled before it was sent.  Return to the default
        // state and attempt to connect to get an updated ConnectionResult.
        mIntentInProgress = false;
        mGoogleApiClient.connect();
        }
        }
        }


    public void postRequest(String url, List<NameValuePair> headers){
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost(url);

        try {
            httppost.setEntity(new UrlEncodedFormEntity(headers));
            httpclient.execute(httppost);

        } catch (ClientProtocolException e) {
            // TODO Auto-generated catch block

        } catch (IOException e) {
            // TODO Auto-generated catch block
        }


    }
public void onConnected(Bundle connectionHint) {
        // We've resolved any connection errors.  mGoogleApiClient can be used to
        // access Google APIs on behalf of the user.
            Person p = Plus.PeopleApi.getCurrentPerson(mGoogleApiClient);
            if(p != null){
                email = Plus.AccountApi.getAccountName(mGoogleApiClient);
                List<NameValuePair> headers = new ArrayList<NameValuePair>();
                headers.add(new BasicNameValuePair("email",email));
                headers.add(new BasicNameValuePair("name",p.getDisplayName()));
                headers.add(new BasicNameValuePair("image",p.getImage().getUrl()));
                headers.add(new BasicNameValuePair("secret_key",RAILS_SECRET_KEY));

                postRequest(RAILS_APP_URL + "/login",headers);

                Toast.makeText(getApplicationContext(), "Logged in to server",
                        Toast.LENGTH_LONG).show();
            }

            Switch s = (Switch)findViewById(R.id.switch1);
            s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    List<NameValuePair> headers = new ArrayList<NameValuePair>();
                    headers.add(new BasicNameValuePair("email",email));
                    headers.add(new BasicNameValuePair("secret_key",RAILS_SECRET_KEY));

                    if(isChecked){
                        //switch to busy
                        headers.add(new BasicNameValuePair("status","1"));

                        Toast.makeText(getApplicationContext(), "Marked as tutoring",
                                Toast.LENGTH_LONG).show();
                    }
                    else{
                        //switch to available
                        headers.add(new BasicNameValuePair("status","2"));

                        Toast.makeText(getApplicationContext(), "Marked as available",
                                Toast.LENGTH_LONG).show();
                    }

                    postRequest(RAILS_APP_URL + "/change_status",headers);
                }
            });


        //now connect to gcm

    context = getApplicationContext();
    // Check device for Play Services APK. If check succeeds, proceed with
    //  GCM registration.
    if (checkPlayServices()) {
        gcm = GoogleCloudMessaging.getInstance(this);
        regid = getRegistrationId(context);

        if (regid.isEmpty()) {
            registerInBackground();

            Toast.makeText(getApplicationContext(), "registering",
                    Toast.LENGTH_LONG).show();
        }
        else{

            Toast.makeText(getApplicationContext(), "Device registered",
                    Toast.LENGTH_LONG).show();
        }
    } else {

    }



}

protected void onActivityResult(int requestCode, int responseCode, Intent intent) {
        if (requestCode == RC_SIGN_IN) {
        mIntentInProgress = false;

        if (!mGoogleApiClient.isConnecting()) {
        mGoogleApiClient.connect();
        }
        }
        }

public void onConnectionSuspended(int cause) {
        mGoogleApiClient.connect();
        }


    public void addStudentToList(final String subject, final String name) {
        LinearLayout horizontal = new LinearLayout(appContext);
        horizontal.setOrientation(LinearLayout.HORIZONTAL);
        TextView ns = new TextView(appContext);
        ns.setText(name + "::" + subject);
        horizontal.addView(ns);
        Button no = new Button(appContext);
        no.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MainActivity.this.deleteStudentFromList(subject,name);
            }
        });

        Drawable noImg  = appContext.getResources().getDrawable(R.drawable.ic_action_cancel);
        no.setCompoundDrawablesWithIntrinsicBounds(noImg,null,null,null);
        horizontal.addView(no);
        Button yes = new Button(appContext);
        yes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(appContext, "now tutoring",
                        Toast.LENGTH_LONG).show();
                LinearLayout l = (LinearLayout)findViewById(R.id.studentList);
                l.removeAllViews();
                List<NameValuePair> resolve = new ArrayList<NameValuePair>();
                resolve.add(new BasicNameValuePair("subject",subject));
                resolve.add(new BasicNameValuePair("name",name));
                resolve.add(new BasicNameValuePair("email",email));
                resolve.add(new BasicNameValuePair("secret_key",RAILS_SECRET_KEY));
                ((Switch)findViewById(R.id.switch1)).setChecked(true);
                postRequest(RAILS_APP_URL + "/resolve", resolve);
                //post request to server
                //TODO post to resolve tutor request
            }
        });
        Drawable yesImg  = appContext.getResources().getDrawable(R.drawable.ic_action_accept);
        yes.setCompoundDrawablesWithIntrinsicBounds(yesImg,null,null,null);
        horizontal.addView(yes);
        studentList.addView(horizontal);
    }

    public void deleteStudentFromList(String subject, String name) {


        LinearLayout l = (LinearLayout)findViewById(R.id.studentList);
        for(int i = 0; i<l.getChildCount(); i++){
            LinearLayout ll = (LinearLayout)l.getChildAt(i);
            TextView nameAndSubject = (TextView)ll.getChildAt(0);
            Toast.makeText(appContext, "nameandsubject"+nameAndSubject.getText(),
                        Toast.LENGTH_LONG).show();
            if(nameAndSubject.getText().toString().contains(subject) && nameAndSubject.getText().toString().contains(name)){
                Toast.makeText(appContext, "delete",
                        Toast.LENGTH_LONG).show();
                l.removeViewAt(i);
                return;
            }
        }
    }
}
