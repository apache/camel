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
package org.apache.camel.management;

import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedScheduledPollConsumerLastPollTest extends ManagementTestSupport {

    @Test
    public void testLastPollAttributes() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=consumers,*"), null);
        assertEquals(1, set.size());
        ObjectName on = set.iterator().next();

        // the consumer polls, but there is nothing to poll yet
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertNotNull(mbeanServer.getAttribute(on, "LastPollTimestamp")));
        assertNull(mbeanServer.getAttribute(on, "LastPollMessagesTimestamp"));
        assertEquals(0, mbeanServer.getAttribute(on, "LastPollMessagesCount"));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        template.sendBodyAndHeader(fileUri(), "Hello", Exchange.FILE_NAME, "a.txt");
        template.sendBodyAndHeader(fileUri(), "World", Exchange.FILE_NAME, "b.txt");
        mock.assertIsSatisfied();

        // the consumer keeps polling after that, and finds nothing: the last poll moves on, the last poll with messages
        // stays
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Date lastMessages = (Date) mbeanServer.getAttribute(on, "LastPollMessagesTimestamp");
            assertNotNull(lastMessages);
            Date lastPoll = (Date) mbeanServer.getAttribute(on, "LastPollTimestamp");
            assertTrue(lastPoll.after(lastMessages), "Last poll " + lastPoll + " after " + lastMessages);
        });
        int count = (Integer) mbeanServer.getAttribute(on, "LastPollMessagesCount");
        // the two files are polled together, or one by one
        assertTrue(count == 1 || count == 2, "Polled messages: " + count);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10")).to("mock:result");
            }
        };
    }

}
