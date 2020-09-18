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

import java.awt.*;
import java.net.URL;
import java.util.List;

import javax.imageio.ImageIO;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Holder;
import javax.xml.ws.soap.SOAPBinding;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.cxf.mtom_feature.Hello;
import org.apache.camel.cxf.mtom_feature.HelloService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CxfMtomConsumerTest extends CamelTestSupport {
    protected static final String MTOM_ENDPOINT_ADDRESS = "http://localhost:"
                                                          + CXFTestSupport.getPort1() + "/CxfMtomConsumerTest/jaxws-mtom/hello";
    protected static final String MTOM_ENDPOINT_URI = "cxf://" + MTOM_ENDPOINT_ADDRESS
                                                      + "?serviceClass=org.apache.camel.cxf.mtom_feature.Hello";

    private static final Logger LOG = LoggerFactory.getLogger(CxfMtomConsumerTest.class);

    private final QName serviceName = new QName("http://apache.org/camel/cxf/mtom_feature", "HelloService");

    @Override
    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {
            public void configure() {
                from(MTOM_ENDPOINT_URI).process(new Processor() {
                    @SuppressWarnings("unchecked")
                    public void process(final Exchange exchange) throws Exception {
                        AttachmentMessage in = exchange.getIn(AttachmentMessage.class);
                        assertEquals(0, in.getAttachments().size(), "We should not get any attachements here.");
                        assertEquals("application/xop+xml", in.getHeader("Content-Type"), "Get a wrong Content-Type header");
                        // Get the parameter list
                        List<?> parameter = in.getBody(List.class);
                        // Get the operation name
                        Holder<byte[]> photo = (Holder<byte[]>) parameter.get(0);
                        assertNotNull(photo.value, "The photo should not be null");
                        assertEquals("Should get the right request", new String(photo.value, "UTF-8"),
                                "RequestFromCXF");
                        photo.value = "ResponseFromCamel".getBytes("UTF-8");
                        Holder<Image> image = (Holder<Image>) parameter.get(1);
                        assertNotNull(image.value, "We should get the image here");
                        // set the holder message back    
                        exchange.getMessage().setBody(new Object[] { null, photo, image });

                    }
                });
            }
        };
    }

    private Hello getPort() {
        URL wsdl = getClass().getResource("/mtom.wsdl");
        assertNotNull(wsdl, "WSDL is null");

        HelloService service = new HelloService(wsdl, serviceName);
        assertNotNull(service, "Service is null");
        Hello port = service.getHelloPort();

        ((BindingProvider) port).getRequestContext()
                .put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY,
                        MTOM_ENDPOINT_ADDRESS);
        return port;
    }

    protected Image getImage(String name) throws Exception {
        return ImageIO.read(getClass().getResource(name));
    }

    @Test
    public void testInvokingService() throws Exception {
        if (MtomTestHelper.isAwtHeadless(null, LOG)) {
            return;
        }

        Holder<byte[]> photo = new Holder<>("RequestFromCXF".getBytes("UTF-8"));
        Holder<Image> image = new Holder<>(getImage("/java.jpg"));

        Hello port = getPort();

        SOAPBinding binding = (SOAPBinding) ((BindingProvider) port).getBinding();
        binding.setMTOMEnabled(true);

        port.detail(photo, image);

        assertEquals("ResponseFromCamel", new String(photo.value, "UTF-8"));
        assertNotNull(image.value);

    }

}
