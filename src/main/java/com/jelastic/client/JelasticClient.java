package com.jelastic.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContexts;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.settings.Proxy;

import javax.net.ssl.SSLContext;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin HTTP client in front of the Jelastic REST API.
 *
 * <p>It centralises what used to be copied and pasted in every API call: proxy configuration, cookie handling,
 * timeouts, custom headers and, above all, error handling. Every call either returns a parsed answer or throws a
 * {@link JelasticApiException} describing what the platform actually replied.</p>
 */
public class JelasticClient implements Closeable {

    private static final String USER_AGENT = "jelastic-maven-plugin";

    private final String scheme;
    private final String host;
    private final int port;
    private final Log log;
    private final ObjectMapper mapper;
    private final CookieStore cookieStore = new BasicCookieStore();
    private final Map<String, String> headers = new LinkedHashMap<String, String>();

    private CloseableHttpClient httpClient;

    public JelasticClient(String scheme, String host, int port, Proxy proxy, boolean trustAllCertificates,
                          int connectTimeoutSeconds, int socketTimeoutSeconds, Log log) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.log = log;
        this.mapper = new ObjectMapper();
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.httpClient = createHttpClient(proxy, trustAllCertificates, connectTimeoutSeconds, socketTimeoutSeconds);
    }

    public void addHeaders(Map<String, String> additionalHeaders) {
        if (additionalHeaders != null) {
            headers.putAll(additionalHeaders);
        }
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    /**
     * Sends the parameters as an UTF-8 url encoded form.
     */
    public <T> T post(String path, Map<String, String> params, Class<T> type) throws IOException {
        HttpPost httpPost = new HttpPost(buildUri(path, null));
        httpPost.setEntity(new UrlEncodedFormEntity(toNameValuePairs(params), "UTF-8"));

        return execute(httpPost, type);
    }

    /**
     * Sends an arbitrary entity, typically a multipart upload.
     */
    public <T> T post(String path, HttpEntity entity, Class<T> type) throws IOException {
        HttpPost httpPost = new HttpPost(buildUri(path, null));
        httpPost.setEntity(entity);

        return execute(httpPost, type);
    }

    /**
     * Sends the parameters in the query string, url encoded in UTF-8.
     */
    public <T> T get(String path, Map<String, String> params, Class<T> type) throws IOException {
        return execute(new HttpGet(buildUri(path, params)), type);
    }

    public void close() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                log.debug("Unable to close the HTTP client: " + e.getMessage());
            } finally {
                httpClient = null;
            }
        }
    }

    private <T> T execute(HttpRequestBase request, Class<T> type) throws IOException {
        for (Map.Entry<String, String> header : headers.entrySet()) {
            request.addHeader(header.getKey(), header.getValue());
        }

        request.addHeader("Accept", "application/json");

        log.debug(request.getMethod() + " " + request.getURI());

        return httpClient.execute(request, new JsonResponseHandler<T>(mapper, type, log));
    }

    private URI buildUri(String path, Map<String, String> params) throws IOException {
        try {
            URIBuilder builder = new URIBuilder()
                    .setScheme(scheme)
                    .setHost(host)
                    .setPort(port)
                    .setPath(path.startsWith("/") ? path : "/" + path);

            if (params != null) {
                builder.setParameters(toNameValuePairs(params));
            }

            return builder.build();
        } catch (URISyntaxException e) {
            throw new IOException("Unable to build the URL for [" + path + "] on host [" + host + "]", e);
        }
    }

    private List<NameValuePair> toNameValuePairs(Map<String, String> params) {
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                pairs.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
            }
        }

        return pairs;
    }

    private CloseableHttpClient createHttpClient(Proxy proxy, boolean trustAllCertificates,
                                                 int connectTimeoutSeconds, int socketTimeoutSeconds) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setCookieSpec(CookieSpecs.DEFAULT)
                .setConnectTimeout(connectTimeoutSeconds * 1000)
                .setConnectionRequestTimeout(connectTimeoutSeconds * 1000)
                .setSocketTimeout(socketTimeoutSeconds * 1000)
                .build();

        HttpClientBuilder builder = HttpClients.custom()
                .useSystemProperties()
                .setUserAgent(USER_AGENT)
                .setDefaultRequestConfig(requestConfig)
                .setDefaultCookieStore(cookieStore);

        if (trustAllCertificates) {
            log.warn("TLS certificate validation is disabled for [" + host + "]: the connection can be intercepted.");
            builder.setSSLSocketFactory(createInsecureSocketFactory());
        }

        if (proxy != null) {
            log.debug("Using proxy " + proxy.getHost() + ":" + proxy.getPort());
            builder.setProxy(new HttpHost(proxy.getHost(), proxy.getPort(), proxyScheme(proxy)));

            if (proxy.getUsername() != null && proxy.getUsername().length() > 0) {
                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(new AuthScope(proxy.getHost(), proxy.getPort()),
                        new UsernamePasswordCredentials(proxy.getUsername(), proxy.getPassword()));
                builder.setDefaultCredentialsProvider(credentialsProvider);
            }
        }

        return builder.build();
    }

    private static String proxyScheme(Proxy proxy) {
        String protocol = proxy.getProtocol();
        if (protocol == null || protocol.length() == 0) {
            return "http";
        }

        // Maven declares the protocol of the proxy itself; both http and https proxies are reached over http(s).
        return protocol.equalsIgnoreCase("https") ? "https" : "http";
    }

    private SSLConnectionSocketFactory createInsecureSocketFactory() {
        try {
            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, new TrustStrategy() {
                        public boolean isTrusted(X509Certificate[] chain, String authType) {
                            return true;
                        }
                    })
                    .build();

            return new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to disable TLS certificate validation", e);
        }
    }
}
