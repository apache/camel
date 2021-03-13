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
package org.apache.camel.dsl.xml.jaxb;

import java.io.InputStream;

import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.StartupStep;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.StartupStepRecorder;
import org.apache.camel.spi.annotations.RoutesLoader;
import org.apache.camel.support.RoutesBuilderLoaderSupport;

import static org.apache.camel.xml.jaxb.JaxbHelper.loadRestsDefinition;
import static org.apache.camel.xml.jaxb.JaxbHelper.loadRouteTemplatesDefinition;
import static org.apache.camel.xml.jaxb.JaxbHelper.loadRoutesDefinition;

@ManagedResource(description = "Managed JAXB XML RoutesBuilderLoader")
@RoutesLoader(JaxbXmlRoutesBuilderLoader.EXTENSION)
public class JaxbXmlRoutesBuilderLoader extends RoutesBuilderLoaderSupport {

    public static final String EXTENSION = "xml";

    private StartupStepRecorder recorder;

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();
        recorder = getCamelContext().adapt(ExtendedCamelContext.class).getStartupStepRecorder();
    }

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
                // we use configure to load the routes

                StartupStep step = recorder != null
                        ? recorder.beginStep(JaxbXmlRoutesBuilderLoader.class, resource.getLocation(),
                                "Loading and Parsing XML routes")
                        : null;
                try {
                    try (InputStream is = resource.getInputStream()) {
                        RouteTemplatesDefinition templates = loadRouteTemplatesDefinition(getCamelContext(), is);
                        if (templates != null) {
                            setRouteTemplateCollection(templates);
                        }
                    }

                    try (InputStream is = resource.getInputStream()) {
                        RestsDefinition rests = loadRestsDefinition(getCamelContext(), is);
                        if (rests != null) {
                            setRestCollection(rests);
                        }
                    }

                    try (InputStream is = resource.getInputStream()) {
                        RoutesDefinition routes = loadRoutesDefinition(getCamelContext(), is);
                        if (routes != null) {
                            setRouteCollection(routes);
                        }
                    }
                } finally {
                    if (recorder != null) {
                        recorder.endStep(step);
                    }
                }
            }
        };
    }
}
