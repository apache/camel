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

import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.ServiceStatus;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

import static org.awaitility.Awaitility.await;

/**
 * Extended test to see if mbeans is removed and stats are correct
 */
public class ManagedRouteStopAndStartCleanupTest extends ManagedRouteStopAndStartTest {

    @Override
    @Test
    public void testStopAndStartRoute() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getRouteObjectName(mbeanServer);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader("file://target/data/managed", "Hello World", Exchange.FILE_NAME, "hello.txt");

        assertMockEndpointsSatisfied();

        // should be started
        String state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals("Should be started", ServiceStatus.Started.name(), state);

        // need a bit time to let JMX update
        await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> {
            // should have 1 completed exchange
            Long completed = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
            assertEquals(1, completed.longValue());
        });

        // should be 1 consumer and 2 processors
        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=consumers,*"), null);
        assertEquals("Should be 1 consumer", 1, set.size());

        set = mbeanServer.queryNames(new ObjectName("*:type=processors,*"), null);
        assertEquals("Should be 2 processors", 2, set.size());

        // stop
        log.info(">>>>>>>>>>>>>>>>>> invoking stop <<<<<<<<<<<<<<<<<<<<<");
        mbeanServer.invoke(on, "stop", null, null);
        log.info(">>>>>>>>>>>>>>>>>> invoking stop DONE <<<<<<<<<<<<<<<<<<<<<");

        state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals("Should be stopped", ServiceStatus.Stopped.name(), state);

        // should be 0 consumer and 0 processor
        set = mbeanServer.queryNames(new ObjectName("*:type=consumers,*"), null);
        assertEquals("Should be 0 consumer", 0, set.size());

        set = mbeanServer.queryNames(new ObjectName("*:type=processors,*"), null);
        assertEquals("Should be 0 processor", 0, set.size());

        mock.reset();
        mock.expectedBodiesReceived("Bye World");
        // wait 2 seconds while route is stopped to verify that file was not consumed
        mock.setResultWaitTime(2000);

        template.sendBodyAndHeader("file://target/data/managed", "Bye World", Exchange.FILE_NAME, "bye.txt");

        // route is stopped so we do not get the file
        mock.assertIsNotSatisfied();

        // prepare mock for starting route
        mock.reset();
        mock.expectedBodiesReceived("Bye World");

        // start
        log.info(">>>>>>>>>>>>>>>>> invoking start <<<<<<<<<<<<<<<<<<");
        mbeanServer.invoke(on, "start", null, null);
        log.info(">>>>>>>>>>>>>>>>> invoking start DONE <<<<<<<<<<<<<<<<<<");

        state = (String) mbeanServer.getAttribute(on, "State");
        assertEquals("Should be started", ServiceStatus.Started.name(), state);

        // should be 1 consumer and 1 processor
        set = mbeanServer.queryNames(new ObjectName("*:type=consumers,*"), null);
        assertEquals("Should be 1 consumer", 1, set.size());

        set = mbeanServer.queryNames(new ObjectName("*:type=processors,*"), null);
        assertEquals("Should be 2 processors", 2, set.size());

        // this time the file is consumed
        mock.assertIsSatisfied();

        // need a bit time to let JMX update
        await().atMost(1, TimeUnit.SECONDS).until(() -> {
            Long num = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
            return num == 2;
        });

        // should have 2 completed exchange
        Long completed = (Long) mbeanServer.getAttribute(on, "ExchangesCompleted");
        assertEquals(2, completed.longValue());
    }

}
