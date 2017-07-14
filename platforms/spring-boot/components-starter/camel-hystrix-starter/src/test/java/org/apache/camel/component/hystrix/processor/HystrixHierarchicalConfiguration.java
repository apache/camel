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
package org.apache.camel.component.hystrix.processor;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.HystrixConfigurationDefinition;
import org.springframework.context.annotation.Bean;

public class HystrixHierarchicalConfiguration {
    @Bean(name = "ref-hystrix")
    public HystrixConfigurationDefinition hystrixConfiguration() {
        return new HystrixConfigurationDefinition()
            .groupKey("ref-group-key")
            .corePoolSize(5);
    }

    @Bean
    public RouteBuilder routeBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .routeId("hystrix-route")
                    .hystrix()
                        .hystrixConfiguration("ref-hystrix")
                        .hystrixConfiguration()
                            .groupKey("local-conf-group-key")
                            .end()
                        .to("direct:foo")
                        .onFallback()
                            .transform().constant("Fallback message")
                        .end();

                from("direct:foo")
                    .transform().body(b -> "Bye World");

            }
        };
    }
}
