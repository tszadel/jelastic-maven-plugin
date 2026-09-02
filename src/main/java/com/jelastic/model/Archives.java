package com.jelastic.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by doba on 6/17/15.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Archives {

        private JelasticResponse response;
        private int result;
        @JsonDeserialize(using = ErrorMessageDeserializer.class)
        private String error;
        private Debug debug;

        public Archives() {

        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class JelasticResponse {
            private int result;
            @JsonDeserialize(using = ErrorMessageDeserializer.class)
            private String error;
            private List<Archive> objects =  new ArrayList<Archive>();

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

            public List<Archive> getObjects() {
                return objects;
            }

            public void setObjects(List<Archive> objects) {
                this.objects = objects;
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

