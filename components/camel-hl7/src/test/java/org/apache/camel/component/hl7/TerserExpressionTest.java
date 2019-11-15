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

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v24.message.ADT_A01;
import ca.uhn.hl7v2.model.v24.segment.PID;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.hl7.HL7.hl7terser;

public class TerserExpressionTest extends CamelTestSupport {

    private static final String PATIENT_ID = "123456";

    @Test
    public void testTerserExpression() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:test1");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(PATIENT_ID);
        template.sendBody("direct:test1", createADT01Message());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTerserPredicateValue() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:test2");
        mock.expectedMessageCount(1);
        template.sendBody("direct:test2", createADT01Message());
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testTerserPredicateNull() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:test3");
        mock.expectedMessageCount(1);
        template.sendBody("direct:test3", createADT01Message());
        assertMockEndpointsSatisfied();
    }

    @Test(expected = CamelExecutionException.class)
    public void testTerserInvalidExpression() throws Exception {
        template.sendBody("direct:test4", createADT01Message());
    }

    @Test(expected = CamelExecutionException.class)
    public void testTerserInvalidMessage() throws Exception {
        template.sendBody("direct:test4", "text instead of message");
    }

    @Test
    public void testTerserAnnotatedMethod() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:test5");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(PATIENT_ID);
        template.sendBody("direct:test5", createADT01Message());
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        final TerserBean terserBean = new TerserBean();

        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:test1").transform(hl7terser("PID-3-1")).to("mock:test1");
                from("direct:test2").filter(hl7terser("PID-3-1").isEqualTo(PATIENT_ID)).to("mock:test2");
                from("direct:test3").filter(hl7terser("PID-4-1").isNull()).to("mock:test3");
                from("direct:test4").filter(hl7terser("blorg gablorg").isNull()).to("mock:test3");
                from("direct:test5").bean(terserBean).to("mock:test5");
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
        pid.getPatientIdentifierList(0).getID().setValue(PATIENT_ID);

        return adt;
    }

    public class TerserBean {
        public String patientId(@Hl7Terser(value = "PID-3-1") String patientId) {
            return patientId;
        }
    }
}
