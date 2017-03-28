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
package org.apache.camel.component.sjms2.producer;

import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.TextMessage;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms2.support.Jms2TestSupport;
import org.junit.Test;

public class InOnlyQueueProducerTest extends Jms2TestSupport {
    
    private static final String TEST_DESTINATION_NAME = "sync.queue.producer.test";
    
    public InOnlyQueueProducerTest() {
    }
    
    @Override
    protected boolean useJmx() {
        return false;
    }

    @Test
    public void testInOnlyQueueProducer() throws Exception {
        MessageConsumer mc = createQueueConsumer(TEST_DESTINATION_NAME);
        assertNotNull(mc);
        final String expectedBody = "Hello World!";
        MockEndpoint mock = getMockEndpoint("mock:result");

        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(expectedBody);

        template.sendBody("direct:start", expectedBody);
        Message message = mc.receive(5000);
        assertNotNull(message);
        assertTrue(message instanceof TextMessage);
        
        TextMessage tm = (TextMessage) message;
        String text = tm.getText();
        assertNotNull(text);
        
        template.sendBody("direct:finish", text);
        
        mock.assertIsSatisfied();
        mc.close();

    }

    /**
     * @see org.apache.camel.test.junit4.CamelTestSupport#createRouteBuilder()
     *
     * @return
     * @throws Exception
     */
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .to("sjms2:queue:" + TEST_DESTINATION_NAME);
                
                from("direct:finish")
                    .to("log:test.log.1?showBody=true", "mock:result");
            }
        };
    }
}
