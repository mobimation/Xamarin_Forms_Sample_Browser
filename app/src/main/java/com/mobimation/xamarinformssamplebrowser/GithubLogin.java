package com.mobimation.xamarinformssamplebrowser;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.app.FragmentActivity;
import android.support.v4.os.AsyncTaskCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.alorma.github.sdk.services.repos.GithubReposClient;
import com.alorma.github.sdk.services.repos.UserReposClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;

public class GithubLogin extends AppCompatActivity {

    private String xamarinColor="3498db";
    String userid, password;
    TextView labelStatus;
    Activity a;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        a=this;
        setContentView(R.layout.activity_github_login);
        // Prepare UI
        final EditText editTextUserid = (EditText)this.findViewById(R.id.editTextUserId);
        final EditText editTextPassword = (EditText)this.findViewById(R.id.editTextPassword);
        labelStatus = (TextView)this.findViewById(R.id.labelStatus);

        Button buttonLogin = (Button)this.findViewById(R.id.buttonLogin);
        buttonLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                userid=editTextUserid.getText().toString();
                password=editTextPassword.getText().toString();
                GithubReposClient client = new UserReposClient(userid,
                        "xamarin/xamarin-forms-samples/blob/master");
                AsyncTaskCompat.executeParallel(new LoginTask(username, password));
                // new AsyncLogin(getApplicationContext()).execute(userid, password);
            }
        });
    }








    private class LoginTask extends ProgressDialogTask<Authorization> {
        private String mUserName;
        private String mPassword;
        private String mOtpCode;

        /**
         * Instantiates a new load repository list task.
         */
        public LoginTask(String userName, String password) {
            super(GithubLogin.this, R.string.please_wait, R.string.authenticating);
            mUserName = userName;
            mPassword = password;
        }

        public LoginTask(String userName, String password, String otpCode) {
            super(GithubLogin.this, R.string.please_wait, R.string.authenticating);
            mUserName = userName;
            mPassword = password;
            mOtpCode = otpCode;
        }

        public LoginTask(FragmentActivity activity, int resWaitId, int resWaitMsg) {
            super(activity, resWaitId, resWaitMsg);
        }

        @Override
        protected Authorization run() throws IOException {
            GitHubClient client = new ClientForAuthorization(mOtpCode);
            client.setCredentials(mUserName, mPassword);
            client.setUserAgent("Octodroid");

            String description = "Octodroid - " + Build.MANUFACTURER + " " + Build.MODEL;
            String fingerprint = getHashedDeviceId();
            int index = 1;

            OAuthService authService = new OAuthService(client);
            for (Authorization authorization : authService.getAuthorizations()) {
                String note = authorization.getNote();
                if ("Gh4a".equals(note)) {
                    authService.deleteAuthorization(authorization.getId());
                } else if (note != null && note.startsWith("Octodroid")) {
                    if (fingerprint.equals(authorization.getFingerprint())) {
                        authService.deleteAuthorization(authorization.getId());
                    } else if (note.startsWith(description)) {
                        index++;
                    }
                }
            }

            if (index > 1) {
                description += " #" + index;
            }

            Authorization auth = new Authorization();
            auth.setNote(description);
            auth.setUrl("http://github.com/slapperwan/gh4a");
            auth.setFingerprint(fingerprint);
            auth.setScopes(Arrays.asList("user", "repo", "gist"));

            return authService.createAuthorization(auth);
        }

        @Override
        protected void onError(Exception e) {
            if (e instanceof TwoFactorAuthException) {
                if ("sms".equals(((TwoFactorAuthException) e).getTwoFactorAuthType())) {
                    AsyncTaskCompat.executeParallel(new DummyPostTask(mUserName, mPassword));
                } else {
                    open2FADialog(mUserName, mPassword);
                }
            } else {
                Toast.makeText(GithubLogin.this, e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected void onSuccess(Authorization result) {
            SharedPreferences sharedPreferences = getSharedPreferences(
                    SettingsFragment.PREF_NAME, MODE_PRIVATE);
            Editor editor = sharedPreferences.edit();
            editor.putString(Constants.User.AUTH_TOKEN, result.getToken());
            editor.putString(Constants.User.LOGIN, mUserName);
            editor.apply();

            goToToplevelActivity(false);
            finish();
        }

        private String getHashedDeviceId() {
            String androidId = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ANDROID_ID);
            if (androidId == null) {
                // shouldn't happen, do a lame fallback in that case
                androidId = Build.FINGERPRINT;
            }

            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                byte[] result = digest.digest(androidId.getBytes("UTF-8"));
                StringBuilder sb = new StringBuilder();
                for (byte b : result) {
                    sb.append(String.format(Locale.US, "%02X", b));
                }
                return sb.toString();
            } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
                // won't happen
                return androidId;
            }
        }
    }












    /**
     * Run asynchronous GitHub login.
     */
    private class AsyncLogin extends AsyncTask<String, String, String> {

        private final String TAG = GithubLogin.class.getSimpleName();
        private Context context;
        private String code;

        public String convertStreamToString(InputStream is) {
    /*
     * To convert the InputStream to String we use the BufferedReader.readLine()
     * method. We iterate until the BufferedReader return null which means
     * there's no more data to read. Each line will appended to a StringBuilder
     * and returned as String.
     */
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();

            String line = null;
            try {
                while ((line = reader.readLine()) != null) {
                    sb.append(line + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return sb.toString();
        }

        protected AsyncLogin(Context context) {
            this.context = context;
        }

        @Override
        protected  void onPreExecute()
        {

        }
        @Override
        protected String doInBackground(String... params) {
            String webUrl=null;
            Log.d(TAG, "doInBackground");
            publishProgress("Submitting..");


            /**
             * Submit JSON request over https to Ln4 Solutions server
             */
            code=params[1];
            return webUrl;  // onPostExecute() receives result..
        }

        /**
         * publishProgress() updates status string
         * @param values Tell user how the background job is progressing
         */
        @Override
        protected void onProgressUpdate(String... values) {
            labelStatus.setText(values[0]);
        }

        /**
         * Called when doInBackground() completes.
         * Takes care of launching a browser with the given url
         * @param webUrl The url to view; null if invalid customer id
         */
        @Override
        protected void onPostExecute(String webUrl) {
            if (webUrl!=null) {  // If web access granted
            }
            else
                labelStatus.setText("Customer id " + code + " not valid");
        }
    }
}
