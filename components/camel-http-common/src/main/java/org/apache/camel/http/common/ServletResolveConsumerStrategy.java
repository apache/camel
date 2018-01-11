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
package org.apache.camel.http.common;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;

/**
 * Strategy to resolve which consumer to service an incoming {@link javax.servlet.http.HttpServletRequest}.
 */
public interface ServletResolveConsumerStrategy {

    /**
     * Resolve the consumer to use.
     *
     * @param request   the http request
     * @param consumers the map of registered consumers
     * @return the consumer to service the request, or <tt>null</tt> if no match,
     * which sends back a {@link javax.servlet.http.HttpServletResponse#SC_NOT_FOUND} to the client.
     */
    HttpConsumer resolve(HttpServletRequest request, Map<String, HttpConsumer> consumers);

    /**
     * Checks if the http request method (GET, POST, etc) would be allow among the registered consumers.

     * @param request   the http request
     * @param method    the http method
     * @param consumers the map of registered consumers
     * @return the consumer to service the request, or <tt>null</tt> if no match,
     * which sends back a {@link javax.servlet.http.HttpServletResponse#SC_METHOD_NOT_ALLOWED} to the client.
     */
    boolean isHttpMethodAllowed(HttpServletRequest request, String method, Map<String, HttpConsumer> consumers);

}
