package com.jelastic.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * User: Igor.Yova@gmail.com
 * Date: 6/9/11
 * Time: 12:10 PM
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Authentication {
    private String uid;
    private int result;
    private String session;
    private String email;
    private String name;
    private Debug debug;
    @JsonDeserialize(using = ErrorMessageDeserializer.class)
    private String error;


    public Authentication() {
    }

    public Authentication(String uid, int result, String session, String email, String name, Debug debug, String error) {
        this.uid = uid;
        this.result = result;
        this.session = session;
        this.email = email;
        this.name = name;
        this.debug = debug;
        this.error = error;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public int getResult() {
        return result;
    }

    public void setResult(int result) {
        this.result = result;
    }

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
