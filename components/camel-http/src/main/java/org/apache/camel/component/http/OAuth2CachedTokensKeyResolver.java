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
package org.apache.camel.component.http;

import java.net.URI;

/**
 * Computes how cached OAuth2 tokens are shared between requests, when the http endpoint caches tokens
 * ({@code oauth2CacheTokens=true}).
 * <p>
 * The key returned for a request decides which cached token it can reuse. The client id, client secret, token endpoint,
 * scope and resource indicator are always added to the key by Camel, so a resolver only decides how much of the request
 * URI it takes into account. The token request does not include the request URI, so a key that leaves out parts of the
 * URI only means fewer token requests.
 * <p>
 * {@link OAuth2CachedTokensKey} has the built-in strategies. Use the endpoint option
 * {@code oauth2CachedTokensKeyResolver} to plug in a custom one.
 */
@FunctionalInterface
public interface OAuth2CachedTokensKeyResolver {

    /**
     * Computes the cache key for a request.
     *
     * @param  requestUri the URI of the HTTP request that needs a token
     * @return            the key (requests with equal keys share a cached token), or <tt>null</tt> to not cache the
     *                    token for this request
     */
    String resolveKey(URI requestUri);
}
