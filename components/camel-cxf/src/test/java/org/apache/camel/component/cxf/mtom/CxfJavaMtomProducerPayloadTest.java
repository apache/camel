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

import javax.xml.ws.Holder;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.attachment.AttachmentMessage;
import org.junit.Test;

public class CxfJavaMtomProducerPayloadTest extends CxfMtomConsumerTest {
    protected static final String MTOM_ENDPOINT_URI_MTOM_ENABLE = 
        MTOM_ENDPOINT_URI + "&properties.mtom-enabled=true"
        + "&defaultOperationName=Detail";
    
    @Override
    @SuppressWarnings("unchecked")
    @Test
    public void testInvokingService() throws Exception {   
        if (MtomTestHelper.isAwtHeadless(null, log)) {
            return;
        }

        final Holder<byte[]> photo = new Holder<>("RequestFromCXF".getBytes("UTF-8"));
        final Holder<Image> image = new Holder<>(getImage("/java.jpg"));
        
        Exchange exchange = context.createProducerTemplate().send(MTOM_ENDPOINT_URI_MTOM_ENABLE, new Processor() {

            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody(new Object[] {photo, image});
                
            }
            
        });
        
        // Make sure we don't put the attachement into out message
        assertEquals("The attachement size should be 0 ", 0, exchange.getOut(AttachmentMessage.class).getAttachments().size());
        
        Object[] result = exchange.getOut().getBody(Object[].class);
        
        Holder<byte[]> photo1 = (Holder<byte[]>) result[1];
            
        Holder<Image> image1 = (Holder<Image>) result[2];
        
        assertEquals("ResponseFromCamel", new String(photo1.value, "UTF-8"));
        assertNotNull(image1.value);
        
    }

}
