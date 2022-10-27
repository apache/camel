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
package org.apache.camel.main.download;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.impl.engine.DefaultUriFactoryResolver;
import org.apache.camel.spi.EndpointUriFactory;

/**
 * Auto downloaded needed JARs when resolving uri factory.
 */
public class DependencyDownloaderUriFactoryResolver extends DefaultUriFactoryResolver {

    public DependencyDownloaderUriFactoryResolver(CamelContext context) {
        setCamelContext(context);
    }

    @Override
    public EndpointUriFactory resolveFactory(String name, CamelContext context) {
        // need to trigger component resolver that is capable of downloading if needed
        try {
            context.adapt(ExtendedCamelContext.class).getComponentResolver().resolveComponent(name, context);
        } catch (Exception e) {
            // ignore
        }

        return super.resolveFactory(name, context);
    }
}
