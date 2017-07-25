package com.odoo.core.account;

import android.content.Intent;
import android.content.res.XmlResourceParser;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.odoo.App;
import com.odoo.OdooActivity;
import com.odoo.R;
import com.odoo.base.addons.res.ResCompany;
import com.odoo.base.addons.res.ResUsers;
import com.odoo.config.FirstLaunchConfig;
import com.odoo.core.auth.OdooAccountManager;
import com.odoo.core.auth.OdooAuthenticator;
import com.odoo.core.orm.ODataRow;
import com.odoo.core.rpc.Odoo;
import com.odoo.core.rpc.handler.OdooVersionException;
import com.odoo.core.rpc.helper.ODomain;
import com.odoo.core.rpc.listeners.IDatabaseListListener;
import com.odoo.core.rpc.listeners.IOdooConnectionListener;
import com.odoo.core.rpc.listeners.IOdooLoginCallback;
import com.odoo.core.rpc.listeners.OdooError;
import com.odoo.core.support.OUser;
import com.odoo.core.support.OdooUserLoginSelectorDialog;
import com.odoo.core.utils.IntentUtils;
import com.odoo.core.utils.OResource;
import com.odoo.datas.OConstants;

import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.List;

public class OdooLogin extends AppCompatActivity implements View.OnClickListener,
        View.OnFocusChangeListener, OdooUserLoginSelectorDialog.IUserLoginSelectListener,
        IOdooConnectionListener, IOdooLoginCallback, AdapterView.OnItemSelectedListener {

    private EditText edtUsername, edtPassword, edtSelfHosted;
    private Boolean mCreateAccountRequest = false;
    private Boolean mSelfHostedURL = false;
    private Boolean mConnectedToServer = false;
    private Boolean mAutoLogin = false;
    private Boolean mRequestedForAccount = false;
    private AccountCreator accountCreator = null;
    private Spinner databaseSpinner = null;
    private List<String> databases = new ArrayList<>();
    private Spinner selfHostedSpinner = null;
    private List<String> selfHosteds = new ArrayList<>();
    private TextView mLoginProcessStatus = null;
    private App mApp;
    private Odoo mOdoo;
    private String serverUrl = OConstants.URL_ODOO;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.base_login);
        mApp = (App) getApplicationContext();
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            if (extras.containsKey(OdooAuthenticator.KEY_NEW_ACCOUNT_REQUEST))
                mCreateAccountRequest = true;
            if (extras.containsKey(OdooActivity.KEY_ACCOUNT_REQUEST)) {
                mRequestedForAccount = true;
                setResult(RESULT_CANCELED);
            }
        }
        mLoginProcessStatus = (TextView) findViewById(R.id.login_process_status);
        if (!mCreateAccountRequest) {
            if (OdooAccountManager.anyActiveUser(this)) {
                startOdooActivity();
                return;
            } else if (OdooAccountManager.hasAnyAccount(this)) {
                onRequestAccountSelect();
            }
        }
        init();
        readHostsFile();
    }

    private void init() {
        TextView mTermsCondition = (TextView) findViewById(R.id.termsCondition);
        mTermsCondition.setMovementMethod(LinkMovementMethod.getInstance());
        findViewById(R.id.btnLogin).setOnClickListener(this);
        findViewById(R.id.forgot_password).setOnClickListener(this);
        findViewById(R.id.create_account).setOnClickListener(this);
        findViewById(R.id.txvAddSelfHosted).setOnClickListener(this);
        edtSelfHosted = (EditText) findViewById(R.id.edtSelfHostedURL);
        selfHostedSpinner = (Spinner) findViewById(R.id.spinnerHostList);
    }

    private void startOdooActivity() {
        startActivity(new Intent(OdooLogin.this, OdooActivity.class));
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_base_login, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.txvAddSelfHosted:
                toggleSelfHostedURL();
                break;
            case R.id.btnLogin:
                loginUser();
                break;
            case R.id.forgot_password:
                IntentUtils.openURLInBrowser(this, OConstants.URL_ODOO_RESET_PASSWORD);
                break;
            case R.id.create_account:
                IntentUtils.openURLInBrowser(this, OConstants.URL_ODOO_SIGN_UP);
                break;
        }
    }

    private void readHostsFile() {
        List<String> hosts = new ArrayList<>();

        try {
            XmlResourceParser xpp = getResources().getXml(R.xml.hosts);

            while (xpp.getEventType()!= XmlPullParser.END_DOCUMENT) {
                String name = xpp.getName();
                if (xpp.getEventType() == XmlPullParser.START_TAG) {
                    if (name.equals("host")) {
                        xpp.next();
                        if (xpp.getEventType() == XmlPullParser.TEXT) {
                            hosts.add(xpp.getText());
                        }
                    }
                }
                xpp.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!hosts.isEmpty()) {
            enableSelfHostedUrlSpinner(hosts);
        }
    }

    private void toggleSelfHostedURL() {
        TextView txvAddSelfHosted = (TextView) findViewById(R.id.txvAddSelfHosted);
        if (!mSelfHostedURL) {
            mSelfHostedURL = true;
            findViewById(R.id.layoutSelfHosted).setVisibility(View.VISIBLE);
            edtSelfHosted.setOnFocusChangeListener(this);
            edtSelfHosted.requestFocus();
            serverUrl = OConstants.URL_ODOO;
            txvAddSelfHosted.setText(R.string.label_login_with_odoo);
        } else {
            findViewById(R.id.layoutBorderDB).setVisibility(View.GONE);
            findViewById(R.id.layoutDatabase).setVisibility(View.GONE);
            findViewById(R.id.layoutSelfHosted).setVisibility(View.GONE);
            mSelfHostedURL = false;
            txvAddSelfHosted.setText(R.string.label_add_self_hosted_url);
            serverUrl = "";
            edtSelfHosted.setText(serverUrl);
        }
    }

    private void enableSelfHostedUrlSpinner(List<String> hosts) {
        selfHosteds = hosts;
        mSelfHostedURL = true;
        edtSelfHosted.setVisibility(View.GONE);
        selfHostedSpinner.setVisibility(View.VISIBLE);

        if (hosts.size() > 1) {
            selfHosteds.add(0, OResource.string(this, R.string.label_select_host));
            serverUrl = "";
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, selfHosteds);
        selfHostedSpinner.setAdapter(adapter);
        selfHostedSpinner.setOnItemSelectedListener(this);

        if (hosts.size() == 1) {
            testSelfHostedUrl(createServerURL(hosts.get(0)));
        }

        findViewById(R.id.layoutSelfHosted).setVisibility(View.VISIBLE);
        findViewById(R.id.txvAddSelfHosted).setVisibility(View.GONE);
    }

    @Override
    public void onFocusChange(final View v, final boolean hasFocus) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mSelfHostedURL && v.getId() == R.id.edtSelfHostedURL && !hasFocus) {
                    if (!TextUtils.isEmpty(edtSelfHosted.getText())
                            && validateURL(edtSelfHosted.getText().toString())) {
                        edtSelfHosted.setError(null);
                        String test_url = createServerURL(edtSelfHosted.getText().toString());
                        testSelfHostedUrl(test_url);
                    }
                }
            }
        }, 500);
    }

    @Override
    public void onItemSelected(final AdapterView<?> parent, final View view, final int position, long id) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (selfHosteds.size() > 1 && position == 0) {
                    // current item is "Select Host"
                    findViewById(R.id.imgValidURL).setVisibility(View.GONE);
                    serverUrl = "";
                    databases = new ArrayList<>();
                    showDatabases();
                    return;
                }

                if (parent.getId() == R.id.spinnerHostList) {
                    String test_url = createServerURL(parent.getItemAtPosition(position).toString());
                    testSelfHostedUrl(test_url);
                }
            }
        }, 500);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }

    private void testSelfHostedUrl(String test_url) {
        if (mAutoLogin) {
            findViewById(R.id.controls).setVisibility(View.GONE);
            findViewById(R.id.login_progress).setVisibility(View.VISIBLE);
            mLoginProcessStatus.setText(OResource.string(OdooLogin.this,
                    R.string.status_connecting_to_server));
        }
        findViewById(R.id.imgValidURL).setVisibility(View.GONE);
        findViewById(R.id.serverURLCheckProgress).setVisibility(View.VISIBLE);
        findViewById(R.id.layoutBorderDB).setVisibility(View.GONE);
        findViewById(R.id.layoutDatabase).setVisibility(View.GONE);
        Log.v("", "Testing URL :" + test_url);
        try {
            Odoo.createInstance(OdooLogin.this, test_url).setOnConnect(OdooLogin.this);
            serverUrl = test_url;
        } catch (OdooVersionException e) {
            e.printStackTrace();
        }
    }

    private boolean validateURL(String url) {
        return (url.contains("."));
    }

    private String createServerURL(String server_url) {
        if (server_url == null || server_url.isEmpty()) {
            return "";
        }
        StringBuilder serverURL = new StringBuilder();
        if (!server_url.contains("http://") && !server_url.contains("https://")) {
            serverURL.append("http://");
        }
        serverURL.append(server_url);
        return serverURL.toString();
    }

    // User Login
    private void loginUser() {
        Log.v("", "LoginUser()");
        String databaseName;
        edtUsername = (EditText) findViewById(R.id.edtUserName);
        edtPassword = (EditText) findViewById(R.id.edtPassword);

        if (mSelfHostedURL) {
            edtSelfHosted.setError(null);
            if (TextUtils.isEmpty(serverUrl)) {
                if (selfHosteds.isEmpty()) {
                    edtSelfHosted.setError(OResource.string(this, R.string.error_provide_server_url));
                    edtSelfHosted.requestFocus();
                } else if (selfHosteds.size() > 1 && selfHostedSpinner.getSelectedItemPosition() == 0) {
                    Toast.makeText(this, OResource.string(this, R.string.label_select_host), Toast.LENGTH_LONG).show();
                    findViewById(R.id.controls).setVisibility(View.VISIBLE);
                    findViewById(R.id.login_progress).setVisibility(View.GONE);
                }
                return;
            }
            if (databaseSpinner != null && databases.size() > 1 && databaseSpinner.getSelectedItemPosition() == 0) {
                Toast.makeText(this, OResource.string(this, R.string.label_select_database), Toast.LENGTH_LONG).show();
                findViewById(R.id.controls).setVisibility(View.VISIBLE);
                findViewById(R.id.login_progress).setVisibility(View.GONE);
                return;
            }

        }
        edtUsername.setError(null);
        edtPassword.setError(null);
        if (TextUtils.isEmpty(edtUsername.getText())) {
            edtUsername.setError(OResource.string(this, R.string.error_provide_username));
            edtUsername.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(edtPassword.getText())) {
            edtPassword.setError(OResource.string(this, R.string.error_provide_password));
            edtPassword.requestFocus();
            return;
        }
        findViewById(R.id.controls).setVisibility(View.GONE);
        findViewById(R.id.login_progress).setVisibility(View.VISIBLE);
        mLoginProcessStatus.setText(OResource.string(OdooLogin.this,
                R.string.status_connecting_to_server));
        if (mConnectedToServer) {
            databaseName = databases.get(0);
            if (databaseSpinner != null) {
                databaseName = databases.get(databaseSpinner.getSelectedItemPosition());
            }
            mAutoLogin = false;
            loginProcess(databaseName);
        } else {
            mAutoLogin = true;
            try {
                Odoo.createInstance(OdooLogin.this, serverUrl).setOnConnect(OdooLogin.this);
            } catch (OdooVersionException e) {
                e.printStackTrace();
            }
        }
    }

    private void showDatabases() {
        if (databases.size() > 1) {
            findViewById(R.id.layoutBorderDB).setVisibility(View.VISIBLE);
            findViewById(R.id.layoutDatabase).setVisibility(View.VISIBLE);
            databaseSpinner = (Spinner) findViewById(R.id.spinnerDatabaseList);
            databases.add(0, OResource.string(this, R.string.label_select_database));
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, databases);
            databaseSpinner.setAdapter(adapter);
        } else {
            databaseSpinner = null;
            findViewById(R.id.layoutBorderDB).setVisibility(View.GONE);
            findViewById(R.id.layoutDatabase).setVisibility(View.GONE);
        }
    }

    @Override
    public void onUserSelected(OUser user) {
        OdooAccountManager.login(this, user.getAndroidName());
        new AccountUpdater().execute();
    }

    @Override
    public void onRequestAccountSelect() {
        OdooUserLoginSelectorDialog dialog = new OdooUserLoginSelectorDialog(this);
        dialog.setUserLoginSelectListener(this);
        dialog.show();
    }

    @Override
    public void onNewAccountRequest() {
        init();
    }


    @Override
    public void onConnect(Odoo odoo) {
        Log.v("Odoo", "Connected to server.");
        mOdoo = odoo;
        databases.clear();
        findViewById(R.id.serverURLCheckProgress).setVisibility(View.GONE);
        edtSelfHosted.setError(null);
        mLoginProcessStatus.setText(OResource.string(OdooLogin.this, R.string.status_connected_to_server));
        mOdoo.getDatabaseList(new IDatabaseListListener() {
            @Override
            public void onDatabasesLoad(List<String> strings) {
                databases.addAll(strings);
                showDatabases();
                mConnectedToServer = true;
                findViewById(R.id.imgValidURL).setVisibility(View.VISIBLE);
                if (mAutoLogin) {
                    loginUser();
                }
            }
        });
    }

    @Override
    public void onError(OdooError error) {
        // Some error occurred
        if (error.getResponseCode() == Odoo.ErrorCode.InvalidURL.get() ||
                error.getResponseCode() == -1) {
            findViewById(R.id.controls).setVisibility(View.VISIBLE);
            findViewById(R.id.login_progress).setVisibility(View.GONE);
            edtSelfHosted.setError(OResource.string(OdooLogin.this, R.string.error_invalid_odoo_url));
            edtSelfHosted.requestFocus();
        }
        findViewById(R.id.controls).setVisibility(View.VISIBLE);
        findViewById(R.id.login_progress).setVisibility(View.GONE);
        findViewById(R.id.serverURLCheckProgress).setVisibility(View.VISIBLE);
    }

    @Override
    public void onCancelSelect() {
    }

    private void loginProcess(String database) {
        Log.v("", "LoginProcess");
        final String username = edtUsername.getText().toString();
        final String password = edtPassword.getText().toString();
        Log.v("", "Processing Self Hosted Server Login");
        mLoginProcessStatus.setText(OResource.string(OdooLogin.this, R.string.status_logging_in));
        mOdoo.authenticate(username, password, database, this);
    }

    @Override
    public void onLoginSuccess(Odoo odoo, OUser user) {
        mApp.setOdoo(odoo, user);
        mLoginProcessStatus.setText(OResource.string(OdooLogin.this, R.string.status_login_success));
        mOdoo = odoo;
        if (accountCreator != null) {
            accountCreator.cancel(true);
        }
        accountCreator = new AccountCreator();
        accountCreator.execute(user);
    }

    @Override
    public void onLoginFail(OdooError error) {
        loginFail(error);
    }

    private void loginFail(OdooError error) {
        Log.e("Login Failed", error.getMessage());
        findViewById(R.id.controls).setVisibility(View.VISIBLE);
        findViewById(R.id.login_progress).setVisibility(View.GONE);
        edtUsername.setError(OResource.string(this, R.string.error_invalid_username_or_password));
    }

    private class AccountCreator extends AsyncTask<OUser, Void, Boolean> {

        private OUser mUser;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mLoginProcessStatus.setText(OResource.string(OdooLogin.this, R.string.status_creating_account));
        }

        @Override
        protected Boolean doInBackground(OUser... params) {
            mUser = params[0];
            if (OdooAccountManager.createAccount(OdooLogin.this, mUser)) {
                mUser = OdooAccountManager.getDetails(OdooLogin.this, mUser.getAndroidName());
                OdooAccountManager.login(OdooLogin.this, mUser.getAndroidName());
                FirstLaunchConfig.onFirstLaunch(OdooLogin.this, mUser);

                try {
                    ResUsers userModel = new ResUsers(getApplicationContext(), null);
                    ODomain userDomain = new ODomain().add("id", "=", mUser.getUserId());
                    userModel.quickSyncRecords(userDomain);

                    // Syncing company details
                    ODataRow company_details = new ODataRow();
                    company_details.put("id", mUser.getCompanyId());
                    ResCompany company = new ResCompany(OdooLogin.this, mUser);
                    company.quickCreateRecord(company_details);

                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            super.onPostExecute(success);
            mLoginProcessStatus.setText(OResource.string(OdooLogin.this, R.string.status_redirecting));
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!mRequestedForAccount)
                        startOdooActivity();
                    else {
                        Intent intent = new Intent();
                        intent.putExtra(OdooActivity.KEY_NEW_USER_NAME, mUser.getAndroidName());
                        setResult(RESULT_OK, intent);
                        finish();
                    }
                }
            }, 1500);
        }
    }

    private class AccountUpdater extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            findViewById(R.id.controls).setVisibility(View.GONE);
            findViewById(R.id.login_progress).setVisibility(View.VISIBLE);
            mLoginProcessStatus.setText("Updating permissions..." /*OResource.string(OdooLogin.this, */);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                ResUsers userModel = new ResUsers(OdooLogin.this, null);
                ODomain userDomain = new ODomain().add("id", "=", userModel.getUser().getUserId());
                userModel.quickSyncRecords(userDomain);

                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }

            return false;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                mLoginProcessStatus.setText(OResource.string(OdooLogin.this, R.string.status_redirecting));
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startOdooActivity();
                    }
                }, 500);
            }
        }
    }
}