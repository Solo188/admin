package com.example.deviceadmindemo;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private DevicePolicyManager dpm;
    private ComponentName       adminComponent;
    private TextView            statusText;

    private File   pendingPhotoFile; // Временный файл фото с камеры
    private String currentPassword;  // Текущий пароль шифрования

    private static final int REQ_ADMIN       = 1;
    private static final int REQ_CAMERA      = 2;
    private static final int REQ_PICK_FILE   = 3;
    private static final int REQ_PERM_CAMERA = 100;
    private static final int REQ_PERM_STORAGE= 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dpm            = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, AdminReceiver.class);
        statusText     = findViewById(R.id.statusText);

        // Запрашиваем разрешения при старте
        requestPermissions();

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

        // Установить пароль шифрования
        findViewById(R.id.btnSetEncPassword).setOnClickListener(v -> showSetPasswordDialog());

        // Выбрать файл и зашифровать
        findViewById(R.id.btnPickFile).setOnClickListener(v -> {
            if (currentPassword == null) {
                toast("Сначала установите пароль шифрования");
                return;
            }
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(Intent.createChooser(intent, "Выберите файл"), REQ_PICK_FILE);
        });

        // Сфотографировать и зашифровать
        findViewById(R.id.btnCamera).setOnClickListener(v -> {
            if (currentPassword == null) {
                toast("Сначала установите пароль шифрования");
                return;
            }
            openCamera();
        });

        // Расшифровать .enc файл
        findViewById(R.id.btnDecrypt).setOnClickListener(v -> showDecryptDialog());
    }

    // ─── Шифрование ──────────────────────────────────────────────────────────

    private void showSetPasswordDialog() {
        EditText input = new EditText(this);
        input.setHint("Введите пароль шифрования");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Пароль шифрования")
                .setMessage("Этот пароль нужен для шифрования и расшифровки файлов.\nЗапомни его — восстановление невозможно.")
                .setView(input)
                .setPositiveButton("Установить", (d, w) -> {
                    String pass = input.getText().toString().trim();
                    if (pass.length() >= 4) {
                        currentPassword = pass;
                        toast("Пароль установлен");
                        updateUI();
                    } else {
                        toast("Минимум 4 символа");
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void encryptFile(File inputFile) {
        new AlertDialog.Builder(this)
                .setTitle("Зашифровать файл?")
                .setMessage("Файл: " + inputFile.getName() +
                        "\nРазмер: " + formatSize(inputFile.length()) +
                        "\n\nОригинал будет безопасно удалён после шифрования.")
                .setPositiveButton("Зашифровать", (d, w) -> {
                    new Thread(() -> {
                        try {
                            // Создаём .enc файл рядом с оригиналом
                            File encDir    = getExternalFilesDir("encrypted");
                            if (encDir != null) encDir.mkdirs();
                            File outputFile = new File(encDir,
                                    inputFile.getName() + CryptoManager.ENC_EXTENSION);

                            CryptoManager.encrypt(inputFile, outputFile, currentPassword);

                            // Безопасно удаляем оригинал
                            CryptoManager.secureDelete(inputFile);

                            runOnUiThread(() -> {
                                toast("✅ Зашифровано: " + outputFile.getName());
                                updateUI();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() ->
                                toast("❌ Ошибка шифрования: " + e.getMessage()));
                        }
                    }).start();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showDecryptDialog() {
        // Показываем список .enc файлов
        File encDir = getExternalFilesDir("encrypted");
        if (encDir == null || !encDir.exists()) {
            toast("Нет зашифрованных файлов");
            return;
        }

        File[] files = encDir.listFiles(f -> f.getName().endsWith(CryptoManager.ENC_EXTENSION));
        if (files == null || files.length == 0) {
            toast("Нет зашифрованных файлов");
            return;
        }

        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) names[i] = files[i].getName();

        new AlertDialog.Builder(this)
                .setTitle("Выберите файл для расшифровки")
                .setItems(names, (d, which) -> {
                    File selectedFile = files[which];
                    showEnterPasswordForDecrypt(selectedFile);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showEnterPasswordForDecrypt(File encFile) {
        EditText input = new EditText(this);
        input.setHint("Введите пароль");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle("Пароль для расшифровки")
                .setView(input)
                .setPositiveButton("Расшифровать", (d, w) -> {
                    String pass = input.getText().toString();
                    new Thread(() -> {
                        try {
                            // Убираем .enc из имени
                            String origName = encFile.getName()
                                    .replace(CryptoManager.ENC_EXTENSION, "");
                            File outputFile = new File(
                                    getExternalFilesDir("decrypted"), origName);
                            if (outputFile.getParentFile() != null)
                                outputFile.getParentFile().mkdirs();

                            CryptoManager.decrypt(encFile, outputFile, pass);

                            runOnUiThread(() ->
                                toast("✅ Расшифровано: " + outputFile.getAbsolutePath()));
                        } catch (Exception e) {
                            runOnUiThread(() ->
                                toast("❌ Неверный пароль или повреждённый файл"));
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
                    new String[]{Manifest.permission.CAMERA}, REQ_PERM_CAMERA);
            return;
        }
        try {
            // Создаём временный файл для фото
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date());
            pendingPhotoFile = new File(getCacheDir(), "PHOTO_" + timeStamp + ".jpg");

            Uri photoUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider", pendingPhotoFile);

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            startActivityForResult(intent, REQ_CAMERA);
        } catch (Exception e) {
            toast("Ошибка камеры: " + e.getMessage());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void activateAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Защита приложения от удаления и блокировка экрана");
        startActivityForResult(intent, REQ_ADMIN);
    }

    private void updateUI() {
        boolean isAdmin = dpm.isAdminActive(adminComponent);
        statusText.setText(
                "Device Admin:  " + (isAdmin ? "✅ активен" : "❌ не активен") + "\n" +
                "Пароль шифрования: " + (currentPassword != null ? "✅ установлен" : "❌ не установлен") + "\n\n" +
                "Зашифрованные файлы:\n" + listEncryptedFiles()
        );
    }

    private String listEncryptedFiles() {
        File encDir = getExternalFilesDir("encrypted");
        if (encDir == null || !encDir.exists()) return "  нет файлов";
        File[] files = encDir.listFiles(f -> f.getName().endsWith(".enc"));
        if (files == null || files.length == 0) return "  нет файлов";
        StringBuilder sb = new StringBuilder();
        for (File f : files) {
            sb.append("  🔒 ").append(f.getName())
              .append(" (").append(formatSize(f.length())).append(")\n");
        }
        return sb.toString().trim();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " Б";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " КБ";
        return String.format(Locale.US, "%.1f МБ", bytes / (1024.0 * 1024));
    }

    private void requestPermissions() {
        String[] perms = {
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        ActivityCompat.requestPermissions(this, perms, REQ_PERM_STORAGE);
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
            // Фото сделано — шифруем
            if (pendingPhotoFile != null && pendingPhotoFile.exists()) {
                encryptFile(pendingPhotoFile);
            }

        } else if (req == REQ_PICK_FILE && res == RESULT_OK && data != null) {
            // Файл выбран через файловый менеджер
            Uri uri = data.getData();
            if (uri != null) {
                try {
                    // Копируем файл в кэш чтобы получить к нему доступ
                    String name = getFileName(uri);
                    File tempFile = new File(getCacheDir(), name);
                    try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                         java.io.FileOutputStream out = new java.io.FileOutputStream(tempFile)) {
                        byte[] buf = new byte[8192];
                        int    read;
                        while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
                    }
                    encryptFile(tempFile);
                } catch (Exception e) {
                    toast("Ошибка: " + e.getMessage());
                }
            }
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(
                    uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(
                            android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) result = cursor.getString(idx);
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
            if (result == null) result = "file_" + System.currentTimeMillis();
        }
        return result;
    }
}
