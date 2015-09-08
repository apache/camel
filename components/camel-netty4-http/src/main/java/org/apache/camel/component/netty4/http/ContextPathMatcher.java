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
package org.apache.camel.component.netty4.http;

/**
 * A matcher used for selecting the correct {@link org.apache.camel.component.netty4.http.handlers.HttpServerChannelHandler}
 * to handle an incoming {@link io.netty.handler.codec.http.HttpRequest} when you use multiple routes on the same
 * port.
 * <p/>
 * As when we do that, we need to multiplex and select the correct consumer route to process the HTTP request.
 * To do that we need to match on the incoming HTTP request context-path from the request.
 */
public interface ContextPathMatcher {

    /**
     * Whether the target context-path matches a regular url.
     *
     * @param path  the context-path from the incoming HTTP request
     * @return <tt>true</tt> to match, <tt>false</tt> if not.
     */
    boolean matches(String path);

    /**
     * Whether the target context-path matches a REST url.
     *
     * @param path  the context-path from the incoming HTTP request
     * @param wildcard whether to match strict or by wildcards
     * @return <tt>true</tt> to match, <tt>false</tt> if not.
     */
    boolean matchesRest(String path, boolean wildcard);

    /**
     * Matches the given request HTTP method with the configured HTTP method of the consumer
     *
     * @param method    the request HTTP method
     * @param restrict  the consumer configured HTTP restrict method
     * @return <tt>true</tt> if matched, <tt>false</tt> otherwise
     */
    boolean matchMethod(String method, String restrict);

}
