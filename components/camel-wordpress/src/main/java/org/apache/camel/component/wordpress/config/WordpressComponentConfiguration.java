package org.apache.camel.component.wordpress.config;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.StringHelper;
import org.wordpress4j.WordpressConstants;

@UriParams
public class WordpressComponentConfiguration {

    @UriParam(description = "The Wordpress API URL from your site, e.g. http://myblog.com/wp-json/")
    @Metadata(required = "true")
    private String url;
    @UriParam(defaultValue = WordpressConstants.API_VERSION)
    private String apiVersion = WordpressConstants.API_VERSION;
    @UriParam(description = "The user used to authenticate with Basic Auth")
    private String user;
    @UriParam(description = "The password used to authenticate with Basic Auth")
    private String password;

    /**
     * Wordpress URL in {@link URI} format
     */
    private URI uri;

    public WordpressComponentConfiguration() {

    }

    public String getUrl() {
        return url;
    }

    public URI getUri() {
        return uri;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getUser() {
        return user;
    }
    
    public void setUser(String user) {
        this.user = user;
    }

    public void validate() {
        StringHelper.notEmpty(this.apiVersion, "apiVersion");
        StringHelper.notEmpty(this.url, "url");
        try {
            this.uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Impossible to set Wordpress API URL", e);
        }
    }

}
