package com.shllabs.recommender.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Structured output the planner LLM call must produce. The server never trusts
 * {@code reply} to contain assessment names/URLs for recommend/refine/compare turns —
 * those are always resolved against the real catalog by {@code ChatOrchestratorService}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Plan {

    /** one of: clarify | recommend | refine | compare | refuse */
    private String action;

    /** Natural-language reply. Used verbatim for clarify/refuse; ignored for recommend/refine/compare. */
    private String reply;

    /** Free-text query used to retrieve candidates from the catalog index. */
    private String searchQuery;

    /** Optional test-type filter, e.g. ["K","P"]. */
    private List<String> testTypes;

    private Integer maxDurationMinutes;

    private String jobLevel;

    private Boolean remoteOnly;

    private Integer topK;

    /** Names (as mentioned by the user) to resolve+compare against the catalog. */
    private List<String> compareNames;

    /** True when the planner believes this turn concludes the task. */
    private Boolean done;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public String getSearchQuery() { return searchQuery; }
    public void setSearchQuery(String searchQuery) { this.searchQuery = searchQuery; }

    public List<String> getTestTypes() { return testTypes; }
    public void setTestTypes(List<String> testTypes) { this.testTypes = testTypes; }

    public Integer getMaxDurationMinutes() { return maxDurationMinutes; }
    public void setMaxDurationMinutes(Integer maxDurationMinutes) { this.maxDurationMinutes = maxDurationMinutes; }

    public String getJobLevel() { return jobLevel; }
    public void setJobLevel(String jobLevel) { this.jobLevel = jobLevel; }

    public Boolean getRemoteOnly() { return remoteOnly; }
    public void setRemoteOnly(Boolean remoteOnly) { this.remoteOnly = remoteOnly; }

    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }

    public List<String> getCompareNames() { return compareNames; }
    public void setCompareNames(List<String> compareNames) { this.compareNames = compareNames; }

    public Boolean getDone() { return done; }
    public void setDone(Boolean done) { this.done = done; }
}
