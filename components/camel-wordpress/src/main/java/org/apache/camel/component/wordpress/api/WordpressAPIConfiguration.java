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
package org.apache.camel.component.wordpress.api;

import java.io.Serializable;
import java.util.Objects;

import org.apache.camel.component.wordpress.api.auth.WordpressAuthentication;

import static java.util.Objects.hash;

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
        return "WordpressAPIConfiguration{" + this.apiUrl + ", Version=" + this.apiVersion + ", " + this.authentication + "}";
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
