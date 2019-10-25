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
package org.apache.camel.component.jetty.rest;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty.BaseJettyTest;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.Test;

public class RestJettyCustomContentTypeTest extends BaseJettyTest {

    @Test
    public void testBlob() throws Exception {
        Exchange out = template.request("http://localhost:" + getPort() + "/users/blob", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
            }
        });

        assertEquals("application/foobar", out.getOut().getHeader(Exchange.CONTENT_TYPE));
        assertEquals("Some foobar stuff goes here", out.getOut().getBody(String.class));
    }

    @Test
    public void testJSon() throws Exception {
        Exchange out = template.request("http://localhost:" + getPort() + "/users/lives", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
            }
        });

        assertEquals("application/json", out.getOut().getHeader(Exchange.CONTENT_TYPE));
        assertEquals("{\"iso\":\"EN\",\"country\":\"England\"}", out.getOut().getBody(String.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // enable json binding
                restConfiguration().component("jetty").host("localhost").port(getPort()).bindingMode(RestBindingMode.json);

                rest("/users/").consumes("application/json").produces("application/json").get("blob").to("direct:blob").get("lives").to("direct:lives");

                from("direct:blob")
                    // but send back non json data
                    .setHeader(Exchange.CONTENT_TYPE, constant("application/foobar")).transform().constant("Some foobar stuff goes here");

                CountryPojo country = new CountryPojo();
                country.setIso("EN");
                country.setCountry("England");
                from("direct:lives").transform().constant(country);
            }
        };
    }

}
