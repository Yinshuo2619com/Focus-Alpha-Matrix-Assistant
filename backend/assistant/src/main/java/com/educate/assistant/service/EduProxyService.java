package com.educate.assistant.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import javax.net.ssl.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class EduProxyService {

    private static final String SESSION_KEY_PREFIX = "edu:session:";
    private static final long SESSION_TTL_HOURS = 2;

    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EduProxyService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        // Trust all SSL certificates only for this RestTemplate (educational systems often use self-signed certs)
        // Does NOT affect global SSL settings
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
                // Apply SSL bypass only to this connection
                if (connection instanceof HttpsURLConnection httpsConn) {
                    try {
                        TrustManager[] trustAllCerts = new TrustManager[]{
                            new X509TrustManager() {
                                public X509Certificate[] getAcceptedIssuers() { return null; }
                                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                            }
                        };
                        SSLContext sslContext = SSLContext.getInstance("TLS");
                        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                        httpsConn.setSSLSocketFactory(sslContext.getSocketFactory());
                        httpsConn.setHostnameVerifier((hostname, session) -> true);
                    } catch (Exception ignored) {}
                }
            }
        };
        factory.setConnectTimeout(15000);
        factory.setReadTimeout(30000);
        this.restTemplate = new RestTemplate(factory);
    }

    // ==================== Session Management ====================

    // Session-critical cookies that should NOT be overwritten by non-login responses
    private static final Set<String> SESSION_CRITICAL_COOKIES = Set.of("SESSION", "__pstsid__");

    public Map<String, String> getSessionCookies(Long userId) {
        String json = redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + userId);
        if (json == null || json.isEmpty()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private void saveSessionCookies(Long userId, Map<String, String> cookies) {
        try {
            String json = objectMapper.writeValueAsString(cookies);
            redisTemplate.opsForValue().set(SESSION_KEY_PREFIX + userId, json, SESSION_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception ignored) {}
    }

    public void storeCookies(Long userId, Map<String, String> newCookies) {
        storeCookies(userId, newCookies, false);
    }

    /**
     * Store cookies from proxy responses. When isLoginResponse=false, session-critical cookies
     * (SESSION, __pstsid__) are NOT overwritten to prevent the edu system's cookie rotation
     * from breaking the session.
     */
    public void storeCookies(Long userId, Map<String, String> newCookies, boolean isLoginResponse) {
        Map<String, String> filtered = newCookies.entrySet().stream()
            .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
            .filter(e -> !"deleted".equalsIgnoreCase(e.getValue()))
            .filter(e -> !"null".equalsIgnoreCase(e.getValue()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (filtered.isEmpty()) return;

        Map<String, String> existing = new HashMap<>(getSessionCookies(userId));
        if (!isLoginResponse) {
            filtered.forEach((k, v) -> {
                if (!SESSION_CRITICAL_COOKIES.contains(k) || !existing.containsKey(k)) {
                    existing.put(k, v);
                }
            });
        } else {
            existing.putAll(filtered);
        }
        saveSessionCookies(userId, existing);
    }

    public void clearSession(Long userId) {
        redisTemplate.delete(SESSION_KEY_PREFIX + userId);
    }

    // ==================== Educational System Login ====================

    /**
     * Login to the educational system with SHA1(password + salt) encryption.
     * Flow: GET /login-salt → SHA1 encrypt → POST /login
     */
    public boolean loginToSchool(Long userId, String username, String password, String schoolId) {
        String baseUrl = getSchoolBaseUrl(schoolId);
        if (baseUrl == null) {
            throw new RuntimeException("未找到学校配置: " + schoolId);
        }

        String loginPath = getLoginPath(schoolId);
        String loginUrl = baseUrl + loginPath;
        // salt URL: replace "/login" at end with "/login-salt"
        String saltUrl = baseUrl + loginPath.replace("/login", "/login-salt");

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        headers.set("Accept", "application/json, text/plain, */*");

        try {
            // Step 1: GET /login-salt to obtain salt
            System.out.println("[EduLogin] Fetching salt from: " + saltUrl);
            HttpEntity<Void> saltEntity = new HttpEntity<>(headers);
            ResponseEntity<String> saltResponse = restTemplate.exchange(
                saltUrl, HttpMethod.GET, saltEntity, String.class);

            Map<String, String> saltCookies = extractCookies(saltResponse.getHeaders());
            storeCookies(userId, saltCookies, true);

            String salt = parseSalt(saltResponse.getBody());
            System.out.println("[EduLogin] Got salt: " + salt + ", cookies: " + saltCookies.keySet());

            // Step 2: SHA1(salt + '-' + password) — educational system JS does: CryptoJS.SHA1(salt + '-' + password)
            String encryptedPassword = sha1(salt + "-" + password);

            // Step 3: POST /login with encrypted credentials
            HttpHeaders postHeaders = new HttpHeaders();
            postHeaders.setContentType(MediaType.APPLICATION_JSON);
            postHeaders.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            postHeaders.set("Referer", loginUrl);
            postHeaders.set("Accept", "application/json, text/plain, */*");

            // Forward cookies from salt request
            Map<String, String> allCookies = getSessionCookies(userId);
            if (!allCookies.isEmpty()) {
                postHeaders.set("Cookie", allCookies.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("; ")));
            }

            Map<String, Object> jsonBody = new HashMap<>();
            jsonBody.put("username", username);
            jsonBody.put("password", encryptedPassword);
            jsonBody.put("captcha", "");
            jsonBody.put("terminal", "web");

            HttpEntity<Map<String, Object>> postEntity = new HttpEntity<>(jsonBody, postHeaders);
            System.out.println("[EduLogin] POST " + loginUrl + " with username: " + username);

            ResponseEntity<String> loginResponse = restTemplate.exchange(
                loginUrl, HttpMethod.POST, postEntity, String.class);

            // Store login response cookies
            Map<String, String> loginCookies = extractCookies(loginResponse.getHeaders());
            storeCookies(userId, loginCookies, true);

            System.out.println("[EduLogin] Login response: " + loginResponse.getStatusCode()
                + ", cookies: " + loginCookies.keySet()
                + ", body preview: " + preview(loginResponse.getBody(), 200));

            // Check success: JSON {"result":true} or 302 redirect or SESSION/rememberMe cookie
            String body = loginResponse.getBody();
            boolean success = (body != null && body.contains("\"result\":true"))
                || loginResponse.getStatusCode().is3xxRedirection()
                || loginCookies.containsKey("SESSION")
                || loginCookies.containsKey("rememberMe");

            if (!success) {
                if (body != null && (body.contains("密码错误") || body.contains("用户名不存在"))) {
                    throw new RuntimeException("用户名或密码错误");
                }
                throw new RuntimeException("登录失败: " + preview(body, 200));
            }

            System.out.println("[EduLogin] Login succeeded for user: " + username
                + ", total cookies: " + getSessionCookies(userId).size());
            return true;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            System.out.println("[EduLogin] Login failed: " + e.getMessage());
            throw new RuntimeException("登录教务系统失败: " + e.getMessage());
        }
    }

    /**
     * Parse salt from response.
     * Formats: plain string "xxx", JSON {"salt":"xxx"}, or quoted "xxx"
     */
    private String parseSalt(String body) {
        if (body == null || body.isEmpty()) {
            throw new RuntimeException("获取 salt 失败：响应为空");
        }
        String trimmed = body.trim();

        // 1. Plain string (e.g. UUID: 2f2d75e1-3741-47c4-8e97-cf42ccd7e22c)
        if (trimmed.matches("[a-zA-Z0-9\\-]+")) {
            return trimmed;
        }

        // 2. JSON: {"salt":"xxx"} or {"data":{"salt":"xxx"}}
        String[] patterns = {"\"salt\":\"", "\"salt\": \""};
        for (String pattern : patterns) {
            int idx = trimmed.indexOf(pattern);
            if (idx >= 0) {
                int start = idx + pattern.length();
                int end = trimmed.indexOf("\"", start);
                if (end > start) {
                    return trimmed.substring(start, end);
                }
            }
        }

        // 3. Quoted string: "xxx"
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }

        throw new RuntimeException("无法从响应中解析 salt，响应内容: " + preview(body, 300));
    }

    /**
     * SHA1 hash and return hex string
     */
    private String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA1 加密失败", e);
        }
    }

    private String preview(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "empty";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(bytes.length, 200); i++) {
            sb.append(String.format("%02x", bytes[i]));
        }
        if (bytes.length > 200) sb.append("...");
        return sb.toString();
    }

    private String getLoginPath(String schoolId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT login_path FROM school_config WHERE school_id = ? AND enabled = 1",
                String.class, schoolId);
        } catch (Exception e) {
            return "/login";
        }
    }

    // ==================== Schedule Page Fetching ====================

    /**
     * Fetch the schedule page HTML using stored session cookies.
     */
    public String fetchSchedulePage(Long userId, String schoolId) {
        String baseUrl = getSchoolBaseUrl(schoolId);
        if (baseUrl == null) {
            throw new RuntimeException("未找到学校配置: " + schoolId);
        }

        String schedulePath = getSchedulePath(schoolId);
        String scheduleUrl = baseUrl + schedulePath;

        Map<String, String> cookies = getSessionCookies(userId);
        if (cookies.isEmpty()) {
            throw new RuntimeException("未登录教务系统，请先登录");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Cookie", cookies.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; ")));

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                scheduleUrl, HttpMethod.GET, entity, String.class);

            // Store any new cookies
            Map<String, String> newCookies = extractCookies(response.getHeaders());
            if (!newCookies.isEmpty()) {
                storeCookies(userId, newCookies);
            }

            System.out.println("[EduProxy] Fetched schedule: " + scheduleUrl
                + ", status: " + response.getStatusCode()
                + ", body length: " + (response.getBody() != null ? response.getBody().length() : 0));

            return response.getBody() != null ? response.getBody() : "";

        } catch (Exception e) {
            System.out.println("[EduProxy] Failed to fetch schedule: " + e.getMessage());
            throw new RuntimeException("获取课表页面失败: " + e.getMessage());
        }
    }

    /**
     * Fetch schedule data from the educational system's JSON API.
     * The course-table page loads data via AJAX: get-data?bizTypeld=2&semesterld={semesterId}
     */
    public String fetchScheduleDataApi(Long userId, String schoolId, int semesterId) {
        String baseUrl = getSchoolBaseUrl(schoolId);
        if (baseUrl == null) {
            throw new RuntimeException("未找到学校配置: " + schoolId);
        }

        String schedulePath = getSchedulePath(schoolId);
        String dataUrl = baseUrl + schedulePath + "/get-data?bizTypeId=2&semesterId=" + semesterId;

        Map<String, String> cookies = getSessionCookies(userId);
        if (cookies.isEmpty()) {
            throw new RuntimeException("未登录教务系统，请先登录");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Accept", "application/json, text/plain, */*");
            headers.set("X-Requested-With", "XMLHttpRequest");
            headers.set("Referer", baseUrl + schedulePath);
            headers.set("Cookie", cookies.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; ")));

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                dataUrl, HttpMethod.GET, entity, String.class);

            // Store any new cookies
            Map<String, String> newCookies = extractCookies(response.getHeaders());
            if (!newCookies.isEmpty()) {
                storeCookies(userId, newCookies);
            }

            System.out.println("[EduProxy] Fetched schedule data API: " + dataUrl
                + ", status: " + response.getStatusCode()
                + ", body length: " + (response.getBody() != null ? response.getBody().length() : 0));

            return response.getBody() != null ? response.getBody() : "";

        } catch (Exception e) {
            System.out.println("[EduProxy] Failed to fetch schedule data: " + e.getMessage());
            throw new RuntimeException("获取课表数据失败: " + e.getMessage());
        }
    }

    // ==================== Helper Methods ====================

    private String getSchoolBaseUrl(String schoolId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT base_url FROM school_config WHERE school_id = ? AND enabled = 1",
                String.class, schoolId);
        } catch (Exception e) {
            return null;
        }
    }

    private String getSchedulePath(String schoolId) {
        try {
            String path = jdbcTemplate.queryForObject(
                "SELECT schedule_path FROM school_config WHERE school_id = ? AND enabled = 1",
                String.class, schoolId);
            // Ensure path starts with /student/ if it doesn't already
            if (path != null && !path.startsWith("/student/") && path.startsWith("/")) {
                path = "/student" + path;
            }
            return path;
        } catch (Exception e) {
            return "/student/for-std/course-table";
        }
    }

    private Map<String, String> extractCookies(HttpHeaders headers) {
        Map<String, String> cookies = new HashMap<>();
        List<String> setCookies = headers.getOrDefault(HttpHeaders.SET_COOKIE, List.of());
        for (String setCookie : setCookies) {
            String mainPart = setCookie.split(";")[0].trim();
            int eq = mainPart.indexOf('=');
            if (eq > 0) {
                String name = mainPart.substring(0, eq).trim();
                String value = mainPart.substring(eq + 1).trim();
                // Skip cookie deletion (empty value, e.g. Set-Cookie: SESSION=; Max-Age=0)
                if (!value.isEmpty()) {
                    cookies.put(name, value);
                }
            }
        }
        return cookies;
    }

    // ==================== Proxy Request ====================

    public ProxyResult proxyRequest(Long userId, String method, String targetUrl,
                                     HttpServletRequest request, String allowedBaseUrl) {
        return proxyRequest(userId, method, targetUrl, request, allowedBaseUrl, false);
    }

    public ProxyResult proxyRequest(Long userId, String method, String targetUrl,
                                     HttpServletRequest request, String allowedBaseUrl,
                                     boolean excludeRememberMe) {
        if (!targetUrl.startsWith(allowedBaseUrl)) {
            throw new SecurityException("Proxy target outside allowed domain");
        }

        HttpHeaders headers = new HttpHeaders();

        // Forward all request headers (except hop-by-hop and encoding we can't decompress)
        java.util.Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            String lower = name.toLowerCase();
            if (!"host".equals(lower) && !"connection".equals(lower)
                && !"cookie".equals(lower) && !"content-length".equals(lower)
                && !"accept-encoding".equals(lower)) {
                headers.set(name, request.getHeader(name));
            }
        }

        // Always set Referer to the target URL (educational system checks this)
        headers.set("Referer", targetUrl);

        // Always set Origin for all requests
        headers.set("Origin", allowedBaseUrl);

        // Set Host to upstream server
        try {
            java.net.URI uri = java.net.URI.create(targetUrl);
            headers.set("Host", uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : ""));
        } catch (Exception e) {
            // ignore
        }

        // Forward cookies
        Map<String, String> cookies = getSessionCookies(userId);
        if (!cookies.isEmpty()) {
            java.util.stream.Stream<Map.Entry<String, String>> stream = cookies.entrySet().stream();
            if (excludeRememberMe) {
                stream = stream.filter(e -> !"rememberMe".equals(e.getKey()));
            }
            String cookieHeader = stream
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));
            headers.set("Cookie", cookieHeader);
            System.out.println("[EduProxy] Sending cookies: " + cookies.keySet()
                + (excludeRememberMe ? " (excl. rememberMe)" : "") + " to " + targetUrl);
        } else {
            System.out.println("[EduProxy] NO cookies for user " + userId + " to " + targetUrl);
        }

        // Forward Content-Type for POST
        if ("POST".equalsIgnoreCase(method)) {
            String contentType = request.getContentType() != null
                ? request.getContentType() : "application/x-www-form-urlencoded";
            headers.setContentType(MediaType.parseMediaType(contentType));
        }

        // Read request body
        byte[] body = new byte[0];
        try {
            body = request.getInputStream().readAllBytes();
        } catch (IOException e) {
            // ignore
        }

        HttpEntity<byte[]> entity = new HttpEntity<>(body, headers);
        System.out.println("[EduProxy] " + method + " " + targetUrl
            + " (body=" + body.length + " bytes, contentType=" + headers.getContentType() + ")"
            + ", Referer=" + headers.getFirst("Referer")
            + ", Origin=" + headers.getFirst("Origin")
            + ", X-Req-With=" + headers.getFirst("X-Requested-With")
            + ", bodyHex=" + bytesToHex(body));

        ResponseEntity<byte[]> response;
        try {
            response = restTemplate.exchange(
                targetUrl, HttpMethod.valueOf(method), entity, byte[].class);
        } catch (Exception e) {
            System.out.println("[EduProxy] Request FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("[EduProxy] Caused by: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage());
            }
            throw new RuntimeException("代理请求失败: " + e.getMessage());
        }

        System.out.println("[EduProxy] Response: " + response.getStatusCode()
            + " (" + (response.getBody() != null ? response.getBody().length : 0) + " bytes)"
            + ", contentType: " + response.getHeaders().getContentType()
            + ", location: " + response.getHeaders().getFirst(HttpHeaders.LOCATION));

        // Extract Set-Cookie (reuses extractCookies which skips empty/deletion values)
        Map<String, String> responseCookies = extractCookies(response.getHeaders());
        List<String> setCookies = response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of());

        String contentType = response.getHeaders().getContentType() != null
            ? response.getHeaders().getContentType().toString() : "text/html";

        String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
        int statusCode = response.getStatusCode().value();

        return new ProxyResult(response.getBody(), contentType, responseCookies, statusCode, location, setCookies);
    }

    public record ProxyResult(byte[] body, String contentType, Map<String, String> cookies,
                               int statusCode, String location, List<String> rawSetCookies) {}
}
