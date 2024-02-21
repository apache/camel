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

import jakarta.xml.ws.Endpoint;
import jakarta.xml.ws.Holder;
import jakarta.xml.ws.soap.SOAPBinding;

import javax.imageio.ImageIO;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test for exercising MTOM enabled end-to-end router in PAYLOAD mode
 */
@ContextConfiguration
@ExtendWith(SpringExtension.class)
public class CxfMtomPOJOProducerTest {

    static int port = CXFTestSupport.getPort1();

    private static final Logger LOG = LoggerFactory.getLogger(CxfMtomPOJOProducerTest.class);

    @Autowired
    protected CamelContext context;
    private Endpoint endpoint;

    @BeforeEach
    public void setUp() throws Exception {
        endpoint = Endpoint.publish("http://localhost:" + port + "/CxfMtomPOJOProducerTest/jaxws-mtom/hello", getImpl());
        SOAPBinding binding = (SOAPBinding) endpoint.getBinding();
        binding.setMTOMEnabled(true);

    }

    @AfterEach
    public void tearDown() throws Exception {
        if (endpoint != null) {
            endpoint.stop();
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testInvokingServiceFromCxfProducer() throws Exception {
        if (MtomTestHelper.isAwtHeadless(null, LOG)) {
            return;
        }

        final Holder<byte[]> photo = new Holder<>(MtomTestHelper.REQ_PHOTO_DATA);
        final Holder<Image> image = new Holder<>(getImage("/java.jpg"));

        Exchange exchange = context.createProducerTemplate().send("direct://testEndpoint", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(new Object[] { photo, image });

            }

        });

        assertEquals(2, exchange.getMessage(AttachmentMessage.class).getAttachments().size(),
                "The attachement size should be 2");

        Object[] result = exchange.getMessage().getBody(Object[].class);
        Holder<byte[]> photo1 = (Holder<byte[]>) result[1];
        assertArrayEquals(MtomTestHelper.RESP_PHOTO_DATA, photo1.value);
        Holder<Image> image1 = (Holder<Image>) result[2];
        assertNotNull(image1.value);
        if (image.value instanceof BufferedImage) {
            assertEquals(560, ((BufferedImage) image1.value).getWidth());
            assertEquals(300, ((BufferedImage) image1.value).getHeight());
        }

    }

    private Image getImage(String name) throws Exception {
        return ImageIO.read(getClass().getResource(name));
    }

    protected Object getImpl() {
        return new HelloImpl();
    }

}
