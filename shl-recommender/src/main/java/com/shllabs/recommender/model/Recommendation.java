package com.shllabs.recommender.model;

public class Recommendation {

    private String name;
    private String url;
    private String testType;

    public Recommendation() { }

    public Recommendation(String name, String url, String testType) {
        this.name = name;
        this.url = url;
        this.testType = testType;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTestType() { return testType; }
    public void setTestType(String testType) { this.testType = testType; }
}
