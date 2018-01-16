package com.rhaker.reactnativesmsandroid;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.util.Log;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.WritableNativeMap;

import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by azghibarta on 1/15/18.
 */

public class SmsSender {

    private String phoneNumber;
    private ArrayList<String> smsParts;
    private String smsBody;

    private Context context;
    private SmsManager smsManager;

    private String sentAction = "SMS_SENT_";

    private PendingIntent sentPI;

    private BroadcastReceiver sentReceiver;

    private Callback callback;
    private int successSentPartCount = 0;
    private Boolean receiversAreRegistered = false;
    private Boolean isSending = false;
    private Timer timer = new Timer();
    private int deliveryTimeout = 15*1000;

    public SmsSender(
            String mPhoneNumber,
            String mMessageBody,
            int timeout,
            Context mContext,
            Callback mCallback) {
        phoneNumber = mPhoneNumber;
        deliveryTimeout = timeout;
        context = mContext;
        callback = mCallback;

        smsManager = SmsManager.getDefault();
        smsBody = mMessageBody;
        smsParts = this.smsManager.divideMessage(mMessageBody);

        String messageId = SmsSender.generateMessageId();
        sentAction += messageId;

        sentPI = PendingIntent.getBroadcast(context, 0, new Intent(sentAction), 0);

        initializeSentReceiver();
    }

    private static String generateMessageId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        StringBuilder salt = new StringBuilder();
        Random rnd = new Random();
        while (salt.length() < 12) {
            int index = (int) (rnd.nextFloat() * chars.length());
            salt.append(chars.charAt(index));
        }
        return salt.toString();
    }

    private void initializeSentReceiver() {
        sentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d("SENT_REPORT", "Intent: " + action + ", Action: " + sentAction);
                if (!intent.getAction().equals(sentAction)) return;
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        processSmsPartSentSuccess();
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        processSmsPartSentFailure("RESULT_ERROR_GENERIC_FAILURE");
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        processSmsPartSentFailure("RESULT_ERROR_NO_SERVICE");
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        processSmsPartSentFailure("RESULT_ERROR_NULL_PDU");
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        processSmsPartSentFailure("RESULT_ERROR_RADIO_OFF");
                        break;
                }
            }
        };


    }

    private void registerReceivers() {
        if (!receiversAreRegistered) {
            context.registerReceiver(sentReceiver, new IntentFilter(sentAction));
            receiversAreRegistered = true;
        }
    }

    private void unregisterReceivers() {
        if (receiversAreRegistered) {
            context.unregisterReceiver(sentReceiver);
            receiversAreRegistered = false;
        }
    }

    private void processSmsPartSentFailure(String errorMessage) {
        isSending = false;
        timer.purge();
        unregisterReceivers();
        WritableNativeMap result = new WritableNativeMap();
        result.putString("error", errorMessage);
        result.putBoolean("success", false);
        callback.invoke(result);
    }

    private void processSmsPartSentSuccess() {
        successSentPartCount++;
        if (successSentPartCount == smsParts.size()) {
            isSending = false;
            timer.purge();
            unregisterReceivers();

            WritableNativeMap result = new WritableNativeMap();
            result.putBoolean("success", true);
            callback.invoke(result);
        }
    }

    public void sendSmsWithDeliveryReport() {
        if (!isSending) {
            isSending = true;
            registerReceivers();

            ArrayList<PendingIntent> sentPIs = new ArrayList<>();
            for (int i = 0; i < smsParts.size(); i++) {
                sentPIs.add(sentPI);
            }

            smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    smsParts,
                    sentPIs,
                    null
            );

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (isSending) {
                        processSmsPartSentFailure("DELIVERY_TIMEOUT");
                    }
                }
            }, deliveryTimeout);
        }
        else {
            Log.e("SMS_SENDER_ERROR", "Cannot send another message while previous is processing");
        }
    }
}
