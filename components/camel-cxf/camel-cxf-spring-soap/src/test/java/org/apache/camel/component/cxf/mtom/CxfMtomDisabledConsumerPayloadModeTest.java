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
import java.util.List;

import jakarta.activation.DataHandler;
import jakarta.mail.util.ByteArrayDataSource;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.cxf.common.CxfPayload;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.staxutils.StaxUtils;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for exercising SOAP with Attachment (SwA) feature of a CxfConsumer in PAYLOAD mode. That is, testing
 * attachment with MTOM optimization off.
 */
@ContextConfiguration
public class CxfMtomDisabledConsumerPayloadModeTest extends CxfMtomConsumerPayloadModeTest {
    static int port = CXFTestSupport.getPort1();

    @Override
    protected String getRequestMessage() {
        return MtomTestHelper.MTOM_DISABLED_REQ_MESSAGE;
    }

    public static class MyProcessor implements Processor {

        @Override
        @SuppressWarnings("unchecked")
        public void process(Exchange exchange) throws Exception {
            CxfPayload<SoapHeader> in = exchange.getIn().getBody(CxfPayload.class);

            // verify request
            assertEquals(1, in.getBody().size());

            DataHandler dr = exchange.getIn(AttachmentMessage.class).getAttachment(MtomTestHelper.REQ_PHOTO_CID);
            assertEquals("application/octet-stream", dr.getContentType());
            assertArrayEquals(MtomTestHelper.REQ_PHOTO_DATA, IOUtils.readBytesFromStream(dr.getInputStream()));

            dr = exchange.getIn(AttachmentMessage.class).getAttachment(MtomTestHelper.REQ_IMAGE_CID);
            assertEquals("image/jpeg", dr.getContentType());
            assertArrayEquals(MtomTestHelper.requestJpeg, IOUtils.readBytesFromStream(dr.getInputStream()));

            // create response
            List<Source> elements = new ArrayList<>();
            elements.add(new DOMSource(
                    StaxUtils.read(new StringReader(MtomTestHelper.MTOM_DISABLED_RESP_MESSAGE)).getDocumentElement()));
            CxfPayload<SoapHeader> body = new CxfPayload<>(
                    new ArrayList<SoapHeader>(),
                    elements, null);
            exchange.getMessage().setBody(body);
            exchange.getMessage(AttachmentMessage.class).addAttachment(MtomTestHelper.RESP_PHOTO_CID,
                    new DataHandler(new ByteArrayDataSource(MtomTestHelper.RESP_PHOTO_DATA, "application/octet-stream")));

            exchange.getMessage(AttachmentMessage.class).addAttachment(MtomTestHelper.RESP_IMAGE_CID,
                    new DataHandler(new ByteArrayDataSource(MtomTestHelper.responseJpeg, "image/jpeg")));

        }
    }

}
