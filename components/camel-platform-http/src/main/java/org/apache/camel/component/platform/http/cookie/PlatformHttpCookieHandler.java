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

import org.apache.camel.Exchange;
import org.apache.camel.component.platform.http.PlatformHttpConstants;


/**
 * Allows components to add, retrieve, and expire cookies.
 *
 * @param <T> the Cookie type
 */
public interface PlatformHttpCookieHandler<T> {

    /**
     * Gets a {@code PlatformHttpCookieHandler} instance from an {@code exchange} header.
     */
    static <T extends PlatformHttpCookieHandler> T getCookieHandler(Exchange exchange) {
        return (T) exchange.getMessage().getHeader(
                PlatformHttpConstants.COOKIE_HANDLER, PlatformHttpCookieHandler.class);
    }

    /**
     * Add a cookie that will be sent back in the response.
     *
     * @param name  the cookie name
     * @param value the cookie value
     */
    void addCookie(String name, String value);

    /**
     * Expire a cookie.
     *
     * @param  name the cookie name
     * @return      the cookie if it existed otherwise {@code null}
     */
    T removeCookie(String name);

    /**
     * Get the cookie with the specified name.
     *
     * @param  name the cookie name
     * @return      the cookie if it exists otherwise {@code null}
     */
    T getCookie(String name);
}
