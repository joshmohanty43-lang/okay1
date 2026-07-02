package com.shllabs.recommender.model;

import java.util.List;

/**
 * Normalized, immutable representation of a single SHL Individual Test Solution.
 */
public final class Assessment {

    private final String id;
    private final String name;
    private final String url;
    private final List<String> jobLevels;
    private final List<String> languages;
    private final Integer durationMinutes; // null when unknown/variable/untimed
    private final boolean remote;
    private final boolean adaptive;
    private final String description;
    private final List<String> testTypeCodes; // e.g. ["K","P"]
    private final String searchText; // pre-lowercased blob used for indexing

    public Assessment(String id, String name, String url, List<String> jobLevels, List<String> languages,
                       Integer durationMinutes, boolean remote, boolean adaptive, String description,
                       List<String> testTypeCodes, String searchText) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.jobLevels = jobLevels;
        this.languages = languages;
        this.durationMinutes = durationMinutes;
        this.remote = remote;
        this.adaptive = adaptive;
        this.description = description;
        this.testTypeCodes = testTypeCodes;
        this.searchText = searchText;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public List<String> getJobLevels() { return jobLevels; }
    public List<String> getLanguages() { return languages; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public boolean isRemote() { return remote; }
    public boolean isAdaptive() { return adaptive; }
    public String getDescription() { return description; }
    public List<String> getTestTypeCodes() { return testTypeCodes; }
    public String getSearchText() { return searchText; }
}
