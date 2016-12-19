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

package org.apache.camel.spring.cloud.processor.remote;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@DirtiesContext
@RunWith(SpringRunner.class)
@SpringBootApplication
@SpringBootTest(
    classes = {
        CamelAutoConfiguration.class,
        CamelCloudServiceCallTest.TestConfiguration.class
    },
    properties = {
        "ribbon.enabled=false"
    }
)
public class CamelCloudServiceCallTest {
    private static final int[] PORTS = new int[] {
        9090, 9091, 9092, 9093
    };

    @Autowired
    private ProducerTemplate template;

    @Test
    public void testServiceCall() throws Exception {
        for (int port : PORTS) {
            Assert.assertEquals(Integer.toString(port), template.requestBody("direct:start", null, String.class));
        }
    }

    // **************************
    // Configuration
    // **************************

    @Configuration
    public static class TestConfiguration {
        @Bean
        public DiscoveryClient myDiscoveryClient1() {
            CamelCloudDiscoveryClient client = new CamelCloudDiscoveryClient("myDiscoveryClient1");
            for (int port : PORTS) {
                client.addServiceInstance("custom-server-list", "localhost", port);
            }

            return client;
        }

        @Bean
        public RouteBuilder myRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start")
                        .serviceCall()
                            .name("custom-server-list/hello")
                            .loadBalancer("random-load-balancer");

                    for (int port : PORTS) {
                        fromF("jetty:http://localhost:%d/hello", port)
                            .transform()
                                .constant(Integer.toString(port));
                    }
                }
            };
        }
    }

}

