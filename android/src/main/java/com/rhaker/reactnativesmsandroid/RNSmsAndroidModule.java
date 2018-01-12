package com.rhaker.reactnativesmsandroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.util.Log;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.lang.SecurityException;
import java.lang.String;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.LifecycleEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RNSmsAndroidModule extends ReactContextBaseJavaModule implements LifecycleEventListener {
    public static final String TAG = RNSmsAndroidModule.class.getSimpleName();

    private ReactApplicationContext reactContext;

    private BroadcastReceiver mReceiver;
    private boolean isReceiverRegistered = false;
    
    // set the activity - pulled in from Main
    public RNSmsAndroidModule(ReactApplicationContext reactContext) {
      super(reactContext);

      this.reactContext = reactContext;

      mReceiver = new SmsReceiver(reactContext);
      getReactApplicationContext().addLifecycleEventListener(this);
      registerReceiverIfNecessary(mReceiver);
    }

    @Override
    public String getName() {
      return "SmsAndroid";
    }
    
    private void registerReceiverIfNecessary(BroadcastReceiver receiver) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && getCurrentActivity() != null) {
            getCurrentActivity().registerReceiver(
						  receiver,
						  new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
						  );
            isReceiverRegistered = true;
            return;
        }
	
        if (getCurrentActivity() != null) {
            getCurrentActivity().registerReceiver(
						  receiver,
						  new IntentFilter("android.provider.Telephony.SMS_RECEIVED")
						  );
            isReceiverRegistered = true;
        }
    }

    private void unregisterReceiver(BroadcastReceiver receiver) {
        if (isReceiverRegistered && getCurrentActivity() != null) {
            getCurrentActivity().unregisterReceiver(receiver);
            isReceiverRegistered = false;
        }
    }
    
    @Override
    public void onHostResume() {
        registerReceiverIfNecessary(mReceiver);
    }
    
    @Override
    public void onHostPause() {
        unregisterReceiver(mReceiver);
    }
    
    @Override
    public void onHostDestroy() {
        unregisterReceiver(mReceiver);
    }
    
    @ReactMethod
    public void sms(String phoneNumberString, String body, String sendType, Callback callback) {

        // send directly if user requests and android greater than 4.4
        if ((sendType.equals("sendDirect")) && (body != null) && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)) {

            try {
                SmsManager smsManager = SmsManager.getDefault();
                ArrayList<String> smsParts = smsManager.divideMessage(body);
                smsManager.sendMultipartTextMessage(phoneNumberString, null, smsParts, null, null);
                callback.invoke(null,"success");
            }

            catch (Exception e) {
                callback.invoke(null,"error");
                e.printStackTrace();
            }

        } else {

            // launch default sms package, user hits send
            Intent sendIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("sms:" + phoneNumberString.trim()));
            sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (body != null) {
                sendIntent.putExtra("sms_body", body);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                String defaultSmsPackageName = Telephony.Sms.getDefaultSmsPackage(getCurrentActivity());
                if (defaultSmsPackageName != null) {
                    sendIntent.setPackage(defaultSmsPackageName);
                }
            }

            try {
                this.reactContext.startActivity(sendIntent);
                callback.invoke(null,"success");
            }

            catch (Exception e) {
                callback.invoke(null,"error");
                e.printStackTrace();
            }

        }

    }

    @ReactMethod
    public void checkIfIsDefaultSmsApp(Promise promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                final String myPackageName = getReactApplicationContext().getPackageName();
                promise.resolve(
                        Telephony.Sms.getDefaultSmsPackage(
                                getCurrentActivity()
                        ).equals(myPackageName)
                );
            }
            else {
                promise.resolve(true);
            }
        }
        catch (Exception ex) {
            promise.reject("default_sms_app_check_error", ex);
        }
    }

    @ReactMethod
    public void askDefaultSmsAppCapabilities(final Promise promise) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                final String myPackageName = getReactApplicationContext().getPackageName();

                AlertDialog.Builder builder = new AlertDialog.Builder(getCurrentActivity());
                builder.setMessage("Please, accept this app as default SMS application");
                builder.setCancelable(false);

                builder.setPositiveButton(
                        "Accept",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                        Intent intent =
                                                new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                                        intent.putExtra(
                                                Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
                                                myPackageName
                                        );
                                        getCurrentActivity().startActivity(intent);
                                    }
                                    promise.resolve("asked");
                                }
                                catch (Exception ex) {
                                    promise.reject("sms_default_app_dialog_error", ex);
                                }
                                dialog.cancel();
                            }
                        }
                );

                builder.setNegativeButton(
                        "Cancel",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                promise.reject(
                                        "sms_default_app_dialog_rejected",
                                        new Exception("User rejected dialog")
                                );
                                dialog.cancel();
                            }
                        }
                );

                builder.show();
            }
            else {
                promise.resolve("skd_not_require_this");
            }
        }
        catch (Exception ex) {
            promise.reject("sms_default_app_dialog_error", ex);
        }
    }

    @ReactMethod
    public void markAsRead(String id, Promise promise) {
        try {
            ContentValues values = new ContentValues();
            values.put("read", "1");

            Uri uri = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ?
                    Telephony.Sms.Inbox.CONTENT_URI : Uri.parse("content://sms/inbox");

            getReactApplicationContext().getContentResolver().update(uri, values, "_id="+id, null);

            promise.resolve("success");
        }
        catch (Exception ex) {
            promise.reject("mark_as_read_error", ex);
        }
    }

    @ReactMethod
    public void list(String filter, final Callback errorCallback, final Callback successCallback) {
        try{
            JSONObject filterJ = new JSONObject(filter);
            String uri_filter = filterJ.has("box") ? filterJ.optString("box") : "inbox";
            int fread = filterJ.has("read") ? filterJ.optInt("read") : -1;
            int fid = filterJ.has("_id") ? filterJ.optInt("_id") : -1;
            String faddress = filterJ.optString("address");
            String fcontent = filterJ.optString("body");
            int indexFrom = filterJ.has("indexFrom") ? filterJ.optInt("indexFrom") : 0;
            int maxCount = filterJ.has("maxCount") ? filterJ.optInt("maxCount") : -1;
            Cursor cursor = getCurrentActivity().getContentResolver().query(Uri.parse("content://sms/"+uri_filter), null, "", null, null);
            int c = 0;
            JSONArray jsons = new JSONArray();
            while (cursor.moveToNext()) {
                boolean matchFilter = false;
                if (fid > -1)
                matchFilter = fid == cursor.getInt(cursor.getColumnIndex("_id"));
                else if (fread > -1)
                matchFilter = fread == cursor.getInt(cursor.getColumnIndex("read"));
                else if (faddress.length() > 0)
                matchFilter = faddress.equals(cursor.getString(cursor.getColumnIndex("address")).trim());
                else if (fcontent.length() > 0)
                matchFilter = fcontent.equals(cursor.getString(cursor.getColumnIndex("body")).trim());
                else {
                    matchFilter = true;
                }
                if (matchFilter)
                {
                    if (c >= indexFrom) {
                        if (maxCount>0 && c >= indexFrom + maxCount) break;
                        c++;
                        // Long dateTime = Long.parseLong(cursor.getString(cursor.getColumnIndex("date")));
                        // String message = cursor.getString(cursor.getColumnIndex("body"));
                        JSONObject json;
                        json = getJsonFromCursor(cursor);
                        jsons.put(json);

                    }
                }

            }
            cursor.close();
            try {
                successCallback.invoke(c, jsons.toString());
            } catch (Exception e) {
                errorCallback.invoke(e.getMessage());
            }
        } catch (JSONException e)
        {
            errorCallback.invoke(e.getMessage());
            return;
        }
    }

    private JSONObject getJsonFromCursor(Cursor cur) {
        JSONObject json = new JSONObject();

        int nCol = cur.getColumnCount();
        String[] keys = cur.getColumnNames();
        try
        {
            for (int j = 0; j < nCol; j++)
            switch (cur.getType(j)) {
                case 0:
                json.put(keys[j], null);
                break;
                case 1:
                json.put(keys[j], cur.getLong(j));
                break;
                case 2:
                json.put(keys[j], cur.getFloat(j));
                break;
                case 3:
                json.put(keys[j], cur.getString(j));
                break;
                case 4:
                json.put(keys[j], cur.getBlob(j));
            }
        }
        catch (Exception e)
        {
            return null;
        }

        return json;
    }
}
