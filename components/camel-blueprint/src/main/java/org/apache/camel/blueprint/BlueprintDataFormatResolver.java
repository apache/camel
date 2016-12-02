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
package org.apache.camel.blueprint;

import org.apache.camel.CamelContext;
import org.apache.camel.core.osgi.OsgiDataFormatResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatFactory;
import org.apache.camel.spi.DataFormatResolver;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlueprintDataFormatResolver extends OsgiDataFormatResolver {
    private static final Logger LOG = LoggerFactory.getLogger(BlueprintDataFormatResolver.class);

    public BlueprintDataFormatResolver(BundleContext bundleContext) {
        super(bundleContext);
    }

    @Override
    public DataFormat resolveDataFormat(String name, CamelContext context) {
        DataFormat dataFormat = null;

        DataFormatResolver resolver = context.getRegistry().lookupByNameAndType(".camelBlueprint.dataformatResolver." + name, DataFormatResolver.class);
        if (resolver != null) {
            LOG.debug("Found dataformat resolver: {} in registry: {}", name, resolver);
            dataFormat = resolver.resolveDataFormat(name, context);
        }

        if (dataFormat == null) {
            dataFormat = super.resolveDataFormat(name, context);
        }

        return dataFormat;
    }

    @Override
    public DataFormat createDataFormat(String name, CamelContext context) {
        DataFormatFactory factory = context.getRegistry().lookupByNameAndType(".camelBlueprint.dataformatFactory." + name, DataFormatFactory.class);
        if (factory  != null) {
            LOG.debug("Found dataformat factory: {} in registry: {}", name, factory);
            return factory.newInstance();
        }

        return super.createDataFormat(name, context);
    }
}
