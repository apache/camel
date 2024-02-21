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
package org.apache.camel.dataformat.mime.multipart;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.util.ByteArrayDataSource;

import org.apache.camel.Exchange;
import org.apache.camel.attachment.Attachment;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.attachment.DefaultAttachment;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MimeMultipartDataFormatTest extends CamelTestSupport {
    private Exchange exchange;
    private AttachmentMessage in;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        exchange = new DefaultExchange(context);
        in = exchange.getIn(AttachmentMessage.class);
    }

    @Test
    public void roundtripWithTextAttachments() throws IOException {
        String attContentType = "text/plain";
        String attText = "Attachment Text";
        String attFileName = "Attachment File Name";
        in.setBody("Body text");
        in.setHeader(Exchange.CONTENT_TYPE, "text/plain;charset=iso8859-1;other-parameter=true");
        in.setHeader(Exchange.CONTENT_ENCODING, "UTF8");
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Description", "Sample Attachment Data");
        headers.put("X-AdditionalData", "additional data");
        addAttachment(attContentType, attText, attFileName, headers);
        Exchange result = template.send("direct:roundtrip", exchange);
        AttachmentMessage out = result.getMessage(AttachmentMessage.class);
        assertEquals("Body text", out.getBody(String.class));
        assertTrue(out.getHeader(Exchange.CONTENT_TYPE, String.class).startsWith("text/plain"));
        assertEquals("UTF8", out.getHeader(Exchange.CONTENT_ENCODING));
        assertTrue(out.hasAttachments());
        assertEquals(1, out.getAttachmentNames().size());
        assertTrue(out.getAttachmentNames().contains(attFileName));
        Attachment att = out.getAttachmentObject(attFileName);
        DataHandler dh = att.getDataHandler();
        assertNotNull(dh);
        assertEquals(attContentType, dh.getContentType());
        InputStream is = dh.getInputStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, os);
        assertEquals(attText, new String(os.toByteArray()));
        assertEquals("Sample Attachment Data", att.getHeader("content-description"));
        assertEquals("additional data", att.getHeader("X-AdditionalData"));
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
        AttachmentMessage out = result.getMessage(AttachmentMessage.class);
        assertEquals("Body text", out.getBody(String.class));
        assertTrue(out.getHeader(Exchange.CONTENT_TYPE, String.class).startsWith("text/plain"));
        assertEquals("UTF8", out.getHeader(Exchange.CONTENT_ENCODING));
        assertTrue(out.hasAttachments());
        assertEquals(1, out.getAttachmentNames().size());
        assertTrue(out.getAttachmentNames().contains(attFileName));
        DataHandler dh = out.getAttachment(attFileName);
        assertNotNull(dh);
        assertEquals(attContentType, dh.getContentType());
        InputStream is = dh.getInputStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, os);
        assertEquals(attText, new String(os.toByteArray()));
    }

    @Test
    @Disabled("Fails on CI servers and some platforms - maybe due locale or something")
    public void roundtripWithTextAttachmentsAndSpecialCharacters() throws IOException {
        String attContentType = "text/plain";
        String attText = "Attachment Text with special characters: \u00A9";
        String attFileName = "Attachment File Name with special characters: \u00A9";
        in.setBody("Body text with special characters: \u00A9");
        in.setHeader(Exchange.CONTENT_TYPE, "text/plain");
        in.setHeader(Exchange.CONTENT_ENCODING, "UTF8");
        addAttachment(attContentType, attText, attFileName);
        Exchange result = template.send("direct:roundtrip", exchange);
        AttachmentMessage out = result.getMessage(AttachmentMessage.class);
        assertEquals("Body text with special characters: \u00A9", out.getBody(String.class));
        assertTrue(out.getHeader(Exchange.CONTENT_TYPE, String.class).startsWith("text/plain"));
        assertEquals("UTF8", out.getHeader(Exchange.CONTENT_ENCODING));
        assertTrue(out.hasAttachments());
        assertEquals(1, out.getAttachmentNames().size());
        assertTrue(out.getAttachmentNames().contains(attFileName));
        DataHandler dh = out.getAttachment(attFileName);
        assertNotNull(dh);
        assertEquals(attContentType, dh.getContentType());
        InputStream is = dh.getInputStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, os);
        assertEquals(attText, new String(os.toByteArray()));
    }

    @Test
    public void roundtripWithMultipleTextAttachmentsButDifferentCharsets() throws IOException {
        String att1ContentType = "text/plain; charset=ISO-8859-1";
        String att1Text = new String("empf\u00e4nger1".getBytes("ISO-8859-1"), "ISO-8859-1");
        String att1FileName = "empf\u00e4nger1";
        String att2ContentType = "text/plain; charset=ISO-8859-15";
        String att2Text = new String("empf\u00e4nger15".getBytes("ISO-8859-15"), "ISO-8859-15");
        String att2FileName = "empf\u00e4nger15";
        String att3ContentType = "text/plain; charset=UTF-8";
        String att3Text = new String("empf\u00e4nger8".getBytes("UTF-8"), "UTF-8");
        String att3FileName = "empf\u00e4nger8";
        addAttachment(att1ContentType, att1Text, att1FileName);
        addAttachment(att2ContentType, att2Text, att2FileName);
        addAttachment(att3ContentType, att3Text, att3FileName);

        in.setBody(new String("empf\u00e4nger15".getBytes("ISO-8859-15"), "ISO-8859-15"));
        in.setHeader(Exchange.CONTENT_TYPE, "text/plain; charset=ISO-8859-15");
        in.setHeader(Exchange.CONTENT_ENCODING, "ISO-8859-15");

        Exchange result = template.send("direct:roundtrip", exchange);
        AttachmentMessage out = result.getMessage(AttachmentMessage.class);

        assertEquals(att2Text, out.getBody(String.class));
        assertTrue(out.getHeader(Exchange.CONTENT_TYPE, String.class).startsWith("text/plain"));
        assertEquals("ISO-8859-15", out.getHeader(Exchange.CONTENT_ENCODING));
        assertTrue(out.hasAttachments());
        assertEquals(3, out.getAttachmentNames().size());

        assertTrue(out.getAttachmentNames().contains(att1FileName));
        DataHandler dh = out.getAttachment(att1FileName);
        assertNotNull(dh);
        assertEquals(att1ContentType, dh.getContentType());
        InputStream is = dh.getInputStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, os);
        assertEquals(att1Text, new String(os.toByteArray(), "ISO-8859-1"));

        assertTrue(out.getAttachmentNames().contains(att2FileName));
        dh = out.getAttachment(att2FileName);
        assertNotNull(dh);
        assertEquals(att2ContentType, dh.getContentType());
        is = dh.getInputStream();
        os = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, os);
        assertEquals(att2Text, new String(os.toByteArray(), "ISO-8859-15"));

        assertTrue(out.getAttachmentNames().contains(att3FileName));
        dh = out.getAttachment(att3FileName);
        assertNotNull(dh);
        assertEquals(att3ContentType, dh.getContentType());
        is = dh.getInputStream();
        os = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, os);
        assertEquals(att3Text, new String(os.toByteArray(), "UTF-8"));
    }

    @Test
    public void roundtripWithTextAttachmentsAndBinaryContent() throws IOException {
        String attContentType = "text/plain";
        String attText = "Attachment Text";
        String attFileName = "Attachment File Name";
        in.setBody("Body text");
        in.setHeader(Exchange.CONTENT_TYPE, "text/plain;charset=iso8859-1;other-parameter=true");
        addAttachment(attContentType, attText, attFileName);
        Exchange result = template.send("direct:roundtripbinarycontent", exchange);
        AttachmentMessage out = result.getMessage(AttachmentMessage.class);
        assertEquals("Body text", out.getBody(String.class));
        assertTrue(out.getHeader(Exchange.CONTENT_TYPE, String.class).startsWith("text/plain"));
        assertEquals("iso8859-1", out.getHeader(Exchange.CONTENT_ENCODING));
        assertTrue(out.hasAttachments());
        assertEquals(1, out.getAttachmentNames().size());
        assertTrue(out.getAttachmentNames().contains(attFileName));
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
        byte[] attText = { 0, 1, 2, 3, 4, 5, 6, 7 };
        String attFileName = "Attachment File Name";
        in.setBody("Body text");
        DataSource ds = new ByteArrayDataSource(attText, attContentType);
        in.addAttachment(attFileName, new DataHandler(ds));
        Exchange result = template.send("direct:roundtrip", exchange);
        AttachmentMessage out = result.getMessage(AttachmentMessage.class);
        assertEquals("Body text", out.getBody(String.class));
        assertTrue(out.hasAttachments());
        assertEquals(1, out.getAttachmentNames().size());
        assertTrue(out.getAttachmentNames().contains(attFileName));
        DataHandler dh = out.getAttachment(attFileName);
        assertNotNull(dh);
        assertEquals(attContentType, dh.getContentType());
        InputStream is = dh.getInputStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, os);
        assertArrayEquals(attText, os.toByteArray());
    }

    @Test
    public void roundtripWithBinaryMultipleAttachments() throws IOException {
        String attContentType1 = "application/binary";
        byte[] attText1 = { 0, 1, 2, 3, 4, 5, 6, 7 };
        String attFileName1 = "Attachment File Name 1";

        String attContentType2 = "application/binary";
        byte[] attText2 = { 0, 1, 2, 3, 4, 5, 6, 7, 8 };
        String attFileName2 = "Attachment File Name 2";

        in.setBody("Body text");
        DataSource ds1 = new ByteArrayDataSource(attText1, attContentType1);
        DataSource ds2 = new ByteArrayDataSource(attText2, attContentType2);
        in.addAttachment(attFileName1, new DataHandler(ds1));
        in.addAttachment(attFileName2, new DataHandler(ds2));
        addAttachment(ds1, attFileName1, null);
        addAttachment(ds2, attFileName2, null);

        Exchange result = template.send("direct:roundtrip", exchange);
        AttachmentMessage out = result.getMessage(AttachmentMessage.class);
        assertEquals("Body text", out.getBody(String.class));
        assertTrue(out.hasAttachments());
        assertEquals(2, out.getAttachmentNames().size());

        assertTrue(out.getAttachmentNames().contains(attFileName1));
        assertTrue(out.getAttachmentNames().contains(attFileName2));

        DataHandler dh1 = out.getAttachment(attFileName1);
        assertNotNull(dh1);
        assertEquals(attContentType1, dh1.getContentType());

        InputStream is1 = dh1.getInputStream();
        ByteArrayOutputStream os1 = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is1, os1);
        assertArrayEquals(attText1, os1.toByteArray());

        DataHandler dh2 = out.getAttachment(attFileName2);
        assertNotNull(dh2);
        assertEquals(attContentType2, dh2.getContentType());

        InputStream is2 = dh2.getInputStream();
        ByteArrayOutputStream os2 = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is2, os2);
        assertArrayEquals(attText2, os2.toByteArray());

    }

    @Test
    public void roundtripWithBinaryAttachmentsAndBinaryContent() throws IOException {
        String attContentType = "application/binary";
        byte[] attText = { 0, 1, 2, 3, 4, 5, 6, 7 };
        String attFileName = "Attachment File Name";
        in.setBody("Body text");
        DataSource ds = new ByteArrayDataSource(attText, attContentType);
        in.addAttachment(attFileName, new DataHandler(ds));
        Exchange result = template.send("direct:roundtripbinarycontent", exchange);
        AttachmentMessage out = result.getMessage(AttachmentMessage.class);
        assertEquals("Body text", out.getBody(String.class));
        assertTrue(out.hasAttachments());
        assertEquals(1, out.getAttachmentNames().size());
        assertTrue(out.getAttachmentNames().contains(attFileName));
        DataHandler dh = out.getAttachment(attFileName);
        assertNotNull(dh);
        assertEquals(attContentType, dh.getContentType());
        InputStream is = dh.getInputStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, os);
        assertArrayEquals(attText, os.toByteArray());
    }

    @Test
    public void roundtripWithoutAttachments() {
        in.setBody("Body text");
        Exchange result = template.send("direct:roundtrip", exchange);
        AttachmentMessage out = result.getMessage(AttachmentMessage.class);
        assertEquals("Body text", out.getBody(String.class));
        assertFalse(out.hasAttachments());
    }

    @Test
    public void roundtripWithoutAttachmentsToMultipart() {
        in.setBody("Body text");
        Exchange result = template.send("direct:roundtripmultipart", exchange);
        AttachmentMessage out = result.getMessage(AttachmentMessage.class);
        assertEquals("Body text", out.getBody(String.class));
        assertFalse(out.hasAttachments());
    }

    @Test
    public void roundtripWithoutAttachmentsAndContentType() {
        in.setBody("Body text");
        in.setHeader("Content-Type", "text/plain");
        Exchange result = template.send("direct:roundtrip", exchange);
        AttachmentMessage out = result.getMessage(AttachmentMessage.class);
        assertEquals("Body text", out.getBody(String.class));
        assertFalse(out.hasAttachments());
    }

    @Test
    public void roundtripWithoutAttachmentsAndInvalidContentType() {
        in.setBody("Body text");
        in.setHeader("Content-Type", "text?plain");
        Exchange result = template.send("direct:roundtrip", exchange);
        AttachmentMessage out = result.getMessage(AttachmentMessage.class);
        assertEquals("Body text", out.getBody(String.class));
        assertFalse(out.hasAttachments());
    }

    @Test
    public void marhsalOnlyMixed() throws IOException {
        in.setBody("Body text");
        in.setHeader("Content-Type", "text/plain");
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Description", "Sample Attachment Data");
        headers.put("X-AdditionalData", "additional data");
        addAttachment("application/octet-stream", "foobar", "attachment.bin", headers);
        Exchange result = template.send("direct:marshalonlymixed", exchange);
        assertTrue(result.getMessage().getHeader("Content-Type", String.class).startsWith("multipart/mixed"));
        String resultBody = result.getMessage().getBody(String.class);
        assertTrue(resultBody.contains("Content-Description: Sample Attachment Data"));
    }

    @Test
    public void marhsalOnlyRelated() throws IOException {
        in.setBody("Body text");
        in.setHeader("Content-Type", "text/plain");
        addAttachment("application/octet-stream", "foobar", "attachment.bin");
        Exchange result = template.send("direct:marshalonlyrelated", exchange);
        assertTrue(result.getMessage().getHeader("Content-Type", String.class).startsWith("multipart/related"));
    }

    @Test
    public void marhsalUnmarshalInlineHeaders() throws IOException {
        in.setBody("Body text");
        in.setHeader("Content-Type", "text/plain");
        in.setHeader("included", "must be included");
        in.setHeader("excluded", "should not be there");
        in.setHeader("x-foo", "any value");
        in.setHeader("x-bar", "also there");
        in.setHeader("xbar", "should not be there");
        addAttachment("application/octet-stream", "foobar", "attachment.bin");
        Exchange intermediate = template.send("direct:marshalonlyinlineheaders", exchange);
        String bodyStr = intermediate.getMessage().getBody(String.class);
        assertTrue(bodyStr.contains("must be included"));
        assertFalse(bodyStr.contains("should not be there"));
        assertTrue(bodyStr.contains("x-foo:"));
        assertTrue(bodyStr.contains("x-bar:"));
        assertFalse(bodyStr.contains("xbar"));
        intermediate.setIn(intermediate.getMessage());
        intermediate.setMessage(null);
        intermediate.getIn().removeHeaders(".*");
        intermediate.getIn().setHeader("included", "should be replaced");
        Exchange out = template.send("direct:unmarshalonlyinlineheaders", intermediate);
        assertEquals("Body text", out.getMessage().getBody(String.class));
        assertEquals("must be included", out.getMessage().getHeader("included"));
        assertNull(out.getMessage().getHeader("excluded"));
        assertEquals("any value", out.getMessage().getHeader("x-foo"));
        assertEquals("also there", out.getMessage().getHeader("x-bar"));
    }

    @Test
    public void unmarshalRelated() throws IOException {
        in.setBody(new File("src/test/resources/multipart-related.txt"));
        Attachment dh = unmarshalAndCheckAttachmentName("950120.aaCB@XIson.com");
        assertNotNull(dh);
        assertEquals("The fixed length records", dh.getHeader("Content-Description"));
        assertEquals("header value1,header value2", dh.getHeader("X-Additional-Header"));
        assertEquals(2, dh.getHeaderAsList("X-Additional-Header").size());
    }

    @Test
    public void unmarshalWithoutId() throws IOException {
        in.setBody(new File("src/test/resources/multipart-without-id.txt"));
        unmarshalAndCheckAttachmentName("@camel.apache.org");
    }

    @Test
    public void unmarshalNonMimeBody() {
        in.setBody("This is not a MIME-Multipart");
        Exchange out = template.send("direct:unmarshalonly", exchange);
        assertNotNull(out.getMessage());
        String bodyStr = out.getMessage().getBody(String.class);
        assertEquals("This is not a MIME-Multipart", bodyStr);
    }

    @Test
    public void unmarshalInlineHeadersNonMimeBody() {
        in.setBody("This is not a MIME-Multipart");
        Exchange out = template.send("direct:unmarshalonlyinlineheaders", exchange);
        assertNotNull(out.getMessage());
        String bodyStr = out.getMessage().getBody(String.class);
        assertEquals("This is not a MIME-Multipart", bodyStr);
    }

    /*
     * This test will only work of stream caching is enabled on the route. In order to find out whether the body
     * is a multipart or not the stream has to be read, and if the underlying data is a stream (but not a stream cache)
     * there is no way back
     */
    @Test
    public void unmarshalInlineHeadersNonMimeBodyStream() {
        in.setBody(new ByteArrayInputStream("This is not a MIME-Multipart".getBytes(StandardCharsets.UTF_8)));
        Exchange out = template.send("direct:unmarshalonlyinlineheaders", exchange);
        assertNotNull(out.getMessage());
        String bodyStr = out.getMessage().getBody(String.class);
        assertEquals("This is not a MIME-Multipart", bodyStr);
    }

    @Test
    public void attachmentReadOnce() throws IOException {
        String attContentType = "text/plain";
        String attText = "Attachment Text";
        InputStream attInputStream = new ByteArrayInputStream(attText.getBytes());
        String attFileName = "Attachment File Name";
        in.setBody("Body text");
        in.setHeader(Exchange.CONTENT_TYPE, "text/plain;charset=iso8859-1;other-parameter=true");
        in.setHeader("Content-Transfer-Encoding", "UTF8");
        in.setHeader(Exchange.CONTENT_ENCODING, "UTF8");
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Description", "Sample Attachment Data");
        headers.put("X-AdditionalData", "additional data");
        CountingByteArrayDataSource attachmentDs = new CountingByteArrayDataSource(attInputStream, attContentType);
        addAttachment(attachmentDs, attFileName, headers);
        Exchange result = template.send("direct:roundtrip", exchange);
        AttachmentMessage out = result.getMessage(AttachmentMessage.class);
        assertEquals("Body text", out.getBody(String.class));
        assertTrue(out.getHeader(Exchange.CONTENT_TYPE, String.class).startsWith("text/plain"));
        assertEquals("UTF8", out.getHeader(Exchange.CONTENT_ENCODING));
        assertTrue(out.hasAttachments());
        assertEquals(1, out.getAttachmentNames().size());
        assertTrue(out.getAttachmentNames().contains(attFileName));
        Attachment att = out.getAttachmentObject(attFileName);
        DataHandler dh = att.getDataHandler();
        assertNotNull(dh);
        assertEquals(attContentType, dh.getContentType());
        InputStream is = dh.getInputStream();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        IOHelper.copyAndCloseInput(is, os);
        assertEquals(attText, new String(os.toByteArray()));
        assertEquals("Sample Attachment Data", att.getHeader("content-description"));
        assertEquals("additional data", att.getHeader("X-AdditionalData"));
        assertEquals(1, attachmentDs.readCounts); // Fails - input is read twice
    }

    private Attachment unmarshalAndCheckAttachmentName(String matcher) throws IOException {
        Exchange intermediate = template.send("direct:unmarshalonlyinlineheaders", exchange);
        assertNotNull(intermediate.getMessage());
        String bodyStr = intermediate.getMessage().getBody(String.class);
        assertNotNull(bodyStr);
        assertTrue(bodyStr.startsWith("25"));
        assertEquals(1, intermediate.getMessage(AttachmentMessage.class).getAttachmentNames().size());
        assertTrue(intermediate.getMessage(AttachmentMessage.class).getAttachmentNames().iterator().next().contains(matcher));
        Attachment att = intermediate.getMessage(AttachmentMessage.class)
                .getAttachmentObject(intermediate.getMessage(AttachmentMessage.class).getAttachmentNames().iterator().next());
        DataHandler dh = att.getDataHandler();
        assertNotNull(dh);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        dh.writeTo(bos);
        String attachmentString = new String(bos.toByteArray(), StandardCharsets.UTF_8);
        assertTrue(attachmentString.startsWith("Old MacDonald had a farm"));
        return att;
    }

    private void addAttachment(DataSource ds, String attFileName, Map<String, String> headers) {
        DefaultAttachment attachment = new DefaultAttachment(ds);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                attachment.addHeader(entry.getKey(), entry.getValue());
            }
        }
        in.addAttachmentObject(attFileName, attachment);
    }

    private void addAttachment(String attContentType, String attText, String attFileName) throws IOException {
        addAttachment(attContentType, attText, attFileName, null);
    }

    private void addAttachment(String attContentType, String attText, String attFileName, Map<String, String> headers)
            throws IOException {
        DataSource ds = new ByteArrayDataSource(attText, attContentType);
        DefaultAttachment attachment = new DefaultAttachment(ds);
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                attachment.addHeader(entry.getKey(), entry.getValue());
            }
        }
        in.addAttachmentObject(attFileName, attachment);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:roundtrip").marshal().mimeMultipart().to("log:mime?showHeaders=true").unmarshal().mimeMultipart();
                from("direct:roundtripmultipart").marshal().mimeMultipart(true, false, false).to("log:mime?showHeaders=true")
                        .unmarshal().mimeMultipart();
                from("direct:roundtripinlineheaders").marshal().mimeMultipart(false, true, false)
                        .to("log:mime?showHeaders=true").unmarshal().mimeMultipart(false, true, false);
                from("direct:roundtripbinarycontent").marshal().mimeMultipart(false, false, true)
                        .to("log:mime?showHeaders=true").to("dataformat:mimeMultipart:unmarshal");
                from("direct:marshalonlyrelated").marshal().mimeMultipart("related");
                from("direct:marshalonlymixed").marshal().mimeMultipart();
                from("direct:marshalonlyinlineheaders").marshal().mimeMultipart("mixed", false, true, "(included|x-.*)", false);
                from("direct:unmarshalonly").unmarshal().mimeMultipart(false, false, false);
                from("direct:unmarshalonlyinlineheaders").streamCaching().unmarshal().mimeMultipart(false, true, false);
            }
        };
    }

    private class CountingByteArrayDataSource extends ByteArrayDataSource {

        volatile int readCounts;

        CountingByteArrayDataSource(InputStream is, String attContentType) throws IOException {
            super(is, attContentType);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            readCounts++;
            return super.getInputStream();
        }

    }

}
