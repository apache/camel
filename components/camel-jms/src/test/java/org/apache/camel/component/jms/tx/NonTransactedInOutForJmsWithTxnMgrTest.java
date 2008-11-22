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
package org.apache.camel.component.jms.tx;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class NonTransactedInOutForJmsWithTxnMgrTest extends ContextTestSupport {
    private static final transient Log LOG = LogFactory.getLog(NonTransactedInOutForJmsWithTxnMgrTest.class);
    private MyActiveMQConsumer consumer;
    
    public void testJmsNonTransactedInOutWithTxnMgr() throws Exception {
        for (int i = 0; i < 10; i++) {
            String tmpStr = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><aft>" + i + "</aft>";
            Object reply = template.requestBody("activemq:mainStage", tmpStr);
            assertTrue("Received a reply", reply.equals("I was here: " + tmpStr));
            LOG.info("received reply: " + reply);
        }
    }

    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/jms/tx/nonTxInOutJmsTest.xml");
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return SpringCamelContext.springCamelContext(createApplicationContext());
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new SpringRouteBuilder() {
            public void configure() throws Exception {
                from("activemq:queue:mainStage?replyTo=queue:mainStage.reply").to("activemq:queue:request?replyTo=queue:request.reply");
            }
        };
    }

    protected void setUp() throws Exception {
        super.setUp();
        // start the jms consumer here
        consumer = new MyActiveMQConsumer();
        Thread thread = new Thread(consumer);
        thread.start();
    }
    @Override
    protected void tearDown() throws Exception {
        log.debug("tearDown test: " + getName());
        Thread.sleep(2000);
        consumer.close();
        super.tearDown();
    }
}
