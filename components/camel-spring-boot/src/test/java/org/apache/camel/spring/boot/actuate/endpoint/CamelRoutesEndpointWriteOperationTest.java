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
package org.apache.camel.spring.boot.actuate.endpoint;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
import org.apache.camel.spring.boot.actuate.endpoint.CamelRoutesEndpoint.TimeInfo;
import org.apache.camel.spring.boot.actuate.endpoint.CamelRoutesEndpoint.WriteAction;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * Test for the {@link CamelRoutesEndpoint} actuator endpoint.
 */
@DirtiesContext
@RunWith(SpringRunner.class)
@EnableAutoConfiguration
@SpringBootApplication
@SpringBootTest(
    classes = {CamelAutoConfiguration.class, CamelRoutesEndpointAutoConfiguration.class, ActuatorTestRoute.class},
    properties = {"management.endpoint.camelroutes.read-only = false"})
public class CamelRoutesEndpointWriteOperationTest extends Assert {

    @Autowired
    CamelRoutesEndpoint endpoint;

    @Autowired
    CamelContext camelContext;

    @Test
    public void testWriteOperation() throws Exception {
        ServiceStatus status = camelContext.getRouteController().getRouteStatus("foo-route");
        Assert.assertTrue(status.isStarted());
        TimeInfo timeInfo = new TimeInfo();
        timeInfo.setAbortAfterTimeout(true);
        timeInfo.setTimeout(5L);
        endpoint.doWriteAction("foo-route", WriteAction.STOP, timeInfo);
        status = camelContext.getRouteController().getRouteStatus("foo-route");
        Assert.assertTrue(status.isStopped());
        endpoint.doWriteAction("foo-route", WriteAction.START, timeInfo);
        status = camelContext.getRouteController().getRouteStatus("foo-route");
        Assert.assertTrue(status.isStarted());
    }

    @Test
    public void testRouteDump() throws Exception {
        String dump = endpoint.getRouteDump("foo-route");
        assertNotNull(dump);
        assertTrue(dump, dump.contains("<route "));
        assertTrue(dump, dump.contains("<from "));
        assertTrue(dump, dump.contains("uri=\"timer:foo\""));
        assertTrue(dump, dump.contains("<to "));
        assertTrue(dump, dump.contains("uri=\"log:foo\""));
        assertTrue(dump, dump.contains("</route>"));
    }

}
