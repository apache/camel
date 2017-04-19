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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@DirtiesContext
@SpringBootApplication
@SpringBootTest(
    classes = {
        RibbonLoadBalancerTest.TestConfiguration.class
    },
    properties = {
        "debug=false",
        "camel.cloud.service-discovery.services[myService]=localhost:9090,localhost:9091",
        "camel.cloud.ribbon.load-balancer.enabled=true"
})
public class RibbonLoadBalancerTest {
    @Autowired
    private CamelContext context;
    @Autowired
    private ProducerTemplate template;

    @Test
    public void testLoadBalancer() throws Exception {
        DefaultServiceCallProcessor processor = findServiceCallProcessor();

        Assert.assertNotNull(processor.getLoadBalancer());
        Assert.assertTrue(processor.getLoadBalancer() instanceof RibbonServiceLoadBalancer);

        RibbonServiceLoadBalancer loadBalancer = (RibbonServiceLoadBalancer)processor.getLoadBalancer();
        Assert.assertTrue(loadBalancer.getServiceDiscovery() instanceof CamelCloudServiceDiscovery);
        Assert.assertTrue(loadBalancer.getServiceFilter() instanceof CamelCloudServiceFilter);

        Assert.assertEquals("9091", template.requestBody("direct:start", null, String.class));
        Assert.assertEquals("9090", template.requestBody("direct:start", null, String.class));
    }

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
                    from("jetty:http://localhost:9090").routeId("9090")
                        .transform().constant("9090");
                    from("jetty:http://localhost:9091").routeId("9091")
                        .transform().constant("9091");
                }
            };
        }
    }

    // ************************************
    // Helpers
    // ************************************

    protected DefaultServiceCallProcessor findServiceCallProcessor() {
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
