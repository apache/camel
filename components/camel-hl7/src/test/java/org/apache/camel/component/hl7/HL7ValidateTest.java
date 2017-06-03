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

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.Version;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v24.message.ADT_A01;
import ca.uhn.hl7v2.model.v24.segment.PID;
import ca.uhn.hl7v2.parser.GenericParser;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.validation.ValidationContext;
import ca.uhn.hl7v2.validation.ValidationException;
import ca.uhn.hl7v2.validation.builder.ValidationRuleBuilder;
import ca.uhn.hl7v2.validation.impl.NoValidation;
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class HL7ValidateTest extends CamelTestSupport {

    private HL7DataFormat hl7;

    @Test
    public void testUnmarshalFailed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(0);

        String body = createHL7AsString();
        try {
            template.sendBody("direct:unmarshalFailed", body);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(HL7Exception.class, e.getCause());
            assertIsInstanceOf(DataTypeException.class, e.getCause());
            assertTrue("Should be a validation error message", e.getCause().getMessage().startsWith("ca.uhn.hl7v2.validation.ValidationException: Validation failed:"));
        }

        assertMockEndpointsSatisfied();
    }
        

    @Test
    public void testUnmarshalOk() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(1);

        String body = createHL7AsString();
        template.sendBody("direct:unmarshalOk", body);

        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testUnmarshalOkCustom() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(1);

        String body = createHL7AsString();
        template.sendBody("direct:unmarshalOkCustom", body);

        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testMarshalWithValidation() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedMessageCount(0);

        Message message = createADT01Message();
        try {
            template.sendBody("direct:start1", message);
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            assertIsInstanceOf(HL7Exception.class, e.getCause());
            assertIsInstanceOf(ValidationException.class, e.getCause().getCause());
            System.out.println(e.getCause().getCause().getMessage());
            assertTrue("Should be a validation error message", e.getCause().getCause().getMessage().startsWith("Validation failed:"));
        }

        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testMarshalWithoutValidation() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedMessageCount(1);

        Message message = createADT01Message();
        template.sendBody("direct:start2", message);

        assertMockEndpointsSatisfied();
    }
    
    protected RouteBuilder createRouteBuilder() throws Exception {
        HapiContext hapiContext = new DefaultHapiContext();
        hapiContext.setValidationContext(new NoValidation());
        Parser p = new GenericParser(hapiContext);
        hl7 = new HL7DataFormat();
        hl7.setParser(p);
        
        /*
         * Let's start by adding a validation rule to the default validation
         * that disallows PID-2 to be empty.
         */
        ValidationRuleBuilder builder = new ValidationRuleBuilder() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void configure() {
                forVersion(Version.V24)
                        .message("ADT", "*")
                        .terser("PID-2", not(empty()));
            }
        };
        ValidationContext customValidationContext = ValidationContextFactory.fromBuilder(builder);
        
        HapiContext customContext = new DefaultHapiContext(customValidationContext);
        final Parser customParser = new GenericParser(customContext);
        
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:unmarshalFailed").unmarshal().hl7().to("mock:unmarshal");
                from("direct:unmarshalOk").unmarshal().hl7(false).to("mock:unmarshal");
                from("direct:unmarshalOkCustom").unmarshal(hl7).to("mock:unmarshal");
                from("direct:start1").marshal().hl7(customParser).to("mock:end");
                from("direct:start2").marshal().hl7(true).to("mock:end");
                
            }
        };
    }

    private static String createHL7AsString() {
        String line1 = "MSH|^~\\&|REQUESTING|ICE|INHOUSE|RTH00|20080808093202||ORM^O01|0808080932027444985|P|2.4|||AL|NE|||";
        String line2 = "PID|1||ICE999999^^^ICE^ICE||Testpatient^Testy^^^Mr||19740401|M|||123 Barrel Drive^^^^SW18 4RT|||||2||||||||||||||";
        String line3 = "NTE|1||Free text for entering clinical details|";
        String line4 = "PV1|1||^^^^^^^^Admin Location|||||||||||||||NHS|";
        String line5 = "ORC|NW|213||175|REQ||||20080808093202|ahsl^^Administrator||G999999^TestDoctor^GPtests^^^^^^NAT|^^^^^^^^Admin Location | 819600|200808080932||RTH00||ahsl^^Administrator||";
        String line6 = "OBR|1|213||CCOR^Serum Cortisol ^ JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||";
        String line7 = "OBR|2|213||GCU^Serum Copper ^ JRH06 |||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||";
        String line8 = "OBR|3|213||THYG^Serum Thyroglobulin ^JRH06|||200808080932||0.100||||||^|G999999^TestDoctor^GPtests^^^^^^NAT|819600|ADM162||||||820|||^^^^^R||||||||";

        StringBuilder body = new StringBuilder();
        body.append(line1);
        body.append("\r");
        body.append(line2);
        body.append("\r");
        body.append(line3);
        body.append("\r");
        body.append(line4);
        body.append("\r");
        body.append(line5);
        body.append("\r");
        body.append(line6);
        body.append("\r");
        body.append(line7);
        body.append("\r");
        body.append(line8);
        return body.toString();
    }
    
    private static Message createADT01Message() throws Exception {
        ADT_A01 adt = new ADT_A01();
        adt.initQuickstart("ADT", "A01", "P");

        // Populate the PID Segment
        PID pid = adt.getPID();
        pid.getPatientName(0).getFamilyName().getSurname().setValue("Doe");
        pid.getPatientName(0).getGivenName().setValue("John");
        pid.getPhoneNumberBusiness(0).getPhoneNumber().setValue("333123456");
        pid.getPatientIdentifierList(0).getID().setValue("123456");
       
        return adt;
    }

}
