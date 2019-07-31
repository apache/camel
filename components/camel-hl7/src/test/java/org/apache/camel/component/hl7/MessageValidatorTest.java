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
package org.apache.camel.component.hl7;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.Version;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v24.message.ADT_A01;
import ca.uhn.hl7v2.model.v24.segment.PID;
import ca.uhn.hl7v2.validation.ValidationContext;
import ca.uhn.hl7v2.validation.builder.ValidationRuleBuilder;
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.hl7.HL7.messageConforms;
import static org.apache.camel.component.hl7.HL7.messageConformsTo;

public class MessageValidatorTest extends CamelTestSupport {

    private ValidationContext defaultValidationContext;
    private ValidationContext customValidationContext;
    private HapiContext defaultContext;
    private HapiContext customContext;

    @Override
    protected void doPreSetup() throws Exception {
        defaultValidationContext = ValidationContextFactory.defaultValidation();
        defaultContext = new DefaultHapiContext(defaultValidationContext);
        // we validate separately, not during parsing or rendering
        defaultContext.getParserConfiguration().setValidating(false);

        ValidationRuleBuilder builder = new ValidationRuleBuilder() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void configure() {
                forVersion(Version.V24)
                        .message("ADT", "A01")
                        .terser("PID-8", not(empty()));
            }
        };
        customValidationContext = ValidationContextFactory.fromBuilder(builder);
        customContext = new DefaultHapiContext(customValidationContext);
        // we validate separately, not during parsing or rendering
        customContext.getParserConfiguration().setValidating(false);
    }

    @Test
    public void testDefaultHapiContext() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:test4");
        mock.expectedMessageCount(1);
        Message msg = createADT01Message();
        template.sendBody("direct:test4", msg);
        assertMockEndpointsSatisfied();
    }

    @Test(expected = CamelExecutionException.class)
    public void testCustomHapiContext() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:test5");
        mock.expectedMessageCount(0);
        Message msg = createADT01Message();
        template.sendBody("direct:test5", msg);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDefaultValidationContext() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:test1");
        mock.expectedMessageCount(1);
        Message msg = createADT01Message();
        template.sendBody("direct:test1", msg);
        assertMockEndpointsSatisfied();
    }

    @Test(expected = CamelExecutionException.class)
    public void testCustomValidationContext() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:test2");
        mock.expectedMessageCount(0);
        Message msg = createADT01Message();
        template.sendBody("direct:test2", msg);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDynamicCustomValidationContext() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:test3");
        mock.expectedMessageCount(1);
        Message msg = createADT01Message();
        template.sendBodyAndHeader("direct:test3", msg, "validator", defaultValidationContext);
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testDynamicDefaultHapiContext() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:test6");
        mock.expectedMessageCount(1);
        Message msg = createADT01Message();
        msg.setParser(defaultContext.getPipeParser());
        template.sendBody("direct:test6", msg);
        assertMockEndpointsSatisfied();
    }

    @Test(expected = CamelExecutionException.class)
    public void testDynamicCustomHapiContext() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:test6");
        mock.expectedMessageCount(0);
        Message msg = createADT01Message();
        msg.setParser(customContext.getPipeParser());
        template.sendBody("direct:test6", msg);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:test1").validate(messageConformsTo(defaultValidationContext)).to("mock:test1");
                from("direct:test2").validate(messageConformsTo(customValidationContext)).to("mock:test2");
                from("direct:test3").validate(messageConformsTo(header("validator"))).to("mock:test3");
                from("direct:test4").validate(messageConformsTo(defaultContext)).to("mock:test4");
                from("direct:test5").validate(messageConformsTo(customContext)).to("mock:test5");
                from("direct:test6").validate(messageConforms()).to("mock:test6");
            }
        };
    }

    private static Message createADT01Message() throws Exception {
        ADT_A01 adt = new ADT_A01();
        adt.initQuickstart("ADT", "A01", "P");

        // Populate the PID Segment
        PID pid = adt.getPID();
        pid.getPatientName(0).getFamilyName().getSurname().setValue("Doe");
        pid.getPatientName(0).getGivenName().setValue("John");
        pid.getPatientIdentifierList(0).getID().setValue("123456");

        return adt;
    }
}
