package org.wordpress4j.model;

import static com.google.common.base.MoreObjects.toStringHelper;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PostRevision extends Publishable implements Serializable {

    private static final long serialVersionUID = 4138540913280269413L;

    private Integer parent;

    private String guid;

    private String title;

    private String content;

    private String excerpt;

    public PostRevision() {

    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public Integer getParent() {
        return parent;
    }

    public void setParent(Integer parent) {
        this.parent = parent;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(String excerpt) {
        this.excerpt = excerpt;
    }

    @Override
    public String toString() {
        return toStringHelper(this).add("ID", this.getId()).add("PostID", this.parent).addValue(this.getTitle()).toString();
    }

}
