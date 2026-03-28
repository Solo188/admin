package com.example.deviceadmindemo;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private DevicePolicyManager dpm;
    private ComponentName       adminComponent;
    private TextView            statusText;
    private String              currentPassword;
    private File                pendingPhotoFile;

    private static final int REQ_ADMIN     = 1;
    private static final int REQ_CAMERA    = 2;
    private static final int REQ_PICK_FILE = 3;
    private static final int REQ_PERMS     = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dpm            = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, AdminReceiver.class);
        statusText     = findViewById(R.id.statusText);

        requestPerms();

        // Device Admin
        findViewById(R.id.btnActivateAdmin).setOnClickListener(v -> activateAdmin());
        findViewById(R.id.btnLock).setOnClickListener(v -> {
            if (dpm.isAdminActive(adminComponent)) dpm.lockNow();
            else toast("Сначала активируйте Admin");
        });
        findViewById(R.id.btnDeactivate).setOnClickListener(v -> {
            dpm.removeActiveAdmin(adminComponent);
            updateUI();
        });

        // Пароль шифрования
        findViewById(R.id.btnSetEncPassword).setOnClickListener(v -> showSetPasswordDialog());

        // Выбрать любой файл
        findViewById(R.id.btnPickFile).setOnClickListener(v -> {
            if (!checkPassword()) return;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(i, "Выберите файл"), REQ_PICK_FILE);
        });

        // Камера
        findViewById(R.id.btnCamera).setOnClickListener(v -> {
            if (!checkPassword()) return;
            openCamera();
        });

        // Расшифровать
        findViewById(R.id.btnDecrypt).setOnClickListener(v -> {
            if (!checkPassword()) return;
            pickFileForDecrypt();
        });
    }

    // ─── Пароль ──────────────────────────────────────────────────────────────

    private void showSetPasswordDialog() {
        EditText input = new EditText(this);
        input.setHint("Пароль (мин. 4 символа)");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Пароль шифрования")
                .setMessage("Без этого пароля файлы невозможно восстановить.")
                .setView(input)
                .setPositiveButton("Установить", (d, w) -> {
                    String p = input.getText().toString().trim();
                    if (p.length() >= 4) {
                        currentPassword = p;
                        toast("Пароль установлен");
                        updateUI();
                    } else toast("Минимум 4 символа");
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private boolean checkPassword() {
        if (currentPassword == null) {
            toast("Сначала установите пароль шифрования");
            return false;
        }
        return true;
    }

    // ─── Шифрование ──────────────────────────────────────────────────────────

    /**
     * Получили реальный путь к файлу — показываем диалог подтверждения.
     * После шифрования оригинал заменяется на .enc в той же папке.
     */
    private void confirmAndEncrypt(File file) {
        new AlertDialog.Builder(this)
                .setTitle("Зашифровать?")
                .setMessage(
                        "Файл: " + file.getName() + "\n" +
                        "Папка: " + file.getParent() + "\n" +
                        "Размер: " + formatSize(file.length()) + "\n\n" +
                        "Оригинал будет ЗАМЕНЁН на:\n" +
                        file.getName() + ".enc\n\n" +
                        "Восстановить можно только паролем."
                )
                .setPositiveButton("Зашифровать", (d, w) -> {
                    new Thread(() -> {
                        try {
                            File enc = CryptoManager.encryptInPlace(file, currentPassword);
                            // Уведомляем MediaStore что файл изменился
                            sendBroadcast(new Intent(
                                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                    Uri.fromFile(enc)));
                            runOnUiThread(() -> {
                                toast("✅ Зашифровано: " + enc.getName());
                                updateUI();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() ->
                                toast("❌ Ошибка: " + e.getMessage()));
                        }
                    }).start();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ─── Расшифровка ─────────────────────────────────────────────────────────

    private void pickFileForDecrypt() {
        // Выбираем .enc файл
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        // Подсказка пользователю
        startActivityForResult(
                Intent.createChooser(i, "Выберите .enc файл для расшифровки"),
                REQ_PICK_FILE + 100);
    }

    private void decryptFile(File encFile) {
        new AlertDialog.Builder(this)
                .setTitle("Расшифровать?")
                .setMessage(
                        "Файл: " + encFile.getName() + "\n" +
                        "Папка: " + encFile.getParent() + "\n\n" +
                        "Будет восстановлен оригинал:\n" +
                        encFile.getName().replace(".enc", "")
                )
                .setPositiveButton("Расшифровать", (d, w) -> {
                    new Thread(() -> {
                        try {
                            File orig = CryptoManager.decryptInPlace(encFile, currentPassword);
                            sendBroadcast(new Intent(
                                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                    Uri.fromFile(orig)));
                            runOnUiThread(() ->
                                toast("✅ Восстановлено: " + orig.getName()));
                        } catch (Exception e) {
                            runOnUiThread(() ->
                                toast("❌ Неверный пароль или файл повреждён"));
                        }
                    }).start();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ─── Камера ──────────────────────────────────────────────────────────────

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQ_PERMS);
            return;
        }
        try {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            // Сохраняем фото прямо в DCIM/Camera
            File dcim = new File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_DCIM), "Camera");
            dcim.mkdirs();
            pendingPhotoFile = new File(dcim, "IMG_" + ts + ".jpg");

            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider", pendingPhotoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            startActivityForResult(intent, REQ_CAMERA);
        } catch (Exception e) {
            toast("Ошибка камеры: " + e.getMessage());
        }
    }

    // ─── URI → File ──────────────────────────────────────────────────────────

    /**
     * Получаем реальный путь файла из URI.
     * Если не получается — копируем во временную папку.
     */
    private File uriToFile(Uri uri) throws Exception {
        // Пробуем получить реальный путь через MediaStore
        String[] proj = {MediaStore.Images.Media.DATA};
        try (Cursor c = getContentResolver().query(uri, proj, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int col = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                String path = c.getString(col);
                if (path != null) return new File(path);
            }
        } catch (Exception ignored) {}

        // Запасной вариант — копируем в кэш
        String name = getFileName(uri);
        File   temp = new File(getCacheDir(), name);
        try (InputStream in  = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(temp)) {
            byte[] buf = new byte[8192];
            int    r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        }
        return temp;
    }

    private String getFileName(Uri uri) {
        try (Cursor c = getContentResolver().query(
                uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "file_" + System.currentTimeMillis();
    }

    // ─── UI ──────────────────────────────────────────────────────────────────

    private void activateAdmin() {
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Защита от удаления и блокировка экрана");
        startActivityForResult(i, REQ_ADMIN);
    }

    private void updateUI() {
        boolean isAdmin = dpm.isAdminActive(adminComponent);
        statusText.setText(
                "Device Admin:      " + (isAdmin ? "✅ активен" : "❌ не активен") + "\n" +
                "Пароль шифрования: " + (currentPassword != null ? "✅ установлен" : "❌ не установлен")
        );
    }

    private String formatSize(long b) {
        if (b < 1024) return b + " Б";
        if (b < 1024 * 1024) return (b / 1024) + " КБ";
        return String.format(Locale.US, "%.1f МБ", b / (1024.0 * 1024));
    }

    private void requestPerms() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, REQ_PERMS);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);

        if (req == REQ_ADMIN) {
            updateUI();

        } else if (req == REQ_CAMERA && res == RESULT_OK) {
            if (pendingPhotoFile != null && pendingPhotoFile.exists()) {
                confirmAndEncrypt(pendingPhotoFile);
            }

        } else if (req == REQ_PICK_FILE && res == RESULT_OK && data != null) {
            // Шифрование
            new Thread(() -> {
                try {
                    File file = uriToFile(data.getData());
                    runOnUiThread(() -> confirmAndEncrypt(file));
                } catch (Exception e) {
                    runOnUiThread(() -> toast("Ошибка: " + e.getMessage()));
                }
            }).start();

        } else if (req == REQ_PICK_FILE + 100 && res == RESULT_OK && data != null) {
            // Расшифровка
            new Thread(() -> {
                try {
                    File file = uriToFile(data.getData());
                    if (!file.getName().endsWith(".enc")) {
                        runOnUiThread(() -> toast("Выберите .enc файл"));
                        return;
                    }
                    runOnUiThread(() -> decryptFile(file));
                } catch (Exception e) {
                    runOnUiThread(() -> toast("Ошибка: " + e.getMessage()));
                }
            }).start();
        }
    }
}
