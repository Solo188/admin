package com.example.deviceowner;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private DevicePolicyManager dpm;
    private ComponentName adminComponent;
    private static final int REQUEST_ENABLE_ADMIN = 1;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, AdminReceiver.class);
        statusText = findViewById(R.id.statusText);

        // Активация Device Admin
        findViewById(R.id.btnActivateAdmin).setOnClickListener(v -> activateAdmin());

        // Блокировка экрана
        findViewById(R.id.btnLock).setOnClickListener(v -> {
            if (checkAdmin()) dpm.lockNow();
        });

        // Смена пароля (только Device Owner)
        findViewById(R.id.btnSetPassword).setOnClickListener(v -> {
            if (!checkOwner()) return;
            EditText input = new EditText(this);
            input.setHint("Новый пин (мин. 4 символа)");
            input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                    android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            new AlertDialog.Builder(this)
                    .setTitle("Установить пароль")
                    .setView(input)
                    .setPositiveButton("Установить", (d, w) -> {
                        String pass = input.getText().toString();
                        if (pass.length() >= 4) {
                            try {
                                dpm.resetPassword(pass, 0);
                                toast("Пароль установлен: " + pass);
                            } catch (Exception e) {
                                toast("Ошибка: " + e.getMessage());
                            }
                        } else {
                            toast("Минимум 4 символа");
                        }
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        });

        // Отключить/включить камеру
        findViewById(R.id.btnCamera).setOnClickListener(v -> {
            if (!checkOwner()) return;
            boolean disabled = dpm.getCameraDisabled(adminComponent);
            dpm.setCameraDisabled(adminComponent, !disabled);
            toast("Камера: " + (disabled ? "ВКЛЮЧЕНА" : "ОТКЛЮЧЕНА"));
            updateUI();
        });

        // Запрет скриншотов
        findViewById(R.id.btnScreenshot).setOnClickListener(v -> {
            if (!checkOwner()) return;
            boolean disabled = dpm.getScreenCaptureDisabled(adminComponent);
            dpm.setScreenCaptureDisabled(adminComponent, !disabled);
            toast("Скриншоты: " + (disabled ? "РАЗРЕШЕНЫ" : "ЗАПРЕЩЕНЫ"));
            updateUI();
        });

        // Автовыдача разрешения микрофона себе
        findViewById(R.id.btnGrantMic).setOnClickListener(v -> {
            if (!checkOwner()) return;
            try {
                dpm.setPermissionGrantState(
                        adminComponent,
                        getPackageName(),
                        android.Manifest.permission.RECORD_AUDIO,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                );
                toast("Микрофон выдан без диалога");
            } catch (Exception e) {
                toast("Ошибка: " + e.getMessage());
            }
        });

        // Перезагрузка устройства
        findViewById(R.id.btnReboot).setOnClickListener(v -> {
            if (!checkOwner()) return;
            new AlertDialog.Builder(this)
                    .setTitle("Перезагрузка")
                    .setMessage("Перезагрузить устройство?")
                    .setPositiveButton("Да", (d, w) -> {
                        try {
                            dpm.reboot(adminComponent);
                        } catch (Exception e) {
                            toast("Ошибка: " + e.getMessage());
                        }
                    })
                    .setNegativeButton("Нет", null)
                    .show();
        });

        // Сброс к заводским
        findViewById(R.id.btnWipe).setOnClickListener(v -> {
            if (!checkAdmin()) return;
            new AlertDialog.Builder(this)
                    .setTitle("⚠️ СБРОС К ЗАВОДСКИМ")
                    .setMessage("ВСЕ ДАННЫЕ БУДУТ УДАЛЕНЫ!\nЭто необратимо. Продолжить?")
                    .setPositiveButton("СБРОСИТЬ", (d, w) -> dpm.wipeData(0))
                    .setNegativeButton("Отмена", null)
                    .show();
        });

        // Деактивировать Admin
        findViewById(R.id.btnDeactivate).setOnClickListener(v -> {
            dpm.removeActiveAdmin(adminComponent);
            updateUI();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    private boolean checkAdmin() {
        if (!dpm.isAdminActive(adminComponent)) {
            toast("Сначала активируйте Device Admin");
            return false;
        }
        return true;
    }

    private boolean checkOwner() {
        if (!dpm.isDeviceOwnerApp(getPackageName())) {
            toast("Требуется Device Owner!\nВыполни в LADB:\ndpm set-device-owner " +
                    getPackageName() + "/.AdminReceiver");
            return false;
        }
        return true;
    }

    private void activateAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Device Owner Demo — полный контроль над устройством");
        startActivityForResult(intent, REQUEST_ENABLE_ADMIN);
    }

    private void updateUI() {
        boolean isAdmin = dpm.isAdminActive(adminComponent);
        boolean isOwner = dpm.isDeviceOwnerApp(getPackageName());
        boolean cameraOff = isAdmin && dpm.getCameraDisabled(adminComponent);
        boolean screenshotOff = isOwner && dpm.getScreenCaptureDisabled(adminComponent);

        statusText.setText(
                "Device Admin:  " + (isAdmin ? "✅ АКТИВЕН" : "❌ не активен") + "\n" +
                "Device Owner:  " + (isOwner ? "✅ АКТИВЕН" : "❌ не активен") + "\n\n" +
                "Камера:        " + (cameraOff ? "🚫 отключена" : "✅ включена") + "\n" +
                "Скриншоты:     " + (screenshotOff ? "🚫 запрещены" : "✅ разрешены") + "\n\n" +
                (isOwner ? "Все функции доступны" :
                        "Device Owner недоступен\nИспользуй LADB:\ndpm set-device-owner " +
                        getPackageName() + "/.AdminReceiver")
        );
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQUEST_ENABLE_ADMIN) updateUI();
    }
}
