/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.wordpress;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.camel.component.wordpress.api.WordpressConstants;
import org.apache.camel.component.wordpress.api.model.SearchCriteria;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.StringHelper;

@UriParams
public class WordpressComponentConfiguration {

    private URI uri;

    @UriParam(description = "The Wordpress API URL from your site, e.g. http://myblog.com/wp-json/")
    @Metadata(required = true)
    private String url;
    @UriParam(description = "The Wordpress REST API version", defaultValue = WordpressConstants.API_VERSION)
    private String apiVersion = WordpressConstants.API_VERSION;
    @UriParam(description = "Authorized user to perform writing operations")
    private String user;
    @UriParam(description = "Password from authorized user")
    private String password;
    @UriParam(description = "The entity ID. Should be passed when the operation performed requires a specific entity, e.g. deleting a post")
    private Integer id;
    @UriParam(description = "The criteria to use with complex searches.", prefix = "criteria.", multiValue = true)
    private Map<String, Object> criteria;
    @UriParam(description = "Whether to bypass trash and force deletion.")
    private boolean force;
    @UriParam(description = "Search criteria")
    private SearchCriteria searchCriteria;

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

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Map<String, Object> getCriteria() {
        return criteria;
    }

    public void setCriteria(Map<String, Object> criteria) {
        this.criteria = criteria;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public SearchCriteria getSearchCriteria() {
        return searchCriteria;
    }

    public void setSearchCriteria(SearchCriteria searchCriteria) {
        this.searchCriteria = searchCriteria;
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
