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

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.mail.util.ByteArrayDataSource;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.component.cxf.CxfPayload;
import org.apache.camel.cxf.mtom_feature.Hello;
import org.apache.cxf.attachment.AttachmentDataSource;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.staxutils.StaxUtils;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

/**
 * 
 * Unit test for exercising SOAP with Attachment (SwA) feature of a CxfProducer in PAYLOAD mode.  
 * That is, testing attachment with MTOM optimization off.
 *  
 */
@ContextConfiguration
public class CxfMtomDisabledProducerPayloadModeTest extends CxfMtomProducerPayloadModeTest {

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
        if (MtomTestHelper.isAwtHeadless(logger, null)) {
            return;
        }

        Exchange exchange = context.createProducerTemplate().send("direct:testEndpoint", new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                List<Source> elements = new ArrayList<>();
                elements.add(new DOMSource(StaxUtils.read(new StringReader(MtomTestHelper.MTOM_DISABLED_REQ_MESSAGE)).getDocumentElement()));
                CxfPayload<SoapHeader> body = new CxfPayload<>(new ArrayList<SoapHeader>(),
                    elements, null);
                exchange.getIn().setBody(body);
                exchange.getIn(AttachmentMessage.class).addAttachment(MtomTestHelper.REQ_PHOTO_CID,
                    new DataHandler(new ByteArrayDataSource(MtomTestHelper.REQ_PHOTO_DATA, "application/octet-stream")));

                exchange.getIn(AttachmentMessage.class).addAttachment(MtomTestHelper.REQ_IMAGE_CID,
                    new DataHandler(new ByteArrayDataSource(MtomTestHelper.requestJpeg, "image/jpeg")));

            }
            
        });
        
        // process response - verify response attachments
        
        CxfPayload<?> out = exchange.getOut().getBody(CxfPayload.class);
        Assert.assertEquals(1, out.getBody().size());
        

        DataHandler dr = exchange.getOut(AttachmentMessage.class).getAttachment(MtomTestHelper.RESP_PHOTO_CID);
        Assert.assertEquals("application/octet-stream", dr.getContentType());
        MtomTestHelper.assertEquals(MtomTestHelper.RESP_PHOTO_DATA, IOUtils.readBytesFromStream(dr.getInputStream()));
   
        dr = exchange.getOut(AttachmentMessage.class).getAttachment(MtomTestHelper.RESP_IMAGE_CID);
        Assert.assertEquals("image/jpeg", dr.getContentType());
        
        BufferedImage image = ImageIO.read(dr.getInputStream());
        Assert.assertEquals(560, image.getWidth());
        Assert.assertEquals(300, image.getHeight());
        
    }
 
    public static class MyHelloImpl extends HelloImpl implements Hello {
        
        @Resource
        WebServiceContext ctx;
        
        @Override
        public void detail(Holder<byte[]> photo, Holder<Image> image) {
            
            // verify request attachments
            Map<String, DataHandler> map 
                = CastUtils.cast((Map<?, ?>)ctx.getMessageContext().get(MessageContext.INBOUND_MESSAGE_ATTACHMENTS));
            Assert.assertEquals(2, map.size());
            
            DataHandler dh = map.get(MtomTestHelper.REQ_PHOTO_CID);
            Assert.assertEquals("application/octet-stream", dh.getContentType());
            byte[] bytes = null;
            try {
                bytes = IOUtils.readBytesFromStream(dh.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
            MtomTestHelper.assertEquals(bytes, MtomTestHelper.REQ_PHOTO_DATA);
            
            dh = map.get(MtomTestHelper.REQ_IMAGE_CID);
            Assert.assertEquals("image/jpeg", dh.getContentType());

            BufferedImage bufferedImage = null;
            try {  
                bufferedImage = ImageIO.read(dh.getInputStream());

            } catch (IOException e) {
                e.printStackTrace();
            }
            Assert.assertNotNull(bufferedImage);
            Assert.assertEquals(41, bufferedImage.getWidth());
            Assert.assertEquals(39, bufferedImage.getHeight());  

            // add output attachments
            map = CastUtils.cast((Map<?, ?>)ctx.getMessageContext().get(MessageContext.OUTBOUND_MESSAGE_ATTACHMENTS)); 

            try {
                DataSource ds = new AttachmentDataSource("image/jpeg", getClass().getResourceAsStream("/Splash.jpg"));
                map.put(MtomTestHelper.RESP_IMAGE_CID, new DataHandler(ds)); 
                
            } catch (IOException e) {
                e.printStackTrace();
            }
            
            try {
                DataSource ds = new AttachmentDataSource("application/octet-stream", 
                                                         new ByteArrayInputStream(MtomTestHelper.RESP_PHOTO_DATA));
                map.put(MtomTestHelper.RESP_PHOTO_CID, new DataHandler(ds)); 
                
            } catch (IOException e) {
                e.printStackTrace();
            }
            
        }
    }
}
