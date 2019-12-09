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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.EndpointRegistry;
import org.apache.camel.support.CamelContextHelper;

/**
 * Default implementation of {@link org.apache.camel.spi.EndpointRegistry}
 */
public class DefaultEndpointRegistry extends AbstractDynamicRegistry<EndpointKey, Endpoint> implements EndpointRegistry<EndpointKey> {

    public DefaultEndpointRegistry(CamelContext context) {
        super(context, CamelContextHelper.getMaximumEndpointCacheSize(context));
    }

    public DefaultEndpointRegistry(CamelContext context, Map<EndpointKey, Endpoint> endpoints) {
        this(context);
        putAll(endpoints);
    }

    @Override
    public boolean isStatic(String key) {
        return isStatic(new EndpointKey(key));
    }

    @Override
    public boolean isDynamic(String key) {
        return isDynamic(new EndpointKey(key));
    }

    @Override
    public String toString() {
        return "EndpointRegistry for " + context.getName() + ", capacity: " + maxCacheSize;
    }
}
