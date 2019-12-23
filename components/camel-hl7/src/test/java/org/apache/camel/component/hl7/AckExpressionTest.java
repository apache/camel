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

import ca.uhn.hl7v2.AcknowledgmentCode;
import ca.uhn.hl7v2.ErrorCode;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v24.message.ACK;
import ca.uhn.hl7v2.model.v24.message.ADT_A01;
import ca.uhn.hl7v2.model.v24.segment.PID;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.hl7.HL7.ack;
import static org.apache.camel.component.hl7.HL7.hl7terser;

public class AckExpressionTest extends CamelTestSupport {

    @Test
    public void testAckExpression() throws Exception {
        ADT_A01 a01 = createADT01Message();
        ACK ack = template.requestBody("direct:test1", a01, ACK.class);
        assertEquals("AA", ack.getMSA().getAcknowledgementCode().getValue());
        assertEquals(a01.getMSH().getMessageControlID().getValue(), ack.getMSA().getMessageControlID()
            .getValue());
    }

    @Test
    public void testAckExpressionWithCode() throws Exception {
        ADT_A01 a01 = createADT01Message();
        ACK ack = template.requestBody("direct:test2", a01, ACK.class);
        assertEquals("CA", ack.getMSA().getAcknowledgementCode().getValue());
        assertEquals(a01.getMSH().getMessageControlID().getValue(), ack.getMSA().getMessageControlID()
            .getValue());
    }

    @Test
    public void testNakExpression() throws Exception {
        ADT_A01 a01 = createADT01Message();
        ACK ack = template.requestBody("direct:test3", a01, ACK.class);
        assertEquals("AE", ack.getMSA().getAcknowledgementCode().getValue());
        assertEquals(a01.getMSH().getMessageControlID().getValue(), ack.getMSA().getMessageControlID()
            .getValue());
        assertEquals(String.valueOf(ErrorCode.APPLICATION_INTERNAL_ERROR.getCode()), ack.getERR()
            .getErrorCodeAndLocation(0).getCodeIdentifyingError().getIdentifier().getValue());
    }

    @Test
    public void testNakExpressionWithParameters() throws Exception {
        ADT_A01 a01 = createADT01Message();
        ACK ack = template.requestBody("direct:test4", a01, ACK.class);
        assertEquals("AR", ack.getMSA().getAcknowledgementCode().getValue());
        assertEquals(a01.getMSH().getMessageControlID().getValue(), ack.getMSA().getMessageControlID()
            .getValue());
        assertEquals(String.valueOf(ErrorCode.APPLICATION_INTERNAL_ERROR.getCode()), ack.getERR()
            .getErrorCodeAndLocation(0).getCodeIdentifyingError().getIdentifier().getValue());
        assertEquals("Problem!", ack.getERR().getErrorCodeAndLocation(0).getCodeIdentifyingError()
            .getAlternateText().getValue());
    }

    @Test
    public void testNakExpressionWithoutException() throws Exception {
        ADT_A01 a01 = createADT01Message();
        ACK ack = template.requestBody("direct:test5", a01, ACK.class);
        assertEquals("AR", ack.getMSA().getAcknowledgementCode().getValue());
        assertEquals(a01.getMSH().getMessageControlID().getValue(), ack.getMSA().getMessageControlID()
            .getValue());
        assertEquals(String.valueOf(ErrorCode.DATA_TYPE_ERROR.getCode()), ack.getERR().getErrorCodeAndLocation(0)
            .getCodeIdentifyingError().getIdentifier().getValue());
        assertEquals("Problem!", ack.getERR().getErrorCodeAndLocation(0).getCodeIdentifyingError()
            .getAlternateText().getValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:test1").transform(ack());
                from("direct:test2").transform(ack(AcknowledgmentCode.CA));
                from("direct:test3").onException(HL7Exception.class).handled(true).transform(ack()).end()
                    .transform(hl7terser("/.BLORG"));
                from("direct:test4").onException(HL7Exception.class).handled(true)
                    .transform(ack(AcknowledgmentCode.AR, "Problem!", ErrorCode.APPLICATION_INTERNAL_ERROR)).end()
                    .transform(hl7terser("/.BLORG"));
                from("direct:test5").transform(ack(AcknowledgmentCode.AR, "Problem!", ErrorCode.DATA_TYPE_ERROR));
            }
        };
    }

    private static ADT_A01 createADT01Message() throws Exception {
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
