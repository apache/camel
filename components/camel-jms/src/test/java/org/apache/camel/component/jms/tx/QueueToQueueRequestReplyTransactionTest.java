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
package org.apache.camel.component.jms.tx;

import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Policy;
import org.apache.camel.spring.spi.SpringTransactionPolicy;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test case derived from: http://camel.apache.org/transactional-client.html and Martin Krasser's sample:
 * http://www.nabble.com/JMS-Transactions---How-To-td15168958s22882.html#a15198803
 * <p/>
 * NOTE: had to split into separate test classes as I was unable to fully tear down and isolate the test cases, I'm not
 * sure why, but as soon as we know the Transaction classes can be joined into one.
 */
@Tags({ @Tag("not-parallel"), @Tag("spring"), @Tag("tx") })
public class QueueToQueueRequestReplyTransactionTest extends AbstractTransactionTest {

    @Test
    public void testRollbackUsingXmlQueueToQueueRequestReplyUsingDynamicMessageSelector() throws Exception {
        final ConditionalExceptionProcessor cp = new ConditionalExceptionProcessor(5);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                Policy required = context().getRegistry().lookupByNameAndType("PROPAGATION_REQUIRED_POLICY",
                        SpringTransactionPolicy.class);

                from("activemq:queue:AbstractTransactionTest").policy(required).process(cp)
                        .to("activemq-1:queue:AbstractTransactionTest.bar?replyTo=queue:AbstractTransactionTest.bar.reply");

                from("activemq-1:queue:AbstractTransactionTest.bar").process(e -> {
                    String request = e.getIn().getBody(String.class);
                    Message out = e.getMessage();
                    String selectorValue = e.getIn().getHeader("camelProvider", String.class);
                    if (selectorValue != null) {
                        out.setHeader("camelProvider", selectorValue);
                    }
                    out.setBody("Re: " + request);
                });
            }
        });

        for (int i = 0; i < 5; ++i) {
            Object reply = template.requestBody("activemq:queue:AbstractTransactionTest", "blah" + i);
            assertEquals("Re: blah" + i, reply, "Received unexpected reply at item " + i);
            assertNull(cp.getErrorMessage(), cp.getErrorMessage());
        }
    }

}
