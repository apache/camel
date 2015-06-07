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

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v24.message.ADR_A19;
import ca.uhn.hl7v2.model.v24.segment.MSA;
import ca.uhn.hl7v2.model.v24.segment.MSH;
import ca.uhn.hl7v2.model.v24.segment.QRD;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Unit test for HL7 DataFormat.
 */
public class HL7DataFormatTest extends CamelTestSupport {
    private static final String NONE_ISO_8859_1 = 
        "\u221a\u00c4\u221a\u00e0\u221a\u00e5\u221a\u00ed\u221a\u00f4\u2248\u00ea";

    private HL7DataFormat hl7 = new HL7DataFormat();
    private HL7DataFormat hl7big5 = new HL7DataFormat() {
        @Override
        protected String guessCharsetName(byte[] b, Exchange exchange) {
            return "Big5";
        }
    };

    @Test
    public void testMarshal() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:marshal");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(byte[].class);
        mock.message(0).body(String.class).contains("MSA|AA|123");
        mock.message(0).body(String.class).contains("QRD|20080805120000");

        Message message = createHL7AsMessage();
        template.sendBody("direct:marshal", message);

        assertMockEndpointsSatisfied();
    }
    
    @Test
    public void testMarshalISO8859() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:marshal");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(byte[].class);
        mock.message(0).body(String.class).contains("MSA|AA|123");
        mock.message(0).body(String.class).contains("QRD|20080805120000");
        mock.message(0).body(String.class).not().contains(NONE_ISO_8859_1);
        Message message = createHL7AsMessage();
        template.sendBodyAndProperty("direct:marshal", message, Exchange.CHARSET_NAME, "ISO-8859-1");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMarshalUTF16InMessage() throws Exception {
        String charsetName = "UTF-16";
        MockEndpoint mock = getMockEndpoint("mock:marshal");
        mock.expectedMessageCount(1);

        Message message = createHL7WithCharsetAsMessage(HL7Charset.getHL7Charset(charsetName));
        template.sendBodyAndProperty("direct:marshal", message, Exchange.CHARSET_NAME, charsetName);
        assertMockEndpointsSatisfied();

        byte[] body = (byte[])mock.getExchanges().get(0).getIn().getBody();
        String msg = new String(body, Charset.forName(charsetName));
        assertTrue(msg.contains("MSA|AA|123"));
        assertTrue(msg.contains("QRD|20080805120000"));
    }
    
    @Test
    public void testMarshalUTF8() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:marshal");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(byte[].class);
        mock.message(0).body(String.class).contains("MSA|AA|123");
        mock.message(0).body(String.class).contains("QRD|20080805120000");
        mock.message(0).body(String.class).contains(NONE_ISO_8859_1);
        Message message = createHL7AsMessage();
        template.sendBodyAndProperty("direct:marshal", message, Exchange.CHARSET_NAME, "UTF-8");
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testUnmarshal() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Message.class);

        mock.expectedHeaderReceived(HL7Constants.HL7_SENDING_APPLICATION, "MYSENDER");
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
        mock.expectedHeaderReceived(HL7Constants.HL7_CONTEXT, hl7.getHapiContext());
        mock.expectedHeaderReceived(HL7Constants.HL7_CHARSET, null);

        mock.expectedHeaderReceived(Exchange.CHARSET_NAME, "UTF-8");

        String body = createHL7AsString();
        template.sendBody("direct:unmarshal", body);

        assertMockEndpointsSatisfied();

        Message msg = mock.getExchanges().get(0).getIn().getBody(Message.class);
        assertEquals("2.4", msg.getVersion());
        QRD qrd = (QRD) msg.get("QRD");
        assertEquals("0101701234", qrd.getWhoSubjectFilter(0).getIDNumber().getValue());
    }

    @Test
    public void testUnmarshalWithExplicitUTF16Charset() throws Exception {
        String charset = "UTF-16";
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Message.class);
        mock.expectedHeaderReceived(HL7Constants.HL7_CHARSET, HL7Charset.getHL7Charset(charset).getHL7CharsetName());
        mock.expectedHeaderReceived(Exchange.CHARSET_NAME, charset);

        // Message with explicit encoding in MSH-18
        byte[] body = createHL7WithCharsetAsString(HL7Charset.UTF_16).getBytes(Charset.forName(charset));
        template.sendBodyAndHeader("direct:unmarshal", new ByteArrayInputStream(body), Exchange.CHARSET_NAME, charset);

        assertMockEndpointsSatisfied();

        Message msg = mock.getExchanges().get(0).getIn().getBody(Message.class);
        assertEquals("2.4", msg.getVersion());
        QRD qrd = (QRD) msg.get("QRD");
        assertEquals("0101701234", qrd.getWhoSubjectFilter(0).getIDNumber().getValue());
    }

    @Test
    public void testUnmarshalWithImplicitBig5Charset() throws Exception {
        String charset = "Big5";
        MockEndpoint mock = getMockEndpoint("mock:unmarshalBig5");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Message.class);
        mock.expectedHeaderReceived(HL7Constants.HL7_CHARSET, null);
        mock.expectedHeaderReceived(Exchange.CHARSET_NAME, charset);

        // Message without explicit encoding in MSH-18, but the unmarshaller "guesses"
        // this time that it is Big5
        byte[] body = createHL7AsString().getBytes(Charset.forName(charset));
        template.sendBody("direct:unmarshalBig5", new ByteArrayInputStream(body));

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
                from("direct:unmarshal").unmarshal(hl7).to("mock:unmarshal");
                from("direct:unmarshalBig5").unmarshal(hl7big5).to("mock:unmarshalBig5");
            }
        };
    }

    private static String createHL7AsString() {
        return createHL7WithCharsetAsString(null);
    }

    private static String createHL7WithCharsetAsString(HL7Charset charset) {
        String hl7Charset = charset == null ? "" : charset.getHL7CharsetName();
        String line1 = String.format("MSH|^~\\&|MYSENDER|MYSENDERAPP|MYCLIENT|MYCLIENTAPP|200612211200||QRY^A19|1234|P|2.4||||||%s", hl7Charset);
        String line2 = "QRD|200612211200|R|I|GetPatient|||1^RD|0101701234|DEM||";

        StringBuilder body = new StringBuilder();
        body.append(line1);
        body.append("\r");
        body.append(line2);
        return body.toString();
    }

    private static ADR_A19 createHL7AsMessage() throws Exception {
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
        msa.getMsa3_TextMessage().setValue(NONE_ISO_8859_1);

        QRD qrd = adr.getQRD();
        qrd.getQueryDateTime().getTimeOfAnEvent().setValue("20080805120000");

        return adr;
    }

    private static ADR_A19 createHL7WithCharsetAsMessage(HL7Charset charset) throws Exception {
        ADR_A19 adr = createHL7AsMessage();
        adr.getMSH().getCharacterSet(0).setValue(charset.getHL7CharsetName());
        return adr;
    }

}
