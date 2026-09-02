package com.jelastic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JelasticMojoTest {

    private static final String COMMENT_PROPERTY = "jelastic-comment";

    private final ObjectMapper mapper = new ObjectMapper();

    @After
    public void clearProperties() {
        System.clearProperty(COMMENT_PROPERTY);
    }

    /**
     * The description of a pom regularly holds quotes and accented characters; it used to be concatenated into the
     * JSON payload by hand, which produced an invalid payload and an unrelated error from the platform.
     */
    @Test
    public void archiveDataEscapesQuotesAndAccents() throws Exception {
        String comment = "Uploaded by Maven plugin. Gestion des \"clients\" de l'agence à Genève \\ Zürich";

        Map<String, Object> data = JelasticMojo.archiveData("mon-app-1.0.war",
                "https://api.example.com/xssu/rest/download/abc", 4096L, comment);

        JsonNode parsed = mapper.readTree(mapper.writeValueAsString(data));
        assertEquals(comment, parsed.get("comment").asText());
        assertEquals("mon-app-1.0.war", parsed.get("name").asText());
        assertEquals(4096L, parsed.get("size").asLong());
        assertEquals(0, parsed.get("link").asInt());
    }

    @Test
    public void archiveDataKeepsUtf8AfterASerializationRoundTrip() throws Exception {
        Map<String, Object> data = JelasticMojo.archiveData("café.war", "https://example.com/café", 1L,
                "Déploiement de l'application « café » 🚀");

        String json = mapper.writeValueAsString(data);
        JsonNode parsed = mapper.readTree(json);

        assertEquals("Déploiement de l'application « café » 🚀", parsed.get("comment").asText());
        assertEquals("café.war", parsed.get("name").asText());
    }

    @Test
    public void commentFallsBackOnTheSystemProperty() {
        System.setProperty(COMMENT_PROPERTY, "Livraison de l'appli");

        assertEquals("Uploaded by Maven plugin. Livraison de l'appli", new PublishMojo().getArtifactComment());
    }

    @Test
    public void commentIsCollapsedOnASingleLine() {
        System.setProperty(COMMENT_PROPERTY, "Première ligne\n   deuxième ligne\r\ntroisième ");

        assertEquals("Uploaded by Maven plugin. Première ligne deuxième ligne troisième",
                new PublishMojo().getArtifactComment());
    }

    @Test
    public void commentDefaultsToThePrefixWhenNothingIsConfigured() {
        assertEquals("Uploaded by Maven plugin", new PublishMojo().getArtifactComment());
    }

    @Test
    public void nonProxyHostsSupportsWildcardsAndSeparators() {
        assertTrue(JelasticMojo.isNonProxyHost("*.infomaniak.cloud", "api.pub1.infomaniak.cloud"));
        assertTrue(JelasticMojo.isNonProxyHost("localhost|*.jelastic.com", "api.jelastic.com"));
        assertTrue(JelasticMojo.isNonProxyHost("api.jelastic.com", "api.jelastic.com"));
        assertFalse(JelasticMojo.isNonProxyHost("*.jelastic.com", "api.pub1.infomaniak.cloud"));
        assertFalse(JelasticMojo.isNonProxyHost(null, "api.jelastic.com"));
        assertFalse(JelasticMojo.isNonProxyHost("  ", "api.jelastic.com"));
    }
}
