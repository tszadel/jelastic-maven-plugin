package com.jelastic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jelastic.client.BoundaryOnlyContentType;
import com.jelastic.client.JelasticClient;
import com.jelastic.client.ProgressHttpEntity;
import com.jelastic.model.Archive;
import com.jelastic.model.Archives;
import com.jelastic.model.Authentication;
import com.jelastic.model.CreateObject;
import com.jelastic.model.Deploy;
import com.jelastic.model.LogOut;
import com.jelastic.model.UpLoader;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Proxy;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Common behaviour of the Jelastic goals: reading the configuration, uploading the artifact and talking to the
 * platform API.
 *
 * <p>Works against any Jelastic based cloud: Jelastic itself, Infomaniak Public Cloud, Scaleway, ... The API host is
 * given by the {@code api_hoster} parameter or the {@code jelastic-hoster} system property.</p>
 */
public abstract class JelasticMojo extends AbstractMojo {

    private static final String WAR_TYPE = "war";
    private static final String EAR_TYPE = "ear";
    private static final String JAR_TYPE = "jar";

    /** Attached artifacts nobody deploys — they are outputs of the build all the same. */
    private static final List<String> COMPANION_CLASSIFIERS =
            Arrays.asList("sources", "javadoc", "tests", "test-sources", "test-javadoc");

    private static final String HTTP_PROTOCOL = "http";
    private static final String HTTPS_PROTOCOL = "https";

    private static final String SCHEMA = HTTPS_PROTOCOL;
    private static final int DEFAULT_PORT = -1;
    private static final String VERSION = "1.0";

    private static final String URL_AUTHENTICATION = "/" + VERSION + "/users/authentication/rest/signin";
    private static final String URL_UPLOADER = "/" + VERSION + "/storage/uploader/rest/upload";
    private static final String URL_LOG_OUT = "/" + VERSION + "/users/authentication/rest/signout";
    private static final String URL_CREATE_OBJECT = "/deploy/createobject";
    private static final String URL_DEPLOY = "/deploy/DeployArchive";
    private static final String URL_GET_ARCHIVES = "/GetArchives";
    private static final String URL_DELETE_ARCHIVE = "/DeleteArchive";

    private static final int SAME_FILES_LIMIT = 5;
    private static final String COMMENT_PREFIX = "Uploaded by Maven plugin";
    private static final int PROGRESS_STEP = 10;

    //Properties
    private static final String JELASTIC_PREDEPLOY_HOOK_PROPERTY = "jelastic-predeploy-hook";
    private static final String JELASTIC_POSTDEPLOY_HOOK_PROPERTY = "jelastic-postdeploy-hook";
    private static final String NODE_GROUP_PROPERTY = "nodegroup";
    private static final String ENVIRONMENT_PROPERTY = "environment";
    private static final String CONTEXT_PROPERTY = "context";
    private static final String JELASTIC_EMAIL_PROPERTY = "jelastic-email";
    private static final String JELASTIC_PASSWORD_PROPERTY = "jelastic-password";
    private static final String JELASTIC_HOSTER_PROPERTY = "jelastic-hoster";
    private static final String JELASTIC_ACTION_KEY = "action-key";
    private static final String JELASTIC_ARTIFACT_NAME = "jelastic-artifact";
    private static final String JELASTIC_COMMENT_PROPERTY = "jelastic-comment";
    private static final String JELASTIC_HEADERS_PROPERTY = "jelastic-headers";
    private static final String JELASTIC_SESSION_PROPERTY = "jelastic-session";
    private static final String JELASTIC_TOKEN_PROPERTY = "jelastic-apitoken";
    private static final String JELASTIC_PROPERTIES_FILE = "jelastic-properties";
    private static final String JELASTIC_UPLOAD_ONLY_PROPERTY = "jelastic-upload-only";

    //Env. vars
    private static final String MAVEN_DEPLOY_ARTIFACT_ENV = "MAVEN_DEPLOY_ARTIFACT";

    private Properties externalProperties;
    private File artifactFile;
    private long totalSize;
    private boolean ownsSession;
    private int lastReportedProgress = -PROGRESS_STEP;

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The Maven session, used to read the proxy configuration of {@code settings.xml}.
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession mavenSession;

    /**
     * The packaging of the Maven project that this goal operates upon.
     */
    @Parameter(defaultValue = "${project.packaging}", readonly = true, required = true)
    private String packaging;

