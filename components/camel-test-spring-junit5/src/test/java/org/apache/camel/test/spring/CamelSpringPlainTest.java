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
package org.apache.camel.test.spring;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ServiceStatus;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.engine.DefaultManagementStrategy;
import org.apache.camel.test.spring.junit5.CamelSpringTest;
import org.apache.camel.test.spring.junit5.StopWatchTestExecutionListener;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// tag::example[]
@CamelSpringTest
@ContextConfiguration
// Put here to prevent Spring context caching across tests and test methods since some tests inherit
// from this test and therefore use the same Spring context.  Also because we want to reset the
// Camel context and mock endpoints between test methods automatically.
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class CamelSpringPlainTest {

    @Autowired
    protected CamelContext camelContext;

    @EndpointInject("mock:a")
    protected MockEndpoint mockA;

    @EndpointInject("mock:b")
    protected MockEndpoint mockB;

    @Produce("direct:start")
    protected ProducerTemplate start;

    @Test
    public void testPositive() throws Exception {
        assertEquals(ServiceStatus.Started, camelContext.getStatus());

        mockA.expectedBodiesReceived("David");
        mockB.expectedBodiesReceived("Hello David");

        start.sendBody("David");

        MockEndpoint.assertIsSatisfied(camelContext);
    }

    @Test
    public void testJmx() {
        assertEquals(DefaultManagementStrategy.class, camelContext.getManagementStrategy().getClass());
    }

    @Test
    public void testShutdownTimeout() {
        assertEquals(10, camelContext.getShutdownStrategy().getTimeout());
        assertEquals(TimeUnit.SECONDS, camelContext.getShutdownStrategy().getTimeUnit());
    }

    @Test
    public void testStopwatch() {
        StopWatch stopWatch = StopWatchTestExecutionListener.getStopWatch();
        
        assertNotNull(stopWatch);
        assertTrue(stopWatch.taken() < 100);
    }

    @Test
    public void testExcludedRoute() {
        assertNotNull(camelContext.getRoute("excludedRoute"));
    }

    @Test
    public void testProvidesBreakpoint() {
        assertNull(camelContext.getDebugger());
    }

    @Test
    public void testRouteCoverage() {
        // noop
    }

}
// end::example[]

