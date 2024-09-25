package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class CrowdMonitoringController {
    public static String IMAGE_FOLDER = "/home/vassarlabs/images/";

    @Autowired
    private SocketHandler socketHandler;

    @PostMapping("/api/process/file")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file,
                                             @RequestParam("model") String modelName,
                                             @RequestHeader("X-Session-Id") String sessionId) {
        Path filePath = Paths.get(IMAGE_FOLDER + file.getOriginalFilename());

        try {
            Files.write(filePath, file.getBytes());
            socketHandler.executeScriptAndBroadcast(modelName, String.valueOf(filePath), sessionId);
            return ResponseEntity.ok("Image uploaded successfully"); // No need to broadcast completion message here

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload image or run script.");
        }
    }

    @GetMapping("/api/download/{fileName}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String fileName) {

        try {
            // Read the file content
            byte[] content = socketHandler.downloadFile(fileName);

            // Set headers for the response
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName);

            return new ResponseEntity<>(content, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
