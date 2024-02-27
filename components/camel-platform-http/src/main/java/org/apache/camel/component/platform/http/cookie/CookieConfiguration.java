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
package org.apache.camel.component.platform.http.cookie;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Attributes that are set when creating Cookies.
 */
@UriParams
@Configurer(extended = true)
public class CookieConfiguration {

    public static final String DEFAULT_PATH = "/";
    public static final boolean DEFAULT_SECURE_FLAG = false;
    public static final boolean DEFAULT_HTTP_ONLY_FLAG = false;
    public static final CookieSameSite DEFAULT_SAME_SITE = CookieSameSite.LAX;
    @UriParam(defaultValue = "/")
    private String cookiePath = DEFAULT_PATH;
    @UriParam
    private String cookieDomain;
    @UriParam
    private Long cookieMaxAge;
    @UriParam(defaultValue = "false")
    private boolean cookieSecure = DEFAULT_SECURE_FLAG;
    @UriParam(defaultValue = "false")
    private boolean cookieHttpOnly = DEFAULT_HTTP_ONLY_FLAG;
    @UriParam(defaultValue = "Lax")
    private CookieSameSite cookieSameSite = DEFAULT_SAME_SITE;

    public CookieConfiguration() {
    }

    public CookieConfiguration(String cookiePath, String cookieDomain, Long cookieMaxAge,
                               boolean cookieSecure, boolean cookieHttpOnly, CookieSameSite cookieSameSite) {
        this.cookiePath = cookiePath;
        this.cookieDomain = cookieDomain;
        this.cookieMaxAge = cookieMaxAge;
        this.cookieSecure = cookieSecure;
        this.cookieHttpOnly = cookieHttpOnly;
        this.cookieSameSite = cookieSameSite;
    }

    /**
     * Sets the URL path that must exist in the requested URL in order to send the Cookie.
     */
    public void setCookiePath(String cookiePath) {
        this.cookiePath = cookiePath;
    }

    public String getCookiePath() {
        return cookiePath;
    }

    /**
     * Sets which server can receive cookies.
     */
    public void setCookieDomain(String cookieDomain) {
        this.cookieDomain = cookieDomain;
    }

    public String getCookieDomain() {
        return cookieDomain;
    }

    /**
     * Sets the maximum cookie age in seconds.
     */
    public void setCookieMaxAge(Long cookieMaxAge) {
        this.cookieMaxAge = cookieMaxAge;
    }

    public Long getCookieMaxAge() {
        return cookieMaxAge;
    }

    /**
     * Sets whether the cookie is only sent to the server with an encrypted request over HTTPS.
     */
    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    /**
     * Sets whether to prevent client side scripts from accessing created cookies.
     */
    public void setCookieHttpOnly(boolean cookieHttpOnly) {
        this.cookieHttpOnly = cookieHttpOnly;
    }

    public boolean isCookieHttpOnly() {
        return cookieHttpOnly;
    }

    /**
     * Sets whether to prevent the browser from sending cookies along with cross-site requests.
     */
    public void setCookieSameSite(CookieSameSite cookieSameSite) {
        this.cookieSameSite = cookieSameSite;
    }

    public CookieSameSite getCookieSameSite() {
        return cookieSameSite;
    }

    public static class Builder {
        private String path = DEFAULT_PATH;
        private String domain;
        private Long maxAge;
        private boolean secure = DEFAULT_SECURE_FLAG;
        private boolean httpOnly = DEFAULT_HTTP_ONLY_FLAG;
        private CookieSameSite sameSite = DEFAULT_SAME_SITE;

        public Builder() {

        }

        /**
         * Sets the URL path that must exist in the requested URL in order to send the Cookie.
         */
        public Builder setPath(String path) {
            this.path = path;
            return this;
        }

        /**
         * Sets which server can receive cookies.
         */
        public Builder setDomain(String domain) {
            this.domain = domain;
            return this;
        }

        /**
         * Sets the maximum cookie age in seconds.
         */
        public Builder setMaxAge(Long maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        /**
         * Sets whether the cookie is only sent to the server with an encrypted request over HTTPS.
         */
        public Builder setSecure(boolean secure) {
            this.secure = secure;
            return this;
        }

        /**
         * Sets whether to prevent client side scripts from accessing created cookies.
         */
        public Builder setHttpOnly(boolean httpOnly) {
            this.httpOnly = httpOnly;
            return this;
        }

        /**
         * Sets whether to prevent the browser from sending cookies along with cross-site requests.
         */
        public Builder setSameSite(CookieSameSite sameSite) {
            this.sameSite = sameSite;
            return this;
        }

        public CookieConfiguration build() {
            return new CookieConfiguration(path, domain, maxAge, secure, httpOnly, sameSite);
        }
    }

    /**
     * The Cookie {@code SameSite} policy that declares whether a Cookie should be sent with cross-site requests.
     */
    public enum CookieSameSite {

        /**
         * Prevents cookies from being sent to the target site in all cross-site browsing contexts.
         */
        STRICT("Strict"),

        /**
         * Cookies are sent in a first-party context, also when following a link to the origin site.
         */
        LAX("Lax"),

        /**
         * Cookies are set in all first-party and cross-origin contexts.
         */
        NONE("None");

        CookieSameSite(String value) {
            this.value = value;
        }

        private final String value;

        public String getValue() {
            return value;
        }
    }
}
