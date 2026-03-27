package com.example.deviceowner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d("BootReceiver", "Телефон перезагружен — приложение запущено");
            // Запускаем главный экран в фоне если нужно
            // Или просто логируем факт загрузки
        }
    }
}
