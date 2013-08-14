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
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.util.ObjectHelper;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OsgiDataFormatResolver implements DataFormatResolver {
    private static final Logger LOG = LoggerFactory.getLogger(OsgiDataFormatResolver.class);

    private final BundleContext bundleContext;

    public OsgiDataFormatResolver(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public DataFormat resolveDataFormat(String name, CamelContext context) {
        // lookup in registry first
        Object bean = null;
        try {
            bean = context.getRegistry().lookupByName(name);
            if (bean != null) {
                LOG.debug("Found language: {} in registry: {}", name, bean);
            }
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Ignored error looking up bean: " + name + ". Error: " + e);
            }
        }
        if (bean instanceof DataFormat) {
            return (DataFormat) bean;
        }
        return getDataFormat(name, context);
    }

    protected DataFormat getDataFormat(String name, CamelContext context) {
        LOG.trace("Finding DataFormat: {}", name);
        try {
            ServiceReference<?>[] refs = bundleContext.getServiceReferences(DataFormatResolver.class.getName(), "(dataformat=" + name + ")");
            if (refs != null) {
                for (ServiceReference<?> ref : refs) {
                    Object service = bundleContext.getService(ref);
                    if (DataFormatResolver.class.isAssignableFrom(service.getClass())) {
                        DataFormatResolver resolver = (DataFormatResolver) service;
                        return resolver.resolveDataFormat(name, context);
                    }
                }
            }
            return null;
        } catch (InvalidSyntaxException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

}
