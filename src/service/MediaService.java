package service;

import javax.crypto.SecretKey;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MediaService {

    public static class MediaResult {
        public byte[] imageBytes = new byte[0];
        public byte[] videoBytes = new byte[0];
        public byte[] audioBytes = new byte[0];
        public List<Path> processedFiles = new ArrayList<>();
    }

    public MediaResult processMediaFiles(Encryptor encryptor, SecretKey aesKey, List<Path> filesToProcess) {
        MediaResult result = new MediaResult();
        List<Path> images = new ArrayList<>();
        List<Path> videos = new ArrayList<>();
        List<Path> audios = new ArrayList<>();

        try {
            for (Path file : filesToProcess) {
                if (!Files.exists(file)) continue;
                String fileName = file.getFileName().toString().toLowerCase();
                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png") || fileName.endsWith(".webp")) {
                    images.add(file);
                } else if (fileName.endsWith(".mp4") || fileName.endsWith(".avi") || fileName.endsWith(".webm")) {
                    videos.add(file);
                } else if (fileName.endsWith(".mp3") || fileName.endsWith(".wav") || fileName.endsWith(".m4a") || fileName.endsWith(".ogg")) {
                    audios.add(file);
                }
                result.processedFiles.add(file);
            }

            if (!images.isEmpty()) {
                byte[] zippedImages = zipFiles(images);
                result.imageBytes = encryptor.encryptBytesWithAES(zippedImages, aesKey);
            }
            
            if (!videos.isEmpty()) {
                byte[] zippedVideos = zipFiles(videos);
                result.videoBytes = encryptor.encryptBytesWithAES(zippedVideos, aesKey);
            }
            
            if (!audios.isEmpty()) {
                byte[] zippedAudios = zipFiles(audios);
                result.audioBytes = encryptor.encryptBytesWithAES(zippedAudios, aesKey);
            }

        } catch (Exception e) {
            System.out.println("⚠️ Error processing media files: " + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    private byte[] zipFiles(List<Path> files) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Path file : files) {
                ZipEntry entry = new ZipEntry(file.getFileName().toString());
                zos.putNextEntry(entry);
                Files.copy(file, zos);
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    public Map<String, byte[]> unzipFiles(byte[] zipBytes) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                files.put(entry.getName(), baos.toByteArray());
            }
        }
        return files;
    }

    public void deleteProcessedFiles(List<Path> files) {
        System.out.println("🗑️ Cleaning up media files...");
        for (Path file : files) {
            try {
                Files.delete(file);
            } catch (IOException e) {
                System.out.println("Failed to delete: " + file.getFileName());
            }
        }
    }

    public void restoreMedia(int recordIndex, byte[] encryptedImage, byte[] encryptedVideo, Decryptor decryptor, SecretKey aesKey) throws Exception {
        if (encryptedImage != null && encryptedImage.length > 0) {
            byte[] zippedImages = decryptor.decryptBytes(encryptedImage, aesKey);
            Map<String, byte[]> images = unzipFiles(zippedImages);
            for (Map.Entry<String, byte[]> entry : images.entrySet()) {
                Path path = Paths.get("media", "restored_" + recordIndex + "_" + entry.getKey());
                Files.write(path, entry.getValue());
                System.out.println("📸 Image restored: " + path);
            }
        }

        if (encryptedVideo != null && encryptedVideo.length > 0) {
            byte[] zippedVideos = decryptor.decryptBytes(encryptedVideo, aesKey);
            Map<String, byte[]> videos = unzipFiles(zippedVideos);
            for (Map.Entry<String, byte[]> entry : videos.entrySet()) {
                Path path = Paths.get("media", "restored_" + recordIndex + "_" + entry.getKey());
                Files.write(path, entry.getValue());
                System.out.println("🎥 Video restored: " + path);
            }
        }
    }
    
    // Helper to get raw bytes map for web display
    public Map<String, byte[]> decryptMediaToMap(byte[] encryptedData, Decryptor decryptor, SecretKey aesKey) throws Exception {
        if (encryptedData != null && encryptedData.length > 0) {
            byte[] zipped = decryptor.decryptBytes(encryptedData, aesKey);
            return unzipFiles(zipped);
        }
        return new HashMap<>();
    }
}
