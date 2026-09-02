package com.jelastic.model;


//{"response":{"result":8,"error":"access not permitted [subject = 11759/0 accessObject = [JDeploy, JDeploy/*] rights = [CREATE, CREATE_ONCE]]"},"result":0,"debug":{"time":64,"cpu":{"usage":9.757838,"time":50}}}

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateObject {
    private JelasticResponse response;
    private int result;
    @JsonDeserialize(using = ErrorMessageDeserializer.class)
    private String error;
    private Debug debug;

    public CreateObject() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JelasticObject {
        private int id;
        private String developer;
        private String uploadDate;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getDeveloper() {
            return developer;
        }

        public void setDeveloper(String developer) {
            this.developer = developer;
        }

        public String getUploadDate() {
            return uploadDate;
        }

        public void setUploadDate(String uploadDate) {
            this.uploadDate = uploadDate;
        }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JelasticResponse {
        private int id;
        private int result;
        @JsonDeserialize(using = ErrorMessageDeserializer.class)
        private String error;
        private JelasticObject object;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public int getResult() {
            return result;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }

        public void setResult(int result) {
            this.result = result;
        }

        public JelasticObject getObject() {
            return object;
        }

        public void setObject(JelasticObject object) {
            this.object = object;
        }
    }

    public JelasticResponse getResponse() {
        return response;
    }

    public void setResponse(JelasticResponse response) {
        this.response = response;
    }

    public int getResult() {
        return result;
    }

    public void setResult(int result) {
        this.result = result;
    }

    public Debug getDebug() {
        return debug;
    }

    public void setDebug(Debug debug) {
        this.debug = debug;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
