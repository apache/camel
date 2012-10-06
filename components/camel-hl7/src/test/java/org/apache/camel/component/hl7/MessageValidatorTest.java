/**
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

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v24.message.ADT_A01;
import ca.uhn.hl7v2.model.v24.segment.PID;
import ca.uhn.hl7v2.validation.MessageRule;
import ca.uhn.hl7v2.validation.ValidationContext;
import ca.uhn.hl7v2.validation.ValidationException;
import ca.uhn.hl7v2.validation.impl.DefaultValidation;
import ca.uhn.hl7v2.validation.impl.MessageRuleBinding;
import ca.uhn.hl7v2.validation.impl.ValidationContextImpl;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.hl7.HL7.messageConformsTo;

public class MessageValidatorTest extends CamelTestSupport {

    private static final ValidationException[] VALIDATION_OK = new ValidationException[0];
    private ValidationContext defaultContext;
    private ValidationContextImpl customContext;

    @Override
    protected void doPreSetup() throws Exception {
        defaultContext = new DefaultValidation();
        customContext = new DefaultValidation();
        @SuppressWarnings("serial")
        MessageRule rule = new MessageRule() {

            @Override
            public String getDescription() {
                return null;
            }

            @Override
            public String getSectionReference() {
                return null;
            }

            @Override
            public ValidationException[] test(Message msg) {
                ADT_A01 a01 = (ADT_A01)msg;
                if (a01.getPID().getAdministrativeSex().getValue() == null) {
                    ValidationException[] e = new ValidationException[1];
                    e[0] = new ValidationException("No gender provided!");
                    return e;
                }
                return VALIDATION_OK;
            }

        };
        MessageRuleBinding binding = new MessageRuleBinding("2.4", "ADT", "A01", rule);
        customContext.getMessageRuleBindings().add(binding);
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
        template.sendBodyAndHeader("direct:test3", msg, "validator", defaultContext);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:test1").validate(messageConformsTo(defaultContext)).to("mock:test1");
                from("direct:test2").validate(messageConformsTo(customContext)).to("mock:test2");
                from("direct:test3").validate(messageConformsTo(header("validator"))).to("mock:test3");
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
