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

package org.apache.camel.spring.cloud;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.spring.boot.cloud.CamelCloudAutoConfiguration;
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

@DirtiesContext
@RunWith(SpringRunner.class)
@SpringBootApplication
@SpringBootTest(
    classes = {
        CamelAutoConfiguration.class,
        CamelCloudAutoConfiguration.class,
        CamelSpringCloudServiceCallRibbonTest.TestConfiguration.class
    },
    properties = {
        "ribbon.eureka.enabled=false",
        "ribbon.listOfServers=localhost:9090,localhost:9092",
        "ribbon.ServerListRefreshInterval=15000",
        "debug=false"
    }
)
public class CamelSpringCloudServiceCallRibbonTest {
    @Autowired
    private ProducerTemplate template;

    @Test
    public void testServiceCall() throws Exception {
        Assert.assertEquals("9090", template.requestBody("direct:start", null, String.class));
        Assert.assertEquals("9092", template.requestBody("direct:start", null, String.class));
    }

    // *************************************
    // Config
    // *************************************

    @Configuration
    public static class TestConfiguration {
        @Bean
        public RouteBuilder myRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start")
                        .serviceCall()
                        .name("custom-svc-list/hello");

                    from("jetty:http://localhost:9090/hello")
                        .transform()
                        .constant("9090");
                    from("jetty:http://localhost:9091/hello")
                        .transform()
                        .constant("9091");
                    from("jetty:http://localhost:9092/hello")
                        .transform()
                        .constant("9092");
                }
            };
        }
    }
}

