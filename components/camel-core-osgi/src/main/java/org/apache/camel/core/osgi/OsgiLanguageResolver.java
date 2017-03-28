/**
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
package org.apache.camel.core.osgi;

import org.apache.camel.CamelContext;
import org.apache.camel.NoSuchLanguageException;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.LanguageResolver;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResolverHelper;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OsgiLanguageResolver implements LanguageResolver {
    private static final Logger LOG = LoggerFactory.getLogger(OsgiLanguageResolver.class);

    private final BundleContext bundleContext;

    public OsgiLanguageResolver(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public Language resolveLanguage(String name, CamelContext context) {
        // lookup in registry first
        Language lang = ResolverHelper.lookupLanguageInRegistryWithFallback(context, name);
        if (lang != null) {
            return lang;
        }

        lang = getLanguage(name, context);
        if (lang != null) {
            return lang;
        }
        LanguageResolver resolver = getLanguageResolver("default", context);
        if (resolver != null) {
            return resolver.resolveLanguage(name, context);
        }
        throw new NoSuchLanguageException(name);
    }

    protected Language getLanguage(String name, CamelContext context) {
        LOG.trace("Finding Language: {}", name);
        try {
            ServiceReference<?>[] refs = bundleContext.getServiceReferences(LanguageResolver.class.getName(), "(language=" + name + ")");
            if (refs != null) {
                for (ServiceReference<?> ref : refs) {
                    Object service = bundleContext.getService(ref);
                    if (LanguageResolver.class.isAssignableFrom(service.getClass())) {
                        LanguageResolver resolver = (LanguageResolver) service;
                        return resolver.resolveLanguage(name, context);
                    }
                }
            }

            return null;
        } catch (InvalidSyntaxException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    protected LanguageResolver getLanguageResolver(String name, CamelContext context) {
        LOG.trace("Finding LanguageResolver: {}", name);
        try {
            ServiceReference<?>[] refs = bundleContext.getServiceReferences(LanguageResolver.class.getName(), "(resolver=" + name + ")");
            if (refs != null) {
                for (ServiceReference<?> ref : refs) {
                    Object service = bundleContext.getService(ref);
                    if (LanguageResolver.class.isAssignableFrom(service.getClass())) {
                        LanguageResolver resolver = (LanguageResolver) service;
                        return resolver;
                    }
                }
            }
            return null;
        } catch (InvalidSyntaxException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

}
