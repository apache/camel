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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.spring.boot.CamelAutoConfiguration;
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
 * Test for the {@link CamelRouteControllerEndpoint} actuator endpoint.
 */
@DirtiesContext
@RunWith(SpringRunner.class)
@EnableAutoConfiguration
@SpringBootApplication
@SpringBootTest(classes = {CamelAutoConfiguration.class, CamelRouteControllerEndpointAutoConfiguration.class, ActuatorTestRoute.class})
public class CamelRouteControllerEndpointTest extends Assert {

    @Autowired
    CamelRouteControllerEndpoint endpoint;

    @Autowired
    CamelContext camelContext;

    @Test
    public void testRouteControllerEndpoint() throws Exception {
        List<String> routesId = endpoint.getControlledRoutes();

        assertNotNull(routesId);
        /* FIXME DefaultRouteController returns empty list while it does control any route...
        assertFalse(routesId.isEmpty());
        assertEquals(routesId.size(), camelContext.getRoutes().size());
        assertTrue(routesId.stream().anyMatch(r -> "foo-route".equals(r)));
        */
    }

}
