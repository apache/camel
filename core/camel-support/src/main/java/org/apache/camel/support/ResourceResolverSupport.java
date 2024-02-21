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

import org.apache.camel.CamelContext;
import org.apache.camel.spi.PropertiesComponent;
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
    private final String scheme;
    private final Logger logger;
    private CamelContext camelContext;

    protected ResourceResolverSupport(String scheme) {
        this.scheme = scheme;
        this.logger = LoggerFactory.getLogger(getClass());
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

        String context = StringHelper.after(location, ":");
        if (context == null) {
            throw new IllegalArgumentException("No context path provided: " + location);
        }

        if (context.contains(PropertiesComponent.PREFIX_TOKEN) && context.contains(PropertiesComponent.SUFFIX_TOKEN)) {
            context = camelContext.getPropertiesComponent().parseUri(context);
        }

        return createResource(location, context);
    }

    protected abstract Resource createResource(String location, String remaining);

    protected String getRemaining(String location) {
        return StringHelper.after(location, getSupportedScheme() + ":");
    }

    protected Logger getLogger() {
        return this.logger;
    }
}
