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
package org.apache.camel.spring.processor;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.RuntimeCamelException;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;

public class SpringDoubleLoadBalancerMisconfigurationTest extends ContextTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        try {
            super.setUp();
            fail("Should have thrown exception");
        } catch (RuntimeCamelException e) {
            FailedToCreateRouteException fe = assertIsInstanceOf(FailedToCreateRouteException.class, e.getCause());
            IllegalArgumentException ie = assertIsInstanceOf(IllegalArgumentException.class, fe.getCause());
            assertTrue(ie.getMessage().startsWith("Loadbalancer already configured to: RandomLoadBalancer. Cannot set it to: LoadBalanceType[RoundRobinLoadBalancer"));
        }
    }

    @Test
    public void testDummy() {
        // noop
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this, "org/apache/camel/spring/processor/DoubleLoadBalancerMisconfigurationTest.xml");
    }
}