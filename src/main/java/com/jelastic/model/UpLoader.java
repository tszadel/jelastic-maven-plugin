package com.jelastic.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * User: Igor.Yova@gmail.com
 * Date: 6/9/11
 * Time: 3:18 PM
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpLoader {
    private int result;
    private String file;
    private JelasticRequest request;
    private String name;
    private long size;
    @JsonDeserialize(using = ErrorMessageDeserializer.class)
    private String error;

    public UpLoader() {
    }

    public UpLoader(int result, String file, JelasticRequest request, String name, long size, String error) {
        this.result = result;
        this.file = file;
        this.request = request;
        this.name = name;
        this.size = size;
        this.error = error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JelasticRequest {
        private String fid;
        private String session;
        private String appid;
        private String file;
        private String charset;

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }

        public String getFid() {
            return fid;
        }

        public void setFid(String fid) {
            this.fid = fid;
        }

        public String getSession() {
            return session;
        }

        public void setSession(String session) {
            this.session = session;
        }

        public String getAppid() {
            return appid;
        }

        public void setAppid(String appid) {
            this.appid = appid;
        }

        public String getCharset() {
            return charset;
        }

        public void setCharset(String charset) {
            this.charset = charset;
        }
    }

    public int getResult() {
        return result;
    }

    public void setResult(int result) {
        this.result = result;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public JelasticRequest getRequest() {
        return request;
    }

    public void setRequest(JelasticRequest request) {
        this.request = request;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
