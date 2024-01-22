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
import org.apache.camel.component.platform.http.cookie.PlatformHttpCookieHandler;


/**
 * Class for adding, retrieving, and expiring Vertx cookies.
 */
public class VertxCookieHandler implements PlatformHttpCookieHandler<Cookie> {

    private PlatformHttpCookieHandler<Cookie> delegate;

    public VertxCookieHandler(PlatformHttpCookieHandler<Cookie> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void addCookie(String name, String value) {
        delegate.addCookie(name, value);
    }

    @Override
    public Cookie removeCookie(String name) {
        return delegate.removeCookie(name);
    }

    @Override
    public Cookie getCookie(String name) {
        return delegate.getCookie(name);
    }
}
