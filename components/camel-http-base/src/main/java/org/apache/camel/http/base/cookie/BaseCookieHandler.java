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
package org.apache.camel.http.base.cookie;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookieStore;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;

/**
 * A basic implementation of a CamelCookie handler based on the JDK
 * CookieManager.
 */
public abstract class BaseCookieHandler implements CookieHandler {

    @Override
    public void storeCookies(Exchange exchange, URI uri, Map<String, List<String>> headerMap) throws IOException {
        getCookieManager(exchange).put(uri, headerMap);
    }

    @Override
    public Map<String, List<String>> loadCookies(Exchange exchange, URI uri) throws IOException {
        // the map is not used, so we do not need to fetch the headers from the
        // exchange
        return getCookieManager(exchange).get(uri, Collections.emptyMap());
    }

    @Override
    public CookieStore getCookieStore(Exchange exchange) {
        return getCookieManager(exchange).getCookieStore();
    }

    protected abstract CookieManager getCookieManager(Exchange exchange);
}
