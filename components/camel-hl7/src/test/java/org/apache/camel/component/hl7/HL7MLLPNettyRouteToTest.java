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
 * Unit test for the HL7MLLPNetty Codec.
 */
public class HL7MLLPNettyRouteToTest extends HL7TestSupport {

    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        // START SNIPPET: e1
        HL7MLLPNettyDecoderFactory decoder = new HL7MLLPNettyDecoderFactory();
        decoder.setCharset("iso-8859-1");
        decoder.setConvertLFtoCR(true);
        jndi.bind("hl7decoder", decoder);

        HL7MLLPNettyEncoderFactory encoder = new HL7MLLPNettyEncoderFactory();
        decoder.setCharset("iso-8859-1");
        decoder.setConvertLFtoCR(true);
        jndi.bind("hl7encoder", encoder);
        // END SNIPPET: e1

        return jndi;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start")
                    .to("netty4:tcp://127.0.0.1:" + getPort() + "?sync=true&decoder=#hl7decoder&encoder=#hl7encoder")
                    // because HL7 message contains a bunch of control chars then the logger do not log all of the data
                    .log("HL7 message: ${body}")
                    .to("mock:result");

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
                    });
            }
        };
    }

    @Test
    public void testSendHL7Message() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        // START SNIPPET: e2
        String line1 = "MSH|^~\\&|MYSENDER|MYRECEIVER|MYAPPLICATION||200612211200||QRY^A19|1234|P|2.4";
        String line2 = "QRD|200612211200|R|I|GetPatient|||1^RD|0101701234|DEM||";

        StringBuilder in = new StringBuilder();
        in.append(line1);
        in.append("\n");
        in.append(line2);

        String out = template.requestBody("direct:start", in.toString(), String.class);
        // END SNIPPET: e2

        String[] lines = out.split("\r");
        assertEquals("MSH|^~\\&|MYSENDER||||200701011539||ADR^A19^ADR_A19|456|P|2.4", lines[0]);
        assertEquals("MSA|AA|123", lines[1]);

        assertMockEndpointsSatisfied();
    }

    // START SNIPPET: e3
    private static Message createHL7AsMessage() throws Exception {
        ADR_A19 adr = new ADR_A19();
        adr.initQuickstart("ADR", "A19", "P");

        // Populate the MSH Segment
        MSH mshSegment = adr.getMSH();
        mshSegment.getDateTimeOfMessage().getTimeOfAnEvent().setValue("200701011539");
        mshSegment.getSendingApplication().getNamespaceID().setValue("MYSENDER");
        mshSegment.getMessageControlID().setValue("456");

        // Populate the PID Segment
        MSA msa = adr.getMSA();
        msa.getAcknowledgementCode().setValue("AA");
        msa.getMessageControlID().setValue("123");

        QRD qrd = adr.getQRD();
        qrd.getQueryDateTime().getTimeOfAnEvent().setValue("20080805120000");

        return adr;
    }
    // END SNIPPET: e3

}
