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
package org.apache.camel.example.karaf.rest;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.osgi.activator.CamelRoutesActivatorConstants;
import org.osgi.service.component.annotations.Component;

@Component(service = RouteBuilder.class, property = {CamelRoutesActivatorConstants.PRE_START_UP_PROP_NAME + "=true"})
public class CoreRouteBuilder extends RouteBuilder {
    
    @Override
    public void configure() throws Exception {
        
        getContext().setStreamCaching(true);

        restConfiguration().component("netty-http").port(8080)
            .endpointProperty("matchOnUriPrefix", "true")
            .contextPath("/camel-rest-osgi-activator")
            .bindingMode(RestBindingMode.off)
            .apiContextPath("/api-doc").apiProperty("api.title", "Camel Examples Karaf Rest API")
            .apiProperty("api.version", "1.0.0")
            .apiProperty("cors", "true")
            .enableCORS(true);

    }
}
