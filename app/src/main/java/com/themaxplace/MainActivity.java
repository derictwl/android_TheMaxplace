package com.themaxplace;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.Response;
import com.themaxplace.utility.SharedPrefsUtils;
import com.themaxplace.utility.UIUpdater;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class MainActivity extends PermissionSupportActivity implements Permission.PermissionCallback {

    private static final int INTERVAL = 1000 * 60;//1 Minute
    SwipeRefreshLayout swipeContainer;
    WebView webView, tempWebView;
    private UIUpdater mUIUpdater;


    private static final int INPUT_FILE_REQUEST_CODE = 1;
    private static final int FILECHOOSER_RESULTCODE = 1;
    private WebSettings webSettings;
    private ValueCallback<Uri> mUploadMessage;
    private Uri mCapturedImageURI = null;
    private ValueCallback<Uri[]> mFilePathCallback;
    private String mCameraPhotoPath;
    private String[] Pemissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        checkForPermissions();
        swipeContainer = (SwipeRefreshLayout) findViewById(R.id.swipeContainer);
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                webView.reload();
                swipeContainer.setRefreshing(false);
            }
        });
        CookieManager.getInstance().setAcceptCookie(true);
        webView = (WebView) findViewById(R.id.webView);
        tempWebView = (WebView) findViewById(R.id.tempWebView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadsImagesAutomatically(true);
        tempWebView.getSettings().setLoadsImagesAutomatically(true);

        webView.getSettings().setDomStorageEnabled(true);
        tempWebView.getSettings().setDomStorageEnabled(true);


        tempWebView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new myClient().setListener(new myClient.pageFinishInterface() {
            @Override
            public void onPageFinished(String url) {
                if (url.equals(getResources().getString(R.string.main_url))) {
                    if (SharedPrefsUtils.getBooleanPreference(MainActivity.this, getString(R.string.IsDeviceRegistered), false)) {
                        long lastRegisterTime = SharedPrefsUtils.getLongPreference(MainActivity.this, getResources().getString(R.string.dateofdeviceregistration), 0);
                        long currentTime = System.currentTimeMillis();
                        Log.d(TAG, "onPageFinished: Last login :" + TimeUnit.MILLISECONDS.toHours((currentTime - lastRegisterTime)));
                        if (lastRegisterTime != 0 && TimeUnit.MILLISECONDS.toHours((currentTime - lastRegisterTime)) >= 24) {
                            int user = SharedPrefsUtils.getIntegerPreference(MainActivity.this, getResources().getString(R.string.user_id), 0);
                            registerUserWithToken(user);
                        }
                    }
                }
            }
        }));

        webView.setWebViewClient(new PQClient());
        webView.setWebChromeClient(new PQChromeClient());
        tempWebView.setWebViewClient(new myClient());
        webView.loadUrl(getString(R.string.main_url));
        mUIUpdater = new UIUpdater(new Runnable() {
            @Override
            public void run() {
                callApiForCheckLogin();
            }
        }, INTERVAL);
        if (!SharedPrefsUtils.getBooleanPreference(this, getString(R.string.IsDeviceRegistered), false)) {
            mUIUpdater.startUpdates();
        }
    }

    private static final String TAG = "MainActivity";

    private void callApiForCheckLogin() {
//        String cookies = CookieManager.getInstance().getCookie(getResources().getString(R.string.main_url));
//        tempWebView.loadUrl(getString(R.string.repeat_url));
        Ion.with(this).load(getString(R.string.repeat_url)).addHeader("cookie", CookieManager.getInstance().getCookie(getResources().getString(R.string.main_url))).asString().withResponse().setCallback(new FutureCallback<Response<String>>() {
            @Override
            public void onCompleted(Exception e, Response<String> result) {
                if (e == null) {
                    Log.d(TAG, "onCompleted: We got response code :" + result.getHeaders().code());
                    Log.d(TAG, "onCompleted: " + result.getResult());
                    try {
                        int userId = Integer.parseInt(result.getResult());
                        if (userId > 0) {
                            registerUserWithToken(userId);
                        }
                    } catch (NumberFormatException e1) {
                        e1.printStackTrace();
                    }
                } else
                    e.printStackTrace();
            }
        });
    }

    //TODO // FIXME: 13/5/17 add device type as per iOS instructions
    private void registerUserWithToken(int userId) {
        if (userId == 0) {
            return;
        }
        SharedPrefsUtils.setIntegerPreference(this, getResources().getString(R.string.user_id), userId);
        String registerToken = null;
        try {
            registerToken = String.format(getResources().getString(R.string.register_url), String.valueOf(userId), URLEncoder.encode(SharedPrefsUtils.getStringPreference(this, getString(R.string.deviceToken)), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (registerToken == null) {
            return;
        }
        Log.d(TAG, "registerUserWithToken: Url is :" + registerToken);
        Ion.with(this).load(registerToken).asString().withResponse().setCallback(new FutureCallback<Response<String>>() {
            @Override
            public void onCompleted(Exception e, Response<String> result) {
                if (e == null) {
                    Log.d(TAG, "onCompleted register Token: " + result.getResult());
                    SharedPrefsUtils.setBooleanPreference(MainActivity.this, getString(R.string.IsDeviceRegistered), true);
                    SharedPrefsUtils.setLongPreference(MainActivity.this, getString(R.string.dateofdeviceregistration), System.currentTimeMillis());
                    if (mUIUpdater != null) {
                        mUIUpdater.stopUpdates();
                    }
                } else {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void onPermissionGranted(int requestCode) {

    }

    @Override
    public void onPermissionDenied(int requestCode) {

    }

    @Override
    public void onPermissionAccessRemoved(int requestCode) {

    }


    private static class myClient extends WebViewClient {
        private static final String TAG = "myClient";

        public myClient setListener(pageFinishInterface listener) {
            this.listener = listener;
            return this;
        }

        pageFinishInterface listener;

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Log.d(TAG, "shouldOverrideUrlLoading() called with: view = [" + view + "], request = [" + request + "]");
            return super.shouldOverrideUrlLoading(view, request);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            Log.d(TAG, "onPageStarted() called with: view = [" + view + "], url = [" + url + "], favicon = [" + favicon + "]");
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            Log.d(TAG, "onPageFinished() called with: view = [" + view + "], url = [" + url + "]");
        }

        public interface pageFinishInterface {
            void onPageFinished(String url);
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File imageFile = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        return imageFile;
    }

    public class PQChromeClient extends WebChromeClient {

        // For Android 5.0
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePath, WebChromeClient.FileChooserParams fileChooserParams) {
            // Double check that we don't have any existing callbacks
            if (mFilePathCallback != null) {
                mFilePathCallback.onReceiveValue(null);
            }
            mFilePathCallback = filePath;

            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                // Create the File where the photo should go
                File photoFile = null;
                try {
                    photoFile = createImageFile();
                    takePictureIntent.putExtra("PhotoPath", mCameraPhotoPath);
                } catch (IOException ex) {
                    // Error occurred while creating the File
                    Log.e(TAG, "Unable to create Image File", ex);
                }

                // Continue only if the File was successfully created
                if (photoFile != null) {
                    mCameraPhotoPath = "file:" + photoFile.getAbsolutePath();
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                            Uri.fromFile(photoFile));
                } else {
                    takePictureIntent = null;
                }
            }

            Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
            contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
            contentSelectionIntent.setType("image/*");

            Intent[] intentArray;
            if (takePictureIntent != null) {
                intentArray = new Intent[]{takePictureIntent};
            } else {
                intentArray = new Intent[0];
            }

            Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
            chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
            chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser");
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);

            startActivityForResult(chooserIntent, INPUT_FILE_REQUEST_CODE);

            return true;

        }

        // openFileChooser for Android 3.0+
        public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType) {

            mUploadMessage = uploadMsg;
            // Create AndroidExampleFolder at sdcard
            // Create AndroidExampleFolder at sdcard

            File imageStorageDir = new File(
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES)
                    , "AndroidExampleFolder");

            if (!imageStorageDir.exists()) {
                // Create AndroidExampleFolder at sdcard
                imageStorageDir.mkdirs();
            }

            // Create camera captured image file path and name
            File file = new File(
                    imageStorageDir + File.separator + "IMG_"
                            + String.valueOf(System.currentTimeMillis())
                            + ".jpg");
            Log.d("File", "File: " + file);
            mCapturedImageURI = Uri.fromFile(file);

            // Camera capture image intent
            final Intent captureIntent = new Intent(
                    android.provider.MediaStore.ACTION_IMAGE_CAPTURE);

            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCapturedImageURI);

            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");

            // Create file chooser intent
            Intent chooserIntent = Intent.createChooser(i, "Image Chooser");

            // Set camera intent to file chooser
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS
                    , new Parcelable[]{captureIntent});

            // On select image call onActivityResult method of activity
            startActivityForResult(chooserIntent, FILECHOOSER_RESULTCODE);


        }

        // openFileChooser for Android < 3.0
        public void openFileChooser(ValueCallback<Uri> uploadMsg) {
            openFileChooser(uploadMsg, "");
        }

        //openFileChooser for other Android versions
        public void openFileChooser(ValueCallback<Uri> uploadMsg,
                                    String acceptType,
                                    String capture) {

            openFileChooser(uploadMsg, acceptType);
        }

    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Check if the key event was the Back button and if there's history
        if ((keyCode == KeyEvent.KEYCODE_BACK) && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        // If it wasn't the Back key or there's no web page history, bubble up to the default
        // system behavior (probably exit the activity)

        return super.onKeyDown(keyCode, event);
    }


    public class PQClient extends WebViewClient {
        ProgressDialog progressDialog;

        public boolean shouldOverrideUrlLoading(WebView view, String url) {

            // If url contains mailto link then open Mail Intent
            if (url.contains("mailto:")) {

                // Could be cleverer and use a regex
                //Open links in new browser
                view.getContext().startActivity(
                        new Intent(Intent.ACTION_VIEW, Uri.parse(url)));

                // Here we can open new activity

                return true;

            } else {

                // Stay within this webview and load url
                view.loadUrl(url);
                return true;
            }
        }

        //Show loader on url load
        public void onPageStarted(WebView view, String url, Bitmap favicon) {

            // Then show progress  Dialog
            // in standard case YourActivity.this
            if (progressDialog == null) {
                progressDialog = new ProgressDialog(MainActivity.this);
                progressDialog.setMessage("Loading...");
                progressDialog.hide();
            }
        }

        // Called when all page resources loaded
        public void onPageFinished(WebView view, String url) {
            webView.loadUrl("javascript:(function(){ " +
                    "document.getElementById('android-app').style.display='none';})()");

            try {
                // Close progressDialog
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                    progressDialog = null;
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            if (requestCode != INPUT_FILE_REQUEST_CODE || mFilePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }

            Uri[] results = null;

            // Check that the response is a good one
            if (resultCode == Activity.RESULT_OK) {
                if (data == null) {
                    // If there is not data, then we may have taken a photo
                    if (mCameraPhotoPath != null) {
                        results = new Uri[]{Uri.parse(mCameraPhotoPath)};
                    }
                } else {
                    String dataString = data.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
            }

            mFilePathCallback.onReceiveValue(results);
            mFilePathCallback = null;

        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            if (requestCode != FILECHOOSER_RESULTCODE || mUploadMessage == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }

            if (requestCode == FILECHOOSER_RESULTCODE) {

                if (null == this.mUploadMessage) {
                    return;

                }

                Uri result = null;

                try {
                    if (resultCode != RESULT_OK) {

                        result = null;

                    } else {

                        // retrieve from the private variable if the intent is null
                        result = data == null ? mCapturedImageURI : data.getData();
                    }
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), "activity :" + e,
                            Toast.LENGTH_LONG).show();
                }

                mUploadMessage.onReceiveValue(result);
                mUploadMessage = null;

            }
        }

        return;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else
            super.onBackPressed();

    }

    public void startSettingActivity() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setData(Uri.parse("package:" + getPackageName()));
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(intent);
    }

    private void checkForPermissions() {
        Permission.PermissionBuilder permissionBuilder =
                new Permission.PermissionBuilder(Pemissions, 100, this)
                        .enableDefaultRationalDialog(getString(R.string.app_name), getString(R.string.req_permission))
                        .enableDefaultSettingDialog(getString(R.string.app_name), getString(R.string.rejected_permission));

        ((PermissionSupportActivity) MainActivity.this).requestAppPermissions(permissionBuilder.build());


    }
}
