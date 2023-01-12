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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.activation.DataHandler;
import jakarta.mail.util.ByteArrayDataSource;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.xpath.XPathConstants;

import org.w3c.dom.Element;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.cxf.common.CxfPayload;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.helpers.XPathUtils;
import org.apache.cxf.staxutils.StaxUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for exercising MTOM feature of a CxfConsumer in PAYLOAD mode
 */
@ContextConfiguration
@ExtendWith(SpringExtension.class)
public class CxfMtomConsumerPayloadModeTest {

    static int port = CXFTestSupport.getPort1();

    private static final Logger LOG = LoggerFactory.getLogger(CxfMtomConsumerPayloadModeTest.class);

    @Autowired
    protected CamelContext context;

    @Test
    public void testConsumer() throws Exception {
        if (MtomTestHelper.isAwtHeadless(null, LOG)) {
            return;
        }

        context.createProducerTemplate().send("cxf:bean:consumerEndpoint", new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                List<Source> elements = new ArrayList<>();
                elements.add(new DOMSource(StaxUtils.read(new StringReader(getRequestMessage())).getDocumentElement()));
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
    }

    // START SNIPPET: consumer
    public static class MyProcessor implements Processor {

        @Override
        @SuppressWarnings("unchecked")
        public void process(Exchange exchange) throws Exception {
            CxfPayload<SoapHeader> in = exchange.getIn().getBody(CxfPayload.class);

            // verify request
            assertEquals(1, in.getBody().size());

            Map<String, String> ns = new HashMap<>();
            ns.put("ns", MtomTestHelper.SERVICE_TYPES_NS);
            ns.put("xop", MtomTestHelper.XOP_NS);

            XPathUtils xu = new XPathUtils(ns);
            Element body = new XmlConverter().toDOMElement(in.getBody().get(0));
            Element ele = (Element) xu.getValue("//ns:Detail/ns:photo/xop:Include", body,
                    XPathConstants.NODE);
            String photoId = ele.getAttribute("href").substring(4); // skip "cid:"
            assertEquals(MtomTestHelper.REQ_PHOTO_CID, photoId);

            ele = (Element) xu.getValue("//ns:Detail/ns:image/xop:Include", body,
                    XPathConstants.NODE);
            String imageId = ele.getAttribute("href").substring(4); // skip "cid:"
            assertEquals(MtomTestHelper.REQ_IMAGE_CID, imageId);

            DataHandler dr = exchange.getIn(AttachmentMessage.class).getAttachment(photoId);
            assertEquals("application/octet-stream", dr.getContentType());
            assertArrayEquals(MtomTestHelper.REQ_PHOTO_DATA, IOUtils.readBytesFromStream(dr.getInputStream()));

            dr = exchange.getIn(AttachmentMessage.class).getAttachment(imageId);
            assertEquals("image/jpeg", dr.getContentType());
            assertArrayEquals(MtomTestHelper.requestJpeg, IOUtils.readBytesFromStream(dr.getInputStream()));

            // create response
            List<Source> elements = new ArrayList<>();
            elements.add(new DOMSource(StaxUtils.read(new StringReader(MtomTestHelper.RESP_MESSAGE)).getDocumentElement()));
            CxfPayload<SoapHeader> sbody = new CxfPayload<>(
                    new ArrayList<SoapHeader>(),
                    elements, null);
            exchange.getMessage().setBody(sbody);
            exchange.getMessage(AttachmentMessage.class).addAttachment(MtomTestHelper.RESP_PHOTO_CID,
                    new DataHandler(new ByteArrayDataSource(MtomTestHelper.RESP_PHOTO_DATA, "application/octet-stream")));

            exchange.getMessage(AttachmentMessage.class).addAttachment(MtomTestHelper.RESP_IMAGE_CID,
                    new DataHandler(new ByteArrayDataSource(MtomTestHelper.responseJpeg, "image/jpeg")));

        }
    }
    // END SNIPPET: consumer

    protected String getRequestMessage() {
        return MtomTestHelper.REQ_MESSAGE;
    }

}
