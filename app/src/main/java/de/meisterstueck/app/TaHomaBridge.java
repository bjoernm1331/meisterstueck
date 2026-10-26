package de.meisterstueck.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Brücke zwischen der Oberfläche (WebView) und der lokalen TaHoma-Schnittstelle.
 *
 * Die TaHoma nutzt im Developer Mode ein selbst signiertes Zertifikat. Die App merkt sich beim
 * ersten Verbinden den Fingerabdruck dieses Zertifikats und akzeptiert danach nur noch genau
 * dieses. Der Token verlässt die App nie in Richtung Oberfläche.
 */
public class TaHomaBridge {

    private static final String PREFS = "meisterstueck";
    private static final String BASE_PATH = "/enduser-mobile-web/1/enduserAPI/";

    private final WebView webView;
    private final SharedPreferences prefs;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private SSLSocketFactory socketFactory;

    public TaHomaBridge(Context context, WebView webView) {
        this.webView = webView;
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, new TrustManager[] { new PinningTrustManager() }, new SecureRandom());
            this.socketFactory = ssl.getSocketFactory();
        } catch (Exception e) {
            this.socketFactory = null;
        }
    }

    @JavascriptInterface
    public String getConfig() {
        JSONObject o = new JSONObject();
        try {
            o.put("host", prefs.getString("host", ""));
            o.put("hasToken", !prefs.getString("token", "").isEmpty());
        } catch (Exception ignored) {
            // Leeres Objekt genügt als Antwort.
        }
        return o.toString();
    }

    @JavascriptInterface
    public void setConfig(String host, String token) {
        String h = host == null ? "" : host.trim();
        h = h.replaceFirst("^https?://", "");
        while (h.endsWith("/")) {
            h = h.substring(0, h.length() - 1);
        }
        if (!h.isEmpty() && h.indexOf(':') < 0) {
            h = h + ":8443";
        }
        prefs.edit()
                .putString("host", h)
                .putString("token", token == null ? "" : token.trim())
                .remove("pin")
                .apply();
    }

    @JavascriptInterface
    public void clearConfig() {
        prefs.edit().clear().apply();
    }

    @JavascriptInterface
    public void request(final String id, final String method, final String path, final String body) {
        pool.execute(() -> perform(id, method, path, body));
    }

    private void perform(String id, String method, String path, String body) {
        int status;
        String text;
        HttpsURLConnection connection = null;
        try {
            if (socketFactory == null) {
                throw new IllegalStateException("Verschlüsselte Verbindung nicht verfügbar");
            }
            if (path == null || !path.matches("[A-Za-z0-9/_\\-]+") || path.contains("..")) {
                throw new IllegalArgumentException("Ungültiger Pfad");
            }
            String host = prefs.getString("host", "");
            if (host.isEmpty()) {
                throw new IllegalStateException("Keine TaHoma eingerichtet");
            }

            URL url = new URL("https://" + host + BASE_PATH + path);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setSSLSocketFactory(socketFactory);
            // Der Name im Zertifikat passt nicht zur IP-Adresse; geprüft wird stattdessen der Fingerabdruck.
            connection.setHostnameVerifier((hostname, session) -> true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            if (!"apiVersion".equals(path)) {
                connection.setRequestProperty("Authorization", "Bearer " + prefs.getString("token", ""));
            }

            if ("POST".equals(method)) {
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                byte[] data = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(data.length);
                OutputStream out = connection.getOutputStream();
                out.write(data);
                out.close();
            } else {
                connection.setRequestMethod("GET");
            }

            status = connection.getResponseCode();
            InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            text = readAll(in);
        } catch (Exception e) {
            status = 0;
            text = errorJson(e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        deliver(id, status, text);
    }

    private void deliver(String id, int status, String text) {
        final String js = "window.__nativeResult(" + JSONObject.quote(id) + "," + status + ","
                + JSONObject.quote(text) + ");";
        ui.post(() -> webView.evaluateJavascript(js, null));
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        in.close();
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String errorJson(Exception e) {
        String message = e.getMessage();
        if (e instanceof UnknownHostException) {
            message = "Adresse nicht gefunden. Bitte die IP-Adresse der TaHoma eintragen";
        } else if (e instanceof SocketTimeoutException) {
            message = "Zeitüberschreitung";
        } else if (e instanceof ConnectException) {
            message = "Verbindung abgelehnt";
        } else if (message == null || message.isEmpty()) {
            message = e.getClass().getSimpleName();
        }
        try {
            return new JSONObject().put("error", message).toString();
        } catch (Exception ex) {
            return "{}";
        }
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** Merkt sich das Zertifikat beim ersten Kontakt und lässt danach nur dieses zu. */
    private class PinningTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // Nicht benötigt.
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (chain == null || chain.length == 0) {
                throw new CertificateException("Kein Zertifikat erhalten");
            }
            String fingerprint;
            try {
                fingerprint = sha256(chain[0].getEncoded());
            } catch (Exception e) {
                throw new CertificateException("Zertifikat nicht lesbar");
            }
            String pinned = prefs.getString("pin", "");
            if (pinned.isEmpty()) {
                prefs.edit().putString("pin", fingerprint).apply();
            } else if (!pinned.equals(fingerprint)) {
                throw new CertificateException(
                        "Das Zertifikat der TaHoma hat sich geändert. Bitte abmelden und neu verbinden.");
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
