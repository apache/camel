package org.wordpress4j;

import static com.google.common.base.MoreObjects.toStringHelper;
import static java.util.Objects.hash;

import java.io.Serializable;
import java.util.Objects;

import org.wordpress4j.auth.WordpressAuthentication;

/**
 * Model for the API configuration.
 */
public final class WordpressAPIConfiguration implements Serializable {

    private static final long serialVersionUID = 3512991364074374129L;
    private String apiUrl;
    private String apiVersion;
    private WordpressAuthentication authentication;

    public WordpressAPIConfiguration() {

    }

    public WordpressAPIConfiguration(final String apiUrl, final String apiVersion) {
        this.apiUrl = apiUrl;
        this.apiVersion = apiVersion;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public WordpressAuthentication getAuthentication() {
        return authentication;
    }

    public void setAuthentication(WordpressAuthentication authentication) {
        this.authentication = authentication;
    }

    @Override
    public String toString() {
        return toStringHelper(this).addValue(this.apiUrl).add("Version", this.apiVersion).addValue(this.authentication).toString();
    }

    @Override
    public int hashCode() {
        return hash(this.apiUrl, this.apiVersion, this.authentication);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!WordpressAPIConfiguration.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        return Objects.equals(this, obj);
    }

}
