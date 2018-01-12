package com.rhaker.reactnativesmsandroid;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmsReceiver extends BroadcastReceiver {
    private ReactApplicationContext mContext;

    private static final String EVENT = "com.rhaker.reactnativesmsandroid:smsReceived";

    public SmsReceiver() {
        super();
    }

    public SmsReceiver(ReactApplicationContext context) {
        mContext = context;
    }

    private boolean saveMessageToInbox(String phone, String body, String readState) {
        try {
            ContentValues values = new ContentValues();
            values.put("address", phone);
            values.put("body", body);
            values.put("read", readState);

            Uri uri = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ?
                    Telephony.Sms.Inbox.CONTENT_URI : Uri.parse("content://sms/inbox");
            mContext.getApplicationContext().getContentResolver().insert(uri, values);

            return true;
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private void processMessages(SmsMessage[] messages) {
        if (messages.length == 0) {
            return;
        }
        if (mContext == null) {
            return;
        }
        if (! mContext.hasActiveCatalystInstance()) {
            return;
        }

        Map<String, String> messageInfos = new HashMap<String, String>();

        for (SmsMessage message : messages) {
            String originatingAddress = message.getOriginatingAddress();
            String messageBody = message.getMessageBody();

            Log.d(RNSmsAndroidModule.TAG, String.format("%s: %s", originatingAddress, messageBody));

            if (!messageInfos.containsKey(originatingAddress)) {
                messageInfos.put(originatingAddress, messageBody);
            }
            else {
                String previousParts = messageInfos.get(originatingAddress);
                String updatedMessage = previousParts + messageBody;
                messageInfos.put(originatingAddress, updatedMessage);
            }
        }

        WritableNativeArray results = new WritableNativeArray();

        for (String key : messageInfos.keySet()) {
            String messageBody = messageInfos.get(key);

            if (saveMessageToInbox(key, messageBody, "0")) {
                WritableNativeMap infoMap = new WritableNativeMap();
                infoMap.putString("originatingAddress", key);
                infoMap.putString("body", messageBody);

                results.pushMap(infoMap);
            }
        }

        if (results.size() > 0) {
            mContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(EVENT, results);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            processMessages(Telephony.Sms.Intents.getMessagesFromIntent(intent));
            return;
        }

        try {
            final Bundle bundle = intent.getExtras();

            if (bundle == null || ! bundle.containsKey("pdus")) {
                return;
            }

            final Object[] pdus = (Object[]) bundle.get("pdus");
            if (pdus != null && pdus.length > 0) {
                ArrayList<SmsMessage> messages = new ArrayList<SmsMessage>();
                for (Object pdu : pdus) {
                    SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdu);
                    messages.add(msg);
                }
                processMessages((SmsMessage[]) messages.toArray());
            }
        } catch (Exception e) {
            Log.e(RNSmsAndroidModule.TAG, e.getMessage());
        }
    }
}
