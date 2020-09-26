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
package org.apache.camel.support.component;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.catalog.RuntimeCamelCatalog;
import org.apache.camel.spi.SendDynamicAware;

/**
 * Support class for {@link SendDynamicAware} implementations.
 */
public abstract class SendDynamicAwareSupport implements SendDynamicAware {

    public Map<String, String> endpointProperties(Exchange exchange, String uri) throws Exception {
        RuntimeCamelCatalog catalog = exchange.getContext().adapt(ExtendedCamelContext.class).getRuntimeCamelCatalog();
        Map<String, String> properties = catalog.endpointProperties(uri);
        return properties;
    }

    public Map<String, String> endpointLenientProperties(Exchange exchange, String uri) throws Exception {
        RuntimeCamelCatalog catalog = exchange.getContext().adapt(ExtendedCamelContext.class).getRuntimeCamelCatalog();
        Map<String, String> properties = catalog.endpointLenientProperties(uri);
        return properties;
    }

    public String asEndpointUri(Exchange exchange, String scheme, Map<String, String> properties) throws Exception {
        RuntimeCamelCatalog catalog = exchange.getContext().adapt(ExtendedCamelContext.class).getRuntimeCamelCatalog();
        return catalog.asEndpointUri(scheme, properties, false);
    }
}
