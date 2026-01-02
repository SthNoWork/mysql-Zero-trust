package service;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class MediaService {

    public static class MediaResult {
        public byte[] imageBytes = new byte[0];
        public byte[] videoBytes = new byte[0];
        public List<Path> processedFiles = new ArrayList<>();
    }

    public MediaResult processMediaFiles(Encryptor encryptor, SecretKey aesKey) {
        MediaResult result = new MediaResult();
        
        try (Stream<Path> paths = Files.walk(Paths.get("media"))) {
            List<Path> mediaFiles = paths.filter(Files::isRegularFile).toList();

            for (Path file : mediaFiles) {
                String fileName = file.getFileName().toString().toLowerCase();
                
                if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png")) {
                    System.out.println("📸 Found Image: " + fileName);
                    byte[] rawImage = Files.readAllBytes(file);
                    result.imageBytes = encryptor.encryptBytesWithAES(rawImage, aesKey);
                    result.processedFiles.add(file);
                } 
                else if (fileName.endsWith(".mp4") || fileName.endsWith(".avi")) {
                    System.out.println("🎥 Found Video: " + fileName);
                    byte[] rawVideo = Files.readAllBytes(file);
                    result.videoBytes = encryptor.encryptBytesWithAES(rawVideo, aesKey);
                    result.processedFiles.add(file);
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ Error reading media folder: " + e.getMessage());
        }
        return result;
    }

    public void deleteProcessedFiles(List<Path> files) {
        System.out.println("🗑️ Cleaning up media files...");
        for (Path file : files) {
            try {
                Files.delete(file);
                System.out.println("Deleted: " + file.getFileName());
            } catch (IOException e) {
                System.out.println("Failed to delete: " + file.getFileName());
            }
        }
    }

    public void restoreMedia(int recordIndex, byte[] encryptedImage, byte[] encryptedVideo, Decryptor decryptor, SecretKey aesKey) throws Exception {
        if (encryptedImage != null && encryptedImage.length > 0) {
            byte[] imageBytes = decryptor.decryptBytes(encryptedImage, aesKey);
            Path imagePath = Paths.get("media", "restored_image_" + recordIndex + ".jpg");
            Files.write(imagePath, imageBytes);
            System.out.println("📸 Image restored to: " + imagePath.toString());
        }

        if (encryptedVideo != null && encryptedVideo.length > 0) {
            byte[] videoBytes = decryptor.decryptBytes(encryptedVideo, aesKey);
            Path videoPath = Paths.get("media", "restored_video_" + recordIndex + ".mp4");
            Files.write(videoPath, videoBytes);
            System.out.println("🎥 Video restored to: " + videoPath.toString());
        }
    }

    public byte[] decryptImageToBytes(byte[] encryptedImage, Decryptor decryptor, SecretKey aesKey) throws Exception {
        if (encryptedImage != null && encryptedImage.length > 0) {
            return decryptor.decryptBytes(encryptedImage, aesKey);
        }
        return null;
    }

    public byte[] decryptVideoToBytes(byte[] encryptedVideo, Decryptor decryptor, SecretKey aesKey) throws Exception {
        if (encryptedVideo != null && encryptedVideo.length > 0) {
            return decryptor.decryptBytes(encryptedVideo, aesKey);
        }
        return null;
    }
}
