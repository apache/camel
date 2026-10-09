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
import java.util.Locale;

/**
 * The built-in strategies for the cache key of OAuth2 tokens, see {@link OAuth2CachedTokensKeyResolver}.
 */
public enum OAuth2CachedTokensKey implements OAuth2CachedTokensKeyResolver {

    /**
     * One token per scheme, host and port (the default).
     */
    HOST_ONLY {
        @Override
        public String resolveKey(URI requestUri) {
            return authority(requestUri);
        }
    },
    /**
     * One token per scheme, host, port and path. The query is not taken into account.
     */
    HOST_AND_PATH {
        @Override
        public String resolveKey(URI requestUri) {
            String path = requestUri.getRawPath();
            return authority(requestUri) + (path != null ? path : "");
        }
    },
    /**
     * One token per request URI, including the query (the default before Camel 4.23).
     */
    FULL_URI {
        @Override
        public String resolveKey(URI requestUri) {
            return requestUri.toString();
        }
    };

    private static String authority(URI uri) {
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
        return scheme + "://" + host + ":" + OAuth2ClientConfigurer.effectivePort(uri);
    }
}
