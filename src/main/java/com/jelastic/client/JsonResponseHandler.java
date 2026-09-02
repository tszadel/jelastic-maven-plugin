package com.jelastic.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Reads a response body and maps it to a model object.
 *
 * <p>Contrary to {@code BasicResponseHandler} the body is read whatever the status code is: Jelastic based platforms
 * do answer with a meaningful JSON payload on 4xx/5xx, and throwing that payload away is precisely what used to turn a
 * readable API error into an obscure build failure. The body is decoded with the charset advertised by the server and
 * falls back to UTF-8 (and never to the platform default charset) so that accented characters survive.</p>
 */
class JsonResponseHandler<T> implements ResponseHandler<T> {

    private final ObjectMapper mapper;
    private final Class<T> type;
    private final Log log;

    JsonResponseHandler(ObjectMapper mapper, Class<T> type, Log log) {
        this.mapper = mapper;
        this.type = type;
        this.log = log;
    }

    public T handleResponse(HttpResponse response) throws IOException {
        int statusCode = response.getStatusLine().getStatusCode();
        String body = readBody(response.getEntity());

        if (log != null && log.isDebugEnabled()) {
            log.debug("HTTP " + statusCode + " <- " + body);
        }

        if (type == null) {
            if (statusCode >= 300) {
                throw new JelasticApiException("Request failed", statusCode, body);
            }
            return null;
        }

        if (body == null || body.trim().isEmpty()) {
            throw new JelasticApiException("Empty answer from the platform", statusCode, null);
        }

        try {
            return mapper.readValue(body, type);
        } catch (JsonProcessingException e) {
            if (statusCode >= 300) {
                throw new JelasticApiException("Request rejected by the platform", statusCode, body);
            }
            throw new JelasticApiException("Unexpected non JSON answer from the platform", statusCode, body);
        }
    }

    private String readBody(HttpEntity entity) throws IOException {
        if (entity == null) {
            return null;
        }

        Charset charset = null;
        ContentType contentType = ContentType.get(entity);
        if (contentType != null) {
            charset = contentType.getCharset();
        }

        return EntityUtils.toString(entity, charset != null ? charset : StandardCharsets.UTF_8);
    }
}
