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
import org.apache.camel.model.DataFormatDefinition;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

public class OsgiDataFormatResolver implements DataFormatResolver {

    private static final transient Log LOG = LogFactory.getLog(OsgiDataFormatResolver.class);

    private final BundleContext bundleContext;

    public OsgiDataFormatResolver(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public DataFormat resolveDataFormat(String name, CamelContext context) {
        // lookup in registry first
        Object bean = null;
        try {
            bean = context.getRegistry().lookup(name);
            if (bean != null && LOG.isDebugEnabled()) {
                LOG.debug("Found language: " + name + " in registry: " + bean);
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

    public DataFormatDefinition resolveDataFormatDefinition(String name, CamelContext context) {
        // lookup type and create the data format from it
        DataFormatDefinition type = lookup(context, name, DataFormatDefinition.class);
        if (type == null && context.getDataFormats() != null) {
            type = context.getDataFormats().get(name);
        }
        return type;
    }

    private static <T> T lookup(CamelContext context, String ref, Class<T> type) {
        try {
            return context.getRegistry().lookup(ref, type);
        } catch (Exception e) {
            // need to ignore not same type and return it as null
            return null;
        }
    }

    protected DataFormat getDataFormat(String name, CamelContext context) {
        LOG.trace("Finding DataFormat: " + name);
        try {
            ServiceReference[] refs = bundleContext.getServiceReferences(DataFormatResolver.class.getName(), "(dataformat=" + name + ")");
            if (refs != null && refs.length > 0) {
                DataFormatResolver resolver = (DataFormatResolver) bundleContext.getService(refs[0]);
                return resolver.resolveDataFormat(name, context);
            }
            return null;
        } catch (InvalidSyntaxException e) {
            throw new RuntimeException(e); // Should never happen
        }
    }

}