    /**
     * Directory scanned to find the artifact to upload.
     */
    @Parameter(defaultValue = "${project.build.directory}", property = "jelastic.outputDirectory", required = true)
    private File outputDirectory;

    /**
     * Extra HTTP headers sent with every API call.
     */
    @Parameter
    private Map<String, String> headers;

    /**
     * Account used to sign in, when no API token is provided.
     */
    @Parameter(property = "jelastic.email")
    private String email;

    /**
     * Password used to sign in, when no API token is provided.
     */
    @Parameter(property = "jelastic.password")
    private String password;

    /**
     * API token, to be preferred over the email/password pair.
     */
    @Parameter(property = "jelastic.apiToken")
    private String apiToken;

    /**
     * Comment attached to the uploaded archive. Defaults to the description of the project.
     */
    @Parameter(property = "jelastic.comment")
    private String comment;

    /**
     * Context the artifact is deployed to.
     */
    @Parameter(defaultValue = "ROOT", property = "jelastic.context")
    private String context;

    /**
     * Host name of the API of the hoster, for instance {@code api.jelastic.com} or
     * {@code api.pub1.infomaniak.cloud}.
     */
    @Parameter(defaultValue = "api.jelastic.com", property = "jelastic.hoster")
    private String api_hoster;

    /**
     * Name of the target environment.
     */
    @Parameter(property = "jelastic.environment")
    private String environment;

    /**
     * Name of the target node group, for instance {@code cp}.
     */
    @Parameter(property = "jelastic.nodeGroup")
    private String nodeGroup;

    /**
     * Name of the artifact to upload, when the output directory holds several of them.
     */
    @Parameter(property = "jelastic.artifact")
    private String artifact;

    /**
     * Additional parameters sent to the deploy call.
     */
    @Parameter
    private Map<String, String> deployParams;

    /**
     * Uploads and registers the archive without deploying it.
     */
    @Parameter(defaultValue = "false", property = "jelastic.uploadOnly")
    private boolean uploadOnly;

    /**
     * Skips the execution of the goal.
     */
    @Parameter(defaultValue = "false", property = "jelastic.skip")
    private boolean skip;

    /**
     * Accepts any TLS certificate. Only useful for a private platform using a self signed certificate; leaving it to
     * {@code false} is what protects the credentials sent to the API.
     */
    @Parameter(defaultValue = "false", property = "jelastic.trustAllCertificates")
    private boolean trustAllCertificates;

    /**
     * Connection timeout, in seconds.
     */
    @Parameter(defaultValue = "30", property = "jelastic.connectTimeoutSeconds")
    private int connectTimeoutSeconds;

    /**
     * Read timeout, in seconds. It applies between two blocks of data, not to the whole upload.
     */
    @Parameter(defaultValue = "300", property = "jelastic.socketTimeoutSeconds")
    private int socketTimeoutSeconds;

