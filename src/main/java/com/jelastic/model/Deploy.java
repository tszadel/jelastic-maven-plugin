package com.jelastic.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * User: Igor.Yova@gmail.com
 * Date: 6/9/11
 * Time: 4:13 PM
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Deploy {

    //{"response":{"result":0,"responses":[{"result":0,"nodeid":1566,"out":"gfhdf"}]},"result":0,"debug":{"time":17593,"cpu":{"usage":0.01421001,"time":20}}}

    private JelasticResponse response;
    private int result;
    @JsonDeserialize(using = ErrorMessageDeserializer.class)
    private String error;
    private Debug debug;

    public Deploy() {
    }

    public Deploy(JelasticResponse response, int result, String error, Debug debug) {
        this.response = response;
        this.result = result;
        this.error = error;
        this.debug = debug;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
     public static class JelasticResponse {
        private int result;
        private JelasticResponses[] responses;
        @JsonDeserialize(using = ErrorMessageDeserializer.class)
        private String error;
        private String out;

         public int getResult() {
             return result;
         }

         public void setResult(int result) {
             this.result = result;
         }

         public JelasticResponses[] getResponses() {
             return responses;
         }

         public void setResponses(JelasticResponses[] responses) {
             this.responses = responses;
         }

         public String getError() {
             return error;
         }

         public void setError(String error) {
             this.error = error;
         }

         public String getOut() {
             return out;
         }

         public void setOut(String out) {
             this.out = out;
         }

        @JsonIgnoreProperties(ignoreUnknown = true)
         public static class JelasticResponses {
             private int result;
             private int nodeid;
             private String out;

             public int getResult() {
                 return result;
             }

             public void setResult(int result) {
                 this.result = result;
             }

             public int getNodeid() {
                 return nodeid;
             }

             public void setNodeid(int nodeid) {
                 this.nodeid = nodeid;
             }

             public String getOut() {
                 return out;
             }

             public void setOut(String out) {
                 this.out = out;
             }
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

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Debug getDebug() {
        return debug;
    }

    public void setDebug(Debug debug) {
        this.debug = debug;
    }
}
