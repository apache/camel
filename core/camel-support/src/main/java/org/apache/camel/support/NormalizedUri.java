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

    // Whether the endpoint uri was computed from the current message at runtime (for example the per-message
    // recipient of toD, enrich, wireTap, pollEnrich, recipientList, routingSlip or dynamicRouter). When true the
    // query option values are bound more strictly, to stop message content from being turned into routing
    // configuration: a {{...}} property placeholder, a #class: reference or a #property: reference in such a value is
    // kept as literal text (no placeholder resolution, no class instantiation, no property lookup), while plain
    // registry references (#bean:, #type:) are still resolved. Not part of equals/hashCode: the endpoint registry key
    // stays the uri alone.
    private final boolean literalOptionBinding;

    private NormalizedUri(String normalizedValue, String rawUri, boolean literalOptionBinding) {
        super(normalizedValue);
        this.rawUri = rawUri;
        this.literalOptionBinding = literalOptionBinding;
    }

    /**
     * Creates a new {@link NormalizedUri} instance
     *
     * @param  uri        the uri
     * @param  normalized whether its already normalized
     * @return            the created normalized uri
     */
    public static NormalizedUri newNormalizedUri(String uri, boolean normalized) {
        return newNormalizedUri(uri, normalized, false);
    }

    /**
     * Creates a new {@link NormalizedUri} instance.
     *
     * @param  uri                  the uri
     * @param  normalized           whether its already normalized
     * @param  literalOptionBinding whether the query option values were computed from the current message and must be
     *                              bound as literal text (no property-placeholder and no bean/class reference
     *                              resolution)
     * @return                      the created normalized uri
     * @since                       4.24
     */
    public static NormalizedUri newNormalizedUri(String uri, boolean normalized, boolean literalOptionBinding) {
        if (normalized) {
            return new NormalizedUri(uri, null, literalOptionBinding);
        } else {
            return new NormalizedUri(EndpointHelper.normalizeEndpointUri(uri), uri, literalOptionBinding);
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

    /**
     * Whether the query option values of this uri were computed from the current message at runtime and must therefore
     * be bound as literal text, that is without property-placeholder resolution and without bean/class reference
     * ({@code #class:}, {@code #bean:}, {@code #type:}, ...) resolution.
     *
     * @since 4.24
     */
    public boolean isLiteralOptionBinding() {
        return literalOptionBinding;
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
