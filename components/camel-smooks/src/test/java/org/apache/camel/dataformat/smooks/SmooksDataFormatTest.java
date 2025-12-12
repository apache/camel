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
package org.apache.camel.dataformat.smooks;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.smooks.gender.Gender;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.processor.MarshalProcessor;
import org.apache.camel.support.processor.UnmarshalProcessor;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.smooks.io.source.JavaSource;
import org.smooks.support.StreamUtils;
import org.xmlunit.builder.DiffBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SmooksDataFormatTest extends CamelTestSupport {
    private static final String SMOOKS_CONFIG = "/smooks-config.xml";
    private static final String CUSTOMER_XML = "/xml/customer.xml";
    private static final String EXPECTED_CUSTOMER_XML = "/xml/expected-customer.xml";
    private DefaultCamelContext camelContext;
    private SmooksDataFormat dataFormatter;

    public SmooksDataFormatTest() {
        super();
        testConfigurationBuilder.withUseRouteBuilder(false);
    }

    @BeforeEach
    public void beforeEach() {
        camelContext = new DefaultCamelContext();
        dataFormatter = new SmooksDataFormat();
        dataFormatter.setSmooksConfig(SMOOKS_CONFIG);
        dataFormatter.setCamelContext(camelContext);
        dataFormatter.start();
    }

    @AfterEach
    public void afterEach() throws IOException {
        dataFormatter.stop();
        camelContext.stop();
        camelContext.close();
    }

    @Test
    public void unmarshal() throws Exception {
        @SuppressWarnings("resource")
        // NOTE: resource will be closed by the context
        final UnmarshalProcessor unmarshalProcessor = new UnmarshalProcessor(dataFormatter);
        final DefaultExchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody(getCustomerInputStream(CUSTOMER_XML));

        unmarshalProcessor.process(exchange);

        assertEquals(Customer.class, exchange.getOut().getBody().getClass());
    }

    @Test
    public void marshal() throws Exception {
        @SuppressWarnings("resource")
        // NOTE: resource will be closed by the context
        final MarshalProcessor marshalProcessor = new MarshalProcessor(dataFormatter);
        final DefaultExchange exchange = new DefaultExchange(camelContext);
        final Customer customer = new Customer();
        customer.setFirstName("John");
        customer.setLastName("Cocktolstol");
        customer.setGender(Gender.Male);
        customer.setAge(35);
        customer.setCountry("Wonderland");

        exchange.getIn().setBody(customer, JavaSource.class);

        marshalProcessor.process(exchange);

        assertFalse(DiffBuilder.compare(getCustomerXml(EXPECTED_CUSTOMER_XML)).withTest(exchange.getOut().getBody(String.class))
                .ignoreComments().ignoreWhitespace().build().hasDifferences());
    }

    @Test
    public void unmarshalMarshalThroughCamel() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a")
                        .unmarshal().smooks(SMOOKS_CONFIG)
                        .marshal().smooks(SMOOKS_CONFIG);
            }
        });

        context.start();

        final Exchange exchange
                = template.request("direct:a", e -> e.getIn().setBody(getCustomerInputStream(CUSTOMER_XML)));

        assertFalse(
                DiffBuilder.compare(getCustomerXml(EXPECTED_CUSTOMER_XML)).withTest(exchange.getMessage().getBody(String.class))
                        .ignoreComments().ignoreWhitespace().build().hasDifferences());
    }

    private InputStream getCustomerInputStream(final String resource) {
        return getClass().getResourceAsStream(resource);
    }

    private String getCustomerXml(final String resource) throws IOException {
        return StreamUtils.readStream(new InputStreamReader(getCustomerInputStream(resource)));
    }

}
