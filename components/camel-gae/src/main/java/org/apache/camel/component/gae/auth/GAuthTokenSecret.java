/**
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
package org.apache.camel.component.gae.auth;

/**
 * A request token secret container with marshalling/unmarshalling methods to
 * and from a cookie.
 * 
 * @see GAuthAuthorizeBinding
 * @see GAuthUpgradeBinding
 */
public class GAuthTokenSecret {

    /**
     * Name of the request token secret cookie.
     */
    public static final String COOKIE_NAME = "gauth-token-secret";

    private String value;

    /**
     * Creates a new {@link GAuthTokenSecret}
     * 
     * @param value
     *            request token secret.
     */
    public GAuthTokenSecret(String value) {
        this.value = value;
    }

    /**
     * Returns the request token secret.
     */
    public String getValue() {
        return value;
    }

    /**
     * Creates a cookie from this {@link GAuthTokenSecret}.
     */
    public String toCookie() {
        return COOKIE_NAME + "=" + value;
    }

    /**
     * Create a {@link GAuthTokenSecret} from a cookies string.
     * 
     * @param cookies cookies string.
     * @return either an {@link GAuthTokenSecret} instance or <code>null</code>
     *         if there's no cookie with name {@link #COOKIE_NAME}.
     */
    public static GAuthTokenSecret fromCookie(String cookies) {
        if (cookies == null) {
            return null;
        }
        for (String cookie : cookies.split(";")) {
            String[] pair = cookie.split("=");
            if (pair[0].trim().equals(COOKIE_NAME)) {
                return new GAuthTokenSecret(pair[1].trim());
            }
        }
        return null;
    }
    
}

