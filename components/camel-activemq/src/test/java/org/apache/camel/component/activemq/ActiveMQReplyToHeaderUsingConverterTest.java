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
package org.apache.camel.component.activemq;

import org.apache.activemq.command.ActiveMQQueue;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import static org.apache.camel.component.activemq.ActiveMQComponent.activeMQComponent;
import org.apache.camel.component.mock.AssertionClause;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.jms.Destination;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @version $Revision: 538973 $
 */
public class ActiveMQReplyToHeaderUsingConverterTest extends ContextTestSupport {
    private static final transient Log LOG = LogFactory.getLog(ActiveMQReplyToHeaderUsingConverterTest.class);

    protected Object expectedBody = "<time>" + new Date() + "</time>";
    protected String replyQueueName = "queue://test.my.reply.queue";
    protected String correlationID = "ABC-123";
    protected String messageType = getClass().getName();

    public void testSendingAMessageFromCamelSetsCustomJmsHeaders() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);

        resultEndpoint.expectedBodiesReceived(expectedBody);
        AssertionClause firstMessage = resultEndpoint.message(0);
        firstMessage.header("cheese").isEqualTo(123);
        firstMessage.header("JMSCorrelationID").isEqualTo(correlationID);
        firstMessage.header("JMSReplyTo").isEqualTo(ActiveMQConverter.toDestination(replyQueueName));
        firstMessage.header("JMSType").isEqualTo(messageType);

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put("cheese", 123);
        headers.put("JMSReplyTo", replyQueueName);
        headers.put("JMSCorrelationID", correlationID);
        headers.put("JMSType", messageType);
        template.sendBodyAndHeaders("activemq:test.a", expectedBody, headers);

        resultEndpoint.assertIsSatisfied();

        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        Object replyTo = exchange.getIn().getHeader("JMSReplyTo");
        LOG.info("Reply to is: " + replyTo);
        Destination destination = assertIsInstanceOf(Destination.class, replyTo);
        assertEquals("ReplyTo", replyQueueName, destination.toString());
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        // START SNIPPET: example
        camelContext.addComponent("activemq", activeMQComponent("vm://localhost?broker.persistent=false"));
        // END SNIPPET: example

        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("activemq:test.a").to("activemq:test.b");

                from("activemq:test.b").to("mock:result");
            }
        };
    }
}