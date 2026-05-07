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
package org.apache.camel.component.servlet.rest;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.servlet.ServletCamelRouterTestSupport;
import org.apache.camel.converter.jaxb.JaxbConstants;
import org.apache.camel.model.rest.RestBindingMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RestServletNoContentRestConfigTest extends ServletCamelRouterTestSupport {

    @Test
    public void testEmptyJson204ConfigNoContentEnabled() throws Exception {
        WebRequest req = new GetMethodWebRequest(contextUrl + "/services/v1/empty/country");
        WebResponse response = query(req, false);

        assertEquals(204, response.getResponseCode());
        assertTrue(response.getText().isEmpty());
    }

    @Test
    public void testEmptyXml204ConfigNoContentEnabled() throws Exception {
        WebRequest req = new GetMethodWebRequest(contextUrl + "/services/v1/empty/address");
        WebResponse response = query(req, false);

        assertEquals(204, response.getResponseCode());
        assertTrue(response.getText().isEmpty());
    }

    @Test
    public void testEmptyJson200RestConfigNoContentDisabled() throws Exception {
        WebRequest req = new GetMethodWebRequest(contextUrl + "/services/v2/empty/country");
        WebResponse response = query(req, false);

        assertEquals(200, response.getResponseCode());
        assertTrue(response.getText().equals("[]"));
    }

    @Test
    public void testEmptyXml200RestConfigNoContentDisabled() throws Exception {
        WebRequest req = new GetMethodWebRequest(contextUrl + "/services/v2/empty/address");
        WebResponse response = query(req, false);

        assertEquals(200, response.getResponseCode());
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                     "<address:address xmlns:address=\"http://www.camel.apache.org/jaxb/example/address/1\"/>\n",
                response.getText());
    }

    @Test
    public void testEmpty200VerbNoContentDisabled() throws Exception {
        WebRequest req = new GetMethodWebRequest(contextUrl + "/services/v3/empty/country");
        WebResponse response = query(req, false);

        assertEquals(200, response.getResponseCode());
        assertTrue(response.getText().equals("[]"));
    }

    @Test
    public void testJson200ConfigNoContentEnabled() throws Exception {
        WebRequest req = new GetMethodWebRequest(contextUrl + "/services/country");
        WebResponse response = query(req, false);

        assertEquals(200, response.getResponseCode());
        assertEquals("{\"iso\":\"EN\",\"country\":\"England\"}",
                response.getText());
    }

    @Test
    public void testXml200ConfigNoContentEnabled() throws Exception {
        WebRequest req = new GetMethodWebRequest(contextUrl + "/services/address");
        WebResponse response = query(req, false);

        assertEquals(200, response.getResponseCode());
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                     "<address:address xmlns:address=\"http://www.camel.apache.org/jaxb/example/address/1\">\n" +
                     "    <address:street>Main Street</address:street>\n" +
                     "    <address:streetNumber>3a</address:streetNumber>\n" +
                     "    <address:zip>65843</address:zip>\n" +
                     "    <address:city>Sulzbach</address:city>\n" +
                     "</address:address>\n",
                response.getText());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().component("servlet").host("localhost")
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
                        .setHeader(JaxbConstants.JAXB_PART_CLASS, simple("org.apache.camel.component.servlet.rest.Address"))
                        .setHeader(JaxbConstants.JAXB_PART_NAMESPACE,
                                simple("{http://www.camel.apache.org/jaxb/example/address/1}address"))
                        .transform()
                        .constant(emptyAddress);

                from("direct:v2address")
                        .setHeader(JaxbConstants.JAXB_PART_CLASS, simple("org.apache.camel.component.servlet.rest.Address"))
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
                        .setHeader(JaxbConstants.JAXB_PART_CLASS, simple("org.apache.camel.component.servlet.rest.Address"))
                        .setHeader(JaxbConstants.JAXB_PART_NAMESPACE,
                                simple("{http://www.camel.apache.org/jaxb/example/address/1}address"))
                        .transform()
                        .constant(address);
            }
        };
    }
}
