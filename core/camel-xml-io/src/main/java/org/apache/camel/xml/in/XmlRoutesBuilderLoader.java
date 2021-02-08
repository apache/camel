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
package org.apache.camel.xml.in;

import java.io.InputStream;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.RoutesBuilderLoaderSupport;

@ManagedResource(description = "Managed XML RoutesBuilderLoader")
@JdkService(RoutesBuilderLoader.FACTORY_GROUP + "/" + XmlRoutesBuilderLoader.EXTENSION)
public class XmlRoutesBuilderLoader extends RoutesBuilderLoaderSupport {

    public static final String EXTENSION = "xml";
    public static final String NAMESPACE = "http://camel.apache.org/schema/spring";

    @ManagedAttribute(description = "Supported file extension")
    @Override
    public String getSupportedExtension() {
        return EXTENSION;
    }

    @Override
    public RoutesBuilder loadRoutesBuilder(Resource resource) throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                try (InputStream is = resource.getInputStream()) {
                    new ModelParser(is, NAMESPACE)
                            .parseRouteTemplatesDefinition()
                            .ifPresent(this::setRouteTemplateCollection);
                }
                try (InputStream is = resource.getInputStream()) {
                    new ModelParser(is, NAMESPACE)
                            .parseRestsDefinition()
                            .ifPresent(this::setRestCollection);
                }
                try (InputStream is = resource.getInputStream()) {
                    new ModelParser(is, NAMESPACE)
                            .parseRoutesDefinition()
                            .ifPresent(this::setRouteCollection);
                }
            }
        };
    }
}
