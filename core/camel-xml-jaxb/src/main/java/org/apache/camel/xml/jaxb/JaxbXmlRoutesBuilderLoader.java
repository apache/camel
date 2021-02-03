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
package org.apache.camel.xml.jaxb;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteTemplatesDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesBuilderLoader;
import org.apache.camel.spi.annotations.JdkService;

import static org.apache.camel.xml.jaxb.JaxbHelper.loadRestsDefinition;
import static org.apache.camel.xml.jaxb.JaxbHelper.loadRouteTemplatesDefinition;
import static org.apache.camel.xml.jaxb.JaxbHelper.loadRoutesDefinition;

@JdkService(RoutesBuilderLoader.FACTORY_GROUP + "/xml")
public class JaxbXmlRoutesBuilderLoader implements RoutesBuilderLoader, CamelContextAware {
    public static final String NAMESPACE = "http://camel.apache.org/schema/spring";

    private CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public RoutesBuilder loadRoutesBuilder(Resource resource) throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                RouteTemplatesDefinition templates = loadRouteTemplatesDefinition(getCamelContext(), resource.getInputStream());
                if (templates != null) {
                    setRouteTemplateCollection(templates);
                }

                RestsDefinition rests = loadRestsDefinition(getCamelContext(), resource.getInputStream());
                if (rests != null) {
                    setRestCollection(rests);
                }

                RoutesDefinition routes = loadRoutesDefinition(getCamelContext(), resource.getInputStream());
                if (routes != null) {
                    setRouteCollection(routes);
                }
            }
        };
    }
}
