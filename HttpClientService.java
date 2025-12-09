
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class HttpClientService {

    private static final String HISTORY_FILE = "history.txt";
    public HttpClient client;

    public HttpClientService() {
        TrustManager trust509 = new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {

            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType)
                    throws CertificateException {

            }
        };
        TrustManager[] trustAllCerts = new TrustManager[] { trust509 };

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            client = HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

    }

    public String sendHttpRequest(String method, String url, String headers, String bodyType, String rawBody,
            List<FormField> fields) throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url));

        for (String line : headers.split("\n")) {
            if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                builder.header(parts[0].trim(), parts[1].trim());
            }
        }

        if (method.equals("GET") || method.equals("DELETE")) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            if (bodyType.equals("Raw")) {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(rawBody));
            } else {
                String boundary = "---JavaBoundary" + System.currentTimeMillis();
                builder.header("Content-Type", "multipart/form-data; boundary=" + boundary);
                byte[] multipartBody = buildMultipartBody(fields, boundary);
                builder.method(method, HttpRequest.BodyPublishers.ofByteArray(multipartBody));
            }
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return formatResponse(response);
    }

    private byte[] buildMultipartBody(List<FormField> fields, String boundary) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (FormField field : fields) {
            baos.write(("--" + boundary + "\r\n").getBytes());
            if (field.isFile) {
                Path filePath = Paths.get(field.value);
                String fileName = filePath.getFileName().toString();
                baos.write(("Content-Disposition: form-data; name=\"" + field.name + "\"; filename=\"" + fileName
                        + "\"\r\n").getBytes());
                baos.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes());
                Files.copy(filePath, baos);
                baos.write("\r\n".getBytes());
            } else {
                baos.write(("Content-Disposition: form-data; name=\"" + field.name + "\"\r\n\r\n").getBytes());
                baos.write(field.value.getBytes());
                baos.write("\r\n".getBytes());
            }
        }
        baos.write(("--" + boundary + "--\r\n").getBytes());
        return baos.toByteArray();
    }

    private String formatResponse(HttpResponse<String> response) {
        StringBuilder sb = new StringBuilder();
        sb.append("Status: ").append(response.statusCode()).append("\n");
        sb.append("Headers:\n");
        response.headers().map()
                .forEach((k, v) -> sb.append(" ").append(k).append(": ").append(String.join(", ", v)).append("\n"));
        var body = response.body();
        if (body != null && (body.trim().startsWith("{") || body.trim().startsWith("["))) {
            body = prettyPrintJson(body);
        }
        sb.append("\nBody:\n").append(body);
        sb.append("\n");
        return sb.toString();
    }

    public void saveFullHistory(String method, String url, String headers, String bodyType, String rawBody,
            List<FormField> fields) {
        try (FileWriter fw = new FileWriter(HISTORY_FILE, true)) {
            var serializedHeaders = headers.replace("\n", "\\n");
            var serializedBody = rawBody.replace("\n", "\\n");
            fw.write(method + "@@" + url + "@@" + (serializedHeaders.length() > 0 ? serializedHeaders : " ") + "@@"
                    + bodyType + "@@"
                    + (serializedBody.length() > 0 ? serializedBody : " ") + "@@");
            for (FormField f : fields) {
                fw.write(f.name + "," + f.value.replace("\n", "\\n") + "," + f.isFile + ";");
            }
            fw.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<String> loadHistory() {
        try {
            return Files.readAllLines(Paths.get(HISTORY_FILE));
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public String generateCurl(String method, String url, String headers, String bodyType, String rawBody,
            List<FormField> fields) {
        StringBuilder curl = new StringBuilder("curl -X ").append(method).append(" \"").append(url).append("\"");

        // Adicionar headers
        for (String line : headers.split("\n")) {
            if (line.contains(":")) {
                curl.append(" -H \"").append(line.trim()).append("\"");
            }
        }

        // Adicionar corpo
        if (!method.equals("GET") && !method.equals("DELETE")) {
            if (bodyType.equals("Raw") && rawBody != null && !rawBody.isEmpty()) {
                curl.append(" -d '").append(rawBody.replace("'", "\\'")).append("'");
            } else if (bodyType.equals("Form-Data")) {
                for (FormField f : fields) {
                    if (f.isFile) {
                        curl.append(" -F \"").append(f.name).append("=@").append(f.value).append("\"");
                    } else {
                        curl.append(" -F \"").append(f.name).append("=").append(f.value).append("\"");
                    }
                }
            }
        }

        return curl.toString();
    }

    public ParsedCurl parseCurl(String curlCommand) {
        ParsedCurl parsed = new ParsedCurl();
        parsed.method = "GET"; // default

        // Regex para capturar -X, -H, -d e URL
        Pattern pattern = Pattern
                .compile("(-X\\s+\\w+)|(-H\\s+\"?[^\"]+\"?)|(-d\\s+'[^']+'|--data\\s+'[^']+')|(https?://\\S+)");
        Matcher matcher = pattern.matcher(curlCommand);

        while (matcher.find()) {
            Object match = matcher.group().trim();

            switch (match) {
                case String it when it.startsWith("-X") -> {
                    parsed.method = it.replace("-X", "").trim();
                }
                case String it when it.startsWith("-H") -> {
                    String header = clean(it.replace("-H", "").trim());
                    String[] parts = header.split(":", 2);
                    if (parts.length == 2) {
                        parsed.headers.add(parts[0].trim() + ":" + parts[1].trim());
                    }
                }
                case String it when it.startsWith("-d") || it.startsWith("--data") -> {
                    parsed.body = clean(it.replaceFirst("-d|--data", "").trim());
                }
                case String it when it.startsWith("http") -> {
                    parsed.url = clean(it);
                }
                default -> {
                    // Ignora desconhecidos
                }
            }

        }
        return parsed;
    }

    private static String clean(String value) {
        return value.replaceAll("^['\"]|['\"]$", ""); // remove aspas simples ou duplas no início/fim
    }

    private static String prettyPrintJson(String json) {
        StringBuilder pretty = new StringBuilder();
        int indent = 0;
        boolean inQuotes = false;
        boolean escape = false;

        for (char c : json.toCharArray()) {
            switch (c) {
                case '"' -> {
                    pretty.append(c);
                    if (!escape) {
                        inQuotes = !inQuotes;
                    }
                    escape = false;
                }
                case '\\' -> {
                    pretty.append(c);
                    escape = true; // próximo caractere é escapado
                }
                case '{', '[' -> {
                    pretty.append(c);
                    if (!inQuotes) {
                        pretty.append("\n");
                        indent++;
                        pretty.append("  ".repeat(indent));
                    }
                    escape = false;
                }
                case '}', ']' -> {
                    if (!inQuotes) {
                        pretty.append("\n");
                        indent--;
                        pretty.append("  ".repeat(indent));
                    }
                    pretty.append(c);
                    escape = false;
                }
                case ',' -> {
                    pretty.append(c);
                    if (!inQuotes) {
                        pretty.append("\n");
                        pretty.append("  ".repeat(indent));
                    }
                    escape = false;
                }
                case ':' -> {
                    pretty.append(c);
                    if (!inQuotes) {
                        pretty.append(" ");
                    }
                    escape = false;
                }
                default -> {
                    pretty.append(c);
                    escape = false;
                }
            }
        }
        return pretty.toString();
    }

    public static class ParsedCurl {
        String method;
        String url;
        List<String> headers = new ArrayList<>();
        String body;
    }

    public static class FormField {
        String name, value;
        boolean isFile;

        public FormField(String name, String value, boolean isFile) {
            this.name = name;
            this.value = value;
            this.isFile = isFile;
        }
    }
}
