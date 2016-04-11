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
package org.apache.camel.management;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.api.management.mbean.ManagedAggregateProcessorMBean;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.AggregateController;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.DefaultAggregateController;
import org.junit.Test;

/**
 *
 */
public class ManagedAggregateControllerTest extends ManagementTestSupport {

    private AggregateController controller = new DefaultAggregateController();

    @Test
    public void testForceCompletionOfAll() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=processors,name=\"myAggregator\"");
        assertTrue(mbeanServer.isRegistered(on));

        getMockEndpoint("mock:aggregated").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "test1", "id", "1");
        template.sendBodyAndHeader("direct:start", "test2", "id", "2");
        template.sendBodyAndHeader("direct:start", "test3", "id", "1");
        template.sendBodyAndHeader("direct:start", "test4", "id", "2");

        getMockEndpoint("mock:aggregated").expectedMessageCount(2);
        getMockEndpoint("mock:aggregated").expectedBodiesReceivedInAnyOrder("test1test3", "test2test4");
        getMockEndpoint("mock:aggregated").expectedPropertyReceived(Exchange.AGGREGATED_COMPLETED_BY, "force");

        Integer pending = (Integer) mbeanServer.invoke(on, "aggregationRepositoryGroups", null, null);
        assertEquals(2, pending.intValue());

        Integer groups = (Integer) mbeanServer.invoke(on, "forceCompletionOfAllGroups", null, null);
        assertEquals(2, groups.intValue());

        assertMockEndpointsSatisfied();

        Long completed = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
        assertEquals(4, completed.longValue());

        completed = (Long) mbeanServer.getAttribute(on, "TotalCompleted");
        assertEquals(2, completed.longValue());

        Long in = (Long) mbeanServer.getAttribute(on, "TotalIn");
        assertEquals(4, in.longValue());

        Long byForced = (Long) mbeanServer.getAttribute(on, "CompletedByForce");
        assertEquals(2, byForced.longValue());

        Long bySize = (Long) mbeanServer.getAttribute(on, "CompletedBySize");
        assertEquals(0, bySize.longValue());

        Integer size = (Integer) mbeanServer.getAttribute(on, "CompletionSize");
        assertEquals(10, size.longValue());

        String lan = (String) mbeanServer.getAttribute(on, "CorrelationExpressionLanguage");
        assertEquals("header", lan);

        String cor = (String) mbeanServer.getAttribute(on, "CorrelationExpression");
        assertEquals("id", cor);

        Integer inflight = (Integer) mbeanServer.getAttribute(on, "InProgressCompleteExchanges");
        assertEquals(0, inflight.intValue());

        pending = (Integer) mbeanServer.invoke(on, "aggregationRepositoryGroups", null, null);
        assertEquals(0, pending.intValue());
    }

    @Test
    public void testForceCompletionOfGroup() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        ObjectName on = ObjectName.getInstance("org.apache.camel:context=camel-1,type=processors,name=\"myAggregator\"");
        assertTrue(mbeanServer.isRegistered(on));

        getMockEndpoint("mock:aggregated").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "test1", "id", "1");
        template.sendBodyAndHeader("direct:start", "test2", "id", "2");
        template.sendBodyAndHeader("direct:start", "test3", "id", "1");
        template.sendBodyAndHeader("direct:start", "test4", "id", "2");

        assertMockEndpointsSatisfied();

        getMockEndpoint("mock:aggregated").expectedMessageCount(1);
        getMockEndpoint("mock:aggregated").expectedBodiesReceivedInAnyOrder("test1test3");
        getMockEndpoint("mock:aggregated").expectedPropertyReceived(Exchange.AGGREGATED_COMPLETED_BY, "force");

        Integer pending = (Integer) mbeanServer.invoke(on, "aggregationRepositoryGroups", null, null);
        assertEquals(2, pending.intValue());

        Integer groups = (Integer) mbeanServer.invoke(on, "forceCompletionOfGroup", new Object[]{"1"}, new String[]{"java.lang.String"});
        assertEquals(1, groups.intValue());

        assertMockEndpointsSatisfied();

        Long completed = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
        assertEquals(4, completed.longValue());

        completed = (Long) mbeanServer.getAttribute(on, "TotalCompleted");
        assertEquals(1, completed.longValue());

        Long in = (Long) mbeanServer.getAttribute(on, "TotalIn");
        assertEquals(4, in.longValue());

        Long byForced = (Long) mbeanServer.getAttribute(on, "CompletedByForce");
        assertEquals(1, byForced.longValue());

        Long bySize = (Long) mbeanServer.getAttribute(on, "CompletedBySize");
        assertEquals(0, bySize.longValue());

        Integer size = (Integer) mbeanServer.getAttribute(on, "CompletionSize");
        assertEquals(10, size.longValue());

        String lan = (String) mbeanServer.getAttribute(on, "CorrelationExpressionLanguage");
        assertEquals("header", lan);

        String cor = (String) mbeanServer.getAttribute(on, "CorrelationExpression");
        assertEquals("id", cor);

        Integer inflight = (Integer) mbeanServer.getAttribute(on, "InProgressCompleteExchanges");
        assertEquals(0, inflight.intValue());

        pending = (Integer) mbeanServer.invoke(on, "aggregationRepositoryGroups", null, null);
        assertEquals(1, pending.intValue());

        // we can also use the client mbean
        ManagedAggregateProcessorMBean client = context.getManagedProcessor("myAggregator", ManagedAggregateProcessorMBean.class);
        assertNotNull(client);

        assertEquals(1, client.getCompletedByForce());
        assertEquals(4, client.getTotalIn());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .aggregate(header("id"), new MyAggregationStrategy()).aggregateController(controller).id("myAggregator")
                        .completionSize(10)
                    .to("mock:aggregated");
            }
        };
    }

    public static class MyAggregationStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }
            String body1 = oldExchange.getIn().getBody(String.class);
            String body2 = newExchange.getIn().getBody(String.class);

            oldExchange.getIn().setBody(body1 + body2);
            return oldExchange;
        }
    }
}