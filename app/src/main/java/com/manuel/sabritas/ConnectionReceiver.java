package com.manuel.sabritas;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class ConnectionReceiver extends BroadcastReceiver {
    public static ReceiverListener receiverListener;

    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (receiverListener != null) {
            boolean isConnected = networkInfo != null && networkInfo.isConnectedOrConnecting();
            receiverListener.onNetworkChange(isConnected);
        }
    }

    public interface ReceiverListener {
        void onNetworkChange(boolean isConnected);
    }
}