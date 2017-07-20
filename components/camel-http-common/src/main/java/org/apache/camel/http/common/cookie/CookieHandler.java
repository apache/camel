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
package org.apache.camel.http.common.cookie;

import java.io.IOException;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.URI;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;

/**
 * The interface for cookie handling will allow components to handle cookies for
 * HTTP requests.
 * <p>
 * Note: The defined cookie policies apply. The default is
 * CookiePolicy.ACCEPT_ORIGINAL_SERVER, so cookies will only be handled for
 * fully qualified host names in the URI (not local host names like "myhost" or
 * "localhost").
 */
public interface CookieHandler {

    /**
     * Store cookies for a HTTP response in the cookie handler
     * 
     * @param exchange the exchange
     * @param uri the URI of the called HTTP service
     * @param headerMap a map containing the HTTP headers returned by the server
     * @throws IOException if the cookies cannot be stored
     */
    void storeCookies(Exchange exchange, URI uri, Map<String, List<String>> headerMap) throws IOException;

    /**
     * Create cookie headers from the stored cookies appropriate for a given
     * URI.
     * 
     * @param exchange the exchange
     * @param uri the URI of the called HTTP service
     * @return a map containing the cookie headers that can be set to the HTTP
     *         request. Only cookies that are supposed to be sent to the URI in
     *         question are considered.
     * @throws IOException if the cookies cannot be loaded
     */
    Map<String, List<String>> loadCookies(Exchange exchange, URI uri) throws IOException;

    /**
     * Get the CookieStore. This method can be used if the is using a CookieHandler by itself.
     *
     * @param exchange the exchange
     * @return the CookieStore
     */
    CookieStore getCookieStore(Exchange exchange);

    /**
     * Define a CookiePolicy for cookies stored by this CookieHandler
     * 
     * @param cookiePolicy the CookiePolicy
     */
    void setCookiePolicy(CookiePolicy cookiePolicy);
}
