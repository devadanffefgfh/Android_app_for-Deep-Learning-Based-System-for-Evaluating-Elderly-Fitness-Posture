package com.edu.wzu.app;

public class RecommendRequest {
    private String body_part;

    public RecommendRequest(String body_part) {
        this.body_part = body_part;
    }

    public String getBody_part() {
        return body_part;
    }

    public void setBody_part(String body_part) {
        this.body_part = body_part;
    }
}
