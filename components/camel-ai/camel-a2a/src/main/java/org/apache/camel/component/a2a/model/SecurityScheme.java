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
package org.apache.camel.component.a2a.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A2A v1.0 security scheme using oneof wrappers. Exactly one of the four scheme fields must be set.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityScheme {
    private ApiKeySecurityScheme apiKeySecurityScheme;
    private HttpAuthSecurityScheme httpAuthSecurityScheme;
    private OpenIdConnectSecurityScheme openIdConnectSecurityScheme;
    private OAuth2SecurityScheme oauth2SecurityScheme;
    private MutualTlsSecurityScheme mtlsSecurityScheme;
    private String description;

    public SecurityScheme() {
    }

    @JsonIgnore
    public String getType() {
        validate();
        if (apiKeySecurityScheme != null) {
            return "apiKey";
        }
        if (httpAuthSecurityScheme != null) {
            return "http";
        }
        if (openIdConnectSecurityScheme != null) {
            return "openIdConnect";
        }
        if (oauth2SecurityScheme != null) {
            return "oauth2";
        }
        if (mtlsSecurityScheme != null) {
            return "mtls";
        }
        return null;
    }

    @JsonIgnore
    public void validate() {
        if (oneOfCount() != 1) {
            throw new IllegalArgumentException(
                    "SecurityScheme must contain exactly one scheme definition");
        }
    }

    @JsonIgnore
    public String getName() {
        return apiKeySecurityScheme != null ? apiKeySecurityScheme.name : null;
    }

    @JsonIgnore
    public String getIn() {
        return getLocation();
    }

    @JsonIgnore
    public String getLocation() {
        return apiKeySecurityScheme != null ? apiKeySecurityScheme.location : null;
    }

    @JsonIgnore
    public String getScheme() {
        return httpAuthSecurityScheme != null ? httpAuthSecurityScheme.scheme : null;
    }

    @JsonIgnore
    public String getBearerFormat() {
        return httpAuthSecurityScheme != null ? httpAuthSecurityScheme.bearerFormat : null;
    }

    @JsonIgnore
    public String getOpenIdConnectUrl() {
        return openIdConnectSecurityScheme != null ? openIdConnectSecurityScheme.openIdConnectUrl : null;
    }

    @JsonIgnore
    public Map<String, Object> getFlows() {
        return oauth2SecurityScheme != null ? oauth2SecurityScheme.flows : null;
    }

    // --- Oneof field accessors ---

    public ApiKeySecurityScheme getApiKeySecurityScheme() {
        return apiKeySecurityScheme;
    }

    public void setApiKeySecurityScheme(ApiKeySecurityScheme apiKeySecurityScheme) {
        assertCanSet(apiKeySecurityScheme);
        this.apiKeySecurityScheme = apiKeySecurityScheme;
    }

    public HttpAuthSecurityScheme getHttpAuthSecurityScheme() {
        return httpAuthSecurityScheme;
    }

    public void setHttpAuthSecurityScheme(HttpAuthSecurityScheme httpAuthSecurityScheme) {
        assertCanSet(httpAuthSecurityScheme);
        this.httpAuthSecurityScheme = httpAuthSecurityScheme;
    }

    public OpenIdConnectSecurityScheme getOpenIdConnectSecurityScheme() {
        return openIdConnectSecurityScheme;
    }

    public void setOpenIdConnectSecurityScheme(OpenIdConnectSecurityScheme openIdConnectSecurityScheme) {
        assertCanSet(openIdConnectSecurityScheme);
        this.openIdConnectSecurityScheme = openIdConnectSecurityScheme;
    }

    public OAuth2SecurityScheme getOauth2SecurityScheme() {
        return oauth2SecurityScheme;
    }

    public void setOauth2SecurityScheme(OAuth2SecurityScheme oauth2SecurityScheme) {
        assertCanSet(oauth2SecurityScheme);
        this.oauth2SecurityScheme = oauth2SecurityScheme;
    }

    public MutualTlsSecurityScheme getMtlsSecurityScheme() {
        return mtlsSecurityScheme;
    }

    public void setMtlsSecurityScheme(MutualTlsSecurityScheme mtlsSecurityScheme) {
        assertCanSet(mtlsSecurityScheme);
        this.mtlsSecurityScheme = mtlsSecurityScheme;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // --- Static factories ---

    public static SecurityScheme apiKey(String in, String name) {
        SecurityScheme s = new SecurityScheme();
        s.apiKeySecurityScheme = new ApiKeySecurityScheme(in, name);
        return s;
    }

    public static SecurityScheme httpBearer() {
        SecurityScheme s = new SecurityScheme();
        s.httpAuthSecurityScheme = new HttpAuthSecurityScheme("bearer", null);
        return s;
    }

    public static SecurityScheme openIdConnect(String url) {
        SecurityScheme s = new SecurityScheme();
        s.openIdConnectSecurityScheme = new OpenIdConnectSecurityScheme(url);
        return s;
    }

    public static SecurityScheme oauth2(Map<String, Object> flows) {
        SecurityScheme s = new SecurityScheme();
        s.oauth2SecurityScheme = new OAuth2SecurityScheme(flows);
        return s;
    }

    public static SecurityScheme mutualTls() {
        SecurityScheme s = new SecurityScheme();
        s.mtlsSecurityScheme = new MutualTlsSecurityScheme();
        return s;
    }

    private void assertCanSet(Object value) {
        if (value != null && oneOfCount() > 0) {
            throw new IllegalArgumentException(
                    "SecurityScheme must contain exactly one scheme definition");
        }
    }

    private int oneOfCount() {
        int count = 0;
        count += apiKeySecurityScheme != null ? 1 : 0;
        count += httpAuthSecurityScheme != null ? 1 : 0;
        count += openIdConnectSecurityScheme != null ? 1 : 0;
        count += oauth2SecurityScheme != null ? 1 : 0;
        count += mtlsSecurityScheme != null ? 1 : 0;
        return count;
    }

    // --- Nested scheme types ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApiKeySecurityScheme {
        @JsonAlias("in")
        private String location;
        private String name;

        public ApiKeySecurityScheme() {
        }

        public ApiKeySecurityScheme(String location, String name) {
            this.location = location;
            this.name = name;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        @JsonIgnore
        public String getIn() {
            return location;
        }

        @JsonIgnore
        public void setIn(String in) {
            this.location = in;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HttpAuthSecurityScheme {
        private String scheme;
        private String bearerFormat;

        public HttpAuthSecurityScheme() {
        }

        public HttpAuthSecurityScheme(String scheme, String bearerFormat) {
            this.scheme = scheme;
            this.bearerFormat = bearerFormat;
        }

        public String getScheme() {
            return scheme;
        }

        public void setScheme(String scheme) {
            this.scheme = scheme;
        }

        public String getBearerFormat() {
            return bearerFormat;
        }

        public void setBearerFormat(String bearerFormat) {
            this.bearerFormat = bearerFormat;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OpenIdConnectSecurityScheme {
        private String openIdConnectUrl;

        public OpenIdConnectSecurityScheme() {
        }

        public OpenIdConnectSecurityScheme(String openIdConnectUrl) {
            this.openIdConnectUrl = openIdConnectUrl;
        }

        public String getOpenIdConnectUrl() {
            return openIdConnectUrl;
        }

        public void setOpenIdConnectUrl(String openIdConnectUrl) {
            this.openIdConnectUrl = openIdConnectUrl;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OAuth2SecurityScheme {
        private Map<String, Object> flows;
        private String oauth2MetadataUrl;

        public OAuth2SecurityScheme() {
        }

        public OAuth2SecurityScheme(Map<String, Object> flows) {
            this.flows = flows;
        }

        public Map<String, Object> getFlows() {
            return flows;
        }

        public void setFlows(Map<String, Object> flows) {
            this.flows = flows;
        }

        public String getOauth2MetadataUrl() {
            return oauth2MetadataUrl;
        }

        public void setOauth2MetadataUrl(String oauth2MetadataUrl) {
            this.oauth2MetadataUrl = oauth2MetadataUrl;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MutualTlsSecurityScheme {
    }
}
