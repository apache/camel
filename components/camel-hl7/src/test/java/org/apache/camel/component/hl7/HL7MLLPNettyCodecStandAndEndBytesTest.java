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
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

/**
 * Unit test for the HL7MLLP Codec using different start and end bytes.
 */
public class HL7MLLPNettyCodecStandAndEndBytesTest extends HL7TestSupport {

    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        HL7MLLPNettyDecoderFactory decoder = new HL7MLLPNettyDecoderFactory();
        decoder.setCharset("iso-8859-1");
        // to test with different start and end bytes.
        decoder.setStartByte('*');
        decoder.setEndByte1('#');
        decoder.setEndByte2('*');
        decoder.setConvertLFtoCR(false);

        jndi.bind("hl7decoder", decoder);

        HL7MLLPNettyEncoderFactory encoder = new HL7MLLPNettyEncoderFactory();
        encoder.setCharset("iso-8859-1");
        // to test with different start and end bytes.
        encoder.setStartByte('*');
        encoder.setEndByte1('#');
        encoder.setEndByte2('*');
        encoder.setConvertLFtoCR(false);

        jndi.bind("hl7encoder", encoder);

        return jndi;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("netty4:tcp://127.0.0.1:" + getPort() + "?sync=true&decoder=#hl7decoder&encoder=#hl7encoder")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            Message input = exchange.getIn().getBody(Message.class);

                            assertEquals("2.4", input.getVersion());
                            QRD qrd = (QRD)input.get("QRD");
                            assertEquals("0101701234", qrd.getWhoSubjectFilter(0).getIDNumber().getValue());

                            Message response = createHL7AsMessage();
                            exchange.getOut().setBody(response);
                        }
                    })
                    .to("mock:result");
            }
        };
    }

    @Test
    public void testSendHL7Message() throws Exception {
        String line1 = "MSH|^~\\&|MYSENDER|MYRECEIVER|MYAPPLICATION||200612211200||QRY^A19|1234|P|2.4";
        String line2 = "QRD|200612211200|R|I|GetPatient|||1^RD|0101701234|DEM||";

        StringBuilder in = new StringBuilder();
        in.append(line1);
        in.append("\r");
        in.append(line2);

        String out = template.requestBody("netty4:tcp://127.0.0.1:" + getPort() + "?sync=true&decoder=#hl7decoder&encoder=#hl7encoder", in.toString(), String.class);

        String[] lines = out.split("\r");
        assertEquals("MSH|^~\\&|MYSENDER||||200701011539||ADR^A19||||123", lines[0]);
        assertEquals("MSA|AA|123", lines[1]);
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

        return adr;
    }

}
