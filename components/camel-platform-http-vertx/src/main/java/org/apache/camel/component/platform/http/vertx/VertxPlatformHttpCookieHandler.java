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
package org.apache.camel.component.platform.http.vertx;

import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.ext.web.RoutingContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.platform.http.PlatformHttpCookieHandler;

/**
 * Cookie Handler implementation allowing the platform-http-vertx component to add, retrieve,
 * and expire cookies.
 */
public class VertxPlatformHttpCookieHandler implements PlatformHttpCookieHandler<Cookie> {

    public static final long DEFAULT_MAX_AGE = 60L * 60 * 24; // 1 day
    public static final String DEFAULT_PATH = "/";
    public static final boolean DEFAULT_SECURE_FLAG = false;
    public static final boolean DEFAULT_HTTP_ONLY_FLAG = false;
    public static final CookieSameSite DEFAULT_SAME_SITE = CookieSameSite.STRICT;

    private String path = DEFAULT_PATH;
    private String domain;
    private long maxAge = DEFAULT_MAX_AGE;
    private boolean secure = DEFAULT_SECURE_FLAG;
    private boolean httpOnly = DEFAULT_HTTP_ONLY_FLAG;
    private CookieSameSite sameSite = DEFAULT_SAME_SITE;
    private RoutingContext routingContext;

    public VertxPlatformHttpCookieHandler() {
    }

    private VertxPlatformHttpCookieHandler(RoutingContext routingContext) {
        this.routingContext = routingContext;
    }

    /* package */ static VertxPlatformHttpCookieHandler getCookieHandler(Exchange exchange) {
        return VertxPlatformHttpSupport.getCookieHandler(exchange);
    }

    /**
     * Set the URL path that must exist in the requested URL in order to send the Cookie header.
     */
    public VertxPlatformHttpCookieHandler setPath(String path) {
        if (path != null) {
            this.path = path;
        }
        return this;
    }

    /**
     * Set which server can receive cookies.
     */
    public VertxPlatformHttpCookieHandler setDomain(String domain) {
        this.domain = domain;
        return this;
    }

    /**
     * Set the maximum cookie age in seconds.
     */
    public VertxPlatformHttpCookieHandler setMaxAge(long maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    /**
     * Set whether cookies are only sent when the request is encrypted.
     */
    public VertxPlatformHttpCookieHandler setSecure(boolean secure) {
        this.secure = secure;
        return this;
    }

    /**
     * Set whether to prevent client side scripts from accessing created cookies.
     */
    public VertxPlatformHttpCookieHandler setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    /**
     * Set the 'SameSite' attribute that prevents the browser from sending cookies along with cross-site requests.
     */
    public VertxPlatformHttpCookieHandler setSameSite(CookieSameSite sameSite) {
        this.sameSite = sameSite;
        return this;
    }

    @Override
    public void addCookie(String name, String value) {
        Cookie cookie = Cookie.cookie(name, value)
                .setPath(path)
                .setDomain(domain)
                .setSecure(secure)
                .setHttpOnly(httpOnly)
                .setMaxAge(maxAge)
                .setSameSite(sameSite);
        routingContext.response().addCookie(cookie);
    }

    @Override
    public Cookie removeCookie(String name) {
        return routingContext.response().removeCookie(name);
    }

    @Override
    public Cookie getCookie(String name) {
        return routingContext.request().getCookie(name);
    }

    @Override
    public PlatformHttpCookieHandler<Cookie> getInstance(Exchange exchange) {
        return new VertxPlatformHttpCookieHandler(
                exchange.getMessage().getHeader(
                        VertxPlatformHttpConstants.ROUTING_CONTEXT, RoutingContext.class))
                .setPath(path)
                .setDomain(domain)
                .setSecure(secure)
                .setHttpOnly(httpOnly)
                .setMaxAge(maxAge)
                .setSameSite(sameSite);
    }
}
