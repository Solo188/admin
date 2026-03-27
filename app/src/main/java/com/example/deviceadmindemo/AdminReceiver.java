package com.example.deviceadmindemo;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class AdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "AdminReceiver";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.d(TAG, "Device Admin АКТИВИРОВАН");
        Toast.makeText(context, "Администратор активирован", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.d(TAG, "Device Admin ДЕАКТИВИРОВАН");
        Toast.makeText(context, "Администратор деактивирован", Toast.LENGTH_SHORT).show();
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        Log.d(TAG, "Запрос на деактивацию");
        return "Вы уверены? Приложение потеряет права администратора.";
    }

    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        super.onPasswordFailed(context, intent);
        Log.d(TAG, "Неверный пароль разблокировки");
    }

    @Override
    public void onPasswordSucceeded(Context context, Intent intent) {
        super.onPasswordSucceeded(context, intent);
        Log.d(TAG, "Пароль введён успешно");
    }
}
