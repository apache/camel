package org.wordpress4j.model;

import static com.google.common.base.MoreObjects.toStringHelper;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Describes a object that may be published on the Wordpress engine, eg. a Post,
 * a Page etc.
 */
@JacksonXmlRootElement(localName = "textPublishable")
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class TextPublishable extends Publishable {

    private static final long serialVersionUID = -2913318702739560478L;

    private Content guid;

    private String link;

    private PublishableStatus status;

    private String type;

    private Content title;

    private Content content;

    private Content excerpt;

    private String template;

    private List<Content> meta;

    @JsonProperty("comment_status")
    private PostCommentStatus commentStatus;

    @JsonProperty("ping_status")
    private PingStatus pingStatus;

    @JsonProperty("featured_media")
    private Integer featuredMedia;

    public TextPublishable() {

    }

    public Content getTitle() {
        return title;
    }

    public void setTitle(Content title) {
        this.title = title;
    }

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        this.content = content;
    }

    public Content getExcerpt() {
        return excerpt;
    }

    public void setExcerpt(Content excerpt) {
        this.excerpt = excerpt;
    }

    public Content getGuid() {
        return guid;
    }

    public void setGuid(Content guid) {
        this.guid = guid;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public PublishableStatus getStatus() {
        return status;
    }

    public void setStatus(PublishableStatus status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getFeaturedMedia() {
        return featuredMedia;
    }

    public void setFeaturedMedia(Integer featuredMedia) {
        this.featuredMedia = featuredMedia;
    }

    public PostCommentStatus getCommentStatus() {
        return commentStatus;
    }

    public void setCommentStatus(PostCommentStatus commentStatus) {
        this.commentStatus = commentStatus;
    }

    public PingStatus getPingStatus() {
        return pingStatus;
    }

    public void setPingStatus(PingStatus pingStatus) {
        this.pingStatus = pingStatus;
    }

    public List<Content> getMeta() {
        return meta;
    }

    public void setMeta(List<Content> meta) {
        this.meta = meta;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }
    
    //@formatter:off
    @Override
    public String toString() {
        return toStringHelper(this)
            .add("ID", this.getId())
            .add("Status", this.getStatus())
            .addValue(this.guid)
            .addValue(this.getTitle()).toString();
    }
    //@formatter:on
}

