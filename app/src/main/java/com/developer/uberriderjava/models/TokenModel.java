package com.developer.uberriderjava.models;

public class TokenModel {
    private String token;

    public TokenModel(String token) {
        this.token = token;
    }

    public TokenModel() {
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
