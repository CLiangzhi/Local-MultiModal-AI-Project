package com.example.ai.service;

import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.UUID;

@Service
public class ImageGenService {

    private static final Path IMAGE_DIR = Path.of("local_data/generated_images");
    private static final String BASE_URL = "https://image.pollinations.ai";

    static {
        // Clash HTTP proxy
        System.setProperty("https.proxyHost", "127.0.0.1");
        System.setProperty("https.proxyPort", "7890");
        // Don't proxy localhost connections
        System.setProperty("http.nonProxyHosts", "localhost|127.0.0.1|127.*|[::1]");

        // Disable SSL cert verification for proxy CONNECT tunnel compatibility
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLSv1.2");
            sc.init(null, trustAll, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            System.err.println("[ImageGen] SSL初始化警告: " + e.getMessage());
        }
    }

    public ImageGenService() {
        try {
            Files.createDirectories(IMAGE_DIR);
        } catch (IOException e) {
            throw new RuntimeException("无法创建图片存储目录", e);
        }
    }

    public String generateImage(String prompt, int width, int height, String model, Integer seed) throws IOException {
        StringBuilder url = new StringBuilder(BASE_URL);
        url.append("/prompt/");
        url.append(URLEncoder.encode(prompt, StandardCharsets.UTF_8));
        url.append("?width=").append(width);
        url.append("&height=").append(height);
        url.append("&nologo=true");
        if (model != null && !model.isEmpty()) {
            url.append("&model=").append(model);
        }
        if (seed != null) {
            url.append("&seed=").append(seed);
        }

        URL targetUrl = new URL(url.toString());
        HttpURLConnection conn = (HttpURLConnection) targetUrl.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "image/png, image/jpeg, */*");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

        int status = conn.getResponseCode();
        if (status != 200) {
            conn.disconnect();
            throw new IOException("Pollinations API returned HTTP " + status);
        }

        byte[] imageBytes;
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            imageBytes = out.toByteArray();
        } finally {
            conn.disconnect();
        }

        String filename = UUID.randomUUID().toString() + ".png";
        Files.write(IMAGE_DIR.resolve(filename), imageBytes);
        return filename;
    }

    public Path getImagePath(String filename) {
        return IMAGE_DIR.resolve(filename);
    }
}
