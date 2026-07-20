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
package org.apache.camel.component.cxf.spring;

import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.cxf.jaxws.CxfEndpoint;
import org.apache.cxf.Bus;
import org.apache.cxf.Bus.BusState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that stopping a CxfSpringEndpoint does not shut down the shared application-wide CXF bus.
 */
public class CxfSpringEndpointSharedBusTest extends AbstractSpringBeanTestSupport {

    @Override
    protected String[] getApplicationContextFiles() {
        CXFTestSupport.getPort1();
        return new String[] { "org/apache/camel/component/cxf/spring/CxfEndpointBeansRouter.xml" };
    }

    @Test
    public void testStopEndpointDoesNotShutdownSharedBus() throws Exception {
        CxfEndpoint endpoint = ctx.getBean("routerEndpoint", CxfEndpoint.class);
        Bus bus = endpoint.getBus();
        assertNotNull(bus);

        // stopping the endpoint must not shut down the shared bus
        endpoint.stop();
        assertTrue(bus.getState() == BusState.RUNNING || bus.getState() == BusState.INITIAL,
                "Shared bus must still be running after endpoint stop, but was: " + bus.getState());
    }

    @Test
    public void testTwoEndpointsShareSameBus() throws Exception {
        CxfEndpoint endpoint1 = ctx.getBean("routerEndpoint", CxfEndpoint.class);
        CxfEndpoint endpoint2 = ctx.getBean("serviceEndpoint", CxfEndpoint.class);

        assertSame(endpoint1.getBus(), endpoint2.getBus(),
                "Both endpoints should share the same application bus");

        // stop one endpoint — the other's bus must remain running
        endpoint1.stop();
        Bus sharedBus = endpoint2.getBus();
        assertTrue(sharedBus.getState() == BusState.RUNNING || sharedBus.getState() == BusState.INITIAL,
                "Shared bus must still be running after stopping one endpoint");
    }
}
