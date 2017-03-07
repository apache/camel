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

import java.util.Collection;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatFactory;
import org.apache.camel.spi.DataFormatResolver;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResolverHelper;
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
        DataFormat dataFormat = ResolverHelper.lookupDataFormatInRegistryWithFallback(context, name);
        if (dataFormat == null) {
            dataFormat = getDataFormat(name, context, false);
        }

        if (dataFormat == null) {
            dataFormat = createDataFormat(name, context);
        }

        return dataFormat;
    }

    public DataFormat createDataFormat(String name, CamelContext context) {
        DataFormat dataFormat = null;

        // lookup in registry first
        DataFormatFactory dataFormatFactory = ResolverHelper.lookupDataFormatFactoryInRegistryWithFallback(context, name);
        if (dataFormatFactory != null) {
            dataFormat = dataFormatFactory.newInstance();
        }

        if (dataFormat == null) {
            dataFormat = getDataFormat(name, context, true);
        }

        return dataFormat;
    }

    private DataFormat getDataFormat(String name, CamelContext context, boolean create) {
        LOG.trace("Finding DataFormat: {}", name);
        try {
            Collection<ServiceReference<DataFormatResolver>> refs = bundleContext.getServiceReferences(DataFormatResolver.class, "(dataformat=" + name + ")");
            if (refs != null) {
                for (ServiceReference<DataFormatResolver> ref : refs) {
                    return create
                        ? bundleContext.getService(ref).createDataFormat(name, context)
                        : bundleContext.getService(ref).resolveDataFormat(name, context);
                }
            }
            return null;
        } catch (InvalidSyntaxException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

}
