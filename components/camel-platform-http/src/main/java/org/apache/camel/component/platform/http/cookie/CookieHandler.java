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
 * A cookie handler that allows Platform consumers to add, retrieve, and expire cookies.
 */
public interface CookieHandler {

    /**
     * Adds a cookie that will be sent back in the response.
     *
     * @param cookieName  the cookie name
     * @param cookieValue the cookie value
     */
    void addCookie(String cookieName, String cookieValue);

    /**
     * Expires a cookie notifying the user-agent or producer to remove it from their cookie-store.
     *
     * @param  cookieName the cookie name
     * @return            the value of the cookie to expire if one exists, otherwise {@code null}
     */
    String removeCookie(String cookieName);

    /**
     * Accesses the cookie value.
     *
     * @param  cookieName the cookie name
     * @return            the value of the cookie if one exists, otherwise {@code null}
     */
    String getCookieValue(String cookieName);
}
