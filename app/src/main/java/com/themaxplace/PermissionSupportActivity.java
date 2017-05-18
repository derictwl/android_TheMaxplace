package com.themaxplace;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.PopupWindow;
import android.widget.Toast;


public abstract class PermissionSupportActivity extends AppCompatActivity{


    private Permission.PermissionCallback mPermissionCallback = null;
    private Permission mPermission = null;
    private boolean doubleBackToExitPressedOnce;
    private PopupWindow pw;
    private boolean isBackAllowed = true;

    private static final String TAG = "PermissionSupportActivi";
    private boolean isNotifcations = true;

    public void requestAppPermissions(@NonNull Permission permission) {
        mPermission = permission;
        mPermissionCallback = mPermission.getPermissionCallback();
        requestAppPermissions(mPermission.getRequestedPermissions(), mPermission.getRequestCode(), mPermission.getPermissionCallback());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (verifyPermissions(grantResults)) {
            mPermissionCallback.onPermissionGranted(requestCode);
        } else {
            boolean showRationale = shouldShowRequestPermissionRationale(permissions);
            if (!showRationale) {
                doNotAskedEnable(requestCode);
            } else {
                showRationalMessage(requestCode);
            }
        }

    }

    private void requestAppPermissions(final String[] requestedPermissions, final int requestCode, @Nullable Permission.PermissionCallback permissionCallback) {
        if (!hasPermissions(requestedPermissions)) {
            if (shouldShowRequestPermissionRationale(requestedPermissions)) {
                showRationalMessage(requestCode);
            } else {
                ActivityCompat.requestPermissions(PermissionSupportActivity.this, requestedPermissions, requestCode);
            }
        } else {
            mPermissionCallback.onPermissionGranted(requestCode);
        }
    }

    protected boolean hasPermissions(String[] permissions) {
        int length = permissions.length;
        for (int i = 0; i < length; i++) {
            if (ContextCompat.checkSelfPermission(this, permissions[i])
                    != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    public boolean shouldShowRequestPermissionRationale(String[] permissions) {
        int length = permissions.length;
        for (int i = 0; i < length; i++) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i]))
                return true;
        }
        return false;
    }

    private boolean verifyPermissions(int[] grantResults) {
        // At least one result must be checked.
        if (grantResults.length < 1)
            return false;

        // Verify that each required permission has been granted, otherwise return false.
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }


    private void showRequestPermissionDialog() {
        String message = mPermission.getRationalDialogMessage();
        String positiveButton = getString(R.string.rational_permission_proceed);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage(message);

        if (!TextUtils.isEmpty(mPermission.getRationalDialogTitle())) {
            alertDialogBuilder.setTitle(mPermission.getRationalDialogTitle());

        }
        alertDialogBuilder.setPositiveButton(positiveButton, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                ActivityCompat.requestPermissions(PermissionSupportActivity.this, mPermission.getRequestedPermissions(), mPermission.getRequestCode());
            }
        });
        alertDialogBuilder.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
        alertDialog.setCancelable(false);
        alertDialog.setCanceledOnTouchOutside(false);

    }


    private void showRationalMessage(int requestCode) {

        if (mPermission.isShowCustomRationalDialog()) {
            mPermissionCallback.onPermissionDenied(requestCode);
        } else {
            showRequestPermissionDialog();
        }
    }

    private void doNotAskedEnable(int requestCode) {

        if (mPermission.isShowCustomSettingDialog()) {
            mPermissionCallback.onPermissionAccessRemoved(requestCode);
        } else {
            showSettingsPermissionDialog();
        }
    }

    private void showSettingsPermissionDialog() {

        String message = mPermission.getSettingDialogMessage();
        String positiveButton = getString(R.string.permission_string_btn);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage(message);

        if (!TextUtils.isEmpty(mPermission.getSettingDialogTitle())) {
            alertDialogBuilder.setTitle(mPermission.getSettingDialogTitle());

        }
        alertDialogBuilder.setPositiveButton(positiveButton, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                startSettingActivity();
            }
        });

        alertDialogBuilder.setCancelable(false);
        alertDialogBuilder.show();

    }

    public void startSettingActivity() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setData(Uri.parse("package:" + getPackageName()));
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivityForResult(intent, mPermission.getRequestCode());
    }

    @Override
    public void onBackPressed() {


        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        }
        if (!isBackAllowed) {
            if (doubleBackToExitPressedOnce) {
                finish();
                return;
            }

            this.doubleBackToExitPressedOnce = true;
            Toast.makeText(this, "Please click BACK again to exit", Toast.LENGTH_SHORT).show();

            new Handler().postDelayed(new Runnable() {

                @Override
                public void run() {
                    doubleBackToExitPressedOnce = false;
                }
            }, 2000);
        } else {
            super.onBackPressed();
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mPermission != null) {
            if (requestCode == mPermission.getRequestCode()) {
                if (hasPermissions(mPermission.getRequestedPermissions())) {
                    if (mPermissionCallback != null) {
                        mPermissionCallback.onPermissionGranted(mPermission.getRequestCode());
                    }
                } else {
                    if (mPermissionCallback != null) {
                        mPermissionCallback.onPermissionDenied(mPermission.getRequestCode());
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);

    }

    public boolean isBackAllowed() {
        return isBackAllowed;
    }

    public void setBackAllowed(boolean backAllowed) {
        isBackAllowed = backAllowed;
    }

    public void initiatePopupWindow(View v) {
//        try {
//            //We need to get the instance of the LayoutInflater, use the context of this activity
//            LayoutInflater inflater = (LayoutInflater) PermissionSupportActivity.this
//                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//            //Inflate the view from a predefined XML layout
//            View layout = inflater.inflate(R.layout.item_recent_chat,
//                    new FrameLayout(PermissionSupportActivity.this));
//            // create a 300px width and 470px height PopupWindow
//            pw = new PopupWindow(layout, 300, 470, true);
//            // display the popup in the center
//            pw.showAtLocation(v,Gravity.TOP,50,50);
//
////            TextView mResultText = (TextView) layout.findViewById(R.id.server_status_text);
////            Button cancelButton = (Button) layout.findViewById(R.id.end_data_send_button);
////            cancelButton.setOnClickListener(cancel_button_click_listener);
////
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    private void showInternetPopup(String message) {
//
//        Dialog dialog = new Dialog(this, R.style.PauseDialog);
//        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
//        dialog.setContentView(R.layout.dialog_notification);
//        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
//        dialog.setCancelable(false);
//        dialog.setCanceledOnTouchOutside(false);
//        ((TextView) dialog.findViewById(R.id.tv_notification)).setText(message);
//
//        LinearLayout linearLayout = (LinearLayout) dialog.findViewById(R.id.ll_notification);
//
//        Window window = dialog.getWindow();
//        WindowManager.LayoutParams wlp = window.getAttributes();
//
//        wlp.width = WindowManager.LayoutParams.FILL_PARENT;
//        wlp.gravity = Gravity.TOP;
//        wlp.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
//        window.setAttributes(wlp);
//
//        if (dialog.isShowing())
//            dialog.cancel();
//
//        dialog.show();
//
//        linearLayout.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//
//
//            }
//        });

    }

    public void hideNotifications() {
        isNotifcations = false;
    }


    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}