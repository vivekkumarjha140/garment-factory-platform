package com.garmentfactory.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;

/** Minimal HTTP client for integration tests against the randomly chosen server port. */
public class ApiClient {

    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern REFRESH_COOKIE = Pattern.compile("gf_refresh=([^;]*)");

    public record Response(int status, String body, HttpHeaders headers) {

        public String accessToken() {
            Matcher matcher = ACCESS_TOKEN.matcher(body);
            if (!matcher.find()) {
                throw new AssertionError("No accessToken in response: " + body);
            }
            return matcher.group(1);
        }

        public String refreshToken() {
            for (String header : headers.allValues("set-cookie")) {
                Matcher matcher = REFRESH_COOKIE.matcher(header);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            throw new AssertionError("No gf_refresh cookie in " + headers.allValues("set-cookie"));
        }

        public String header(String name) {
            return headers.firstValue(name).orElse(null);
        }
    }

    /** A response whose body is binary (images). */
    public record BytesResponse(int status, byte[] body, HttpHeaders headers) {

        public String header(String name) {
            return headers.firstValue(name).orElse(null);
        }
    }

    private final Environment environment;
    private final HttpClient http = HttpClient.newHttpClient();

    public ApiClient(Environment environment) {
        this.environment = environment;
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + environment.getProperty("local.server.port") + path);
    }

    public Response send(String method, String path, String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
        headers.forEach(builder::header);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body(), response.headers());
    }

    public Response post(String path, String body) throws Exception {
        return send("POST", path, body, Map.of());
    }

    public Response get(String path, String bearer) throws Exception {
        return send("GET", path, null, bearer == null ? Map.of() : Map.of("Authorization", "Bearer " + bearer));
    }

    public Response delete(String path, String bearer) throws Exception {
        return send("DELETE", path, null, Map.of("Authorization", "Bearer " + bearer));
    }

    /** A request carrying both the access token and that login's refresh cookie. */
    public Response sendWithSession(String method, String path, String body, Response login) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + login.accessToken());
        headers.put("Cookie", "gf_refresh=" + login.refreshToken());
        return send(method, path, body, headers);
    }

    /** What the SPA sends to /refresh and /logout: the custom header and the cookie. */
    public Response postAsBrowser(String path, String refreshToken) throws Exception {
        return send("POST", path, "", Map.of("X-Requested-With", "XMLHttpRequest", "Cookie", "gf_refresh=" + refreshToken));
    }

    public BytesResponse getBytes(String path, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        return new BytesResponse(response.statusCode(), response.body(), response.headers());
    }

    /** A multipart/form-data upload with one file part named "file" and any number of text fields. */
    public Response postMultipart(String path, String bearer, String fileName, String fileContentType,
                                  byte[] file, Map<String, String> fields) throws Exception {
        String boundary = "----gfboundary" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n"
                    + field.getValue() + "\r\n");
        }
        write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
                + "\"\r\nContent-Type: " + fileContentType + "\r\n\r\n");
        body.write(file);
        write(body, "\r\n--" + boundary + "--\r\n");

        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + bearer)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body(), response.headers());
    }

    private static void write(ByteArrayOutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String registerJson(String email, String password) {
        return "{\"fullName\":\"Test User\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    public static String loginJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}