package com.rhaker.reactnativesmsandroid;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.SmsManager;
import android.util.Log;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.WritableNativeMap;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by azghibarta on 1/15/18.
 */

public class SmsSender {

    private String phoneNumber;
    private ArrayList<String> smsParts;

    private Context context;
    private SmsManager smsManager;

    private final String sentAction = "SMS_SENT";
    private final String deliveredAction = "SMS_DELIVERED";

    private PendingIntent sentPI;
    private PendingIntent deliveredPI;

    private BroadcastReceiver sentReceiver;
    private BroadcastReceiver deliverReceiver;

    private Callback callback;
    private int successSentPartCount = 0;
    private Boolean receiversAreRegistered = false;
    private Boolean isSending = false;
    private Timer timer = new Timer();
    private int deliveryTimeout = 15*1000;
    private Boolean needToWaitDeliveryReport = true;

    public SmsSender(
            String mPhoneNumber,
            String mMessageBody,
            Boolean waitDelivery,
            int timeout,
            Context mContext,
            Callback mCallback) {
        phoneNumber = mPhoneNumber;
        needToWaitDeliveryReport = waitDelivery;
        deliveryTimeout = timeout;
        context = mContext;
        callback = mCallback;

        smsManager = SmsManager.getDefault();
        smsParts = this.smsManager.divideMessage(mMessageBody);

        sentPI = PendingIntent.getBroadcast(context, 0, new Intent(sentAction), 0);
        deliveredPI = PendingIntent.getBroadcast(context, 0, new Intent(deliveredAction), 0);

        initializeSentReceiver();
        initializeDeliveryReceiver();
    }

    private void initializeSentReceiver() {
        sentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        if (!needToWaitDeliveryReport) {
                            processSmsPartSentSuccess();
                        }
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

    private void initializeDeliveryReceiver() {
        deliverReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (getResultCode()) {
                    case Activity.RESULT_OK:
                        processSmsPartSentSuccess();
                        break;
                    case Activity.RESULT_CANCELED:
                        processSmsPartSentFailure("DELIVERY_FAILED");
                        break;
                }
            }
        };
    }

    private void registerReceivers() {
        if (!receiversAreRegistered) {
            context.registerReceiver(sentReceiver, new IntentFilter(sentAction));
            if (needToWaitDeliveryReport) {
                context.registerReceiver(deliverReceiver, new IntentFilter(deliveredAction));
            }
            receiversAreRegistered = true;
        }
    }

    private void unregisterReceivers() {
        if (receiversAreRegistered) {
            context.unregisterReceiver(sentReceiver);
            if (needToWaitDeliveryReport) {
                context.unregisterReceiver(deliverReceiver);
            }
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
            ArrayList<PendingIntent> deliveredPIs = new ArrayList<>();
            for (int i = 0; i < smsParts.size(); i++) {
                sentPIs.add(sentPI);
                deliveredPIs.add(deliveredPI);
            }

            smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    smsParts,
                    sentPIs,
                    deliveredPIs
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
