package org.wordpress4j.model;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * The Wordpress rendered content
 */
@JacksonXmlRootElement(localName = "content")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Content implements Serializable {

    private static final long serialVersionUID = -6355688058047458932L;

    private String rendered;
    private String raw;

    @JsonProperty("protected")
    private Boolean protected_;

    public Content() {

    }
    
    public Content(String rendered) {
        this.rendered = rendered;
    }

    public String getRendered() {
        return rendered;
    }

    public void setRendered(String rendered) {
        this.rendered = rendered;
    }

    public Boolean getProtected_() {
        return protected_;
    }

    public void setProtected_(Boolean protected_) {
        this.protected_ = protected_;
    }
    
    public String getRaw() {
        return raw;
    }
    
    public void setRaw(String raw) {
        this.raw = raw;
    }

    @Override
    public String toString() {
        return this.rendered;
    }

}
