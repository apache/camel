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
package org.apache.camel.support;

import org.apache.camel.ValueHolder;
import org.apache.camel.spi.NormalizedEndpointUri;

/**
 * Implementation of {@link NormalizedEndpointUri}.
 *
 * Use the {@link #newNormalizedUri(String, boolean)} as factory method.
 */
public final class NormalizedUri extends ValueHolder<String> implements NormalizedEndpointUri {

    // must extend ValueHolder to let this class be used as key for Camels endpoint registry

    // The raw (un-normalized) URI, preserved so that components with useRawUri()=true
    // receive the original unencoded form even when the endpoint is resolved via a
    // NormalizedEndpointUri (e.g. from SendDynamicProcessor / toD).
    private final String rawUri;

    private NormalizedUri(String normalizedValue, String rawUri) {
        super(normalizedValue);
        this.rawUri = rawUri;
    }

    /**
     * Creates a new {@link NormalizedUri} instance
     *
     * @param  uri        the uri
     * @param  normalized whether its already normalized
     * @return            the created normalized uri
     */
    public static NormalizedUri newNormalizedUri(String uri, boolean normalized) {
        if (normalized) {
            return new NormalizedUri(uri, null);
        } else {
            return new NormalizedUri(EndpointHelper.normalizeEndpointUri(uri), uri);
        }
    }

    /**
     * Returns the raw (un-normalized) URI that was used to create this instance. Components that declare
     * {@code useRawUri()=true} should receive this value so that parameter values are not URL-decoded before they reach
     * the component.
     *
     * <p>
     * May be {@code null} when the raw form is not known (e.g. when the URI was already normalized at construction
     * time). Callers should treat {@code null} as "fall back to the normalized URI".
     *
     * @since 4.23
     */
    public String getRawUri() {
        return rawUri;
    }

    @Override
    public String getUri() {
        return get();
    }

    @Override
    public String toString() {
        return get();
    }
}
