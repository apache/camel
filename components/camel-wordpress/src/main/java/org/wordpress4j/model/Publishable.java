package org.wordpress4j.model;

import java.io.Serializable;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Describes a object that has base properties for a {@link TextPublishable} object.
 */
@JacksonXmlRootElement(localName = "publishable")
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class Publishable implements Serializable {

    private static final long serialVersionUID = 5695150309094986591L;

    private Integer id;

    private Integer author;

    private Date date;

    @JsonProperty("date_gmt")
    private Date dateGmt;

    private Date modified;

    @JsonProperty("modified_gmt")
    private Date modifiedGmt;

    private String slug;

    public Publishable() {

    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Date getDateGmt() {
        return dateGmt;
    }

    public void setDateGmt(Date dateGmt) {
        this.dateGmt = dateGmt;
    }

    public Date getModified() {
        return modified;
    }

    public void setModified(Date modified) {
        this.modified = modified;
    }

    public Date getModifiedGmt() {
        return modifiedGmt;
    }

    public void setModifiedGmt(Date modifiedGmt) {
        this.modifiedGmt = modifiedGmt;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public Integer getAuthor() {
        return author;
    }

    public void setAuthor(Integer author) {
        this.author = author;
    }

    @Override
    public String toString() {
        return this.slug;
    }

}
