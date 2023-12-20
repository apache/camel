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
package org.apache.camel.component.cxf.mtom;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.annotation.Resource;
import jakarta.mail.util.ByteArrayDataSource;
import jakarta.xml.ws.Holder;
import jakarta.xml.ws.WebServiceContext;
import jakarta.xml.ws.handler.MessageContext;

import javax.imageio.ImageIO;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.component.cxf.common.CxfPayload;
import org.apache.camel.cxf.mtom_feature.Hello;
import org.apache.cxf.attachment.AttachmentDataSource;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.staxutils.StaxUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 *
 * Unit test for exercising SOAP with Attachment (SwA) feature of a CxfProducer in PAYLOAD mode. That is, testing
 * attachment with MTOM optimization off.
 *
 */
@ContextConfiguration
public class CxfMtomDisabledProducerPayloadModeTest extends CxfMtomProducerPayloadModeTest {

    private static final Logger LOG = LoggerFactory.getLogger(CxfMtomDisabledProducerPayloadModeTest.class);

    @Override
    protected boolean isMtomEnabled() {
        return false;
    }

    @Override
    protected Object getServiceImpl() {
        return new MyHelloImpl();
    }

    @Override
    @Test
    public void testProducer() throws Exception {
        if (MtomTestHelper.isAwtHeadless(null, LOG)) {
            return;
        }

        Exchange exchange = context.createProducerTemplate().send("direct:testEndpoint", new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                List<Source> elements = new ArrayList<>();
                elements.add(new DOMSource(
                        StaxUtils.read(new StringReader(MtomTestHelper.MTOM_DISABLED_REQ_MESSAGE)).getDocumentElement()));
                CxfPayload<SoapHeader> body = new CxfPayload<>(
                        new ArrayList<SoapHeader>(),
                        elements, null);
                exchange.getIn().setBody(body);
                exchange.getIn(AttachmentMessage.class).addAttachment(MtomTestHelper.REQ_PHOTO_CID,
                        new DataHandler(new ByteArrayDataSource(MtomTestHelper.REQ_PHOTO_DATA, "application/octet-stream")));

                exchange.getIn(AttachmentMessage.class).addAttachment(MtomTestHelper.REQ_IMAGE_CID,
                        new DataHandler(new ByteArrayDataSource(MtomTestHelper.requestJpeg, "image/jpeg")));

            }

        });

        // process response - verify response attachments

        CxfPayload<?> out = exchange.getMessage().getBody(CxfPayload.class);
        assertEquals(1, out.getBody().size());

        DataHandler dr = exchange.getMessage(AttachmentMessage.class).getAttachment(MtomTestHelper.RESP_PHOTO_CID);
        assertEquals("application/octet-stream", dr.getContentType());
        assertArrayEquals(MtomTestHelper.RESP_PHOTO_DATA, IOUtils.readBytesFromStream(dr.getInputStream()));

        dr = exchange.getMessage(AttachmentMessage.class).getAttachment(MtomTestHelper.RESP_IMAGE_CID);
        assertEquals("image/jpeg", dr.getContentType());

        BufferedImage image = ImageIO.read(dr.getInputStream());
        assertEquals(560, image.getWidth());
        assertEquals(300, image.getHeight());

    }

    public static class MyHelloImpl extends HelloImpl implements Hello {

        @Resource
        WebServiceContext ctx;

        @Override
        public void detail(Holder<byte[]> photo, Holder<Image> image) {

            // verify request attachments
            Map<String, DataHandler> map
                    = CastUtils.cast((Map<?, ?>) ctx.getMessageContext().get(MessageContext.INBOUND_MESSAGE_ATTACHMENTS));
            assertEquals(2, map.size());

            DataHandler dh = map.get(MtomTestHelper.REQ_PHOTO_CID);
            assertEquals("application/octet-stream", dh.getContentType());
            byte[] bytes = null;
            try {
                bytes = IOUtils.readBytesFromStream(dh.getInputStream());
            } catch (IOException e) {
                LOG.warn("I/O error reading bytes from stream: {}", e.getMessage(), e);
            }
            assertArrayEquals(MtomTestHelper.REQ_PHOTO_DATA, bytes);

            dh = map.get(MtomTestHelper.REQ_IMAGE_CID);
            assertEquals("image/jpeg", dh.getContentType());

            BufferedImage bufferedImage = null;
            try {
                bufferedImage = ImageIO.read(dh.getInputStream());

            } catch (IOException e) {
                LOG.warn("I/O error reading bytes from stream: {}", e.getMessage(), e);
            }
            assertNotNull(bufferedImage);
            assertEquals(41, bufferedImage.getWidth());
            assertEquals(39, bufferedImage.getHeight());

            // add output attachments
            map = CastUtils.cast((Map<?, ?>) ctx.getMessageContext().get(MessageContext.OUTBOUND_MESSAGE_ATTACHMENTS));

            try {
                DataSource ds = new AttachmentDataSource("image/jpeg", getClass().getResourceAsStream("/Splash.jpg"));
                map.put(MtomTestHelper.RESP_IMAGE_CID, new DataHandler(ds));

            } catch (Exception e) {
                LOG.warn("I/O error: {}", e.getMessage(), e);
            }

            try {
                DataSource ds = new AttachmentDataSource(
                        "application/octet-stream",
                        new ByteArrayInputStream(MtomTestHelper.RESP_PHOTO_DATA));
                map.put(MtomTestHelper.RESP_PHOTO_CID, new DataHandler(ds));

            } catch (Exception e) {
                LOG.warn("I/O error: {}", e.getMessage(), e);
            }

        }
    }
}
