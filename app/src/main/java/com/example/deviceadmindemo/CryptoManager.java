package com.example.deviceadmindemo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM шифрование файлов.
 *
 * Как работает:
 * 1. Из пароля генерируется ключ через PBKDF2 (100_000 итераций)
 * 2. Генерируется случайная соль (16 байт) и IV (12 байт)
 * 3. Файл шифруется AES-256-GCM
 * 4. Соль + IV записываются в начало .enc файла
 *
 * При расшифровке:
 * 1. Читаем соль и IV из начала файла
 * 2. Восстанавливаем ключ из пароля + соли
 * 3. Расшифровываем
 *
 * Без правильного пароля расшифровка невозможна.
 */
public class CryptoManager {

    private static final String ALGORITHM     = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int    KEY_SIZE      = 256;
    private static final int    ITERATIONS    = 100_000;
    private static final int    SALT_SIZE     = 16;
    private static final int    IV_SIZE       = 12;
    private static final int    GCM_TAG_SIZE  = 128;
    public  static final String ENC_EXTENSION = ".enc";

    /**
     * Зашифровать файл.
     * Создаёт файл с расширением .enc рядом с оригиналом.
     * Оригинал НЕ удаляет — удаляй сам после проверки.
     */
    public static void encrypt(File inputFile, File outputFile, String password)
            throws Exception {

        // Генерируем случайную соль и IV
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_SIZE];
        byte[] iv   = new byte[IV_SIZE];
        random.nextBytes(salt);
        random.nextBytes(iv);

        // Генерируем ключ из пароля
        SecretKey key = deriveKey(password, salt);

        // Шифруем
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_SIZE, iv));

        try (FileInputStream  fis = new FileInputStream(inputFile);
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            // Записываем соль и IV в начало файла
            fos.write(salt);
            fos.write(iv);

            // Читаем и шифруем блоками по 8KB
            byte[] buffer = new byte[8192];
            int    read;
            while ((read = fis.read(buffer)) != -1) {
                byte[] encrypted = cipher.update(buffer, 0, read);
                if (encrypted != null) fos.write(encrypted);
            }
            // Финальный блок
            byte[] finalBlock = cipher.doFinal();
            if (finalBlock != null) fos.write(finalBlock);
        }
    }

    /**
     * Расшифровать файл.
     * Читает соль и IV из начала .enc файла.
     */
    public static void decrypt(File inputFile, File outputFile, String password)
            throws Exception {

        try (FileInputStream  fis = new FileInputStream(inputFile);
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            // Читаем соль и IV из начала файла
            byte[] salt = new byte[SALT_SIZE];
            byte[] iv   = new byte[IV_SIZE];
            if (fis.read(salt) != SALT_SIZE) throw new Exception("Повреждённый файл");
            if (fis.read(iv)   != IV_SIZE)   throw new Exception("Повреждённый файл");

            // Восстанавливаем ключ из пароля + соли
            SecretKey key = deriveKey(password, salt);

            // Расшифровываем
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_SIZE, iv));

            byte[] buffer = new byte[8192];
            int    read;
            while ((read = fis.read(buffer)) != -1) {
                byte[] decrypted = cipher.update(buffer, 0, read);
                if (decrypted != null) fos.write(decrypted);
            }
            byte[] finalBlock = cipher.doFinal();
            if (finalBlock != null) fos.write(finalBlock);
        }
    }

    /**
     * Генерация ключа из пароля через PBKDF2.
     * 100_000 итераций делают перебор паролей очень медленным.
     */
    private static SecretKey deriveKey(String password, byte[] salt) throws Exception {
        KeySpec          spec    = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_SIZE);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
        byte[]           keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Удалить файл безопасно — перезаписать нулями перед удалением.
     */
    public static void secureDelete(File file) throws Exception {
        long   length = file.length();
        byte[] zeros  = new byte[8192];
        try (FileOutputStream fos = new FileOutputStream(file)) {
            long written = 0;
            while (written < length) {
                int toWrite = (int) Math.min(zeros.length, length - written);
                fos.write(zeros, 0, toWrite);
                written += toWrite;
            }
        }
        file.delete();
    }
}
