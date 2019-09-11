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
package org.apache.camel.example.micrometer;

import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Route Builder that exposes the endpoint used by Prometheus to scrape
 * monitoring data from the Camel application. When running in a Spring
 * Boot 2.x environment, this is not required as Spring Boot already exposes
 * this endpoint by default.
 */
@Component
public class ScrapeRouteBuilder extends RouteBuilder {

    private final PrometheusMeterRegistry prometheusMeterRegistry;

    @Autowired
    public ScrapeRouteBuilder(PrometheusMeterRegistry prometheusMeterRegistry) {
        this.prometheusMeterRegistry = prometheusMeterRegistry;
    }

    @Override
    public void configure() throws Exception {
        from("netty-http:http://0.0.0.0:8088/metrics")
                .routeId("netty-http:scrape")
                .log(LoggingLevel.INFO, "Scraping metrics")
                .transform().method(prometheusMeterRegistry, "scrape()");

    }
}
