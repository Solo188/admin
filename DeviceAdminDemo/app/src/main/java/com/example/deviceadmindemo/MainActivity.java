package com.example.deviceadmindemo;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Главный экран приложения.
 *
 * Показывает:
 * - Статус Device Admin (активен/не активен)
 * - Кнопку активации/деактивации
 * - Кнопку блокировки экрана (работает только если Admin активен)
 */
public class MainActivity extends AppCompatActivity {

    private DevicePolicyManager devicePolicyManager;
    private ComponentName adminComponent;

    // Код запроса для onActivityResult
    private static final int REQUEST_ENABLE_ADMIN = 1;

    private TextView statusText;
    private Button btnToggleAdmin;
    private Button btnLockScreen;
    private Button btnCameraDisable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Инициализация системного сервиса
        devicePolicyManager = (DevicePolicyManager)
                getSystemService(Context.DEVICE_POLICY_SERVICE);

        // Компонент нашего AdminReceiver
        adminComponent = new ComponentName(this, AdminReceiver.class);

        // UI элементы
        statusText      = findViewById(R.id.statusText);
        btnToggleAdmin  = findViewById(R.id.btnToggleAdmin);
        btnLockScreen   = findViewById(R.id.btnLockScreen);
        btnCameraDisable = findViewById(R.id.btnCameraDisable);

        // Кнопка активации/деактивации
        btnToggleAdmin.setOnClickListener(v -> {
            if (isAdminActive()) {
                deactivateAdmin();
            } else {
                activateAdmin();
            }
        });

        // Кнопка блокировки экрана
        btnLockScreen.setOnClickListener(v -> {
            if (isAdminActive()) {
                // Мгновенная блокировка экрана — без задержки
                devicePolicyManager.lockNow();
            } else {
                statusText.setText("Сначала активируйте администратора");
            }
        });

        // Кнопка отключения камеры
        btnCameraDisable.setOnClickListener(v -> {
            if (isAdminActive()) {
                // Проверяем текущее состояние и переключаем
                boolean cameraDisabled = devicePolicyManager
                        .getCameraDisabled(adminComponent);
                devicePolicyManager.setCameraDisabled(adminComponent, !cameraDisabled);
                updateUI();
            } else {
                statusText.setText("Сначала активируйте администратора");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Обновляем UI каждый раз когда возвращаемся на экран
        updateUI();
    }

    /**
     * Проверяем активен ли Device Admin
     */
    private boolean isAdminActive() {
        return devicePolicyManager.isAdminActive(adminComponent);
    }

    /**
     * Открываем системный диалог активации Device Admin
     */
    private void activateAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Это демо-приложение Device Admin API.\n" +
                "После активации приложение сможет:\n" +
                "- Блокировать экран\n" +
                "- Отключать камеру\n" +
                "- Следить за попытками разблокировки"
        );
        startActivityForResult(intent, REQUEST_ENABLE_ADMIN);
    }

    /**
     * Деактивируем Device Admin программно
     */
    private void deactivateAdmin() {
        devicePolicyManager.removeActiveAdmin(adminComponent);
        updateUI();
    }

    /**
     * Обновляем UI в зависимости от статуса
     */
    private void updateUI() {
        boolean active = isAdminActive();

        if (active) {
            boolean cameraDisabled = devicePolicyManager
                    .getCameraDisabled(adminComponent);

            statusText.setText(
                    "Статус: АКТИВЕН\n\n" +
                    "Камера: " + (cameraDisabled ? "ОТКЛЮЧЕНА" : "включена") + "\n" +
                    "Удалить приложение: НЕВОЗМОЖНО\n" +
                    "Принудительная блокировка: доступна"
            );
            btnToggleAdmin.setText("Деактивировать Admin");
            btnLockScreen.setEnabled(true);
            btnCameraDisable.setEnabled(true);
            btnCameraDisable.setText(cameraDisabled
                    ? "Включить камеру" : "Отключить камеру");
        } else {
            statusText.setText(
                    "Статус: не активен\n\n" +
                    "Приложение работает как обычное.\n" +
                    "Активируйте администратора чтобы\n" +
                    "разблокировать функции."
            );
            btnToggleAdmin.setText("Активировать Admin");
            btnLockScreen.setEnabled(false);
            btnCameraDisable.setEnabled(false);
            btnCameraDisable.setText("Отключить камеру");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_ADMIN) {
            updateUI();
        }
    }
}
