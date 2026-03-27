package com.example.deviceadmindemo;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

/**
 * AdminReceiver — главный компонент Device Admin.
 *
 * Получает системные события:
 * - Активация прав администратора
 * - Деактивация
 * - Попытка деактивации
 * - Неверный пароль разблокировки
 * - Превышение лимита попыток пароля
 */
public class AdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "AdminReceiver";

    /**
     * Вызывается когда пользователь активировал Device Admin
     */
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.d(TAG, "Device Admin АКТИВИРОВАН");
        Toast.makeText(context, "Администратор активирован", Toast.LENGTH_SHORT).show();
    }

    /**
     * Вызывается когда пользователь деактивировал Device Admin
     */
    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.d(TAG, "Device Admin ДЕАКТИВИРОВАН");
        Toast.makeText(context, "Администратор деактивирован", Toast.LENGTH_SHORT).show();
    }

    /**
     * Вызывается перед деактивацией — можно показать предупреждение
     * Возвращаем строку которую увидит пользователь
     */
    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        Log.d(TAG, "Запрос на деактивацию");
        return "Вы уверены? Приложение потеряет права администратора.";
    }

    /**
     * Пользователь ввёл неверный пароль разблокировки
     */
    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        super.onPasswordFailed(context, intent);
        Log.d(TAG, "Неверный пароль разблокировки");
    }

    /**
     * Пользователь ввёл правильный пароль после неудачных попыток
     */
    @Override
    public void onPasswordSucceeded(Context context, Intent intent) {
        super.onPasswordSucceeded(context, intent);
        Log.d(TAG, "Пароль введён успешно");
    }

    /**
     * Превышен лимит попыток — устройство заблокировано
     */
    @Override
    public void onPasswordExpired(Context context, Intent intent) {
        super.onPasswordExpired(context, intent);
        Log.d(TAG, "Пароль просрочен");
    }
}
