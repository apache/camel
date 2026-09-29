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
package org.apache.camel.component.syslog;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Text outside US-ASCII in a syslog message (RFC 5424 MSG and structured data values are UTF-8).
 */
public class SyslogCharsetTest extends CamelTestSupport {

    // text in the Latin-1 range and in a CJK script
    private static final String TEXT = "caf\u00e9 Gr\u00fc\u00dfe \u65e5\u672c";
    // a name with an umlaut
    private static final String NAME = "J\u00fcrgen";
    private static final String RFC5424_HEADER = "<34>1 2003-10-11T22:14:15.003Z mymachine.example.com su - ID47 ";
    private static final byte[] BOM = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };

    @RegisterExtension
    AvailablePortFinder.Port serverPort = AvailablePortFinder.find();

    @Test
    public void testConverterRfc3164() {
        SyslogMessage message = SyslogConverter.toSyslogMessage("<165>Aug  4 05:34:00 mymachine " + TEXT);

        assertEquals("mymachine", message.getHostname());
        assertEquals(TEXT, message.getLogMessage());
    }

    @Test
    public void testConverterRfc5424() {
        SyslogMessage message
                = SyslogConverter.toSyslogMessage(RFC5424_HEADER + "[exampleSDID@32473 user=\"" + NAME + "\"] " + TEXT);

        Rfc5424SyslogMessage rfc5424 = assertInstanceOf(Rfc5424SyslogMessage.class, message);
        assertEquals("[exampleSDID@32473 user=\"" + NAME + "\"]", rfc5424.getStructuredData());
        assertEquals(TEXT, rfc5424.getLogMessage());
    }

    @Test
    public void testParseUtf8Bytes() {
        SyslogMessage message = SyslogConverter.parseMessage((RFC5424_HEADER + "- " + TEXT).getBytes(StandardCharsets.UTF_8));

        assertEquals(TEXT, message.getLogMessage());
    }

    @Test
    public void testParseMsgUtf8WithBom() throws Exception {
        byte[] bytes = concat(RFC5424_HEADER + "- ", BOM, TEXT.getBytes(StandardCharsets.UTF_8));

        // RFC 5424: the BOM marks MSG as UTF-8, it is not part of the text
        assertEquals(TEXT, SyslogConverter.parseMessage(bytes).getLogMessage());
        assertEquals(TEXT, SyslogConverter.parseMessage(bytes, StandardCharsets.ISO_8859_1).getLogMessage());
    }

    @Test
    public void testParseWithCharset() {
        String text = "caf\u00e9 Gr\u00fc\u00dfe";
        byte[] bytes = ("<165>Aug  4 05:34:00 mymachine " + text).getBytes(StandardCharsets.ISO_8859_1);

        assertEquals(text, SyslogConverter.parseMessage(bytes, StandardCharsets.ISO_8859_1).getLogMessage());
    }

    @Test
    public void testUnmarshalUtf8Bytes() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(1);

        byte[] bytes = concat(RFC5424_HEADER + "[exampleSDID@32473 user=\"" + NAME + "\"] ", BOM,
                TEXT.getBytes(StandardCharsets.UTF_8));
        template.sendBody("direct:unmarshal", bytes);

        MockEndpoint.assertIsSatisfied(context);
        Rfc5424SyslogMessage message
                = assertInstanceOf(Rfc5424SyslogMessage.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals("[exampleSDID@32473 user=\"" + NAME + "\"]", message.getStructuredData());
        assertEquals(TEXT, message.getLogMessage());
    }

    @Test
    public void testUnmarshalUsesExchangeCharset() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:unmarshal");
        mock.expectedMessageCount(1);

        String text = "caf\u00e9 Gr\u00fc\u00dfe";
        byte[] bytes = ("<165>Aug  4 05:34:00 mymachine " + text).getBytes(StandardCharsets.ISO_8859_1);
        template.sendBodyAndHeader("direct:unmarshal", bytes, Exchange.CHARSET_NAME, "ISO-8859-1");

        MockEndpoint.assertIsSatisfied(context);
        SyslogMessage message = mock.getReceivedExchanges().get(0).getIn().getBody(SyslogMessage.class);
        assertEquals(text, message.getLogMessage());
    }

    @Test
    public void testMarshalUnmarshalRoundTripRfc3164() throws Exception {
        SyslogMessage message = new SyslogMessage();
        message.setFacility(SyslogFacility.LOCAL4);
        message.setSeverity(SyslogSeverity.NOTICE);
        message.setHostname("host1");
        message.setTimestamp(timestamp());
        message.setLogMessage(TEXT);

        SyslogMessage back = roundTrip(message);

        assertEquals(SyslogFacility.LOCAL4, back.getFacility());
        assertEquals(SyslogSeverity.NOTICE, back.getSeverity());
        assertEquals("host1", back.getHostname());
        assertEquals(TEXT, back.getLogMessage());
    }

    @Test
    public void testMarshalUnmarshalRoundTripRfc5424() throws Exception {
        Rfc5424SyslogMessage message = new Rfc5424SyslogMessage();
        message.setFacility(SyslogFacility.LOCAL4);
        message.setSeverity(SyslogSeverity.NOTICE);
        message.setHostname("host1");
        message.setTimestamp(timestamp());
        message.setAppName("app");
        message.setProcId("42");
        message.setMsgId("ID1");
        message.setStructuredData("[exampleSDID@32473 user=\"" + NAME + "\"]");
        message.setLogMessage(TEXT);

        Rfc5424SyslogMessage back = assertInstanceOf(Rfc5424SyslogMessage.class, roundTrip(message));

        assertEquals("host1", back.getHostname());
        assertEquals("app", back.getAppName());
        assertEquals("42", back.getProcId());
        assertEquals("ID1", back.getMsgId());
        assertEquals("[exampleSDID@32473 user=\"" + NAME + "\"]", back.getStructuredData());
        assertEquals(TEXT, back.getLogMessage());
    }

    @Test
    public void testNettyUdpUtf8() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:netty");
        mock.expectedMessageCount(1);

        byte[] data = concat(RFC5424_HEADER + "[exampleSDID@32473 user=\"" + NAME + "\"] ", BOM,
                TEXT.getBytes(StandardCharsets.UTF_8));
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(new DatagramPacket(data, data.length, InetAddress.getByName("127.0.0.1"), serverPort.getPort()));
        }

        MockEndpoint.assertIsSatisfied(context);
        Rfc5424SyslogMessage message
                = assertInstanceOf(Rfc5424SyslogMessage.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals("[exampleSDID@32473 user=\"" + NAME + "\"]", message.getStructuredData());
        assertEquals(TEXT, message.getLogMessage());
    }

    private SyslogMessage roundTrip(SyslogMessage message) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:roundTrip");
        mock.reset();
        mock.expectedMessageCount(1);

        template.sendBody("direct:roundTrip", message);

        MockEndpoint.assertIsSatisfied(context);
        return mock.getReceivedExchanges().get(0).getIn().getBody(SyslogMessage.class);
    }

    private static Calendar timestamp() {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.set(2026, Calendar.SEPTEMBER, 29, 10, 11, 12);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    private static byte[] concat(String head, byte[]... parts) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(head.getBytes(StandardCharsets.UTF_8));
        for (byte[] part : parts) {
            bos.write(part);
        }
        return bos.toByteArray();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:unmarshal").unmarshal().syslog().to("mock:unmarshal");

                from("direct:roundTrip").marshal().syslog().unmarshal().syslog().to("mock:roundTrip");

                from("netty:udp://127.0.0.1:" + serverPort.getPort() + "?sync=false&allowDefaultCodec=false")
                        .unmarshal().syslog().to("mock:netty");
            }
        };
    }
}
