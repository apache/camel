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
package org.apache.camel.spring.boot.issues;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.SocketUtils;

@RunWith(SpringRunner.class)
@EnableAutoConfiguration
@SpringBootTest(classes = { RestDslPostTest.class })
public class RestDslPostTest extends Assert {

    static final int PORT = SocketUtils.findAvailableTcpPort(20000);

    @EndpointInject(uri = "mock:user")
    protected MockEndpoint resultEndpointUser;
    @EndpointInject(uri = "mock:country")
    protected MockEndpoint resultEndpointCountry;

    @Autowired
    protected ProducerTemplate template;

    @Autowired
    CamelContext context;

    @Test
    public void testMultiplePostTypes() throws Exception {

        UserPojo user = new UserPojo();
        user.setId(1);
        user.setName("My Name");
        resultEndpointUser.expectedBodiesReceived(user);
        resultEndpointUser.expectedMessageCount(1);

        CountryPojo country = new CountryPojo();
        country.setCountry("England");
        country.setIso("EN");
        resultEndpointCountry.expectedBodiesReceived(country);
        resultEndpointCountry.expectedMessageCount(1);

        ExchangeBuilder builder = ExchangeBuilder.anExchange(context)
                .withHeader(Exchange.HTTP_METHOD, HttpMethod.POST)
                .withHeader(Exchange.ACCEPT_CONTENT_TYPE, MediaType.APPLICATION_JSON);
        Exchange outExchangeUser = builder.withBody("{\"id\": 1, \"name\": \"My Name\"}").build();
        Exchange outExchangeCountry = builder.withBody("{\"iso\": \"EN\", \"country\": \"England\"}").build();

        template.send("jetty:http://localhost:" + PORT + "/user", outExchangeUser);
        template.send("jetty:http://localhost:" + PORT + "/country", outExchangeCountry);

        resultEndpointCountry.assertIsSatisfied();
        resultEndpointUser.assertIsSatisfied();

    }

    @Configuration
    public static class ContextConfig {
        @Bean
        public RouteBuilder route() {
            return new RouteBuilder() {
                public void configure() {
                    restConfiguration().host("localhost").port(PORT).bindingMode(RestBindingMode.json);

                    rest("/").post("/user").type(UserPojo.class).route().to("mock:user").endRest().post("/country")
                            .type(CountryPojo.class).route().to("mock:country").endRest();

                }
            };
        }
    }
}
