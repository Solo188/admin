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
     * Зашифровать файл — .enc создаётся В ТОЙ ЖЕ папке что и оригинал.
     * Оригинал безопасно удаляется.
     *
     * Пример:
     * /DCIM/Camera/photo.jpg  →  /DCIM/Camera/photo.jpg.enc
     * оригинал удалён
     */
    public static File encryptInPlace(File inputFile, String password) throws Exception {
        // .enc файл рядом с оригиналом
        File outputFile = new File(inputFile.getParent(),
                inputFile.getName() + ENC_EXTENSION);

        // Соль и IV
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_SIZE];
        byte[] iv   = new byte[IV_SIZE];
        random.nextBytes(salt);
        random.nextBytes(iv);

        SecretKey key    = deriveKey(password, salt);
        Cipher    cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_SIZE, iv));

        try (FileInputStream  fis = new FileInputStream(inputFile);
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            // Соль + IV в начало файла
            fos.write(salt);
            fos.write(iv);
            // Шифруем блоками
            byte[] buffer = new byte[8192];
            int    read;
            while ((read = fis.read(buffer)) != -1) {
                byte[] enc = cipher.update(buffer, 0, read);
                if (enc != null) fos.write(enc);
            }
            byte[] fin = cipher.doFinal();
            if (fin != null) fos.write(fin);
        }

        // Безопасно удаляем оригинал
        secureDelete(inputFile);

        return outputFile;
    }

    /**
     * Расшифровать .enc файл — оригинал восстанавливается рядом.
     *
     * Пример:
     * /DCIM/Camera/photo.jpg.enc  →  /DCIM/Camera/photo.jpg
     * .enc файл удаляется
     */
    public static File decryptInPlace(File encFile, String password) throws Exception {
        // Убираем .enc из имени
        String origName = encFile.getName().replace(ENC_EXTENSION, "");
        File   outputFile = new File(encFile.getParent(), origName);

        try (FileInputStream  fis = new FileInputStream(encFile);
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            byte[] salt = new byte[SALT_SIZE];
            byte[] iv   = new byte[IV_SIZE];
            if (fis.read(salt) != SALT_SIZE) throw new Exception("Повреждённый файл");
            if (fis.read(iv)   != IV_SIZE)   throw new Exception("Повреждённый файл");

            SecretKey key    = deriveKey(password, salt);
            Cipher    cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_SIZE, iv));

            byte[] buffer = new byte[8192];
            int    read;
            while ((read = fis.read(buffer)) != -1) {
                byte[] dec = cipher.update(buffer, 0, read);
                if (dec != null) fos.write(dec);
            }
            byte[] fin = cipher.doFinal();
            if (fin != null) fos.write(fin);
        }

        // Удаляем .enc файл
        encFile.delete();

        return outputFile;
    }

    private static SecretKey deriveKey(String password, byte[] salt) throws Exception {
        KeySpec          spec    = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_SIZE);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
        byte[]           bytes   = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(bytes, "AES");
    }

    /**
     * Безопасное удаление — перезаписываем нулями перед delete()
     */
    public static void secureDelete(File file) throws Exception {
        long   len  = file.length();
        byte[] zeros = new byte[8192];
        try (FileOutputStream fos = new FileOutputStream(file)) {
            long written = 0;
            while (written < len) {
                int toWrite = (int) Math.min(zeros.length, len - written);
                fos.write(zeros, 0, toWrite);
                written += toWrite;
            }
        }
        file.delete();
    }
}
