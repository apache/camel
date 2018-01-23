package org.wordpress4j.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Taxonomy implements Serializable {

    private static final long serialVersionUID = 390452251497218257L;
    private String name;
    private String slug;
    private String description;
    private boolean hierarchical;
    @JsonProperty("rest_base")
    private String restBase;
    @JsonProperty("show_cloud")
    private boolean showCloud;
    private List<Map<String, String>> capabilities;
    private List<String> labels;
    private List<String> types;
    
    public Taxonomy() {
        this.capabilities = new ArrayList<>();
        this.labels = new ArrayList<>();
        this.types = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isHierarchical() {
        return hierarchical;
    }

    public void setHierarchical(boolean hierarchical) {
        this.hierarchical = hierarchical;
    }

    public String getRestBase() {
        return restBase;
    }

    public void setRestBase(String restBase) {
        this.restBase = restBase;
    }

    public boolean isShowCloud() {
        return showCloud;
    }

    public void setShowCloud(boolean showCloud) {
        this.showCloud = showCloud;
    }

    public List<Map<String, String>> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<Map<String, String>> capabilities) {
        this.capabilities = capabilities;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public List<String> getTypes() {
        return types;
    }

    public void setTypes(List<String> types) {
        this.types = types;
    }

}
