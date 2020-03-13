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
package org.apache.camel.impl.engine;

import org.apache.camel.ValueHolder;
import org.apache.camel.spi.NormalizedEndpointUri;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.util.StringHelper;

/**
 * Key used in {@link DefaultEndpointRegistry} in {@link AbstractCamelContext},
 * to ensure a consistent lookup.
 */
public final class EndpointKey extends ValueHolder<String> implements NormalizedEndpointUri {

    public EndpointKey(String uri) {
        this(uri, false);
    }

    /**
     * Optimized when the uri is already normalized.
     */
    public EndpointKey(String uri, boolean normalized) {
        super(normalized ? uri : EndpointHelper.normalizeEndpointUri(uri));
        StringHelper.notEmpty(uri, "uri");
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
