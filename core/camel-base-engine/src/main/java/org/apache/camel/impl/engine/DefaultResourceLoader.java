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
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.StaticService;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.spi.ResourceResolver;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.support.ResolverHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * Default {@link ResourceLoader}.
 */
public class DefaultResourceLoader extends ServiceSupport implements ResourceLoader, StaticService {
    /**
     * Prefix to use for looking up existing {@link ResourceLoader} from the {@link org.apache.camel.spi.Registry}.
     */
    public static final String RESOURCE_LOADER_KEY_PREFIX = "resource-loader-";

    private final Map<String, ResourceResolver> resolvers;
    private CamelContext camelContext;
    private ResourceResolver fallbackResolver;

    public DefaultResourceLoader() {
        this(null);
    }

    public DefaultResourceLoader(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.resolvers = new ConcurrentHashMap<>();
        this.fallbackResolver = new DefaultResourceResolvers.ClasspathResolver() {
            @Override
            public Resource resolve(String location) {
                return super.resolve(DefaultResourceResolvers.ClasspathResolver.SCHEME + ":" + location);
            }
        };

        this.fallbackResolver.setCamelContext(camelContext);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(this.fallbackResolver);
    }

    @Override
    public void doStop() throws Exception {
        super.doStop();

        ServiceHelper.stopService(resolvers.values());

        resolvers.clear();
    }

    public ResourceResolver getFallbackResolver() {
        return fallbackResolver;
    }

    public void setFallbackResolver(ResourceResolver fallbackResolver) {
        this.fallbackResolver = fallbackResolver;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.fallbackResolver.setCamelContext(this.camelContext);
    }

    @Override
    public Resource resolveResource(final String uri) {
        ObjectHelper.notNull(uri, "Resource uri must not be null");

        //
        // If the scheme is not set, use a fallback resolver which by default uses the classpath
        // resolver but a custom implementation can be provided. This is useful when as example
        // resources need to be discovered on a set of location through a dedicated resolver.
        //
        String scheme = StringHelper.before(uri, ":");
        if (scheme == null) {
            return this.fallbackResolver.resolve(uri);
        }

        ResourceResolver rr = getResourceResolver(scheme);
        if (rr == null) {
            throw new IllegalArgumentException(
                    "Cannot find a ResourceResolver in classpath supporting the scheme: " + scheme);
        }

        return rr.resolve(uri);
    }

    /**
     * Is there an existing resource resolver that can load from the given scheme
     *
     * @param scheme the scheme
     */
    protected boolean hasResourceResolver(String scheme) {
        ResourceResolver answer = getCamelContext().getRegistry().lookupByNameAndType(
                RESOURCE_LOADER_KEY_PREFIX + scheme,
                ResourceResolver.class);

        if (answer == null) {
            answer = resolvers.get(scheme);
        }

        return answer != null;
    }

    /**
     * Looks up a {@link ResourceResolver} for the given scheme in the registry or fallback to a factory finder
     * mechanism if none found.
     *
     * @param  scheme the file extension for which a loader should be find.
     * @return        a {@link RoutesBuilderLoader} or <code>null</code> if none found.
     */
    private ResourceResolver getResourceResolver(final String scheme) {
        ResourceResolver answer = getCamelContext().getRegistry().lookupByNameAndType(
                RESOURCE_LOADER_KEY_PREFIX + scheme,
                ResourceResolver.class);

        if (answer == null) {
            answer = resolvers.computeIfAbsent(scheme, this::resolveService);
        }

        return answer;
    }

    /**
     * Looks up a {@link ResourceResolver} for the given scheme with factory finder.
     *
     * @param  scheme the file extension for which a loader should be find.
     * @return        a {@link RoutesBuilderLoader} or <code>null</code> if none found.
     */
    private ResourceResolver resolveService(String scheme) {
        final CamelContext context = getCamelContext();
        final FactoryFinder finder
                = context.getCamelContextExtension().getBootstrapFactoryFinder(ResourceResolver.FACTORY_PATH);

        ResourceResolver rr = ResolverHelper.resolveService(context, finder, scheme, ResourceResolver.class).orElse(null);
        if (rr != null) {
            CamelContextAware.trySetCamelContext(rr, getCamelContext());
            ServiceHelper.startService(rr);
        }

        return rr;
    }
}
