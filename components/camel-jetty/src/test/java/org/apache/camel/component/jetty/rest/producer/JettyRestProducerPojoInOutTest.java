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
package org.apache.camel.component.jetty.rest.producer;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.component.jetty.rest.CountryPojo;
import org.apache.camel.component.jetty.rest.UserPojo;
import org.apache.camel.component.jetty.rest.UserService;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.Test;

public class JettyRestProducerPojoInOutTest extends BaseJettyTest {

    @Test
    public void testJettyEmptyBody() throws Exception {
        String out = fluentTemplate.to("rest:get:users/lives").withHeader(Exchange.CONTENT_TYPE, "application/json").request(String.class);

        assertNotNull(out);
        assertEquals("{\"iso\":\"EN\",\"country\":\"England\"}", out);
    }

    @Test
    public void testJettyJSonBody() throws Exception {
        String body = "{\"id\": 123, \"name\": \"Donald Duck\"}";

        String out = fluentTemplate.to("rest:post:users/lives").withHeader(Exchange.CONTENT_TYPE, "application/json").withBody(body).request(String.class);

        assertNotNull(out);
        assertEquals("{\"iso\":\"EN\",\"country\":\"England\"}", out);
    }

    @Test
    public void testJettyPojoIn() throws Exception {
        UserPojo user = new UserPojo();
        user.setId(123);
        user.setName("Donald Duck");

        String out = fluentTemplate.to("rest:post:users/lives").withHeader(Exchange.CONTENT_TYPE, "application/json").withBody(user).request(String.class);

        assertNotNull(out);
        assertEquals("{\"iso\":\"EN\",\"country\":\"England\"}", out);
    }

    @Test
    public void testJettyPojoInOut() throws Exception {
        UserPojo user = new UserPojo();
        user.setId(123);
        user.setName("Donald Duck");

        // must provide outType parameter to tell Camel to bind the output from
        // the REST service from json to POJO
        CountryPojo pojo = fluentTemplate.to("rest:post:users/lives?outType=org.apache.camel.component.jetty.rest.CountryPojo")
            .withHeader(Exchange.CONTENT_TYPE, "application/json").withBody(user).request(CountryPojo.class);

        assertNotNull(pojo);
        assertEquals("EN", pojo.getIso());
        assertEquals("England", pojo.getCountry());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // configure to use jetty on localhost with the given port
                // and enable auto binding mode
                restConfiguration().component("jetty").producerComponent("http").host("localhost").port(getPort()).bindingMode(RestBindingMode.auto);

                // use the rest DSL to define the rest services
                rest("/users/")
                    // just return the default country here
                    .get("lives").to("direct:start").post("lives").type(UserPojo.class).outType(CountryPojo.class).route().bean(new UserService(), "livesWhere");

                CountryPojo country = new CountryPojo();
                country.setIso("EN");
                country.setCountry("England");
                from("direct:start").transform().constant(country);
            }
        };
    }

}
