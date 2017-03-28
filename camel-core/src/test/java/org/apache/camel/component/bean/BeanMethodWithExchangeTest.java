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

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.naming.Context;

import org.apache.camel.Attachment;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultAttachment;
import org.apache.camel.util.jndi.JndiContext;

public class BeanMethodWithExchangeTest extends ContextTestSupport {
    
    public void testBeanWithAnnotationAndExchangeTest() throws Exception {
        Exchange result = template.request("direct:start1", new Processor() {

            public void process(Exchange exchange) throws Exception {
                Message m = exchange.getIn();
                m.addAttachment("attachment", new DataHandler(new FileDataSource("src/test/org/apache/camel/component/bean/BeanWithAttachmentAnnotationTest.java")));
            }
            
        });
        
        assertTrue(result.getOut().getAttachmentObjects().containsKey("attachment2"));
        assertTrue(result.getOut().getAttachments().containsKey("attachment1"));
        assertEquals("attachmentValue1", result.getOut().getAttachmentObjects().get("attachment1").getHeader("attachmentHeader1"));
        assertFalse(result.getOut().getAttachments().containsKey("attachment"));

    }

    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        answer.bind("processor", new AttachmentProcessor());
        return answer;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start1").to("bean:processor");
            }
        };
    }

    public static class AttachmentProcessor {
        public void doSomething(Exchange exchange) {
            Attachment att = new DefaultAttachment(new FileDataSource("src/test/org/apache/camel/component/bean/BeanMethodWithExchangeTest.java"));
            att.addHeader("attachmentHeader1", "attachmentValue1");
            exchange.getOut().addAttachmentObject("attachment1", att);
            exchange.getOut().addAttachment("attachment2", new DataHandler(new FileDataSource("src/test/org/apache/camel/component/bean/BeanMethodWithExchangeTest.java")));
        }
       
    }

}
