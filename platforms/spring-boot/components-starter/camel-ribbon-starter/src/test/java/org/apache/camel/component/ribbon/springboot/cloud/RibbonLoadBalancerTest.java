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
package org.apache.camel.component.ribbon.springboot.cloud;

import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ribbon.cloud.RibbonServiceLoadBalancer;
import org.apache.camel.impl.cloud.DefaultServiceCallProcessor;
import org.apache.camel.spring.boot.cloud.CamelCloudServiceDiscovery;
import org.apache.camel.spring.boot.cloud.CamelCloudServiceFilter;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;


public class RibbonLoadBalancerTest {
    private static final int PORT1 = AvailablePortFinder.getNextAvailable();
    private static final int PORT2 = AvailablePortFinder.getNextAvailable();

    @Test
    public void testLoadBalancer() throws Exception {
        new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                "spring.main.banner-mode=off",
                "camel.cloud.consul.service-discovery.enabled=false",
                "debug=false",
                "camel.cloud.service-discovery.services[myService]=localhost:" + PORT1 + ",localhost:" + PORT2,
                "camel.cloud.ribbon.load-balancer.enabled=true")
            .run(
                context -> {
                    final CamelContext camelContext = context.getBean(CamelContext.class);
                    final ProducerTemplate template = camelContext.createProducerTemplate();

                    DefaultServiceCallProcessor processor = findServiceCallProcessor(camelContext);
                    assertThat(processor.getLoadBalancer()).isNotNull();
                    assertThat(processor.getLoadBalancer()).isInstanceOf(RibbonServiceLoadBalancer.class);

                    RibbonServiceLoadBalancer loadBalancer = (RibbonServiceLoadBalancer)processor.getLoadBalancer();
                    assertThat(loadBalancer.getServiceDiscovery()).isInstanceOf(CamelCloudServiceDiscovery.class);
                    assertThat(loadBalancer.getServiceFilter()).isInstanceOf(CamelCloudServiceFilter.class);

                    assertThat(template.requestBody("direct:start", null, String.class)).isEqualTo("" + PORT2);
                    assertThat(template.requestBody("direct:start", null, String.class)).isEqualTo("" + PORT1);
                }
            );
    }

    @EnableAutoConfiguration
    @Configuration
    public static class TestConfiguration {
        @Bean
        public RoutesBuilder routeBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start")
                        .routeId("scall")
                        .serviceCall()
                            .name("myService")
                            .uri("jetty:http://myService")
                            .end();
                    fromF("jetty:http://localhost:%d", PORT1)
                        .routeId("" + PORT1)
                        .transform()
                        .constant("" + PORT1);
                    fromF("jetty:http://localhost:%d", PORT2)
                        .routeId("" + PORT2)
                        .transform()
                        .constant("" + PORT2);
                }
            };
        }
    }

    // ************************************
    // Helpers
    // ************************************

    protected DefaultServiceCallProcessor findServiceCallProcessor(CamelContext context) {
        Route route = context.getRoute("scall");

        Assert.assertNotNull("ServiceCall Route should be present", route);

        return findServiceCallProcessor(route.navigate())
            .orElseThrow(() -> new IllegalStateException("Unable to find a ServiceCallProcessor"));
    }

    protected Optional<DefaultServiceCallProcessor> findServiceCallProcessor(Navigate<Processor> navigate) {
        for (Processor processor : navigate.next()) {
            if (processor instanceof DefaultServiceCallProcessor) {
                return Optional.ofNullable((DefaultServiceCallProcessor)processor);
            }

            if (processor instanceof Navigate) {
                return findServiceCallProcessor((Navigate<Processor>)processor);
            }
        }

        return Optional.empty();
    }
}
