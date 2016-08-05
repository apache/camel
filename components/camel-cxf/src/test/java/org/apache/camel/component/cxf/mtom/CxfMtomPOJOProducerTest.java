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

import javax.imageio.ImageIO;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Holder;
import javax.xml.ws.soap.SOAPBinding;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.cxf.CXFTestSupport;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import static org.junit.Assert.assertEquals;

/**
 * Unit test for exercising MTOM enabled end-to-end router in PAYLOAD mode
 * 
 * @version 
 */
@ContextConfiguration
public class CxfMtomPOJOProducerTest extends AbstractJUnit4SpringContextTests {
    static int port = CXFTestSupport.getPort1();
    
    @Autowired
    protected CamelContext context;
    private Endpoint endpoint;

    @Before
    public void setUp() throws Exception {
        endpoint = Endpoint.publish("http://localhost:" + port + "/CxfMtomPOJOProducerTest/jaxws-mtom/hello", getImpl());
        SOAPBinding binding = (SOAPBinding)endpoint.getBinding();
        binding.setMTOMEnabled(true);
        
    }
    
    @After
    public void tearDown() throws Exception {
        if (endpoint != null) {
            endpoint.stop();
        }
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testInvokingServiceFromCxfProducer() throws Exception {
        if (MtomTestHelper.isAwtHeadless(logger, null)) {
            return;
        }

        final Holder<byte[]> photo = new Holder<byte[]>(MtomTestHelper.REQ_PHOTO_DATA);
        final Holder<Image> image = new Holder<Image>(getImage("/java.jpg"));
        
        Exchange exchange = context.createProducerTemplate().send("direct://testEndpoint", new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(new Object[] {photo, image});
                
            }
            
        });
        
        assertEquals("The attachement size should be 2 ", 2, exchange.getOut().getAttachments().size());
        
        Object[] result = exchange.getOut().getBody(Object[].class);
        Holder<byte[]> photo1 = (Holder<byte[]>) result[1];
        MtomTestHelper.assertEquals(MtomTestHelper.RESP_PHOTO_DATA,  photo1.value);      
        Holder<Image> image1 = (Holder<Image>) result[2];
        Assert.assertNotNull(image1.value);
        if (image.value instanceof BufferedImage) {
            Assert.assertEquals(560, ((BufferedImage)image1.value).getWidth());
            Assert.assertEquals(300, ((BufferedImage)image1.value).getHeight());            
        }
        
    }
    
    private Image getImage(String name) throws Exception {
        return ImageIO.read(getClass().getResource(name));
    }
    
    
    protected Object getImpl() {
        return new HelloImpl();
    }
    

}
