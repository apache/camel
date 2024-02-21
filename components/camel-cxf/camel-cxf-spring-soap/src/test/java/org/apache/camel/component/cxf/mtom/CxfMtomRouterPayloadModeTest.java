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
import java.net.URL;

import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Endpoint;
import jakarta.xml.ws.Holder;
import jakarta.xml.ws.soap.SOAPBinding;

import javax.imageio.ImageIO;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.cxf.mtom_feature.Hello;
import org.apache.camel.cxf.mtom_feature.HelloService;
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
public class CxfMtomRouterPayloadModeTest {

    static int port1 = CXFTestSupport.getPort1();
    static int port2 = CXFTestSupport.getPort2();

    private static final Logger LOG = LoggerFactory.getLogger(CxfMtomRouterPayloadModeTest.class);

    @Autowired
    protected CamelContext context;
    private Endpoint endpoint;

    @BeforeEach
    public void setUp() throws Exception {
        endpoint = Endpoint.publish("http://localhost:" + port2 + "/"
                                    + getClass().getSimpleName() + "/jaxws-mtom/hello",
                getImpl());
        SOAPBinding binding = (SOAPBinding) endpoint.getBinding();
        binding.setMTOMEnabled(true);

    }

    @AfterEach
    public void tearDown() throws Exception {
        if (endpoint != null) {
            endpoint.stop();
        }
    }

    @Test
    public void testInvokingServiceFromCXFClient() throws Exception {
        if (MtomTestHelper.isAwtHeadless(null, LOG)) {
            return;
        }

        Holder<byte[]> photo = new Holder<>(MtomTestHelper.REQ_PHOTO_DATA);
        Holder<Image> image = new Holder<>(getImage("/java.jpg"));

        Hello port = getPort();

        SOAPBinding binding = (SOAPBinding) ((BindingProvider) port).getBinding();
        binding.setMTOMEnabled(true);

        port.detail(photo, image);

        assertArrayEquals(MtomTestHelper.RESP_PHOTO_DATA, photo.value);
        assertNotNull(image.value);
        if (image.value instanceof BufferedImage) {
            assertEquals(560, ((BufferedImage) image.value).getWidth());
            assertEquals(300, ((BufferedImage) image.value).getHeight());
        }

    }

    protected Hello getPort() {
        URL wsdl = getClass().getResource("/mtom.wsdl");
        assertNotNull(wsdl, "WSDL is null");

        HelloService service = new HelloService(wsdl, HelloService.SERVICE);
        assertNotNull(service, "Service is null");
        Hello port = service.getHelloPort();
        ((BindingProvider) port).getRequestContext()
                .put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                        "http://localhost:" + port1 + "/CxfMtomRouterPayloadModeTest/jaxws-mtom/hello");
        return port;
    }

    private Image getImage(String name) throws Exception {
        return ImageIO.read(getClass().getResource(name));
    }

    protected Object getImpl() {
        return new HelloImpl();
    }

}
