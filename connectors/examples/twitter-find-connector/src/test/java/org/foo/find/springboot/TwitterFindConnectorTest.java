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
package org.foo.find.springboot;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.twitter.search.TwitterSearchEndpoint;
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
    properties = {
        "spring.main.banner-mode=off"
    },
    classes = {
        TwitterFindConnectorTest.TestConfiguration.class
    }
)
public class TwitterFindConnectorTest {
    @Autowired
    private CamelContext camelContext;

    @Test
    public void testConfiguration() throws Exception {
        List<TwitterSearchEndpoint> endpoints = camelContext.getEndpoints().stream()
            .filter(TwitterSearchEndpoint.class::isInstance)
            .map(TwitterSearchEndpoint.class::cast)
            .collect(Collectors.toList());

        Assert.assertFalse(endpoints.isEmpty());

        endpoints.forEach(endpoint -> {
            if (endpoint.getEndpointUri().startsWith("twitter-search-twitter-find-component:")) {
                Assert.assertEquals("cameltest", endpoint.getKeywords());
                Assert.assertTrue(endpoint.isFilterOld());
            } else if (endpoint.getEndpointUri().startsWith("twitter-search-tw-find1:")) {
                Assert.assertEquals("camelsearchtest1", endpoint.getKeywords());
                Assert.assertFalse(endpoint.isFilterOld());
            } else if (endpoint.getEndpointUri().startsWith("twitter-search-tw-find2:")) {
                Assert.assertEquals("camelsearchtest2", endpoint.getKeywords());
                Assert.assertFalse(endpoint.isFilterOld());
            } else {
                Assert.fail("Unexpected endpoint " + endpoint.getEndpointUri());
            }
        });

        Assert.assertNotEquals(
            camelContext.getComponent("twitter-find-component"),
            camelContext.getComponent("twitter-search-tw-find1")
        );
        Assert.assertNotEquals(
            camelContext.getComponent("twitter-find-component"),
            camelContext.getComponent("twitter-search-tw-find2")
        );
        Assert.assertNotEquals(
            camelContext.getComponent("twitter-search-tw-find1"),
            camelContext.getComponent("twitter-search-tw-find2")
        );
    }

    // ***********************************
    // Configuration
    // ***********************************

    @Configuration
    public static class TestConfiguration {
        @Bean
        public RouteBuilder routeBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("twitter-find?filterOld=true")
                        .noAutoStartup()
                        .to("mock:result");
                    from("tw-find1?keywords=camelsearchtest1&filterOld=false")
                        .noAutoStartup()
                        .to("mock:result");
                    from("tw-find2?keywords=camelsearchtest2&filterOld=false")
                        .noAutoStartup()
                        .to("mock:result");
                }
            };
        }
    }
}
