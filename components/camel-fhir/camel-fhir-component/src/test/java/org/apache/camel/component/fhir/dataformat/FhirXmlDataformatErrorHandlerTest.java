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
package org.apache.camel.component.fhir.dataformat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParserErrorHandler;
import ca.uhn.fhir.parser.LenientErrorHandler;
import ca.uhn.fhir.parser.StrictErrorHandler;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.fhir.FhirXmlDataFormat;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.hl7.fhir.dstu3.model.Patient;
import org.junit.Before;
import org.junit.Test;

public class FhirXmlDataformatErrorHandlerTest extends CamelTestSupport {

    private static final String INPUT = "<Patient><active value=\"true\"/><active value=\"false\"/></Patient>";

    private MockEndpoint mockEndpoint;
    private final FhirContext fhirContext = FhirContext.forDstu3();

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mockEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
    }

    @Test(expected = DataFormatException.class)
    public void unmarshalParserErrorHandler() throws Throwable {
        try {
            template.sendBody("direct:unmarshalErrorHandlerStrict", INPUT);
        } catch (CamelExecutionException e) {
            throw e.getCause();
        }
    }

    @Test
    public void unmarshalLenientErrorHandler() throws Exception {
        mockEndpoint.expectedMessageCount(1);

        template.sendBody("direct:unmarshalErrorHandlerLenient", INPUT);

        mockEndpoint.assertIsSatisfied();

        Exchange exchange = mockEndpoint.getExchanges().get(0);
        Patient patient = (Patient) exchange.getIn().getBody();
        assertEquals(true, patient.getActive());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                FhirXmlDataFormat strickErrorHandlerDataformat = getStrictErrorHandlerDataFormat();
                FhirXmlDataFormat lenientErrorHandlerDataFormat = getLenientErrorHandlerDataFormat();

                from("direct:unmarshalErrorHandlerStrict")
                        .unmarshal(strickErrorHandlerDataformat)
                        .to("mock:errorIsThrown");

                from("direct:unmarshalErrorHandlerLenient")
                        .unmarshal(lenientErrorHandlerDataFormat)
                        .to("mock:result");
            }

            private FhirXmlDataFormat getStrictErrorHandlerDataFormat() {
                FhirXmlDataFormat fhirXmlDataFormat = new FhirXmlDataFormat();
                fhirXmlDataFormat.setFhirContext(fhirContext);
                IParserErrorHandler parserErrorHandler = new StrictErrorHandler();
                fhirXmlDataFormat.setParserErrorHandler(parserErrorHandler);
                return fhirXmlDataFormat;
            }

            private FhirXmlDataFormat getLenientErrorHandlerDataFormat() {
                FhirXmlDataFormat fhirXmlDataFormat = new FhirXmlDataFormat();
                fhirXmlDataFormat.setFhirContext(fhirContext);
                IParserErrorHandler parserErrorHandler = new LenientErrorHandler();
                fhirXmlDataFormat.setParserErrorHandler(parserErrorHandler);
                return fhirXmlDataFormat;
            }

        };
    }
}
