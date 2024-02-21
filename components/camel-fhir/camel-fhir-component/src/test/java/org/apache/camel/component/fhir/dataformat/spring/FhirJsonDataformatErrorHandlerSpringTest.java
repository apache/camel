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
package org.apache.camel.component.fhir.dataformat.spring;

import ca.uhn.fhir.parser.DataFormatException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class FhirJsonDataformatErrorHandlerSpringTest extends CamelSpringTestSupport {

    private static final String INPUT
            = "{\"resourceType\":\"Patient\",\"extension\":[ {\"valueDateTime\":\"2011-01-02T11:13:15\"} ]}";
    private MockEndpoint mockEndpoint;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        mockEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
    }

    @Test
    public void unmarshalParserErrorHandler() {
        try {
            template.sendBody("direct:unmarshalErrorHandlerStrict", INPUT);
            fail("Expected a DataFormatException");
        } catch (CamelExecutionException e) {
            assertTrue(e.getCause() instanceof DataFormatException);
        }
    }

    @Test
    public void unmarshalLenientErrorHandler() throws Exception {
        mockEndpoint.expectedMessageCount(1);

        template.sendBody("direct:unmarshalErrorHandlerLenient", INPUT);

        mockEndpoint.assertIsSatisfied();

        Exchange exchange = mockEndpoint.getExchanges().get(0);
        Patient patient = (Patient) exchange.getIn().getBody();
        assertEquals(1, patient.getExtension().size());
        assertNull(patient.getExtension().get(0).getUrl());
        assertEquals("2011-01-02T11:13:15", patient.getExtension().get(0).getValueAsPrimitive().getValueAsString());
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "org/apache/camel/dataformat/fhir/json/FhirJsonDataFormatErrorHandlerSpringTest.xml");
    }
}
