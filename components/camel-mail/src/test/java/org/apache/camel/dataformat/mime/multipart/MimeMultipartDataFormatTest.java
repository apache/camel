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
package org.apache.camel.dataformat.mime.multipart;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.util.ByteArrayDataSource;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.hamcrest.core.StringStartsWith.startsWith;

public class MimeMultipartDataFormatTest extends CamelTestSupport {
    private Exchange exchange;
    private Message in;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        exchange = new DefaultExchange(context);
        in = exchange.getIn();
    }

    @Test
    public void roundtripWithTextAttachments() throws IOException {
        String attContentType = "text/plain";
        String attText = "Attachment Text";
        String attFileName = "Attachment File Name";
        in.setBody("Body text");
        in.setHeader(Exchange.CONTENT_TYPE, "text/plain;charset=iso8859-1;other-parameter=true");
        in.setHeader(Exchange.CONTENT_ENCODING, "UTF8");
        addAttachment(attContentType, attText, attFileName);
        Exchange result = template.send("direct:roundtrip", exchange);
        Message out = result.getOut();
        assertEquals("Body text", out.getBody(String.class));
        assertThat(out.getHeader(Exchange.CONTENT_TYPE, String.class), startsWith("text/plain"));
        assertEquals("UTF8", out.getHeader(Exchange.CONTENT_ENCODING));
        assertTrue(out.hasAttachments());
        assertEquals(1, out.getAttachmentNames().size());
        assertThat(out.getAttachmentNames(), hasItem(attFileName));
        DataHandler dh = out.getAttachment(attFileName);
        assertNotNull(dh);
        assertEquals(attContentType, dh.getContentType());
        InputStream is = dh.getInputStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, os);
        assertEquals(attText, new String(os.toByteArray()));
    }

    @Test
    public void roundtripWithTextAttachmentsHeadersInline() throws IOException {
        String attContentType = "text/plain";
        String attText = "Attachment Text";
        String attFileName = "Attachment File Name";
        in.setBody("Body text");
        in.setHeader(Exchange.CONTENT_TYPE, "text/plain;charset=iso8859-1;other-parameter=true");
        in.setHeader(Exchange.CONTENT_ENCODING, "UTF8");
        addAttachment(attContentType, attText, attFileName);
        Exchange result = template.send("direct:roundtripinlineheaders", exchange);
        Message out = result.getOut();
        assertEquals("Body text", out.getBody(String.class));
        assertThat(out.getHeader(Exchange.CONTENT_TYPE, String.class), startsWith("text/plain"));
        assertEquals("UTF8", out.getHeader(Exchange.CONTENT_ENCODING));
        assertTrue(out.hasAttachments());
        assertEquals(1, out.getAttachmentNames().size());
        assertThat(out.getAttachmentNames(), hasItem(attFileName));
        DataHandler dh = out.getAttachment(attFileName);
        assertNotNull(dh);
        assertEquals(attContentType, dh.getContentType());
        InputStream is = dh.getInputStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, os);
        assertEquals(attText, new String(os.toByteArray()));
    }

    @Test
    public void roundtripWithTextAttachmentsAndSpecialCharacters() throws IOException {
        String attContentType = "text/plain";
        String attText = "Attachment Text with special characters: \u00A9";
        String attFileName = "Attachment File Name with special characters: \u00A9";
        in.setBody("Body text with special characters: \u00A9");
        in.setHeader(Exchange.CONTENT_TYPE, "text/plain");
        in.setHeader(Exchange.CONTENT_ENCODING, "UTF8");
        addAttachment(attContentType, attText, attFileName);
        Exchange result = template.send("direct:roundtrip", exchange);
        Message out = result.getOut();
        assertEquals("Body text with special characters: \u00A9", out.getBody(String.class));
        assertThat(out.getHeader(Exchange.CONTENT_TYPE, String.class), startsWith("text/plain"));
        assertEquals("UTF8", out.getHeader(Exchange.CONTENT_ENCODING));
        assertTrue(out.hasAttachments());
        assertEquals(1, out.getAttachmentNames().size());
        assertThat(out.getAttachmentNames(), hasItem(attFileName));
        DataHandler dh = out.getAttachment(attFileName);
        assertNotNull(dh);
        assertEquals(attContentType, dh.getContentType());
        InputStream is = dh.getInputStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, os);
        assertEquals(attText, new String(os.toByteArray()));
    }

    @Test
    public void roundtripWithTextAttachmentsAndBinaryContent() throws IOException {
        String attContentType = "text/plain";
        String attText = "Attachment Text";
        String attFileName = "Attachment File Name";
        in.setBody("Body text");
        in.setHeader(Exchange.CONTENT_TYPE, "text/plain;charset=iso8859-1;other-parameter=true");
        in.setHeader(Exchange.CONTENT_ENCODING, "UTF8");
        addAttachment(attContentType, attText, attFileName);
        Exchange result = template.send("direct:roundtripbinarycontent", exchange);
        Message out = result.getOut();
        assertEquals("Body text", out.getBody(String.class));
        assertThat(out.getHeader(Exchange.CONTENT_TYPE, String.class), startsWith("text/plain"));
        assertEquals("UTF8", out.getHeader(Exchange.CONTENT_ENCODING));
        assertTrue(out.hasAttachments());
        assertEquals(1, out.getAttachmentNames().size());
        assertThat(out.getAttachmentNames(), hasItem(attFileName));
        DataHandler dh = out.getAttachment(attFileName);
        assertNotNull(dh);
        assertEquals(attContentType, dh.getContentType());
        InputStream is = dh.getInputStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, os);
        assertEquals(attText, new String(os.toByteArray()));
    }

    @Test
    public void roundtripWithBinaryAttachments() throws IOException {
        String attContentType = "application/binary";
        byte[] attText = {0, 1, 2, 3, 4, 5, 6, 7};
        String attFileName = "Attachment File Name";
        in.setBody("Body text");
        DataSource ds = new ByteArrayDataSource(attText, attContentType);
        in.addAttachment(attFileName, new DataHandler(ds));
        Exchange result = template.send("direct:roundtrip", exchange);
        Message out = result.getOut();
        assertEquals("Body text", out.getBody(String.class));
        assertTrue(out.hasAttachments());
        assertEquals(1, out.getAttachmentNames().size());
        assertThat(out.getAttachmentNames(), hasItem(attFileName));
        DataHandler dh = out.getAttachment(attFileName);
        assertNotNull(dh);
        assertEquals(attContentType, dh.getContentType());
        InputStream is = dh.getInputStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, os);
        assertArrayEquals(attText, os.toByteArray());
    }

    @Test
    public void roundtripWithBinaryAttachmentsAndBinaryContent() throws IOException {
        String attContentType = "application/binary";
        byte[] attText = {0, 1, 2, 3, 4, 5, 6, 7};
        String attFileName = "Attachment File Name";
        in.setBody("Body text");
        DataSource ds = new ByteArrayDataSource(attText, attContentType);
        in.addAttachment(attFileName, new DataHandler(ds));
        Exchange result = template.send("direct:roundtripbinarycontent", exchange);
        Message out = result.getOut();
        assertEquals("Body text", out.getBody(String.class));
        assertTrue(out.hasAttachments());
        assertEquals(1, out.getAttachmentNames().size());
        assertThat(out.getAttachmentNames(), hasItem(attFileName));
        DataHandler dh = out.getAttachment(attFileName);
        assertNotNull(dh);
        assertEquals(attContentType, dh.getContentType());
        InputStream is = dh.getInputStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, os);
        assertArrayEquals(attText, os.toByteArray());
    }

    @Test
    public void roundtripWithoutAttachments() throws IOException {
        in.setBody("Body text");
        Exchange result = template.send("direct:roundtrip", exchange);
        Message out = result.getOut();
        assertEquals("Body text", out.getBody(String.class));
        assertFalse(out.hasAttachments());
    }

    @Test
    public void roundtripWithoutAttachmentsToMultipart() throws IOException {
        in.setBody("Body text");
        Exchange result = template.send("direct:roundtripmultipart", exchange);
        Message out = result.getOut();
        assertEquals("Body text", out.getBody(String.class));
        assertFalse(out.hasAttachments());
    }

    @Test
    public void roundtripWithoutAttachmentsAndContentType() throws IOException {
        in.setBody("Body text");
        in.setHeader("Content-Type", "text/plain");
        Exchange result = template.send("direct:roundtrip", exchange);
        Message out = result.getOut();
        assertEquals("Body text", out.getBody(String.class));
        assertFalse(out.hasAttachments());
    }

    @Test
    public void roundtripWithoutAttachmentsAndInvalidContentType() throws IOException {
        in.setBody("Body text");
        in.setHeader("Content-Type", "text?plain");
        Exchange result = template.send("direct:roundtrip", exchange);
        Message out = result.getOut();
        assertEquals("Body text", out.getBody(String.class));
        assertFalse(out.hasAttachments());
    }

    @Test
    public void marhsalOnlyMixed() throws IOException {
        in.setBody("Body text");
        in.setHeader("Content-Type", "text/plain");
        addAttachment("application/octet-stream", "foobar", "attachment.bin");
        Exchange result = template.send("direct:marshalonlymixed", exchange);
        assertThat(result.getOut().getHeader("Content-Type", String.class), startsWith("multipart/mixed"));
    }

    @Test
    public void marhsalOnlyRelated() throws IOException {
        in.setBody("Body text");
        in.setHeader("Content-Type", "text/plain");
        addAttachment("application/octet-stream", "foobar", "attachment.bin");
        Exchange result = template.send("direct:marshalonlyrelated", exchange);
        assertThat(result.getOut().getHeader("Content-Type", String.class), startsWith("multipart/related"));
    }

    private void addAttachment(String attContentType, String attText, String attFileName) throws IOException {
        DataSource ds = new ByteArrayDataSource(attText, attContentType);
        in.addAttachment(attFileName, new DataHandler(ds));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:roundtrip").marshal().mimeMultipart().to("log:mime?showHeaders=true").unmarshal().mimeMultipart();
                from("direct:roundtripmultipart").marshal().mimeMultipart(true, false, false).to("log:mime?showHeaders=true").unmarshal().mimeMultipart();
                from("direct:roundtripinlineheaders").marshal().mimeMultipart(false, true, false).to("log:mime?showHeaders=true").unmarshal().mimeMultipart(false, true, false);
                from("direct:roundtripbinarycontent").marshal().mimeMultipart(false, false, true).to("log:mime?showHeaders=true").to("dataformat:mime-multipart:unmarshal");
                from("direct:marshalonlyrelated").marshal().mimeMultipart("related");
                from("direct:marshalonlymixed").marshal().mimeMultipart();
            }
        };
    }
}
