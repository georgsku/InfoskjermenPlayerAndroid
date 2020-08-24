package com.favo.infoskjermenplayerandroid;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.util.Log;
import org.json.JSONObject;

import android.view.KeyEvent;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;


/* To Do:
    Kontroller inputs, finne ut keycodes.
*/

public class MainActivity extends Activity{

    private WebView webview;
    private WebView splashscreen;

    private String host = "http://app.infoskjermen.no";
    private String TAG = "Message";
    private String physical_id;

    public static SharedPreferences prefs;

    private Button btn;
    private EditText hostinput;
    private LinearLayout modal;

    private int mediumAnimationDuration;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        splashscreen = findViewById(R.id.splashscreen);
        webview = findViewById(R.id.webview);


        splashscreen.getSettings().setJavaScriptEnabled(true);
        splashscreen.loadUrl("file:///android_asset/splashscreen.html");

        prefs = this.getSharedPreferences("Infoskjermen", Context.MODE_PRIVATE);
        String app_host = prefs.getString("app_host", null);
        if (app_host != null ) {
            host = app_host;
        }
        ChangeHostModal();

        webview.setVisibility(View.GONE);

        mediumAnimationDuration = getResources().getInteger(
                android.R.integer.config_mediumAnimTime);

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isConnectedToNetwork(getApplicationContext())) {
                    new CheckServerConnection().execute();
                } else {
                    reconnectToServer();
                    splashscreen.evaluateJavascript("setMessage('Reconnecting... Please check your internet connection')", null);
                }

            }
        }, 5000);

    }


    private void WebviewLoadGo() {
        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        webview.getSettings().setMediaPlaybackRequiresUserGesture(false);

        webview.loadUrl(host + "/go");

        webview.setWebViewClient(new WebViewClient() {
            public void onPageFinished(WebView webView, String url) {
                super.onPageFinished(webview, url);

                webView.evaluateJavascript("window.localStorage.physical_id", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        new SendDeviceInfo().execute(value);
                    }
                });
                CrossFadeWebViews();
            }
        });

    }

    private void ChangeHostModal() {
        btn = findViewById(R.id.hostbtn);
        hostinput = findViewById(R.id.hostinput);
        modal = findViewById(R.id.modal);

        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
               Editable inputhost = hostinput.getText();
                Log.i(TAG, "onClick: ");
                Log.i(TAG, inputhost.toString());
                webview.loadUrl(inputhost + "/go");
                modal.setVisibility(View.GONE);
           }
        });
    }

    private void CrossFadeWebViews() {
        webview.setAlpha(0f);
        webview.setVisibility(View.VISIBLE);
        webview.animate()
                .alpha(1f)
                .setDuration(mediumAnimationDuration)
                .setListener(null);

        splashscreen.animate()
                .alpha(0f)
                .setDuration(mediumAnimationDuration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        splashscreen.clearAnimation();
                        splashscreen.setVisibility(View.GONE);
                    }
                });
    }

    private void reconnectToServer() {
        Log.i(TAG, "Reconnecting to server...");
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isConnectedToNetwork(getApplicationContext())) {
                    new CheckServerConnection().execute();
                } else {
                    reconnectToServer();
                    splashscreen.evaluateJavascript("setMessage('Reconnecting... Please check your internet connection')", null);
                }
            }
        }, 3000);
    }

    private class CheckServerConnection extends AsyncTask<String, Void, Void> {

        private int responseCode;

        @Override
        protected Void doInBackground(String... arg0) {

            try {

                URL url = new URL(host + "/up");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("GET");
                c.connect();
                Log.d(TAG, "doInBackground:");
                responseCode = c.getResponseCode();

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Server returned HTTP " + c.getResponseCode() + " " + c.getResponseMessage());
                }
            } catch (Exception e) {
                e.printStackTrace();

                Log.i(TAG, "FEIL: " + responseCode);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            if (responseCode == 200) {
                WebviewLoadGo();
                splashscreen.evaluateJavascript("setMessage('Lets go!')", null);
            } else {
                reconnectToServer();
                splashscreen.evaluateJavascript("setMessage('Connecting to server...')", null);
            }
        }
    }

    private boolean isConnectedToNetwork(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        return activeNetwork != null &&
                activeNetwork.isConnectedOrConnecting();
    }


    private String codes = "";
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {

        Log.i("KEY", "" + event.getKeyCode());

        codes += "-" + event.getKeyCode();

        if (codes.endsWith("")) {
            webview.reload();
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            Log.e(TAG, "Key up, code " + event.getKeyCode());
        }

        return true;
    }



    private class SendDeviceInfo extends AsyncTask<String, Void,Void> {

        @Override
        protected Void doInBackground(String... arg0) {
            try {

                String versionName = "";
                try {
                    PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                    versionName = packageInfo.versionName;
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }

                JSONObject jsonObject = new JSONObject();

                jsonObject.put("Host", host);
                jsonObject.put("App-version", versionName);
                jsonObject.put("Device", android.os.Build.PRODUCT + " " + android.os.Build.MODEL);
                jsonObject.put("SDK", android.os.Build.VERSION.SDK);
                jsonObject.put("OS.V", System.getProperty("os.version"));

                JSONObject options = new JSONObject();
                options.put("options", jsonObject);

                String physical_id = arg0[0];
                String physical_id_str = physical_id.replace("\"", "");
                Log.i(TAG, "Physical_id: " + physical_id_str);

                URL url = new URL(host + "/listeners/set_options/" + physical_id_str + "/");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");

                OutputStream os = conn.getOutputStream();
                os.write(options.toString().getBytes("UTF-8"));
                os.close();

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Server returned HTTP " + conn.getResponseCode() + " " + conn.getResponseMessage());
                }

            } catch (Exception e) {
                e.printStackTrace();

            }
            return null;
        }
    }
}
