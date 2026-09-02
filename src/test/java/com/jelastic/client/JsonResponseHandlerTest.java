package com.jelastic.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jelastic.model.Authentication;
import com.jelastic.model.CreateObject;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHttpResponse;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JsonResponseHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private HttpResponse response(int status, String body, ContentType contentType) {
        BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, status, "");
        response.setEntity(new ByteArrayEntity(body.getBytes(StandardCharsets.UTF_8), contentType));

        return response;
    }

    private <T> T handle(HttpResponse response, Class<T> type) throws Exception {
        return new JsonResponseHandler<T>(mapper, type, new SystemStreamLog()).handleResponse(response);
    }

    /**
     * Without an explicit charset, HttpClient used to fall back on ISO-8859-1 and mangled every accented message.
     */
    @Test
    public void readsTheBodyAsUtf8WhenTheServerDoesNotAdvertiseACharset() throws Exception {
        HttpResponse response = response(200, "{\"result\":701,\"error\":\"Échec de l'authentification\"}",
                ContentType.create("application/json"));

        assertEquals("Échec de l'authentification", handle(response, Authentication.class).getError());
    }

    @Test
    public void honoursTheCharsetAdvertisedByTheServer() throws Exception {
        HttpResponse response = response(200, "{\"result\":0,\"name\":\"Genève\"}",
                ContentType.create("application/json", StandardCharsets.UTF_8));

        assertEquals("Genève", handle(response, Authentication.class).getName());
    }

    /**
     * A JSON error carried by a 4xx answer stays readable instead of being thrown away with the body.
     */
    @Test
    public void parsesTheErrorPayloadOfAnHttpError() throws Exception {
        HttpResponse response = response(401, "{\"result\":701,\"error\":\"authentication failed\"}",
                ContentType.APPLICATION_JSON);

        assertEquals("authentication failed", handle(response, Authentication.class).getError());
    }

    /**
     * A gateway answering a JSON body without a Jelastic result would deserialize into a model whose primitive result
     * defaults to 0, so the HTTP failure would be read as a success.
     */
    @Test
    public void rejectsAJsonBodyOfAnHttpErrorThatCarriesNoJelasticResult() {
        try {
            handle(response(401, "{\"error\":\"unauthorized\"}", ContentType.APPLICATION_JSON), Authentication.class);
            fail("expected a JelasticApiException");
        } catch (Exception e) {
            assertTrue(e instanceof JelasticApiException);
            assertEquals(401, ((JelasticApiException) e).getStatusCode());
            assertTrue(e.getMessage(), e.getMessage().contains("unauthorized"));
        }
    }

    @Test
    public void keepsTheErrorNestedInTheResponseMemberOfAnHttpError() throws Exception {
        HttpResponse response = response(500,
                "{\"result\":0,\"response\":{\"result\":8,\"error\":\"access not permitted\"}}",
                ContentType.APPLICATION_JSON);

        CreateObject createObject = handle(response, CreateObject.class);
        assertEquals("access not permitted", createObject.getResponse().getError());
        assertEquals(8, createObject.getResponse().getResult());
    }

    @Test
    public void reportsAnHtmlGatewayErrorInsteadOfAParsingFailure() {
        String html = "<html><head><title>502 Bad Gateway</title></head><body><h1>502 Bad Gateway</h1></body></html>";

        try {
            handle(response(502, html, ContentType.TEXT_HTML), Authentication.class);
            fail("expected a JelasticApiException");
        } catch (Exception e) {
            assertTrue(e instanceof JelasticApiException);
            assertEquals(502, ((JelasticApiException) e).getStatusCode());
            assertTrue(e.getMessage(), e.getMessage().contains("HTTP 502"));
            assertTrue(e.getMessage(), e.getMessage().contains("502 Bad Gateway"));
        }
    }

    @Test
    public void reportsAnEmptyAnswer() {
        try {
            handle(response(200, "", ContentType.APPLICATION_JSON), Authentication.class);
            fail("expected a JelasticApiException");
        } catch (Exception e) {
            assertTrue(e instanceof JelasticApiException);
            assertTrue(e.getMessage(), e.getMessage().contains("Empty answer"));
        }
    }

    @Test
    public void reportsANonJsonAnswerOnASuccessfulStatus() {
        try {
            handle(response(200, "Service temporarily unavailable", ContentType.TEXT_PLAIN), Authentication.class);
            fail("expected a JelasticApiException");
        } catch (Exception e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Service temporarily unavailable"));
        }
    }
}
