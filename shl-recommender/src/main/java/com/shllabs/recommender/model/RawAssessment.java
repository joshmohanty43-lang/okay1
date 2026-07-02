package com.shllabs.recommender.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Mirrors the raw scraped JSON shape (snake_case fields from the SHL product catalog scrape).
 * Kept deliberately permissive: unknown fields are ignored so the loader survives schema drift.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RawAssessment {

    @JsonProperty("entity_id")
    private String entityId;

    private String name;

    private String link;

    @JsonProperty("job_levels")
    private List<String> jobLevels;

    private List<String> languages;

    @JsonProperty("duration_raw")
    private String durationRaw;

    private String duration;

    private String remote;

    private String adaptive;

    private String description;

    private List<String> keys;

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public List<String> getJobLevels() { return jobLevels; }
    public void setJobLevels(List<String> jobLevels) { this.jobLevels = jobLevels; }

    public List<String> getLanguages() { return languages; }
    public void setLanguages(List<String> languages) { this.languages = languages; }

    public String getDurationRaw() { return durationRaw; }
    public void setDurationRaw(String durationRaw) { this.durationRaw = durationRaw; }

    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }

    public String getRemote() { return remote; }
    public void setRemote(String remote) { this.remote = remote; }

    public String getAdaptive() { return adaptive; }
    public void setAdaptive(String adaptive) { this.adaptive = adaptive; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getKeys() { return keys; }
    public void setKeys(List<String> keys) { this.keys = keys; }
}
