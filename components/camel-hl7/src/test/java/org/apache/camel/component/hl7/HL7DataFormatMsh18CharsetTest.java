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

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v24.message.ADR_A19;
import ca.uhn.hl7v2.util.Terser;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The HL7 data format reads and writes a message in the character set named in MSH-18 (HL7 table 0211).
 */
public class HL7DataFormatMsh18CharsetTest extends CamelTestSupport {

    private static final String GREEK = "Παπαδόπουλος";
    private static final String TURKISH = "Şahİn Öztürk ğı";
    private static final String HEBREW = "כהן";
    private static final String ARABIC = "محمد";
    private static final String CHINESE = "张伟";
    private static final String LATIN9 = "€ ŠšŽž";
    private static final String CYRILLIC = "Иванов";

    @Test
    public void testUnmarshalIso88596() throws Exception {
        assertUnmarshal("8859/6", "ISO-8859-6", ARABIC);
    }

    @Test
    public void testUnmarshalIso88597() throws Exception {
        assertUnmarshal("8859/7", "ISO-8859-7", GREEK);
    }

    @Test
    public void testUnmarshalIso88598() throws Exception {
        assertUnmarshal("8859/8", "ISO-8859-8", HEBREW);
    }

    @Test
    public void testUnmarshalIso88599() throws Exception {
        assertUnmarshal("8859/9", "ISO-8859-9", TURKISH);
    }

    @Test
    public void testUnmarshalIso885915() throws Exception {
        assertUnmarshal("8859/15", "ISO-8859-15", LATIN9);
    }

    @Test
    public void testUnmarshalGb18030() throws Exception {
        assertUnmarshal("GB 18030-2000", "GB18030", CHINESE);
    }

    @Test
    public void testUnmarshalIso88595() throws Exception {
        // control: this entry of the table is right
        assertUnmarshal("8859/5", "ISO-8859-5", CYRILLIC);
    }

    @Test
    public void testMarshalIso88597() throws Exception {
        assertMarshal("8859/7", "ISO-8859-7", GREEK);
    }

    @Test
    public void testMarshalGb18030() throws Exception {
        assertMarshal("GB 18030-2000", "GB18030", CHINESE);
    }

    private void assertUnmarshal(String msh18, String javaCharset, String name) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(1);

        String hl7 = "MSH|^~\\&|MYSENDER|MYSENDERAPP|MYCLIENT|MYCLIENTAPP|200612211200||QRY^A19|1234|P|2.4||||||" + msh18
                     + "\rQRD|200612211200|R|I|GetPatient|||1^RD|0101701234^" + name + "|DEM||";
        template.sendBody("direct:unmarshal", new ByteArrayInputStream(hl7.getBytes(Charset.forName(javaCharset))));

        MockEndpoint.assertIsSatisfied(context);
        Exchange exchange = mock.getReceivedExchanges().get(0);
        assertEquals(javaCharset, exchange.getIn().getHeader(Exchange.CHARSET_NAME));
        Message message = exchange.getIn().getBody(Message.class);
        assertEquals(name, new Terser(message).get("QRD-8-2"));
    }

    private void assertMarshal(String msh18, String javaCharset, String name) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:marshal");
        mock.expectedMessageCount(1);

        ADR_A19 adr = new ADR_A19();
        adr.getMSH().getFieldSeparator().setValue("|");
        adr.getMSH().getEncodingCharacters().setValue("^~\\&");
        adr.getMSH().getMessageType().getMessageType().setValue("ADR");
        adr.getMSH().getMessageType().getTriggerEvent().setValue("A19");
        adr.getMSH().getVersionID().getVersionID().setValue("2.4");
        adr.getMSH().getCharacterSet(0).setValue(msh18);
        adr.getMSA().getAcknowledgementCode().setValue("AA");
        adr.getMSA().getMessageControlID().setValue("123");
        adr.getMSA().getMsa3_TextMessage().setValue(name);
        template.sendBody("direct:marshal", adr);

        MockEndpoint.assertIsSatisfied(context);
        byte[] body = mock.getReceivedExchanges().get(0).getIn().getBody(byte[].class);
        String text = new String(body, Charset.forName(javaCharset));
        try (HapiContext hapi = new DefaultHapiContext()) {
            assertEquals(name, new Terser(hapi.getGenericParser().parse(text)).get("MSA-3"));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:marshal").marshal().hl7().to("mock:marshal");
                from("direct:unmarshal").unmarshal().hl7(false).to("mock:unmarshal");
            }
        };
    }
}
