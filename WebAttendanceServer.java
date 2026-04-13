import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.BindException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class WebAttendanceServer {
    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_PORT_TRIES = 25;

    public static void main(String[] args) throws IOException {
        int requestedPort = DEFAULT_PORT;
        if (args != null && args.length >= 1 && args[0] != null && !args[0].trim().isEmpty()) {
            try {
                requestedPort = Integer.parseInt(args[0].trim());
            } catch (NumberFormatException ignored) {
                requestedPort = DEFAULT_PORT;
            }
        }

        HttpServer server = null;
        int boundPort = -1;
        for (int i = 0; i < MAX_PORT_TRIES; i++) {
            int port = requestedPort + i;
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
                boundPort = port;
                break;
            } catch (BindException ignored) {
                // try next port
            }
        }

        if (server == null || boundPort < 0) {
            throw new IOException("Could not bind to ports " + requestedPort + "..." + (requestedPort + MAX_PORT_TRIES - 1)
                    + ". Close the other server or choose a different port.");
        }

        server.createContext("/", ex -> {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendText(ex, 405, "Method Not Allowed", "text/plain; charset=utf-8");
                return;
            }
            serveResource(ex, "/web/index.html", "text/html; charset=utf-8");
        });

        server.createContext("/app.js", ex -> serveResource(ex, "/web/app.js", "text/javascript; charset=utf-8"));
        server.createContext("/styles.css", ex -> serveResource(ex, "/web/styles.css", "text/css; charset=utf-8"));
        server.createContext("/manifest.webmanifest", ex -> serveResource(ex, "/web/manifest.webmanifest", "application/manifest+json; charset=utf-8"));
        server.createContext("/service-worker.js", ex -> serveResource(ex, "/web/service-worker.js", "text/javascript; charset=utf-8"));
        server.createContext("/icon.svg", ex -> serveResource(ex, "/web/icon.svg", "image/svg+xml"));
        server.createContext("/icon-maskable.svg", ex -> serveResource(ex, "/web/icon-maskable.svg", "image/svg+xml"));

        server.createContext("/api/calc", ex -> {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendText(ex, 405, "Method Not Allowed", "text/plain; charset=utf-8");
                return;
            }

            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            String mode = q.getOrDefault("mode", "now");

            try {
                int totalHeld = parseInt(q.get("totalHeld"));
                int attended = parseInt(q.get("attended"));

                if (attended > totalHeld) throw new IllegalArgumentException("attended cannot exceed totalHeld");

                if ("plan".equalsIgnoreCase(mode)) {
                    int upcoming = parseInt(q.get("upcoming"));
                    int minAttendUpcoming = AttendanceCalculator.minUpcomingAttendanceNeeded(totalHeld, attended, upcoming);
                    int maxLeavesUpcoming = upcoming - minAttendUpcoming;

                    int finalTotal = totalHeld + upcoming;
                    int finalAttendedMin = attended + minAttendUpcoming;
                    double finalPctMin = finalTotal == 0 ? 0.0 : (finalAttendedMin * 100.0) / finalTotal;

                    sendJson(ex, 200, "{"
                            + "\"mode\":\"plan\","
                            + "\"totalHeld\":" + totalHeld + ","
                            + "\"attended\":" + attended + ","
                            + "\"upcoming\":" + upcoming + ","
                            + "\"minAttendUpcoming\":" + minAttendUpcoming + ","
                            + "\"maxLeavesUpcoming\":" + maxLeavesUpcoming + ","
                            + "\"finalPctMin\":" + jsonNumber(finalPctMin)
                            + "}");
                } else {
                    int leavesNow = AttendanceCalculator.leavesYouCanTakeNow(totalHeld, attended);
                    int needToReach = AttendanceCalculator.classesNeededToReachMinimum(totalHeld, attended);
                    double pct = totalHeld == 0 ? 0.0 : (attended * 100.0) / totalHeld;
                    boolean ok = AttendanceCalculator.meetsMinimum(attended, totalHeld);

                    sendJson(ex, 200, "{"
                            + "\"mode\":\"now\","
                            + "\"totalHeld\":" + totalHeld + ","
                            + "\"attended\":" + attended + ","
                            + "\"pct\":" + jsonNumber(pct) + ","
                            + "\"ok\":" + ok + ","
                            + "\"leavesNow\":" + leavesNow + ","
                            + "\"classesNeededToReach\":" + needToReach
                            + "}");
                }
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "Bad request" : e.getMessage();
                sendJson(ex, 400, "{\"error\":\"" + escapeJson(msg) + "\"}");
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("Web Attendance App running at http://127.0.0.1:" + boundPort + "/");
    }

    private static void serveResource(HttpExchange ex, String resourcePath, String contentType) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendText(ex, 405, "Method Not Allowed", "text/plain; charset=utf-8");
            return;
        }

        InputStream in = WebAttendanceServer.class.getResourceAsStream(resourcePath);
        if (in == null) {
            sendText(ex, 404, "Not Found", "text/plain; charset=utf-8");
            return;
        }

        byte[] bytes = in.readAllBytes();
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", contentType);
        h.set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static int parseInt(String s) {
        if (s == null) throw new IllegalArgumentException("Missing parameter");
        s = s.trim();
        if (s.isEmpty()) throw new IllegalArgumentException("Empty parameter");
        int v = Integer.parseInt(s);
        if (v < 0) throw new IllegalArgumentException("Values must be non-negative");
        return v;
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> out = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) return out;
        String[] pairs = rawQuery.split("&");
        for (String p : pairs) {
            int idx = p.indexOf('=');
            String k = idx >= 0 ? p.substring(0, idx) : p;
            String v = idx >= 0 ? p.substring(idx + 1) : "";
            k = urlDecode(k);
            v = urlDecode(v);
            out.put(k, v);
        }
        return out;
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        sendText(ex, code, json, "application/json; charset=utf-8");
    }

    private static void sendText(HttpExchange ex, int code, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", contentType);
        h.set("Cache-Control", "no-store");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String jsonNumber(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return "0";
        return String.format(java.util.Locale.ROOT, "%.2f", d);
    }
}

