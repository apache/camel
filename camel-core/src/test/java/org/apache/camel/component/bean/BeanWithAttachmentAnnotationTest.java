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
package org.apache.camel.component.bean;

import java.util.Map;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.naming.Context;

import org.apache.camel.Attachment;
import org.apache.camel.AttachmentObjects;
import org.apache.camel.Attachments;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultAttachment;
import org.apache.camel.util.jndi.JndiContext;

public class BeanWithAttachmentAnnotationTest extends ContextTestSupport {
    
    public void testBeanWithOldAnnotationAndExchangeTest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("attachment");
        
        template.send("direct:inOld", new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                Message m = exchange.getIn();
                m.addAttachmentObject("attachment", new DefaultAttachment(new FileDataSource("src/test/org/apache/camel/component/bean/BeanWithAttachmentAnnotationTest.java")));
            }
            
        });

        mock.assertIsSatisfied();
    }

    public void testBeanWithAnnotationAndExchangeTest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("attachment");
        
        template.send("direct:in", new Processor() {

            public void process(Exchange exchange) throws Exception {
                exchange.setPattern(ExchangePattern.InOut);
                Message m = exchange.getIn();
                m.addAttachmentObject("attachment", new DefaultAttachment(new FileDataSource("src/test/org/apache/camel/component/bean/BeanWithAttachmentAnnotationTest.java")));
            }
            
        });

        mock.assertIsSatisfied();
    }

    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        answer.bind("processorOld", new AttachmentProcessorOld());
        answer.bind("processor", new AttachmentProcessor());
        return answer;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:in").to("bean:processor").to("mock:result");
                from("direct:inOld").to("bean:processorOld").to("mock:result");
            }
        };
    }

    public static class AttachmentProcessorOld {
        // START SNIPPET: e1
        public String doSomething(@Attachments Map<String, DataHandler> attachments) {
            assertNotNull(attachments);
            assertEquals("The attache size is wrong", 1, attachments.size());
            String key = attachments.keySet().iterator().next();
            assertNotNull(key);
            assertNotNull(attachments.get(key));
            DataHandler handler = attachments.get(key);
            assertNotNull(handler);
            assertTrue("The data source should be a instance of FileDataSource", handler.getDataSource() instanceof FileDataSource);     
            return key;
        }
        // END SNIPPET: e1
    }

    public static class AttachmentProcessor {
        // START SNIPPET: e2
        public String doSomething(@AttachmentObjects Map<String, Attachment> attachments) {
            assertNotNull(attachments);
            assertEquals("The attache size is wrong", 1, attachments.size());
            String key = attachments.keySet().iterator().next();
            assertNotNull(key);
            assertNotNull(attachments.get(key));
            Attachment attachment = attachments.get(key);
            assertNotNull(attachment);
            DataHandler handler = attachment.getDataHandler();
            assertNotNull(handler);
            assertTrue("The data source should be a instance of FileDataSource", handler.getDataSource() instanceof FileDataSource);     
            return key;
        }
        // END SNIPPET: e2
    }
}
