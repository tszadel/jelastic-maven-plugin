package com.jelastic;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What the uploader receives.
 *
 * <p>These assertions look pedantic and are not: the platform reads the boundary as everything that follows
 * {@code boundary=}, so a second parameter in the {@code Content-Type} makes it search the body for a separator that
 * was never written. The deployment then stops on the first call, with an error naming neither the plugin nor the
 * request — see {@link JelasticMojo#uploadBody(File, String)}.</p>
 */
public class UploadBodyTest {

    @Test
    public void theContentTypeCarriesTheBoundaryAndNothingElse() throws Exception {
        Header contentType = JelasticMojo.uploadBody(artifact("ts-pilot-0.0.1.jar"), "session-1234").getContentType();

        String value = contentType.getValue();
        assertTrue(value, value.startsWith("multipart/form-data; boundary="));
        assertFalse("the charset would be part of the boundary the platform reads: " + value,
                value.contains("charset"));
        assertEquals(1, value.split(";").length - 1);
    }

    /**
     * The property the platform actually depends on: the separator it deduces from the header exists in the body.
     * Reading the boundary as {@code <frontière>; charset=UTF-8} is precisely what breaks it.
     */
    @Test
    public void theBoundaryReadFromTheHeaderIsTheOneWrittenInTheBody() throws Exception {
        HttpEntity body = JelasticMojo.uploadBody(artifact("ts-pilot-0.0.1.jar"), "session-1234");

        String boundary = body.getContentType().getValue().substring("multipart/form-data; boundary=".length());

        assertTrue("separator missing from the body: " + boundary, written(body).contains("--" + boundary + "\r\n"));
    }

    @Test
    public void theBodyStillCarriesTheThreeFieldsTheUploaderExpects() throws Exception {
        String body = written(JelasticMojo.uploadBody(artifact("ts-pilot-0.0.1.jar"), "session-1234"));

        assertTrue(body, body.contains("name=\"fid\""));
        assertTrue(body, body.contains("name=\"session\""));
        assertTrue(body, body.contains("session-1234"));
        assertTrue(body, body.contains("name=\"file\"; filename=\"ts-pilot-0.0.1.jar\""));
    }

    /**
     * The charset is kept out of the header, not dropped: it still encodes the part headers, which is what an
     * accented artifact name needs.
     */
    @Test
    public void anAccentedArtifactNameIsStillWrittenInUtf8() throws Exception {
        String body = written(JelasticMojo.uploadBody(artifact("café-1.0.jar"), "session-1234"));

        assertTrue(body, body.contains("filename=\"café-1.0.jar\""));
    }

    private static File artifact(String name) throws Exception {
        File file = new File(System.getProperty("java.io.tmpdir"), name);
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(new byte[]{'P', 'K', 3, 4, 0, 0, 0, 0});
        }
        file.deleteOnExit();

        return file;
    }

    private static String written(HttpEntity entity) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        entity.writeTo(out);

        return out.toString(StandardCharsets.UTF_8.name());
    }
}
