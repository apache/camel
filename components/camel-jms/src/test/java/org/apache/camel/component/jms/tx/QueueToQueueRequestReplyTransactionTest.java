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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.spi.Policy;
import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.apache.log4j.Logger;

/**
 * Test case derived from:
 * http://activemq.apache.org/camel/transactional-client.html and Martin
 * Krasser's sample:
 * http://www.nabble.com/JMS-Transactions---How-To-td15168958s22882.html#a15198803
 * <p/>
 * NOTE: had to split into separate test classes as I was unable to fully tear
 * down and isolate the test cases, I'm not sure why, but as soon as we know the
 * Transaction classes can be joined into one.
 *
 * @author Kevin Ross
 */
public class QueueToQueueRequestReplyTransactionTest extends AbstractTransactionTest {

    private Logger log = Logger.getLogger(getClass());

    public void testRollbackUsingXmlQueueToQueueRequestReplyUsingDynamicMessageSelector() throws Exception {

        JmsComponent c = (JmsComponent)context.getComponent("activemq");
        JmsComponent c1 = (JmsComponent)context.getComponent("activemq-1");
        final ConditionalExceptionProcessor cp = new ConditionalExceptionProcessor(10);
        context.addRoutes(new SpringRouteBuilder() {
            @Override
            public void configure() throws Exception {
                Policy required = bean(SpringTransactionPolicy.class, "PROPAGATION_REQUIRED_POLICY");
                from("activemq:queue:foo?replyTo=queue:foo.reply").policy(required).process(cp).to("activemq-1:queue:bar?replyTo=queue:bar.reply");
                from("activemq-1:queue:bar").process(new Processor() {
                    public void process(Exchange e) {
                        String request = e.getIn().getBody(String.class);
                        Message out = e.getOut(true);
                        String selectorValue = e.getIn().getHeader("camelProvider", String.class);
                        if (selectorValue != null) {
                            out.setHeader("camelProvider", selectorValue);
                        }
                        out.setBody("Re: " + request);
                    }
                });
            }
        });

        for (int i = 0; i < 10; ++i) {
            Object reply = template.requestBody("activemq:queue:foo", "blah" + i);
            assertTrue("Received unexpeced reply", reply.equals("Re: blah" + i));
            assertTrue(cp.getErrorMessage(), cp.getErrorMessage() == null);
        }
    }
/*
 * This is a working test but is commented out because there is bug in that ConditionalExceptionProcessor 
 * gets somehow reused among different tests, which it should not and then the second test always get its request 
 * flow rolled back
 * 
 * I didn't split this test into two separate tests as I think this will be a good reminder of the problem that
 * needs fixing
 * 
 * The bellow log crearly shows the same processor reused between tests
 *  testRollbackUsingXmlQueueToQueueRequestReplyUsingDynamicMessageSelector()
 *  org.apache.camel.component.jms.tx.ConditionalExceptionProcessor@63a721; getCount() = 1
 *  org.apache.camel.component.jms.tx.ConditionalExceptionProcessor@63a721; getCount() = 2
 *       
 *  testRollbackUsingXmlQueueToQueueRequestReplyUsingMessageSelectorPerProducer()
 *  org.apache.camel.component.jms.tx.ConditionalExceptionProcessor@63a721; getCount() = 3
 *  org.apache.camel.component.jms.tx.ConditionalExceptionProcessor@63a721; getCount() = 4
*/
    /*
    public void testRollbackUsingXmlQueueToQueueRequestReplyUsingMessageSelectorPerProducer() throws Exception {

        JmsComponent c = (JmsComponent)context.getComponent("activemq");
        c.getConfiguration().setReplyToDestinationSelectorName("camelProvider");
        JmsComponent c1 = (JmsComponent)context.getComponent("activemq-1");
        c1.getConfiguration().setReplyToDestinationSelectorName("camelProvider");
        
        context.addRoutes(new SpringRouteBuilder() {
            @Override
            public void configure() throws Exception {
                Policy required = bean(SpringTransactionPolicy.class, "PROPAGATION_REQUIRED_POLICY");
                from("activemq:queue:foo?replyTo=queue:foo.reply").policy(required).process(new ConditionalExceptionProcessor()).to("activemq-1:queue:bar?replyTo=queue:bar.reply");
                from("activemq-1:queue:bar").process(new Processor() {
                    public void process(Exchange e) {
                        String request = e.getIn().getBody(String.class);
                        Message out = e.getOut(true);
                        String selectorValue = e.getIn().getHeader("camelProvider", String.class);
                        out.setHeader("camelProvider", selectorValue);
                        out.setBody("Re: " + request);
                    }
                });
            }
        });

        Object reply = template.requestBody("activemq:queue:foo", "blah");
        assertTrue("Received unexpeced reply", reply.equals("Re: blah"));
    }
    */

}
