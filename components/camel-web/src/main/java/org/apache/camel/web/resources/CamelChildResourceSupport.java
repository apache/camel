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
package org.apache.camel.web.resources;

import com.sun.jersey.api.view.ImplicitProduces;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.converter.DefaultTypeConverter;
import org.apache.camel.spi.TypeConverterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A useful base class for any sub resource of the root {@link org.apache.camel.web.resources.CamelContextResource}
 */
@ImplicitProduces(Constants.HTML_MIME_TYPES)
public class CamelChildResourceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(CamelChildResourceSupport.class);

    private final CamelContext camelContext;
    private final ProducerTemplate template;
    private CamelContextResource contextResource;

    public CamelChildResourceSupport(CamelContextResource contextResource) {
        this.contextResource = contextResource;
        camelContext = contextResource.getCamelContext();
        template = contextResource.getTemplate();
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public ProducerTemplate getTemplate() {
        return template;
    }

    public CamelContextResource getContextResource() {
        return contextResource;
    }

    public DefaultTypeConverter getDefaultTypeConverter() {
        TypeConverterRegistry converterRegistry = getCamelContext().getTypeConverterRegistry();
        if (converterRegistry instanceof DefaultTypeConverter) {
            return (DefaultTypeConverter) converterRegistry;
        } else {
            LOG.info("Not a default type converter as it is: " + converterRegistry);
        }
        return null;
    }
}
