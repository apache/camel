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
package org.apache.camel.spring.boot.duplicatedrouteid;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.boot.CamelSpringBootInitializationException;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

public class DuplicatedRouteIdTest extends Assert {

    @Test(expected = CamelSpringBootInitializationException.class)
    public void shouldDetectDuplicatedRouteId() {
        new SpringApplication(DuplicatedRouteIdTestConfiguration.class).run();
    }

}

@SpringBootApplication
class DuplicatedRouteIdTestConfiguration {

    @Bean
    RoutesBuilder firstRoute() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:first").routeId("foo").to("mock:first");
            }
        };
    }

    @Bean
    RoutesBuilder secondRoute() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:second").routeId("foo").to("mock:second");
            }
        };
    }


}