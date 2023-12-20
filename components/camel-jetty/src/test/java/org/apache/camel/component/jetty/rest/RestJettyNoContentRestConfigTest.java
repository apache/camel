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
import org.apache.camel.converter.jaxb.JaxbConstants;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.support.MessageHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RestJettyNoContentRestConfigTest extends BaseJettyTest {

    @Test
    public void testEmptyJson204ConfigNoContentEnabled() {
        Exchange exchange = template.request("http://localhost:" + getPort() + "/v1/empty/country", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "GET");
            }
        });

        assertEquals(204, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertNull(exchange.getMessage().getBody());
    }

    @Test
    public void testEmptyXml204ConfigNoContentEnabled() {
        Exchange exchange = template.request("http://localhost:" + getPort() + "/v1/empty/address", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "GET");
            }
        });

        assertEquals(204, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertNull(exchange.getMessage().getBody());
    }

    @Test
    public void testEmptyJson200RestConfigNoContentDisabled() {
        Exchange exchange = template.request("http://localhost:" + getPort() + "/v2/empty/country", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "GET");
            }
        });

        assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("[]", MessageHelper.extractBodyAsString(exchange.getMessage()));
    }

    @Test
    public void testEmptyXml200RestConfigNoContentDisabled() {
        Exchange exchange = template.request("http://localhost:" + getPort() + "/v2/empty/address", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "GET");
            }
        });

        assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                     "<address:address xmlns:address=\"http://www.camel.apache.org/jaxb/example/address/1\"/>\n",
                MessageHelper.extractBodyAsString(exchange.getMessage()));
    }

    @Test
    public void testEmpty200VerbNoContentDisabled() {
        Exchange exchange = template.request("http://localhost:" + getPort() + "/v3/empty/country", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "GET");
            }
        });

        assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("[]", MessageHelper.extractBodyAsString(exchange.getMessage()));
    }

    @Test
    public void testJson200ConfigNoContentEnabled() {
        Exchange exchange = template.request("http://localhost:" + getPort() + "/country", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "GET");
            }
        });

        assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("{\"iso\":\"EN\",\"country\":\"England\"}", MessageHelper.extractBodyAsString(exchange.getMessage()));
    }

    @Test
    public void testXml200ConfigNoContentEnabled() {
        Exchange exchange = template.request("http://localhost:" + getPort() + "/address", new Processor() {
            @Override
            public void process(Exchange exchange) {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "GET");
            }
        });

        assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                     "<address:address xmlns:address=\"http://www.camel.apache.org/jaxb/example/address/1\">\n" +
                     "    <address:street>Main Street</address:street>\n" +
                     "    <address:streetNumber>3a</address:streetNumber>\n" +
                     "    <address:zip>65843</address:zip>\n" +
                     "    <address:city>Sulzbach</address:city>\n" +
                     "</address:address>\n",
                MessageHelper.extractBodyAsString(exchange.getMessage()));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().component("jetty").host("localhost").port(getPort())
                        .bindingMode(RestBindingMode.auto).enableNoContentResponse(true);

                rest("/v1/empty")
                        .get("/country").to("direct:v1country")
                        .get("/address").to("direct:v1address").produces("application/xml").type(Address.class);

                rest("/v2/empty/").enableNoContentResponse(false)
                        .get("/country").to("direct:v2country")
                        .get("/address").to("direct:v2address").produces("application/xml").type(Address.class);

                rest("/v3/empty")
                        .get("/country").to("direct:v3country").enableNoContentResponse(false);

                rest()
                        .get("/country").to("direct:v4country")
                        .get("/address").to("direct:v3address").produces("application/xml").type(Address.class);

                from("direct:v1country").transform().constant(new java.util.ArrayList<CountryPojo>());
                from("direct:v2country").transform().constant(new java.util.ArrayList<CountryPojo>());
                from("direct:v3country").transform().constant(new java.util.ArrayList<CountryPojo>());

                CountryPojo country = new CountryPojo();
                country.setIso("EN");
                country.setCountry("England");
                from("direct:v4country").transform().constant(country);

                Address emptyAddress = new Address();
                from("direct:v1address")
                        .setHeader(JaxbConstants.JAXB_PART_CLASS, simple("org.apache.camel.component.jetty.rest.Address"))
                        .setHeader(JaxbConstants.JAXB_PART_NAMESPACE,
                                simple("{http://www.camel.apache.org/jaxb/example/address/1}address"))
                        .transform()
                        .constant(emptyAddress);

                from("direct:v2address")
                        .setHeader(JaxbConstants.JAXB_PART_CLASS, simple("org.apache.camel.component.jetty.rest.Address"))
                        .setHeader(JaxbConstants.JAXB_PART_NAMESPACE,
                                simple("{http://www.camel.apache.org/jaxb/example/address/1}address"))
                        .transform()
                        .constant(emptyAddress);

                Address address = new Address();
                address.setStreet("Main Street");
                address.setStreetNumber("3a");
                address.setZip("65843");
                address.setCity("Sulzbach");
                from("direct:v3address")
                        .setHeader(JaxbConstants.JAXB_PART_CLASS, simple("org.apache.camel.component.jetty.rest.Address"))
                        .setHeader(JaxbConstants.JAXB_PART_NAMESPACE,
                                simple("{http://www.camel.apache.org/jaxb/example/address/1}address"))
                        .transform()
                        .constant(address);
            }
        };
    }
}
