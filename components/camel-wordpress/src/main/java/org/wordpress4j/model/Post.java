package org.wordpress4j.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Represents a Wordpress Post.
 */
@JacksonXmlRootElement(localName = "post")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Post extends TextPublishable implements Serializable {

    private static final long serialVersionUID = -2077181715632668792L;

    private String password;

    private Format format;

    private Boolean stick;

    private List<Integer> categories;

    private List<Integer> tags;

    @JsonProperty("liveblog_likes")
    private Integer liveblogLikes;

    public Post() {
        this.categories = new ArrayList<>();
        this.tags = new ArrayList<>();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Format getFormat() {
        return format;
    }

    public void setFormat(Format format) {
        this.format = format;
    }

    public Boolean isStick() {
        return stick;
    }

    public void setStick(Boolean stick) {
        this.stick = stick;
    }

    public List<Integer> getCategories() {
        return categories;
    }

    public void setCategories(List<Integer> categories) {
        this.categories = categories;
    }

    public List<Integer> getTags() {
        return tags;
    }

    public void setTags(List<Integer> tags) {
        this.tags = tags;
    }

    public Integer getLiveblogLikes() {
        return liveblogLikes;
    }

    public void setLiveblogLikes(Integer liveblogLikes) {
        this.liveblogLikes = liveblogLikes;
    }

}
