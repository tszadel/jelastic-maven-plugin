package com.jelastic.client;

import java.io.IOException;

/**
 * Raised when the platform answered something that could not be understood as a Jelastic API response.
 *
 * <p>This typically happens when a request hits a reverse proxy or an API gateway (Infomaniak, Cloudflare, a corporate
 * proxy, ...) that answers with an HTML error page, a plain text message or an empty body instead of the expected
 * JSON payload. The raw answer is kept so that the build log tells what really happened instead of failing later on
 * with an unrelated {@link NullPointerException}.</p>
 */
public class JelasticApiException extends IOException {

    private static final long serialVersionUID = 1L;

    private static final int MAX_BODY_LENGTH = 512;

    private final int statusCode;
    private final String body;

    public JelasticApiException(String message, int statusCode, String body) {
        super(buildMessage(message, statusCode, body));
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }

    private static String buildMessage(String message, int statusCode, String body) {
        StringBuilder sb = new StringBuilder(message);
        if (statusCode > 0) {
            sb.append(" (HTTP ").append(statusCode).append(')');
        }

        String summary = summarize(body);
        if (summary != null) {
            sb.append(": ").append(summary);
        }

        return sb.toString();
    }

    /**
     * Turns a raw response body into a single readable line, so that an HTML error page does not flood the build log.
     */
    static String summarize(String body) {
        if (body == null) {
            return null;
        }

        String text = body.trim();
        if (text.isEmpty()) {
            return null;
        }

        if (text.regionMatches(true, 0, "<!doctype", 0, 9) || text.regionMatches(true, 0, "<html", 0, 5)) {
            text = text.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                    .replaceAll("(?s)<[^>]*>", " ")
                    .replace("&nbsp;", " ");
        }

        text = text.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return null;
        }

        if (text.length() > MAX_BODY_LENGTH) {
            text = text.substring(0, MAX_BODY_LENGTH) + "...";
        }

        return text;
    }
}
