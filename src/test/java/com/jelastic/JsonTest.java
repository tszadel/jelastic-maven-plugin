package com.jelastic;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jelastic.model.Authentication;
import com.jelastic.model.CreateObject;
import com.jelastic.model.Deploy;
import com.jelastic.model.LogOut;
import com.jelastic.model.UpLoader;
import org.junit.Test;

import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Parsing of the answers returned by the platform.
 */
public class JsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private <T> T read(String resource, Class<T> type) throws Exception {
        URL url = getClass().getClassLoader().getResource(resource);
        assertNotNull("missing test resource " + resource, url);

        return mapper.readValue(url, type);
    }

    @Test
    public void authOkTest() throws Exception {
        Authentication authentication = read("authentication_ok.json", Authentication.class);
        assertEquals("48bxaad71ccc7996325f3803311326b0247d", authentication.getSession());
        assertEquals(0, authentication.getResult());
        assertNull(authentication.getError());
    }

    @Test
    public void authErrorTest() throws Exception {
        Authentication authentication = read("authentication_error.json", Authentication.class);
        assertEquals("authentication failed", authentication.getError());
        assertEquals(701, authentication.getResult());
    }

    /**
     * Some hosters answer through a gateway that returns a structured error instead of a plain string.
     */
    @Test
    public void authErrorAsObjectTest() throws Exception {
        Authentication authentication = read("authentication_error_object.json", Authentication.class);
        assertEquals("401 - Le jeton d'authentification est invalide", authentication.getError());
        assertEquals(401, authentication.getResult());
    }

    @Test
    public void createObjectOkTest() throws Exception {
        CreateObject createObject = read("createobject_ok.json", CreateObject.class);
        assertEquals(0, createObject.getResult());
        assertEquals(247, createObject.getResponse().getId());
        assertEquals(247, createObject.getResponse().getObject().getId());
    }

    @Test
    public void createObjectErrorTest() throws Exception {
        CreateObject createObject = read("createobject_error.json", CreateObject.class);
        assertEquals("invalid parameter [session]", createObject.getError());
        assertNull(createObject.getResponse());
    }

    @Test
    public void createObjectErrorAsListTest() throws Exception {
        CreateObject createObject = read("createobject_error_list.json", CreateObject.class);
        assertEquals("quota exceeded; try again later", createObject.getError());
    }

    @Test
    public void upLoaderOkTest() throws Exception {
        UpLoader upLoader = read("uploader_ok.json", UpLoader.class);
        assertEquals("jelastic-maven-plugin-1.0-SNAPSHOT.jar", upLoader.getName());
        assertEquals(14498L, upLoader.getSize());
    }

    @Test
    public void upLoaderErrorTest() throws Exception {
        UpLoader upLoader = read("uploader_error.json", UpLoader.class);
        assertEquals("invalid param", upLoader.getError());
    }

    /**
     * A failed deployment carries a null response: reading it must not blow up.
     */
    @Test
    public void deployErrorTest() throws Exception {
        Deploy deploy = read("deploy_error.json", Deploy.class);
        assertEquals("application [8129583aae37a4b556d36dbd56abbc68,8129583aae37a4b556d36dbd56abbc68] not exist",
                deploy.getError());
        assertNull(deploy.getResponse());
        assertEquals(11, deploy.getResult());
    }

    @Test
    public void deployOkTest() throws Exception {
        Deploy deploy = read("deploy_ok.json", Deploy.class);
        assertEquals(0, deploy.getResponse().getResult());
        assertNotNull(deploy.getResponse().getOut());
    }

    @Test
    public void logOutIgnoresUnknownMembersTest() throws Exception {
        LogOut logOut = mapper.readValue("{\"result\":0,\"unknown\":\"whatever\"}", LogOut.class);
        assertEquals(0, logOut.getResult());
    }
}
