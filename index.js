import { NativeModules, DeviceEventEmitter } from "react-native";
import type CancellableSubscription from "./js/CancellableSubscription";
import type ReceivedSmsMessage from "./js/ReceivedSmsMessage";

const SMS_RECEIVED_EVENT = "com.rhaker.reactnativesmsandroid:smsReceived";

export function addSmsListener(listener: (message: ReceivedSmsMessage) => void): CancellableSubscription {
    return DeviceEventEmitter.addListener(
	SMS_RECEIVED_EVENT,
	listener
    );
}

module.exports = NativeModules.SmsAndroid
