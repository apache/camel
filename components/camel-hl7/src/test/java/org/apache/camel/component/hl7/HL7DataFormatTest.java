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
import ca.uhn.hl7v2.model.v24.segment.MSA;
import ca.uhn.hl7v2.model.v24.segment.MSH;
import ca.uhn.hl7v2.model.v24.segment.QRD;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for HL7 DataFormat.
 */
public class HL7DataFormatTest extends ContextTestSupport {

    public void testMarshal() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:marshal");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(byte[].class);
        mock.message(0).bodyAs(String.class).contains("MSA|AA|123");
        mock.message(0).bodyAs(String.class).contains("QRD|20080805120000");

        Message message = createHL7AsMessage();
        template.sendBody("direct:marshal", message);

        assertMockEndpointsSatisfied();
    }

    public void testUnmarshal() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Message.class);

        mock.expectedHeaderReceived(HL7Constants.HL7_SENDING_APPLICATION, "MYSERVER");
        mock.expectedHeaderReceived(HL7Constants.HL7_SENDING_FACILITY, "MYSENDERAPP");
        mock.expectedHeaderReceived(HL7Constants.HL7_RECEIVING_APPLICATION, "MYCLIENT");
        mock.expectedHeaderReceived(HL7Constants.HL7_RECEIVING_FACILITY, "MYCLIENTAPP");
        mock.expectedHeaderReceived(HL7Constants.HL7_TIMESTAMP, "200612211200");
        mock.expectedHeaderReceived(HL7Constants.HL7_SECURITY, null);
        mock.expectedHeaderReceived(HL7Constants.HL7_MESSAGE_TYPE, "QRY");
        mock.expectedHeaderReceived(HL7Constants.HL7_TRIGGER_EVENT, "A19");
        mock.expectedHeaderReceived(HL7Constants.HL7_MESSAGE_CONTROL, "1234");
        mock.expectedHeaderReceived(HL7Constants.HL7_PROCESSING_ID, "P");
        mock.expectedHeaderReceived(HL7Constants.HL7_VERSION_ID, "2.4");

        String body = createHL7AsString();
        template.sendBody("direct:unmarshal", body);

        assertMockEndpointsSatisfied();

        Message msg = mock.getExchanges().get(0).getIn().getBody(Message.class);
        assertEquals("2.4", msg.getVersion());
        QRD qrd = (QRD) msg.get("QRD");
        assertEquals("0101701234", qrd.getWhoSubjectFilter(0).getIDNumber().getValue());
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:marshal").marshal().hl7().to("mock:marshal");

                from("direct:unmarshal").unmarshal().hl7().to("mock:unmarshal");
            }
        };
    }

    private static String createHL7AsString() {
        String line1 = "MSH|^~\\&|MYSENDER|MYSENDERAPP|MYCLIENT|MYCLIENTAPP|200612211200||QRY^A19|1234|P|2.4";
        String line2 = "QRD|200612211200|R|I|GetPatient|||1^RD|0101701234|DEM||";

        StringBuffer body = new StringBuffer();
        body.append(line1);
        body.append("\n");
        body.append(line2);
        return body.toString();
    }

    private static Message createHL7AsMessage() throws Exception {
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

}
