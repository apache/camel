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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Valid RFC 5424 messages: without MSG, with escaped characters in a PARAM-VALUE, and with a NILVALUE timestamp.
 */
public class SyslogRfc5424ParseTest extends CamelTestSupport {

    private static final String HEADER = "<165>1 2003-10-11T22:14:15.003Z mymachine.example.com evntslog - ID47 ";
    private static final String EXAMPLE_SD = "[exampleSDID@32473 iut=\"3\" eventSource=\"Application\" eventID=\"1011\"]";

    @Test
    public void testStructuredDataOnly() {
        // RFC 5424 6.5, example 4: "This is a valid message"
        String sd = EXAMPLE_SD + "[examplePriority@32473 class=\"high\"]";

        Rfc5424SyslogMessage message = parse(HEADER + sd);

        assertEquals("mymachine.example.com", message.getHostname());
        assertEquals("evntslog", message.getAppName());
        assertEquals("-", message.getProcId());
        assertEquals("ID47", message.getMsgId());
        assertEquals(sd, message.getStructuredData());
        assertEquals("", message.getLogMessage());
    }

    @Test
    public void testNilStructuredDataWithoutMsg() {
        Rfc5424SyslogMessage message = parse("<34>1 2003-10-11T22:14:15.003Z mymachine.example.com su - ID47 -");

        assertEquals("ID47", message.getMsgId());
        assertEquals("-", message.getStructuredData());
        assertEquals("", message.getLogMessage());
    }

    @Test
    public void testEscapedBracketInParamValue() {
        String sd = "[exampleSDID@32473 note=\"a\\] b\"]";

        Rfc5424SyslogMessage message = parse(HEADER + sd + " hello");

        assertEquals(sd, message.getStructuredData());
        assertEquals("hello", message.getLogMessage());
    }

    @Test
    public void testUnterminatedParamValue() {
        // invalid input: the PARAM-VALUE is never closed, so the rest of the message is read as structured data and
        // MSG is empty (before, the element was closed at the ']' inside the quotes)
        String rest = "[exampleSDID@32473 note=\"x] hello";

        Rfc5424SyslogMessage message = parse(HEADER + rest);

        assertEquals(rest, message.getStructuredData());
        assertEquals("", message.getLogMessage());
    }

    @Test
    public void testEscapedQuoteAndBackslashInParamValue() {
        String sd = "[exampleSDID@32473 quote=\"say \\\"x\\] y\\\"\" path=\"c:\\\\\" note=\"b\\] c\"][other@32473 n=\"2\"]";

        Rfc5424SyslogMessage message = parse(HEADER + sd + " hello world");

        assertEquals(sd, message.getStructuredData());
        assertEquals("hello world", message.getLogMessage());
    }

    @Test
    public void testNilTimestamp() {
        // RFC 5424 6.2.3: a sender that cannot obtain the time MUST send the NILVALUE
        Rfc5424SyslogMessage message = parse("<34>1 - mymachine.example.com su - ID47 - hello");

        assertNull(message.getTimestamp());
        assertEquals("mymachine.example.com", message.getHostname());
        assertEquals("su", message.getAppName());
        assertEquals("hello", message.getLogMessage());
    }

    @Test
    public void testRfc3164WithoutMsg() {
        SyslogMessage message = SyslogConverter.toSyslogMessage("<34>Oct 11 22:14:15 mymachine");

        assertEquals("mymachine", message.getHostname());
        assertEquals("", message.getLogMessage());
    }

    @Test
    public void testRfc5424ExamplesUnchanged() {
        Rfc5424SyslogMessage example1 = parse(
                "<34>1 2003-10-11T22:14:15.003Z mymachine.example.com su - ID47 - 'su root' failed for lonvick on /dev/pts/8");
        assertNotNull(example1.getTimestamp());
        assertEquals("-", example1.getStructuredData());
        assertEquals("'su root' failed for lonvick on /dev/pts/8", example1.getLogMessage());

        Rfc5424SyslogMessage example2
                = parse("<165>1 2003-08-24T05:14:15.000003-07:00 192.0.2.1 myproc 8710 - - %% It's time to make the do-nuts.");
        assertEquals("192.0.2.1", example2.getHostname());
        assertEquals("8710", example2.getProcId());
        assertEquals("-", example2.getMsgId());
        assertEquals("-", example2.getStructuredData());
        assertEquals("%% It's time to make the do-nuts.", example2.getLogMessage());

        Rfc5424SyslogMessage example3 = parse(HEADER + EXAMPLE_SD + " An application event log entry...");
        assertEquals(EXAMPLE_SD, example3.getStructuredData());
        assertEquals("An application event log entry...", example3.getLogMessage());

        // a quote that does not start a PARAM-VALUE, as parsed before
        Rfc5424SyslogMessage stray = parse(HEADER + "[id@1 a=\"1\"b\"] hello");
        assertEquals("[id@1 a=\"1\"b\"]", stray.getStructuredData());
        assertEquals("hello", stray.getLogMessage());
    }

    @Test
    public void testUnmarshalStructuredDataOnly() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(SyslogConstants.SYSLOG_HOSTNAME, "mymachine.example.com");

        String sd = EXAMPLE_SD + "[examplePriority@32473 class=\"high\"]";
        template.sendBody("direct:unmarshal", HEADER + sd);

        MockEndpoint.assertIsSatisfied(context);
        Rfc5424SyslogMessage message
                = assertInstanceOf(Rfc5424SyslogMessage.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals(sd, message.getStructuredData());
    }

    @Test
    public void testUnmarshalNilTimestamp() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:unmarshal", "<34>1 - mymachine.example.com su - ID47 - hello");

        MockEndpoint.assertIsSatisfied(context);
        assertNull(mock.getReceivedExchanges().get(0).getIn().getHeader(SyslogConstants.SYSLOG_TIMESTAMP));
        assertEquals("hello", mock.getReceivedExchanges().get(0).getIn().getBody(SyslogMessage.class).getLogMessage());
    }

    private static Rfc5424SyslogMessage parse(String text) {
        return assertInstanceOf(Rfc5424SyslogMessage.class, SyslogConverter.toSyslogMessage(text));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:unmarshal").unmarshal().syslog().to("mock:result");
            }
        };
    }
}
