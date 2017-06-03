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
import ca.uhn.hl7v2.model.v24.message.ADR_A19;
import ca.uhn.hl7v2.model.v24.message.ADT_A01;
import ca.uhn.hl7v2.model.v24.message.QRY_A19;
import ca.uhn.hl7v2.model.v24.segment.MSA;
import ca.uhn.hl7v2.model.v24.segment.MSH;
import ca.uhn.hl7v2.model.v24.segment.PID;
import ca.uhn.hl7v2.model.v24.segment.QRD;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.spi.DataFormat;
import org.junit.Test;

/**
 * Unit test for HL7 routing.
 */
public class HL7RouteTest extends HL7TestSupport {

    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        HL7MLLPCodec codec = new HL7MLLPCodec();
        codec.setCharset("iso-8859-1");

        jndi.bind("hl7codec", codec);

        MyHL7BusinessLogic logic = new MyHL7BusinessLogic();
        jndi.bind("hl7service", logic);

        return jndi;
    }

    @Test
    public void testSendA19() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:a19");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Message.class);

        String line1 = "MSH|^~\\&|MYSENDER|MYSENDERAPP|MYCLIENT|MYCLIENTAPP|200612211200||QRY^A19|1234|P|2.4";
        String line2 = "QRD|200612211200|R|I|GetPatient|||1^RD|0101701234|DEM||";

        StringBuilder in = new StringBuilder();
        in.append(line1);
        in.append("\r");
        in.append(line2);

        String out = template.requestBody("mina2:tcp://127.0.0.1:" + getPort() + "?sync=true&codec=#hl7codec", in.toString(), String.class);

        String[] lines = out.split("\r");
        assertEquals("MSH|^~\\&|MYSENDER||||200701011539||ADR^A19||||123", lines[0]);
        assertEquals("MSA|AA|123", lines[1]);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendA01() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:a01");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Message.class);

        String line1 = "MSH|^~\\&|MYSENDER|MYSENDERAPP|MYCLIENT|MYCLIENTAPP|200612211200||ADT^A01|123|P|2.4";
        String line2 = "PID|||123456||Doe^John";

        StringBuilder in = new StringBuilder();
        in.append(line1);
        in.append("\r");
        in.append(line2);

        String out = template.requestBody("mina2:tcp://127.0.0.1:" + getPort() + "?sync=true&codec=#hl7codec", in.toString(), String.class);
        String[] lines = out.split("\r");
        assertEquals("MSH|^~\\&|MYSENDER||||200701011539||ADT^A01||||123", lines[0]);
        assertEquals("PID|||123||Doe^John", lines[1]);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendUnknown() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unknown");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Message.class);

        String line1 = "MSH|^~\\&|MYSENDER|MYSENDERAPP|MYCLIENT|MYCLIENTAPP|200612211200||ADT^A02|1234|P|2.4";
        String line2 = "PID|||123456||Doe^John";

        StringBuilder in = new StringBuilder();
        in.append(line1);
        in.append("\r");
        in.append(line2);

        template.requestBody("mina2:tcp://127.0.0.1:" + getPort() + "?sync=true&codec=#hl7codec", in.toString());

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                DataFormat hl7 = new HL7DataFormat();
                // we setup or HL7 listener on port 8888 (using the hl7codec) and in sync mode so we can return a response
                from("mina2:tcp://127.0.0.1:" + getPort() + "?sync=true&codec=#hl7codec")
                    // we use the HL7 data format to unmarshal from HL7 stream to the HAPI Message model
                    // this ensures that the camel message has been enriched with hl7 specific headers to
                    // make the routing much easier (see below)
                    .unmarshal(hl7)
                    // using choice as the content base router
                    .choice()
                        // where we choose that A19 queries invoke the handleA19 method on our hl7service bean
                        .when(header("CamelHL7TriggerEvent").isEqualTo("A19"))
                            .bean("hl7service", "handleA19")
                            .to("mock:a19")
                        // and A01 should invoke the handleA01 method on our hl7service bean
                        .when(header("CamelHL7TriggerEvent").isEqualTo("A01")).to("mock:a01")
                            .bean("hl7service", "handleA01")
                            .to("mock:a19")
                        // other types should go to mock:unknown
                        .otherwise()
                            .to("mock:unknown")
                    // end choice block
                    .end()
                    // marshal response back
                    .marshal(hl7);
                // END SNIPPET: e1
            }
        };
    }

    // START SNIPPET: e2
    public class MyHL7BusinessLogic {

        // This is a plain POJO that has NO imports whatsoever on Apache Camel.
        // its a plain POJO only importing the HAPI library so we can much easier work with the HL7 format.

        public Message handleA19(Message msg) throws Exception {
            // here you can have your business logic for A19 messages
            assertTrue(msg instanceof QRY_A19);
            // just return the same dummy response
            return createADR19Message();
        }

        public Message handleA01(Message msg) throws Exception {
            // here you can have your business logic for A01 messages
            assertTrue(msg instanceof ADT_A01);
            // just return the same dummy response
            return createADT01Message(((ADT_A01)msg).getMSH().getMessageControlID().getValue());
        }
    }
    // END SNIPPET: e2

    private static Message createADR19Message() throws Exception {
        ADR_A19 adr = new ADR_A19();

        // Populate the MSH Segment
        MSH mshSegment = adr.getMSH();
        mshSegment.getFieldSeparator().setValue("|");
        mshSegment.getEncodingCharacters().setValue("^~\\&");
        mshSegment.getDateTimeOfMessage().getTimeOfAnEvent().setValue("200701011539");
        mshSegment.getSendingApplication().getNamespaceID().setValue("MYSENDER");
        mshSegment.getSequenceNumber().setValue("123");
        mshSegment.getMessageType().getMessageType().setValue("ADR");
        mshSegment.getMessageType().getTriggerEvent().setValue("A19");

        // Populate the PID Segment
        MSA msa = adr.getMSA();
        msa.getAcknowledgementCode().setValue("AA");
        msa.getMessageControlID().setValue("123");

        QRD qrd = adr.getQRD();
        qrd.getQueryDateTime().getTimeOfAnEvent().setValue("20080805120000");

        return adr.getMessage();
    }

    private static Message createADT01Message(String msgId) throws Exception {
        ADT_A01 adt = new ADT_A01();

        // Populate the MSH Segment
        MSH mshSegment = adt.getMSH();
        mshSegment.getFieldSeparator().setValue("|");
        mshSegment.getEncodingCharacters().setValue("^~\\&");
        mshSegment.getDateTimeOfMessage().getTimeOfAnEvent().setValue("200701011539");
        mshSegment.getSendingApplication().getNamespaceID().setValue("MYSENDER");
        mshSegment.getSequenceNumber().setValue("123");
        mshSegment.getMessageType().getMessageType().setValue("ADT");
        mshSegment.getMessageType().getTriggerEvent().setValue("A01");

        // Populate the PID Segment
        PID pid = adt.getPID();
        pid.getPatientName(0).getFamilyName().getSurname().setValue("Doe");
        pid.getPatientName(0).getGivenName().setValue("John");
        pid.getPatientIdentifierList(0).getID().setValue(msgId);

        return adt;
    }

}
