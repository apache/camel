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
package org.apache.camel.converter.jaxb;

import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxb.address.Address;
import org.apache.camel.converter.jaxb.person.Person;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConcurrentJaxbDataFormatSchemaValidationTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ConcurrentJaxbDataFormatSchemaValidationTest.class);

    @EndpointInject("mock:marshall")
    private MockEndpoint mockMarshall;

    @EndpointInject("mock:unmarshall")
    private MockEndpoint mockUnmarshall;

    private int testCount = 1000;
    private int concurrencyLevel = 10;

    @Test
    public void concurrentMarshallSuccess() throws Exception {
        mockMarshall.expectedMessageCount(testCount);

        Address address = new Address();
        address.setAddressLine1("Hauptstr. 1; 01129 Entenhausen");
        Person person = new Person();
        person.setFirstName("Christian");
        person.setLastName("Mueller");
        person.setAge(Integer.valueOf(36));
        person.setAddress(address);

        StopWatch watch = new StopWatch();
        for (int i = 0; i < testCount; i++) {
            template.sendBody("seda:marshall", person);
        }

        MockEndpoint.assertIsSatisfied(context);
        LOG.info("Validation of {} messages took {} ms", testCount, watch.taken());

        String payload = mockMarshall.getExchanges().get(0).getIn().getBody(String.class);
        LOG.info(payload);

        assertTrue(payload.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"));
        assertTrue(payload.contains(
                "<person xmlns=\"person.jaxb.converter.camel.apache.org\" xmlns:ns2=\"address.jaxb.converter.camel.apache.org\">"));
        assertTrue(payload.contains("<firstName>Christian</firstName>"));
        assertTrue(payload.contains("<lastName>Mueller</lastName>"));
        assertTrue(payload.contains("<age>36</age>"));
        assertTrue(payload.contains("<address>"));
        assertTrue(payload.contains("<ns2:addressLine1>Hauptstr. 1; 01129 Entenhausen</ns2:addressLine1>"));
        assertTrue(payload.contains("</address>"));
        assertTrue(payload.contains("</person>"));
    }

    @Test
    public void concurrentUnmarshall() throws Exception {
        mockUnmarshall.expectedMessageCount(testCount);

        String xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<person xmlns=\"person.jaxb.converter.camel.apache.org\" xmlns:ns2=\"address.jaxb.converter.camel.apache.org\">")
                .append("<firstName>Christian</firstName>")
                .append("<lastName>Mueller</lastName>")
                .append("<age>36</age>")
                .append("<address>")
                .append("<ns2:addressLine1>Hauptstr. 1; 01129 Entenhausen</ns2:addressLine1>")
                .append("</address>")
                .append("</person>")
                .toString();

        StopWatch watch = new StopWatch();
        for (int i = 0; i < testCount; i++) {
            template.sendBody("seda:unmarshall", xml);
        }

        MockEndpoint.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        LOG.info("Validation of {} messages took {} ms", testCount, watch.taken());

        Person person = mockUnmarshall.getExchanges().get(0).getIn().getBody(Person.class);

        assertEquals("Christian", person.getFirstName());
        assertEquals("Mueller", person.getLastName());
        assertEquals(Integer.valueOf(36), person.getAge());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                JaxbDataFormat jaxbDataFormat = new JaxbDataFormat();
                jaxbDataFormat.setContextPath(Person.class.getPackage().getName());
                jaxbDataFormat.setSchema("classpath:person.xsd,classpath:address.xsd");
                jaxbDataFormat.setAccessExternalSchemaProtocols("file");

                from("seda:marshall?concurrentConsumers=" + concurrencyLevel)
                        .marshal(jaxbDataFormat)
                        .to("mock:marshall");

                from("seda:unmarshall?concurrentConsumers=" + concurrencyLevel)
                        .unmarshal(jaxbDataFormat)
                        .to("mock:unmarshall");
            }
        };
    }
}
