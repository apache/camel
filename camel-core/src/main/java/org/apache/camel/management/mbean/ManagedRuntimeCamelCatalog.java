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
package org.apache.camel.management.mbean;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedRuntimeCamelCatalogMBean;
import org.apache.camel.runtimecatalog.RuntimeCamelCatalog;
import org.apache.camel.spi.ManagementStrategy;

/**
 * @version 
 */
@ManagedResource(description = "Managed RuntimeCamelCatalog")
public class ManagedRuntimeCamelCatalog extends ManagedService implements ManagedRuntimeCamelCatalogMBean {
    private final CamelContext camelContext;
    private final RuntimeCamelCatalog camelCatalog;

    public ManagedRuntimeCamelCatalog(CamelContext camelContext, RuntimeCamelCatalog camelCatalog) {
        super(camelContext, camelCatalog);
        this.camelContext = camelContext;
        this.camelCatalog = camelCatalog;
    }

    public void init(ManagementStrategy strategy) {
        // do nothing
    }

    @Override
    public String componentJSonSchema(String name) {
        return camelCatalog.componentJSonSchema(name);
    }

    @Override
    public String dataFormatJSonSchema(String name) {
        return camelCatalog.dataFormatJSonSchema(name);
    }

    @Override
    public String languageJSonSchema(String name) {
        return camelCatalog.languageJSonSchema(name);
    }

    @Override
    public String modelJSonSchema(String name) {
        return camelCatalog.modelJSonSchema(name);
    }
}
