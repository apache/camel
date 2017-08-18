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
import org.apache.camel.spring.boot.model.RouteDetailsInfo;
import org.apache.camel.spring.boot.model.RouteInfo;
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
@SpringBootTest(classes = {CamelAutoConfiguration.class, CamelRoutesEndpointAutoConfiguration.class, ActuatorTestRoute.class})
public class CamelRoutesMvcEndpointTest extends Assert {

    @Autowired
    CamelRoutesMvcEndpoint endpoint;

    @Autowired
    CamelContext camelContext;

    @Test
    public void testRoutesEndpoint() throws Exception {
        List<RouteInfo> routes = (List<RouteInfo>)endpoint.invoke();

        assertFalse(routes.isEmpty());
        assertEquals(routes.size(), camelContext.getRoutes().size());
        assertTrue(routes.stream().anyMatch(r -> "foo-route".equals(r.getId())));
    }

    @Test
    public void testMvcRoutesEndpoint() throws Exception {
        Object result = endpoint.detail("foo-route");

        assertTrue(result instanceof RouteDetailsInfo);
        assertEquals("foo-route", ((RouteDetailsInfo)result).getId());
    }

}
