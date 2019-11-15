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
package org.apache.camel.test.cxf.blueprint;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.ProcessingException;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.cxf.jaxrs.CxfRsEndpoint;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactoryBean;
import org.junit.Assert;
import org.junit.Test;

public class CxfRsEndpointBeansTest extends CamelBlueprintTestSupport {

    @Produce("direct:startURLOverride")
    private ProducerTemplate pT;

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/cxf/blueprint/CxfRsEndpointBeans.xml";
    }

    @Override
    protected String getBundleDirectives() {
        return "blueprint.aries.xml-validation:=false";
    }

    @Test
    public void testCxfBusInjection() {

        CxfRsEndpoint serviceEndpoint = context.getEndpoint("cxfrs:bean:serviceEndpoint", CxfRsEndpoint.class);
        CxfRsEndpoint routerEndpoint = context.getEndpoint("cxfrs:bean:routerEndpoint", CxfRsEndpoint.class);
        JAXRSServerFactoryBean server = routerEndpoint.createJAXRSServerFactoryBean();
        JAXRSClientFactoryBean client = serviceEndpoint.createJAXRSClientFactoryBean();
        assertEquals("These cxfrs endpoints don't share the same bus", server.getBus().getId(), client.getBus().getId());
    }

    @Test
    public void testDestinationOverrideURLHandling() {

        try {
            context.getRouteController().startRoute("url-override-route");
        } catch (Exception e) {
            fail(e.getMessage());
        }

        List<String> expected = Arrays.asList(
                                              "foo1",
                                              "foo2",
                                              "foo1",
                                              "foo2",
                                              "foo1");

        expected.forEach(host -> pT.send(exchange -> {
            Message in = exchange.getIn();
            in.setHeader(CxfConstants.CAMEL_CXF_RS_USING_HTTP_API, false);
            in.setHeader(CxfConstants.OPERATION_NAME, "getCustomer");
            in.setBody("Scott");
            in.setHeader(Exchange.ACCEPT_CONTENT_TYPE, "application/json");
            in.setHeader(Exchange.DESTINATION_OVERRIDE_URL, "http://" + host);
            in.setHeader(Exchange.HTTP_METHOD, "GET");
        }));

        MockEndpoint mockEndpoint = getMockEndpoint("mock:resultURLOverride");
        Assert.assertArrayEquals(expected.toArray(),
                                 mockEndpoint.getExchanges().stream()
                                     .map(exchange -> exchange.getProperty(Exchange.EXCEPTION_CAUGHT, ProcessingException.class).getCause().toString())
                                     .map(exceptionMessage -> exceptionMessage.split("\\: ")[1])
                                     .collect(Collectors.toList()).toArray());

    }

}
