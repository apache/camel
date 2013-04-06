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
package org.apache.camel.component.cxf.mtom;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.net.URL;

import javax.imageio.ImageIO;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Holder;
import javax.xml.ws.soap.SOAPBinding;

import org.apache.camel.CamelContext;
import org.apache.camel.component.cxf.CXFTestSupport;
import org.apache.camel.cxf.mtom_feature.Hello;
import org.apache.camel.cxf.mtom_feature.HelloService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertNotNull;

/**
 * Unit test for exercising MTOM enabled end-to-end router in PAYLOAD mode
 * 
 * @version 
 */
@ContextConfiguration
public class CxfMtomRouterPayloadModeTest extends AbstractJUnit4SpringContextTests {
    static int port1 = CXFTestSupport.getPort1();    
    static int port2 = CXFTestSupport.getPort2();    
    
    @Autowired
    protected CamelContext context;
    private Endpoint endpoint;

    @Before
    public void setUp() throws Exception {
        endpoint = Endpoint.publish("http://localhost:" + port2 + "/" 
            + getClass().getSimpleName() + "/jaxws-mtom/hello", getImpl());
        SOAPBinding binding = (SOAPBinding)endpoint.getBinding();
        binding.setMTOMEnabled(true);
        
    }
    
    @After
    public void tearDown() throws Exception {
        if (endpoint != null) {
            endpoint.stop();
        }
    }

    @Test
    public void testInvokingServiceFromCXFClient() throws Exception {        
        if (MtomTestHelper.isAwtHeadless(logger, null)) {
            return;
        }

        Holder<byte[]> photo = new Holder<byte[]>(MtomTestHelper.REQ_PHOTO_DATA);
        Holder<Image> image = new Holder<Image>(getImage("/java.jpg"));

        Hello port = getPort();

        SOAPBinding binding = (SOAPBinding) ((BindingProvider)port).getBinding();
        binding.setMTOMEnabled(true);

        port.detail(photo, image);
        
        MtomTestHelper.assertEquals(MtomTestHelper.RESP_PHOTO_DATA,  photo.value);      
        Assert.assertNotNull(image.value);
        if (image.value instanceof BufferedImage) {
            Assert.assertEquals(560, ((BufferedImage)image.value).getWidth());
            Assert.assertEquals(300, ((BufferedImage)image.value).getHeight());            
        }
        
    }
    
    protected Hello getPort() {
        URL wsdl = getClass().getResource("/mtom.wsdl");
        assertNotNull("WSDL is null", wsdl);

        HelloService service = new HelloService(wsdl, HelloService.SERVICE);
        assertNotNull("Service is null ", service);
        Hello port = service.getHelloPort();
        ((BindingProvider)port).getRequestContext()
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
