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

    private NormalizedUri(String value) {
        super(value);
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
            return new NormalizedUri(uri);
        } else {
            return new NormalizedUri(EndpointHelper.normalizeEndpointUri(uri));
        }
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