    /**
     * Runs the goal.
     *
     * @param deployArtifact {@code true} to deploy the archive once it is uploaded and registered.
     */
    protected void run(boolean deployArtifact) throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping Jelastic goal (jelastic.skip is set)");
            return;
        }

        if (!isSupportedPackaging()) {
            getLog().info("Skipping Jelastic goal: packaging [" + packaging + "] is neither WAR, EAR nor JAR");
            return;
        }

        JelasticClient client = createClient();
        Authentication authentication = null;
        try {
            authentication = authentication(client);
            getLog().info("------------------------------------------------------------------------");
            getLog().info("   Authentication : SUCCESS");
            getLog().info("------------------------------------------------------------------------");

            UpLoader upLoader = upload(client, authentication);
            getLog().info("      File UpLoad : SUCCESS");
            getLog().info("         File URL : " + upLoader.getFile());
            getLog().info("        File size : " + upLoader.getSize());
            getLog().info("------------------------------------------------------------------------");

            CreateObject createObject = createObject(client, upLoader, authentication);
            getLog().info("File registration : SUCCESS");
            getLog().info("  Registration ID : " + createObject.getResponse().getObject().getId());
            getLog().info("     Developer ID : " + createObject.getResponse().getObject().getDeveloper());
            getLog().info("------------------------------------------------------------------------");

            deleteObsoleteArchives(client, authentication);

            if (deployArtifact && !isUploadOnly()) {
                Deploy deploy = deploy(client, authentication, upLoader);
                getLog().info("      Deploy file : SUCCESS");
                getLog().info("       Deploy log :");
                getLog().info(getDeployOutput(deploy));
            }
        } finally {
            if (ownsSession && authentication != null) {
                logOut(client, authentication);
            }
            client.close();
        }
    }

    boolean isSupportedPackaging() {
        return WAR_TYPE.equals(packaging) || EAR_TYPE.equals(packaging) || JAR_TYPE.equals(packaging);
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    private JelasticClient createClient() {
        JelasticClient client = new JelasticClient(SCHEMA, getApiJelastic(), DEFAULT_PORT, getMavenProxy(),
                trustAllCertificates, connectTimeoutSeconds, socketTimeoutSeconds, getLog());
        client.addHeaders(getHeaders());

        return client;
    }

    private String getApiJelastic() {
        String hoster = System.getProperty(JELASTIC_HOSTER_PROPERTY);
        if (isNotEmpty(hoster)) {
            api_hoster = hoster;
        }

        return api_hoster;
    }

    private Map<String, String> getHeaders() {
        String jelasticHeaders = System.getProperty(JELASTIC_HEADERS_PROPERTY);
        getLog().debug(JELASTIC_HEADERS_PROPERTY + "=" + jelasticHeaders);

        if (isNotEmpty(jelasticHeaders)) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> decoded = new ObjectMapper()
                        .readValue(URLDecoder.decode(jelasticHeaders, "UTF-8"), Map.class);
                if (headers == null) {
                    headers = decoded;
                } else {
                    headers.putAll(decoded);
                }
                getLog().debug("headers=" + headers);
            } catch (IOException e) {
                getLog().warn("Unable to read [" + JELASTIC_HEADERS_PROPERTY + "]: " + e.getMessage());
            }
        }

        return headers;
    }

    Authentication authentication(JelasticClient client) throws MojoExecutionException {
        String session = getSessionFromProperties();
        if (session != null) {
            getLog().debug("auth by " + JELASTIC_SESSION_PROPERTY);
            return sessionOnly(session);
        }

        String token = getApiToken();
        if (isNotEmpty(token)) {
            getLog().debug("auth by apitoken");
            return sessionOnly(token);
        }

        getLog().debug("auth by email/password");
        String login = getEmail();
        if (!isNotEmpty(login) || !isNotEmpty(getPassword())) {
            throw new MojoExecutionException("No credentials found: set <apiToken>, or <email> and <password>, "
                    + "in the plugin configuration (or through the jelastic-apitoken, jelastic-email and "
                    + "jelastic-password system properties).");
        }

        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("login", login);
        params.put("password", getPassword());

        Authentication authentication = call(client, "Authentication", URL_AUTHENTICATION, params,
                Authentication.class);
        checkResult("Authentication", authentication.getResult(), authentication.getError());
        ownsSession = true;

        if (!isNotEmpty(authentication.getSession())) {
            throw new MojoExecutionException("Authentication succeeded but no session was returned by "
                    + getApiJelastic() + ".");
        }

        return authentication;
    }

    private Authentication sessionOnly(String session) {
        Authentication authentication = new Authentication();
        authentication.setSession(session);
        authentication.setResult(0);

        return authentication;
    }


    /**
     * Builds the multipart body of the upload request.
     *
     * <p>The part headers are written in UTF-8 so that an artifact name holding non ASCII characters survives the
     * trip, but the resulting {@code Content-Type} advertises the boundary and <b>nothing else</b>. That second half
     * is not cosmetic. {@code MultipartEntityBuilder.setCharset} appends the charset as a second parameter, after the
     * boundary:</p>
     *
     * <pre>multipart/form-data; boundary=BTDqPv9a1viO6ve; charset=UTF-8</pre>
     *
     * <p>which is valid HTTP, and which the Jelastic uploader reads as a boundary named
     * {@code BTDqPv9a1viO6ve; charset=UTF-8}. It then looks for that string in the body, never finds it, and answers
     * with its own crash rather than a message:</p>
     *
     * <pre>result=99, error=java.lang.StringIndexOutOfBoundsException: start 0, end -1, length 4293</pre>
     *
     * <p>The upload is the very first call of a deployment, so the whole chain stops there — with an error naming
     * neither the plugin, nor the charset, nor the request. Observed on app.jpe.infomaniak.com in September 2026,
     * on three consecutive deployments of a project that had switched from 1.9.5 to this fork; 1.9.5 sent no charset
     * and kept working on the same platform, the same day, with the same token.</p>
     */
    static HttpEntity uploadBody(File artifactFile, String session) {
        ContentType textType = ContentType.create("text/plain", StandardCharsets.UTF_8);
        HttpEntity multipart = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .setCharset(StandardCharsets.UTF_8)
                .addTextBody("fid", "123456", textType)
                .addTextBody("session", session, textType)
                .addBinaryBody("file", artifactFile, ContentType.APPLICATION_OCTET_STREAM, artifactFile.getName())
                .build();

        return new BoundaryOnlyContentType(multipart);
    }

    public UpLoader upload(JelasticClient client, Authentication authentication) throws MojoExecutionException {
        artifactFile = selectArtifact();

        getLog().info("File Uploading Progress :");
        lastReportedProgress = -PROGRESS_STEP;

        HttpEntity multipart = uploadBody(artifactFile, authentication.getSession());

        totalSize = multipart.getContentLength();

        HttpEntity entity = new ProgressHttpEntity(multipart, new ProgressHttpEntity.ProgressListener() {
            public void transferred(long transferred) {
                reportProgress(transferred);
            }
        });

        UpLoader upLoader;
        try {
            upLoader = client.post(URL_UPLOADER, entity, UpLoader.class);
        } catch (IOException e) {
            throw new MojoExecutionException("File upload failed: " + e.getMessage(), e);
        }

        checkResult("File upload", upLoader.getResult(), upLoader.getError());

        if (isNotEmpty(upLoader.getFile())) {
            String fileUrl = upLoader.getFile().replaceFirst(HTTP_PROTOCOL, HTTPS_PROTOCOL);
            if (isAvailableByHttps(fileUrl)) {
                upLoader.setFile(fileUrl);
            }
        }

        return upLoader;
    }

    private void reportProgress(long transferred) {
        if (totalSize <= 0) {
            return;
        }

        int percent = (int) ((transferred / (float) totalSize) * 100);
        if (percent - lastReportedProgress >= PROGRESS_STEP || (percent >= 100 && lastReportedProgress < 100)) {
            getLog().info("[" + percent + "%]");
            lastReportedProgress = percent;
        }
    }

    private File selectArtifact() throws MojoExecutionException {
        File[] files = outputDirectory.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                return pathname.isFile()
                        && pathname.getName().matches(".*\\.(" + WAR_TYPE + "|" + EAR_TYPE + "|" + JAR_TYPE + ")$");
            }
        });

        if (files == null || files.length == 0) {
            throw new MojoExecutionException("No artifact found in [" + outputDirectory
                    + "]: build the project before deploying it.");
        }

        List<File> candidates = new ArrayList<File>(Arrays.asList(files));
        File selected = chooseArtifact(outputDirectory, candidates, deployableOutputs(),
                getCustomArtifactName());

        getLog().debug("Found artifacts:");
        for (File file : candidates) {
            getLog().debug("\t" + (selected.getName().equals(file.getName()) ? "(*) " : "  * ")
                    + file.getName() + " - " + file.length());
        }

        getLog().info("Selected artifact: " + selected.getAbsolutePath());

        return selected;
    }

    /**
     * The files this build actually produced and that make sense to deploy.
     *
     * <p>The main artifact plus the attached ones, minus the companions nobody deploys: sources, javadoc and test
     * jars. Empty when {@code deploy} runs in an invocation of its own — {@code mvn jelastic:deploy} without
     * {@code package} — because Maven has then attached no file to the project.</p>
     */
    private List<File> deployableOutputs() {
        List<File> outputs = new ArrayList<File>();
        if (project == null) {
            return outputs;
        }

        List<Artifact> artifacts = new ArrayList<Artifact>();
        if (project.getArtifact() != null) {
            artifacts.add(project.getArtifact());
        }
        if (project.getAttachedArtifacts() != null) {
            artifacts.addAll(project.getAttachedArtifacts());
        }

        for (Artifact artifact : artifacts) {
            String classifier = artifact.getClassifier();
            if (COMPANION_CLASSIFIERS.contains(classifier)) {
                continue;
            }
            if (artifact.getFile() != null && artifact.getFile().isFile()) {
                outputs.add(artifact.getFile());
            }
        }

        return outputs;
    }

    /**
     * Picks the artifact to upload.
     *
     * <h2>What was wrong with « the biggest file of the directory »</h2>
     * <p>Size was the only criterion, and it works right up to the day it does not: {@code target/} is a scratch
     * directory, not a manifest. A {@code copy-dependencies} execution, a shaded test jar, a leftover from a previous
     * build under another {@code finalName} — anything bigger than the application wins, and the deployment succeeds
     * while putting the wrong code online. Nothing in the log says so, because the log only ever printed the winner.</p>
     *
     * <p>Selection now starts from what Maven <b>produced</b> — the main artifact and its attached ones — and falls
     * back on the directory listing only when the project carries no attached file, which is what happens when
     * {@code jelastic:deploy} runs in an invocation of its own.</p>
     *
     * <h2>Size still decides, and on purpose</h2>
     * <p>A Spring Boot project configured with {@code <classifier>exec</classifier>} produces two jars: the plain
     * library jar, and the executable one that bundles every dependency. Both are artifacts of the build, both are
     * legitimately named, and only the second one runs. The bigger of the two <b>is</b> the executable one — so size
     * remains the tie-break, now applied between siblings of the same build rather than between a build output and
     * whatever else sits in the directory.</p>
     *
     * <h2>A named artifact that is missing is an error</h2>
     * <p>It used to warn and deploy the biggest file instead. Someone who names an artifact has a reason to; a typo in
     * that name then put an unintended jar online, and the warning scrolled past in a CI log nobody reads when the
     * build is green. Failing costs a red build and a one-line fix.</p>
     *
     * @param outputDirectory the directory being scanned, for the error messages
     * @param candidates      every deployable-looking file found there, in listing order
     * @param buildOutputs    the files this build produced, possibly empty
     * @param requested       the artifact named by the caller, possibly {@code null}
     */
    static File chooseArtifact(File outputDirectory, List<File> candidates, List<File> buildOutputs,
                               String requested) throws MojoExecutionException {
        if (isNotEmpty(requested)) {
            for (File candidate : candidates) {
                if (candidate.getName().equals(requested)) {
                    return candidate;
                }
            }
            File named = new File(outputDirectory, requested);
            if (named.isFile()) {
                return named;
            }
            throw new MojoExecutionException("Artifact [" + requested + "] not found in [" + outputDirectory
                    + "]. Deploying another one would put code online that nobody asked for: fix the name, or drop it"
                    + " to let the build decide.");
        }

        List<File> preferred = new ArrayList<File>();
        for (File candidate : candidates) {
            if (contains(buildOutputs, candidate)) {
                preferred.add(candidate);
            }
        }
        List<File> retained = preferred.isEmpty() ? candidates : preferred;

        List<File> sorted = new ArrayList<File>(retained);
        Collections.sort(sorted, new Comparator<File>() {
            public int compare(File left, File right) {
                return Long.valueOf(right.length()).compareTo(left.length());
            }
        });

        return sorted.get(0);
    }

    /** Same file, whatever the path was spelled like — {@code target/x.jar} and an absolute path are one file. */
    private static boolean contains(List<File> files, File wanted) {
        for (File file : files) {
            if (file.getAbsoluteFile().equals(wanted.getAbsoluteFile())) {
                return true;
            }
        }
        return false;
    }

    public CreateObject createObject(JelasticClient client, UpLoader upLoader, Authentication authentication)
            throws MojoExecutionException {
        Map<String, Object> data = archiveData(artifactFile.getName(), upLoader.getFile(), upLoader.getSize(),
                getArtifactComment());

        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("charset", "UTF-8");
        params.put("session", authentication.getSession());
        params.put("type", "JDeploy");
        params.put("data", toJson(client, data));

        CreateObject createObject = call(client, "Create object", URL_CREATE_OBJECT, params, CreateObject.class);
        checkResult("Create object", createObject.getResult(), createObject.getError());

        CreateObject.JelasticResponse response = createObject.getResponse();
        if (response == null) {
            throw new MojoExecutionException("Create object failed: the platform returned no response body.");
        }

        checkResult("Create object", response.getResult(), response.getError());

        if (response.getObject() == null) {
            throw new MojoExecutionException("Create object failed: the platform registered no object.");
        }

        return createObject;
    }

    /**
     * Describes the uploaded archive.
     *
     * <p>Serialized with Jackson rather than concatenated by hand: a description holding a quote, a backslash or a
     * non ASCII character used to produce an invalid JSON payload, which the platform rejected with an error that had
     * nothing to do with the real cause.</p>
     */
    static Map<String, Object> archiveData(String name, String archive, long size, String comment) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("name", name);
        data.put("archive", archive);
        data.put("link", 0);
        data.put("size", size);
        data.put("comment", comment);

        return data;
    }

    /**
     * Keeps at most {@link #SAME_FILES_LIMIT} archives uploaded by this plugin for a given file name.
     *
     * <p>Housekeeping only: a failure here never fails the build.</p>
     */
    private void deleteObsoleteArchives(JelasticClient client, Authentication authentication) {
        try {
            Map<String, String> params = new LinkedHashMap<String, String>();
            params.put("charset", "UTF-8");
            params.put("session", authentication.getSession());

            Archives archives = client.get(URL_GET_ARCHIVES, params, Archives.class);
            if (archives == null || archives.getResult() != 0 || archives.getResponse() == null
                    || archives.getResponse().getResult() != 0 || archives.getResponse().getObjects().isEmpty()) {
                return;
            }

            List<Integer> ids = new ArrayList<Integer>();
            for (Archive archive : archives.getResponse().getObjects()) {
                if (artifactFile.getName().equals(archive.getName())
                        && archive.getComment() != null && archive.getComment().startsWith(COMMENT_PREFIX)) {
                    ids.add(archive.getId());
                }
            }

            if (ids.size() < SAME_FILES_LIMIT) {
                return;
            }

            Collections.sort(ids);

            for (int id : ids.subList(0, ids.size() - SAME_FILES_LIMIT)) {
                Map<String, String> parameters = new LinkedHashMap<String, String>(params);
                parameters.put("id", String.valueOf(id));

                getLog().debug("Deleting obsolete archive " + id);
                client.get(URL_DELETE_ARCHIVE, parameters, null);
            }
        } catch (Exception e) {
            getLog().debug("Clean up of the obsolete archives skipped: " + e.getMessage());
        }
    }

    public Deploy deploy(JelasticClient client, Authentication authentication, UpLoader upLoader)
            throws MojoExecutionException {
        String targetEnvironment = getEnvironment();
        if (!isNotEmpty(targetEnvironment)) {
            throw new MojoExecutionException("No target environment: set <environment> in the plugin configuration "
                    + "(or the jelastic-environment system property).");
        }

        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("charset", "UTF-8");
        params.put("session", authentication.getSession());
        params.put("archiveUri", upLoader.getFile());
        params.put("archiveName", upLoader.getName());
        params.put("newContext", getContext());
        params.put("domain", targetEnvironment);
        params.put("nodeGroup", getNodeGroup());

        String preDeployHookContent = getPreDeployHookContent();
        if (preDeployHookContent != null) {
            params.put("preDeployHook", preDeployHookContent);
        }

        String postDeployHookContent = getPostDeployHookContent();
        if (postDeployHookContent != null) {
            params.put("postDeployHook", postDeployHookContent);
        }

        String actionKey = System.getProperty(JELASTIC_ACTION_KEY);
        if (actionKey != null) {
            params.put("actionkey", actionKey);
        }

        if (deployParams != null) {
            for (Map.Entry<String, String> entry : deployParams.entrySet()) {
                if (entry.getValue() == null || entry.getValue().trim().isEmpty()) {
                    continue;
                }
                params.put(entry.getKey(), entry.getValue());
            }
        }

        Deploy deploy;
        try {
            deploy = client.get(URL_DEPLOY, params, Deploy.class);
        } catch (IOException e) {
            throw new MojoExecutionException("Deploy failed: " + e.getMessage(), e);
        }

        checkResult("Deploy", deploy.getResult(), deploy.getError());

        Deploy.JelasticResponse response = deploy.getResponse();
        if (response == null) {
            throw new MojoExecutionException("Deploy failed: the platform returned no response body.");
        }

        checkResult("Deploy", response.getResult(), response.getError());

        return deploy;
    }

    private String getDeployOutput(Deploy deploy) {
        Deploy.JelasticResponse response = deploy.getResponse();
        if (response.getResponses() != null) {
            StringBuilder sb = new StringBuilder();
            for (Deploy.JelasticResponse.JelasticResponses nodeResponse : response.getResponses()) {
                if (nodeResponse == null || nodeResponse.getOut() == null) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(System.getProperty("line.separator"));
                }
                sb.append(nodeResponse.getOut());
            }

            if (sb.length() > 0) {
                return sb.toString();
            }
        }

        return response.getOut() != null ? response.getOut() : "(no output)";
    }

    /**
     * Closes the session opened by this run. The artifact is already deployed at this point, so a failure here is
     * reported but never fails the build.
     */
    public void logOut(JelasticClient client, Authentication authentication) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("charset", "UTF-8");
        params.put("session", authentication.getSession());

        try {
            LogOut logOut = client.get(URL_LOG_OUT, params, LogOut.class);
            if (logOut.getResult() != 0) {
                getLog().warn("           LogOut : FAILED - " + logOut.getError());
            } else {
                getLog().info("           LogOut : SUCCESS");
            }
        } catch (IOException e) {
            getLog().warn("           LogOut : FAILED - " + e.getMessage());
        }
    }

    private <T> T call(JelasticClient client, String step, String url, Map<String, String> params, Class<T> type)
            throws MojoExecutionException {
        try {
            return client.post(url, params, type);
        } catch (IOException e) {
            throw new MojoExecutionException(step + " failed: " + e.getMessage(), e);
        }
    }

    private String toJson(JelasticClient client, Map<String, Object> data) throws MojoExecutionException {
        try {
            return client.getMapper().writeValueAsString(data);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to build the archive description: " + e.getMessage(), e);
        }
    }

    /**
     * Fails the build with the message returned by the platform instead of a bare {@code NullPointerException}.
     */
    private void checkResult(String step, int result, String error) throws MojoExecutionException {
        if (result == 0) {
            return;
        }

        String message = step + " failed: " + (isNotEmpty(error) ? error : "no message returned by the platform")
                + " [result=" + result + "]";
        getLog().error(message);

        throw new MojoExecutionException(message);
    }

    String getArtifactComment() {
        String localComment = System.getProperty(JELASTIC_COMMENT_PROPERTY);

        if (!isNotEmpty(localComment)) {
            localComment = comment;
        }

        if (!isNotEmpty(localComment) && project != null && project.getModel() != null) {
            localComment = project.getModel().getDescription();
        }

        if (!isNotEmpty(localComment)) {
            return COMMENT_PREFIX;
        }

        return COMMENT_PREFIX + ". " + localComment.replaceAll("\\s+", " ").trim();
    }

    private String getSessionFromProperties() {
        String session = System.getProperty(JELASTIC_SESSION_PROPERTY);

        return isNotEmpty(session) ? session : null;
    }

    private String getApiToken() {
        String fromProperties = System.getProperty(JELASTIC_TOKEN_PROPERTY);

        return isNotEmpty(fromProperties) ? fromProperties : apiToken;
    }

    private String getEmail() {
        return getProperty(JELASTIC_EMAIL_PROPERTY, JELASTIC_EMAIL_PROPERTY, email);
    }

    private String getPassword() {
        return getProperty(JELASTIC_PASSWORD_PROPERTY, JELASTIC_PASSWORD_PROPERTY, password);
    }

    private String getContext() {
        return getProperty("jelastic-" + CONTEXT_PROPERTY, CONTEXT_PROPERTY, context);
    }

    private String getEnvironment() {
        return getProperty("jelastic-" + ENVIRONMENT_PROPERTY, ENVIRONMENT_PROPERTY, environment);
    }

    private String getNodeGroup() {
        return getProperty("jelastic-" + NODE_GROUP_PROPERTY, NODE_GROUP_PROPERTY, nodeGroup);
    }

    /**
     * Reads a value from, in order, the system properties, the file pointed at by {@code jelastic-properties} and
     * finally the plugin configuration.
     */
    private String getProperty(String systemPropertyName, String filePropertyName, String configuredValue) {
        String fromSystem = System.getProperty(systemPropertyName);
        if (isNotEmpty(fromSystem)) {
            return fromSystem;
        }

        String fromFile = getExternalProperties().getProperty(filePropertyName);
        if (isNotEmpty(fromFile)) {
            return fromFile;
        }

        return configuredValue;
    }

    private Properties getExternalProperties() {
        if (externalProperties != null) {
            return externalProperties;
        }

        externalProperties = new Properties();
        String path = System.getProperty(JELASTIC_PROPERTIES_FILE);
        if (isNotEmpty(path)) {
            InputStream is = null;
            try {
                is = Files.newInputStream(Paths.get(path));
                Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                externalProperties.load(reader);
            } catch (IOException e) {
                getLog().error("Unable to read [" + path + "]: " + e.getMessage());
            } finally {
                closeQuietly(is);
            }
        }

        return externalProperties;
    }

    public boolean isExternalParameterPassed() {
        return !getExternalProperties().isEmpty();
    }

    public boolean isUploadOnly() {
        if (uploadOnly) {
            return true;
        }

        String value = System.getProperty(JELASTIC_UPLOAD_ONLY_PROPERTY);

        return value != null && (value.equalsIgnoreCase("1") || value.equalsIgnoreCase("true"));
    }

    private String getCustomArtifactName() {
        String artifactName = System.getProperty(JELASTIC_ARTIFACT_NAME);
        if (isNotEmpty(artifactName)) {
            return artifactName;
        }

        if (isNotEmpty(artifact)) {
            return artifact;
        }

        return getCustomArtifactNameFromEnvVar();
    }

    private String getCustomArtifactNameFromEnvVar() {
        String projectComment = System.getProperty(JELASTIC_COMMENT_PROPERTY);
        getLog().debug("***** comment: " + projectComment);
        if (!isNotEmpty(projectComment)) {
            return null;
        }

        //Get value for MAVEN_DEPLOY_ARTIFACT_project_name
        String projectName = projectComment.replaceAll(" ", "_").replaceAll("-", "_");
        String envVar = MAVEN_DEPLOY_ARTIFACT_ENV + "_" + projectName;
        String value = System.getenv(envVar);
        getLog().debug(envVar + "=" + value);

        if (!isNotEmpty(value)) {
            //Get value for MAVEN_DEPLOY_ARTIFACT
            value = System.getenv(MAVEN_DEPLOY_ARTIFACT_ENV);
            getLog().debug(MAVEN_DEPLOY_ARTIFACT_ENV + "=" + value);
        }

        return value;
    }

    private String getPreDeployHookContent() {
        return readHook(JELASTIC_PREDEPLOY_HOOK_PROPERTY, "preDeployHook");
    }

    private String getPostDeployHookContent() {
        return readHook(JELASTIC_POSTDEPLOY_HOOK_PROPERTY, "postDeployHook");
    }

    private String readHook(String property, String label) {
        String path = System.getProperty(property);
        if (!isNotEmpty(path)) {
            return null;
        }

        try {
            byte[] content = Files.readAllBytes(Paths.get(path));
            return new String(content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLog().warn("Can't read [" + label + "] from [" + path + "]: " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns the first active proxy of {@code settings.xml} able to serve the API host.
     */
    private Proxy getMavenProxy() {
        if (mavenSession == null || mavenSession.getSettings() == null) {
            return null;
        }

        List<Proxy> proxyList = mavenSession.getSettings().getProxies();
        if (proxyList == null) {
            return null;
        }

        for (Proxy proxy : proxyList) {
            if (!proxy.isActive()) {
                continue;
            }

            String protocol = proxy.getProtocol();
            if (protocol != null && !HTTP_PROTOCOL.equalsIgnoreCase(protocol)
                    && !HTTPS_PROTOCOL.equalsIgnoreCase(protocol)) {
                continue;
            }

            if (isNonProxyHost(proxy.getNonProxyHosts(), getApiJelastic())) {
                getLog().debug("Host [" + getApiJelastic() + "] excluded from the proxy by nonProxyHosts");
                continue;
            }

            return proxy;
        }

        return null;
    }

    static boolean isNonProxyHost(String nonProxyHosts, String host) {
        if (nonProxyHosts == null || nonProxyHosts.trim().isEmpty() || host == null) {
            return false;
        }

        for (String pattern : nonProxyHosts.split("[|,]")) {
            String trimmed = pattern.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String regex = "\\Q" + trimmed.replace("*", "\\E.*\\Q") + "\\E";
            if (host.matches(regex)) {
                return true;
            }
        }

        return false;
    }

    private boolean isAvailableByHttps(String fileUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(fileUrl).toURL().openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(connectTimeoutSeconds * 1000);
            connection.setReadTimeout(connectTimeoutSeconds * 1000);

            return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
        } catch (IOException e) {
            getLog().debug("[" + fileUrl + "] is not reachable over HTTPS: " + e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            getLog().debug("[" + fileUrl + "] is not a valid URL: " + e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void closeQuietly(InputStream is) {
        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
                getLog().debug("Unable to close the stream: " + e.getMessage());
            }
        }
    }

    private static boolean isNotEmpty(String value) {
        return value != null && !value.isEmpty();
    }
}
