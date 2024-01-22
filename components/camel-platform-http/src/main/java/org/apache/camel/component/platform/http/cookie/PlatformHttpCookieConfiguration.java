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

/**
 * Cookie attributes.
 */
public class PlatformHttpCookieConfiguration {

    public static final long DEFAULT_MAX_AGE = 60L * 60 * 24; // 1 day
    public static final String DEFAULT_PATH = "/";
    public static final boolean DEFAULT_SECURE_FLAG = false;
    public static final boolean DEFAULT_HTTP_ONLY_FLAG = false;
    public static final CookieSameSite DEFAULT_SAME_SITE = CookieSameSite.LAX;

    private String path = DEFAULT_PATH;
    private String domain;
    private long maxAge = DEFAULT_MAX_AGE;
    private boolean secure = DEFAULT_SECURE_FLAG;
    private boolean httpOnly = DEFAULT_HTTP_ONLY_FLAG;
    private CookieSameSite sameSite = DEFAULT_SAME_SITE;

    public PlatformHttpCookieConfiguration() {
    }

    public PlatformHttpCookieConfiguration(String path, String domain, long maxAge,
                                           boolean secure, boolean httpOnly, CookieSameSite sameSite) {
        this.path = path;
        this.domain = domain;
        this.maxAge = maxAge;
        this.secure = secure;
        this.httpOnly = httpOnly;
        this.sameSite = sameSite;
    }

    public String getPath() {
        return path;
    }

    public String getDomain() {
        return domain;
    }

    public long getMaxAge() {
        return maxAge;
    }

    public boolean isSecure() {
        return secure;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public CookieSameSite getSameSite() {
        return sameSite;
    }

    public static class Builder {
        private String path = DEFAULT_PATH;
        private String domain;
        private long maxAge = DEFAULT_MAX_AGE;
        private boolean secure = DEFAULT_SECURE_FLAG;
        private boolean httpOnly = DEFAULT_HTTP_ONLY_FLAG;
        private CookieSameSite sameSite = DEFAULT_SAME_SITE;

        public Builder() {

        }

        /**
         * Set the URL path that must exist in the requested URL in order to send the Cookie.
         */
        public Builder setPath(String path) {
            this.path = path;
            return this;
        }

        /**
         * Set which server can receive cookies.
         */
        public Builder setDomain(String domain) {
            this.domain = domain;
            return this;
        }

        /**
         * Set the maximum cookie age in seconds.
         */
        public Builder setMaxAge(long maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        /**
         * Set whether to prevent client side scripts from accessing created cookies.
         */
        public Builder setSecure(boolean secure) {
            this.secure = secure;
            return this;
        }

        /**
         * Set whether to prevent client side scripts from accessing created cookies.
         */
        public Builder setHttpOnly(boolean httpOnly) {
            this.httpOnly = httpOnly;
            return this;
        }

        /**
         * Set whether to prevent the browser from sending cookies along with cross-site requests.
         */
        public Builder setSameSite(CookieSameSite sameSite) {
            this.sameSite = sameSite;
            return this;
        }

        public PlatformHttpCookieConfiguration build() {
            return new PlatformHttpCookieConfiguration(path, domain, maxAge, secure, httpOnly, sameSite);
        }
    }
}
