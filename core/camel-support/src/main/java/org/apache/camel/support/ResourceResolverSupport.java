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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceResolver;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for {@link ResourceResolver} implementations.
 */
public abstract class ResourceResolverSupport extends ServiceSupport implements ResourceResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceResolverSupport.class);

    private final String scheme;
    private CamelContext camelContext;

    protected ResourceResolverSupport(String scheme) {
        this.scheme = scheme;
    }

    public String getSupportedScheme() {
        return scheme;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public Resource resolve(String location) {
        if (!location.startsWith(getSupportedScheme() + ":")) {
            throw new IllegalArgumentException("Unsupported scheme: " + location);
        }

        return createResource(location);
    }

    protected abstract Resource createResource(String location);

    protected String tryDecodeUri(String uri) {
        try {
            // try to decode as the uri may contain %20 for spaces etc
            uri = URLDecoder.decode(uri, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            LOGGER.trace("Error URL decoding uri using UTF-8 encoding: {}. This exception is ignored.", uri);
            // ignore
        }

        return uri;
    }

    protected String getRemaining(String location) {
        return StringHelper.after(location, getSupportedScheme() + ":");
    }
}
